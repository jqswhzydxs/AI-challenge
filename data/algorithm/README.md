# 算法组工程化交付说明

> 项目：生产-能源协同优化算法模块  
> 版本：v1.0  
> 算法语言：MATLAB  
> 交付日期：2026-08-03

## 1. 模块用途

本算法模块用于根据钢铁能源历史数据生成生产-能源协同优化方案，主要包括：

- 日级 MILP 排产计划
- 工艺参数优化结果
- 实时 MPC 调控指令
- EC 能耗降低率
- ER 方案可执行率

后端可通过调用 MATLAB 主程序，将用户上传或系统保存的能源数据转换为前端可展示的 JSON 结果。

## 2. 当前文件清单

| 文件 | 说明 | 是否必需 |
| --- | --- | --- |
| `main.m` | MATLAB 主程序，支持指定输入文件和输出文件 | 必需 |
| `steel_data_cleaned.csv` | 输入数据样例，15 分钟粒度钢铁能源历史数据 | 必需 |
| `input_sample.csv` | 精简输入样例，取自 `steel_data_cleaned.csv` 前 10 行 | 建议 |
| `input_schema.md` | 输入字段、粒度和校验要求说明 | 建议 |
| `output_sample.json` | 输出结果样例，包含日级计划和实时调控结果 | 必需 |
| `output_schema.md` | 输出字段和失败 JSON 格式说明 | 建议 |
| `requirements.txt` | MATLAB 版本、Toolbox 和运行要求 | 建议 |
| `run.bat` | Windows 一键运行脚本 | 可选 |
| `文件补充.md` | 算法组补充说明原文 | 可选 |
| `算法组工程化交付说明.docx` | 原始 Word 说明文档 | 可选 |
| `README.md` | 本说明文件 | 必需 |

说明：`README.md` 为工程总说明；`input_schema.md`、`output_schema.md`、`requirements.txt` 为拆分后的专项说明文件，便于后端和文档组引用。

## 3. 运行环境

| 项目 | 要求 |
| --- | --- |
| MATLAB 版本 | R2022b 或更高 |
| 必需 Toolbox | Optimization Toolbox |
| 关键函数 | `readtable`、`datetime`、`pchip`、`intlinprog`、`jsonencode` |
| 第三方依赖 | 无 |
| GPU | 不需要 |
| 预计耗时 | 约 10-15 秒，具体取决于机器性能和数据量 |

注意：`intlinprog` 来自 Optimization Toolbox。如果缺少该 Toolbox，MILP 排产步骤会失败。

## 4. 标准运行命令

在 MATLAB 命令行中进入算法目录后运行：

```matlab
main('steel_data_cleaned.csv', 'output_sample.json')
```

Windows 命令行或后端进程调用时，推荐使用：

```bash
matlab -batch "cd('D:\桌面\挑战杯\算法组文件'); main('steel_data_cleaned.csv','output_sample.json')"
```

如果后端为每个任务生成独立输入和输出文件，可替换为：

```bash
matlab -batch "cd('D:\桌面\挑战杯\算法组文件'); main('input_task_001.csv','output_task_001.json')"
```

## 5. 输入文件格式

当前 `main.m` 支持读取 CSV 文件，并在程序内部执行：

```text
15 分钟粒度数据 -> 取最近 7 天 -> PCHIP 插值 -> 1 分钟粒度数据
```

因此，当前推荐输入为 **15 分钟粒度的历史能源 CSV 数据**。算法会自动截取最近 7 天并插值为 1 分钟粒度。

### 5.1 必需字段

| 字段 | 类型 | 单位 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `timestamp` | datetime/string | - | 是 | 时间戳，可由 `date`、`timestamp`、`DateTime` 自动统一 |
| `elec` | float | kWh | 是 | 用电量，可由 `Usage_kWh`、`Power`、`Power_kW` 自动统一 |

### 5.2 可选字段

| 字段 | 类型 | 单位 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `steam` | float | 吨 | 否 | 蒸汽量；如果缺失，当前算法会按用电量估算 |

### 5.3 输入样例

```csv
timestamp,elec,steam
2018-10-01 00:00:00,4.57,0.5
2018-10-01 00:15:00,4.50,0.5
2018-10-01 00:30:00,4.43,0.5
2018-10-01 00:45:00,4.38,0.5
```

### 5.4 输入校验建议

后端调用算法前建议校验：

- 文件格式必须为 CSV。
- 至少包含时间列和用电量列。
- 时间列可被 MATLAB `datetime` 解析。
- 用电量必须为数值。
- 至少提供最近 7 天数据，即 672 个 15 分钟采样点。
- 推荐提供 7 天以上历史数据，算法会自动截取最近 7 天。
- 用电量小于等于 `0.01` 的记录会被算法过滤。

## 6. 输出文件格式

算法输出为 JSON 文件，顶层包含两个对象：

```json
{
  "daily_plan": {},
  "realtime_control": {}
}
```

### 6.1 `daily_plan`

| 字段 | 类型 | 单位 | 说明 |
| --- | --- | --- | --- |
| `timestamp` | string | - | 方案生成时间 |
| `plan_horizon` | int | hour | 计划周期，当前为 24 |
| `unit` | string | - | 排产时间单位，当前为 `hour` |
| `data_granularity` | string | - | 算法内部数据粒度，当前为 `1 minute` |
| `EC_baseline` | float | kWh/吨 | 基准能耗，当前为 `14.00` |
| `EC_optimized` | float | kWh/吨 | 优化后能耗 |
| `EC_reduction` | float | % | 能耗降低率 |
| `total_production` | float | 吨 | 24 小时总产量 |
| `total_energy` | float | kWh | 总预测能耗 |
| `optimal_temperature` | float | ℃ | 最优加热温度 |
| `optimal_speed` | float | m/s | 最优轧制速度 |
| `schedule` | array | - | 24 小时排产明细 |

`schedule` 明细字段：

| 字段 | 类型 | 单位 | 说明 |
| --- | --- | --- | --- |
| `hour` | int | hour | 小时序号，范围 `0-23` |
| `demand` | float | 吨 | 小时需求 |
| `production` | float | 吨 | 小时排产量 |

### 6.2 `realtime_control`

| 字段 | 类型 | 单位 | 说明 |
| --- | --- | --- | --- |
| `timestamp` | string | - | 调控指令对应时间 |
| `control.boiler_load` | float | MW | 锅炉负荷 |
| `control.turbine_output` | float | MW | 汽机出力 |
| `control.grid_purchase` | float | kWh | 外购电量 |
| `control.power_factor_target` | float | - | 功率因数目标，当前为 `0.95` |
| `forecast.elec_next_5min` | float | kWh | 未来 5 分钟用电预测 |
| `forecast.steam_next_5min` | float | 吨 | 未来 5 分钟蒸汽预测 |
| `performance.ER` | float | % | 方案可执行率 |

## 7. 输出样例

简化示例：

```json
{
  "daily_plan": {
    "plan_horizon": 24,
    "EC_baseline": 14,
    "EC_optimized": 13.2785,
    "EC_reduction": 5.1534,
    "optimal_temperature": 1140,
    "optimal_speed": 11,
    "schedule": [
      {
        "hour": 0,
        "demand": 0.3393,
        "production": 0.2411
      }
    ]
  },
  "realtime_control": {
    "control": {
      "boiler_load": 29.515,
      "turbine_output": 7.7527,
      "grid_purchase": 0,
      "power_factor_target": 0.95
    },
    "performance": {
      "ER": 100
    }
  }
}
```

完整样例见 `output_sample.json`，完整字段说明见 `output_schema.md`。

## 8. 后端接入建议

推荐后端按异步任务方式调用算法：

```text
前端上传原始 CSV
        -> 后端保存文件
        -> 创建 algorithm_task
        -> 后端执行 MATLAB 命令
        -> 读取 output JSON
        -> 拆分写入排产、MPC、评价指标等业务表
        -> 前端根据 taskId 查询任务状态和方案结果
```

建议任务状态：

| 状态 | 说明 |
| --- | --- |
| `UPLOADED` | 原始数据已上传 |
| `VALIDATING` | 数据校验中 |
| `GENERATING` | 算法生成中 |
| `SUCCESS` | 生成成功 |
| `FAILED` | 生成失败 |

后端调用时建议为每个任务创建独立工作目录或独立输出文件名，避免并发任务互相覆盖。

## 9. 当前注意事项

1. `main.m` 会临时生成 `daily_plan_temp.json` 和 `realtime_control_temp.json`，并在结束时删除。并发调用时建议改为任务级临时文件名。
2. 当前 `main.m` 已支持结构化失败 JSON，详见 `output_schema.md`。
3. 当前算法内部会将输入数据插值为 1 分钟粒度，输入文档应以 `main.m` 实际逻辑为准。
4. 如果输入文件列名不是 `timestamp`、`date`、`DateTime` 或 `elec`、`Usage_kWh`、`Power`、`Power_kW`，后端应先做字段映射。
5. 后端已支持识别算法输出中的 `status=error`，并将 `message` 写入算法任务失败原因。

## 10. 建议补充项

为方便后端稳定接入，后续建议算法组继续补充：

- 可以继续扩展更多错误码，例如数据时间间隔不连续、`Optimization Toolbox` 缺失等：

```json
{
  "status": "failed",
  "errorCode": "DATA_INVALID",
  "message": "缺少 timestamp 或 elec 字段"
}
```
