package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.constant.TaskStatus;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.JointOptimizationPlan;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.service.PlanAutoGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动补齐“排产方案 -> 能源运行方案 -> 协同评估结果”的展示链路.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanAutoGenerationServiceImpl implements PlanAutoGenerationService {

    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final EnergyPlanMapper energyPlanMapper;
    private final JointOptimizationPlanMapper jointOptimizationPlanMapper;
    private final EnergyPlanServiceImpl energyPlanService;
    private final JointOptimizationServiceImpl jointOptimizationService;

    @Override
    public void autoGenerateAfterScheduleImported(Long scheduleId) {
        if (scheduleId == null) {
            return;
        }
        try {
            ProductionSchedulePlan schedulePlan = schedulePlanMapper.selectById(scheduleId);
            if (schedulePlan == null) {
                log.warn("自动生成方案链路跳过，排产方案不存在: scheduleId={}", scheduleId);
                return;
            }

            EnergyPlan energyPlan = findExistingEnergyPlan(scheduleId);
            if (energyPlan == null) {
                energyPlan = energyPlanService.generateDerivedPlanForSchedule(schedulePlan, defaultEnergyConstraints());
            }

            JointOptimizationPlan jointPlan = findExistingJointEvaluation(scheduleId, energyPlan.getId());
            if (jointPlan == null) {
                jointOptimizationService.generateEvaluationForPlans(scheduleId, energyPlan.getId());
            }
        } catch (RuntimeException e) {
            log.warn("排产导入后的方案链路自动生成失败: scheduleId={}", scheduleId, e);
        }
    }

    private EnergyPlan findExistingEnergyPlan(Long scheduleId) {
        return energyPlanMapper.selectOne(
                new LambdaQueryWrapper<EnergyPlan>()
                        .eq(EnergyPlan::getSourceScheduleId, scheduleId)
                        .eq(EnergyPlan::getStatus, TaskStatus.SUCCESS)
                        .orderByDesc(EnergyPlan::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private JointOptimizationPlan findExistingJointEvaluation(Long scheduleId, Long energyPlanId) {
        return jointOptimizationPlanMapper.selectOne(
                new LambdaQueryWrapper<JointOptimizationPlan>()
                        .eq(JointOptimizationPlan::getScheduleId, scheduleId)
                        .eq(JointOptimizationPlan::getEnergyPlanId, energyPlanId)
                        .eq(JointOptimizationPlan::getStatus, TaskStatus.SUCCESS)
                        .orderByDesc(JointOptimizationPlan::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private Map<String, Object> defaultEnergyConstraints() {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("steamUnitPrice", "180.00");
        constraints.put("equipmentId", 1L);
        return constraints;
    }
}
