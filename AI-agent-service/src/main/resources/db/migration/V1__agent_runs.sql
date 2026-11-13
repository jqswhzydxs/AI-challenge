CREATE TABLE agent_run (
  run_id CHAR(36) PRIMARY KEY, user_id VARCHAR(32) NOT NULL, scene VARCHAR(64) NOT NULL,
  analysis_status VARCHAR(40) NOT NULL, source VARCHAR(64) NOT NULL, model_attempted BOOLEAN NOT NULL,
  response_json JSON NOT NULL, duration_ms BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX ix_run_user(user_id, created_at)
);
CREATE TABLE agent_evidence_snapshot (
  snapshot_id CHAR(36) PRIMARY KEY, run_id CHAR(36) NOT NULL, content_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX ix_snapshot_run(run_id),
  FOREIGN KEY(run_id) REFERENCES agent_run(run_id)
);
CREATE TABLE agent_tool_call (
  call_id CHAR(36) PRIMARY KEY, run_id CHAR(36) NOT NULL, tool_name VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL, snapshot_id CHAR(36) NOT NULL, error_code VARCHAR(64),
  FOREIGN KEY(run_id) REFERENCES agent_run(run_id)
);
CREATE TABLE agent_feedback (
  feedback_id CHAR(36) PRIMARY KEY, run_id CHAR(36) NOT NULL, user_id VARCHAR(32) NOT NULL,
  correction VARCHAR(2000) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY(run_id) REFERENCES agent_run(run_id)
);
