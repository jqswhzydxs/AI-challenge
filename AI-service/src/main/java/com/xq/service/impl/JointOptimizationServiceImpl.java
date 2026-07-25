package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.mapper.JointOptimizationTimeseriesMapper;
import com.xq.mapper.ConstraintConflictMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.model.dto.JointOptimizeCompareDTO;
import com.xq.model.dto.JointOptimizeDTO;
import com.xq.model.dto.PageQueryDTO;
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
import com.xq.model.vo.JointEnergyPlanSummaryVO;
import com.xq.model.vo.JointOptimizeCompareItemVO;
import com.xq.model.vo.JointOptimizeCompareVO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.JointOptimizationResultSummaryVO;
import com.xq.model.vo.JointParetoFrontierVO;
import com.xq.model.vo.JointParetoPointVO;
import com.xq.model.vo.JointProductionPlanSummaryVO;
import com.xq.model.vo.OptimizeTimeseriesVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.JointOptimizationService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        ProductionSchedulePlan schedulePlan = schedulePlanMapper.selectById(plan.getScheduleId());
        EnergyPlan energyPlan = energyPlanMapper.selectById(plan.getEnergyPlanId());
        List<ProductionScheduleDetail> scheduleDetails = scheduleDetails(plan.getScheduleId());
        List<EnergyPlanDetail> energyDetails = energyDetails(plan.getEnergyPlanId());
        AlgorithmTask task = algorithmTaskMapper.selectById(plan.getTaskId());

        JointOptimizeVO vo = JointOptimizeVO.builder()
                .id(plan.getId())
                .optimizeId(plan.getId())
                .name("生产-能源协同优化方案-" + plan.getId())
                .taskId(plan.getTaskId())
                .scheduleId(plan.getScheduleId())
                .energyPlanId(plan.getEnergyPlanId())
                .status(plan.getStatus())
                .objectives(objectives(task))
                .constraints(constraints(plan))
                .optimizationResult(optimizationResult(plan, energyPlan, scheduleDetails, energyDetails))
                .productionPlan(productionPlan(schedulePlan, scheduleDetails))
                .energyPlan(energyPlan(energyPlan, energyDetails))
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

    @Override
    public Result<PageResult<TaskVO>> listTasks(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        int pageNum = query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;

        LambdaQueryWrapper<AlgorithmTask> wrapper = new LambdaQueryWrapper<AlgorithmTask>()
                .eq(AlgorithmTask::getTaskType, TaskType.JOINT_OPTIMIZATION);
        if (query.getStatus() != null && !query.getStatus().trim().isEmpty()) {
            wrapper.eq(AlgorithmTask::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(AlgorithmTask::getCreateTime);

        IPage<AlgorithmTask> page = algorithmTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<TaskVO> records = page.getRecords().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, records));
    }

    @Override
    public Result<List<ConflictVO>> listConflicts(Long taskId, Long optimizeId) {
        Long targetOptimizeId = optimizeId;
        if (targetOptimizeId == null && taskId != null) {
            JointOptimizationPlan plan = optimizePlanMapper.selectOne(
                    new LambdaQueryWrapper<JointOptimizationPlan>()
                            .eq(JointOptimizationPlan::getTaskId, taskId)
                            .orderByDesc(JointOptimizationPlan::getCreateTime)
                            .last("LIMIT 1")
            );
            if (plan == null) {
                throw new BusinessException(404, "该任务暂无协同优化结果");
            }
            targetOptimizeId = plan.getId();
        }

        LambdaQueryWrapper<ConstraintConflict> wrapper = new LambdaQueryWrapper<>();
        if (targetOptimizeId != null) {
            wrapper.eq(ConstraintConflict::getOptimizeId, targetOptimizeId);
        }
        wrapper.orderByDesc(ConstraintConflict::getStartTime);

        List<ConflictVO> records = conflictMapper.selectList(wrapper).stream()
                .map(this::toConflictVO)
                .collect(Collectors.toList());
        return Result.ok(records);
    }

    @Override
    public Result<JointOptimizeEvaluationVO> getEvaluation(Long optimizeId) {
        JointOptimizationPlan plan = optimizePlanMapper.selectById(optimizeId);
        if (plan == null) {
            throw new BusinessException(404, "协同优化方案不存在");
        }
        return Result.ok(toEvaluationVO(plan));
    }

    @Override
    public Result<JointOptimizeCompareVO> compare(JointOptimizeCompareDTO dto) {
        if (dto == null || dto.getOptimizeIds() == null || dto.getOptimizeIds().isEmpty()) {
            throw new BusinessException(400, "协同优化方案 ID 列表不能为空");
        }
        Set<Long> orderedIds = dto.getOptimizeIds().stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderedIds.isEmpty()) {
            throw new BusinessException(400, "协同优化方案 ID 列表不能为空");
        }

        List<JointOptimizationPlan> plans = new ArrayList<>();
        for (Long id : orderedIds) {
            JointOptimizationPlan plan = optimizePlanMapper.selectById(id);
            if (plan == null) {
                throw new BusinessException(404, "协同优化方案不存在: " + id);
            }
            plans.add(plan);
        }

        JointOptimizationPlan baseline = plans.get(0);
        List<JointOptimizeCompareItemVO> records = plans.stream().map(plan ->
                JointOptimizeCompareItemVO.builder()
                        .optimizeId(plan.getId())
                        .name("生产-能源协同优化方案-" + plan.getId())
                        .baseline(plan.getId().equals(baseline.getId()))
                        .recommended(plan.getRecommended() != null && plan.getRecommended() == 1)
                        .costReductionRate(plan.getCostReductionRate())
                        .costReductionDelta(value(plan.getCostReductionRate()).subtract(value(baseline.getCostReductionRate())))
                        .energyReductionRate(plan.getEnergyReductionRate())
                        .energyReductionDelta(value(plan.getEnergyReductionRate()).subtract(value(baseline.getEnergyReductionRate())))
                        .mape(plan.getMape())
                        .mapeDelta(value(plan.getMape()).subtract(value(baseline.getMape())))
                        .ec(plan.getEc())
                        .er(plan.getEr())
                        .executeRate(plan.getExecuteRate())
                        .conflictCount(conflictCount(plan.getId()))
                        .build()
        ).collect(Collectors.toList());

        Long recommendedOptimizeId = records.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRecommended()))
                .map(JointOptimizeCompareItemVO::getOptimizeId)
                .findFirst()
                .orElse(bestCostOptimizeId(records));

        JointOptimizeCompareVO vo = JointOptimizeCompareVO.builder()
                .baselineOptimizeId(baseline.getId())
                .recommendedOptimizeId(recommendedOptimizeId)
                .bestCostOptimizeId(bestCostOptimizeId(records))
                .bestEnergyOptimizeId(bestEnergyOptimizeId(records))
                .bestMapeOptimizeId(bestMapeOptimizeId(records))
                .records(records)
                .build();
        return Result.ok(vo);
    }

    @Override
    public Result<JointParetoFrontierVO> getParetoFrontier(PageQueryDTO query) {
        int limit = query != null && query.getPageSize() != null && query.getPageSize() > 0
                ? query.getPageSize()
                : 50;
        List<JointOptimizationPlan> plans = optimizePlanMapper.selectList(
                new LambdaQueryWrapper<JointOptimizationPlan>()
                        .orderByDesc(JointOptimizationPlan::getCreateTime)
                        .last("LIMIT " + limit)
        );
        List<JointParetoPointVO> points = plans.stream()
                .map(plan -> JointParetoPointVO.builder()
                        .optimizeId(plan.getId())
                        .name("生产-能源协同优化方案-" + plan.getId())
                        .costReductionRate(plan.getCostReductionRate())
                        .energyReductionRate(plan.getEnergyReductionRate())
                        .mape(plan.getMape())
                        .er(plan.getEr())
                        .recommended(plan.getRecommended() != null && plan.getRecommended() == 1)
                        .paretoOptimal(false)
                        .build())
                .collect(Collectors.toList());

        List<JointParetoPointVO> markedPoints = points.stream()
                .map(point -> JointParetoPointVO.builder()
                        .optimizeId(point.getOptimizeId())
                        .name(point.getName())
                        .costReductionRate(point.getCostReductionRate())
                        .energyReductionRate(point.getEnergyReductionRate())
                        .mape(point.getMape())
                        .er(point.getEr())
                        .recommended(point.getRecommended())
                        .paretoOptimal(isParetoOptimal(point, points))
                        .build())
                .collect(Collectors.toList());
        List<JointParetoPointVO> frontier = markedPoints.stream()
                .filter(point -> Boolean.TRUE.equals(point.getParetoOptimal()))
                .sorted(Comparator.comparing(point -> value(point.getCostReductionRate())))
                .collect(Collectors.toList());

        JointParetoFrontierVO vo = JointParetoFrontierVO.builder()
                .xAxis("costReductionRate")
                .yAxis("energyReductionRate")
                .metric("mape")
                .points(markedPoints)
                .frontier(frontier)
                .build();
        return Result.ok(vo);
    }

    private List<ProductionScheduleDetail> scheduleDetails(Long scheduleId) {
        return scheduleDetailMapper.selectList(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, scheduleId)
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );
    }

    private List<EnergyPlanDetail> energyDetails(Long energyPlanId) {
        return energyPlanDetailMapper.selectList(
                new LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getPlanId, energyPlanId)
                        .orderByAsc(EnergyPlanDetail::getTimestamp)
        );
    }

    private Map<String, Object> objectives(AlgorithmTask task) {
        Map<String, Object> objectives = new LinkedHashMap<>();
        objectives.put("productionEfficiency", 0.30);
        objectives.put("energyCost", 0.40);
        objectives.put("carbonEmission", 0.20);
        objectives.put("executeRate", 0.10);
        if (task == null || task.getFrontendRequestJson() == null || task.getFrontendRequestJson().trim().isEmpty()) {
            return objectives;
        }
        try {
            Map<String, Object> request = JSON.parseObject(task.getFrontendRequestJson(), Map.class);
            Object weights = request.get("objectiveWeights");
            if (weights instanceof Map<?, ?> map) {
                objectives.clear();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        objectives.put(entry.getKey().toString(), entry.getValue());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return objectives;
        }
        return objectives;
    }

    private Map<String, Object> constraints(JointOptimizationPlan plan) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("scheduleId", plan.getScheduleId());
        constraints.put("energyPlanId", plan.getEnergyPlanId());
        constraints.put("maxBoilerLoad", 80.00);
        constraints.put("electricPriceMode", "PEAK_VALLEY");
        constraints.put("peakPeriod", "18:00-22:00");
        constraints.put("valleyPeriod", "00:00-08:00");
        return constraints;
    }

    private JointOptimizationResultSummaryVO optimizationResult(JointOptimizationPlan plan,
                                                               EnergyPlan energyPlan,
                                                               List<ProductionScheduleDetail> scheduleDetails,
                                                               List<EnergyPlanDetail> energyDetails) {
        return JointOptimizationResultSummaryVO.builder()
                .totalCost(energyPlan != null ? energyPlan.getTotalEnergyCost() : sum(energyDetails.stream().map(EnergyPlanDetail::getEnergyCost).toList()))
                .costReductionRate(plan.getCostReductionRate())
                .energyReductionRate(plan.getEnergyReductionRate())
                .peakShavingRate(peakShavingRate(energyDetails))
                .deadlineCompliance(deadlineCompliance(scheduleDetails))
                .executeRate(plan.getExecuteRate())
                .mape(plan.getMape())
                .ec(plan.getEc())
                .er(plan.getEr())
                .build();
    }

    private JointProductionPlanSummaryVO productionPlan(ProductionSchedulePlan plan,
                                                       List<ProductionScheduleDetail> details) {
        if (plan == null) {
            return null;
        }
        return JointProductionPlanSummaryVO.builder()
                .scheduleId(plan.getId())
                .scheduleName(plan.getScheduleName())
                .scheduleDate(plan.getScheduleDate())
                .planStartTime(plan.getPlanStartTime())
                .totalProduction(plan.getTotalProduction())
                .totalEnergy(plan.getTotalEnergy())
                .ecBaseline(plan.getEcBaseline())
                .ecOptimized(plan.getEcOptimized())
                .detailCount(details != null ? details.size() : 0)
                .build();
    }

    private JointEnergyPlanSummaryVO energyPlan(EnergyPlan plan, List<EnergyPlanDetail> details) {
        if (plan == null) {
            return null;
        }
        return JointEnergyPlanSummaryVO.builder()
                .energyPlanId(plan.getId())
                .planDate(plan.getPlanDate())
                .electricityCost(plan.getElectricityCost())
                .steamCost(plan.getSteamCost())
                .totalEnergyCost(plan.getTotalEnergyCost())
                .peakLoad(max(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList()))
                .avgLoad(avg(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList()))
                .detailCount(details != null ? details.size() : 0)
                .build();
    }

    private BigDecimal peakShavingRate(List<EnergyPlanDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal peak = max(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList());
        if (peak.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return percent(peak.subtract(avg(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList())), peak);
    }

    private BigDecimal deadlineCompliance(List<ProductionScheduleDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long feasible = details.stream()
                .filter(item -> value(item.getProduction()).compareTo(value(item.getDemand())) >= 0)
                .count();
        return percent(new BigDecimal(feasible), new BigDecimal(details.size()));
    }

    private BigDecimal max(List<BigDecimal> values) {
        return values.stream().map(this::value).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum(values).divide(new BigDecimal(values.size()), 4, RoundingMode.HALF_UP);
    }

    private TaskVO toTaskVO(AlgorithmTask task) {
        return TaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .resultId(task.getResultId())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getStartTime() != null ? task.getStartTime() : task.getCreateTime())
                .updateTime(task.getFinishTime() != null ? task.getFinishTime() : task.getUpdateTime())
                .build();
    }

    private JointOptimizeEvaluationVO toEvaluationVO(JointOptimizationPlan plan) {
        return JointOptimizeEvaluationVO.builder()
                .optimizeId(plan.getId())
                .taskId(plan.getTaskId())
                .status(plan.getStatus())
                .recommended(plan.getRecommended() != null && plan.getRecommended() == 1)
                .costReductionRate(plan.getCostReductionRate())
                .energyReductionRate(plan.getEnergyReductionRate())
                .executeRate(plan.getExecuteRate())
                .mape(plan.getMape())
                .ec(plan.getEc())
                .er(plan.getEr())
                .conflictCount(conflictCount(plan.getId()))
                .timeseriesCount(timeseriesCount(plan.getId()))
                .build();
    }

    private Integer conflictCount(Long optimizeId) {
        Long count = conflictMapper.selectCount(
                new LambdaQueryWrapper<ConstraintConflict>()
                        .eq(ConstraintConflict::getOptimizeId, optimizeId)
        );
        return count != null ? count.intValue() : 0;
    }

    private Integer timeseriesCount(Long optimizeId) {
        Long count = timeseriesMapper.selectCount(
                new LambdaQueryWrapper<JointOptimizationTimeseries>()
                        .eq(JointOptimizationTimeseries::getOptimizeId, optimizeId)
        );
        return count != null ? count.intValue() : 0;
    }

    private Long bestCostOptimizeId(List<JointOptimizeCompareItemVO> records) {
        return records.stream()
                .max(Comparator.comparing(item -> value(item.getCostReductionRate())))
                .map(JointOptimizeCompareItemVO::getOptimizeId)
                .orElse(null);
    }

    private Long bestEnergyOptimizeId(List<JointOptimizeCompareItemVO> records) {
        return records.stream()
                .max(Comparator.comparing(item -> value(item.getEnergyReductionRate())))
                .map(JointOptimizeCompareItemVO::getOptimizeId)
                .orElse(null);
    }

    private Long bestMapeOptimizeId(List<JointOptimizeCompareItemVO> records) {
        return records.stream()
                .min(Comparator.comparing(item -> value(item.getMape())))
                .map(JointOptimizeCompareItemVO::getOptimizeId)
                .orElse(null);
    }

    private boolean isParetoOptimal(JointParetoPointVO candidate, List<JointParetoPointVO> points) {
        for (JointParetoPointVO other : points) {
            if (other.getOptimizeId().equals(candidate.getOptimizeId())) {
                continue;
            }
            boolean noWorse = value(other.getCostReductionRate()).compareTo(value(candidate.getCostReductionRate())) >= 0
                    && value(other.getEnergyReductionRate()).compareTo(value(candidate.getEnergyReductionRate())) >= 0
                    && value(other.getEr()).compareTo(value(candidate.getEr())) >= 0
                    && value(other.getMape()).compareTo(value(candidate.getMape())) <= 0;
            boolean strictlyBetter = value(other.getCostReductionRate()).compareTo(value(candidate.getCostReductionRate())) > 0
                    || value(other.getEnergyReductionRate()).compareTo(value(candidate.getEnergyReductionRate())) > 0
                    || value(other.getEr()).compareTo(value(candidate.getEr())) > 0
                    || value(other.getMape()).compareTo(value(candidate.getMape())) < 0;
            if (noWorse && strictlyBetter) {
                return false;
            }
        }
        return true;
    }

    private ConflictVO toConflictVO(ConstraintConflict conflict) {
        return ConflictVO.builder()
                .conflictId(conflict.getId())
                .optimizeId(conflict.getOptimizeId())
                .conflictType(conflict.getConflictType())
                .startTime(conflict.getStartTime())
                .endTime(conflict.getEndTime())
                .description(conflict.getDescription())
                .resolved(conflict.getResolved() != null && conflict.getResolved() == 1)
                .build();
    }
}
