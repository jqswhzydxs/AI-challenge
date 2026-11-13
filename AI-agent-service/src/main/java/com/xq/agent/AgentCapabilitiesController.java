package com.xq.agent;

import com.xq.common.result.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentCapabilitiesController {
  private final ObjectProvider<ChatClient.Builder> builder;
  @Value("${agent.llm.enabled:false}") private boolean enabled;

  @GetMapping("/capabilities")
  public Result<Map<String, Object>> capabilities() {
    boolean configured = enabled && builder.getIfAvailable() != null;
    return Result.ok(Map.of("modelEnabled", configured, "mode", configured ? "MODEL_WITH_VALIDATION" : "RULE_ONLY",
        "description", configured ? "模型已启用；仅在证据完整且事实校验通过后采用结果" : "模型未启用，使用规则分析"));
  }
}
