# 输出数据格式说明

算法输出文件为 JSON，顶层包含：

```json
{
  "daily_plan": {},
  "realtime_control": {}
}
```

## 日级计划字段 `daily_plan`

| 字段 | 类型 | 单位 | 说明 |
| --- | --- | --- | --- |
| `timestamp` | string | - | 计划生成时间 |
| `plan_horizon` | int | 小时 | 固定为 `24` |
| `unit` | string | - | 当前为 `hour` |
| `data_granularity` | string | - | 当前为 `1 minute` |
| `EC_baseline` | float | kWh/吨 | 基准能耗，当前为 `14.00` |
| `EC_optimized` | float | kWh/吨 | 优化能耗，约 `13.28` |
| `EC_reduction` | float | % | 能耗降低率，约 `5.15` |
| `total_production` | float | 吨 | 日总产量 |
| `total_energy` | float | kWh | 日总能耗 |
| `optimal_temperature` | int | ℃ | 最优加热温度，当前为 `1140` |
| `optimal_speed` | float | m/s | 最优轧制速度，当前为 `11.0` |
| `schedule[].hour` | int | - | 小时序号，范围 `0-23` |
| `schedule[].demand` | float | 吨 | 该小时需求 |
| `schedule[].production` | float | 吨 | 该小时排产 |

## 实时调控字段 `realtime_control`

| 字段 | 类型 | 单位 | 说明 |
| --- | --- | --- | --- |
| `timestamp` | string | - | 当前时刻，格式 `HH:MM:SS` |
| `control.boiler_load` | float | MW | 锅炉负荷指令 |
| `control.turbine_output` | float | MW | 汽机出力指令 |
| `control.grid_purchase` | float | kWh | 外购电力 |
| `control.power_factor_target` | float | - | 功率因数目标，当前为 `0.95` |
| `forecast.elec_next_5min` | float | kWh | 5 分钟用电预测 |
| `forecast.steam_next_5min` | float | 吨 | 5 分钟蒸汽预测 |
| `performance.ER` | float | % | 方案可执行率 |

## 失败输出格式

算法执行失败时，`main.m` 会尽量向指定的输出文件写入如下结构：

```json
{
  "status": "error",
  "code": 400,
  "message": "输入数据不足，需要至少30天数据；15分钟粒度不少于2880行，1分钟粒度不少于43200行",
  "received_rows": 96,
  "required_rows": 2880
}
```

其他示例：

```json
{
  "status": "error",
  "code": 404,
  "message": "输入文件不存在: steel_data_cleaned.csv"
}
```

```json
{
  "status": "error",
  "code": 500,
  "message": "MILP求解失败，请检查输入数据是否包含异常值"
}
```

```json
{
  "status": "error",
  "code": 415,
  "message": "输入格式错误，请确保CSV包含 timestamp, elec, steam 三列"
}
```
