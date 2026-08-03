USE challenge_cup_energy;

-- =========================================================
-- 一键清空所有业务数据，保留建表结构
-- 执行：source reset_all.sql;
-- =========================================================

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM sys_user WHERE id >= 0;
DELETE FROM sys_role WHERE id >= 0;
DELETE FROM sys_user_role WHERE id >= 0;
DELETE FROM sys_operation_log WHERE id >= 0;
DELETE FROM production_line WHERE id >= 0;
DELETE FROM production_equipment WHERE id >= 0;
DELETE FROM production_order WHERE id >= 0;
DELETE FROM algorithm_task WHERE id >= 0;
DELETE FROM production_schedule_plan WHERE id >= 0;
DELETE FROM production_schedule_detail WHERE id >= 0;
DELETE FROM energy_medium WHERE id >= 0;
DELETE FROM energy_equipment WHERE id >= 0;
DELETE FROM energy_realtime_data WHERE id >= 0;
DELETE FROM steel_energy_dataset WHERE id >= 0;
DELETE FROM energy_production_history WHERE id >= 0;
DELETE FROM energy_plan WHERE id >= 0;
DELETE FROM energy_plan_detail WHERE id >= 0;
DELETE FROM joint_optimization_plan WHERE id >= 0;
DELETE FROM joint_optimization_timeseries WHERE id >= 0;
DELETE FROM constraint_conflict WHERE id >= 0;
DELETE FROM evaluation_metric WHERE id >= 0;
DELETE FROM mpc_realtime_control WHERE id >= 0;
DELETE FROM warning_record WHERE id >= 0;
DELETE FROM system_config WHERE id >= 0;
DELETE FROM report_statistic WHERE id >= 0;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'All tables cleared.' AS message;
