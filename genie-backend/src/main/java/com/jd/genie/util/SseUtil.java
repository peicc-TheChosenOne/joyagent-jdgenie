package com.jd.genie.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE (Server-Sent Events) 工具类
 * 用于创建和管理服务器发送事件连接
 *
 * @author 系统生成
 */
@Slf4j
public class SseUtil {

    /**
     * 构建SSE发射器
     * 创建一个配置完整的SseEmitter对象，包含错误处理、超时处理和完成处理
     *
     * @param timeout 超时时间（毫秒），如果为null则表示无超时限制
     * @param requestId 请求ID，用于日志追踪
     * @return 配置好的SseEmitter对象
     */
    public static SseEmitter build(Long timeout, String requestId) {
        // 创建SSE发射器，指定超时时间
        SseEmitter sseEmitter = new SseEmitterUTF8(timeout);

        // 设置错误处理回调
        sseEmitter.onError((err)-> {
            // 记录错误日志，包含错误信息和请求ID
            log.error("SseSession Error, msg: {}, requestId: {}", err.getMessage(), requestId);
            // 以错误状态完成SSE连接
            sseEmitter.completeWithError(err);
        });

        // 设置超时处理回调
        sseEmitter.onTimeout(() -> {
            // 记录超时日志
            log.info("SseSession Timeout, requestId : {}", requestId);
            // 正常完成SSE连接
            sseEmitter.complete();
        });

        // 设置完成处理回调
        sseEmitter.onCompletion(() -> {
            // 记录连接完成日志
            log.info("SseSession Completion, requestId : {}", requestId);
        });

        // 返回配置好的SSE发射器
        return sseEmitter;
    }
}