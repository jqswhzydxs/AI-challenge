package com.xq.service;

import com.xq.common.result.Result;
import com.xq.model.dto.AgentChatDTO;
import com.xq.model.vo.AgentAdviceVO;

/**
 * 工业优化 Agent 服务接口.
 * <p>
 * 对外统一提供通用问答、协同优化解读、能源方案诊断和日报生成能力。
 * Controller 只依赖该接口，具体是否调用大模型由实现类根据配置决定。
 * </p>
 */
public interface AgentService {

    /** 通用 Agent 问答入口，可根据 scene 和业务主键自动选择分析模式。 */
    Result<AgentAdviceVO> chat(AgentChatDTO dto);

    /** 解读协同优化结果，重点分析降本率、降耗率、MAPE、ER 和约束冲突。 */
    Result<AgentAdviceVO> explainOptimization(Long optimizeId, AgentChatDTO dto);

    /** 诊断能源运行方案，重点分析成本、负荷峰值、碳排和削峰填谷空间。 */
    Result<AgentAdviceVO> diagnoseEnergyPlan(Long energyPlanId, AgentChatDTO dto);

    /** 汇总报表指标，生成适合页面展示或答辩讲解的日报摘要。 */
    Result<AgentAdviceVO> generateDailyReport(AgentChatDTO dto);
}
