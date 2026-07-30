-- challenge_cup_energy related tables full backup before energy plan cleanup
-- Created at 2026-07-29T10:20:47.914914400
-- Target energy plan id: 2079397804341497858
-- Related energy task ids: [2079397804341497857]
-- Related joint optimization ids: [2079397813208256514]

-- Table structure for algorithm_task
DROP TABLE IF EXISTS `algorithm_task`;
CREATE TABLE `algorithm_task` (
  `id` bigint NOT NULL COMMENT '任务ID，对应taskId',
  `task_type` varchar(64) NOT NULL COMMENT 'PRODUCTION_SCHEDULE/ENERGY_PLAN/JOINT_OPTIMIZATION/REALTIME_CONTROL/REALTIME_MPC',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
  `progress` int DEFAULT NULL COMMENT '进度0-100',
  `result_id` bigint DEFAULT NULL COMMENT '结果主键',
  `message` varchar(500) DEFAULT NULL COMMENT '状态说明',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `algorithm_name` varchar(128) DEFAULT NULL COMMENT '算法名称，如DAILY_MILP_SCHEDULE',
  `algorithm_version` varchar(64) DEFAULT NULL COMMENT '算法版本',
  `result_file_name` varchar(255) DEFAULT NULL COMMENT '算法结果文件名，如daily_plan.json',
  `training_record_count` int DEFAULT NULL COMMENT '训练/拟合记录数，如132481',
  `frontend_request_json` longtext COMMENT '前端请求原始JSON',
  `algorithm_request_json` longtext COMMENT '后端传给算法原始JSON',
  `algorithm_response_json` longtext COMMENT '算法返回原始JSON',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `finish_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_type_status` (`task_type`,`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='算法任务表';

-- Data for algorithm_task
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079397804341497857,'ENERGY_PLAN','SUCCESS',100,2079397804341497858,'能源运行方案已生成',NULL,0,NULL,NULL,NULL,NULL,'{"constraints":{"steamUnitPrice":"180.00","equipmentId":1},"electricPriceMode":"PEAK_VALLEY","objective":"MIN_ENERGY_COST","planDate":"2026-07-17","timeRange":"24h"}',NULL,NULL,'2026-07-21T10:47:25','2026-07-21T10:47:25','2026-07-21T10:47:25','2026-07-21T10:47:25',NULL,NULL,0,NULL);
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079397813145341954,'JOINT_OPTIMIZATION','SUCCESS',100,2079397813208256514,'协同优化评价已生成',NULL,0,NULL,NULL,NULL,NULL,'{"energyPlanId":2079397804341497858,"objectiveWeights":{"productionEfficiency":0.3,"energyCost":0.4,"carbonEmission":0.3,"maxBoilerLoad":80.0},"scheduleId":4000000000000000101}',NULL,NULL,'2026-07-21T10:47:27','2026-07-21T10:47:27','2026-07-21T10:47:27','2026-07-21T10:47:27',NULL,NULL,0,NULL);
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079406426396069890,'ENERGY_PLAN','SUCCESS',100,2079406426396069891,'能源运行方案已生成',NULL,0,NULL,NULL,NULL,NULL,'{"constraints":{"steamUnitPrice":"180.00","equipmentId":1},"electricPriceMode":"PEAK_VALLEY","objective":"MIN_ENERGY_COST","planDate":"2026-07-17","timeRange":"24h"}',NULL,NULL,'2026-07-21T11:21:40','2026-07-21T11:21:40','2026-07-21T11:21:40','2026-07-21T11:21:40',NULL,NULL,0,NULL);
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079406435229274114,'JOINT_OPTIMIZATION','SUCCESS',100,2079406435229274115,'协同优化评价已生成',NULL,0,NULL,NULL,NULL,NULL,'{"energyPlanId":2079406426396069891,"objectiveWeights":{"productionEfficiency":0.3,"energyCost":0.4,"carbonEmission":0.3,"maxBoilerLoad":80.0},"scheduleId":4000000000000000101}',NULL,NULL,'2026-07-21T11:21:42','2026-07-21T11:21:42','2026-07-21T11:21:42','2026-07-21T11:21:42',NULL,NULL,0,NULL);
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (4000000000000000001,'PRODUCTION_SCHEDULE','SUCCESS',100,4000000000000000101,'daily_plan_v3.2.json 导入成功',NULL,0,'DAILY_MILP_SCHEDULE','v3.2','daily_plan_v3.2.json',NULL,NULL,NULL,'{"timestamp":"2026-07-17 16:10:30","plan_horizon":24,"unit":"hour","data_granularity":"1 minute","EC_baseline":14,"EC_optimized":13.278523999999999,"EC_reduction":5.1534000000000066,"total_production":9.4867191577291177,"total_energy":125.96962801716586,"optimal_temperature":1140,"optimal_speed":11}','2026-07-17T16:10:30','2026-07-17T16:10:31','2026-07-21T10:41:33','2026-07-21T10:41:33',NULL,NULL,0,'算法组 daily_plan_v3.2.json');
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (5000000000000000002,'REALTIME_MPC','SUCCESS',100,5000000000000000201,'mpc_results_final.csv 导入成功',NULL,0,'MPC_REALTIME_CONTROL','v1.0','mpc_results_final.csv',NULL,NULL,NULL,'{"description":"2-hour MPC final results, 1-min granularity, 120 time steps","avg_boiler_mw":27.90,"avg_turbine_mw":8.005,"total_grid_kwh":12.4500}','2026-07-17T00:01','2026-07-17T02:00:01','2026-07-21T15:16:08','2026-07-21T15:16:08',NULL,NULL,0,'算法组 mpc_results_final.csv');
INSERT INTO `algorithm_task` (`id`,`task_type`,`status`,`progress`,`result_id`,`message`,`error_message`,`retry_count`,`algorithm_name`,`algorithm_version`,`result_file_name`,`training_record_count`,`frontend_request_json`,`algorithm_request_json`,`algorithm_response_json`,`start_time`,`finish_time`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (5000000000000000003,'REALTIME_MPC','SUCCESS',100,5000000000000000401,'mpc_results_hourly.csv 导入成功',NULL,0,'MPC_REALTIME_CONTROL','v1.0','mpc_results_hourly.csv',NULL,NULL,NULL,'{"description":"2-hour MPC hourly aggregated results, 1-min granularity, 120 time steps","data_type":"hourly_aggregated"}','2026-07-18T00:01','2026-07-18T02:00:01','2026-07-21T15:16:29','2026-07-21T15:16:29',NULL,NULL,0,'算法组 mpc_results_hourly.csv');

-- Table structure for energy_plan
DROP TABLE IF EXISTS `energy_plan`;
CREATE TABLE `energy_plan` (
  `id` bigint NOT NULL COMMENT '能源方案ID',
  `task_id` bigint NOT NULL COMMENT '对应算法任务ID',
  `plan_date` date NOT NULL COMMENT '方案日期',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '方案状态',
  `objective` varchar(64) DEFAULT NULL COMMENT '优化目标',
  `electric_price_mode` varchar(64) DEFAULT NULL COMMENT '电价模式',
  `time_interval` int NOT NULL DEFAULT '15' COMMENT '时间粒度，min',
  `electricity_cost` decimal(14,2) DEFAULT NULL COMMENT '电力成本',
  `steam_cost` decimal(14,2) DEFAULT NULL COMMENT '蒸汽成本',
  `total_energy_cost` decimal(14,2) DEFAULT NULL COMMENT '总能源成本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_energy_plan_task` (`task_id`),
  KEY `idx_energy_plan_date` (`plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='能源运行方案主表';

-- Data for energy_plan
INSERT INTO `energy_plan` (`id`,`task_id`,`plan_date`,`status`,`objective`,`electric_price_mode`,`time_interval`,`electricity_cost`,`steam_cost`,`total_energy_cost`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079397804341497858,2079397804341497857,'2026-07-17','SUCCESS','MIN_ENERGY_COST','PEAK_VALLEY',60,113.65,4644.00,4757.65,'2026-07-21T10:47:25','2026-07-21T10:47:25',NULL,NULL,0,NULL);
INSERT INTO `energy_plan` (`id`,`task_id`,`plan_date`,`status`,`objective`,`electric_price_mode`,`time_interval`,`electricity_cost`,`steam_cost`,`total_energy_cost`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079406426396069891,2079406426396069890,'2026-07-17','SUCCESS','MIN_ENERGY_COST','PEAK_VALLEY',60,75.44,113.37,188.81,'2026-07-21T11:21:40','2026-07-21T11:21:40',NULL,NULL,0,NULL);

-- Table structure for energy_plan_detail
DROP TABLE IF EXISTS `energy_plan_detail`;
CREATE TABLE `energy_plan_detail` (
  `id` bigint NOT NULL COMMENT '明细ID',
  `plan_id` bigint NOT NULL COMMENT '能源方案ID',
  `timestamp` datetime NOT NULL COMMENT '时间点',
  `equipment_id` bigint DEFAULT NULL COMMENT '能源设备ID',
  `output` decimal(14,2) DEFAULT NULL COMMENT '设备输出',
  `electricity_consumption` decimal(14,4) DEFAULT NULL COMMENT '用电量，kWh',
  `steam_consumption` decimal(14,4) DEFAULT NULL COMMENT '蒸汽用量，单位待确认',
  `carbon_emission_tco2` decimal(14,6) DEFAULT NULL COMMENT '碳排放，tCO2',
  `energy_cost` decimal(14,2) DEFAULT NULL COMMENT '能源成本',
  PRIMARY KEY (`id`),
  KEY `idx_plan_time` (`plan_id`,`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='能源运行方案明细表';

-- Data for energy_plan_detail
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829505,2079397804341497858,'2026-07-17T00:00',1,1.37,17.0800,1.9500,0.133224,356.98);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829506,2079397804341497858,'2026-07-17T01:00',1,1.60,19.9600,2.3500,0.155688,429.99);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829507,2079397804341497858,'2026-07-17T02:00',1,1.83,22.8400,1.9500,0.178152,358.99);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829508,2079397804341497858,'2026-07-17T03:00',1,1.37,17.0800,2.3500,0.133224,428.98);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829509,2079397804341497858,'2026-07-17T04:00',1,1.85,23.1600,1.9500,0.180648,359.11);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804391829510,2079397804341497858,'2026-07-17T05:00',1,2.08,26.0400,2.3500,0.203112,432.11);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938369,2079397804341497858,'2026-07-17T06:00',1,1.62,20.2800,1.9500,0.158184,358.10);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938370,2079397804341497858,'2026-07-17T07:00',1,1.85,23.1600,2.3500,0.180648,431.11);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938371,2079397804341497858,'2026-07-17T08:00',1,1.89,23.6400,1.9500,0.184392,366.37);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938372,2079397804341497858,'2026-07-17T09:00',1,1.37,17.0800,2.3500,0.133224,434.10);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938373,2079397804341497858,'2026-07-17T10:00',1,1.60,19.9600,1.9500,0.155688,363.97);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397804458938374,2079397804341497858,'2026-07-17T11:00',1,1.83,22.8400,2.3500,0.178152,437.85);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426396069892,2079406426396069891,'2026-07-17T16:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426396069893,2079406426396069891,'2026-07-17T17:10:30',1,95.00,12.6146,0.0631,0.007190,19.55);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984449,2079406426396069891,'2026-07-17T18:10:30',1,15.00,1.9918,0.0100,0.001135,3.88);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984450,2079406426396069891,'2026-07-17T19:10:30',1,43.67,5.7990,0.0290,0.003305,11.31);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984451,2079406426396069891,'2026-07-17T20:10:30',1,15.00,1.9918,0.0100,0.001135,3.88);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984452,2079406426396069891,'2026-07-17T21:10:30',1,15.00,1.9918,0.0100,0.001135,3.88);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984453,2079406426396069891,'2026-07-17T22:10:30',1,95.00,12.6146,0.0631,0.007190,19.55);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984454,2079406426396069891,'2026-07-17T23:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426458984455,2079406426396069891,'2026-07-18T00:10:30',1,95.00,12.6146,0.0631,0.007190,15.77);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899010,2079406426396069891,'2026-07-18T01:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899011,2079406426396069891,'2026-07-18T02:10:30',1,95.00,12.6146,0.0631,0.007190,15.77);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899012,2079406426396069891,'2026-07-18T03:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899013,2079406426396069891,'2026-07-18T04:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899014,2079406426396069891,'2026-07-18T05:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899015,2079406426396069891,'2026-07-18T06:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426521899016,2079406426396069891,'2026-07-18T07:10:30',1,15.00,1.9918,0.0100,0.001135,2.49);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007873,2079406426396069891,'2026-07-18T08:10:30',1,95.00,12.6146,0.0631,0.007190,19.55);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007874,2079406426396069891,'2026-07-18T09:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007875,2079406426396069891,'2026-07-18T10:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007876,2079406426396069891,'2026-07-18T11:10:30',1,95.00,12.6146,0.0631,0.007190,19.55);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007877,2079406426396069891,'2026-07-18T12:10:30',1,95.00,12.6146,0.0631,0.007190,19.55);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007878,2079406426396069891,'2026-07-18T13:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007879,2079406426396069891,'2026-07-18T14:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);
INSERT INTO `energy_plan_detail` (`id`,`plan_id`,`timestamp`,`equipment_id`,`output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406426589007880,2079406426396069891,'2026-07-18T15:10:30',1,15.00,1.9918,0.0100,0.001135,3.09);

-- Table structure for joint_optimization_plan
DROP TABLE IF EXISTS `joint_optimization_plan`;
CREATE TABLE `joint_optimization_plan` (
  `id` bigint NOT NULL COMMENT '协同优化方案ID',
  `task_id` bigint NOT NULL COMMENT '对应算法任务ID',
  `schedule_id` bigint NOT NULL COMMENT '排产方案ID',
  `energy_plan_id` bigint NOT NULL COMMENT '能源方案ID',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '方案状态',
  `recommended` tinyint NOT NULL DEFAULT '0' COMMENT '是否推荐方案',
  `cost_reduction_rate` decimal(8,2) DEFAULT NULL COMMENT '降本率，%',
  `energy_reduction_rate` decimal(8,2) DEFAULT NULL COMMENT '降耗率，%',
  `execute_rate` decimal(8,2) DEFAULT NULL COMMENT '可执行率，%',
  `mape` decimal(8,2) DEFAULT NULL COMMENT 'MAPE，%',
  `ec` decimal(12,4) DEFAULT NULL COMMENT '单位合格产品能耗',
  `er` decimal(8,2) DEFAULT NULL COMMENT '方案可执行率，%',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_joint_task` (`task_id`),
  KEY `idx_joint_schedule_energy` (`schedule_id`,`energy_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同优化方案主表';

-- Data for joint_optimization_plan
INSERT INTO `joint_optimization_plan` (`id`,`task_id`,`schedule_id`,`energy_plan_id`,`status`,`recommended`,`cost_reduction_rate`,`energy_reduction_rate`,`execute_rate`,`mape`,`ec`,`er`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079397813208256514,2079397813145341954,4000000000000000101,2079397804341497858,'SUCCESS',1,0.00,0.00,100.00,72.27,13.2785,100.00,'2026-07-21T10:47:27','2026-07-21T10:47:27',NULL,NULL,0,NULL);
INSERT INTO `joint_optimization_plan` (`id`,`task_id`,`schedule_id`,`energy_plan_id`,`status`,`recommended`,`cost_reduction_rate`,`energy_reduction_rate`,`execute_rate`,`mape`,`ec`,`er`,`create_time`,`update_time`,`create_by`,`update_by`,`deleted`,`remark`) VALUES (2079406435229274115,2079406435229274114,4000000000000000101,2079406426396069891,'SUCCESS',1,0.00,0.00,70.83,0.00,13.2785,70.83,'2026-07-21T11:21:42','2026-07-21T11:21:42',NULL,NULL,0,NULL);

-- Table structure for joint_optimization_timeseries
DROP TABLE IF EXISTS `joint_optimization_timeseries`;
CREATE TABLE `joint_optimization_timeseries` (
  `id` bigint NOT NULL COMMENT '明细ID',
  `optimize_id` bigint NOT NULL COMMENT '协同优化方案ID',
  `timestamp` datetime NOT NULL COMMENT '时间点',
  `planned_output` decimal(14,2) DEFAULT NULL COMMENT '计划产量，t',
  `electricity_consumption` decimal(14,4) DEFAULT NULL COMMENT '用电量，kWh',
  `steam_consumption` decimal(14,4) DEFAULT NULL COMMENT '蒸汽用量，单位待确认',
  `carbon_emission_tco2` decimal(14,6) DEFAULT NULL COMMENT '碳排放，tCO2',
  `energy_cost` decimal(14,2) DEFAULT NULL COMMENT '能源成本',
  PRIMARY KEY (`id`),
  KEY `idx_optimize_time` (`optimize_id`,`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同优化时序明细表';

-- Data for joint_optimization_timeseries
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813208256515,2079397813208256514,'2026-07-17T16:10:30',0.15,17.0800,1.9500,0.133224,356.98);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813208256516,2079397813208256514,'2026-07-17T17:10:30',0.95,19.9600,2.3500,0.155688,429.99);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813208256517,2079397813208256514,'2026-07-17T18:10:30',0.15,22.8400,1.9500,0.178152,358.99);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813208256518,2079397813208256514,'2026-07-17T19:10:30',0.44,17.0800,2.3500,0.133224,428.98);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813208256519,2079397813208256514,'2026-07-17T20:10:30',0.15,23.1600,1.9500,0.180648,359.11);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365378,2079397813208256514,'2026-07-17T21:10:30',0.15,26.0400,2.3500,0.203112,432.11);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365379,2079397813208256514,'2026-07-17T22:10:30',0.95,20.2800,1.9500,0.158184,358.10);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365380,2079397813208256514,'2026-07-17T23:10:30',0.15,23.1600,2.3500,0.180648,431.11);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365381,2079397813208256514,'2026-07-18T00:10:30',0.95,23.6400,1.9500,0.184392,366.37);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365382,2079397813208256514,'2026-07-18T01:10:30',0.15,17.0800,2.3500,0.133224,434.10);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365383,2079397813208256514,'2026-07-18T02:10:30',0.95,19.9600,1.9500,0.155688,363.97);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079397813275365384,2079397813208256514,'2026-07-18T03:10:30',0.15,22.8400,2.3500,0.178152,437.85);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435229274116,2079406435229274115,'2026-07-17T16:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435229274117,2079406435229274115,'2026-07-17T17:10:30',0.95,12.6146,0.0631,0.007190,19.55);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435229274118,2079406435229274115,'2026-07-17T18:10:30',0.15,1.9918,0.0100,0.001135,3.88);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435229274119,2079406435229274115,'2026-07-17T19:10:30',0.44,5.7990,0.0290,0.003305,11.31);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188674,2079406435229274115,'2026-07-17T20:10:30',0.15,1.9918,0.0100,0.001135,3.88);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188675,2079406435229274115,'2026-07-17T21:10:30',0.15,1.9918,0.0100,0.001135,3.88);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188676,2079406435229274115,'2026-07-17T22:10:30',0.95,12.6146,0.0631,0.007190,19.55);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188677,2079406435229274115,'2026-07-17T23:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188678,2079406435229274115,'2026-07-18T00:10:30',0.95,12.6146,0.0631,0.007190,15.77);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188679,2079406435229274115,'2026-07-18T01:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188680,2079406435229274115,'2026-07-18T02:10:30',0.95,12.6146,0.0631,0.007190,15.77);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188681,2079406435229274115,'2026-07-18T03:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188682,2079406435229274115,'2026-07-18T04:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188683,2079406435229274115,'2026-07-18T05:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188684,2079406435229274115,'2026-07-18T06:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188685,2079406435229274115,'2026-07-18T07:10:30',0.15,1.9918,0.0100,0.001135,2.49);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188686,2079406435229274115,'2026-07-18T08:10:30',0.95,12.6146,0.0631,0.007190,19.55);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435292188687,2079406435229274115,'2026-07-18T09:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297537,2079406435229274115,'2026-07-18T10:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297538,2079406435229274115,'2026-07-18T11:10:30',0.95,12.6146,0.0631,0.007190,19.55);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297539,2079406435229274115,'2026-07-18T12:10:30',0.95,12.6146,0.0631,0.007190,19.55);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297540,2079406435229274115,'2026-07-18T13:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297541,2079406435229274115,'2026-07-18T14:10:30',0.15,1.9918,0.0100,0.001135,3.09);
INSERT INTO `joint_optimization_timeseries` (`id`,`optimize_id`,`timestamp`,`planned_output`,`electricity_consumption`,`steam_consumption`,`carbon_emission_tco2`,`energy_cost`) VALUES (2079406435359297542,2079406435229274115,'2026-07-18T15:10:30',0.15,1.9918,0.0100,0.001135,3.09);

-- Table structure for constraint_conflict
DROP TABLE IF EXISTS `constraint_conflict`;
CREATE TABLE `constraint_conflict` (
  `id` bigint NOT NULL COMMENT '冲突ID',
  `optimize_id` bigint NOT NULL COMMENT '协同优化方案ID',
  `conflict_type` varchar(64) NOT NULL COMMENT '冲突类型',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `description` varchar(1000) DEFAULT NULL COMMENT '冲突说明',
  `resolved` tinyint NOT NULL DEFAULT '0' COMMENT '是否解决',
  PRIMARY KEY (`id`),
  KEY `idx_conflict_optimize` (`optimize_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='约束冲突记录表';

-- Data for constraint_conflict
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435359297543,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-17T17:10:30','2026-07-17T18:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435359297544,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-17T22:10:30','2026-07-17T23:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435359297545,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-18T00:10:30','2026-07-18T01:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435359297546,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-18T02:10:30','2026-07-18T03:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435426406402,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-18T08:10:30','2026-07-18T09:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435426406403,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-18T11:10:30','2026-07-18T12:10:30','能源设备输出超过 80MW 上限',0);
INSERT INTO `constraint_conflict` (`id`,`optimize_id`,`conflict_type`,`start_time`,`end_time`,`description`,`resolved`) VALUES (2079406435426406404,2079406435229274115,'ENERGY_OUTPUT_LIMIT','2026-07-18T12:10:30','2026-07-18T13:10:30','能源设备输出超过 80MW 上限',0);
