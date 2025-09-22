package com.jd.genie.service.impl;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.AgentHandlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentHandlerFactory {

    private final Map<String, AgentHandlerService> handlerMap = new ConcurrentHashMap<>(); // 注册所有 Handler 实例

    // 构造函数注入所有DataHandler实现
    @Autowired
    /**
     * 收集所有实现了 AgentHandlerService 的 Bean
     * Spring启动时自动注入所有处理器实现类
     */
    public AgentHandlerFactory(List<AgentHandlerService> handlers) {
        // 初始化处理器映射
        for (AgentHandlerService handler : handlers) {
            // 可根据Handler的supports方法或自定义注解来注册
            handlerMap.put(handler.getClass().getSimpleName().toLowerCase(), handler);
        }
    }

    // 根据类型获取处理器
    /**
     * 遍历匹配，找到第一个 support 的处理器
     * 根据AgentContext和AgentRequest的特征，选择合适的处理器
     * 支持React模式、PlanSolve模式等多种智能体执行模式
     */
    public AgentHandlerService getHandler(AgentContext context, AgentRequest request) {
        if (Objects.isNull(context) || Objects.isNull(request)) {
            return null;
        }

        // 方法1：通过supports方法匹配
        for (AgentHandlerService handler : handlerMap.values()) {
            if (handler.support(context, request)) {
                return handler;
            }
        }

        return null;
    }
}
