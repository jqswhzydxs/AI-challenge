package com.xq.agent;

import com.xq.contracts.agent.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class HttpIndustrialEvidenceClient implements IndustrialEvidenceClient {
  private final CoreApiClient core;

  public HttpIndustrialEvidenceClient(CoreApiClient core) {
    this.core = core;
  }

  @Override
  public ToolResult<Map<String, Object>> query(String tool, Long id, LocalDate date) {
    var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    String group =
        List.of("evaluation", "conflicts", "jointOptimization").contains(tool)
            ? "optimization"
            : tool;
    String key = "evidence:" + group + ":" + id + ":" + date;
    EvidenceBundle bundle = (EvidenceBundle) request.getAttribute(key);
    if (bundle == null) {
      bundle =
          core.post(
              "/internal/v1/agent/evidence-snapshots",
              new EvidenceRequest(group, id, date),
              request.getHeader("Authorization"),
              EvidenceBundle.class);
      request.setAttribute(key, bundle);
    }
    request.setAttribute("evidenceBundle", bundle);
    ToolResult<Map<String, Object>> result = bundle.tools().get(tool);
    return result == null
        ? ToolResult.of(ToolStatus.ERROR, null, "MISSING_TOOL_EVIDENCE", List.of())
        : result;
  }
}
