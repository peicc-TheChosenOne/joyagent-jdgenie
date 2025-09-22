package com.jd.genie.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 面向前端/上层的增量返回结果模型
 * - status/finished：整体状态
 * - response/responseAll：增量/全量文本
 * - resultMap：结构化结果（如文件、任务等）
 * - traceId/reqId：会话/请求标识
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoBotsResult {
    private String status;                 // 状态：success/failed 等
    private String response = "";         // 增量内容回复（SSE单帧文本）
    private String responseAll = "";      // 全量内容回复（聚合文本）
    private boolean finished;              // 是否结束（SSE是否结束）
    private long useTimes;                 // 耗时（毫秒）
    private long useTokens;                // 模型token消耗
    private Map<String, Object> resultMap; // 结构化输出结果（文件、计划等）
    private String responseType = "markdown"; // 响应内容类型：markdown/html/文本
    private String traceId;                // 会话ID（服务端生成）
    private String reqId;                  // 请求ID（客户端传入/透传）
}
