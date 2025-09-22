package com.jd.genie.service.impl;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.service.IGptProcessService;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.util.ChateiUtils;
import com.jd.genie.util.SseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.TimeUnit;

/**
 * GPT处理服务实现类
 * 实现面向UI的增量流式GPT查询接口
 *
 * @author 系统生成
 */
@Slf4j
@Service
public class GptProcessServiceImpl implements IGptProcessService {

    /**
     * 多智能体服务接口
     * 实际负责转发请求到 /AutoAgent 接口并处理SSE回传
     */
    @Autowired
    private IMultiAgentService multiAgentService;

    /**
     * 面向UI的增量流接口实现
     * 处理GPT查询请求，构建必要的参数和超时设置，
     * 然后将请求转发给多智能体服务进行处理
     *
     * @param req GPT查询请求对象，包含用户查询和相关参数
     * @return SseEmitter SSE发射器，用于向客户端推送增量响应
     */
    @Override
    public SseEmitter queryMultiAgentIncrStream(GptQueryReq req) {
        // 设置超时时间：将1小时转换为毫秒数 (3600000毫秒)
        long timeoutMillis = TimeUnit.HOURS.toMillis(1);

        // 设置默认用户标识为 "genie"
        req.setUser("genie");

        // 设置深度思考参数，默认值为0（如果为空）
        req.setDeepThink(req.getDeepThink() == null ? 0: req.getDeepThink());

        // 生成跟踪ID：traceId = user + sessionId + requestId
        String traceId = ChateiUtils.getRequestId(req);

        // 将生成的traceId设置到请求对象中
        req.setTraceId(traceId);

        // 使用SseUtil构建SSE发射器，传入超时时间和traceId
        final SseEmitter emitter = SseUtil.build(timeoutMillis, req.getTraceId());

        // 调用多智能体服务处理请求，传入请求对象和发射器
        multiAgentService.searchForAgentRequest(req, emitter);

        // 记录请求处理日志
        log.info("queryMultiAgentIncrStream GptQueryReq request:{}", req);

        // 返回SSE发射器给调用方
        return emitter;
    }
}