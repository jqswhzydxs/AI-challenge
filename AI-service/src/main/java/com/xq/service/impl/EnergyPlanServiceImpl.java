package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.PageResult;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EnergyEquipmentMapper;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ReportStatisticMapper;
import com.xq.model.dto.EnergyPlanGenerateDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EnergyEquipment;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ProductionScheduleDetail;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.entity.ReportStatistic;
import com.xq.model.vo.EnergyAnalysisVO;
import com.xq.model.vo.EnergyCarbonReductionPointVO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyConsumptionTrendPointVO;
import com.xq.model.vo.EnergyConsumptionTrendVO;
import com.xq.model.vo.EnergyDeviceStatusVO;
import com.xq.model.vo.EnergyLoadForecastPointVO;
import com.xq.model.vo.EnergyLoadForecastVO;
import com.xq.model.vo.EnergyOptimizationResultVO;
import com.xq.model.vo.EnergyTrendPointVO;
import com.xq.model.vo.EnergyTrendVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.EnergyPlanService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyPlanDetailVO;
import com.xq.model.vo.EnergyStrategyVO;
import com.xq.model.vo.LoadForecastVO;
import com.xq.model.vo.PricePolicyPeriodVO;
import com.xq.model.vo.PricePolicyVO;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyPlanServiceImpl implements EnergyPlanService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final EnergyPlanMapper energyPlanMapper;
    private final EnergyPlanDetailMapper energyPlanDetailMapper;
    private final EnergyEquipmentMapper energyEquipmentMapper;
    private final EnergyRealtimeDataMapper energyRealtimeDataMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final ProductionScheduleDetailMapper scheduleDetailMapper;
    private final ReportStatisticMapper reportStatisticMapper;
    private final EvaluationMetricMapper evaluationMetricMapper;

    @Override
    @Transactional
    public Result<TaskVO> generate(EnergyPlanGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        LocalDate planDate = parseDate(dto.getPlanDate(), "planDate");
        LocalDateTime planStart = planDate.atStartOfDay();
        List<EnergyPlanDetail> derivedDetails = deriveEnergyDetails(dto, planDate, planStart);
        if (derivedDetails.isEmpty()) {
            throw new BusinessException(400, "没有可用于生成能源运行方案的数据，请先导入实时能源数据或日级排产方案");
        }

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.ENERGY_PLAN);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("能源运行方案派生计算中");
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        task.setStartTime(LocalDateTime.now());
        algorithmTaskMapper.insert(task);

        EnergyPlan plan = new EnergyPlan();
        plan.setTaskId(task.getId());
        plan.setPlanDate(planDate);
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setObjective(dto.getObjective());
        plan.setElectricPriceMode(dto.getElectricPriceMode());
        plan.setTimeInterval(60);
        BigDecimal electricityCost = BigDecimal.ZERO;
        BigDecimal steamCost = BigDecimal.ZERO;
        for (EnergyPlanDetail detail : derivedDetails) {
            electricityCost = electricityCost.add(detail.getElectricityConsumption().multiply(priceForHour(detail.getTimestamp().getHour())));
            steamCost = steamCost.add(detail.getSteamConsumption().multiply(steamUnitPrice(dto.getConstraints())));
        }
        plan.setElectricityCost(scale(electricityCost, 2));
        plan.setSteamCost(scale(steamCost, 2));
        plan.setTotalEnergyCost(plan.getElectricityCost().add(plan.getSteamCost()));
        energyPlanMapper.insert(plan);

        for (EnergyPlanDetail detail : derivedDetails) {
            detail.setPlanId(plan.getId());
            detail.setEnergyCost(scale(
                    detail.getElectricityConsumption().multiply(priceForHour(detail.getTimestamp().getHour()))
                            .add(detail.getSteamConsumption().multiply(steamUnitPrice(dto.getConstraints()))),
                    2
            ));
            energyPlanDetailMapper.insert(detail);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setMessage("能源运行方案已生成");
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
        return Result.ok("能源运行任务已创建", vo);
    }

    private List<EnergyPlanDetail> deriveEnergyDetails(EnergyPlanGenerateDTO dto, LocalDate planDate, LocalDateTime planStart) {
        List<EnergyRealtimeData> realtimeData = energyRealtimeDataMapper.selectList(
                new LambdaQueryWrapper<EnergyRealtimeData>()
                        .ge(EnergyRealtimeData::getTimestamp, planStart)
                        .lt(EnergyRealtimeData::getTimestamp, planStart.plusDays(1))
                        .orderByAsc(EnergyRealtimeData::getTimestamp)
        );
        if (!realtimeData.isEmpty()) {
            return aggregateRealtimeDataByHour(realtimeData, dto.getConstraints());
        }
        return deriveFromSchedule(planDate, dto.getConstraints());
    }

    private List<EnergyPlanDetail> aggregateRealtimeDataByHour(List<EnergyRealtimeData> records, Map<String, Object> constraints) {
        List<EnergyPlanDetail> details = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            final int currentHour = hour;
            List<EnergyRealtimeData> group = records.stream()
                    .filter(item -> item.getTimestamp() != null && item.getTimestamp().getHour() == currentHour)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            BigDecimal electricity = sum(group.stream().map(EnergyRealtimeData::getElectricityConsumption).toList());
            BigDecimal steam = sum(group.stream().map(EnergyRealtimeData::getSteamConsumption).toList());
            BigDecimal carbon = sum(group.stream().map(EnergyRealtimeData::getCarbonEmissionTco2).toList());
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setTimestamp(group.get(0).getTimestamp().withMinute(0).withSecond(0).withNano(0));
            detail.setEquipmentId(equipmentId(constraints));
            detail.setElectricityConsumption(scale(electricity, 6));
            detail.setSteamConsumption(scale(steam, 6));
            detail.setCarbonEmissionTco2(scale(carbon, 6));
            detail.setOutput(scale(deriveOutput(electricity), 6));
            details.add(detail);
        }
        return details;
    }

    private List<EnergyPlanDetail> deriveFromSchedule(LocalDate planDate, Map<String, Object> constraints) {
        ProductionSchedulePlan schedulePlan = schedulePlanMapper.selectOne(
                new LambdaQueryWrapper<ProductionSchedulePlan>()
                        .eq(ProductionSchedulePlan::getScheduleDate, planDate)
                        .orderByDesc(ProductionSchedulePlan::getCreateTime)
                        .last("LIMIT 1")
        );
        if (schedulePlan == null) {
            return List.of();
        }
        List<ProductionScheduleDetail> scheduleDetails = scheduleDetailMapper.selectList(
                new LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, schedulePlan.getId())
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );
        List<EnergyPlanDetail> details = new ArrayList<>();
        for (ProductionScheduleDetail scheduleDetail : scheduleDetails) {
            BigDecimal electricity = value(scheduleDetail.getElecForecast());
            BigDecimal production = value(scheduleDetail.getProduction());
            EnergyPlanDetail detail = new EnergyPlanDetail();
            detail.setTimestamp(scheduleDetail.getStartTime());
            detail.setEquipmentId(equipmentId(constraints));
            detail.setOutput(scale(production.multiply(new BigDecimal("100")), 6));
            detail.setElectricityConsumption(scale(electricity, 6));
            detail.setSteamConsumption(scale(electricity.multiply(new BigDecimal("0.005")), 6));
            detail.setCarbonEmissionTco2(scale(electricity.multiply(new BigDecimal("0.00057")), 6));
            details.add(detail);
        }
        return details;
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + " 不能为空，格式应为 yyyy-MM-dd");
        }
        String text = value.trim();
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, fieldName + " 格式错误，应为 yyyy-MM-dd，例如 2026-07-17");
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

    private BigDecimal steamUnitPrice(Map<String, Object> constraints) {
        Object value = constraints != null ? constraints.get("steamUnitPrice") : null;
        return value != null ? new BigDecimal(value.toString()) : new BigDecimal("180.00");
    }

    private Long equipmentId(Map<String, Object> constraints) {
        Object value = constraints != null ? constraints.get("equipmentId") : null;
        return value != null ? Long.valueOf(value.toString()) : 1L;
    }

    private BigDecimal deriveOutput(BigDecimal electricity) {
        return value(electricity).multiply(new BigDecimal("0.08"));
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value(value).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public Result<EnergyPlanVO> getPlanDetail(String planDate) {
        LocalDate date = parseDate(planDate, "planDate");
        EnergyPlan plan = findPlanByDate(date);
        if (plan == null) {
            throw new BusinessException(404, "该日期能源方案不存在");
        }
        return Result.ok(toEnergyPlanVO(plan, true));
    }

    @Override
    public Result<PageResult<EnergyPlanVO>> listHistory(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        int pageNum = query.getPageNum() != null && query.getPageNum() > 0 ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;

        LambdaQueryWrapper<EnergyPlan> wrapper = new LambdaQueryWrapper<>();
        if (query.getDate() != null && !query.getDate().trim().isEmpty()) {
            wrapper.eq(EnergyPlan::getPlanDate, parseDate(query.getDate(), "date"));
        }
        if (query.getStatus() != null && !query.getStatus().trim().isEmpty()) {
            wrapper.eq(EnergyPlan::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(EnergyPlan::getPlanDate)
                .orderByDesc(EnergyPlan::getCreateTime);

        IPage<EnergyPlan> page = energyPlanMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<EnergyPlanVO> records = page.getRecords().stream()
                .map(plan -> toEnergyPlanVO(plan, false))
                .collect(Collectors.toList());
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, records));
    }

    @Override
    public Result<List<EnergyDeviceStatusVO>> getDeviceStatus() {
        List<EnergyEquipment> equipments = energyEquipmentMapper.selectList(
                new LambdaQueryWrapper<EnergyEquipment>().orderByAsc(EnergyEquipment::getId)
        );
        List<EnergyDeviceStatusVO> records = equipments.stream().map(equipment -> {
            EnergyPlanDetail latest = latestDetailForEquipment(equipment.getId());
            BigDecimal currentOutput = latest != null ? value(latest.getOutput()) : BigDecimal.ZERO;
            BigDecimal maxOutput = value(equipment.getMaxOutput());
            BigDecimal loadRate = percent(currentOutput, maxOutput);
            return EnergyDeviceStatusVO.builder()
                    .equipmentId(equipment.getId())
                    .equipmentCode(equipment.getEquipmentCode())
                    .equipmentName(equipment.getEquipmentName())
                    .equipmentType(equipment.getEquipmentType())
                    .status(equipment.getStatus() != null ? equipment.getStatus() : inferEquipmentStatus(loadRate))
                    .currentOutput(scale(currentOutput, 2))
                    .maxOutput(scale(maxOutput, 2))
                    .loadRate(scale(loadRate, 2))
                    .efficiency(scale(equipment.getEfficiency(), 2))
                    .warningLevel(warningLevel(loadRate))
                    .build();
        }).collect(Collectors.toList());
        return Result.ok(records);
    }

    @Override
    public Result<EnergyLoadForecastVO> getLoadForecast(String planDate) {
        EnergyPlan plan = findPlan(planDate);
        if (plan == null) {
            throw new BusinessException(404, "暂无能源方案，请先生成能源运行方案");
        }
        List<EnergyPlanDetail> details = getDetails(plan.getId());
        List<EnergyLoadForecastPointVO> points = details.stream().map(detail ->
                EnergyLoadForecastPointVO.builder()
                        .timestamp(detail.getTimestamp())
                        .hourIndex(detail.getTimestamp() != null ? detail.getTimestamp().getHour() : null)
                        .electricityLoad(scale(detail.getElectricityConsumption(), 2))
                        .steamLoad(scale(detail.getSteamConsumption(), 2))
                        .energyCost(scale(detail.getEnergyCost(), 2))
                        .build()
        ).collect(Collectors.toList());
        EnergyLoadForecastVO vo = EnergyLoadForecastVO.builder()
                .summary(loadForecast(details))
                .points(points)
                .build();
        return Result.ok(vo);
    }

    @Override
    public Result<EnergyConsumptionTrendVO> getConsumptionTrend(PageQueryDTO query) {
        List<EnergyRealtimeData> records = realtimeRecords(query);
        String granularity = isDailyGranularity(query) ? "DAY" : "HOUR";
        Map<String, List<EnergyRealtimeData>> groups = records.stream()
                .filter(item -> item.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        item -> trendKey(item.getTimestamp(), granularity),
                        TreeMap::new,
                        Collectors.toList()
                ));
        List<EnergyConsumptionTrendPointVO> points = groups.entrySet().stream().map(entry -> {
            List<EnergyRealtimeData> items = entry.getValue();
            BigDecimal electricity = sum(items.stream().map(EnergyRealtimeData::getElectricityConsumption).toList());
            BigDecimal steam = sum(items.stream().map(EnergyRealtimeData::getSteamConsumption).toList());
            BigDecimal carbon = sum(items.stream().map(EnergyRealtimeData::getCarbonEmissionTco2).toList());
            return EnergyConsumptionTrendPointVO.builder()
                    .time(entry.getKey())
                    .electricityConsumption(scale(electricity, 2))
                    .steamConsumption(scale(steam, 2))
                    .carbonEmissionTco2(scale(carbon, 4))
                    .energyCost(scale(estimateRealtimeCost(items), 2))
                    .build();
        }).collect(Collectors.toList());
        return Result.ok(EnergyConsumptionTrendVO.builder()
                .granularity(granularity)
                .points(points)
                .build());
    }

    @Override
    public Result<EnergyAnalysisVO> getAnalysis(PageQueryDTO query) {
        List<EnergyRealtimeData> records = realtimeRecords(query);
        EvaluationMetric latestMetric = latestMetric();
        BigDecimal totalElectricity = sum(records.stream().map(EnergyRealtimeData::getElectricityConsumption).toList());
        BigDecimal totalSteam = sum(records.stream().map(EnergyRealtimeData::getSteamConsumption).toList());
        BigDecimal totalCarbon = sum(records.stream().map(EnergyRealtimeData::getCarbonEmissionTco2).toList());
        return Result.ok(EnergyAnalysisVO.builder()
                .sampleCount((long) records.size())
                .totalElectricityConsumption(scale(totalElectricity, 2))
                .totalSteamConsumption(scale(totalSteam, 2))
                .totalCarbonEmissionTco2(scale(totalCarbon, 4))
                .totalEnergyCost(scale(estimateRealtimeCost(records), 2))
                .avgLaggingPowerFactor(avg(records.stream().map(EnergyRealtimeData::getLaggingPowerFactor).toList(), 4))
                .avgLeadingPowerFactor(avg(records.stream().map(EnergyRealtimeData::getLeadingPowerFactor).toList(), 4))
                .mape(latestMetric != null ? latestMetric.getMape() : null)
                .ecBefore(latestMetric != null ? latestMetric.getEcBefore() : null)
                .ecAfter(latestMetric != null ? latestMetric.getEcAfter() : null)
                .er(latestMetric != null ? latestMetric.getEr() : null)
                .build());
    }

    @Override
    public Result<EnergyTrendVO> getTrend(PageQueryDTO query) {
        List<ReportStatistic> stats = reportStats(query);
        List<EnergyTrendPointVO> points = stats.stream()
                .map(stat -> EnergyTrendPointVO.builder()
                        .date(stat.getStatDate())
                        .totalEnergyKgce(scale(stat.getTotalEnergyKgce(), 2))
                        .energyCost(scale(stat.getEnergyCost(), 2))
                        .costSaving(scale(stat.getCostSaving(), 2))
                        .carbonReduction(scale(stat.getCarbonReduction(), 4))
                        .productionOutput(scale(stat.getProductionOutput(), 2))
                        .build())
                .collect(Collectors.toList());
        return Result.ok(EnergyTrendVO.builder().points(points).build());
    }

    @Override
    public Result<EnergyCarbonReductionVO> getCarbonReduction(PageQueryDTO query) {
        List<ReportStatistic> stats = reportStats(query);
        BigDecimal cumulative = BigDecimal.ZERO;
        List<EnergyCarbonReductionPointVO> points = new ArrayList<>();
        for (ReportStatistic stat : stats) {
            BigDecimal carbonReduction = value(stat.getCarbonReduction());
            cumulative = cumulative.add(carbonReduction);
            points.add(EnergyCarbonReductionPointVO.builder()
                    .date(stat.getStatDate())
                    .carbonReduction(scale(carbonReduction, 4))
                    .cumulativeCarbonReduction(scale(cumulative, 4))
                    .build());
        }
        return Result.ok(EnergyCarbonReductionVO.builder()
                .totalCarbonReduction(scale(cumulative, 4))
                .points(points)
                .build());
    }

    private EnergyPlanVO toEnergyPlanVO(EnergyPlan plan, boolean includeDetails) {
        List<EnergyPlanDetail> details = getDetails(plan.getId());

        Map<Long, String> equipmentNameMap = details.stream()
                .map(EnergyPlanDetail::getEquipmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toMap(id -> id, this::equipmentName));

        List<EnergyPlanDetailVO> detailVOs = includeDetails ? details.stream().map(d -> EnergyPlanDetailVO.builder()
                .timestamp(d.getTimestamp())
                .equipmentId(d.getEquipmentId())
                .equipmentName(equipmentNameMap.getOrDefault(d.getEquipmentId(), inferEquipmentName(d.getEquipmentId())))
                .output(d.getOutput())
                .electricityConsumption(d.getElectricityConsumption())
                .steamConsumption(d.getSteamConsumption())
                .carbonEmissionTco2(d.getCarbonEmissionTco2())
                .energyCost(d.getEnergyCost())
                .build()).collect(Collectors.toList()) : null;

        return EnergyPlanVO.builder()
                .planId(plan.getId())
                .taskId(plan.getTaskId())
                .planDate(plan.getPlanDate())
                .status(plan.getStatus())
                .electricityCost(plan.getElectricityCost())
                .steamCost(plan.getSteamCost())
                .totalEnergyCost(plan.getTotalEnergyCost())
                .loadForecast(loadForecast(details))
                .optimizationResult(optimizationResult(plan, details))
                .strategies(strategies(details))
                .pricePolicy(pricePolicy(plan.getElectricPriceMode()))
                .details(detailVOs)
                .build();
    }

    private EnergyPlan findPlan(String planDate) {
        if (planDate != null && !planDate.trim().isEmpty()) {
            return findPlanByDate(parseDate(planDate, "planDate"));
        }
        return energyPlanMapper.selectOne(
                new LambdaQueryWrapper<EnergyPlan>()
                        .orderByDesc(EnergyPlan::getPlanDate)
                        .orderByDesc(EnergyPlan::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private EnergyPlan findPlanByDate(LocalDate date) {
        return energyPlanMapper.selectOne(
                new LambdaQueryWrapper<EnergyPlan>()
                        .eq(EnergyPlan::getPlanDate, date)
                        .orderByDesc(EnergyPlan::getCreateTime)
                        .last("LIMIT 1")
        );
    }

    private List<EnergyPlanDetail> getDetails(Long planId) {
        return energyPlanDetailMapper.selectList(
                new LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getPlanId, planId)
                        .orderByAsc(EnergyPlanDetail::getTimestamp)
        );
    }

    private EnergyPlanDetail latestDetailForEquipment(Long equipmentId) {
        return energyPlanDetailMapper.selectOne(
                new LambdaQueryWrapper<EnergyPlanDetail>()
                        .eq(EnergyPlanDetail::getEquipmentId, equipmentId)
                        .orderByDesc(EnergyPlanDetail::getTimestamp)
                        .last("LIMIT 1")
        );
    }

    private List<EnergyRealtimeData> realtimeRecords(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        LambdaQueryWrapper<EnergyRealtimeData> wrapper = new LambdaQueryWrapper<>();
        String startTime = firstNonBlank(query.getStartTime(), query.getStartDate());
        String endTime = firstNonBlank(query.getEndTime(), query.getEndDate());
        if (startTime != null) {
            wrapper.ge(EnergyRealtimeData::getTimestamp, normalizeDateTime(startTime, false));
        }
        if (endTime != null) {
            wrapper.le(EnergyRealtimeData::getTimestamp, normalizeDateTime(endTime, true));
        }
        wrapper.orderByAsc(EnergyRealtimeData::getTimestamp);
        if (startTime == null && endTime == null) {
            wrapper.last("LIMIT 672");
        }
        return energyRealtimeDataMapper.selectList(wrapper);
    }

    private List<ReportStatistic> reportStats(PageQueryDTO query) {
        if (query == null) {
            query = new PageQueryDTO();
        }
        LambdaQueryWrapper<ReportStatistic> wrapper = new LambdaQueryWrapper<>();
        String startDate = firstNonBlank(query.getStartDate(), query.getStartTime(), query.getDate());
        String endDate = firstNonBlank(query.getEndDate(), query.getEndTime(), query.getDate());
        if (startDate != null) {
            wrapper.ge(ReportStatistic::getStatDate, parseDate(startDate, "startDate"));
        }
        if (endDate != null) {
            wrapper.le(ReportStatistic::getStatDate, parseDate(endDate, "endDate"));
        }
        wrapper.eq(ReportStatistic::getStatType, "DAY")
                .orderByAsc(ReportStatistic::getStatDate);
        if (startDate == null && endDate == null) {
            wrapper.last("LIMIT 31");
        }
        return reportStatisticMapper.selectList(wrapper);
    }

    private EvaluationMetric latestMetric() {
        return evaluationMetricMapper.selectOne(
                new LambdaQueryWrapper<EvaluationMetric>()
                        .orderByDesc(EvaluationMetric::getCalculateTime)
                        .last("LIMIT 1")
        );
    }

    private BigDecimal estimateRealtimeCost(List<EnergyRealtimeData> records) {
        BigDecimal cost = BigDecimal.ZERO;
        for (EnergyRealtimeData record : records) {
            int hour = record.getTimestamp() != null ? record.getTimestamp().getHour() : 0;
            cost = cost.add(value(record.getElectricityConsumption()).multiply(priceForHour(hour)))
                    .add(value(record.getSteamConsumption()).multiply(new BigDecimal("180.00")));
        }
        return cost;
    }

    private BigDecimal avg(List<BigDecimal> values, int scale) {
        List<BigDecimal> present = values.stream()
                .filter(value -> value != null)
                .toList();
        if (present.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum(present).divide(new BigDecimal(present.size()), scale, RoundingMode.HALF_UP);
    }

    private boolean isDailyGranularity(PageQueryDTO query) {
        return query != null && query.getInterval() != null && query.getInterval() >= 1440;
    }

    private String trendKey(LocalDateTime timestamp, String granularity) {
        if ("DAY".equals(granularity)) {
            return timestamp.toLocalDate().toString();
        }
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
    }

    private String normalizeDateTime(String value, boolean endOfDay) {
        String text = value.trim();
        if (text.length() <= 10) {
            return text.substring(0, 10) + (endOfDay ? " 23:59:59" : " 00:00:00");
        }
        return text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String inferEquipmentStatus(BigDecimal loadRate) {
        return value(loadRate).compareTo(BigDecimal.ZERO) > 0 ? "RUNNING" : "STANDBY";
    }

    private String warningLevel(BigDecimal loadRate) {
        BigDecimal rate = value(loadRate);
        if (rate.compareTo(new BigDecimal("95")) >= 0) {
            return "HIGH";
        }
        if (rate.compareTo(new BigDecimal("85")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LoadForecastVO loadForecast(List<EnergyPlanDetail> details) {
        if (details == null || details.isEmpty()) {
            return LoadForecastVO.builder()
                    .peakLoad(BigDecimal.ZERO)
                    .valleyLoad(BigDecimal.ZERO)
                    .avgLoad(BigDecimal.ZERO)
                    .build();
        }
        EnergyPlanDetail peak = details.stream()
                .max(Comparator.comparing(item -> value(item.getElectricityConsumption())))
                .orElse(details.get(0));
        EnergyPlanDetail valley = details.stream()
                .min(Comparator.comparing(item -> value(item.getElectricityConsumption())))
                .orElse(details.get(0));
        BigDecimal totalLoad = sum(details.stream()
                .map(EnergyPlanDetail::getElectricityConsumption)
                .toList());
        BigDecimal avgLoad = totalLoad.divide(new BigDecimal(details.size()), 2, RoundingMode.HALF_UP);
        return LoadForecastVO.builder()
                .peakHour(formatHourRange(peak.getTimestamp()))
                .peakLoad(scale(peak.getElectricityConsumption(), 2))
                .valleyHour(formatHourRange(valley.getTimestamp()))
                .valleyLoad(scale(valley.getElectricityConsumption(), 2))
                .avgLoad(avgLoad)
                .build();
    }

    private EnergyOptimizationResultVO optimizationResult(EnergyPlan plan, List<EnergyPlanDetail> details) {
        BigDecimal totalCost = value(plan.getTotalEnergyCost());
        if (totalCost.compareTo(BigDecimal.ZERO) == 0 && details != null) {
            totalCost = sum(details.stream().map(EnergyPlanDetail::getEnergyCost).toList());
        }
        BigDecimal originalCost = estimateOriginalCost(totalCost, details);
        BigDecimal estimatedSavings = percent(originalCost.subtract(totalCost), originalCost);
        return EnergyOptimizationResultVO.builder()
                .estimatedSavings(scale(nonNegative(estimatedSavings), 2))
                .totalCost(scale(totalCost, 2))
                .originalCost(scale(originalCost, 2))
                .peakShavingRate(scale(peakShavingRate(details), 2))
                .build();
    }

    private List<EnergyStrategyVO> strategies(List<EnergyPlanDetail> details) {
        LoadForecastVO forecast = loadForecast(details);
        List<EnergyStrategyVO> items = new ArrayList<>();
        items.add(EnergyStrategyVO.builder()
                .type("PEAK_SHIFTING")
                .title("峰段负荷削减")
                .description("峰价时段优先降低非关键设备负荷，减少高电价区间外购电。")
                .timeRange("18:00-22:00")
                .impactLevel("HIGH")
                .build());
        items.add(EnergyStrategyVO.builder()
                .type("VALLEY_PRODUCTION")
                .title("谷段增产蓄能")
                .description("将可平移生产任务和蓄热负荷安排到谷价时段，降低单位能耗成本。")
                .timeRange("00:00-08:00")
                .impactLevel("MEDIUM")
                .build());
        items.add(EnergyStrategyVO.builder()
                .type("LOAD_BALANCE")
                .title("平衡小时负荷")
                .description("围绕峰值 " + value(forecast.getPeakLoad()) + " kWh 和谷值 "
                        + value(forecast.getValleyLoad()) + " kWh 调整负荷曲线。")
                .timeRange("全天")
                .impactLevel("MEDIUM")
                .build());
        items.add(EnergyStrategyVO.builder()
                .type("CARBON_CONTROL")
                .title("碳排联动控制")
                .description("对高电耗小时同步监控碳排，优先使用低成本、低碳能源组合。")
                .timeRange("全天")
                .impactLevel("LOW")
                .build());
        return items;
    }

    private PricePolicyVO pricePolicy(String mode) {
        return PricePolicyVO.builder()
                .mode(mode != null && !mode.trim().isEmpty() ? mode : "PEAK_VALLEY")
                .periods(List.of(
                        PricePolicyPeriodVO.builder()
                                .type("VALLEY")
                                .name("谷段")
                                .timeRange("00:00-08:00")
                                .price(new BigDecimal("0.35"))
                                .build(),
                        PricePolicyPeriodVO.builder()
                                .type("FLAT")
                                .name("平段")
                                .timeRange("08:00-18:00,22:00-24:00")
                                .price(new BigDecimal("0.65"))
                                .build(),
                        PricePolicyPeriodVO.builder()
                                .type("PEAK")
                                .name("峰段")
                                .timeRange("18:00-22:00")
                                .price(new BigDecimal("1.05"))
                                .build()
                ))
                .build();
    }

    private BigDecimal estimateOriginalCost(BigDecimal totalCost, List<EnergyPlanDetail> details) {
        BigDecimal base = value(totalCost);
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal peakPremium = BigDecimal.ZERO;
        if (details != null) {
            for (EnergyPlanDetail detail : details) {
                int hour = detail.getTimestamp() != null ? detail.getTimestamp().getHour() : 0;
                if (hour >= 18 && hour < 22) {
                    peakPremium = peakPremium.add(value(detail.getElectricityConsumption())
                            .multiply(new BigDecimal("0.08")));
                }
            }
        }
        BigDecimal uplift = peakPremium.compareTo(BigDecimal.ZERO) > 0
                ? peakPremium
                : base.multiply(new BigDecimal("0.08"));
        return base.add(uplift);
    }

    private BigDecimal peakShavingRate(List<EnergyPlanDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal peak = details.stream()
                .map(EnergyPlanDetail::getElectricityConsumption)
                .map(this::value)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (peak.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal avg = sum(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList())
                .divide(new BigDecimal(details.size()), 6, RoundingMode.HALF_UP);
        return percent(peak.subtract(avg), peak);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100"))
                .divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private String formatHourRange(LocalDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }
        int hour = timestamp.getHour();
        int nextHour = hour + 1;
        return String.format("%02d:00-%02d:00", hour, nextHour);
    }

    private String equipmentName(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        EnergyEquipment equipment = energyEquipmentMapper.selectById(equipmentId);
        if (equipment == null || equipment.getEquipmentName() == null || equipment.getEquipmentName().trim().isEmpty()) {
            return inferEquipmentName(equipmentId);
        }
        return equipment.getEquipmentName();
    }

    private String inferEquipmentName(Long equipmentId) {
        if (equipmentId == null) {
            return "能源设备";
        }
        if (equipmentId == 1L) {
            return "锅炉1";
        }
        return "能源设备-" + equipmentId;
    }
}
