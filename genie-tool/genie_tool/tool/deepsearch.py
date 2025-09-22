# -*- coding: utf-8 -*-
# =====================
# 深度搜索工具 - 多搜索引擎集成和智能问答
# Author: wanghanmin1
# Date:   2025/7/8
# =====================
import asyncio
import json
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from functools import partial
from typing import List, AsyncGenerator, Tuple

from genie_tool.util.log_util import logger
from genie_tool.util.llm_util import ask_llm
from genie_tool.model.document import Doc
from genie_tool.util.log_util import timer
from genie_tool.tool.search_component.query_process import query_decompose
from genie_tool.tool.search_component.answer import answer_question
from genie_tool.tool.search_component.reasoning import search_reasoning
from genie_tool.tool.search_component.search_engine import MixSearch
from genie_tool.model.protocal import StreamMode
from genie_tool.util.file_util import truncate_files
from genie_tool.model.context import LLMModelInfoFactory


class DeepSearch:
    """深度搜索工具 - 集成多搜索引擎和智能问答"""

    def __init__(self, engines: List[str] = []):
        """初始化搜索引擎配置"""
        if not engines:
            engines = os.getenv("USE_SEARCH_ENGINE", "bing").split(",")
        use_bing = "bing" in engines
        use_jina = "jina" in engines
        use_sogou = "sogou" in engines
        use_serp = "serp" in engines
        # 创建搜索函数 - 支持多引擎并行搜索
        self._search_single_query = partial(
            MixSearch().search_and_dedup, use_bing=use_bing, use_jina=use_jina, use_sogou=use_sogou, use_serp=use_serp)
        self.searched_queries = []  # 已搜索查询记录，用于去重
        self.current_docs = []      # 当前文档集合，累积搜索结果

    def search_docs_str(self, model: str = None) -> str:
        """将文档集合格式化为字符串"""
        current_docs_str = ""
        # 根据模型上下文长度限制截断文档
        max_tokens = LLMModelInfoFactory.get_context_length(model)
        truncate_docs = truncate_files(self.current_docs, max_tokens=int(max_tokens * 0.8)) if model else self.current_docs
        for i, doc in enumerate(truncate_docs, start=1):
            current_docs_str += f"文档编号〔{i}〕. \n{doc.to_html()}\n"
        return current_docs_str

    @timer()
    async def run(
            self,
            query: str,  # 用户查询
            request_id: str = None,  # 请求ID
            max_loop: int = 1,  # 最大搜索轮数
            stream: bool = False,  # 是否流式输出
            stream_mode: StreamMode = StreamMode(),  # 流式模式配置
            *args,
            **kwargs
    ) -> AsyncGenerator[str, None]:  # 返回异步生成器
        """深度搜索主流程（流式输出）"""
        current_loop = 1
        
        # 多轮搜索循环
        while current_loop <= max_loop:
            logger.info(f"{request_id} 第 {current_loop} 轮深度搜索...")
            
            # 阶段1: 查询分解
            sub_queries = await query_decompose(query=query)
            
            # 返回分解结果给客户端
            yield json.dumps({
                "requestId": request_id,
                "query": query,
                "searchResult": {"query": sub_queries, "docs": [[]] * len(sub_queries)},
                "isFinal": False,
                "messageType": "extend"  # 查询扩展阶段
            }, ensure_ascii=False)

            await asyncio.sleep(0.1)  # 短暂延迟，确保消息顺序

            # 过滤已搜索查询，避免重复搜索
            sub_queries = [sub_query for sub_query in sub_queries
                           if sub_query not in self.searched_queries]
            
            # 阶段2: 并行搜索并去重
            searched_docs, docs_list = await self._search_queries_and_dedup(
                queries=sub_queries,
                request_id=request_id,
            )

            # 返回搜索结果
            truncate_len = int(os.getenv("SINGLE_PAGE_MAX_SIZE", 200))
            yield json.dumps({
                "requestId": request_id,
                "query": query,
                "searchResult": {
                    "query": sub_queries,
                    "docs": [[d.to_dict(truncate_len=truncate_len) for d in docs_l] for docs_l in docs_list]
                },
                "isFinal": False,
                "messageType": "search"  # 搜索结果阶段
            }, ensure_ascii=False)

            # 更新上下文，累积搜索结果
            self.current_docs.extend(searched_docs)
            self.searched_queries.extend(sub_queries)

            # 达到最大轮数时结束
            if current_loop == max_loop:
                break

            # 阶段3: 推理判断是否需要继续搜索
            reasoning_result = search_reasoning(
                request_id=request_id,
                query=query,
                content=self.search_docs_str(os.getenv("SEARCH_REASONING_MODEL")),
            )

            # 如果推理判断已可回答，结束搜索
            if reasoning_result.get("is_verify", "1") in ["1", 1]:
                logger.info(f"{request_id} 推理判断无需继续搜索")
                break

            current_loop += 1

        # 阶段4: 生成最终答案
        answer = ""
        acc_content = ""
        acc_token = 0
        
        async for chunk in answer_question(
                query=query, 
                search_content=self.search_docs_str(os.getenv("SEARCH_ANSWER_MODEL"))
        ):
            if stream:
                # 流式输出，按token分块
                if acc_token >= stream_mode.token:
                    yield json.dumps({
                        "requestId": request_id,
                        "query": query,
                        "searchResult": {"query": [], "docs": []},
                        "answer": acc_content,
                        "isFinal": False,
                        "messageType": "report"  # 报告生成阶段
                    }, ensure_ascii=False)
                    acc_content = ""
                    acc_token = 0
                acc_content += chunk
                acc_token += 1
            answer += chunk
            
        # 返回剩余答案内容
        if stream and acc_content:
            yield json.dumps({
                "requestId": request_id,
                "query": query,
                "searchResult": {"query": [], "docs": []},
                "answer": acc_content,
                "isFinal": False,
                "messageType": "report"
            }, ensure_ascii=False)
            
        # 返回最终完整结果
        yield json.dumps({
            "requestId": request_id,
            "query": query,
            "searchResult": {"query": [], "docs": []},
            "answer": "" if stream else answer,  # 非流式模式返回完整答案
            "isFinal": True,
            "messageType": "report"
        }, ensure_ascii=False)

    async def _search_queries_and_dedup(
            self,
            queries: List[str],
            request_id: str,
    ) -> Tuple[List[Doc], List[List[Doc]]]:
        """并行搜索多个查询并去重"""
        def _run_async(*args, **kwargs):
            """线程中运行异步搜索"""
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            s_result = loop.run_until_complete(self._search_single_query(*args, **kwargs))
            loop.close()
            return s_result

        # 使用线程池并发执行搜索
        process_list = []
        with ThreadPoolExecutor(max_workers=int(os.getenv("SEARCH_THREAD_NUM", 5))) as executor:
            for query in queries:
                process = executor.submit(_run_async, query, request_id)
                process_list.append(process)
        
        # 等待所有搜索完成
        results = [process.result() for process in as_completed(process_list)]
        
        # 展平结果并去重
        all_docs = [doc for docs in results for doc in docs]
        seen_content = set()
        deduped_docs = []
        for doc in all_docs:
            if doc.content and doc.content not in seen_content:
                deduped_docs.append(doc)
                seen_content.add(doc.content)
                
        return deduped_docs, results  # 返回去重文档和原始分组结果
