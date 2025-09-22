package com.jd.genie.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.genie.agent.enums.ResponseTypeEnum;
import com.jd.genie.model.multi.EventMessage;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jd.genie.model.constant.Constants.RUNNING;
import static com.jd.genie.model.constant.Constants.SUCCESS;

/**
 * 基础智能体响应处理器
 * 提供智能体响应处理的通用基础功能
 * 处理不同类型的智能体响应消息，构建前端可消费的增量结果
 *
 * @author 系统生成
 */
@Slf4j
@Component
public class BaseAgentResponseHandler {

    /**
     * 构建增量处理结果
     * 根据智能体响应构建前端可消费的增量数据包
     * 支持多种消息类型：计划思考、计划、任务等
     *
     * @param request 智能体请求对象，包含请求的基本信息
     * @param eventResult 事件结果对象，用于跟踪整个处理流程的状态
     * @param agentResponse 智能体响应对象，包含后端智能体的原始响应数据
     * @return GptProcessResult 处理后的增量结果，包含前端需要的格式化数据
     */
    protected GptProcessResult buildIncrResult(AgentRequest request, EventResult eventResult, AgentResponse agentResponse) {
        // 创建GPT处理结果对象
        GptProcessResult streamResult = new GptProcessResult();

        // 设置响应类型为文本
        streamResult.setResponseType(ResponseTypeEnum.text.name());

        // 根据智能体响应是否完成设置状态
        streamResult.setStatus(agentResponse.getFinish() ? SUCCESS : RUNNING);

        // 设置完成标志
        streamResult.setFinished(agentResponse.getFinish());

        // 如果是结果类型的消息，设置响应内容
        if ("result".equals(agentResponse.getMessageType())) {
            streamResult.setResponse(agentResponse.getResult());
            streamResult.setResponseAll(agentResponse.getResult());
        }

        // 设置请求ID
        streamResult.setReqId(request.getRequestId());

        // 提取智能体类型信息
        String agentType = (Objects.nonNull(agentResponse.getResultMap())
                && agentResponse.getResultMap().containsKey("agentType"))
                ? String.valueOf(agentResponse.getResultMap().get("agentType")) : null;

        // 初始化结果映射
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("agentType", agentType);
        resultMap.put("multiAgent", new HashMap<>());
        resultMap.put("eventData", new HashMap<>());

        // 构建增量事件消息
        EventMessage message = EventMessage.builder()
                .messageId(agentResponse.getMessageId())
                .build();

        // 检查是否为最终消息
        boolean isFinal = Boolean.TRUE.equals(agentResponse.getIsFinal());

        // 检查是否为过滤的最终消息
        boolean isFilterFinal = (Objects.nonNull(agentResponse.getResultMap())
                && agentResponse.getMessageType().equals("deep_search")
                && agentResponse.getResultMap().containsKey("messageType")
                && agentResponse.getResultMap().get("messageType").equals("extend"));

        // 根据消息类型进行不同的处理逻辑
        switch (agentResponse.getMessageType()) {
            case "plan_thought":
                // 处理计划思考类型的消息
                message.setMessageType(agentResponse.getMessageType());
                message.setMessageOrder(eventResult.getAndIncrOrder(agentResponse.getMessageType()));
                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));

                // 如果是最终消息且结果映射中不包含plan_thought，则添加进去
                if (isFinal && !eventResult.getResultMap().containsKey("plan_thought")) {
                    eventResult.getResultMap().put("plan_thought", agentResponse.getPlanThought());
                }
                break;

            case "plan":
                // 处理计划类型的消息
                if (eventResult.isInitPlan()) {
                    // 计划生成阶段
                    message.setMessageType(agentResponse.getMessageType());
                    message.setMessageOrder(1);
                    message.setResultMap(agentResponse.getPlan());

                    // 如果是最终消息，保存计划到结果映射
                    if (isFinal) {
                        eventResult.getResultMap().put("plan", agentResponse.getPlan());
                    }
                } else {
                    // 计划更新阶段，需要关联任务
                    message.setTaskId(eventResult.getTaskId());
                    message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                    message.setMessageType("task");
                    message.setMessageOrder(1);
                    message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));

                    // 如果是最终消息，设置子任务结果
                    if (isFinal) {
                        eventResult.setResultMapSubTask(message.getResultMap());
                    }
                }
                break;

            case "task":
                // 处理任务类型的消息
                message.setTaskId(eventResult.renewTaskId());
                message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                message.setMessageType(agentResponse.getMessageType());
                message.setMessageOrder(1);
                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));

                // 如果是最终消息，创建任务列表并设置
                if (isFinal) {
                    List<Object> task = new ArrayList<>();
                    task.add(message.getResultMap());
                    eventResult.setResultMapTask(task);
                }
                break;

            default:
                // 处理其他类型的消息（默认任务处理）
                message.setTaskId(eventResult.getTaskId());
                message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                message.setMessageType("task");
                message.setMessageOrder(1);

                // 如果是流式任务消息类型，设置特定的顺序
                if (eventResult.getStreamTaskMessageType().contains(agentResponse.getMessageType())) {
                    String orderKey = eventResult.getTaskId() + ":" + agentResponse.getMessageType();
                    message.setMessageOrder(eventResult.getAndIncrOrder(orderKey));
                }

                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));

                // 如果是最终消息且不是过滤的最终消息，设置子任务结果
                if (isFinal && !isFilterFinal) {
                    eventResult.setResultMapSubTask(message.getResultMap());
                }
                break;
        }

        // 将增量数据添加到结果映射中
        resultMap.put("eventData", JSONObject.parseObject(JSON.toJSONString(message)));
        streamResult.setResultMap(resultMap);

        // 返回构建完成的增量结果
        return streamResult;
    }
}