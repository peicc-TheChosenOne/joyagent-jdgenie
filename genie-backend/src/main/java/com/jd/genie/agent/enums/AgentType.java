package com.jd.genie.agent.enums;

/**
 * 智能体类型枚举
 * 定义系统中支持的不同智能体执行模式
 * 每种类型对应不同的处理策略和执行流程
 * 
 * 使用场景：
 * - 根据业务需求选择合适的智能体类型
 * - 通过 AgentHandlerFactory 路由到对应的处理器
 * - 支持扩展新的智能体类型
 */
public enum AgentType {

    /**
     * 综合型智能体 (COMPREHENSIVE)
     * - 综合运用多种策略的通用智能体
     * - 适用于复杂、多样化的任务场景
     * - 能够灵活适应不同的执行需求
     */
    COMPREHENSIVE(1),

    /**
     * 工作流型智能体 (WORKFLOW)
     * - 基于预定义工作流执行任务
     * - 按照固定步骤和规则处理业务流程
     * - 适用于标准化、流程化的业务场景
     */
    WORKFLOW(2),

    /**
     * 规划-解决型智能体 (PLAN_SOLVE)
     * - 先进行任务规划，再执行具体步骤
     * - 支持多任务并行执行和迭代优化
     * - 适用于需要规划和分解的复杂任务
     * - 处理器：PlanSolveHandlerImpl
     */
    PLAN_SOLVE(3),

    /**
     * 路由型智能体 (ROUTER)
     * - 根据任务特征智能路由到合适的处理单元
     * - 充当任务分发的决策者角色
     * - 适用于需要智能分发的场景
     */
    ROUTER(4),

    /**
     * ReAct型智能体 (REACT)
     * - 基于"思考-行动-观察"循环的推理模式
     * - 通过工具调用和反馈逐步解决问题
     * - 适用于需要动态推理和工具使用的场景
     * - 处理器：ReactHandlerImpl
     * -
     */
    REACT(5);

    /**
     * 智能体类型的整数值标识
     * 用于数据库存储、API传输和配置管理
     */
    private final Integer value;

    /**
     * 构造函数
     * 
     * @param value 智能体类型的整数值标识
     */
    AgentType(Integer value) {
        this.value = value;
    }

    /**
     * 获取智能体类型的整数值
     * 
     * @return Integer 对应的整数标识
     */
    public Integer getValue() {
        return value;
    }

    /**
     * 根据整数值获取对应的智能体类型
     * 
     * @param value 智能体类型的整数标识
     * @return AgentType 对应的智能体类型枚举值
     * @throws IllegalArgumentException 当传入无效的整数值时抛出
     */
    public static AgentType fromCode(int value) {
        for (AgentType type : AgentType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid AgentType code: " + value);
    }
}
