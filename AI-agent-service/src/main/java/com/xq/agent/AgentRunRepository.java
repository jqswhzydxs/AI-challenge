package com.xq.agent;

import com.alibaba.fastjson2.JSON;
import com.xq.contracts.agent.AgentAdviceVO;
import com.xq.contracts.agent.VerifiedIdentity;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class AgentRunRepository {
  private final JdbcTemplate jdbc;

  public AgentRunRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void save(
      AgentAdviceVO result, VerifiedIdentity identity, long durationMillis, Object bundle) {
    jdbc.update(
        "INSERT INTO"
            + " agent_run(run_id,user_id,scene,analysis_status,source,model_attempted,response_json,duration_ms)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        result.getRunId(),
        identity.userId(),
        result.getScene(),
        result.getAnalysisStatus(),
        result.getSource(),
        result.getModelAttempted(),
        JSON.toJSONString(result),
        durationMillis);
    jdbc.update(
        "INSERT INTO agent_evidence_snapshot(snapshot_id,run_id,content_json) VALUES(?,?,?)",
        UUID.randomUUID().toString(),
        result.getRunId(),
        JSON.toJSONString(bundle == null ? result.getRawAnswer() : bundle));
    var snapshots = JSON.parseObject(result.getRawAnswer());
    if (snapshots != null)
      for (String tool : snapshots.keySet()) {
        var call = snapshots.getJSONObject(tool);
        jdbc.update(
            "INSERT INTO agent_tool_call(call_id,run_id,tool_name,status,snapshot_id,error_code)"
                + " VALUES(?,?,?,?,?,?)",
            call.getString("callId"),
            result.getRunId(),
            tool,
            call.getString("status"),
            call.getString("snapshotId"),
            call.getString("errorCode"));
      }
  }

  public Map<String, Object> find(String runId, String userId) {
    var rows =
        jdbc.queryForList(
            "SELECT response_json FROM agent_run WHERE run_id=? AND user_id=?", runId, userId);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND");
    return JSON.parseObject(rows.get(0).get("response_json").toString());
  }

  public void feedback(String runId, String userId, String correction) {
    if (correction == null || correction.isBlank() || correction.length() > 2000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK");
    find(runId, userId);
    jdbc.update(
        "INSERT INTO agent_feedback(feedback_id,run_id,user_id,correction) VALUES(?,?,?,?)",
        UUID.randomUUID().toString(),
        runId,
        userId,
        correction);
  }
}
