package com.jd.genie.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptQueryReq {
    private String query; // 用户问题
    private String sessionId; // 会话ID
    private String requestId; // 请求ID（可由前端传入）
    private Integer deepThink; // 0: React ；非0: Plan+Execute
    /**
     * 前端传入交付物格式：html(网页模式）,docs(文档模式）， table(表格模式）
     */
    private String outputStyle; // 交付物格式
    private String traceId; // 服务端生成的traceId（返回时回传）
    private String user; // 用户标识（用于鉴权/画像等）
}
