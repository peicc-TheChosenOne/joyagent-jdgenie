package com.jd.genie.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.enums.AutoBotsResultStatus;
import com.jd.genie.agent.enums.ResponseTypeEnum;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.handler.AgentResponseHandler;
import com.jd.genie.model.dto.AutoBotsResult;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.util.ChateiUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MultiAgentServiceImpl implements IMultiAgentService {
    @Autowired
    private GenieConfig genieConfig;
    @Autowired
    private Map<AgentType, AgentResponseHandler> handlerMap; // 按 AgentType 分发 UI 层增量事件的处理器

    @Override
    /**
     * UI入口：将前端的增量查询请求转为内部的 AgentRequest，并建立到 /AutoAgent 的SSE转发
     */
    public AutoBotsResult searchForAgentRequest(GptQueryReq gptQueryReq, SseEmitter sseEmitter) {
        AgentRequest agentRequest = buildAgentRequest(gptQueryReq);
        log.info("{} start handle Agent request: {}", gptQueryReq.getRequestId(),
                JSON.toJSONString(agentRequest, SerializerFeature.PrettyFormat));
        try {
            handleMultiAgentRequest(agentRequest, sseEmitter);
        } catch (Exception e) {
            log.error("{}, error in requestMultiAgent, deepThink: {}, errorMsg: {}", gptQueryReq.getRequestId(),
                    gptQueryReq.getDeepThink(), e.getMessage(), e);
            throw e;
        } finally {
            log.info("{}, agent.query.web.singleRequest end, requestId: {}", gptQueryReq.getRequestId(),
                    JSON.toJSONString(gptQueryReq, SerializerFeature.PrettyFormat));
        }

        return ChateiUtils.toAutoBotsResult(agentRequest, AutoBotsResultStatus.loading.name());
    }

    /**
     * 通过 OkHttp 建立到后端 /AutoAgent 的SSE连接，并逐行转发给前端
     */
    public void handleMultiAgentRequest(AgentRequest autoReq, SseEmitter sseEmitter) {
        // 记录请求开始时间，用于性能监控
        long startTime = System.currentTimeMillis();

        // 构建HTTP请求对象
        Request request = buildHttpRequest(autoReq);

        // 记录请求详情日志
        log.info("{} agentRequest:{}", autoReq.getRequestId(),
                JSON.toJSONString(request, SerializerFeature.PrettyFormat));

        // 构建OkHttp客户端，配置各种超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // 设置连接超时时间为 60 秒
                .readTimeout(genieConfig.getSseClientReadTimeout(), TimeUnit.SECONDS) // 设置读取超时时间为 60 秒
                .writeTimeout(1800, TimeUnit.SECONDS) // 设置写入超时时间为 60 秒
                .callTimeout(genieConfig.getSseClientConnectTimeout(), TimeUnit.SECONDS) // 设置调用超时时间为 60 秒
                .build();

        // 发起异步HTTP请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败时的错误处理
                log.error("onFailure {}", e.getMessage(), e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                // 初始化响应数据收集容器
                List<AgentResponse> agentRespList = new ArrayList<>();
                EventResult eventResult = new EventResult();

                // 获取响应体
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    log.error("{} auto agent empty response body", autoReq.getRequestId());
                    return;
                }

                try {
                    // 检查响应是否成功
                    if (!response.isSuccessful()) {
                        log.error("{}, response body is failed: {}", autoReq.getRequestId(), responseBody.string());
                        return;
                    }

                    // 创建流式读取器
                    String line;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream()));

                    // 逐行读取SSE数据流
                    while ((line = reader.readLine()) != null) {
                        // 仅处理以 data: 开头的 SSE 数据帧
                        if (!line.startsWith("data:")) {
                            continue;
                        }

                        // 提取数据内容
                        String data = line.substring(5);

                        // 检查是否为结束标记
                        if (data.equals("[DONE]")) {
                            log.info("{} data equals with [DONE] {}:", autoReq.getRequestId(), data);
                            break;
                        }

                        // 处理心跳数据
                        if (data.startsWith("heartbeat")) {
                            // 透传心跳到前端，保持连接活性
                            GptProcessResult result = buildHeartbeatData(autoReq.getRequestId());
                            sseEmitter.send(result);
                            // log.info("{} heartbeat-data: {}", autoReq.getRequestId(), data);
                            continue;
                        }

                        // log.info("{} recv from autocontroller: {}", autoReq.getRequestId(), data);
                        // 解析智能体响应数据
                        AgentResponse agentResponse = JSON.parseObject(data, AgentResponse.class);

                        // 根据智能体类型获取对应的处理器
                        AgentType agentType = AgentType.fromCode(autoReq.getAgentType());
                        AgentResponseHandler handler = handlerMap.get(agentType);

                        // 处理响应数据，组装前端可消费的数据结构
                        GptProcessResult result = handler.handle(autoReq, agentResponse, agentRespList, eventResult);
                        sseEmitter.send(result);

                        // 检查任务是否完成
                        if (result.isFinished()) {
                            // 记录任务执行时间
                            log.info("{} task total cost time:{}ms", autoReq.getRequestId(),System.currentTimeMillis() - startTime);
                            sseEmitter.complete();
                        }
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        });
    }

    /**
     * 构建到本服务 /AutoAgent 的 HTTP 请求
     */
    private Request buildHttpRequest(AgentRequest autoReq) {
        String reqId = autoReq.getRequestId();
        autoReq.setRequestId(autoReq.getRequestId());
        String url = "http://127.0.0.1:8080/AutoAgent";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                JSONObject.toJSONString(autoReq));
        autoReq.setRequestId(reqId);
        return new Request.Builder().url(url).post(body).build();
    }

    private GptProcessResult buildDefaultAutobotsResult(AgentRequest autoReq, String errMsg) {
        GptProcessResult result = new GptProcessResult();
        boolean isRouter = AgentType.ROUTER.getValue().equals(autoReq.getAgentType());
        if (isRouter) {
            result.setStatus("success");
            result.setFinished(true);
            result.setResponse(errMsg);
            result.setTraceId(autoReq.getRequestId());
        } else {
            result.setResultMap(new HashMap<>());
            result.setStatus("failed");
            result.setFinished(true);
            result.setErrorMsg(errMsg);
        }
        return result;
    }

    /**
     * 构建Agent请求对象
     * 将前端的GPT查询请求(GptQueryReq)转换为内部的Agent请求(AgentRequest)
     * 根据不同的参数设置选择不同的智能体类型和相应的提示词
     *
     * @param req 前端GPT查询请求对象，包含用户查询和相关参数
     * @return AgentRequest 转换后的智能体请求对象，包含所有必要的参数配置
     */
    private AgentRequest buildAgentRequest(GptQueryReq req) {
        // 创建新的Agent请求对象
        AgentRequest request = new AgentRequest();
        // 设置请求ID，使用前端传递的traceId作为唯一标识
        request.setRequestId(req.getTraceId());
        // 设置ERP标识（用户标识）
        request.setErp(req.getUser());
        // 设置查询内容
        request.setQuery(req.getQuery());
        // 根据深度思考参数选择智能体类型：
        // deepThink = 0：选择类型5（基础模式）
        // deepThink != 0：选择类型3（深度思考模式）
        request.setAgentType(req.getDeepThink() == 0 ? 5 : 3);
        // 根据智能体类型设置相应的SOP提示词
        // 类型3（深度思考）：使用genieSopPrompt
        // 类型5（基础模式）：使用空字符串
        request.setSopPrompt(request.getAgentType() == 3 ? genieConfig.getGenieSopPrompt() : "");
        // 根据智能体类型设置基础提示词
        // 类型5（基础模式）：使用genieBasePrompt
        // 类型3（深度思考）：使用空字符串
        request.setBasePrompt(request.getAgentType() == 5 ? genieConfig.getGenieBasePrompt() : "");
        // 设置流式输出模式为true，支持增量响应
        request.setIsStream(true);
        // 设置输出样式，直接使用前端传递的样式配置
        request.setOutputStyle(req.getOutputStyle());
        // 返回构建完成的Agent请求对象
        return request;
    }

    /**
     * 构建心跳数据包
     * 创建一个特殊的心跳响应包，用于维持SSE连接的活性
     * 心跳包不包含实际业务数据，只是为了防止连接超时
     *
     * @param requestId 请求ID，用于跟踪心跳数据包的归属
     * @return GptProcessResult 心跳数据包，包含基本的响应结构但无实际内容
     */
    private GptProcessResult buildHeartbeatData(String requestId) {
        // 创建GPT处理结果对象
        GptProcessResult result = new GptProcessResult();

        // 设置任务未完成状态（心跳包不是最终结果）
        result.setFinished(false);

        // 设置响应状态为成功
        result.setStatus("success");

        // 设置响应类型为文本
        result.setResponseType(ResponseTypeEnum.text.name());

        // 设置响应内容为空（心跳包无实际内容）
        result.setResponse("");

        // 设置完整响应内容为空
        result.setResponseAll("");

        // 设置使用时间为0（心跳不计入实际处理时间）
        result.setUseTimes(0);

        // 设置使用的token数量为0（心跳不消耗token）
        result.setUseTokens(0);

        // 设置请求ID，与原始请求保持一致
        result.setReqId(requestId);

        // 设置包类型为心跳
        result.setPackageType("heartbeat");

        // 设置为未加密状态
        result.setEncrypted(false);

        // 返回构建完成的心跳数据包
        return result;
    }
}
