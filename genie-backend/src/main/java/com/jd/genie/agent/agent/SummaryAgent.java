package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.TaskSummaryResult;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SummaryAgent extends BaseAgent {
    // 任务执行过程总结与最终交付物提取
    private String requestId;
    private Integer messageSizeLimit;
    public static final String logFlag = "summaryTaskResult";

    public SummaryAgent(AgentContext context) {
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class);
        setSystemPrompt(genieConfig.getSummarySystemPrompt());

        setContext(context);
        setRequestId(context.getRequestId());
        setLlm(new LLM(context.getAgentType() == 3 ? genieConfig.getPlannerModelName() : genieConfig.getReactModelName(), ""));
        setMessageSizeLimit(genieConfig.getMessageSizeLimit());
    }

    /**
     * 执行单个步骤
     */
    public String step() {
        return "";
    }

    // 构造文件信息：生成 文件名:描述 列表（过滤内部文件）
    private String createFileInfo() {
        List<File> files = context.getProductFiles();
        if (CollectionUtils.isEmpty(files)) {
            log.info("requestId: {} no files found in context", requestId);
            return "";
        }
        log.info("requestId: {} {} product files:{}", requestId, logFlag, files);
        String result = files.stream()
                .filter(file -> !file.getIsInternalFile()) // 过滤内部文件
                .map(file -> file.getFileName() + " : " + file.getDescription())
                .collect(Collectors.joining("\n"));

        log.info("requestId: {} generated file info: {}", requestId, result);
        return result;
    }

    // 构造用于总结的系统提示内容（注入任务历史、文件列表与原始问题）
    private String formatSystemPrompt(String taskHistory, String query) {
        String systemPrompt = getSystemPrompt();
        if (systemPrompt == null) {
            log.error("requestId: {} {} systemPrompt is null", requestId, logFlag);
            throw new IllegalStateException("System prompt is not configured");
        }

        // 替换占位符
        return systemPrompt
                .replace("{{taskHistory}}", taskHistory)
                .replace("{{fileNameDesc}}", createFileInfo())
                .replace("{{query}}", query);
    }

    // 构造用户消息（此处简化为 userMessage，便于统一 ask 接口）
    private Message createSystemMessage(String content) {
        return Message.userMessage(content, null); // 如果需要更复杂的消息构建，可扩展
    }

    /**
     * 解析LLM响应：按 $$$ 切分为 概要 + 文件名列表，并与上下文文件匹配
     */
    private TaskSummaryResult parseLlmResponse(String llmResponse) {
        if (StringUtils.isEmpty(llmResponse)) {
            log.error("requestId: {} pattern matcher failed for response is null", requestId);
        }

        // 已经成功收集并整理了OpenAI官方所有模型的列表和详细定价信息，包括GPT系列、DALL-E系列、Whisper系列和文本嵌入模型，
        // 并生成了完整的综合分析报告，所有数据基于2025年最新信息。详情可查看报告文件。$$$OpenAI官方模型及价格分析报告2025年.html
        String[] parts1 = llmResponse.split("\\$\\$\\$");
        if (parts1.length < 2) {
            return TaskSummaryResult.builder().taskSummary(parts1[0]).build();
        }

        String summary = parts1[0];
        String fileNames = parts1[1];

        List<File> files = context.getProductFiles();
        if (!CollectionUtils.isEmpty(files)) {
            Collections.reverse(files);
        } else {
            log.error("requestId: {} llmResponse:{} productFile list is empty", requestId, llmResponse);
            // 文件列表为空，交付物中不显示文件
            return TaskSummaryResult.builder().taskSummary(summary).build();
        }
        List<File> product = new ArrayList<>();
        String[] items = fileNames.split("、");
        for (String item : items) {
            String trimmedItem = item.trim();
            if (StringUtils.isBlank(trimmedItem)) {
                continue;
            }
            for (File file : files) {
                if (item.contains(file.getFileName().trim())) {
                    log.info("requestId: {} add file:{}", requestId, file);
                    product.add(file);
                    break;
                }
            }
        }

        return TaskSummaryResult.builder().taskSummary(summary).files(product).build();
    }


    // 总结任务
    public TaskSummaryResult summaryTaskResult(List<Message> messages, String query) {
        long startTime = System.currentTimeMillis();
        // 1. 参数校验（可选）
        if (CollectionUtils.isEmpty(messages) || StringUtils.isEmpty(query)) {
            log.warn("requestId: {}  summaryTaskResult messages:{}  or query:{} is empty", requestId, messages, query);
            return TaskSummaryResult.builder().taskSummary("").build();
        }

        try {
            // 2. 构建系统消息（提取为独立方法）
            log.info("requestId: {} summaryTaskResult: messages:{}", requestId, messages.size());
            StringBuilder sb = new StringBuilder();
            for (Message message : messages) {
                String content = message.getContent();
                if (content != null && content.length() > getMessageSizeLimit()) {
                    log.info("requestId: {} message truncate,{}", requestId, message);
                    content = content.substring(0, getMessageSizeLimit());
                }
                sb.append(String.format("role:%s content:%s\n", message.getRole(), content));
            }
            String formattedPrompt = formatSystemPrompt(sb.toString(), query);
            Message userMessage = createSystemMessage(formattedPrompt);

            // 3. 调用LLM并处理结果
            CompletableFuture<String> summaryFuture = getLlm().ask(
                    context,
                    Collections.singletonList(userMessage),
                    Collections.emptyList(),
                    false,
                    0.01);

            // 5. 解析响应
            String llmResponse = summaryFuture.get();
            log.info("requestId: {} summaryTaskResult: {}", requestId, llmResponse);

            return parseLlmResponse(llmResponse);
        } catch (Exception e) {
            log.error("requestId: {} in summaryTaskResult failed,", requestId, e);

            return TaskSummaryResult.builder().taskSummary("任务执行失败，请联系管理员！").build();
        }
    }
}