# 算法组工程化交付说明

## 当前推荐运行方式

后端默认调用 Python 工程化版本：

```bash
python generate_plan.py input.csv output.json
```

本目录的 `generate_plan.py` 使用 Python 标准库实现，不依赖 MATLAB，也不需要安装第三方 Python 包。

## 文件清单

| 文件 | 说明 |
| --- | --- |
| `generate_plan.py` | Python 工程化算法入口，后端默认调用 |
| `main.m` | MATLAB 原型入口，作为旧版兜底保留 |
| `run.bat` | Windows 一键运行 Python 版本 |
| `run_matlab.bat` | Windows 一键运行 MATLAB 旧版 |
| `requirements.txt` | Python 环境说明 |
| `input_sample.csv` | 输入样例 |
| `input_schema.md` | 输入字段说明 |
| `output_sample.json` | 输出样例 |
| `output_schema.md` | 输出字段说明 |
| `steel_data_cleaned.csv` | 15 分钟粒度原始数据样例 |
| `steel_data_1min_fixed.csv` | 1 分钟粒度数据样例 |

## 输入格式

输入文件为 CSV，至少包含：

| 字段 | 说明 |
| --- | --- |
| `timestamp` / `date` / `datetime` | 时间列 |
| `elec` / `Usage_kWh` / `power` / `power_kw` | 用电量或功率列 |
| `steam` | 可选；缺失时按用电量估算 |

当前支持：

- 1 分钟粒度数据：直接进入排产和实时调控。
- 15 分钟粒度数据：自动取最近 7 天，即 672 个点，并插值为 1 分钟粒度。

## 输出格式

输出文件为 JSON，顶层保持和 MATLAB 原型一致：

```json
{
  "daily_plan": {},
  "realtime_control": {}
}
```

后端会读取 `daily_plan` 入库为排产方案，并读取 `realtime_control` 入库为实时调控结果。

如果算法执行失败，会尽量输出：

```json
{
  "status": "error",
  "code": 400,
  "message": "error message"
}
```

## 后端配置

后端默认配置：

```yaml
algorithm:
  runtime: python
  command: python
  script: generate_plan.py
  working-dir: data/algorithm
  task-dir: target/algorithm-tasks
  timeout-seconds: 180
```

如果需要临时切回 MATLAB 旧版：

```yaml
algorithm:
  runtime: matlab
  matlab-command: D:\MATLAB\R2026a\bin\matlab.exe
  working-dir: data/algorithm
```

## 说明

Python 版本复用了 MATLAB 原型的主要流程：

1. 读取并校验原始 CSV。
2. 判断 1 分钟或 15 分钟粒度。
3. 对 15 分钟数据执行 PCHIP 插值。
4. 生成 24 小时日级排产计划。
5. 优化工艺参数，计算 EC 降低率。
6. 生成实时 MPC 调控结果和 ER 指标。
7. 输出后端可直接解析的 `output.json`。

当前 Python 版本为了降低部署门槛，排产部分采用确定性可行启发式，满足总产量和加热炉运行小时数约束。若后续需要严格 MILP，可在不改变输入输出 JSON 的前提下替换为 SciPy、OR-Tools 或 PuLP 求解器。
