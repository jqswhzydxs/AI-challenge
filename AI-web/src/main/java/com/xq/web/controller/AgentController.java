package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.AgentChatDTO;
import com.xq.model.vo.AgentAdviceVO;
import com.xq.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI Agent 控制器.
 * <p>
 * 只负责暴露 HTTP 接口，具体的大模型调用、工具调用和规则兜底逻辑都在 Service 层完成。
 * </p>
 */
@Tag(name = "AI Agent", description = "生产-能源协同优化智能助手")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** 通用问答入口，适合前端单独做一个智能助手页面。 */
    @Operation(summary = "业务智能问答", description = "根据问题、场景和业务主键调用 Spring AI Agent 生成结构化建议")
    @PostMapping("/chat")
    public Result<AgentAdviceVO> chat(@RequestBody(required = false) AgentChatDTO dto) {
        return agentService.chat(dto);
    }

    /** 协同优化页面的“AI 解读”按钮可以调用该接口。 */
    @Operation(summary = "协同优化结果智能解读", description = "根据协同优化 ID 解读降本、降耗、MAPE、ER 和约束冲突")
    @PostMapping("/optimize/{optimizeId}/explain")
    public Result<AgentAdviceVO> explainOptimization(
            @Parameter(description = "协同优化 ID", required = true, example = "1")
            @PathVariable("optimizeId") Long optimizeId,
            @RequestBody(required = false) AgentChatDTO dto) {
        return agentService.explainOptimization(optimizeId, dto);
    }

    /** 能源页面的“AI 诊断”按钮可以调用该接口。 */
    @Operation(summary = "能源方案智能诊断", description = "根据能源方案 ID 诊断成本、峰值负荷、碳排和削峰填谷空间")
    @PostMapping("/energy-plan/{energyPlanId}/diagnose")
    public Result<AgentAdviceVO> diagnoseEnergyPlan(
            @Parameter(description = "能源方案 ID", required = true, example = "1")
            @PathVariable("energyPlanId") Long energyPlanId,
            @RequestBody(required = false) AgentChatDTO dto) {
        return agentService.diagnoseEnergyPlan(energyPlanId, dto);
    }

    /** Dashboard 或报表页面的“生成 AI 日报”按钮可以调用该接口。 */
    @Operation(summary = "生成优化日报", description = "汇总优化效果、能耗分析、趋势和碳减排指标，生成日报摘要")
    @PostMapping("/report/daily")
    public Result<AgentAdviceVO> generateDailyReport(@RequestBody(required = false) AgentChatDTO dto) {
        return agentService.generateDailyReport(dto);
    }
}
