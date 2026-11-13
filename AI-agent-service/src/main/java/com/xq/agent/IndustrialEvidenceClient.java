package com.xq.agent;

import com.xq.contracts.agent.ToolResult;
import java.time.LocalDate;
import java.util.Map;

/** The only business-data boundary used by the Agent; HTTP replaces Local after migration. */
public interface IndustrialEvidenceClient {
  ToolResult<Map<String, Object>> query(String tool, Long subjectId, LocalDate date);
}
