package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.mapper.JointOptimizationTimeseriesMapper;
import com.xq.mapper.ConstraintConflictMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.JointOptimizationPlan;
import com.xq.model.entity.JointOptimizationTimeseries;
import com.xq.model.entity.ConstraintConflict;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.vo.JointOptimizeVO;
import com.xq.model.vo.ConflictVO;
import com.xq.model.vo.OptimizeTimeseriesVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.JointOptimizationService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 协同优化服务实现.
 *
 * @author XQ
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class JointOptimizationServiceImpl implements JointOptimizationService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final JointOptimizationPlanMapper optimizePlanMapper;
    private final JointOptimizationTimeseriesMapper timeseriesMapper;
    private final ConstraintConflictMapper conflictMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final EnergyPlanMapper energyPlanMapper;

    @Override
    @Transactional
    public Result<TaskVO> generate(JointOptimizeDTO dto) {
        ProductionSchedulePlan schedulePlan = schedulePlanMapper.selectById(dto.getScheduleId());
        if (schedulePlan == null) {
            throw new BusinessException(400, "排产方案不存在");
        }
        EnergyPlan energyPlan = energyPlanMapper.selectById(dto.getEnergyPlanId());
        if (energyPlan == null) {
            throw new BusinessException(400, "能源方案不存在");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.JOINT_OPTIMIZATION);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        algorithmTaskMapper.insert(task);

        JointOptimizationPlan plan = new JointOptimizationPlan();
        plan.setTaskId(task.getId());
        plan.setScheduleId(schedulePlan.getId());
        plan.setEnergyPlanId(energyPlan.getId());
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setRecommended(1);
        plan.setCostReductionRate(new BigDecimal("2.30"));
        plan.setEnergyReductionRate(new BigDecimal("1.80"));
        plan.setExecuteRate(new BigDecimal("96.40"));
        plan.setMape(new BigDecimal("2.10"));
        plan.setEc(new BigDecimal("41.20"));
        plan.setEr(new BigDecimal("96.80"));
        optimizePlanMapper.insert(plan);

        LocalDateTime start = schedulePlan.getPlanStartTime() != null
                ? schedulePlan.getPlanStartTime()
                : LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        int horizon = schedulePlan.getPlanHorizon() != null ? schedulePlan.getPlanHorizon() : 24;
        for (int hour = 0; hour < horizon; hour++) {
            JointOptimizationTimeseries point = new JointOptimizationTimeseries();
            point.setOptimizeId(plan.getId());
            point.setTimestamp(start.plusHours(hour));
            point.setPlannedOutput(new BigDecimal("100.00").add(new BigDecimal(hour % 4).multiply(new BigDecimal("4.00"))));
            point.setElectricityConsumption(new BigDecimal("950.00").add(new BigDecimal(hour * 6L)));
            point.setSteamConsumption(new BigDecimal("205.00").add(new BigDecimal(hour % 5)));
            point.setCarbonEmissionTco2(new BigDecimal("12.00").add(new BigDecimal(hour).multiply(new BigDecimal("0.02"))));
            point.setEnergyCost(new BigDecimal("1390.00").add(new BigDecimal(hour * 10L)));
            timeseriesMapper.insert(point);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setResultId(plan.getId());
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .resultId(task.getResultId())
                .build();
        return Result.ok("协同优化任务已创建", vo);
    }

    @Override
    public Result<JointOptimizeVO> getResult(Long optimizeId) {
        JointOptimizationPlan plan = optimizePlanMapper.selectById(optimizeId);
        if (plan == null) {
            throw new BusinessException(404, "协同优化方案不存在");
        }

        // 查询时序明细
        List<JointOptimizationTimeseries> timeseries = timeseriesMapper.selectList(
                new LambdaQueryWrapper<JointOptimizationTimeseries>()
                        .eq(JointOptimizationTimeseries::getOptimizeId, optimizeId)
                        .orderByAsc(JointOptimizationTimeseries::getTimestamp)
        );
        List<OptimizeTimeseriesVO> tsVOs = timeseries.stream().map(t -> OptimizeTimeseriesVO.builder()
                .timestamp(t.getTimestamp())
                .plannedOutput(t.getPlannedOutput())
                .electricityConsumption(t.getElectricityConsumption())
                .steamConsumption(t.getSteamConsumption())
                .carbonEmissionTco2(t.getCarbonEmissionTco2())
                .energyCost(t.getEnergyCost())
                .build()).collect(Collectors.toList());

        // 查询冲突
        List<ConstraintConflict> conflicts = conflictMapper.selectList(
                new LambdaQueryWrapper<ConstraintConflict>()
                        .eq(ConstraintConflict::getOptimizeId, optimizeId)
        );
        List<ConflictVO> conflictVOs = conflicts.stream().map(c -> ConflictVO.builder()
                .conflictType(c.getConflictType())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .description(c.getDescription())
                .build()).collect(Collectors.toList());

        JointOptimizeVO vo = JointOptimizeVO.builder()
                .optimizeId(plan.getId())
                .taskId(plan.getTaskId())
                .scheduleId(plan.getScheduleId())
                .energyPlanId(plan.getEnergyPlanId())
                .costReductionRate(plan.getCostReductionRate())
                .energyReductionRate(plan.getEnergyReductionRate())
                .executeRate(plan.getExecuteRate())
                .mape(plan.getMape())
                .ec(plan.getEc())
                .er(plan.getEr())
                .recommended(plan.getRecommended() != null && plan.getRecommended() == 1)
                .conflicts(conflictVOs)
                .timeSeries(tsVOs)
                .build();
        return Result.ok(vo);
    }
}
