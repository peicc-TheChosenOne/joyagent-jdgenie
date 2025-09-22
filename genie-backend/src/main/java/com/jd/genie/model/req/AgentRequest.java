package com.jd.genie.model.req;

import com.jd.genie.model.dto.FileInformation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后端调度入口请求模型（对应 /AutoAgent）
 * - query/agentType：用户问题与智能体模式
 * - messages：对话历史（可选）
 * - outputStyle：交付物格式（html/docs/table）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    private String requestId; // 请求ID
    private String erp; // 用户标识（企业账号）
    private String query; // 用户问题
    private Integer agentType; // 智能体类型（React/PlanSolve等）
    private String basePrompt; // 基础提示词（React使用）
    private String sopPrompt; // 规划SOP提示词（Plan使用）
    private Boolean isStream; // 是否流式输出
    private List<Message> messages; // 对话历史
    private String outputStyle; // 交付物产出格式：html/docs/table

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role; // 角色：user/assistant
        private String content; // 内容
        private String commandCode; // 指令码（可选）
        private List<FileInformation> uploadFile; // 本次上传文件
        private List<FileInformation> files; // 历史文件

    }
}
