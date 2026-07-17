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
import com.xq.model.entity.JointOptimizationPlan;
import com.xq.model.entity.JointOptimizationTimeseries;
import com.xq.model.entity.ConstraintConflict;
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
        // 校验排产方案和能源方案存在
        if (schedulePlanMapper.selectById(dto.getScheduleId()) == null) {
            throw new BusinessException(400, "排产方案不存在");
        }
        if (energyPlanMapper.selectById(dto.getEnergyPlanId()) == null) {
            throw new BusinessException(400, "能源方案不存在");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.JOINT_OPTIMIZATION);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        algorithmTaskMapper.insert(task);

        // Mock: 模拟异步
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
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
