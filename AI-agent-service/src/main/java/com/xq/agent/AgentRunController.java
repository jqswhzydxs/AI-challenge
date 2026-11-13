package com.xq.agent;

import com.xq.common.result.Result;
import com.xq.contracts.agent.VerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/runs")
public class AgentRunController {
  private final AgentRunRepository repository;

  public AgentRunController(AgentRunRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/{runId}")
  public Result<Map<String, Object>> find(@PathVariable String runId, HttpServletRequest request) {
    return Result.ok(repository.find(runId, identity(request).userId()));
  }

  @PostMapping("/{runId}/feedback")
  public Result<Void> feedback(
      @PathVariable String runId,
      @RequestBody Map<String, String> body,
      HttpServletRequest request) {
    repository.feedback(runId, identity(request).userId(), body.get("correction"));
    return Result.ok();
  }

  private VerifiedIdentity identity(HttpServletRequest request) {
    return (VerifiedIdentity) request.getAttribute("verifiedIdentity");
  }
}
