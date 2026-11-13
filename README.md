> 2026-11-12：项目已加入渐进式服务化实现。独立应用、Compose、任务恢复与 Agent 证据语义的当前运行入口见 [微服务运行说明](docs/microservices/README.md)，实际验证范围见 [验收报告](docs/microservices/verification-report.md)。下面保留原项目说明供业务与算法资料查阅。

# 生产-能源交互式优化平台 · 后端（AI-challenge）

> 面向钢铁流程工业的「生产排产 → 能源运行 → 协同评估 → 实时调控」闭环后端。
> Spring Boot 3.2.7 多模块单体，算法以 Python 子进程方式集成，结果统一落库，前端通过任务表轮询进度。

---

## 目录

| 序号 | 章节 | 内容 |
| --- | --- | --- |
| 1 | [项目简介](#1-项目简介) | 定位、核心能力、关键指标 |
| 2 | [技术栈](#2-技术栈) | 框架与版本 |
| 3 | [整体架构](#3-整体架构) | 模块划分、请求链路 |
| 4 | [目录结构](#4-目录结构) | 包结构与职责 |
| 5 | [核心业务流程](#5-核心业务流程) | 排产、能源、协同、MPC、Agent |
| 6 | [算法集成](#6-算法集成) | 子进程契约与输出格式 |
| 7 | [数据库设计](#7-数据库设计) | 25 张表与初始化顺序 |
| 8 | [接口文档](#8-接口文档) | 鉴权、响应体、57 个接口 |
| 9 | [定时任务](#9-定时任务) | 后台线程与开关 |
| 10 | [配置说明](#10-配置说明) | 全部环境变量 |
| 11 | [本地运行](#11-本地运行) | 从建库到联调 |
| 12 | [测试](#12-测试) | 单元测试与集成测试 |
| 13 | [设计约定](#13-设计约定与已知约束) | 幂等、状态纠正、口径 |
| 14 | [常见问题](#14-常见问题) | FAQ |
| 15 | [相关文档](#15-相关文档) | docs 索引 |

---

## 1. 项目简介

本仓库是「AI 赋能降本增效——创新型生产-能源交互式优化技术」赛题的后端实现。系统以钢铁企业的**日级生产排产**为起点，向下衔接**能源介质（电、蒸汽）运行方案**，再通过**协同优化评估**衡量降本降耗效果，最后由**滚动 MPC** 把日级计划拆解为分钟级调控指令；并额外提供基于 Spring AI 的**工业决策 Agent**，对上述结果做自然语言解读与诊断。

### 核心能力

| 能力 | 说明 |
| --- | --- |
| 生产排产 | 按订单需求与历史能耗，调用算法生成 24 小时逐时产量与电耗系数（EC）计划 |
| 能源运行 | 基于分时电价、锅炉/汽机出力约束，生成逐时能源成本与碳排测算 |
| 协同优化 | 对排产方案与能源方案做联合评估，输出降本率、降耗率、MAPE、ER 与约束冲突 |
| 实时调控 | 每分钟读取最新采集点，对锅炉负荷、汽机出力、外购电、功率因数做滚动修正 |
| 数据采集 | 内置模拟采集装置（历史样本回放），也可由现场程序通过 HTTP 推送实时点位 |
| 工业 Agent | 大模型 + Tool Calling 读取业务库，输出结构化建议；模型不可用时自动规则兜底 |
| 系统管理 | JWT 登录、角色鉴权、用户/角色/系统配置/审计日志管理 |

### 关键指标口径（主案例 `2026-07-17`）

| 指标 | 数值 | 含义 |
| --- | --- | --- |
| EC_baseline | `14.00 kWh/t` | 优化前吨钢电耗 |
| EC_optimized | `13.2785 kWh/t` | 优化后吨钢电耗 |
| EC_reduction | `5.15%` | 吨钢电耗降低率 |
| MAPE | `3.55%` | 算法预测平均绝对百分比误差 |
| 仿真精度 | `96.45%` | 后端返回 `simulationAccuracy = 100 - mape` |
| ER | `100%` | 方案可执行率 |
| 锅炉负荷 | `20–80 MW` | 硬约束区间 |
| 汽机出力 | `5–30 MW` | 硬约束区间 |

> 注意：`mape` 字段是**误差**，越小越好；前端展示「仿真精度」时应使用后端计算的 `simulationAccuracy`。

---

## 2. 技术栈

| 分类 | 技术 | 版本 | 用途 |
| --- | --- | --- | --- |
| 语言/运行时 | Java | 17 | 主语言 |
| 构建 | Maven | 3.9+ | 多模块构建 |
| 框架 | Spring Boot | 3.2.7 | Web、调度、AOP、校验 |
| 持久层 | MyBatis-Plus | 3.5.7 | BaseMapper、分页、逻辑删除 |
| 连接池 | Druid | 1.2.23 | 数据源监控与池化 |
| 数据库 | MySQL | 8.0 | 业务库 `challenge_cup_energy` |
| 大模型 | Spring AI | 1.0.6 | ChatClient、Tool Calling、结构化输出 |
| 认证 | jjwt | 0.12.6 | HS256 JWT 签发与校验 |
| 密码 | spring-security-crypto | 随 Boot | BCrypt（兼容历史明文） |
| 文档 | Knife4j / springdoc | 4.5.0 | Swagger UI、OpenAPI JSON |
| 工具 | Hutool / fastjson2 | 5.8.29 / 2.0.52 | 通用工具、JSON 序列化 |
| 简化 | Lombok | 1.18.36 | `@Data`、`@RequiredArgsConstructor` |
| 测试 | JUnit 5 + H2 | Boot 3.2.7 | 单元测试、内存库集成测试 |

---

## 3. 整体架构

### 3.1 模块划分

| 模块 | 职责 | 主要产物 |
| --- | --- | --- |
| `AI-common` | 无业务依赖的公共能力 | `Result`、`PageResult`、`BusinessException`、`StatusCode`、`TaskStatus`、`TaskType`、`UserRole`、`JwtUtils` |
| `AI-model` | 数据模型 | 25 个 `entity`、12 个 `dto`、30 余个 `vo` |
| `AI-mapper` | 数据访问 | 25 个 `BaseMapper` 接口（无 XML，全部 LambdaQueryWrapper） |
| `AI-service` | 业务编排 | 14 个服务接口 + 14 个实现、`agent` 子包、`support` 工具 |
| `AI-web` | 接入层与启动 | `Application`、11 个 Controller、8 个配置/基础设施类、2 个定时任务、1 个日志切面、`application.yml` |

### 3.2 依赖方向

```
AI-web  ──►  AI-service  ──►  AI-mapper  ──►  AI-model
   │              │                              ▲
   └──────────────┴──────────────────────────────┘
                        AI-common（被所有模块依赖）
```

- 上层可依赖下层，下层不反向依赖；`AI-common` 不依赖任何业务模块。
- 所有 Mapper 通过 `@MapperScan("com.xq.mapper")` 注册，无 Mapper XML。

### 3.3 一次请求的完整链路

```
前端 (React/Vite)
   │  Authorization: Bearer <JWT>
   ▼
AuthInterceptor ── 校验 Token / 角色 ──► 写入 request 属性 userId、username、role
   ▼
Controller ── 参数校验 (@Valid) ──► Service
   ▼                                  │
OperationLogAspect 记录写操作           ├─► Mapper ──► MySQL
（POST/PUT/DELETE/PATCH）              ├─► ProcessBuilder ──► Python 算法（异步）
   ▼                                  └─► 定时任务 / 模拟采集
Result<T> 统一响应 ──► GlobalExceptionHandler 兜底异常
```

**异步任务模型**：所有耗时算法调用都不阻塞 HTTP 请求。后端先落一条 `algorithm_task`（`PENDING`），提交到 `algorithmTaskExecutor` 线程池执行，接口立即返回 `TaskVO`；前端轮询 `GET /api/tasks/{taskId}` 获取 `PENDING / RUNNING / SUCCESS / FAILED`。

---

## 4. 目录结构

```text
AI-challenge/
├── pom.xml                          # 父 POM，统一版本与插件
├── README.md                        # 本文档
├── AI-common/                       # 公共层
│   └── src/main/java/com/xq/common/
│       ├── constant/                # TaskType、TaskStatus、StatusCode、UserRole
│       ├── exception/               # BusinessException
│       ├── result/                  # Result、PageResult
│       └── utils/                   # JwtUtils
├── AI-model/
│   └── src/main/java/com/xq/model/
│       ├── dto/                     # 12 个入参对象
│       ├── entity/                  # 25 个表映射实体
│       └── vo/                      # 30+ 个出参对象（含嵌套汇总）
├── AI-mapper/
│   └── src/main/java/com/xq/mapper/ # 25 个 Mapper 接口
├── AI-service/
│   └── src/main/java/com/xq/service/
│       ├── *.java                   # 服务接口
│       ├── impl/                    # 业务实现（排产/能源/协同/MPC/报表…）
│       ├── agent/                   # IndustrialAgentTools、IndustrialPromptFactory、AgentFallbackService
│       └── support/                 # PasswordUtils
├── AI-web/
│   └── src/
│       ├── main/java/com/xq/
│       │   ├── Application.java     # @SpringBootApplication + @MapperScan + @EnableScheduling
│       │   ├── web/aspect/          # OperationLogAspect
│       │   ├── web/config/          # Auth、CORS、Jackson、Knife4j、MyBatis-Plus、线程池、异常处理
│       │   ├── web/controller/      # 11 个 REST 控制器
│       │   └── web/task/            # MockEnergyDeviceTask、RealtimeMpcControlTask
│       ├── main/resources/application.yml
│       └── test/java/com/xq/        # 13 个测试类（含 H2 集成测试）
├── database/
│   ├── sql/                         # 建表、种子数据、重置脚本
│   └── backups/                     # 清理前备份
├── data/
│   ├── algorithm/                   # generate_plan.py 与钢铁能耗样本
│   └── mpc/                         # MPC 结果 CSV
├── docs/                            # 有效文档与历史讨论稿
└── scripts/                         # 数据转换、补表脚本
```

---

## 5. 核心业务流程

### 5.1 日级排产（主流程）

```text
① POST /api/production/schedules/generate-from-collected-data
       { "scheduleDate": "2026-07-17", "plannedQuantity": 1200 }
                    │
② generateFromCollectedData
       • planHorizon 必须为 24，plannedQuantity > 0
       • ensureSimpleOrder：以订单号 AUTO-<date> upsert 订单（到期时间当日 23:59:59）
       • 未来日期只建“预约”任务（frontendRequestJson: reservation=true），到期由定时器触发
                    │
③ generateScheduleForDate（当日或过去）
       • 幂等：同日已有非 FAILED 方案 → 409「日期 X 已有排产方案，无需重复生成」
       • 取最近 7 天能源数据（优先 source=MOCK_DEVICE 的 1 分钟粒度，行数需 ≥ 7×1440）
       • 拼 input.csv（头 timestamp,elec,steam）+ 导出 orders.csv
                    │
④ runExternalScheduleTask（异步线程 algorithm-task-N）
       • 工作目录 target/algorithm-tasks/{taskId}/：input.csv、orders.csv、algorithm.log
       • ProcessBuilder：python generate_plan.py <input> <output> [orders]
       • 超时 180s（可配）；读 output.json，status=error 直接失败
                    │
⑤ importDailyPlan
       • 校验 timestamp、plan_horizon=24、schedule 必须 24 条
       • 写 production_schedule_plan + production_schedule_detail
       • EC_optimized 缺失时用 total_energy / total_production 反推
       • 明细 elecForecast = production × EC_optimized
       • 写 evaluation_metric（bizType=SCHEDULE，mape / ec_before / ec_after / er）
                    │
⑥ 收尾
       • markOrdersScheduled：过去日期订单置 COMPLETED，否则 SCHEDULED
       • 若 output.json 含 energy_plan → 导入 energy_plan / energy_plan_detail
       • 触发 PlanAutoGenerationService 派生协同评估
       • realtime_control 不做结构化入库，仅保留在 algorithm_response_json
                    │
⑦ GET /api/tasks/{taskId} 轮询 → SUCCESS/FAILED
```

**状态纠正**：读取方案时，历史日期强制 `SUCCESS`，当天与未来强制 `RUNNING`，避免演示数据长期停留在 `PENDING`。

### 5.2 能源运行方案

| 场景 | 入口 | 数据来源 | 成本公式 |
| --- | --- | --- | --- |
| 手动生成 | `POST /api/energy/plan/generate` | 当日 `energy_realtime_data` 按小时聚合 | `Σ(电耗 × 分时电价) + Σ(蒸汽 × 180)` |
| 无实时数据 | 同上 | 回落当日排产方案逐时派生 | 同上 |
| 排产派生 | 排产成功后自动触发（同步） | 排产明细 `elecForecast` | 同上 |

- 分时电价：谷段 `00:00-08:00`、`22:00-24:00` 为 `0.35`；峰段 `08:00-22:00` 为 `1.05`；常量另设平段 `0.65`。
- 派生规则：`boiler_output = clamp(20 + production×60, 20, 80)`、`steam = elec×0.005`、`carbon = elec×0.00057`。
- 同一 `source_schedule_id` 重复派生时，旧的后端派生方案会被替换，保证幂等。
- 若既无实时数据也无排产方案：400「没有可用于生成能源运行方案的数据，请先导入实时能源数据或日级排产方案」。

### 5.3 协同优化评估

```text
POST /api/optimize/joint/generate { scheduleId, energyPlanId, objectiveWeights }
  → 校验方案存在且明细非空
  → baselineEnergy = totalProduction × ecBaseline
  → optimizedEnergy = schedule.totalEnergy（为 0 时用 Σ elecForecast）
  → energyReductionRate = (baseline − optimized) / baseline × 100
  → baselineCost 按分时电价 + 蒸汽单价累加，costReductionRate 同理
  → MAPE = Σ|actual − predicted| / Σ|actual| × 100
  → ER：有算法 EC 指标时取 100；否则统计锅炉出力落在 20–80 MW 的小时占比
  → 逐小时写 joint_optimization_timeseries；越界写 constraint_conflict
  → plan.recommended = 1，任务置 SUCCESS
```

- **冲突类型**：`ENERGY_OUTPUT_LIMIT`，描述「能源设备输出超出锅炉负荷 20-80MW 约束范围」。
- **对比**：`POST /api/optimize/compare` 以第一个 ID 为基准，输出 `costReductionDelta`、`energyReductionDelta`、`mapeDelta` 及 `bestCost / bestEnergy / bestMape` 三个最优 ID。
- **帕累托前沿**：`GET /api/optimize/pareto-frontier` 取最近 50 条，X 轴降本率、Y 轴降耗率、气泡指标 MAPE，并标记非支配解。

### 5.4 滚动 MPC（分钟级）

`RealtimeMpcControlTask` 默认每 60s 执行一次 `runRealtimeMpcTick()`：

1. 读取最新能源采集点，与当天（或最近）`SUCCESS` 排产方案的计划电量比较，得到偏差率（clamp ±100%）。
2. `锅炉基线 = 30 + actualElec×8`、`汽机基线 = 10 + actualElec×3`，修正系数分别 `0.12 / 0.08`。
3. 斜坡限幅：锅炉每轮最多 ±5 MW，汽机每轮最多 ±2 MW；最终校验锅炉 20–80 MW、汽机 5–30 MW。
4. 外购电 `= actualElec×60 − turbine×1000×0.005`；功率因数目标 0.95，clamp 到 0.90–0.99。
5. 按 `(control_date, control_time)` upsert 到 `mpc_realtime_control`。

找不到排产方案时使用内存基线（`production = 100 t/h`、`EC = 14.00`），保证演示链路不中断。

### 5.5 工业优化 Agent

```text
AgentController
  ├─ POST /api/agent/chat
  ├─ POST /api/agent/optimize/{optimizeId}/explain
  ├─ POST /api/agent/energy-plan/{energyPlanId}/diagnose
  └─ POST /api/agent/report/daily
        │
        ▼
AgentServiceImpl.callAgent(scene, dto)
  ├─ agent.llm.enabled=false 或 ChatClient 不可用 → AgentFallbackService（规则兜底）
  └─ ChatClient.builder()
        .system(IndustrialPromptFactory.systemPrompt())
        .user(IndustrialPromptFactory.userPrompt(scene, dto))
        .tools(IndustrialAgentTools)          // 5 个 @Tool 方法
        .call().entity(AgentAdviceVO.class)   // 结构化输出
        │  异常 → 降级，source=RULE_FALLBACK_AFTER_AI_ERROR
        ▼
     enrich() 补齐 sessionId / scene / source / riskLevel / 各列表 / generatedAt
```

`IndustrialAgentTools` 暴露 5 个只读工具：`queryJointOptimization`、`queryJointOptimizationEvaluation`、`queryOptimizationConflicts`、`queryEnergyPlan`、`queryDailyReportSummary`。工具内部异常会转成 JSON 错误返回给模型，而不是直接抛出。

---

## 6. 算法集成

### 6.1 调用方式

后端不内嵌算法，而是以**子进程 + 文件交换**的方式调用外部算法工程：

```text
python <ALGORITHM_SCRIPT> <input.csv> <output.json> [orders.csv]
matlab -batch "addpath('<working-dir>'); main('<input.csv>','<output.json>')"
```

MATLAB 模式固定调用工作目录下的 `main.m`；Python 模式使用 `algorithm.script` 指定的脚本名。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `algorithm.runtime` | `python` | `python` / `matlab` |
| `algorithm.command` | `python` | Python 可执行命令 |
| `algorithm.script` | `generate_plan.py` | 脚本文件名（相对工作目录） |
| `algorithm.working-dir` | `data/algorithm` | 子进程工作目录 |
| `algorithm.task-dir` | `target/algorithm-tasks` | 每次任务的输入/输出/日志目录 |
| `algorithm.timeout-seconds` | `180` | 超时时间 |
| `algorithm.training-days` | `7` | 训练/回溯天数 |

### 6.2 输入 CSV

```csv
timestamp,elec,steam
2026-07-10 00:00:00,1012.35,24.80
2026-07-10 00:01:00,1015.02,24.91
```

- 表头别名归一：`date/datetime` → `timestamp`，`Usage_kWh/power/power_kw` → `elec`。
- 支持 `%Y-%m-%d %H:%M[:%S]`、`%d/%m/%Y`、`%m/%d/%Y`、ISO 等时间格式。
- 1 分钟粒度直接使用；其它粒度用 PCHIP 插值到 1 分钟后再聚合为 24 个小时点。

订单 CSV（可选）：`order_no, planned_quantity, due_time`，有正需求时 `demand_source=production_orders`，否则按历史能耗估算需求。

### 6.3 输出 JSON（节选）

```jsonc
{
  "daily_plan": {
    "timestamp": "2026-07-17 00:00:00",
    "plan_horizon": 24,
    "unit": "hour",
    "demand_source": "production_orders",
    "EC_baseline": 14.0,
    "EC_optimized": 13.2785,
    "EC_reduction": 5.15,
    "total_demand": 1200.0,
    "total_production": 1180.0,
    "total_energy": 15668.6,
    "optimal_temperature": 1180.0,
    "optimal_speed": 5.2,
    "solver_status": "optimal",
    "solver_exitflag": 1,
    "algorithm_runtime": "python",
    "schedule": [{ "hour": 0, "demand": 50.0, "production": 49.0 }]
  },
  "energy_plan": {
    "plan_date": "2026-07-17",
    "objective": "cost_energy_carbon_executability",
    "electric_price_mode": "PEAK_VALLEY",
    "time_interval": 60,
    "electricity_cost": 12345.6,
    "steam_cost": 2345.6,
    "total_energy_cost": 14691.2,
    "details": [{
      "hour": 0, "boiler_output": 45.0, "turbine_output": 12.0,
      "grid_purchase": 100.0, "electricity_consumption": 1030.0,
      "steam_consumption": 25.0, "carbon_emission_tco2": 0.587,
      "energy_cost": 640.5
    }]
  }
}
```

算法内部关键公式：

```text
electricity   = production × optimized_coeff
boiler_output = clamp(20 + production×60, 20, 80)      # MW
steam         = boiler_output×0.012 + production×0.18  # t
carbon        = electricity×0.00057                    # tCO2
turbine       = clamp(8 + production×0.5, 5, 30)       # MW
grid_purchase = max(0, electricity − boiler_output×0.35)
```

### 6.4 失败处理

| 情况 | 行为 |
| --- | --- |
| 脚本非 0 退出 / `status=error` | 任务置 `FAILED`，`error_message` 记录 stderr 尾部 |
| 超时 | 强杀子进程，任务 `FAILED`，提示超时秒数 |
| 输出结构不合法 | 抛 `ALGORITHM_VALIDATION_FAILED` 语义错误，任务 `FAILED` |
| MATLAB 模式 | 将 `algorithm.runtime` 改为 `matlab`，并配置 `algorithm.matlab-command` |

每次运行的文件都保留在 `target/algorithm-tasks/{taskId}/`，便于回溯问题。

---

## 7. 数据库设计

### 7.1 表清单（25 张）

| 分组 | 表 | 说明 |
| --- | --- | --- |
| 系统 | `sys_user`、`sys_role`、`sys_user_role`、`sys_operation_log` | 账号、角色、关联、审计日志 |
| 生产 | `production_line`、`production_equipment`、`production_order` | 产线、设备、订单 |
| 任务 | `algorithm_task` | 算法任务与请求/响应快照 |
| 排产 | `production_schedule_plan`、`production_schedule_detail` | 日方案头 + 24 小时明细 |
| 能源 | `energy_medium`、`energy_equipment`、`energy_realtime_data`、`steel_energy_dataset`、`energy_production_history` | 介质、设备、实时点位、原始数据集、历史产量能耗 |
| 能源方案 | `energy_plan`、`energy_plan_detail` | 逐时成本、碳排 |
| 协同 | `joint_optimization_plan`、`joint_optimization_timeseries`、`constraint_conflict` | 评估头、时序、冲突 |
| 调控 | `mpc_realtime_control` | 分钟级 MPC 指令 |
| 评价/报表 | `evaluation_metric`、`report_statistic` | 指标、日报月报 |
| 其他 | `warning_record`、`system_config` | 告警、配置 |

### 7.2 通用约定

- 主键统一 `BIGINT`，**不使用自增**，由 MyBatis-Plus `assign_id`（雪花）生成。
- 业务表统一带 `create_time`、`update_time`、`create_by`、`update_by`、`deleted`、`remark`。
- 逻辑删除：`deleted=1` 视为删除，所有查询自动拼接条件。
- 字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
- 时间统一 `Asia/Shanghai`，Jackson 输出格式 `yyyy-MM-dd HH:mm:ss`。

### 7.3 关键唯一键与幂等

| 表 | 唯一键 | 用途 |
| --- | --- | --- |
| `production_order` | `uk_order_no` | 订单号唯一 |
| `production_schedule_detail` | `(schedule_id, hour_index)` | 每小时一行 |
| `energy_realtime_data` | `(timestamp, source)` | 采集点幂等 upsert |
| `energy_plan` | `source_schedule_id`（语义替换，非唯一索引） | 同一排产派生的能源方案幂等覆盖 |
| `mpc_realtime_control` | `(control_date, control_time)` | 每分钟一行 |
| `report_statistic` | `(stat_date, stat_type)` | 日报/月报唯一 |
| `system_config` | `config_key` | 配置键唯一 |

### 7.4 初始化顺序

```sql
SOURCE database/sql/数据库建表SQL.sql;      -- 建表（必需）
SOURCE database/sql/reset_all.sql;          -- 需要重置时执行（会清空业务表）
SOURCE database/sql/seed_demo_data.sql;     -- 账号/角色/产线/设备/订单/报表
SOURCE database/sql/seed_steel_data.sql;    -- 15 分钟粒度能耗数据
SOURCE database/sql/seed_steel_1min.sql;    -- 1 分钟粒度能耗样本（约 40MB，耗时较长）
SOURCE database/sql/seed_mpc_control.sql;   -- MPC final 结果（2026-07-17）
SOURCE database/sql/seed_mpc_hourly.sql;    -- MPC hourly 结果（2026-07-18）
```

仅做页面联调时，最少执行：`数据库建表SQL.sql` + `seed_demo_data.sql` + 两个 MPC 脚本；启动后模拟装置会自动补齐 `MOCK_DEVICE` 来源的分钟级数据。

种子账号：`admin / 123456`（`PasswordUtils` 兼容历史明文密码，新建用户使用 BCrypt）。

主案例方案 ID：`production_schedule_plan.id = 4000000000000000101`（对应 `daily_plan_v3.2.json`）。

---

## 8. 接口文档

### 8.1 通用约定

| 项目 | 约定 |
| --- | --- |
| Base URL | `http://localhost:8080/api` |
| 认证 | `Authorization: Bearer <JWT>`，仅 `POST /api/auth/login` 与文档路径免登录 |
| 请求体 | `application/json; charset=UTF-8`；文件上传为 `multipart/form-data` |
| 时间格式 | `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss` |
| 分页参数 | `pageNum`（默认 1）、`pageSize`（默认 10），另支持 `date`、`status`、`startDate`、`endDate`、`interval` |

统一响应体：

```json
{ "code": 200, "message": "success", "data": { } }
```

| 状态码 | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 / 请求体非法 / 日期格式错误 |
| 401 | 未登录或 Token 失效 |
| 403 | 无权限（如非管理员访问用户管理） |
| 404 | 资源不存在 |
| 409 | 业务冲突（同一天已有排产方案） |
| 415 | 文件格式不符合要求 |
| 500 | 系统内部错误 |
| 600 / 601 / 602 | 预留：算法调用失败 / 超时 / 结果校验失败 |

### 8.2 认证与任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户名密码登录，返回 `token`、`userId`、`username`、`role`、`roles` |
| GET | `/api/auth/userinfo` | 依据请求头 Token 返回当前用户信息 |
| POST | `/api/auth/logout` | 无状态登出（前端清除 Token 即可） |
| GET | `/api/tasks/{taskId}` | 查询算法任务状态与进度 |

### 8.3 首页

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/dashboard/overview` | 今日/本月能耗、生产进度、能源负荷率、方案执行率、预警列表 |

### 8.4 生产排产

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/production/orders` | 分页查询订单 |
| POST | `/api/production/orders/import` | 上传订单 CSV（`orderNo, productName, plannedQuantity, dueTime`） |
| POST | `/api/production/schedule/generate` | 兼容入口：带 `plannedQuantity` 走已采集数据，否则走演示数据分支 |
| POST | `/api/production/schedules/generate-from-collected-data` | **正式入口**：读最近 7 天采集数据生成当日/历史日方案 |
| POST | `/api/production/schedules/generate-from-raw-data` | 兜底入口：上传能源 CSV（≥30 天，2880 行）生成方案 |
| GET | `/api/production/schedules/{scheduleId}` | 方案详情（含 24 小时明细） |
| GET | `/api/production/schedules/date/{date}` | 按日期查方案 |
| GET | `/api/production/schedule/history` | 分页查询方案历史 |
| POST | `/api/production/schedules/compare` | 多方案对比（产量、能耗、EC、达成率） |
| POST | `/api/production/schedules/import-daily-plan` | 直接导入算法 `daily_plan` JSON（联调用） |

排产请求示例：

```json
{ "scheduleDate": "2026-07-17", "plannedQuantity": 1200 }
```

### 8.5 能源运行

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/energy/realtime` | 实时点位（默认最近 50 条，按时间升序返回） |
| POST | `/api/energy/realtime/push` | 外部采集推送，按 `(timestamp, source)` 幂等 upsert |
| POST | `/api/energy/realtime/mock` | 手动写入一条当前分钟模拟点位 |
| POST | `/api/energy/plan/generate` | 生成能源运行方案（异步任务） |
| GET | `/api/energy/plan/{planDate}` | 查询指定日期方案 |
| GET | `/api/energy/plans/history` | 方案历史分页 |
| GET | `/api/energy/device-status` | 锅炉/汽机/外购电设备状态（读最新 MPC 指令） |
| GET | `/api/energy/load-forecast` | 负荷预测曲线（电、汽、成本） |
| GET | `/api/energy/consumption-trend` | 能耗趋势（按 `interval` 切换小时/天粒度） |
| GET | `/api/energy/analysis` | 能耗分析汇总（总量、成本、功率因数、MAPE、EC） |
| GET | `/api/energy/trend` | 日报趋势（默认最近 31 天） |
| GET | `/api/energy/carbon-reduction` | 碳减排累计曲线 |

推送示例（字段支持别名）：

```json
{
  "timestamp": "2026-07-17 10:05:00",
  "electricityConsumption": 1012.35,
  "steamConsumption": 24.8,
  "carbonEmissionTco2": 0.577,
  "source": "COLLECTOR_01"
}
```

### 8.6 协同优化

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/optimize/joint/generate` | 创建协同评估任务（`scheduleId`、`energyPlanId`、`objectiveWeights`） |
| GET | `/api/optimize/joint/{optimizeId}` | 评估结果（产量/能耗汇总、策略、约束） |
| GET | `/api/optimize/tasks` | 协同任务分页 |
| GET | `/api/optimize/conflicts` | 约束冲突列表（`taskId` 或 `optimizeId`） |
| GET | `/api/optimize/evaluation/{optimizeId}` | 指标详情（MAPE、EC、ER、冲突数、时序数） |
| POST | `/api/optimize/compare` | 多方案对比 + 推荐方案 |
| GET | `/api/optimize/pareto-frontier` | 帕累托前沿点集 |

### 8.7 实时调控

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/realtime-control/latest` | 最新一条 MPC 指令 |
| GET | `/api/realtime-control/history` | 指令历史分页 |
| POST | `/api/realtime-control/tick` | 立即执行一次滚动 MPC |
| POST | `/api/realtime-control/import` | 导入 MPC 结果（支持数组或 `data/records/items/controls/result` 包装） |

### 8.8 分析与报表

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/analysis/correlation` | 6 变量 Pearson 相关矩阵（35,040 条 15 分钟样本） |
| GET | `/api/reports/optimization-effect` | 优化效果（EC、降耗率、MAPE、ER、降本、减排） |
| GET | `/api/reports/energy-analysis` | 能耗分析汇总 |
| GET | `/api/reports/energy-trend` | 能耗趋势 |
| GET | `/api/reports/carbon-reduction` | 碳减排累计 |
| GET | `/api/reports/export/{type}` | 导出 CSV，`type` ∈ `optimization-effect / energy-analysis / energy-trend / carbon-reduction` |

相关性矩阵变量顺序：用电量、滞后无功电量、超前无功电量、碳排放、滞后功率因数、超前功率因数。

### 8.9 系统管理（`/api/system/users` 相关接口仅 `ADMIN` 可访问）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/system/users` | 用户分页（含角色） |
| POST | `/api/system/users` | 新建用户（BCrypt 加密，默认启用） |
| PUT | `/api/system/users/{id}` | 修改密码/状态/角色 |
| DELETE | `/api/system/users/{id}` | 删除用户（逻辑删除） |
| GET | `/api/system/roles` | 角色列表（可按 `status` 过滤） |
| GET | `/api/system/logs` | 操作日志分页 |
| GET | `/api/system/config` | 系统配置列表（可按 `group` 过滤） |
| PUT | `/api/system/config` | 更新配置 |
| GET | `/api/system/data-points` | 产线/生产设备/能源设备下拉数据（`type`） |

### 8.10 工业 Agent

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/chat` | 通用问答，可传 `scene`、`question`、`optimizeId`、`energyPlanId` |
| POST | `/api/agent/optimize/{optimizeId}/explain` | 解读协同优化结果 |
| POST | `/api/agent/energy-plan/{energyPlanId}/diagnose` | 诊断能源方案 |
| POST | `/api/agent/report/daily` | 生成日报摘要 |

统一返回 `AgentAdviceVO`：

```json
{
  "sessionId": "demo-session",
  "scene": "OPTIMIZATION_EXPLAIN",
  "source": "SPRING_AI_TOOL_CALLING",
  "aiEnabled": true,
  "riskLevel": "LOW",
  "summary": "整体结论",
  "keyMetrics": ["核心指标"],
  "problems": ["发现的问题"],
  "suggestions": ["优化建议"],
  "evidence": ["数据来源"],
  "nextActions": ["下一步动作"],
  "rawAnswer": "补充说明",
  "generatedAt": "2026-07-17T12:00:00"
}
```

`source` 取值：`SPRING_AI_TOOL_CALLING`（大模型）、`RULE_FALLBACK`（未启用或未配置）、`RULE_FALLBACK_AFTER_AI_ERROR`（调用失败降级）。

---

## 9. 定时任务

| 任务 | 触发方式 | 作用 | 开关 |
| --- | --- | --- | --- |
| `MockEnergyDeviceTask` | `fixedDelay` 60s，首延迟 5s | 回放/生成能源点位写入 `energy_realtime_data`；启动时补齐 7 天 | `MOCK_DEVICE_ENABLED` |
| `RealtimeMpcControlTask` | `fixedDelay` 60s，首延迟 12s | 执行一次滚动 MPC，写入 `mpc_realtime_control` | `REALTIME_MPC_ENABLED` |
| `autoMarkPastEnergyPlansCompleted` | cron `energy-plan.auto-complete.cron`（默认 `0 0 1 * * ?`） | 把历史能源方案置 `SUCCESS` | 固定启用 |
| `autoGeneratePendingSchedules` | `fixedDelay` `schedule.auto-generate.interval-ms`（默认 1800000ms），首延迟 `schedule.auto-generate.initial-delay-ms`（默认 60000ms） | 扫描到期订单，逐日自动生成排产 | 固定启用 |

`autoGeneratePendingSchedules` 按「单日」捕获异常，某一天失败只记录日志并跳过，不影响其余日期。

---

## 10. 配置说明

配置文件：`AI-web/src/main/resources/application.yml`。**所有敏感项必须通过环境变量注入，禁止写死。**

### 10.1 服务与数据源

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/challenge_cup_energy?...` | 数据库连接 |
| `DB_USERNAME` | 空 | 数据库账号 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `MAX_UPLOAD_FILE_SIZE` | `50MB` | 单文件上限 |
| `MAX_UPLOAD_REQUEST_SIZE` | `50MB` | 单请求上限 |

### 10.2 认证

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | `steel-energy-demo-secret-change-me` | 签名密钥，生产必须替换为 ≥32 位随机串 |
| `JWT_EXPIRE_SECONDS` | `86400` | Token 有效期（秒） |

### 10.3 算法

| 环境变量 | 默认值 |
| --- | --- |
| `ALGORITHM_RUNTIME` | `python` |
| `ALGORITHM_COMMAND` | `python` |
| `ALGORITHM_SCRIPT` | `generate_plan.py` |
| `ALGORITHM_MATLAB_COMMAND` | `matlab` |
| `ALGORITHM_WORKING_DIR` | `data/algorithm` |
| `ALGORITHM_TASK_DIR` | `target/algorithm-tasks` |
| `ALGORITHM_TIMEOUT_SECONDS` | `180` |
| `ALGORITHM_TRAINING_DAYS` | `7` |

### 10.4 模拟采集与 MPC

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MOCK_DEVICE_ENABLED` | `true` | 无现场接口时保持开启 |
| `MOCK_DEVICE_MODE` | `replay` | `replay` 回放样本，否则正弦造数 |
| `MOCK_DEVICE_REPLAY_FILE` | `data/algorithm/steel_data_cleaned.csv` | 回放文件 |
| `MOCK_DEVICE_INTERVAL_MS` | `60000` | 写入间隔 |
| `MOCK_DEVICE_INITIAL_DELAY_MS` | `5000` | 首次延迟 |
| `MOCK_DEVICE_BACKFILL_ON_STARTUP` | `true` | 启动补齐 |
| `MOCK_DEVICE_BACKFILL_DAYS` | `7` | 补齐天数 |
| `REALTIME_MPC_ENABLED` | `true` | 滚动 MPC 开关 |
| `REALTIME_MPC_INTERVAL_MS` | `60000` | 滚动周期 |
| `REALTIME_MPC_INITIAL_DELAY_MS` | `12000` | 首次延迟 |

### 10.5 大模型

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_AI_OPENAI_API_KEY` | 空 | 仅环境变量注入 |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | 兼容 OpenAI 协议的网关可直接替换 |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o-mini` | 模型名 |
| `SPRING_AI_OPENAI_TEMPERATURE` | `0.2` | 温度 |
| `SPRING_AI_OPENAI_CHAT_ENABLED` | `true` | Spring AI Chat 开关 |
| `AGENT_LLM_ENABLED` | `true` | 关闭后强制走规则兜底 |

---

## 11. 本地运行

### 11.1 环境要求

- JDK 17
- Maven 3.9+
- MySQL 8.x（`utf8mb4`）
- Python 3.9+（`generate_plan.py` 仅用标准库，无需三方包）

### 11.2 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS challenge_cup_energy
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

随后按 [7.4 初始化顺序](#74-初始化顺序) 导入建表与种子脚本。

### 11.3 配置环境变量（PowerShell）

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/challenge_cup_energy?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:JWT_SECRET="至少32位的随机字符串"
$env:ALGORITHM_WORKING_DIR="D:\桌面\挑战杯\AI-challenge\data\algorithm"
$env:ALGORITHM_TASK_DIR="D:\桌面\挑战杯\AI-challenge\target\algorithm-tasks"
$env:MOCK_DEVICE_ENABLED="true"
$env:REALTIME_MPC_ENABLED="true"
# 可选：启用真实大模型
$env:SPRING_AI_OPENAI_API_KEY="sk-..."
```

### 11.4 编译与启动

```powershell
# 打包（跳过测试）
mvn -DskipTests package

# 方式一：Maven 启动
mvn -pl AI-web spring-boot:run

# 方式二：Jar 启动
java -jar AI-web/target/AI-web-1.0-SNAPSHOT.jar
```

启动成功后：

| 地址 | 用途 |
| --- | --- |
| `http://localhost:8080/api` | 后端 Base URL |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/doc.html` | Knife4j 中文文档 |

### 11.5 冒烟验证

```powershell
# 1. 登录
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"123456"}'

# 2. 携带 Token 访问
curl http://localhost:8080/api/dashboard/overview `
  -H "Authorization: Bearer <token>"

# 3. 查询主案例排产
curl http://localhost:8080/api/production/schedules/date/2026-07-17 `
  -H "Authorization: Bearer <token>"
```

### 11.6 前端联调

前端 `.env`：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=http://localhost:8080/api
```

若通过 Vite 代理，Base URL 保持 `/api`，代理目标指向 `http://localhost:8080`。跨域已在后端放开 `/api/**`（允许携带凭证）。

---

## 12. 测试

```powershell
mvn test                      # 全部测试
mvn -pl AI-web -am test       # 仅 Web 模块及其依赖
mvn -Dtest=AuthServiceImplTest test
```

| 测试类 | 覆盖点 |
| --- | --- |
| `AuthServiceImplTest` | 登录成功/失败、禁用账号、Token 生成 |
| `JwtUtilsTest` | 签发、解析、过期判断 |
| `AuthInterceptorTest` | 白名单、401、角色 403 |
| `ProductionScheduleServiceImplTest` | 幂等、CSV 校验、方案导入、状态纠正 |
| `EnergyPlanServiceImplTest` | 派生规则、成本计算、404 分支 |
| `JointOptimizationServiceImplTest` | 降本/降耗率、MAPE/ER、冲突 |
| `RealtimeControlServiceImplTest` | 斜坡限幅、功率因数 clamp、upsert |
| `DashboardServiceImplTest` | 指标聚合与缺省值 |
| `ReportServiceImplTest` | 报表口径与 CSV 导出 |
| `SystemServiceImplTest` | 用户创建/更新、角色同步 |
| `ControllerSmokeTest` | 控制器装配与路由冒烟 |
| `JacksonConfigTest` | 日期序列化格式与时区 |
| `DatabaseIntegrationTest` | H2（MySQL 模式）验证用户角色、能源、报表 SQL |

测试不依赖本机 MySQL：`DatabaseIntegrationTest` 使用 H2 内存库；其余均为 Mockito 单元测试。

---

## 13. 设计约定与已知约束

1. **算法即外部进程**：后端只负责传参、校验、入库，算法迭代不需要重启 Java 服务。
2. **一切结果可追溯**：`algorithm_task` 保存请求/响应 JSON、耗时、错误信息；每次运行的工作目录保留输入输出文件。
3. **幂等优先**：同一天排产 409 拒绝重复生成；能源点位按 `(timestamp, source)` upsert；MPC 按 `(control_date, control_time)` upsert；`seed_demo_data.sql` 使用 `ON DUPLICATE KEY UPDATE` 可重复执行。
4. **不做未来数据污染**：算法输出中的 `realtime_control` 不入 `mpc_realtime_control`，避免未来 `control_date` 干扰滚动 MPC。
5. **状态自适应**：历史方案强制 `SUCCESS`，当天/未来强制 `RUNNING`，避免演示时出现"历史仍在执行"。
6. **MAPE 是误差**：前端展示精度请使用 `simulationAccuracy = 100 - mape`。
7. **大模型可关断**：`AGENT_LLM_ENABLED=false` 或缺少 API Key 时，Agent 自动降级为规则分析，接口结构不变。
8. **密码兼容**：种子数据为明文 `123456`，`PasswordUtils.matches` 同时支持 BCrypt 与明文；新建/修改密码一律 BCrypt。
9. **角色收敛**：历史角色码 `SYSTEM_ADMIN / PRODUCTION_DISPATCHER / ENERGY_MANAGER` 分别归一为 `ADMIN / USER / USER`。
10. **无 Mapper XML**：所有查询基于 MyBatis-Plus `BaseMapper` 与 `LambdaQueryWrapper`，`mapper-locations` 配置保留但当前无 XML 文件。
11. **模拟装置不写生产数据**：仅写 `energy_realtime_data`，来源标记 `MOCK_DEVICE`，便于与真实采集数据区分。
12. **单库单服务**：当前为演示型单体，未引入消息队列与分布式事务；算法任务通过数据库任务表解耦。

### 已知边界

- 演示分支 `generateScheduleResult`（不传 `plannedQuantity` 时）使用构造数据，仅用于前端联调。
- `energy_production_history` 的 `qualified_output` 在当前数据集中为空。
- 协同评估的 MAPE 在有算法指标时固定取 `3.55`，属于展示口径而非每次实算。
- 报表导出为 CSV（UTF-8），未做 Excel 样式。

---

## 14. 常见问题

**Q1：启动报数据库连接失败？**
检查 `DB_URL`、账号密码与库名；确认 MySQL 已启动且库字符集为 `utf8mb4`。

**Q2：排产提示"能源数据不足"？**
`generateScheduleForDate` 需要最近 7 天、1 分钟粒度、≥10080 行数据。保持 `MOCK_DEVICE_ENABLED=true` 并等待启动回填，或先执行 `seed_steel_1min.sql`。

**Q3：任务一直 `RUNNING`？**
查看 `target/algorithm-tasks/{taskId}/algorithm.log` 与 `algorithm_task.error_message`；常见原因是 `python` 不在 PATH，或 `ALGORITHM_WORKING_DIR` 指向错误目录。

**Q4：接口 401？**
除登录与文档路径外都需要 `Authorization: Bearer <token>`；Token 默认 24 小时过期，前端需重新登录。

**Q5：能源方案没有数据？**
方案依赖当日 `energy_realtime_data` 或当日排产方案，两者都为空时会返回 400，请先生成排产或等待模拟装置写入。

**Q6：Agent 返回 `RULE_FALLBACK`？**
未配置 `SPRING_AI_OPENAI_API_KEY`、`AGENT_LLM_ENABLED=false`，或模型服务不可达。降级结果结构一致，仍包含建议与证据。

**Q7：端口被占用？**
修改 `application.yml` 的 `server.port`，或启动时加 `--server.port=8081`。

**Q8：中文乱码？**
源码与 SQL 均为 UTF-8；PowerShell 先执行 `chcp 65001` 并设置 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`。

---

## 15. 相关文档

| 文档 | 路径 |
| --- | --- |
| 启动说明 | `docs/当前有效文档/启动说明.md` |
| 数据库文件执行说明 | `docs/当前有效文档/数据库文件执行说明.md` |
| 数据库设计报告 | `docs/当前有效文档/数据库设计报告.md` |
| 数据呈现口径说明 | `docs/当前有效文档/数据呈现口径说明-前端与文档组.md` |
| 软件设计报告 | `docs/当前有效文档/软件设计报告.md` |
| 用户手册 | `docs/当前有效文档/用户手册.md` |
| Agent 接入说明 | `docs/AI_AGENT接入说明.md` |
| 历史讨论稿 | `docs/历史讨论稿/` |

---

## 附：模块速查

| 关注点 | 入口文件 |
| --- | --- |
| 启动类 | `AI-web/src/main/java/com/xq/Application.java` |
| 配置 | `AI-web/src/main/resources/application.yml` |
| 鉴权拦截 | `AI-web/src/main/java/com/xq/web/config/AuthInterceptor.java` |
| 全局异常 | `AI-web/src/main/java/com/xq/web/config/GlobalExceptionHandler.java` |
| 排产主流程 | `AI-service/src/main/java/com/xq/service/impl/ProductionScheduleServiceImpl.java` |
| 能源方案 | `AI-service/src/main/java/com/xq/service/impl/EnergyPlanServiceImpl.java` |
| 协同评估 | `AI-service/src/main/java/com/xq/service/impl/JointOptimizationServiceImpl.java` |
| 滚动 MPC | `AI-service/src/main/java/com/xq/service/impl/RealtimeControlServiceImpl.java` |
| Agent | `AI-service/src/main/java/com/xq/service/impl/AgentServiceImpl.java` |
| 算法脚本 | `data/algorithm/generate_plan.py` |
| 建表脚本 | `database/sql/数据库建表SQL.sql` |

---

<sub>作者：XQ · 版本：1.0.0 · 适用 JDK 17 / Spring Boot 3.2.7</sub>
