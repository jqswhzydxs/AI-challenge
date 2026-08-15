# Spring AI 工业优化 Agent 接入说明

## 后端接口

新增统一前缀：`/api/agent`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/agent/chat` | POST | 通用业务问答，可传 `scene`、`question`、`optimizeId`、`energyPlanId` |
| `/api/agent/optimize/{optimizeId}/explain` | POST | 解读协同优化结果，分析降本率、降耗率、MAPE、ER 和冲突 |
| `/api/agent/energy-plan/{energyPlanId}/diagnose` | POST | 诊断能源运行方案，分析成本、峰值负荷、碳排和削峰空间 |
| `/api/agent/report/daily` | POST | 汇总报表指标，生成日报摘要 |

返回结构统一为 `AgentAdviceVO`：

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
  "evidence": ["引用的数据来源"],
  "nextActions": ["下一步动作"],
  "rawAnswer": "原始补充信息",
  "generatedAt": "2026-08-15T12:00:00"
}
```

## 大模型配置

后端已默认启用 Spring AI 大模型调用：

```yaml
agent:
  llm:
    enabled: ${AGENT_LLM_ENABLED:true}
spring:
  ai:
    openai:
      api-key: ${SPRING_AI_OPENAI_API_KEY:}
      chat:
        enabled: ${SPRING_AI_OPENAI_CHAT_ENABLED:true}
```

API Key 不写入代码和配置文件，只通过环境变量 `SPRING_AI_OPENAI_API_KEY` 注入。

Windows PowerShell 本地启动示例：

```powershell
$env:SPRING_AI_OPENAI_API_KEY="你的key"
$env:SPRING_AI_OPENAI_CHAT_MODEL="gpt-4o-mini"
mvn -q -pl AI-web -am spring-boot:run
```

IDEA 运行配置中建议设置：

```text
SPRING_AI_OPENAI_API_KEY=你的key
SPRING_AI_OPENAI_BASE_URL=https://api.openai.com
SPRING_AI_OPENAI_CHAT_MODEL=gpt-4o-mini
```

如果模型服务不可用，业务层会自动降级到规则兜底，接口仍会返回结构化分析结果。

如果使用兼容 OpenAI 协议的国内模型网关，只需要替换 `SPRING_AI_OPENAI_BASE_URL` 和模型名。

## 前端需要改的地方

前端目录 `D:\桌面\挑战杯\挑战杯-前端` 本次没有修改。建议前端增加：

1. `src/api/agent.js`
   - `explainOptimization(optimizeId, data)`
   - `diagnoseEnergyPlan(energyPlanId, data)`
   - `generateDailyReport(data)`
   - `chat(data)`

2. 协同优化页面 `src/pages/Collaboration/index.jsx`
   - 在优化结果详情或操作区增加“AI 解读”按钮。
   - 调用 `POST /api/agent/optimize/{optimizeId}/explain`。
   - 用抽屉或弹窗展示 `summary`、`keyMetrics`、`problems`、`suggestions`。

3. 能源页面 `src/pages/Energy/index.jsx`
   - 在能源方案详情区域增加“AI 诊断”按钮。
   - 调用 `POST /api/agent/energy-plan/{energyPlanId}/diagnose`。

4. Dashboard 或 Report 入口
   - 增加“生成 AI 日报”按钮。
   - 调用 `POST /api/agent/report/daily`。

## 简历描述

基于 Spring AI 为生产-能源协同优化平台设计并实现工业决策 Agent，使用 ChatClient、Tool Calling 和结构化输出封装排产计划、能源方案、协同优化、报表指标等业务工具，实现优化结果解释、能源方案诊断、节能降碳建议与日报生成；支持大模型开关、规则兜底和业务证据追踪，提升系统智能决策与比赛展示能力。
