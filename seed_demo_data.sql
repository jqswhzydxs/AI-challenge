USE challenge_cup_energy;

-- =========================================================
-- 挑战杯联调演示数据
-- 说明：
-- 1. 可重复执行，固定 ID 使用 ON DUPLICATE KEY UPDATE。
-- 2. daily_plan_v3.2.json 已写入排产任务、排产方案和 24 条明细。
-- 3. realtime_control.json 已写入 MPC 实时调控结果。
-- 4. energy_plan / joint_optimization 两类正式算法结果先不造假，等算法组给样例后再补。
-- =========================================================

-- 1. 登录账号和角色
INSERT INTO sys_user (id, username, password, real_name, phone, email, status, deleted, remark)
VALUES
  (1000000000000000001, 'admin', '123456', '系统管理员', '13800000000', 'admin@example.com', 'ENABLE', 0, '联调默认账号'),
  (1000000000000000004, 'dispatcher', '123456', '调度员', '13800000001', 'dispatcher@example.com', 'ENABLE', 0, '联调测试账号'),
  (1000000000000000005, 'energy', '123456', '能源管理员', '13800000002', 'energy@example.com', 'ENABLE', 0, '联调测试账号')
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  real_name = VALUES(real_name),
  phone = VALUES(phone),
  email = VALUES(email),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO sys_role (id, role_code, role_name, status, deleted, remark)
VALUES
  (1000000000000000002, 'SYSTEM_ADMIN', '系统管理员', 'ENABLE', 0, '联调默认角色'),
  (1000000000000000006, 'DISPATCHER', '调度员', 'ENABLE', 0, '联调默认角色'),
  (1000000000000000007, 'ENERGY_ADMIN', '能源管理员', 'ENABLE', 0, '联调默认角色')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
  (1000000000000000003, 1000000000000000001, 1000000000000000002),
  (1000000000000000008, 1000000000000000004, 1000000000000000006),
  (1000000000000000009, 1000000000000000005, 1000000000000000007)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 2. 生产基础数据
INSERT INTO production_line (id, line_code, line_name, max_capacity, min_capacity, status, deleted, remark)
VALUES
  (2000000000000000001, 'ROLLING_LINE_01', '热轧产线 1', 1.20, 0.10, 'AVAILABLE', 0, '联调产线'),
  (2000000000000000002, 'ROLLING_LINE_02', '热轧产线 2', 1.00, 0.10, 'AVAILABLE', 0, '联调产线')
ON DUPLICATE KEY UPDATE
  line_name = VALUES(line_name),
  max_capacity = VALUES(max_capacity),
  min_capacity = VALUES(min_capacity),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO production_equipment (id, line_id, equipment_code, equipment_name, equipment_type, rated_power, status, deleted, remark)
VALUES
  (2000000000000000101, 2000000000000000001, 'FURNACE_01', '加热炉 1', 'FURNACE', 3800.00, 'AVAILABLE', 0, '联调设备'),
  (2000000000000000102, 2000000000000000001, 'ROLLING_MILL_01', '轧机 1', 'ROLLING_MILL', 5200.00, 'AVAILABLE', 0, '联调设备'),
  (2000000000000000103, 2000000000000000002, 'COOLING_01', '冷却段 1', 'COOLING', 1200.00, 'AVAILABLE', 0, '联调设备')
ON DUPLICATE KEY UPDATE
  line_id = VALUES(line_id),
  equipment_name = VALUES(equipment_name),
  equipment_type = VALUES(equipment_type),
  rated_power = VALUES(rated_power),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO production_order (id, order_no, product_name, product_spec, planned_quantity, unit, due_time, priority, status, deleted, remark)
VALUES
  (2000000000000000201, 'PO-20260717-001', '热轧卷板', 'Q235B / 3.0mm', 2.80, 't', '2026-07-18 12:00:00', 1, 'PENDING', 0, '联调订单'),
  (2000000000000000202, 'PO-20260717-002', '热轧卷板', 'Q355B / 4.5mm', 3.20, 't', '2026-07-18 18:00:00', 2, 'PENDING', 0, '联调订单'),
  (2000000000000000203, 'PO-20260717-003', '中厚板', 'Q345R / 8.0mm', 1.90, 't', '2026-07-19 10:00:00', 3, 'RUNNING', 0, '联调订单'),
  (2000000000000000204, 'PO-20260717-004', '酸洗板', 'SPHC / 2.0mm', 1.60, 't', '2026-07-19 16:00:00', 4, 'COMPLETED', 0, '联调订单')
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name),
  product_spec = VALUES(product_spec),
  planned_quantity = VALUES(planned_quantity),
  unit = VALUES(unit),
  due_time = VALUES(due_time),
  priority = VALUES(priority),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

-- 3. 能源基础数据
INSERT INTO energy_medium (id, medium_code, medium_name, unit, standard_coal_factor, deleted, remark)
VALUES
  (3000000000000000001, 'ELECTRICITY', '电力', 'kWh', 0.122900, 0, '联调介质'),
  (3000000000000000002, 'STEAM', '蒸汽', 't', 128.600000, 0, '联调介质')
ON DUPLICATE KEY UPDATE
  medium_name = VALUES(medium_name),
  unit = VALUES(unit),
  standard_coal_factor = VALUES(standard_coal_factor),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO energy_equipment (id, equipment_code, equipment_name, equipment_type, min_output, max_output, efficiency, status, deleted, remark)
VALUES
  (3000000000000000101, 'BOILER_01', '锅炉 1', 'BOILER', 10.00, 60.00, 0.8900, 'AVAILABLE', 0, '联调设备'),
  (3000000000000000102, 'TURBINE_01', '汽轮机 1', 'TURBINE', 5.00, 35.00, 0.9100, 'AVAILABLE', 0, '联调设备'),
  (3000000000000000103, 'GRID_01', '外购电接口', 'GRID', 0.00, 100.00, 1.0000, 'AVAILABLE', 0, '联调设备')
ON DUPLICATE KEY UPDATE
  equipment_name = VALUES(equipment_name),
  equipment_type = VALUES(equipment_type),
  min_output = VALUES(min_output),
  max_output = VALUES(max_output),
  efficiency = VALUES(efficiency),
  status = VALUES(status),
  deleted = 0,
  remark = VALUES(remark);

-- 4. 根据 daily_plan_v3.2.json 导入排产任务和结果
INSERT INTO algorithm_task (
  id, task_type, status, progress, result_id, message, retry_count,
  algorithm_name, algorithm_version, result_file_name, algorithm_response_json,
  start_time, finish_time, deleted, remark
)
VALUES (
  4000000000000000001,
  'PRODUCTION_SCHEDULE',
  'SUCCESS',
  100,
  4000000000000000101,
  'daily_plan_v3.2.json 导入成功',
  0,
  'DAILY_MILP_SCHEDULE',
  'v3.2',
  'daily_plan_v3.2.json',
  '{"timestamp":"2026-07-17 16:10:30","plan_horizon":24,"unit":"hour","data_granularity":"1 minute","EC_baseline":14,"EC_optimized":13.278523999999999,"EC_reduction":5.1534000000000066,"total_production":9.4867191577291177,"total_energy":125.96962801716586,"optimal_temperature":1140,"optimal_speed":11}',
  '2026-07-17 16:10:30',
  '2026-07-17 16:10:31',
  0,
  '算法组 daily_plan_v3.2.json'
)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  progress = VALUES(progress),
  result_id = VALUES(result_id),
  message = VALUES(message),
  algorithm_response_json = VALUES(algorithm_response_json),
  finish_time = VALUES(finish_time),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO production_schedule_plan (
  id, task_id, schedule_name, schedule_date, plan_start_time, plan_horizon,
  plan_unit, data_granularity, status, objective, elec_coefficient,
  total_demand, total_production, total_energy, raw_plan_json, deleted, remark
)
VALUES (
  4000000000000000101,
  4000000000000000001,
  '2026-07-17 日级排产方案',
  '2026-07-17',
  '2026-07-17 16:10:30',
  24,
  'hour',
  '1 minute',
  'SUCCESS',
  'MIN_EC',
  13.278524,
  9.436921,
  9.486719,
  125.969628,
  '{"timestamp":"2026-07-17 16:10:30","plan_horizon":24,"unit":"hour","data_granularity":"1 minute","EC_baseline":14,"EC_optimized":13.278523999999999,"EC_reduction":5.1534000000000066,"total_production":9.4867191577291177,"total_energy":125.96962801716586,"optimal_temperature":1140,"optimal_speed":11}',
  0,
  '算法组 daily_plan_v3.2.json'
)
ON DUPLICATE KEY UPDATE
  schedule_name = VALUES(schedule_name),
  schedule_date = VALUES(schedule_date),
  plan_start_time = VALUES(plan_start_time),
  plan_horizon = VALUES(plan_horizon),
  plan_unit = VALUES(plan_unit),
  data_granularity = VALUES(data_granularity),
  status = VALUES(status),
  objective = VALUES(objective),
  elec_coefficient = VALUES(elec_coefficient),
  total_demand = VALUES(total_demand),
  total_production = VALUES(total_production),
  total_energy = VALUES(total_energy),
  raw_plan_json = VALUES(raw_plan_json),
  deleted = 0,
  remark = VALUES(remark);

DELETE FROM production_schedule_detail WHERE schedule_id = 4000000000000000101;

INSERT INTO production_schedule_detail (
  id, schedule_id, hour_index, start_time, end_time, demand, production, elec_forecast,
  line_id, equipment_id, order_id, equipment_load_rate, conflict_flag
)
SELECT
  4000000000000001000 + s.hour_index,
  4000000000000000101,
  s.hour_index,
  DATE_ADD('2026-07-17 16:10:30', INTERVAL s.hour_index HOUR),
  DATE_ADD('2026-07-17 16:10:30', INTERVAL s.hour_index + 1 HOUR),
  s.demand,
  s.production,
  ROUND(s.production * 13.278524, 6),
  2000000000000000001,
  2000000000000000102,
  CASE
    WHEN s.hour_index < 8 THEN 2000000000000000201
    WHEN s.hour_index < 16 THEN 2000000000000000202
    ELSE 2000000000000000203
  END,
  ROUND(LEAST(100, s.production / 0.95 * 100), 2),
  0
FROM (
  SELECT 0 hour_index, 0.3791368692364509 demand, 0.15 production UNION ALL
  SELECT 1, 0.520875719376839, 0.9500000000000001 UNION ALL
  SELECT 2, 0.46707050968560532, 0.15 UNION ALL
  SELECT 3, 0.43426998711247067, 0.43671915772911529 UNION ALL
  SELECT 4, 0.32538058554883914, 0.15 UNION ALL
  SELECT 5, 0.3253746520027056, 0.15 UNION ALL
  SELECT 6, 0.30128856859337683, 0.9500000000000001 UNION ALL
  SELECT 7, 0.50007933186063369, 0.15000000000000002 UNION ALL
  SELECT 8, 0.43487429288882906, 0.9500000000000001 UNION ALL
  SELECT 9, 0.46118585413782681, 0.15000000000000002 UNION ALL
  SELECT 10, 0.29206378559676716, 0.9500000000000001 UNION ALL
  SELECT 11, 0.52559782363185015, 0.15 UNION ALL
  SELECT 12, 0.49178088963690342, 0.15 UNION ALL
  SELECT 13, 0.33923542122685563, 0.15 UNION ALL
  SELECT 14, 0.3317289419329465, 0.15 UNION ALL
  SELECT 15, 0.33211750942394447, 0.15 UNION ALL
  SELECT 16, 0.36184359176804604, 0.9500000000000001 UNION ALL
  SELECT 17, 0.41609008218153021, 0.15 UNION ALL
  SELECT 18, 0.39325847458596019, 0.15 UNION ALL
  SELECT 19, 0.35864236848871806, 0.9500000000000001 UNION ALL
  SELECT 20, 0.43751581210170504, 0.9500000000000001 UNION ALL
  SELECT 21, 0.321315489720402, 0.15 UNION ALL
  SELECT 22, 0.35886758353966342, 0.15 UNION ALL
  SELECT 23, 0.3771250134502479, 0.15
) s;

-- 排产评价指标，来自 daily_plan_v3.2.json
INSERT INTO evaluation_metric (
  id, biz_type, biz_id, mape, ec_before, ec_after, er, cost_saving, carbon_reduction, calculate_time
)
VALUES (
  4000000000000000201,
  'SCHEDULE',
  4000000000000000101,
  2.10,
  14.0000,
  13.278524,
  96.80,
  6200.00,
  0.82,
  '2026-07-17 16:10:31'
)
ON DUPLICATE KEY UPDATE
  mape = VALUES(mape),
  ec_before = VALUES(ec_before),
  ec_after = VALUES(ec_after),
  er = VALUES(er),
  cost_saving = VALUES(cost_saving),
  carbon_reduction = VALUES(carbon_reduction),
  calculate_time = VALUES(calculate_time);

-- 5. 根据 realtime_control.json 导入 MPC 调控结果
INSERT INTO algorithm_task (
  id, task_type, status, progress, result_id, message, retry_count,
  algorithm_name, algorithm_version, result_file_name, algorithm_response_json,
  start_time, finish_time, deleted, remark
)
VALUES (
  5000000000000000001,
  'REALTIME_MPC',
  'SUCCESS',
  100,
  5000000000000000101,
  'realtime_control.json 导入成功',
  0,
  'MPC_REALTIME_CONTROL',
  'v1.0',
  'realtime_control.json',
  '{"timestamp":"01:00:00","control":{"boiler_load":30.009999999999998,"turbine_output":10.177999999999999,"grid_purchase":0,"power_factor_target":0.95},"forecast":{"elec_next_5min":4.1819999999999995,"steam_next_5min":0.505}}',
  '2026-07-17 01:00:00',
  '2026-07-17 01:00:01',
  0,
  '算法组 realtime_control.json'
)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  progress = VALUES(progress),
  result_id = VALUES(result_id),
  message = VALUES(message),
  algorithm_response_json = VALUES(algorithm_response_json),
  finish_time = VALUES(finish_time),
  deleted = 0,
  remark = VALUES(remark);

INSERT INTO mpc_realtime_control (
  id, task_id, control_date, control_time, raw_timestamp,
  boiler_load_mw, turbine_output_mw, grid_purchase_kwh, power_factor_target,
  elec_next_5min_kwh, steam_next_5min_t, source_file_name, raw_json
)
VALUES (
  5000000000000000101,
  5000000000000000001,
  '2026-07-17',
  '01:00:00',
  '01:00:00',
  30.010000,
  10.178000,
  0.000000,
  0.950000,
  4.182000,
  0.505000,
  'realtime_control.json',
  '{"timestamp":"01:00:00","control":{"boiler_load":30.009999999999998,"turbine_output":10.177999999999999,"grid_purchase":0,"power_factor_target":0.95},"forecast":{"elec_next_5min":4.1819999999999995,"steam_next_5min":0.505}}'
)
ON DUPLICATE KEY UPDATE
  task_id = VALUES(task_id),
  boiler_load_mw = VALUES(boiler_load_mw),
  turbine_output_mw = VALUES(turbine_output_mw),
  grid_purchase_kwh = VALUES(grid_purchase_kwh),
  power_factor_target = VALUES(power_factor_target),
  elec_next_5min_kwh = VALUES(elec_next_5min_kwh),
  steam_next_5min_t = VALUES(steam_next_5min_t),
  source_file_name = VALUES(source_file_name),
  raw_json = VALUES(raw_json);

-- 6. 首页告警和报表统计
INSERT INTO warning_record (id, warning_type, level, message, biz_type, biz_id, warning_time, handled)
VALUES
  (6000000000000000001, 'ENERGY_LOAD', 'HIGH', '17:00-18:00 预测负荷接近上限，请关注锅炉负荷裕度', 'SCHEDULE', 4000000000000000101, '2026-07-17 17:05:00', 0),
  (6000000000000000002, 'POWER_FACTOR', 'MEDIUM', '功率因数目标维持在 0.95，建议持续监控无功波动', 'REALTIME_MPC', 5000000000000000101, '2026-07-17 01:00:00', 0),
  (6000000000000000003, 'ORDER_RISK', 'LOW', '订单 PO-20260717-002 存在排产窗口压缩风险', 'ORDER', 2000000000000000202, '2026-07-17 16:30:00', 0)
ON DUPLICATE KEY UPDATE
  warning_type = VALUES(warning_type),
  level = VALUES(level),
  message = VALUES(message),
  biz_type = VALUES(biz_type),
  biz_id = VALUES(biz_id),
  warning_time = VALUES(warning_time),
  handled = VALUES(handled);

INSERT INTO report_statistic (id, stat_date, stat_type, total_energy_kgce, energy_cost, cost_saving, carbon_reduction, production_output)
VALUES
  (6000000000000000101, '2026-07-17', 'DAY', 13250.50, 36300.00, 6200.00, 0.82, 9.4867),
  (6000000000000000102, '2026-07-01', 'MONTH', 351200.00, 955000.00, 125000.00, 36.00, 287.5000)
ON DUPLICATE KEY UPDATE
  total_energy_kgce = VALUES(total_energy_kgce),
  energy_cost = VALUES(energy_cost),
  cost_saving = VALUES(cost_saving),
  carbon_reduction = VALUES(carbon_reduction),
  production_output = VALUES(production_output);

-- 7. 合成 48 条实时能源曲线数据，供首页和能源页联调
DELETE FROM energy_realtime_data
WHERE timestamp >= '2026-07-17 00:00:00'
  AND timestamp < '2026-07-17 12:00:00'
  AND source = 'seed_demo_data';

INSERT INTO energy_realtime_data (
  id, timestamp, raw_timestamp, electricity_consumption, steam_consumption,
  carbon_emission_tco2, lagging_reactive_power_kvarh, leading_reactive_power_kvarh,
  lagging_power_factor, leading_power_factor, nsm, week_status, day_of_week,
  load_type, data_quality, source
)
SELECT
  7000000000000000000 + n.n,
  DATE_ADD('2026-07-17 00:00:00', INTERVAL n.n * 15 MINUTE),
  DATE_FORMAT(DATE_ADD('2026-07-17 00:00:00', INTERVAL n.n * 15 MINUTE), '%Y-%m-%d %H:%i:%s'),
  ROUND(4.00 + (n.n % 12) * 0.18 + IF(n.n BETWEEN 16 AND 32, 0.80, 0.00), 4),
  ROUND(0.45 + (n.n % 8) * 0.025, 4),
  ROUND((4.00 + (n.n % 12) * 0.18 + IF(n.n BETWEEN 16 AND 32, 0.80, 0.00)) * 0.0078, 6),
  ROUND(1.20 + (n.n % 5) * 0.08, 4),
  ROUND(0.30 + (n.n % 4) * 0.04, 4),
  ROUND(0.91 + (n.n % 6) * 0.006, 4),
  ROUND(0.96 - (n.n % 5) * 0.004, 4),
  n.n * 900,
  'WORKDAY',
  'FRIDAY',
  CASE
    WHEN n.n BETWEEN 16 AND 32 THEN 'MAXIMUM_LOAD'
    WHEN n.n BETWEEN 0 AND 8 THEN 'LIGHT_LOAD'
    ELSE 'MEDIUM_LOAD'
  END,
  'NORMAL',
  'seed_demo_data'
FROM (
  SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
  SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
  SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL
  SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL
  SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL
  SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL
  SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL
  SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31 UNION ALL
  SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35 UNION ALL
  SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL
  SELECT 40 UNION ALL SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL
  SELECT 44 UNION ALL SELECT 45 UNION ALL SELECT 46 UNION ALL SELECT 47
) n;

-- 8. 操作日志，供系统页面联调
INSERT INTO sys_operation_log (
  id, user_id, module, operation, request_uri, request_method, request_param, result_code, error_message, operation_time
)
VALUES
  (8000000000000000001, 1000000000000000001, 'AUTH', '登录系统', '/api/auth/login', 'POST', '{"username":"admin"}', 200, NULL, '2026-07-17 16:12:00'),
  (8000000000000000002, 1000000000000000001, 'PRODUCTION', '导入日级排产方案', '/api/production/schedules/import-daily-plan', 'POST', '{"file":"daily_plan_v3.2.json"}', 200, NULL, '2026-07-17 16:13:00'),
  (8000000000000000003, 1000000000000000005, 'REALTIME_MPC', '导入实时调控结果', '/api/realtime-control/latest', 'GET', '{"file":"realtime_control.json"}', 200, NULL, '2026-07-17 16:14:00')
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  module = VALUES(module),
  operation = VALUES(operation),
  request_uri = VALUES(request_uri),
  request_method = VALUES(request_method),
  request_param = VALUES(request_param),
  result_code = VALUES(result_code),
  error_message = VALUES(error_message),
  operation_time = VALUES(operation_time);

-- 9. 快速检查
SELECT 'sys_user' table_name, COUNT(*) row_count FROM sys_user
UNION ALL SELECT 'production_order', COUNT(*) FROM production_order
UNION ALL SELECT 'production_schedule_plan', COUNT(*) FROM production_schedule_plan
UNION ALL SELECT 'production_schedule_detail', COUNT(*) FROM production_schedule_detail WHERE schedule_id = 4000000000000000101
UNION ALL SELECT 'mpc_realtime_control', COUNT(*) FROM mpc_realtime_control
UNION ALL SELECT 'energy_realtime_data(seed)', COUNT(*) FROM energy_realtime_data WHERE source = 'seed_demo_data'
UNION ALL SELECT 'warning_record', COUNT(*) FROM warning_record;
