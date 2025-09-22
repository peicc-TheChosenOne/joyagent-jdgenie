# -*- coding: utf-8 -*-
# =====================
# 请求响应协议定义 - API数据模型
# Author: liumin.423
# Date:   2025/7/7
# =====================
import hashlib
from typing import Optional, Literal, List

from pydantic import BaseModel, Field, computed_field


class StreamMode(BaseModel):
    """流式响应模式配置"""
    mode: Literal["general", "token", "time"] = Field(default="general")  # general:实时 token:按token time:按时间
    token: Optional[int] = Field(default=5, ge=1)  # token模式下每N个token输出一次
    time: Optional[int] = Field(default=5, ge=1)   # time模式下每N秒输出一次


class CIRequest(BaseModel):
    """代码解释器请求模型"""
    request_id: str = Field(alias="requestId", description="请求唯一标识")
    task: Optional[str] = Field(default=None, description="代码分析任务描述")
    file_names: Optional[List[str]] = Field(default=[], alias="fileNames", description="输入文件列表")
    file_name: Optional[str] = Field(default=None, alias="fileName", description="输出文件名称")
    file_description: Optional[str] = Field(default=None, alias="fileDescription", description="输出文件描述")
    stream: bool = True  # 是否流式响应
    stream_mode: Optional[StreamMode] = Field(default=StreamMode(), alias="streamMode", description="流式模式配置")
    origin_file_names: Optional[List[dict]] = Field(default=None, alias="originFileNames", description="原始文件信息")


class ReportRequest(CIRequest):
    """报告生成请求模型"""
    file_type: Literal["html", "markdown", "ppt"] = Field("html", alias="fileType", description="输出报告格式")


class FileRequest(BaseModel):
    """文件请求模型"""
    request_id: str = Field(alias="requestId", description="请求唯一标识")
    file_name: str = Field(alias="fileName", description="文件名称")

    @computed_field
    def file_id(self) -> str:
        """计算文件唯一ID"""
        return get_file_id(self.request_id, self.file_name)


def get_file_id(request_id: str, file_name: str) -> str:
    """基于请求ID和文件名生成唯一文件ID"""
    return hashlib.md5((request_id + file_name).encode("utf-8")).hexdigest()


class FileListRequest(BaseModel):
    request_id: str = Field(alias="requestId", description="Request ID")
    filters: Optional[List[FileRequest]] = Field(default=None, description="过滤条件")
    page: int = 1
    page_size: int = Field(default=10, alias="pageSize", description="Request ID")


class FileUploadRequest(FileRequest):
    description: str = Field(description="返回的生成的文件描述")
    content: str = Field(description="返回的生成的文件内容")


class DeepSearchRequest(BaseModel):
    """深度搜索请求模型"""
    request_id: str = Field(description="请求唯一标识")
    query: str = Field(description="搜索查询内容")
    max_loop: Optional[int] = Field(default=1, alias="maxLoop", description="最大搜索轮数")
    search_engines: List[str] = Field(default=[], description="搜索引擎列表(bing/jina/sogou/serp)")
    stream: bool = Field(default=True, description="是否流式响应")
    stream_mode: Optional[StreamMode] = Field(default=StreamMode(), alias="streamMode", description="流式模式配置")
