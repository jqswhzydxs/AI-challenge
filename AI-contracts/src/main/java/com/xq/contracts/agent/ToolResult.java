package com.xq.contracts.agent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An actual business query, including empty/error outcomes. Null is never an observed zero. */
public record ToolResult<T>(
    ToolStatus status,
    T data,
    String errorCode,
    Instant dataAsOf,
    List<String> warnings,
    String callId,
    String snapshotId) {
  public ToolResult {
    Objects.requireNonNull(status);
    Objects.requireNonNull(dataAsOf);
    Objects.requireNonNull(callId);
    Objects.requireNonNull(snapshotId);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    if (status == ToolStatus.ERROR && (errorCode == null || data != null)) {
      throw new IllegalArgumentException("An error must carry a code and no business data");
    }
  }

  public static <T> ToolResult<T> of(
      ToolStatus status, T data, String errorCode, List<String> warnings) {
    return new ToolResult<>(
        status,
        data,
        errorCode,
        Instant.now(),
        warnings,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString());
  }
}
