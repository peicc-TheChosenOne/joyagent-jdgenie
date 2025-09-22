package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.File;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.tool.ToolCollection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 智能体上下文对象
 *
 * 智能体执行任务时的核心上下文容器，包含了任务执行所需的所有信息：
 * - 请求标识和会话管理
 * - 用户查询和任务描述
 * - 输出器和工具集合
 * - 文件资源管理
 * - 提示词配置
 *
 * 通过Builder模式构建，支持链式调用和可选参数设置
 *
 * @author 系统生成
 */
@Data
@Builder
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    // ==================== 请求与会话管理 ====================

    /**
     * 请求唯一标识
     * 用于全链路追踪和日志记录，确保每个请求可追踪
     */
    String requestId;

    /**
     * 会话标识
     * 支持多轮对话场景，相同会话的请求共享上下文
     */
    String sessionId;

    // ==================== 任务内容 ====================

    /**
     * 用户原始查询
     * 用户输入的原始问题或指令，经过预处理但保持原始意图
     */
    String query;

    /**
     * 当前执行的子任务
     * 智能体拆分后的具体执行任务，可能为空表示初始状态
     */
    String task;

    // ==================== 输出与工具 ====================

    /**
     * 输出器接口
     * 负责将智能体执行结果输出到指定目标
     * 支持SSE流式输出、控制台输出等多种输出方式
     */
    Printer printer;

    /**
     * 工具集合
     * 智能体可使用的所有工具的集合
     * 包括文件操作、代码执行、搜索等各种工具
     */
    ToolCollection toolCollection;

    // ==================== 环境信息 ====================

    /**
     * 日期和环境信息
     * 当前系统时间和运行环境相关信息
     * 用于任务执行时的上下文感知
     */
    String dateInfo;

    // ==================== 文件资源管理 ====================

    /**
     * 全局产品文件列表
     * 智能体现在有执行过程中产生的所有文件
     * 用于文件共享和历史记录
     */
    List<File> productFiles;

    /**
     * 当前任务产品文件列表
     * 当前正在执行的任务所产生的文件
     * 任务完成后可合并到全局文件列表
     */
    List<File> taskProductFiles;

    // ==================== 流式输出配置 ====================

    /**
     * 是否启用流式输出
     * true: 实时流式输出思考过程和结果
     * false: 等待完整结果后一次性输出
     */
    Boolean isStream;

    /**
     * 流式消息类型
     * 指定流式输出的消息类型，如思考过程、工具调用等
     */
    String streamMessageType;

    // ==================== 提示词配置 ====================

    /**
     * SOP提示词
     * 标准操作流程提示词，用于指导智能体的执行策略
     * 通常用于复杂任务的标准化处理流程
     */
    String sopPrompt;

    /**
     * 基础提示词
     * 基础的系统提示词，用于定义智能体的基本行为和能力
     * 适用于常规任务的处理
     */
    String basePrompt;

    // ==================== 智能体类型 ====================

    /**
     * 智能体类型标识
     * 用于路由到对应的处理器
     * 例如：1-REACT智能体, 2-PLAN_SOLVE智能体, 3-ROUTER等
     */
    Integer agentType;
}