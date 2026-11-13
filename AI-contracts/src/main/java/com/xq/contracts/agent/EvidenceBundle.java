package com.xq.contracts.agent;

import java.time.LocalDate;
import java.util.Map;

public record EvidenceBundle(
    String snapshotId,
    String subjectType,
    String subjectId,
    String planVersion,
    LocalDate businessDate,
    String timezone,
    String contentHash,
    Map<String, ToolResult<Map<String, Object>>> tools) {}
