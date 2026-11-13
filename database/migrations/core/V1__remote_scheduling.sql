CREATE TABLE core_outbox (
event_id CHAR(36) PRIMARY KEY,request_id CHAR(36) NOT NULL,payload JSON NOT NULL,state VARCHAR(16) NOT NULL,
delivery_attempts INT NOT NULL DEFAULT 0,next_send DATETIME(3) NOT NULL,owner CHAR(36),lease_until DATETIME(3),
last_error VARCHAR(64),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),sent_at DATETIME(3),
INDEX ix_outbox_send(state,next_send,lease_until));
CREATE TABLE core_inbox (consumer_name VARCHAR(64) NOT NULL,event_id CHAR(36) NOT NULL,
received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(consumer_name,event_id));

ALTER TABLE algorithm_task ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
ADD COLUMN request_id CHAR(36) NULL,ADD COLUMN input_hash CHAR(64),ADD COLUMN compute_status VARCHAR(32),
ADD COLUMN application_status VARCHAR(32),ADD COLUMN derivation_status VARCHAR(32),ADD COLUMN accepted_event_seq BIGINT NOT NULL DEFAULT 0,
ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0,ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
ADD UNIQUE KEY uk_task_request(request_id);
ALTER TABLE production_schedule_plan ADD COLUMN source_request_id CHAR(36),ADD UNIQUE KEY uk_schedule_source(source_request_id);
CREATE TABLE schedule_generation_guard (
business_scope VARCHAR(32) NOT NULL,schedule_date DATE NOT NULL,request_id CHAR(36) NOT NULL UNIQUE,
PRIMARY KEY(business_scope,schedule_date));
CREATE TABLE schedule_submission (
user_id VARCHAR(32) NOT NULL,idempotency_key VARCHAR(128) NOT NULL,request_hash CHAR(64) NOT NULL,
request_id CHAR(36) NOT NULL UNIQUE,task_id BIGINT NOT NULL,request_json JSON NOT NULL,business_date DATE NOT NULL,
state VARCHAR(32) NOT NULL,next_prepare DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
prepare_attempts INT NOT NULL DEFAULT 0,lease_owner CHAR(36),lease_until DATETIME(3),
PRIMARY KEY(user_id,idempotency_key),INDEX ix_submission_prepare(state,next_prepare,lease_until));
CREATE TABLE algorithm_result_receipt (
request_id CHAR(36) PRIMARY KEY,task_id BIGINT NOT NULL,payload JSON NOT NULL,output_hash CHAR(64) NOT NULL,
state VARCHAR(32) NOT NULL DEFAULT 'READY',apply_attempts INT NOT NULL DEFAULT 0,next_apply DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
error_code VARCHAR(64),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3));
CREATE TABLE plan_derivation_job (
request_id CHAR(36) PRIMARY KEY,task_id BIGINT NOT NULL,schedule_id BIGINT NOT NULL UNIQUE,
state VARCHAR(32) NOT NULL DEFAULT 'PENDING',attempt INT NOT NULL DEFAULT 0,next_run DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
lease_owner CHAR(36),lease_until DATETIME(3),error_code VARCHAR(64));

CREATE TABLE schedule_submission_lock(user_id VARCHAR(32) NOT NULL,idempotency_key VARCHAR(128) NOT NULL,PRIMARY KEY(user_id,idempotency_key));

CREATE TABLE task_capacity_lock(scope VARCHAR(32) PRIMARY KEY);
INSERT INTO task_capacity_lock(scope) VALUES('remote-schedule');
