package com.xq.model.dto;

import lombok.Data;

import java.util.Map;

/**
 * 工业优化 Agent 请求参数.
 * <p>
 * 前端可以只传问题，也可以携带协同优化 ID、能源方案 ID 等业务主键，
 * 后端会据此选择合适的分析场景和业务工具。
 * </p>
 */
@Data
public class AgentChatDTO {

    /** 会话 ID，用于前端区分多轮问答上下文。 */
    private String sessionId;

    /** 分析场景，如 GENERAL_CHAT / OPTIMIZATION_EXPLAIN / ENERGY_PLAN_DIAGNOSE / DAILY_REPORT。 */
    private String scene;

    /** 用户输入的问题或补充要求。 */
    private String question;

    /** 协同优化结果 ID，用于触发优化结果解读。 */
    private Long optimizeId;

    /** 能源运行方案 ID，用于触发能源方案诊断。 */
    private Long energyPlanId;

    /** 预留扩展上下文，便于前端后续传页面筛选条件或补充指标。 */
    private Map<String, Object> context;
}
