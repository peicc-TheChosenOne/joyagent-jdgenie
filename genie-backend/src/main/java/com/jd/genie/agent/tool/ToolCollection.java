package com.jd.genie.agent.tool;

import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.dto.tool.McpToolInfo;
import com.jd.genie.agent.tool.mcp.McpTool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具集合类 - 管理可用的工具
 * 负责管理和执行各种工具调用，支持内置工具和MCP外部工具
 */
@Data
@Slf4j
public class ToolCollection {
    private Map<String, BaseTool> toolMap; // 内置工具映射
    private Map<String, McpToolInfo> mcpToolMap; // MCP工具映射
    private AgentContext agentContext; // 智能体上下文

    /**
     * 数字员工（工具到岗位名）
     * - 每个 task 可触发一次命名更新
     * - 并发情形下需额外处理（TODO）
     */
    private String currentTask; // 当前任务

    /**
     * {
     *     "file_tool": "数据记录员",
     *     "code_interpreter": "数据分析师",
     *     "deep_search": "信息调研员",
     *     "report_tool": "报告撰写专家"
     * }
     */
    private JSONObject digitalEmployees; // 数字员工映射

    public ToolCollection() {
        this.toolMap = new HashMap<>();
        this.mcpToolMap = new HashMap<>();
    }

    /** 添加内置工具 */
    public void addTool(BaseTool tool) {
        toolMap.put(tool.getName(), tool);
    }

    /** 获取内置工具 */
    public BaseTool getTool(String name) {
        return toolMap.get(name);
    }

    /** 添加 MCP 工具（远端声明式工具） */
    public void addMcpTool(String name, String desc, String parameters, String mcpServerUrl) {
        mcpToolMap.put(name, McpToolInfo.builder()
                .name(name)
                .desc(desc)
                .parameters(parameters)
                .mcpServerUrl(mcpServerUrl)
                .build());
    }

    /** 获取 MCP 工具定义 */
    public McpToolInfo getMcpTool(String name) {
        return mcpToolMap.get(name);
    }

    /**
     * 执行工具：优先内置 > MCP
     * 根据工具名称查找对应的工具实现并执行
     */
    public Object execute(String name, Object toolInput) {
        if (toolMap.containsKey(name)) {
            BaseTool tool = getTool(name);
            return tool.execute(toolInput);
        } else if (mcpToolMap.containsKey(name)) {
            McpToolInfo toolInfo = mcpToolMap.get(name);
            McpTool mcpTool = new McpTool();
            mcpTool.setAgentContext(agentContext);
            return mcpTool.callTool(toolInfo.getMcpServerUrl(), name, toolInput);
        } else {
            log.error("Error: Unknown tool {}", name);
        }
        return null;
    }

    /** 更新数字员工命名映射 */
    public void updateDigitalEmployee(JSONObject digitalEmployee) {
        if (digitalEmployee == null) {
            log.error("requestId:{} setDigitalEmployee: {}", agentContext.getRequestId(), digitalEmployee);
        }
        setDigitalEmployees(digitalEmployee);
    }

    /** 获取特定工具的数字员工名称 */
    public String getDigitalEmployee(String toolName) {
        if (StringUtils.isEmpty(toolName)) {
            return null;
        }

        if (digitalEmployees == null) {
            return null;
        }

        return (String) digitalEmployees.get(toolName);
    }
}
