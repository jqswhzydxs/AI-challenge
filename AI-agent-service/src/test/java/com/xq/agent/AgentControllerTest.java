package com.xq.agent;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.xq.common.result.Result;
import com.xq.contracts.agent.AgentAdviceVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentControllerTest {
  final AgentService agentService = mock(AgentService.class);
  final MockMvc agentMvc =
      MockMvcBuilders.standaloneSetup(new AgentController(agentService)).build();

  @Test
  void agentOptimizationExplainEndpointReturnsStructuredAdvice() throws Exception {
    // 验证新增 Agent 接口仍然遵循统一 Result 包装，并返回前端可直接展示的结构化字段。
    when(agentService.explainOptimization(eq(1L), any()))
        .thenReturn(
            Result.ok(
                AgentAdviceVO.builder()
                    .scene("OPTIMIZATION_EXPLAIN")
                    .aiEnabled(false)
                    .riskLevel("LOW")
                    .summary("协同优化结果已完成智能解读")
                    .keyMetrics(List.of("MAPE：3.55%"))
                    .build()));

    agentMvc
        .perform(
            post("/api/agent/optimize/1/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"demo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code", is(200)))
        .andExpect(jsonPath("$.data.scene", is("OPTIMIZATION_EXPLAIN")))
        .andExpect(jsonPath("$.data.riskLevel", is("LOW")));
  }
}
