package com.jd.genie.agent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.ThreadUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 代理基类
 * - 统一管理：名称、描述、提示词、可用工具、记忆、LLM、上下文、状态与步数
 * - 提供 run(step循环) 与 updateMemory 等核心能力
 */
@Slf4j
@Data
@Accessors(chain = true)
public abstract class BaseAgent {

    // 基本信息
    private String name;                    // 智能体名称
    private String description;             // 智能体描述
    private String systemPrompt;            // 系统提示词
    private String nextStepPrompt;          // 下一步提示词

    // 核心组件
    public ToolCollection availableTools = new ToolCollection();   // 可用工具集合
    private Memory memory = new Memory();   // 对话记忆
    protected LLM llm;                      // 大语言模型
    protected AgentContext context;         // 执行上下文

    // 执行控制
    private AgentState state = AgentState.IDLE;  // 当前状态
    private int maxSteps = 10;                   // 最大执行步数
    private int currentStep = 0;                // 当前步数
    private int duplicateThreshold = 2;

    // 输出组件
    Printer printer;                        // 输出器(SSE等)

    // 数字员工命名提示词
    private String digitalEmployeePrompt;


    /**
     * 执行单个步骤（由子类实现具体逻辑）
     */
    public abstract String step();


    /**
     * 模板方法模式
     * 主循环：按步执行，直到达到最大步数或状态变为 FINISHED
     */
    public String run(String query) {
        // 初始化状态
        setState(AgentState.IDLE);

        // 添加用户查询到记忆
        if (!query.isEmpty()) {
            updateMemory(RoleType.USER, query, null);
        }
        // 执行循环
        List<String> results = new ArrayList<>();
        try {
            while (currentStep < maxSteps && state != AgentState.FINISHED) {
                currentStep++;
                log.info("{} {} Executing step {}/{}", context.getRequestId(), getName(), currentStep, maxSteps);
                String stepResult = step();  // 调用子类的具体实现
                results.add(stepResult);
            }

            if (currentStep >= maxSteps) {
                currentStep = 0;
                state = AgentState.IDLE;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
        } catch (Exception e) {
            state = AgentState.ERROR; // 将异常透出给上层流程
            throw e;
        }

        return results.isEmpty() ? "No steps executed" : results.get(results.size() - 1);
    }

    /**
     * 更新代理记忆：按角色追加一条消息（支持图像）
     */
    public void updateMemory(RoleType role, String content, String base64Image, Object... args) {
        Message message;
        switch (role) {
            case USER:
                message = Message.userMessage(content, base64Image);
                break;
            case SYSTEM:
                message = Message.systemMessage(content, base64Image);
                break;
            case ASSISTANT:
                message = Message.assistantMessage(content, base64Image);
                break;
            case TOOL:
                message = Message.toolMessage(content, (String) args[0], base64Image);
                break;
            default:
                throw new IllegalArgumentException("Unsupported role type: " + role);
        }
        memory.addMessage(message);
    }

    // 工具执行
    public String executeTool(ToolCall command) {
        if (command == null || command.getFunction() == null || command.getFunction().getName() == null) {
            return "Error: Invalid function call format";
        }

        String name = command.getFunction().getName();
        try {
            // 解析参数
            ObjectMapper mapper = new ObjectMapper();
            Object args = mapper.readValue(command.getFunction().getArguments(), Object.class);

            // 执行工具
            Object result = availableTools.execute(name, args);
            log.info("{} execute tool: {} {} result {}", context.getRequestId(), name, args, result);
            // 格式化结果
            if (Objects.nonNull(result)) {
                return (String) result;
            }
        } catch (Exception e) {
            log.error("{} execute tool {} failed ", context.getRequestId(), name, e);
        }
        return "Tool" + name + " Error.";
    }

    /**
     * 并发执行多个工具调用命令并返回执行结果
     *
     * @param commands 工具调用命令列表
     * @return 返回工具执行结果映射，key为工具ID，value为执行结果
     */
    public Map<String, String> executeTools(List<ToolCall> commands) {
        Map<String, String> result = new ConcurrentHashMap<>();
        CountDownLatch taskCount = ThreadUtil.getCountDownLatch(commands.size());
        for (ToolCall tooCall : commands) {
            ThreadUtil.execute(() -> {
                String toolResult = executeTool(tooCall);
                result.put(tooCall.getId(), toolResult);
                taskCount.countDown();
            });
        }
        ThreadUtil.await(taskCount);
        return result;
    }



}