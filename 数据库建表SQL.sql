-- 生产-能源交互式优化平台 MySQL 建表 SQL
-- 版本：v0.1
-- 说明：根据《后端数据库设计文档-讨论稿.md》生成，适用于 MySQL 8.0。

CREATE DATABASE IF NOT EXISTS challenge_cup_energy
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE challenge_cup_energy;

-- =========================
-- 1. 系统与权限
-- =========================

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password VARCHAR(255) NOT NULL COMMENT '加密密码',
  real_name VARCHAR(64) NULL COMMENT '姓名',
  phone VARCHAR(32) NULL COMMENT '手机号',
  email VARCHAR(128) NULL COMMENT '邮箱',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLE' COMMENT 'ENABLE/DISABLE',
  last_login_time DATETIME NULL COMMENT '最近登录时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人ID',
  update_by BIGINT NULL COMMENT '更新人ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  remark VARCHAR(500) NULL COMMENT '备注',
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY COMMENT '角色ID',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLE' COMMENT '启用状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL COMMENT '创建人ID',
  update_by BIGINT NULL COMMENT '更新人ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  remark VARCHAR(500) NULL COMMENT '备注',
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

INSERT INTO sys_user (id, username, password, real_name, status, remark)
VALUES (1000000000000000001, 'admin', '123456', '系统管理员', 'ENABLE', '联调默认账号')
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  real_name = VALUES(real_name),
  status = VALUES(status),
  deleted = 0;

INSERT INTO sys_role (id, role_code, role_name, status, remark)
VALUES (1000000000000000002, 'SYSTEM_ADMIN', '系统管理员', 'ENABLE', '联调默认角色')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  status = VALUES(status),
  deleted = 0;

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1000000000000000003, 1000000000000000001, 1000000000000000002)
ON DUPLICATE KEY UPDATE
  role_id = VALUES(role_id);

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NULL COMMENT '操作人',
  module VARCHAR(64) NULL COMMENT '模块',
  operation VARCHAR(128) NULL COMMENT '操作名称',
  request_uri VARCHAR(255) NULL COMMENT '请求地址',
  request_method VARCHAR(16) NULL COMMENT '请求方式',
  request_param LONGTEXT NULL COMMENT '请求参数',
  result_code INT NULL COMMENT '响应码',
  error_message VARCHAR(1000) NULL COMMENT '错误信息',
  operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_user_time (user_id, operation_time),
  KEY idx_operation_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =========================
-- 2. 生产侧基础数据
-- =========================

CREATE TABLE IF NOT EXISTS production_line (
  id BIGINT PRIMARY KEY COMMENT '产线ID',
  line_code VARCHAR(64) NOT NULL COMMENT '产线编码',
  line_name VARCHAR(128) NOT NULL COMMENT '产线名称',
  max_capacity DECIMAL(12,2) NULL COMMENT '最大产能，t/h',
  min_capacity DECIMAL(12,2) NULL COMMENT '最小产能，t/h',
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/STOPPED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_line_code (line_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产线表';

CREATE TABLE IF NOT EXISTS production_equipment (
  id BIGINT PRIMARY KEY COMMENT '设备ID',
  line_id BIGINT NOT NULL COMMENT '所属产线',
  equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码',
  equipment_name VARCHAR(128) NOT NULL COMMENT '设备名称',
  equipment_type VARCHAR(64) NULL COMMENT '设备类型',
  rated_power DECIMAL(12,2) NULL COMMENT '额定功率，kW',
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/STOPPED/MAINTAINING',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_equipment_code (equipment_code),
  KEY idx_line_id (line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产设备表';

CREATE TABLE IF NOT EXISTS production_order (
  id BIGINT PRIMARY KEY COMMENT '订单ID',
  order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
  product_name VARCHAR(128) NOT NULL COMMENT '产品名称',
  product_spec VARCHAR(128) NULL COMMENT '产品规格',
  planned_quantity DECIMAL(12,2) NOT NULL COMMENT '计划数量，t',
  unit VARCHAR(16) NOT NULL DEFAULT 't' COMMENT '单位',
  due_time DATETIME NOT NULL COMMENT '交付时间',
  priority INT NOT NULL DEFAULT 1 COMMENT '优先级，数值越小越优先',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_due_time (due_time),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产订单表';

-- =========================
-- 3. 算法任务
-- =========================

CREATE TABLE IF NOT EXISTS algorithm_task (
  id BIGINT PRIMARY KEY COMMENT '任务ID，对应taskId',
  task_type VARCHAR(64) NOT NULL COMMENT 'PRODUCTION_SCHEDULE/ENERGY_PLAN/JOINT_OPTIMIZATION/REALTIME_CONTROL/REALTIME_MPC',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
  progress INT NULL COMMENT '进度0-100',
  result_id BIGINT NULL COMMENT '结果主键',
  message VARCHAR(500) NULL COMMENT '状态说明',
  error_message VARCHAR(1000) NULL COMMENT '失败原因',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  algorithm_name VARCHAR(128) NULL COMMENT '算法名称，如DAILY_MILP_SCHEDULE',
  algorithm_version VARCHAR(64) NULL COMMENT '算法版本',
  result_file_name VARCHAR(255) NULL COMMENT '算法结果文件名，如daily_plan.json',
  training_record_count INT NULL COMMENT '训练/拟合记录数，如132481',
  frontend_request_json LONGTEXT NULL COMMENT '前端请求原始JSON',
  algorithm_request_json LONGTEXT NULL COMMENT '后端传给算法原始JSON',
  algorithm_response_json LONGTEXT NULL COMMENT '算法返回原始JSON',
  start_time DATETIME NULL COMMENT '任务开始时间',
  finish_time DATETIME NULL COMMENT '任务结束时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  KEY idx_task_type_status (task_type, status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算法任务表';

-- =========================
-- 4. 生产排产方案
-- =========================

CREATE TABLE IF NOT EXISTS production_schedule_plan (
  id BIGINT PRIMARY KEY COMMENT '排产方案ID',
  task_id BIGINT NOT NULL COMMENT '对应算法任务ID',
  schedule_name VARCHAR(128) NOT NULL COMMENT '方案名称',
  schedule_date DATE NOT NULL COMMENT '排产日期',
  plan_start_time DATETIME NOT NULL COMMENT '计划开始时间，对应daily_plan.timestamp',
  plan_horizon INT NOT NULL COMMENT '计划跨度，当前为24',
  plan_unit VARCHAR(32) NOT NULL DEFAULT 'hour' COMMENT '计划单位',
  data_granularity VARCHAR(32) NOT NULL DEFAULT '1 minute' COMMENT '模型底层数据粒度',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '方案状态',
  objective VARCHAR(64) NULL COMMENT '优化目标',
  elec_coefficient DECIMAL(12,4) NULL COMMENT '电耗系数，kWh/吨',
  total_demand DECIMAL(14,6) NULL COMMENT '总需求',
  total_production DECIMAL(14,6) NULL COMMENT '总排产量',
  total_energy DECIMAL(14,6) NULL COMMENT '总预测电耗，kWh',
  raw_plan_json LONGTEXT NULL COMMENT 'daily_plan.json原文',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  KEY idx_schedule_date (schedule_date),
  KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排产方案主表';

CREATE TABLE IF NOT EXISTS production_schedule_detail (
  id BIGINT PRIMARY KEY COMMENT '明细ID',
  schedule_id BIGINT NOT NULL COMMENT '排产方案ID',
  hour_index INT NOT NULL COMMENT '小时序号，0-23',
  start_time DATETIME NOT NULL COMMENT '小时开始时间',
  end_time DATETIME NOT NULL COMMENT '小时结束时间',
  demand DECIMAL(14,6) NULL COMMENT '小时需求',
  production DECIMAL(14,6) NOT NULL COMMENT '小时排产量，吨',
  elec_forecast DECIMAL(14,6) NOT NULL COMMENT '小时预测电耗，kWh',
  line_id BIGINT NULL COMMENT '产线ID，当前JSON未提供',
  equipment_id BIGINT NULL COMMENT '设备ID，当前JSON未提供',
  order_id BIGINT NULL COMMENT '订单ID，当前JSON未提供',
  equipment_load_rate DECIMAL(8,2) NULL COMMENT '设备负荷率',
  conflict_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否存在冲突',
  UNIQUE KEY uk_schedule_hour (schedule_id, hour_index),
  KEY idx_schedule_time (schedule_id, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排产方案明细表';

-- =========================
-- 5. 能源侧数据
-- =========================

CREATE TABLE IF NOT EXISTS energy_medium (
  id BIGINT PRIMARY KEY COMMENT '介质ID',
  medium_code VARCHAR(64) NOT NULL COMMENT 'ELECTRICITY/STEAM',
  medium_name VARCHAR(64) NOT NULL COMMENT '介质名称',
  unit VARCHAR(32) NOT NULL COMMENT '单位',
  standard_coal_factor DECIMAL(12,6) NULL COMMENT '折标煤系数',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_medium_code (medium_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能源介质表';

CREATE TABLE IF NOT EXISTS energy_equipment (
  id BIGINT PRIMARY KEY COMMENT '设备ID',
  equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码',
  equipment_name VARCHAR(128) NOT NULL COMMENT '设备名称',
  equipment_type VARCHAR(64) NOT NULL COMMENT '设备类型，如BOILER',
  min_output DECIMAL(12,2) NULL COMMENT '最小输出',
  max_output DECIMAL(12,2) NULL COMMENT '最大输出',
  efficiency DECIMAL(8,4) NULL COMMENT '效率',
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '设备状态',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_energy_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能源设备表';

CREATE TABLE IF NOT EXISTS energy_realtime_data (
  id BIGINT PRIMARY KEY COMMENT '主键',
  timestamp DATETIME NOT NULL COMMENT '清洗后的采集时间',
  raw_timestamp VARCHAR(64) NULL COMMENT '原始时间文本',
  electricity_consumption DECIMAL(14,4) NOT NULL COMMENT '源字段elec，15分钟用电量，kWh',
  steam_consumption DECIMAL(14,4) NULL COMMENT '源字段steam，单位待确认',
  carbon_emission_tco2 DECIMAL(14,6) NULL COMMENT '源字段CO2_tCO2_，tCO2',
  lagging_reactive_power_kvarh DECIMAL(14,4) NULL COMMENT '滞后无功电量',
  leading_reactive_power_kvarh DECIMAL(14,4) NULL COMMENT '超前无功电量',
  lagging_power_factor DECIMAL(8,4) NULL COMMENT '滞后功率因数',
  leading_power_factor DECIMAL(8,4) NULL COMMENT '超前功率因数',
  nsm INT NULL COMMENT '日内秒数',
  week_status VARCHAR(32) NULL COMMENT '工作日/周末',
  day_of_week VARCHAR(32) NULL COMMENT '星期',
  load_type VARCHAR(64) NULL COMMENT '负荷类型',
  data_quality VARCHAR(32) NULL COMMENT 'NORMAL/MISSING/ABNORMAL',
  source VARCHAR(128) NULL DEFAULT 'steel-industry-energy-dataset' COMMENT '数据来源',
  UNIQUE KEY uk_timestamp (timestamp),
  KEY idx_data_quality (data_quality),
  KEY idx_load_type (load_type),
  KEY idx_week_day (week_status, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能源实时数据表';

CREATE TABLE IF NOT EXISTS steel_energy_dataset (
  id BIGINT PRIMARY KEY COMMENT '主键',
  timestamp DATETIME NOT NULL COMMENT '清洗后的时间',
  raw_timestamp VARCHAR(64) NULL COMMENT '原始时间文本',
  electricity_consumption DECIMAL(14,4) NOT NULL COMMENT 'elec，用电量kWh',
  lagging_reactive_power_kvarh DECIMAL(14,4) NULL COMMENT '滞后无功电量',
  leading_reactive_power_kvarh DECIMAL(14,4) NULL COMMENT '超前无功电量',
  carbon_emission_tco2 DECIMAL(14,6) NULL COMMENT '碳排放tCO2',
  lagging_power_factor DECIMAL(8,4) NULL COMMENT '滞后功率因数',
  leading_power_factor DECIMAL(8,4) NULL COMMENT '超前功率因数',
  nsm INT NULL COMMENT '日内秒数',
  week_status VARCHAR(32) NULL COMMENT '工作日/周末',
  day_of_week VARCHAR(32) NULL COMMENT '星期',
  load_type VARCHAR(64) NULL COMMENT '负荷类型',
  steam_consumption DECIMAL(14,4) NULL COMMENT 'steam，单位待确认',
  data_quality VARCHAR(32) NULL COMMENT '数据质量',
  UNIQUE KEY uk_dataset_timestamp (timestamp),
  KEY idx_dataset_load_type (load_type),
  KEY idx_dataset_nsm (nsm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钢铁能源样本表';

CREATE TABLE IF NOT EXISTS energy_production_history (
  id BIGINT PRIMARY KEY COMMENT '主键',
  timestamp DATETIME NOT NULL COMMENT '时间，建议按15分钟对齐',
  steel_output DECIMAL(14,2) NULL COMMENT '轧钢产量，当前数据集缺失',
  qualified_output DECIMAL(14,2) NULL COMMENT '合格产品产量，当前数据集缺失',
  electricity_consumption DECIMAL(14,4) NOT NULL COMMENT '用电量，kWh',
  steam_consumption DECIMAL(14,4) NULL COMMENT '蒸汽用量，单位待确认',
  carbon_emission_tco2 DECIMAL(14,6) NULL COMMENT '碳排放，tCO2',
  energy_cost DECIMAL(14,2) NULL COMMENT '能源成本，派生字段',
  load_type VARCHAR(64) NULL COMMENT '负荷类型',
  data_quality VARCHAR(32) NULL COMMENT '数据质量',
  UNIQUE KEY uk_history_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产能源历史表';

-- =========================
-- 6. 能源运行方案
-- =========================

CREATE TABLE IF NOT EXISTS energy_plan (
  id BIGINT PRIMARY KEY COMMENT '能源方案ID',
  task_id BIGINT NOT NULL COMMENT '对应算法任务ID',
  plan_date DATE NOT NULL COMMENT '方案日期',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '方案状态',
  objective VARCHAR(64) NULL COMMENT '优化目标',
  electric_price_mode VARCHAR(64) NULL COMMENT '电价模式',
  time_interval INT NOT NULL DEFAULT 15 COMMENT '时间粒度，min',
  electricity_cost DECIMAL(14,2) NULL COMMENT '电力成本',
  steam_cost DECIMAL(14,2) NULL COMMENT '蒸汽成本',
  total_energy_cost DECIMAL(14,2) NULL COMMENT '总能源成本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  KEY idx_energy_plan_task (task_id),
  KEY idx_energy_plan_date (plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能源运行方案主表';

CREATE TABLE IF NOT EXISTS energy_plan_detail (
  id BIGINT PRIMARY KEY COMMENT '明细ID',
  plan_id BIGINT NOT NULL COMMENT '能源方案ID',
  timestamp DATETIME NOT NULL COMMENT '时间点',
  equipment_id BIGINT NULL COMMENT '能源设备ID',
  output DECIMAL(14,2) NULL COMMENT '设备输出',
  electricity_consumption DECIMAL(14,4) NULL COMMENT '用电量，kWh',
  steam_consumption DECIMAL(14,4) NULL COMMENT '蒸汽用量，单位待确认',
  carbon_emission_tco2 DECIMAL(14,6) NULL COMMENT '碳排放，tCO2',
  energy_cost DECIMAL(14,2) NULL COMMENT '能源成本',
  KEY idx_plan_time (plan_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能源运行方案明细表';

-- =========================
-- 7. 协同优化
-- =========================

CREATE TABLE IF NOT EXISTS joint_optimization_plan (
  id BIGINT PRIMARY KEY COMMENT '协同优化方案ID',
  task_id BIGINT NOT NULL COMMENT '对应算法任务ID',
  schedule_id BIGINT NOT NULL COMMENT '排产方案ID',
  energy_plan_id BIGINT NOT NULL COMMENT '能源方案ID',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '方案状态',
  recommended TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐方案',
  cost_reduction_rate DECIMAL(8,2) NULL COMMENT '降本率，%',
  energy_reduction_rate DECIMAL(8,2) NULL COMMENT '降耗率，%',
  execute_rate DECIMAL(8,2) NULL COMMENT '可执行率，%',
  mape DECIMAL(8,2) NULL COMMENT 'MAPE，%',
  ec DECIMAL(12,4) NULL COMMENT '单位合格产品能耗',
  er DECIMAL(8,2) NULL COMMENT '方案可执行率，%',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  KEY idx_joint_task (task_id),
  KEY idx_joint_schedule_energy (schedule_id, energy_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协同优化方案主表';

CREATE TABLE IF NOT EXISTS joint_optimization_timeseries (
  id BIGINT PRIMARY KEY COMMENT '明细ID',
  optimize_id BIGINT NOT NULL COMMENT '协同优化方案ID',
  timestamp DATETIME NOT NULL COMMENT '时间点',
  planned_output DECIMAL(14,2) NULL COMMENT '计划产量，t',
  electricity_consumption DECIMAL(14,4) NULL COMMENT '用电量，kWh',
  steam_consumption DECIMAL(14,4) NULL COMMENT '蒸汽用量，单位待确认',
  carbon_emission_tco2 DECIMAL(14,6) NULL COMMENT '碳排放，tCO2',
  energy_cost DECIMAL(14,2) NULL COMMENT '能源成本',
  KEY idx_optimize_time (optimize_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协同优化时序明细表';

CREATE TABLE IF NOT EXISTS constraint_conflict (
  id BIGINT PRIMARY KEY COMMENT '冲突ID',
  optimize_id BIGINT NOT NULL COMMENT '协同优化方案ID',
  conflict_type VARCHAR(64) NOT NULL COMMENT '冲突类型',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME NOT NULL COMMENT '结束时间',
  description VARCHAR(1000) NULL COMMENT '冲突说明',
  resolved TINYINT NOT NULL DEFAULT 0 COMMENT '是否解决',
  KEY idx_conflict_optimize (optimize_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='约束冲突记录表';

CREATE TABLE IF NOT EXISTS evaluation_metric (
  id BIGINT PRIMARY KEY COMMENT '指标ID',
  biz_type VARCHAR(64) NOT NULL COMMENT 'SCHEDULE/ENERGY/JOINT',
  biz_id BIGINT NOT NULL COMMENT '业务主键',
  mape DECIMAL(8,2) NULL COMMENT 'MAPE，%',
  ec_before DECIMAL(12,4) NULL COMMENT '优化前EC',
  ec_after DECIMAL(12,4) NULL COMMENT '优化后EC',
  er DECIMAL(8,2) NULL COMMENT 'ER，%',
  cost_saving DECIMAL(14,2) NULL COMMENT '降本金额，元',
  carbon_reduction DECIMAL(14,6) NULL COMMENT '碳减排，tCO2',
  calculate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '指标计算时间',
  KEY idx_metric_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价指标记录表';

-- =========================
-- 8. 实时 MPC 调控
-- =========================

CREATE TABLE IF NOT EXISTS mpc_realtime_control (
  id BIGINT PRIMARY KEY COMMENT '控制记录ID',
  task_id BIGINT NULL COMMENT '对应算法任务ID，可为空',
  control_date DATE NOT NULL COMMENT '控制日期，由后端按入库日期或业务日期填充',
  control_time TIME NOT NULL COMMENT '控制时间，对应timestamp，格式HH:mm:ss',
  raw_timestamp VARCHAR(32) NOT NULL COMMENT '原始时间字符串',
  boiler_load_mw DECIMAL(14,6) NOT NULL COMMENT '锅炉负荷指令，MW',
  turbine_output_mw DECIMAL(14,6) NOT NULL COMMENT '汽机出力指令，MW',
  grid_purchase_kwh DECIMAL(14,6) NOT NULL COMMENT '外购电力指令，kWh',
  power_factor_target DECIMAL(8,6) NOT NULL COMMENT '功率因数目标值',
  elec_next_5min_kwh DECIMAL(14,6) NOT NULL COMMENT '未来5分钟用电预测，kWh',
  steam_next_5min_t DECIMAL(14,6) NOT NULL COMMENT '未来5分钟蒸汽预测，吨',
  source_file_name VARCHAR(255) NULL COMMENT '来源文件名，如realtime_control.json',
  raw_json LONGTEXT NULL COMMENT '原始JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  UNIQUE KEY uk_control_datetime (control_date, control_time),
  KEY idx_mpc_task_id (task_id),
  KEY idx_mpc_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MPC实时调控结果表';

-- =========================
-- 9. 报表、告警、配置
-- =========================

CREATE TABLE IF NOT EXISTS warning_record (
  id BIGINT PRIMARY KEY COMMENT '告警ID',
  warning_type VARCHAR(64) NOT NULL COMMENT '告警类型',
  level VARCHAR(32) NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
  message VARCHAR(1000) NOT NULL COMMENT '告警信息',
  biz_type VARCHAR(64) NULL COMMENT '关联业务类型',
  biz_id BIGINT NULL COMMENT '关联业务ID',
  warning_time DATETIME NOT NULL COMMENT '告警时间',
  handled TINYINT NOT NULL DEFAULT 0 COMMENT '是否处理',
  KEY idx_warning_time (warning_time),
  KEY idx_warning_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT PRIMARY KEY COMMENT '配置ID',
  config_key VARCHAR(128) NOT NULL COMMENT '配置键',
  config_value VARCHAR(1000) NOT NULL COMMENT '配置值',
  config_name VARCHAR(128) NULL COMMENT '配置名称',
  config_group VARCHAR(64) NULL COMMENT '配置分组',
  editable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可编辑',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS report_statistic (
  id BIGINT PRIMARY KEY COMMENT '主键',
  stat_date DATE NOT NULL COMMENT '统计日期',
  stat_type VARCHAR(64) NOT NULL COMMENT '统计类型',
  total_energy_kgce DECIMAL(14,2) NULL COMMENT '总能耗',
  energy_cost DECIMAL(14,2) NULL COMMENT '能源成本',
  cost_saving DECIMAL(14,2) NULL COMMENT '降本金额',
  carbon_reduction DECIMAL(14,6) NULL COMMENT '碳减排，tCO2',
  production_output DECIMAL(14,2) NULL COMMENT '产量',
  UNIQUE KEY uk_stat_date_type (stat_date, stat_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表统计表';


ALTER TABLE algorithm_task
    MODIFY COLUMN task_type VARCHAR(64) NOT NULL
        COMMENT 'PRODUCTION_SCHEDULE/ENERGY_PLAN/JOINT_OPTIMIZATION/REALTIME_CONTROL/REALTIME_MPC';

CREATE TABLE IF NOT EXISTS mpc_realtime_control (
                                                    id BIGINT PRIMARY KEY COMMENT '控制记录ID',
                                                    task_id BIGINT NULL COMMENT '对应算法任务ID，可为空',
                                                    control_date DATE NOT NULL COMMENT '控制日期，由后端按入库日期或业务日期填充',
                                                    control_time TIME NOT NULL COMMENT '控制时间，对应timestamp，格式HH:mm:ss',
                                                    raw_timestamp VARCHAR(32) NOT NULL COMMENT '原始时间字符串',
                                                    boiler_load_mw DECIMAL(14,6) NOT NULL COMMENT '锅炉负荷指令，MW',
                                                    turbine_output_mw DECIMAL(14,6) NOT NULL COMMENT '汽机出力指令，MW',
                                                    grid_purchase_kwh DECIMAL(14,6) NOT NULL COMMENT '外购电力指令，kWh',
                                                    power_factor_target DECIMAL(8,6) NOT NULL COMMENT '功率因数目标值',
                                                    elec_next_5min_kwh DECIMAL(14,6) NOT NULL COMMENT '未来5分钟用电预测，kWh',
                                                    steam_next_5min_t DECIMAL(14,6) NOT NULL COMMENT '未来5分钟蒸汽预测，吨',
                                                    source_file_name VARCHAR(255) NULL COMMENT '来源文件名，如realtime_control.json',
                                                    raw_json LONGTEXT NULL COMMENT '原始JSON',
                                                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
                                                    UNIQUE KEY uk_control_datetime (control_date, control_time),
                                                    KEY idx_mpc_task_id (task_id),
                                                    KEY idx_mpc_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MPC实时调控结果表';
