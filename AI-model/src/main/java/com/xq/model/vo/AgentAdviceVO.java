package com.xq.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工业优化 Agent 结构化返回结果.
 * <p>
 * 不直接返回一整段自然语言，而是拆成摘要、指标、问题、建议和证据，
 * 方便前端用卡片、列表或抽屉展示，也便于答辩时说明“结构化输出”能力。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAdviceVO {

    /** 会话 ID，透传请求中的 sessionId。 */
    private String sessionId;

    /** 当前分析场景。 */
    private String scene;

    /** 结果来源：Spring AI Tool Calling 或规则兜底。 */
    private String source;

    /** 是否真正调用了大模型。 */
    private Boolean aiEnabled;

    /** 风险等级：LOW / MEDIUM / HIGH。 */
    private String riskLevel;

    /** 一句话总结，用于页面顶部展示。 */
    private String summary;

    /** 核心指标，例如降本率、降耗率、MAPE、ER、碳排等。 */
    private List<String> keyMetrics;

    /** Agent 识别出的主要问题。 */
    private List<String> problems;

    /** 面向生产-能源优化的操作建议。 */
    private List<String> suggestions;

    /** 建议所依据的数据来源或业务证据。 */
    private List<String> evidence;

    /** 建议用户下一步执行的动作。 */
    private List<String> nextActions;

    /** 原始补充信息，主要用于调试或展示模型/兜底返回。 */
    private String rawAnswer;

    /** 生成时间。 */
    private String generatedAt;
}
