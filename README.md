# AI-challenge 项目结构

## 后端代码

- `AI-common/`: 通用返回、异常、常量等公共代码。
- `AI-model/`: DTO、VO、实体类等数据模型。
- `AI-mapper/`: MyBatis Plus mapper 与 XML。
- `AI-service/`: 业务服务层，包含排产、能源、算法调用逻辑。
- `AI-web/`: Spring Boot 启动模块、Controller、配置文件。

## 配套文件

- `docs/current/`: 当前有效文档，包括用户手册、软件设计报告、数据库说明等。
- `docs/archive/`: 历史讨论稿和旧版资料，仅作追溯。
- `database/sql/`: 建表脚本、初始化 seed 脚本、重置脚本。
- `database/backups/`: 数据库备份文件。
- `data/algorithm/`: 算法组交付文件，后端默认从这里调用 MATLAB 主函数。
- `data/mpc/`: MPC 结果 CSV 等数据文件。
- `scripts/`: 数据转换、补表等辅助脚本。

## 本地算法配置

默认配置位于 `AI-web/src/main/resources/application.yml`：

```yaml
algorithm:
  matlab-command: ${ALGORITHM_MATLAB_COMMAND:matlab}
  working-dir: ${ALGORITHM_WORKING_DIR:data/algorithm}
  task-dir: ${ALGORITHM_TASK_DIR:target/algorithm-tasks}
  timeout-seconds: ${ALGORITHM_TIMEOUT_SECONDS:180}
```

在 IDEA 里启动时，建议设置：

```text
ALGORITHM_MATLAB_COMMAND=D:\MATLAB\R2026a\bin\matlab.exe
ALGORITHM_WORKING_DIR=D:\桌面\挑战杯\AI-challenge\data\algorithm
ALGORITHM_TASK_DIR=D:\桌面\挑战杯\AI-challenge\target\algorithm-tasks
ALGORITHM_TIMEOUT_SECONDS=180
```
