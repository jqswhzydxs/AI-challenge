package com.xq.service.agent;

import com.alibaba.fastjson2.JSON;
import com.xq.model.dto.AgentChatDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工业优化 Agent 提示词工厂.
 * <p>
 * 统一约束模型角色、工具使用规则和返回 JSON 结构，避免不同接口生成风格不一致。
 * </p>
 */
@Component
public class IndustrialPromptFactory {

    /** 系统提示词：规定模型身份、数据可信来源和结构化输出字段。 */
    public String systemPrompt() {
        return """
                You are an industrial production-energy optimization agent embedded in a Spring Boot system.
                Your job is to explain optimization results, diagnose energy operation plans, and generate operational suggestions.
                Use tool results as the source of truth. Do not invent numbers.
                Return a JSON object matching these fields exactly:
                sessionId, scene, source, aiEnabled, riskLevel, summary, keyMetrics, problems, suggestions, evidence, nextActions, rawAnswer, generatedAt.
                riskLevel must be one of LOW, MEDIUM, HIGH.
                keyMetrics, problems, suggestions, evidence, nextActions must be arrays of short Chinese strings.
                Keep the language professional and suitable for a software competition demo.
                """;
    }

    /** 用户提示词：把当前场景、业务主键和前端补充上下文组装成模型可理解的任务描述。 */
    public String userPrompt(String scene, AgentChatDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", scene);
        payload.put("sessionId", dto != null ? dto.getSessionId() : null);
        payload.put("question", dto != null ? dto.getQuestion() : null);
        payload.put("optimizeId", dto != null ? dto.getOptimizeId() : null);
        payload.put("energyPlanId", dto != null ? dto.getEnergyPlanId() : null);
        payload.put("context", dto != null ? dto.getContext() : null);
        return """
                Please complete this industrial Agent task.
                Scene and request JSON:
                %s

                If optimizeId is present, call joint optimization tools before answering.
                If energyPlanId is present, call energy plan tools before answering.
                If the scene is DAILY_REPORT, call the daily report tool before answering.
                """.formatted(JSON.toJSONString(payload));
    }
}
