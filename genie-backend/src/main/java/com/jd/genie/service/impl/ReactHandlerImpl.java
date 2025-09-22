package com.jd.genie.service.impl;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.agent.ReActAgent;
import com.jd.genie.agent.agent.ReactImplAgent;
import com.jd.genie.agent.agent.SummaryAgent;
import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.dto.TaskSummaryResult;
import com.jd.genie.agent.enums.AgentType;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.service.AgentHandlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Component
public class ReactHandlerImpl implements AgentHandlerService {

    // ReAct 单智能体处理模式

    @Autowired
    private GenieConfig genieConfig;

    @Override
    public String handle(AgentContext agentContext, AgentRequest request) {
        // 1) 创建执行Agent与汇总Agent
        ReActAgent executor = new ReactImplAgent(agentContext);
        SummaryAgent summary = new SummaryAgent(agentContext);
        summary.setSystemPrompt(summary.getSystemPrompt().replace("{{query}}", request.getQuery()));

        // 2) 执行ReAct主循环（思考-行动-观察）
        executor.run(request.getQuery());
        // 3) 执行完成后做结果汇总，抽取最终答案与文件
        TaskSummaryResult result = summary.summaryTaskResult(executor.getMemory().getMessages(), request.getQuery());

        Map<String, Object> taskResult = new HashMap<>();
        taskResult.put("taskSummary", result.getTaskSummary());

        if (CollectionUtils.isEmpty(result.getFiles())) {
            // 若汇总未返回文件，则回退到上下文收集的产出文件（过滤中间产物）
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

        // 4) SSE 输出最终结果
        agentContext.getPrinter().send("result", taskResult);

        return "";
    }

    @Override
    public Boolean support(AgentContext agentContext, AgentRequest request) {
        return AgentType.REACT.getValue().equals(request.getAgentType());
    }
}
