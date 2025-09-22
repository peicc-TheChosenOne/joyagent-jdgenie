package com.jd.genie.agent.printer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.util.StringUtil;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SSE打印器实现类
 * 负责通过Server-Sent Events (SSE)协议向客户端发送智能体响应消息
 * 支持多种消息类型：工具思考、任务执行、计划制定、结果输出等
 *
 * @author 系统生成
 */
@Slf4j
@Setter
public class SSEPrinter implements Printer {
    private SseEmitter emitter; // SSE发射器，用于发送消息到客户端
    private AgentRequest request; // 原始请求对象
    private Integer agentType; // 智能体类型，用于标识消息来源

    /**
     * 构造函数
     * 初始化SSE打印器，绑定SSE发射器和请求上下文
     *
     * @param emitter SSE发射器，用于向客户端推送消息
     * @param request 智能体请求对象，包含请求的基本信息
     * @param agentType 智能体类型标识，用于区分不同类型的智能体
     */
    public SSEPrinter(SseEmitter emitter, AgentRequest request, Integer agentType) {
        this.emitter = emitter;
        this.request = request;
        this.agentType = agentType;
    }

    /**
     * 发送消息到客户端（完整参数版本）
     * 统一封装后端事件为AgentResponse对象，通过SSE发送给前端
     * 支持多种消息类型，根据类型设置不同的响应字段
     *
     * @param messageId 消息唯一标识，如果为null则自动生成
     * @param messageType 消息类型，如tool_thought、task、result等
     * @param message 消息内容对象，不同类型有不同的数据结构
     * @param digitalEmployee 数字员工标识，可为空
     * @param isFinal 是否为最终消息标记
     */
    @Override
    public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        try {
            // 如果消息ID为空，生成唯一标识
            if (Objects.isNull(messageId)) {
                messageId = StringUtil.getUUID(); // 生成唯一消息ID
            }

            // 调试日志（已注释）
            // log.info("{} sse send {} {} {}", request.getRequestId(), messageType,
            // message, digitalEmployee);

            // 判断是否为最终结果（result类型表示任务完成）
            boolean finish = "result".equals(messageType);

            // 初始化结果映射，包含智能体类型信息
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("agentType", agentType);

            // 构建AgentResponse响应对象
            AgentResponse response = AgentResponse.builder()
                    .requestId(request.getRequestId())
                    .messageId(messageId)
                    .messageType(messageType)
                    .messageTime(String.valueOf(System.currentTimeMillis()))
                    .resultMap(resultMap)
                    .finish(finish)
                    .isFinal(isFinal)
                    .build();

            // 设置数字员工标识（如果有）
            if (!StringUtils.isEmpty(digitalEmployee)) {
                response.setDigitalEmployee(digitalEmployee);
            }

            // 根据消息类型设置不同的响应字段
            switch (messageType) {
                case "tool_thought":
                    // 工具思考内容
                    response.setToolThought((String) message);
                    break;

                case "task":
                    // 任务描述，移除执行顺序前缀
                    response.setTask(((String) message).replaceAll("^执行顺序(\\d+)\\.\\s?", ""));
                    break;

                case "task_summary":
                    // 任务总结信息
                    if (message instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskSummary = (Map<String, Object>) message;
                        Object summary = taskSummary.get("taskSummary");
                        response.setResultMap(taskSummary);
                        response.setTaskSummary(summary != null ? summary.toString() : null);
                    } else {
                        log.error("ssePrinter task_summary format is illegal");
                    }
                    break;

                case "plan_thought":
                    // 计划思考内容
                    response.setPlanThought((String) message);
                    break;

                case "plan":
                    // 执行计划，格式化步骤信息
                    AgentResponse.Plan plan = new AgentResponse.Plan();
                    BeanUtils.copyProperties(message, plan);
                    response.setPlan(AgentResponse.formatSteps(plan));
                    break;

                case "tool_result":
                    // 工具执行结果
                    response.setToolResult((AgentResponse.ToolResult) message);
                    break;

                case "browser":
                case "code":
                case "html":
                case "markdown":
                case "ppt":
                case "file":
                case "knowledge":
                case "deep_search":
                    // 各种输出格式的结果，以JSON格式存储
                    response.setResultMap(JSON.parseObject(JSON.toJSONString(message)));
                    response.getResultMap().put("agentType", agentType);
                    break;

                case "agent_stream":
                    // 智能体流式输出结果
                    response.setResult((String) message);
                    break;

                case "result":
                    // 最终结果，支持字符串、Map等多种格式
                    if (message instanceof String) {
                        response.setResult((String) message);
                    } else if (message instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> taskResult = (Map<String, Object>) message;
                        Object summary = taskResult.get("taskSummary");
                        response.setResultMap(taskResult);
                        response.setResult(summary != null ? summary.toString() : null);
                    } else {
                        // 其他格式转为Map处理
                        Map<String, Object> taskResult = JSON
                                .parseObject(JSON.toJSONString(message));
                        response.setResultMap(taskResult);
                        response.setResult(taskResult.get("taskSummary").toString());
                    }
                    response.getResultMap().put("agentType", agentType);
                    break;

                default:
                    // 未知消息类型，不做特殊处理
                    break;
            }

            // 通过SSE发射器发送响应到客户端
            emitter.send(response);

        } catch (Exception e) {
            // 发送失败时记录错误日志
            log.error("sse send error ", e);
        }
    }

    /**
     * 发送消息（简化版本，包含数字员工标识）
     *
     * @param messageType 消息类型
     * @param message 消息内容
     * @param digitalEmployee 数字员工标识
     */
    @Override
    public void send(String messageType, Object message, String digitalEmployee) {
        send(null, messageType, message, digitalEmployee, true);
    }

    /**
     * 发送消息（最简版本）
     *
     * @param messageType 消息类型
     * @param message 消息内容
     */
    @Override
    public void send(String messageType, Object message) {
        send(null, messageType, message, null, true);
    }

    /**
     * 发送消息（指定消息ID和是否最终消息）
     *
     * @param messageId 消息ID
     * @param messageType 消息类型
     * @param message 消息内容
     * @param isFinal 是否为最终消息
     */
    @Override
    public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        send(messageId, messageType, message, null, isFinal);
    }

    /**
     * 关闭SSE连接
     * 通知客户端数据传输完成，释放连接资源
     */
    @Override
    public void close() {
        emitter.complete();
    }

    /**
     * 更新智能体类型
     * 动态修改当前使用的智能体类型标识
     *
     * @param agentType 新的智能体类型枚举
     */
    @Override
    public void updateAgentType(AgentType agentType) {
        this.agentType = agentType.getValue();
    }
}