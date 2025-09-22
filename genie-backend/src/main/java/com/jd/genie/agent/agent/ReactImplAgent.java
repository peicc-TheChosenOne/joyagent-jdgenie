package com.jd.genie.agent.agent;

import com.alibaba.fastjson.JSON;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import com.jd.genie.agent.dto.tool.ToolChoice;
import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.prompt.ToolCallPrompt;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.util.FileUtil;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.response.AgentResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct 实现代理
 * - 以 ReAct 思考-行动-观察循环驱动工具调用
 * - 根据 react 提示词模板构造 system/next_step
 * - 支持流式输出和工具调用
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReactImplAgent extends ReActAgent {

    private List<ToolCall> toolCalls; // 当前步骤的工具调用列表
    private Integer maxObserve; // 观察结果的最大长度限制
    private String systemPromptSnapshot; // 系统提示词快照
    private String nextStepPromptSnapshot; // 下一步提示词快照

    public ReactImplAgent(AgentContext context) {
        setName("react");
        setDescription("an agent that can execute tool calls.");
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class);

        // 构建工具描述字符串
        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        // 构造系统提示词和下一步提示词
        String promptKey = "default";
        String nextPromptKey = "default";

        setSystemPrompt(genieConfig.getReactSystemPromptMap().getOrDefault(promptKey, ToolCallPrompt.SYSTEM_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt()));
        setNextStepPrompt(
                genieConfig.getReactNextStepPromptMap().getOrDefault(nextPromptKey, ToolCallPrompt.NEXT_STEP_PROMPT)
                        .replace("{{tools}}", toolPrompt.toString())
                        .replace("{{query}}", context.getQuery())
                        .replace("{{date}}", context.getDateInfo())
                        .replace("{{basePrompt}}", context.getBasePrompt()));

        // 保存提示词快照，用于后续动态更新
        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        // 初始化智能体配置
        setPrinter(context.printer);
        setMaxSteps(genieConfig.getReactMaxSteps());
        setLlm(new LLM(genieConfig.getReactModelName(), ""));
        setContext(context);

        // 初始化工具集合
        availableTools = context.getToolCollection();
        setDigitalEmployeePrompt(genieConfig.getDigitalEmployeePrompt());
    }

    @Override
    public boolean think() {
        // 获取文件内容摘要，注入到提示词中
        String filesStr = FileUtil.formatFileInfo(context.getProductFiles(), true);
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr));
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));

        // 构造用户消息（如果不是用户消息）
        if (!getMemory().getLastMessage().getRole().equals(RoleType.USER)) {
            Message userMsg = Message.userMessage(getNextStepPrompt(), null);
            getMemory().addMessage(userMsg);
        }
        try {
            // 获取带工具选项的响应（可流式）
            context.setStreamMessageType("tool_thought");

            // 调用LLM获取思考和工具调用
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(
                    context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    availableTools,
                    ToolChoice.AUTO, null, context.getIsStream(), 300);

            LLM.ToolCallResponse response = future.get();

            setToolCalls(response.getToolCalls());

            // 记录响应信息（非流式场景下回传思考）
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.send("tool_thought", response.getContent());

            }

            // 创建并添加助手消息
            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty()
                    && !"struct_parse".equals(llm.getFunctionCallType())
                            ? Message.fromToolCalls(response.getContent(), response.getToolCalls())
                            : Message.assistantMessage(response.getContent(), null);
            getMemory().addMessage(assistantMsg);

        } catch (Exception e) {

            log.error("{} react think error", context.getRequestId(), e);
            getMemory().addMessage(Message.assistantMessage(
                    "Error encountered while processing: " + e.getMessage(), null));
            setState(AgentState.FINISHED);
            return false;
        }

        return true;
    }

    @Override
    public String act() {
        // 如果没有工具调用，直接返回最后的消息内容
        if (toolCalls.isEmpty()) {
            setState(AgentState.FINISHED);
            return getMemory().getLastMessage().getContent();
        }

        // 执行所有工具调用
        Map<String, String> toolResults = executeTools(toolCalls);
        List<String> results = new ArrayList<>();
        for (ToolCall command : toolCalls) {
            String result = toolResults.get(command.getId());

            // 发送工具执行结果（排除某些内部工具）
            if (!Arrays.asList("code_interpreter", "report_tool", "file_tool", "deep_search")
                    .contains(command.getFunction().getName())) {
                String toolName = command.getFunction().getName();
                printer.send("tool_result", AgentResponse.ToolResult.builder()
                        .toolName(toolName)
                        .toolParam(JSON.parseObject(command.getFunction().getArguments(), Map.class))
                        .toolResult(result)
                        .build(), null);
            }

            // 截取结果长度（如果设置了maxObserve）
            if (maxObserve != null) {
                result = result.substring(0, Math.min(result.length(), maxObserve));
            }

            // 添加工具响应到记忆
            if ("struct_parse".equals(llm.getFunctionCallType())) {
                String content = getMemory().getLastMessage().getContent();
                getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + result);
            } else { // function_call
                Message toolMsg = Message.toolMessage(
                        result,
                        command.getId(),
                        null);
                getMemory().addMessage(toolMsg);
            }
            results.add(result);
        }

        return String.join("\n\n", results);
    }

    @Override
    public String run(String request) {
        return super.run(request);
    }

}
