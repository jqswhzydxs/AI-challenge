package com.xq.agent;

import com.xq.common.result.Result;
import com.xq.contracts.agent.AgentAdviceVO;
import com.xq.contracts.agent.AgentChatDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI Agent 控制器.
 *
 * <p>只负责暴露 HTTP 接口，具体的大模型调用、工具调用和规则兜底逻辑都在 Service 层完成。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

  private final AgentService agentService;

  /** 通用问答入口，适合前端单独做一个智能助手页面。 */
  @PostMapping("/chat")
  public Result<AgentAdviceVO> chat(@RequestBody(required = false) AgentChatDTO dto) {
    return agentService.chat(dto);
  }

  /** 协同优化页面的“AI 解读”按钮可以调用该接口。 */
  @PostMapping("/optimize/{optimizeId}/explain")
  public Result<AgentAdviceVO> explainOptimization(
      @PathVariable("optimizeId") Long optimizeId,
      @RequestBody(required = false) AgentChatDTO dto) {
    return agentService.explainOptimization(optimizeId, dto);
  }

  /** 能源页面的“AI 诊断”按钮可以调用该接口。 */
  @PostMapping("/energy-plan/{energyPlanId}/diagnose")
  public Result<AgentAdviceVO> diagnoseEnergyPlan(
      @PathVariable("energyPlanId") Long energyPlanId,
      @RequestBody(required = false) AgentChatDTO dto) {
    return agentService.diagnoseEnergyPlan(energyPlanId, dto);
  }

  /** Dashboard 或报表页面的“生成 AI 日报”按钮可以调用该接口。 */
  @PostMapping("/report/daily")
  public Result<AgentAdviceVO> generateDailyReport(
      @RequestBody(required = false) AgentChatDTO dto) {
    return agentService.generateDailyReport(dto);
  }
}
