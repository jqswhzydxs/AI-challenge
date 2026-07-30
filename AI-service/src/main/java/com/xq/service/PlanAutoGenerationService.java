package com.xq.service;

/**
 * 排产导入后的方案链路自动生成服务.
 */
public interface PlanAutoGenerationService {

    /**
     * 排产方案落库后自动补齐能源运行方案和协同评估结果.
     *
     * @param scheduleId 排产方案 ID
     */
    void autoGenerateAfterScheduleImported(Long scheduleId);
}
