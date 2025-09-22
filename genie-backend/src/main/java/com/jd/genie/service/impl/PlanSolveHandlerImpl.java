package com.jd.genie.service.impl;


import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ExecutorAgent;
import com.jd.genie.agent.agent.PlanningAgent;
import com.jd.genie.agent.agent.SummaryAgent;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.dto.TaskSummaryResult;
import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.AgentHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlanSolveHandlerImpl implements AgentHandlerService {

    @Autowired
    private GenieConfig genieConfig;


    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {
        // Plan + Execute 模式：先规划后执行，可并行执行多个子任务
        // 构建计划智能体
        PlanningAgent planning = new PlanningAgent(agentContext);

        // 构建执行器智能体
        ExecutorAgent executor = new ExecutorAgent(agentContext);

        // 构建通用总结智能体
        SummaryAgent summary = new SummaryAgent(agentContext);
        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", request.getQuery()));

        // 计划智能体 初次规划
        String planningResult = planning.run(agentContext.getQuery());

        // 迭代索引
        int stepIdx = 0;
        // 最大迭代次数
        int maxStepNum = genieConfig.getPlannerMaxSteps();

        // 不断循环直到最大次数
        while (stepIdx <= maxStepNum) {
            // 将规划结果拆解成可执行任务列表
            List<String> planningResults = Arrays.stream(planningResult.split("<sep>"))
                    .map(task -> "你的任务是：" + task)
                    .toList();
            String executorResult;

            // 执行前清除任务文件
            agentContext.getTaskProductFiles().clear();

             // 单任务串行执行
            if (planningResults.size() == 1) {
                log.info("单任务------");
                executorResult = executor.run(planningResults.get(0));
            } else {
                log.info("多任务------");
                // 多任务并行执行：每个子任务复制当前记忆，执行完毕合并回主执行器
                Map<String, String> tmpTaskResult = new ConcurrentHashMap<>();
                // 定义线程计数器
                CountDownLatch taskCount = ThreadUtil.getCountDownLatch(planningResults.size());
                // 记忆 条数
                int memoryIndex = executor.getMemory().size();
                // 创建从执行器副本，每个任务独立执行
                List<ExecutorAgent> slaveExecutors = new ArrayList<>();
                for (String task : planningResults) {
                    ExecutorAgent slaveExecutor = new ExecutorAgent(agentContext);
                    slaveExecutor.setState(executor.getState());
                    slaveExecutor.getMemory().addMessages(executor.getMemory().getMessages()); // 复制记忆
                    slaveExecutors.add(slaveExecutor);

                    // 并行执行每个任务
                    ThreadUtil.execute(() -> {
                        String taskResult = slaveExecutor.run(task);
                        tmpTaskResult.put(task, taskResult);
                        taskCount.countDown();
                    });
                }
                // 等待所有任务完成
                ThreadUtil.await(taskCount);

                // 合并从执行器的记忆到主执行器
                for (ExecutorAgent slaveExecutor : slaveExecutors) {
                    for (int i = memoryIndex; i < slaveExecutor.getMemory().size(); i++) {
                        executor.getMemory().addMessage(slaveExecutor.getMemory().get(i));
                    }
                    slaveExecutor.getMemory().clear();
                    executor.setState(slaveExecutor.getState());
                }
                executorResult = String.join("\n", tmpTaskResult.values());
            }
            // 基于执行结果继续迭代规划
            planningResult = planning.run(executorResult);

            if ("finish".equals(planningResult)) {
                //任务成功结束，总结任务
                TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());

                Map<String, Object> taskResult = new HashMap<>();
                taskResult.put("taskSummary", result.getTaskSummary());

                if (CollectionUtils.isEmpty(result.getFiles())) {
                    if (!CollectionUtils.isEmpty(agentContext.getProductFiles())) {
                        List<File> fileResponses = agentContext.getProductFiles();
                        // 过滤中间搜索结果文件
                        fileResponses.removeIf(file -> Objects.nonNull(file) && file.getIsInternalFile());
                        Collections.reverse(fileResponses);
                        taskResult.put("fileList", fileResponses);
                    }
                } else {
                    taskResult.put("fileList", result.getFiles());
                }

                agentContext.getPrinter().send("result", taskResult); // 输出最终结果


                break;
            }
            if (planning.getState() == AgentState.IDLE || executor.getState() == AgentState.IDLE) {
                agentContext.getPrinter().send("result", "达到最大迭代次数，任务终止。");
                break;
            }
            if (planning.getState() == AgentState.ERROR || executor.getState() == AgentState.ERROR) {
                agentContext.getPrinter().send("result", "任务执行异常，请联系管理员，任务终止。");
                break;
            }
            stepIdx++;
        }

        return "";
    }

    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType());
    }
}
