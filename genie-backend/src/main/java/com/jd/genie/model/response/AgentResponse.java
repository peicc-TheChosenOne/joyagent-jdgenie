package com.jd.genie.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后端→前端 的统一SSE事件模型
 * - messageType：事件类型（plan/task/tool/html/.../result）
 * - resultMap/result：结构化/纯文本结果
 * - plan/toolResult：结构化计划与工具结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private String requestId; // 请求ID
    private String messageId; // 消息ID
    private Boolean isFinal; // 是否最终消息（用于多路合成）
    private String messageType; // 消息类型：plan/task/tool/html/.../result
    private String digitalEmployee; // 数字员工（工具岗位名）
    private String messageTime; // 发送时间戳（毫秒）
    private String planThought; // 规划阶段的思考
    private Plan plan; // 规划结构
    private String task; // 当前任务标题
    private String taskSummary; // 任务总结
    private String toolThought; // 工具调用前的思考
    private ToolResult toolResult; // 工具调用结果
    private Map<String, Object> resultMap; // 结构化结果
    private String result; // 文本结果
    private Boolean finish; // 是否结束
    private Map<String, String> ext; // 扩展字段

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Plan {
        private String title; // 计划标题
        private List<String> stages; // 阶段/大步骤
        private List<String> steps; // 子任务（“执行顺序X. 标题：描述”或纯文本）
        private List<String> stepStatus; // 子任务状态
        private List<String> notes; // 备注
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult {
        private String toolName; // 工具名
        private Map<String, Object> toolParam;// 工具入参（结构化）
        private String toolResult; // 工具结果（文本/JSON）
    }

    public static Plan formatSteps(Plan plan) {
        Plan newplan = Plan.builder()
                .title(plan.title)
                .steps(new ArrayList<>())
                .stages(new ArrayList<>())
                .stepStatus(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();
        Pattern pattern = Pattern.compile("执行顺序(\\d+)\\.\\s?([\\w\\W]*)\\s?[：:](.*)");
        for (int i = 0; i < plan.getSteps().size(); i++) {
            newplan.getStepStatus().add(plan.getStepStatus().get(i));
            newplan.getNotes().add(plan.getNotes().get(i));

            String step = plan.getSteps().get(i);
            Matcher matcher = pattern.matcher(step);
            if (matcher.find()) {
                newplan.getSteps().add(matcher.group(3).trim());
                newplan.getStages().add(matcher.group(2).trim());
            } else {
                newplan.getSteps().add(step);
                newplan.getStages().add("");
            }
        }
        return newplan;
    }
}
