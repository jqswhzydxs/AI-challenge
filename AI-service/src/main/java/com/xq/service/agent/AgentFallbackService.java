package com.xq.service.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xq.model.dto.AgentChatDTO;
import com.xq.model.vo.AgentAdviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 规则兜底服务.
 * <p>
 * 当大模型未启用、网络不可用或模型返回结构异常时，根据已有业务指标生成稳定建议，
 * 保证演示现场不会因为外部模型服务问题导致接口不可用。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentFallbackService {

    private final IndustrialAgentTools tools;

    /** 根据请求中的业务主键自动路由到协同优化解读、能源诊断或日报生成。 */
    public AgentAdviceVO chat(String scene, AgentChatDTO dto) {
        if (dto != null && dto.getOptimizeId() != null) {
            return explainOptimization(dto.getOptimizeId(), scene, dto);
        }
        if (dto != null && dto.getEnergyPlanId() != null) {
            return diagnoseEnergyPlan(dto.getEnergyPlanId(), scene, dto);
        }
        if ("DAILY_REPORT".equals(scene)) {
            return dailyReport(scene, dto);
        }
        return base(scene, dto)
                .summary("当前未指定具体业务对象，Agent 已就绪。可传入 optimizeId 或 energyPlanId 获取优化解释和能源诊断。")
                .riskLevel("LOW")
                .keyMetrics(List.of("支持协同优化解释", "支持能源方案诊断", "支持日报生成"))
                .problems(List.of("缺少可分析的业务主键"))
                .suggestions(List.of("在请求中补充 optimizeId 或 energyPlanId", "前端可在协同优化和能源页面增加 AI 解读按钮"))
                .evidence(List.of("后端已暴露 /api/agent 系列接口"))
                .nextActions(List.of("选择一个优化结果或能源方案发起分析"))
                .build();
    }

    /** 基于协同优化评价指标和冲突数量，生成可解释的风险等级与优化建议。 */
    public AgentAdviceVO explainOptimization(Long optimizeId, String scene, AgentChatDTO dto) {
        String evaluationJson = tools.queryJointOptimizationEvaluation(optimizeId);
        String conflictsJson = tools.queryOptimizationConflicts(optimizeId);
        JSONObject evaluation = parseObject(evaluationJson);
        JSONArray conflicts = parseArray(conflictsJson);
        int conflictCount = conflicts != null ? conflicts.size() : intValue(evaluation, "conflictCount");
        BigDecimal mape = decimal(evaluation, "mape");
        BigDecimal er = firstDecimal(evaluation, "er", "executeRate");

        // 规则兜底只使用真实业务查询结果，不编造指标。
        List<String> problems = new ArrayList<>();
        if (conflictCount > 0) {
            problems.add("存在 " + conflictCount + " 个约束冲突，需要优先核查设备出力或时段约束");
        }
        if (mape.compareTo(new BigDecimal("10")) > 0) {
            problems.add("MAPE 为 " + mape + "%，仿真误差偏高，建议复核负荷预测与实测数据口径");
        }
        if (er.compareTo(new BigDecimal("90")) < 0) {
            problems.add("方案可执行率为 " + er + "%，存在落地执行风险");
        }
        if (problems.isEmpty()) {
            problems.add("未发现明显高风险项，方案整体可解释性较好");
        }

        String risk = conflictCount > 0 || mape.compareTo(new BigDecimal("15")) > 0 ? "HIGH"
                : er.compareTo(new BigDecimal("90")) < 0 ? "MEDIUM" : "LOW";
        return base(scene, dto)
                .summary("协同优化结果已完成智能解读：重点关注降本率、降耗率、MAPE、ER 和约束冲突。")
                .riskLevel(risk)
                .keyMetrics(List.of(
                        "降本率：" + valueText(evaluation, "costReductionRate") + "%",
                        "降耗率：" + valueText(evaluation, "energyReductionRate") + "%",
                        "MAPE：" + valueText(evaluation, "mape") + "%",
                        "ER/可执行率：" + er + "%",
                        "约束冲突数：" + conflictCount
                ))
                .problems(problems)
                .suggestions(List.of(
                        "优先处理冲突时段的锅炉出力上下限约束",
                        "将高峰电价时段的非关键负荷向谷段迁移",
                        "对 MAPE 偏高时段补充实时数据校准，提升预测可信度"
                ))
                .evidence(List.of("optimizeId=" + optimizeId, "评价指标来自协同优化结果表", "冲突列表来自 constraint_conflict"))
                .nextActions(List.of("查看冲突明细", "对比 Pareto 前沿方案", "重新生成约束更严格的协同优化任务"))
                .rawAnswer(evaluationJson)
                .build();
    }

    /** 基于能源方案明细计算峰均比、成本和碳排，给出削峰填谷建议。 */
    public AgentAdviceVO diagnoseEnergyPlan(Long energyPlanId, String scene, AgentChatDTO dto) {
        String planJson = tools.queryEnergyPlan(energyPlanId);
        JSONObject plan = parseObject(planJson);
        BigDecimal totalCost = decimal(plan, "totalEnergyCost");
        BigDecimal peakElectricity = decimal(plan, "peakElectricity");
        BigDecimal avgElectricity = decimal(plan, "avgElectricity");
        BigDecimal totalCarbon = decimal(plan, "totalCarbonEmissionTco2");
        BigDecimal peakRatio = avgElectricity.compareTo(BigDecimal.ZERO) > 0
                ? peakElectricity.divide(avgElectricity, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String risk = peakRatio.compareTo(new BigDecimal("1.50")) > 0 ? "MEDIUM" : "LOW";
        return base(scene, dto)
                .summary("能源运行方案已完成诊断：重点关注总成本、峰值负荷、平均负荷、碳排和峰谷平衡。")
                .riskLevel(risk)
                .keyMetrics(List.of(
                        "总能源成本：" + totalCost + " 元",
                        "峰值电耗：" + peakElectricity + " kWh",
                        "平均电耗：" + avgElectricity + " kWh",
                        "峰均比：" + peakRatio,
                        "总碳排：" + totalCarbon + " tCO2"
                ))
                .problems(peakRatio.compareTo(new BigDecimal("1.50")) > 0
                        ? List.of("峰均比较高，负荷曲线存在削峰空间")
                        : List.of("负荷曲线相对平稳，暂未发现明显峰值风险"))
                .suggestions(List.of(
                        "将可平移任务安排到谷电时段，降低外购电成本",
                        "对峰值小时设置负荷上限预警",
                        "结合 MPC 实时调控结果滚动修正锅炉和汽轮机出力"
                ))
                .evidence(List.of("energyPlanId=" + energyPlanId, "明细数据来自 energy_plan_detail", "成本数据来自 energy_plan"))
                .nextActions(List.of("查看能源明细曲线", "导出日报", "触发下一轮协同优化评价"))
                .rawAnswer(planJson)
                .build();
    }

    /** 汇总报表服务输出，生成适合首页展示或答辩讲解的日报摘要。 */
    public AgentAdviceVO dailyReport(String scene, AgentChatDTO dto) {
        String reportJson = tools.queryDailyReportSummary();
        return base(scene, dto)
                .summary("已生成生产-能源协同优化日报摘要，可用于系统首页、日报导出或答辩展示。")
                .riskLevel("LOW")
                .keyMetrics(List.of("覆盖优化效果", "覆盖能耗分析", "覆盖能源趋势", "覆盖碳减排统计"))
                .problems(List.of("日报为聚合视角，异常原因仍需结合具体方案和冲突明细追踪"))
                .suggestions(List.of("日报中突出降本、降耗、减碳三类指标", "对异常日补充协同优化结果解释"))
                .evidence(List.of("数据来自 reportService 聚合报表接口"))
                .nextActions(List.of("前端增加日报生成入口", "支持按日期范围筛选日报"))
                .rawAnswer(reportJson)
                .build();
    }

    /** 构建兜底结果的公共字段。 */
    private AgentAdviceVO.AgentAdviceVOBuilder base(String scene, AgentChatDTO dto) {
        return AgentAdviceVO.builder()
                .sessionId(dto != null ? dto.getSessionId() : null)
                .scene(scene)
                .source("RULE_FALLBACK")
                .aiEnabled(false)
                .generatedAt(LocalDateTime.now().toString());
    }

    /** JSON 解析失败时返回空对象，避免兜底分析再次抛异常。 */
    private JSONObject parseObject(String json) {
        try {
            return JSON.parseObject(json);
        } catch (RuntimeException e) {
            return new JSONObject();
        }
    }

    /** JSON 数组解析失败时返回空数组，适用于冲突列表为空或查询异常的场景。 */
    private JSONArray parseArray(String json) {
        try {
            return JSON.parseArray(json);
        } catch (RuntimeException e) {
            return new JSONArray();
        }
    }

    private BigDecimal firstDecimal(JSONObject object, String first, String second) {
        BigDecimal firstValue = decimal(object, first);
        return firstValue.compareTo(BigDecimal.ZERO) != 0 ? firstValue : decimal(object, second);
    }

    private BigDecimal decimal(JSONObject object, String key) {
        if (object == null || object.get(key) == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(object.get(key).toString());
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }

    private int intValue(JSONObject object, String key) {
        if (object == null || object.get(key) == null) {
            return 0;
        }
        try {
            return Integer.parseInt(object.get(key).toString());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String valueText(JSONObject object, String key) {
        Object value = object != null ? object.get(key) : null;
        return value != null ? value.toString() : "0";
    }
}
