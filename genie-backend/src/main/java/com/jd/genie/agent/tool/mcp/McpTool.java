package com.jd.genie.agent.tool.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.util.OkHttpUtil;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MCP (Model Context Protocol) 工具代理类
 *
 * 该类实现了BaseTool接口，作为MCP协议的客户端代理，
 * 负责与外部MCP服务器通信，获取工具列表和调用工具方法。
 * 支持动态发现和调用外部工具服务。
 *
 * 主要功能：
 * 1. 连接MCP服务器获取可用工具列表
 * 2. 代理调用MCP服务器上的工具方法
 * 3. 处理MCP协议的请求和响应格式转换
 *
 * @author 系统生成
 */
@Slf4j
@Data
public class McpTool implements BaseTool {

    /** 智能体上下文，包含请求信息和运行环境 */
    private AgentContext agentContext;

    /**
     * MCP工具请求数据结构
     * 用于封装向MCP服务器发送的请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolRequest {
        /** MCP服务器URL地址 */
        private String server_url;
        /** 工具名称 */
        private String name;
        /** 工具调用参数 */
        private Map<String, Object> arguments;
    }

    /**
     * MCP工具响应数据结构
     * 用于封装从MCP服务器接收的响应数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolResponse {
        /** 响应状态码 */
        private String code;
        /** 响应消息 */
        private String message;
        /** 响应数据 */
        private String data;
    }

    /**
     * 获取工具名称
     *
     * @return 返回工具标识符 "mcp_tool"
     */
    @Override
    public String getName() {
        return "mcp_tool";
    }

    /**
     * 获取工具描述
     * MCP工具的描述信息，当前返回空字符串
     *
     * @return 工具描述信息
     */
    @Override
    public String getDescription() {
        return "";
    }

    /**
     * 获取工具参数定义
     * MCP工具的参数由外部服务器动态定义，此处返回null
     *
     * @return 参数定义Map，MCP工具返回null表示动态参数
     */
    @Override
    public Map<String, Object> toParams() {
        return null;
    }

    /**
     * 执行工具
     * 基础执行方法，MCP工具主要通过callTool方法执行
     *
     * @param input 输入参数
     * @return 执行结果，当前返回null
     */
    @Override
    public Object execute(Object input) {
        return null;
    }

    /**
     * 获取MCP服务器上的工具列表
     *
     * 通过HTTP调用MCP客户端服务，获取指定MCP服务器上可用的工具列表。
     * 该方法实现了MCP协议的工具发现功能。
     *
     * 调用流程：
     * 1. 构建MCP工具列表请求
     * 2. 调用MCP客户端的 /v1/tool/list 接口
     * 3. 返回工具列表的JSON响应
     *
     * @param mcpServerUrl MCP服务器的URL地址
     * @return JSON格式的工具列表响应，失败时返回空字符串
     */
    public String listTool(String mcpServerUrl) {
        try {
            // 获取配置信息
            GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);

            // 构建MCP客户端的工具列表接口URL
            String mcpClientUrl = genieConfig.getMcpClientUrl() + "/v1/tool/list";

            // 构建MCP工具请求对象
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .server_url(mcpServerUrl)  // 指定目标MCP服务器地址
                    .build();

            // 发送HTTP POST请求获取工具列表
            String response = OkHttpUtil.postJson(mcpClientUrl,
                    JSON.toJSONString(mcpToolRequest),
                    null, 30L);

            // 记录请求和响应日志
            log.info("list tool request: {} response: {}",
                    JSON.toJSONString(mcpToolRequest, SerializerFeature.PrettyFormat), response);

            return response;

        } catch (Exception e) {
            // 记录获取工具列表失败的错误日志
            log.error("{} list tool error", agentContext.getRequestId(), e);
        }
        return "";
    }

    /**
     * 调用MCP服务器上的具体工具
     *
     * 通过HTTP调用MCP客户端服务，执行指定MCP服务器上的工具方法。
     * 该方法实现了MCP协议的工具调用功能。
     *
     * 调用流程：
     * 1. 解析输入参数
     * 2. 构建MCP工具调用请求
     * 3. 调用MCP客户端的 /v1/tool/call 接口
     * 4. 返回工具执行结果
     *
     * @param mcpServerUrl MCP服务器的URL地址
     * @param toolName 要调用的工具名称
     * @param input 工具调用参数
     * @return JSON格式的工具执行结果，失败时返回空字符串
     */
    public String callTool(String mcpServerUrl, String toolName, Object input) {
        try {
            // 获取配置信息
            GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);

            // 构建MCP客户端的工具调用接口URL
            String mcpClientUrl = genieConfig.getMcpClientUrl() + "/v1/tool/call";

            // 解析输入参数
            Map<String, Object> params = (Map<String, Object>) input;

            // 构建MCP工具调用请求对象
            McpToolRequest mcpToolRequest = McpToolRequest.builder()
                    .name(toolName)           // 指定要调用的工具名称
                    .server_url(mcpServerUrl) // 指定目标MCP服务器地址
                    .arguments(params)        // 传递工具调用参数
                    .build();

            // 发送HTTP POST请求调用工具
            String response = OkHttpUtil.postJson(mcpClientUrl,
                    JSON.toJSONString(mcpToolRequest),
                    null, 30L);

            // 记录请求和响应日志
            log.info("call tool request: {} response: {}",
                    JSON.toJSONString(mcpToolRequest, SerializerFeature.PrettyFormat), response);

            return response;

        } catch (Exception e) {
            // 记录工具调用失败的错误日志
            log.error("{} call tool error ", agentContext.getRequestId(), e);
        }
        return "";
    }
}
