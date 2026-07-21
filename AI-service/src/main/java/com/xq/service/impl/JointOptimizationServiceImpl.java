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
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import com.xq.model.entity.JointOptimizationPlan;
import com.xq.model.entity.JointOptimizationTimeseries;
import com.xq.model.entity.ConstraintConflict;
import com.xq.model.entity.ProductionScheduleDetail;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final ProductionScheduleDetailMapper scheduleDetailMapper;
    private final EnergyPlanMapper energyPlanMapper;
    private final EnergyPlanDetailMapper energyPlanDetailMapper;

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
        List<ProductionScheduleDetail> scheduleDetails = scheduleDetailMapper.selectList(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, schedulePlan.getId())
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );
        if (scheduleDetails.isEmpty()) {
            throw new BusinessException(400, "排产方案没有明细，无法生成协同优化结果");
        }
        List<EnergyPlanDetail> energyDetails = energyPlanDetailMapper.selectList(
                new LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getPlanId, energyPlan.getId())
                        .orderByAsc(EnergyPlanDetail::getTimestamp)
        );
        if (energyDetails.isEmpty()) {
            throw new BusinessException(400, "能源方案没有明细，无法生成协同优化结果");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.JOINT_OPTIMIZATION);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("协同优化评价派生计算中");
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        JointOptimizationPlan plan = new JointOptimizationPlan();
        plan.setTaskId(task.getId());
        plan.setScheduleId(schedulePlan.getId());
        plan.setEnergyPlanId(energyPlan.getId());
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setRecommended(1);
        BigDecimal optimizedEnergy = value(schedulePlan.getTotalEnergy());
        if (optimizedEnergy.compareTo(BigDecimal.ZERO) == 0) {
            optimizedEnergy = sum(scheduleDetails.stream().map(ProductionScheduleDetail::getElecForecast).toList());
        }
        BigDecimal baselineEnergy = baselineEnergy(schedulePlan, scheduleDetails);
        BigDecimal energyReductionRate = percent(baselineEnergy.subtract(optimizedEnergy), baselineEnergy);
        BigDecimal baselineCost = baselineCost(scheduleDetails, schedulePlan);
        BigDecimal optimizedCost = value(energyPlan.getTotalEnergyCost());
        BigDecimal costReductionRate = optimizedCost.compareTo(BigDecimal.ZERO) > 0
                ? percent(baselineCost.subtract(optimizedCost), baselineCost)
                : energyReductionRate;
        BigDecimal mape = calculateMape(scheduleDetails, energyDetails);
        BigDecimal er = calculateEr(energyDetails, dto.getObjectiveWeights());
        plan.setCostReductionRate(scale(nonNegative(costReductionRate), 2));
        plan.setEnergyReductionRate(scale(nonNegative(energyReductionRate), 2));
        plan.setExecuteRate(er);
        plan.setMape(mape);
        plan.setEc(schedulePlan.getEcOptimized() != null ? schedulePlan.getEcOptimized() : schedulePlan.getElecCoefficient());
        plan.setEr(er);
        optimizePlanMapper.insert(plan);

        int horizon = Math.min(scheduleDetails.size(), energyDetails.size());
        List<ConstraintConflict> conflicts = new ArrayList<>();
        for (int index = 0; index < horizon; index++) {
            ProductionScheduleDetail scheduleDetail = scheduleDetails.get(index);
            EnergyPlanDetail energyDetail = energyDetails.get(index);
            JointOptimizationTimeseries point = new JointOptimizationTimeseries();
            point.setOptimizeId(plan.getId());
            point.setTimestamp(scheduleDetail.getStartTime() != null ? scheduleDetail.getStartTime() : energyDetail.getTimestamp());
            point.setPlannedOutput(scheduleDetail.getProduction());
            point.setElectricityConsumption(energyDetail.getElectricityConsumption());
            point.setSteamConsumption(energyDetail.getSteamConsumption());
            point.setCarbonEmissionTco2(energyDetail.getCarbonEmissionTco2());
            point.setEnergyCost(energyDetail.getEnergyCost());
            timeseriesMapper.insert(point);
            addConflictIfNeeded(conflicts, plan.getId(), scheduleDetail, energyDetail);
        }
        for (ConstraintConflict conflict : conflicts) {
            conflictMapper.insert(conflict);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setMessage("协同优化评价已生成");
        task.setResultId(plan.getId());
        task.setFinishTime(LocalDateTime.now());
        algorithmTaskMapper.updateById(task);

        TaskVO vo = TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .resultId(task.getResultId())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getStartTime())
                .updateTime(task.getFinishTime())
                .build();
        return Result.ok("协同优化任务已创建", vo);
    }

    private BigDecimal baselineEnergy(ProductionSchedulePlan schedulePlan, List<ProductionScheduleDetail> details) {
        BigDecimal totalProduction = value(schedulePlan.getTotalProduction());
        if (totalProduction.compareTo(BigDecimal.ZERO) == 0) {
            totalProduction = sum(details.stream().map(ProductionScheduleDetail::getProduction).toList());
        }
        BigDecimal baselineEc = schedulePlan.getEcBaseline() != null
                ? schedulePlan.getEcBaseline()
                : value(schedulePlan.getElecCoefficient());
        return totalProduction.multiply(baselineEc);
    }

    private BigDecimal baselineCost(List<ProductionScheduleDetail> details, ProductionSchedulePlan schedulePlan) {
        BigDecimal baselineEc = schedulePlan.getEcBaseline() != null
                ? schedulePlan.getEcBaseline()
                : value(schedulePlan.getElecCoefficient());
        BigDecimal optimizedEc = schedulePlan.getEcOptimized() != null
                ? schedulePlan.getEcOptimized()
                : value(schedulePlan.getElecCoefficient());
        BigDecimal cost = BigDecimal.ZERO;
        for (ProductionScheduleDetail detail : details) {
            BigDecimal production = value(detail.getProduction());
            BigDecimal baselineElec = production.multiply(baselineEc);
            int hour = detail.getStartTime() != null ? detail.getStartTime().getHour() : value(detail.getHourIndex()).intValue();
            cost = cost.add(baselineElec.multiply(priceForHour(hour)));
            if (optimizedEc.compareTo(BigDecimal.ZERO) > 0) {
                cost = cost.add(production.multiply(optimizedEc).multiply(new BigDecimal("0.005")).multiply(new BigDecimal("180.00")));
            }
        }
        return cost;
    }

    private BigDecimal calculateMape(List<ProductionScheduleDetail> scheduleDetails, List<EnergyPlanDetail> energyDetails) {
        int count = Math.min(scheduleDetails.size(), energyDetails.size());
        BigDecimal absError = BigDecimal.ZERO;
        BigDecimal actualSum = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal predicted = value(scheduleDetails.get(i).getElecForecast());
            BigDecimal actual = value(energyDetails.get(i).getElectricityConsumption());
            absError = absError.add(actual.subtract(predicted).abs());
            actualSum = actualSum.add(actual.abs());
        }
        return percent(absError, actualSum);
    }

    private BigDecimal calculateEr(List<EnergyPlanDetail> energyDetails, Map<String, Double> objectiveWeights) {
        BigDecimal maxBoilerLoad = getWeightOrDefault(objectiveWeights, "maxBoilerLoad", new BigDecimal("80.00"));
        long executable = energyDetails.stream()
                .filter(item -> value(item.getOutput()).compareTo(maxBoilerLoad) <= 0)
                .count();
        return percent(new BigDecimal(executable), new BigDecimal(energyDetails.size()));
    }

    private void addConflictIfNeeded(List<ConstraintConflict> conflicts,
                                     Long optimizeId,
                                     ProductionScheduleDetail scheduleDetail,
                                     EnergyPlanDetail energyDetail) {
        if (value(energyDetail.getOutput()).compareTo(new BigDecimal("80.00")) > 0) {
            ConstraintConflict conflict = new ConstraintConflict();
            conflict.setOptimizeId(optimizeId);
            conflict.setConflictType("ENERGY_OUTPUT_LIMIT");
            conflict.setStartTime(scheduleDetail.getStartTime());
            conflict.setEndTime(scheduleDetail.getEndTime());
            conflict.setDescription("能源设备输出超过 80MW 上限");
            conflicts.add(conflict);
        }
    }

    private BigDecimal priceForHour(int hour) {
        if (hour >= 0 && hour < 8) {
            return new BigDecimal("0.35");
        }
        if (hour >= 18 && hour < 22) {
            return new BigDecimal("1.05");
        }
        return new BigDecimal("0.65");
    }

    private BigDecimal getWeightOrDefault(Map<String, Double> values, String key, BigDecimal defaultValue) {
        if (values == null || values.get(key) == null) {
            return defaultValue;
        }
        return BigDecimal.valueOf(values.get(key));
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100")).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal value(Integer value) {
        return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value(value).setScale(scale, RoundingMode.HALF_UP);
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
