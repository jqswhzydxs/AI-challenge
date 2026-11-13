CREATE TABLE algorithm_outbox (
event_id CHAR(36) PRIMARY KEY,request_id CHAR(36) NOT NULL,payload JSON NOT NULL,state VARCHAR(16) NOT NULL,
delivery_attempts INT NOT NULL DEFAULT 0,next_send DATETIME(3) NOT NULL,owner CHAR(36),lease_until DATETIME(3),
last_error VARCHAR(64),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),sent_at DATETIME(3),
INDEX ix_outbox_send(state,next_send,lease_until));
CREATE TABLE algorithm_inbox (consumer_name VARCHAR(64) NOT NULL,event_id CHAR(36) NOT NULL,
received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(consumer_name,event_id));

CREATE TABLE compute_job (
request_id CHAR(36) PRIMARY KEY,job_id CHAR(36) NOT NULL UNIQUE,task_id VARCHAR(32) NOT NULL,
state VARCHAR(32) NOT NULL,request_payload JSON,input_hash CHAR(64),attempt INT NOT NULL DEFAULT 0,
event_seq BIGINT NOT NULL DEFAULT 0,lease_owner CHAR(36),lease_until DATETIME(3),cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
next_run DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),error_code VARCHAR(64),output_ref VARCHAR(255),output_hash CHAR(64),
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
INDEX ix_job_claim(state,next_run,lease_until));
CREATE TABLE compute_attempt (
job_id CHAR(36) NOT NULL,attempt INT NOT NULL,owner CHAR(36) NOT NULL,node VARCHAR(255) NOT NULL,process_pid BIGINT,process_started VARCHAR(64),state VARCHAR(32) NOT NULL,
started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),completed_at DATETIME(3),exit_code INT,error_code VARCHAR(64),
output_ref VARCHAR(255),runtime_version VARCHAR(255),PRIMARY KEY(job_id,attempt));
