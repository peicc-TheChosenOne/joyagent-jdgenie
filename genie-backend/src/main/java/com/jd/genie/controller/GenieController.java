package com.jd.genie.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.printer.SSEPrinter;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.tool.common.CodeInterpreterTool;
import com.jd.genie.agent.tool.common.DeepSearchTool;
import com.jd.genie.agent.tool.common.FileTool;
import com.jd.genie.agent.tool.common.ReportTool;
import com.jd.genie.agent.tool.mcp.McpTool;
import com.jd.genie.agent.util.DateUtil;
import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.service.AgentHandlerService;
import com.jd.genie.service.IGptProcessService;
import com.jd.genie.service.impl.AgentHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 智能体调度控制器
 * - 对外提供主入口 /AutoAgent（SSE流）
 * - 组装 Agent 上下文与工具集合
 * - 按 AgentType 选择对应 Handler 执行
 * - 维护 SSE 心跳与连接生命周期
 *
 * AutoAgent 接口执行流程：
 * 1. 建立SSE连接并启动心跳机制
 * 2. 构建AgentContext（包含请求信息、工具集合等）
 * 3. 通过AgentHandlerFactory选择对应的处理器
 * 4. 执行智能体逻辑并通过SSE流式输出结果
 */

@Slf4j
@RestController
@RequestMapping("/")
public class GenieController {

    // SSE心跳与异步任务线程池
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);


    private static final long HEARTBEAT_INTERVAL = 10_000L; // 心跳间隔（毫秒）
    private static final Long AUTO_AGENT_SSE_TIMEOUT = 60 * 60 * 1000L; // 1小时
    @Autowired
    protected GenieConfig genieConfig; // 配置中心，读取 application.yml
    @Autowired
    private AgentHandlerFactory agentHandlerFactory; // Handler 工厂，按类型分发
    @Autowired
    private IGptProcessService gptProcessService; // 面向 UI 的增量查询服务

    /**
     * 启动SSE心跳机制
     * - 每10秒向客户端发送心跳消息，防止连接超时
     * - 异常时自动关闭连接并取消心跳任务
     * 
     * @param emitter   SSE发射器，用于发送心跳消息
     * @param requestId 请求ID，用于日志记录
     * @return ScheduledFuture 心跳任务的调度句柄，用于后续取消
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String requestId) {
        // 定时向客户端发送心跳，避免连接超时被中断
        return executor.scheduleAtFixedRate(() -> {
            try {
                // 发送心跳消息
                // log.info("{} send heartbeat", requestId);
                emitter.send("heartbeat");
            } catch (Exception e) {
                // 发送心跳失败，关闭连接
                log.error("{} heartbeat failed, closing connection", requestId, e);
                emitter.completeWithError(e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册SSE生命周期事件监听器
     * - 监听连接完成、超时、错误事件
     * - 事件发生时自动取消心跳任务并清理资源
     * 
     * @param emitter         SSE发射器
     * @param requestId       请求ID，用于日志记录
     * @param heartbeatFuture 心跳任务句柄，用于取消任务
     */
    private void registerSSEMonitor(SseEmitter emitter, String requestId, ScheduledFuture<?> heartbeatFuture) {
        // 监听 SSE 生命周期事件：完成/超时/错误，确保释放心跳任务
        // 监听SSE异常事件
        emitter.onCompletion(() -> {
            log.info("{} SSE connection completed normally", requestId);
            heartbeatFuture.cancel(true);
        });

        // 监听连接超时事件
        emitter.onTimeout(() -> {
            log.info("{} SSE connection timed out", requestId);
            heartbeatFuture.cancel(true);
            emitter.complete();
        });

        // 监听连接错误事件
        emitter.onError((ex) -> {
            log.info("{} SSE connection error: ", requestId, ex);
            heartbeatFuture.cancel(true);
            emitter.completeWithError(ex);
        });
    }

    /**
     * /AutoAgent 接口主入口
     * 执行完整的智能体调度流程：
     * 1. 建立SSE连接，配置心跳和生命周期监听
     * 2. 处理输出样式，追加相应的提示词
     * 3. 异步执行智能体逻辑，避免阻塞主线程
     * 4. 构建上下文、选择处理器、执行任务
     * 5. 通过SSE流式输出结果，最终关闭连接
     * 
     * @param request AgentRequest 客户端请求，包含查询、AgentType等信息
     * @return SseEmitter SSE发射器，用于流式返回处理结果
     * @throws UnsupportedEncodingException 编码异常
     */
    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {
        // 主入口：按请求构建上下文与工具，选择 Handler 执行，并以 SSE 流式返回
        log.info("{} auto agent request: {}", request.getRequestId(),JSON.toJSONString(request, SerializerFeature.PrettyFormat));
        // 建立长连接（默认1小时）
        SseEmitter emitter = new SseEmitter(AUTO_AGENT_SSE_TIMEOUT);
        // SSE心跳
        ScheduledFuture<?> heartbeatFuture = startHeartbeat(emitter, request.getRequestId()); // 启动心跳
        // 监听SSE事件
        registerSSEMonitor(emitter, request.getRequestId(), heartbeatFuture); // 绑定监听器
        // 拼接输出类型
        request.setQuery(handleOutputStyle(request)); // 根据输出样式追加提示词
        // 执行调度引擎
        ThreadUtil.execute(() -> {
            try {
                Printer printer = new SSEPrinter(emitter, request, request.getAgentType()); // SSE输出器
                // 构建智能体上下文对象，包含执行任务所需的所有必要信息
                AgentContext agentContext = AgentContext.builder()
                        .requestId(request.getRequestId())           // 设置请求唯一标识
                        .sessionId(request.getRequestId())           // 设置会话ID（与请求ID保持一致）
                        .printer(printer)                            // 设置输出打印器（SSE输出器）
                        .query(request.getQuery())                   // 设置用户查询内容
                        .task("")                                    // 初始化任务描述为空
                        .dateInfo(DateUtil.CurrentDateInfo())        // 设置当前日期时间信息
                        .productFiles(new ArrayList<>())             // 初始化产品文件列表为空
                        .taskProductFiles(new ArrayList<>())         // 初始化任务产品文件列表为空
                        .sopPrompt(request.getSopPrompt())           // 设置SOP提示词（深度思考模式）
                        .basePrompt(request.getBasePrompt())         // 设置基础提示词（普通模式）
                        .agentType(request.getAgentType())           // 设置智能体类型标识
                        .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false) // 设置是否流式输出，默认false
                        .build();                                    // 构建AgentContext对象

                // 构建工具列表
                agentContext.setToolCollection(buildToolCollection(agentContext, request)); // 装配工具集合
                // 根据数据类型获取对应的处理器
                AgentHandlerService handler = agentHandlerFactory.getHandler(agentContext, request); // 选择对应模式的处理器
                // 执行处理逻辑
                handler.handle(agentContext, request); // 执行主流程
                // 关闭连接
                emitter.complete(); // 任务完成，关闭 SSE

            } catch (Exception e) {
                log.error("{} auto agent error", request.getRequestId(), e);
            }
        });

        return emitter;
    }

    /**
     * 处理输出样式配置
     * 根据请求中的outputStyle字段，为查询追加相应的输出格式提示词
     * 支持：html（HTML格式）、docs（Markdown格式）、table（Excel格式）等
     * 
     * @param request AgentRequest 请求对象
     * @return String 处理后的查询字符串，包含输出样式提示词
     */
    private String handleOutputStyle(AgentRequest request) {
        String query = request.getQuery();
        Map<String, String> outputStyleMap = genieConfig.getOutputStylePrompts();
        if (!StringUtils.isEmpty(request.getOutputStyle())) {
            // 根据输出样式追加相应的提示词，如果样式不存在则追加空字符串
            query += outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
        }
        return query;
    }

    /**
     * 构建智能体工具集合
     *
     * 动态装配智能体可使用的工具集合，包括内置工具和外部MCP工具。
     * 工具装配采用配置驱动的方式，支持运行时动态启用/禁用特定工具。
     *
     * 工具装配流程：
     * 1. 初始化工具集合容器
     * 2. 装配必备的文件操作工具
     * 3. 根据配置文件装配可选的内置工具（代码解释、报告生成、深度搜索）
     * 4. 通过MCP协议动态加载外部工具
     * 5. 返回完整的工具集合供智能体使用
     *
     * 支持的内置工具类型：
     * - FileTool：文件读取、写入、操作等文件相关功能
     * - CodeInterpreterTool：代码执行、解释、调试功能
     * - ReportTool：报告生成、格式化输出功能
     * - DeepSearchTool：深度搜索、数据检索功能
     *
     * MCP工具集成：
     * - 支持通过MCP协议连接外部工具服务
     * - 动态发现和注册工具方法
     * - 提供统一的工具调用接口
     *
     * 配置方式：
     * - 内置工具通过 genieConfig.getMultiAgentToolListMap() 配置
     * - MCP服务器通过 genieConfig.getMcpServerUrlArr() 配置
     * - 支持工具的热插拔和动态配置
     *
     * @param agentContext AgentContext 智能体上下文，包含请求信息和运行环境
     * @param request      AgentRequest 请求对象，包含智能体类型和配置信息
     * @return ToolCollection 完整的工具集合，包含所有可用工具的统一接口
     * @throws 异常情况会记录日志但不会抛出，确保工具装配的健壮性
     */
    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {

        // 初始化工具集合容器
        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);

        // ============ 装配必备的文件操作工具 ============
        FileTool fileTool = new FileTool();
        fileTool.setAgentContext(agentContext);
        toolCollection.addTool(fileTool);

        // ============ 根据配置装配可选的内置工具 ============
        // 从配置文件读取默认工具列表，默认包含：search,code,report
        List<String> agentToolList = Arrays.asList(genieConfig.getMultiAgentToolListMap()
                .getOrDefault("default", "code,report,search").split(","));

        if (!agentToolList.isEmpty()) {
            // 条件性添加代码解释工具
            if (agentToolList.contains("code")) {
                CodeInterpreterTool codeTool = new CodeInterpreterTool();
                codeTool.setAgentContext(agentContext);
                toolCollection.addTool(codeTool);
            }

            // 条件性添加报告生成工具
            if (agentToolList.contains("report")) {
                ReportTool htmlTool = new ReportTool();
                htmlTool.setAgentContext(agentContext);
                toolCollection.addTool(htmlTool);
            }

            // 条件性添加深度搜索工具
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                deepSearchTool.setAgentContext(agentContext);
                toolCollection.addTool(deepSearchTool);
            }
        }

        // ============ 通过MCP协议集成外部工具 ============
        try {
            McpTool mcpTool = new McpTool();
            mcpTool.setAgentContext(agentContext);

            // 遍历配置的MCP服务器地址
            for (String mcpServer : genieConfig.getMcpServerUrlArr()) {
                // 获取MCP服务器提供的工具列表
                String listToolResult = mcpTool.listTool(mcpServer);

                // 检查服务器响应是否有效
                if (listToolResult.isEmpty()) {
                    log.error("{} mcp server {} invalid", agentContext.getRequestId(), mcpServer);
                    continue;
                }

                // 解析服务器响应
                JSONObject resp = JSON.parseObject(listToolResult);

                // 检查响应状态码
                if (resp.getIntValue("code") != 200) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }

                // 获取工具数据列表
                JSONArray data = resp.getJSONArray("data");
                if (data.isEmpty()) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }

                // 遍历注册每个MCP工具
                for (int i = 0; i < data.size(); i++) {
                    JSONObject tool = data.getJSONObject(i);

                    // 提取工具信息
                    String method = tool.getString("name");
                    String description = tool.getString("description");
                    String inputSchema = tool.getString("inputSchema");

                    // 注册MCP工具到工具集合
                    toolCollection.addMcpTool(method, description, inputSchema, mcpServer);
                }
            }
        } catch (Exception e) {
            // MCP工具集成失败，记录错误但不影响整体流程
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }

        // 返回完整的工具集合
        return toolCollection;
    }

    /**
     * 探活接口
     *
     * @return
     */
    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * 处理Agent流式增量查询请求，返回SSE事件流
     * 
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        return gptProcessService.queryMultiAgentIncrStream(params);
    }

}
