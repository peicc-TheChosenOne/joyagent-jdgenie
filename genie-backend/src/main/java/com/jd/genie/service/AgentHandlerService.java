package com.jd.genie.service;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.model.req.AgentRequest;

public interface AgentHandlerService {

    /**
     * 处理Agent请求
     * @param context 运行上下文（包含请求ID、工具集合、SSE输出器等）
     * @param request 原始用户请求
     * @return 可选的结果字符串（通常通过SSE输出，返回值可忽略）
     */
    String handle(AgentContext context, AgentRequest request);

    /**
     * 是否由当前Handler处理
     * @return true 表示匹配该处理器（例如根据 AgentType）
     */
    Boolean support(AgentContext context, AgentRequest request);

}