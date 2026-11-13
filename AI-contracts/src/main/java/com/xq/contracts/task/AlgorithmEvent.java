package com.xq.contracts.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record AlgorithmEvent(
    String eventId,
    String eventType,
    int schemaVersion,
    String requestId,
    String taskId,
    String jobId,
    Integer attempt,
    long eventSeq,
    LocalDate businessDate,
    String inputRef,
    String inputSha256,
    String outputRef,
    String outputSha256,
    String algorithmVersion,
    String errorCode,
    boolean retryable,
    String traceparent,
    Instant occurredAt) {
  public void validate() {
    if (schemaVersion != 1
        || !Set.of(
                "AlgorithmRequested.v1",
                "AlgorithmRunning.v1",
                "AlgorithmSucceeded.v1",
                "AlgorithmFailed.v1",
                "AlgorithmCancelRequested.v1",
                "AlgorithmCanceled.v1")
            .contains(eventType)) throw new IllegalArgumentException("UNKNOWN_PROTOCOL_VERSION");
    UUID.fromString(eventId);
    UUID.fromString(requestId);
    if (taskId == null || !taskId.matches("[0-9]{1,20}"))
      throw new IllegalArgumentException("INVALID_TASK_ID");
    if (occurredAt == null || eventSeq < 0) throw new IllegalArgumentException("INVALID_EVENT");
    if ("AlgorithmRequested.v1".equals(eventType)
        && (businessDate == null
            || inputRef == null
            || inputSha256 == null
            || !inputSha256.matches("[a-f0-9]{64}")
            || algorithmVersion == null))
      throw new IllegalArgumentException("INVALID_INPUT_MANIFEST");
    if ("AlgorithmSucceeded.v1".equals(eventType)
        && (attempt == null
            || attempt < 1
            || outputRef == null
            || outputSha256 == null
            || !outputSha256.matches("[a-f0-9]{64}")))
      throw new IllegalArgumentException("INVALID_RESULT");
  }

  public AlgorithmEvent transition(
      String type,
      String job,
      int nextAttempt,
      long seq,
      String output,
      String hash,
      String error,
      boolean canRetry) {
    return new AlgorithmEvent(
        UUID.randomUUID().toString(),
        type,
        1,
        requestId,
        taskId,
        job,
        nextAttempt,
        seq,
        businessDate,
        inputRef,
        inputSha256,
        output,
        hash,
        algorithmVersion,
        error,
        canRetry,
        traceparent,
        Instant.now());
  }
}
