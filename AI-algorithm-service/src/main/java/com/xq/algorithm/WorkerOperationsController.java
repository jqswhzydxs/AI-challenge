package com.xq.algorithm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WorkerOperationsController {
  private final JdbcTemplate jdbc;

  @Value("${algorithm.service-token:}")
  private String token;

  public WorkerOperationsController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/internal/v1/jobs/by-request/{requestId}")
  public Map<String, Object> get(
      @PathVariable String requestId,
      @RequestHeader(value = "X-Service-Token", defaultValue = "") String credential) {
    if (token.length() < 24
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), credential.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    var jobs =
        jdbc.queryForList(
            "SELECT request_id,job_id,state,attempt,event_seq,error_code FROM compute_job WHERE"
                + " request_id=?",
            requestId);
    if (jobs.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return jobs.get(0);
  }
}
