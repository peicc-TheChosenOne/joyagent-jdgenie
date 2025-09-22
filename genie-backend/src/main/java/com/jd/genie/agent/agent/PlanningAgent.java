package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import com.jd.genie.agent.dto.tool.ToolChoice;
import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.prompt.PlanningPrompt;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.common.PlanningTool;
import com.jd.genie.agent.util.FileUtil;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 规划代理 - 负责任务拆解与计划生成/更新
 * - 读取配置化的 system/next_step prompt
 * - 结合可用工具描述，产出结构化的计划（steps）
 * - 与 ExecutorAgent 协同迭代，直到 finish
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanningAgent extends ReActAgent {

    private List<ToolCall> toolCalls;           // 工具调用记录
    private Integer maxObserve;                 // 观测信息最大长度
    private PlanningTool planningTool = new PlanningTool(); // 内置规划工具
    private Boolean isColseUpdate;              // 是否关闭动态更新
    private String systemPromptSnapshot;        // systemPrompt 快照（带 {{files}} 占位）
    private String nextStepPromptSnapshot;      // nextStepPrompt 快照（带 {{files}} 占位）
    private String planId;                      // 计划ID

    public PlanningAgent(AgentContext context) {
        setName("planning");
        setDescription("An agent that creates and manages plans to solve tasks");
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class);

        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        String promptKey = "default";
        String nextPromptKey = "default";
        setSystemPrompt(genieConfig.getPlannerSystemPromptMap().getOrDefault(promptKey, PlanningPrompt.SYSTEM_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{sopPrompt}}", context.getSopPrompt()));
        setNextStepPrompt(genieConfig.getPlannerNextStepPromptMap().getOrDefault(nextPromptKey, PlanningPrompt.NEXT_STEP_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{sopPrompt}}", context.getSopPrompt()));

        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        setPrinter(context.printer);
        setMaxSteps(genieConfig.getPlannerMaxSteps());
        setLlm(new LLM(genieConfig.getPlannerModelName(), ""));

        setContext(context);
        // 是否关闭动态更新  1==1
        setIsColseUpdate("1".equals(genieConfig.getPlanningCloseUpdate()));

        // 初始化工具集合：将 planningTool 注入到可用工具
        availableTools.addTool(planningTool);
        planningTool.setAgentContext(context);
    }

    @Override
    public boolean think() {
        long startTime = System.currentTimeMillis();
        // 获取文件内容摘要，注入 Prompt
        String filesStr = FileUtil.formatFileInfo(context.getProductFiles(), false);
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr));
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));
        log.info("{} planer fileStr {}", context.getRequestId(), filesStr);

        // 若关闭动态更新Plan，则优先推进已有计划
        if (isColseUpdate) {
            if (Objects.nonNull(planningTool.getPlan())) {
                planningTool.stepPlan();
                return true;
            }
        }

        try {
            if (!getMemory().getLastMessage().getRole().equals(RoleType.USER)) {
                Message userMsg = Message.userMessage(getNextStepPrompt(), null);
                getMemory().addMessage(userMsg);
            }

            context.setStreamMessageType("plan_thought");
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    availableTools,
                    ToolChoice.AUTO, null, context.getIsStream(), 300
            );

            LLM.ToolCallResponse response = future.get();
            setToolCalls(response.getToolCalls());

            // 记录响应信息
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.send("plan_thought", response.getContent());
            }

            // 记录响应信息
            log.info("{} {}'s thoughts: {}", context.getRequestId(), getName(), response.getContent());
            log.info("{} {} selected {} tools to use", context.getRequestId(), getName(),
                    response.getToolCalls() != null ? response.getToolCalls().size() : 0);

            // 创建并添加助手消息
            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(response.getContent(), response.getToolCalls()) :
                    Message.assistantMessage(response.getContent(), null);

            getMemory().addMessage(assistantMsg);

        } catch (Exception e) {

            log.error("{} think error ", context.getRequestId(), e);
        }

        return true;
    }

    @Override
    public String act() {
        // 关闭了动态更新Plan，直接执行下一个task
        if (isColseUpdate) {
            // 第一次进来 不走这
            if (Objects.nonNull(planningTool.getPlan())) {
                return getNextTask();
            }
        }

        List<String> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        for (ToolCall toolCall : toolCalls) {
            String result = executeTool(toolCall);
            if (maxObserve != null) {
                result = result.substring(0, Math.min(result.length(), maxObserve));
            }
            results.add(result);

            // 添加工具响应到记忆
            if ("struct_parse".equals(llm.getFunctionCallType())) {
                String content = getMemory().getLastMessage().getContent();
                getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + result);
            } else { // function_call
                Message toolMsg = Message.toolMessage(
                        result,
                        toolCall.getId(),
                        null
                );
                getMemory().addMessage(toolMsg);
            }
        }

        // 上面假如有工具调用则 生成Plan对象
        if (Objects.nonNull(planningTool.getPlan())) {
            // 关闭动态更新 (isColseUpdate = true) 目前是关闭的
            if (isColseUpdate) {
                // 推进计划
                planningTool.stepPlan();
            }
            return getNextTask();
        }

        return String.join("\n\n", results);
    }


    /**
     * 获取下一个要执行的任务
     *
     * 这是一个智能的任务调度方法，根据当前计划的状态决定下一步行动：
     * 1. 检查所有任务是否已完成
     * 2. 如果全部完成，标记智能体结束并返回完成信号
     * 3. 如果有正在进行的任务，返回该任务继续执行
     * 4. 如果没有可执行任务，返回空字符串
     *
     * @return String
     *         - "finish": 所有任务完成
     *         - 具体任务内容: 当前正在进行的任务
     *         - "": 没有可执行任务
     */
    private String getNextTask() {
        // 步骤1：检查计划中所有任务是否都已完成
        // 初始化标志位，假设所有任务都已完成
        boolean allComplete = true;

        // 遍历所有任务状态，检查是否有未完成的任务
        for (String status : planningTool.getPlan().getStepStatus()) {
            if (!"completed".equals(status)) {
                // 发现有未完成的任务，设置标志位为false
                allComplete = false;
                break; // 找到一个未完成的任务就可以停止检查
            }
        }

        // 步骤2：处理所有任务都已完成的情况
        if (allComplete) {
            // 设置智能体状态为完成，表示整个计划执行完毕
            setState(AgentState.FINISHED);

            // 通过SSE发送完整的计划信息给前端
            printer.send("plan", planningTool.getPlan());

            // 返回完成标记，通知调用方所有任务已完成
            return "finish";
        }

        // 步骤3：处理有正在进行的任务的情况
        if (!planningTool.getPlan().getCurrentStep().isEmpty()) {
            // 设置智能体状态为完成（单个任务视角）
            setState(AgentState.FINISHED);

            // 获取当前正在进行的任务，可能包含多个子任务（用<sep>分隔）
            String[] currentSteps = planningTool.getPlan().getCurrentStep().split("<sep>");

            // 发送当前计划状态给前端
            printer.send("plan", planningTool.getPlan());

            // 遍历发送每个子任务详情给前端
            Arrays.stream(currentSteps).forEach(step -> printer.send("task", step));

            // 返回当前正在进行的任务内容
            return planningTool.getPlan().getCurrentStep();
        }

        // 步骤4：没有可执行任务的情况
        // 可能所有任务都处于not_started状态，或者plan为空
        return "";
    }

    @Override
    public String run(String request) {
        if (Objects.isNull(planningTool.getPlan())) {
            GenieConfig genieConfig = SpringContextHolder.getApplicationContext().getBean(GenieConfig.class);
            request = genieConfig.getPlanPrePrompt() + request;
        }
        return super.run(request);
    }
}