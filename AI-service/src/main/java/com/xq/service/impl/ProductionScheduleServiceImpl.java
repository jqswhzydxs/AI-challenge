package com.xq.service.impl;

import com.xq.common.constant.TaskStatus;
import com.xq.common.constant.TaskType;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.vo.TaskVO;
import com.xq.service.ProductionScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.ScheduleDetailVO;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.entity.ProductionScheduleDetail;
import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionScheduleServiceImpl implements ProductionScheduleService {

    private final AlgorithmTaskMapper algorithmTaskMapper;
    private final ProductionSchedulePlanMapper schedulePlanMapper;
    private final ProductionScheduleDetailMapper scheduleDetailMapper;
    private final EvaluationMetricMapper evaluationMetricMapper;

    @Override
    @Transactional
    public Result<TaskVO> generate(ScheduleGenerateDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        LocalDate scheduleDate = parseDate(dto.getScheduleDate(), "scheduleDate");
        int planHorizon = dto.getPlanHorizon() != null ? dto.getPlanHorizon() : 24;
        if (planHorizon <= 0) {
            throw new BusinessException(400, "planHorizon 必须大于 0，建议填写 24");
        }
        LocalDateTime planStart = scheduleDate.atStartOfDay();

        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setFrontendRequestJson(JSON.toJSONString(dto));
        algorithmTaskMapper.insert(task);

        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setTaskId(task.getId());
        plan.setScheduleName(scheduleDate + " 排产方案");
        plan.setScheduleDate(scheduleDate);
        plan.setPlanStartTime(planStart);
        plan.setPlanHorizon(planHorizon);
        plan.setPlanUnit(dto.getPlanUnit() != null ? dto.getPlanUnit() : "hour");
        plan.setDataGranularity(dto.getDataGranularity() != null ? dto.getDataGranularity() : "1 hour");
        plan.setStatus(TaskStatus.SUCCESS);
        plan.setObjective(dto.getObjective());
        plan.setElecCoefficient(getDecimal(dto.getConstraints(), "elecCoefficient", new BigDecimal("42.00")));
        plan.setTotalDemand(new BigDecimal(planHorizon).multiply(new BigDecimal("100.00")));
        plan.setTotalProduction(plan.getTotalDemand());
        plan.setTotalEnergy(plan.getTotalProduction().multiply(plan.getElecCoefficient()));
        plan.setRawPlanJson(JSON.toJSONString(dto));
        schedulePlanMapper.insert(plan);

        for (int hour = 0; hour < planHorizon; hour++) {
            BigDecimal production = new BigDecimal("100.00").add(new BigDecimal(hour % 4).multiply(new BigDecimal("5.00")));
            ProductionScheduleDetail detail = new ProductionScheduleDetail();
            detail.setScheduleId(plan.getId());
            detail.setHourIndex(hour);
            detail.setStartTime(planStart.plusHours(hour));
            detail.setEndTime(planStart.plusHours(hour + 1L));
            detail.setDemand(production);
            detail.setProduction(production);
            detail.setElecForecast(production.multiply(plan.getElecCoefficient()));
            detail.setConflictFlag(0);
            scheduleDetailMapper.insert(detail);
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
        return Result.ok("排产任务已创建", vo);
    }

    @Override
    public Result<SchedulePlanVO> getPlanDetail(Long scheduleId) {
        ProductionSchedulePlan plan = schedulePlanMapper.selectById(scheduleId);
        if (plan == null) {
            throw new BusinessException(404, "排产方案不存在");
        }

        List<ProductionScheduleDetail> details = scheduleDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionScheduleDetail>()
                        .eq(ProductionScheduleDetail::getScheduleId, scheduleId)
                        .orderByAsc(ProductionScheduleDetail::getHourIndex)
        );

        List<ScheduleDetailVO> detailVOs = details.stream().map(d -> ScheduleDetailVO.builder()
                .detailId(d.getId())
                .hourIndex(d.getHourIndex())
                .startTime(d.getStartTime())
                .endTime(d.getEndTime())
                .demand(d.getDemand())
                .production(d.getProduction())
                .elecForecast(d.getElecForecast())
                .build()).collect(Collectors.toList());

        SchedulePlanVO vo = SchedulePlanVO.builder()
                .scheduleId(plan.getId())
                .taskId(plan.getTaskId())
                .scheduleName(plan.getScheduleName())
                .planStartTime(plan.getPlanStartTime())
                .planHorizon(plan.getPlanHorizon())
                .planUnit(plan.getPlanUnit())
                .dataGranularity(plan.getDataGranularity())
                .status(plan.getStatus())
                .elecCoefficient(plan.getElecCoefficient())
                .ecBaseline(plan.getEcBaseline())
                .ecOptimized(plan.getEcOptimized())
                .ecReduction(plan.getEcReduction())
                .optimalTemperature(plan.getOptimalTemperature())
                .optimalSpeed(plan.getOptimalSpeed())
                .totalDemand(plan.getTotalDemand())
                .totalProduction(plan.getTotalProduction())
                .totalEnergy(plan.getTotalEnergy())
                .details(detailVOs)
                .build();
        return Result.ok(vo);
    }

    @Override
    @Transactional
    public Result<ImportPlanResultVO> importDailyPlan(Map<String, Object> dailyPlanJson) {
        if (dailyPlanJson == null || dailyPlanJson.isEmpty()) {
            throw new BusinessException(400, "请求体不能为空，请粘贴 daily_plan_v3.2.json 内容");
        }
        String timestamp = (String) dailyPlanJson.get("timestamp");
        if (timestamp == null || timestamp.trim().isEmpty()) {
            throw new BusinessException(400, "缺少字段: timestamp");
        }
        Object planHorizonValue = dailyPlanJson.get("plan_horizon");
        if (!(planHorizonValue instanceof Number)) {
            throw new BusinessException(400, "缺少字段或字段类型错误: plan_horizon");
        }
        Integer planHorizon = ((Number) planHorizonValue).intValue();
        String planUnit = (String) dailyPlanJson.get("unit");
        String dataGranularity = (String) dailyPlanJson.get("data_granularity");
        BigDecimal ecBaseline = getDecimalAny(dailyPlanJson, null, "EC_baseline", "ecBaseline", "elec_coefficient");
        BigDecimal ecOptimized = getDecimalAny(dailyPlanJson, null, "EC_optimized", "ecOptimized");
        BigDecimal ecReduction = getDecimalAny(dailyPlanJson, null, "EC_reduction", "ecReduction");
        BigDecimal optimalTemperature = getDecimalAny(dailyPlanJson, null, "optimal_temperature", "optimalTemperature");
        BigDecimal optimalSpeed = getDecimalAny(dailyPlanJson, null, "optimal_speed", "optimalSpeed");
        BigDecimal totalDemand = getDecimal(dailyPlanJson, "total_demand", null);
        BigDecimal totalProduction = getDecimal(dailyPlanJson, "total_production", null);
        BigDecimal totalEnergy = getDecimal(dailyPlanJson, "total_energy", null);

        if (planHorizon != 24) {
            throw new BusinessException(400, "plan_horizon 必须为 24");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) dailyPlanJson.get("schedule");
        if (schedule == null || schedule.size() != 24) {
            throw new BusinessException(400, "schedule 必须包含 24 条记录");
        }

        if (totalDemand == null) {
            totalDemand = schedule.stream()
                    .map(item -> getRequiredDecimal(item, "demand"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (totalProduction == null) {
            totalProduction = schedule.stream()
                    .map(item -> getRequiredDecimal(item, "production"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (ecOptimized == null && totalEnergy != null && totalProduction.compareTo(BigDecimal.ZERO) > 0) {
            ecOptimized = totalEnergy.divide(totalProduction, 6, java.math.RoundingMode.HALF_UP);
        }
        if (ecOptimized == null) {
            throw new BusinessException(400, "缺少 EC_optimized，无法计算小时预测电耗");
        }

        // 创建算法任务
        AlgorithmTask task = new AlgorithmTask();
        task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
        task.setStatus(TaskStatus.SUCCESS);
        task.setProgress(100);
        task.setAlgorithmResponseJson(JSON.toJSONString(dailyPlanJson));
        task.setResultFileName("daily_plan_v3.2.json");
        algorithmTaskMapper.insert(task);

        // 创建排产方案主表
        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setTaskId(task.getId());
        plan.setScheduleName("日级排产方案");
        LocalDateTime parsedStartTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        plan.setScheduleDate(parsedStartTime.toLocalDate());
        plan.setPlanStartTime(parsedStartTime);
        plan.setPlanHorizon(planHorizon);
        plan.setPlanUnit(planUnit != null ? planUnit : "hour");
        plan.setDataGranularity(dataGranularity != null ? dataGranularity : "1 minute");
        plan.setStatus(TaskStatus.SUCCESS);
        // 新版 JSON：以 EC_optimized 作为小时预测电耗计算系数，elecCoefficient 仅作为兼容旧前端字段返回。
        plan.setElecCoefficient(ecOptimized);
        plan.setEcBaseline(ecBaseline);
        plan.setEcOptimized(ecOptimized);
        plan.setEcReduction(ecReduction);
        plan.setOptimalTemperature(optimalTemperature);
        plan.setOptimalSpeed(optimalSpeed);
        plan.setTotalDemand(totalDemand);
        plan.setTotalProduction(totalProduction);
        plan.setTotalEnergy(totalEnergy);
        plan.setRawPlanJson(JSON.toJSONString(dailyPlanJson));
        schedulePlanMapper.insert(plan);

        // 关联任务和结果
        task.setResultId(plan.getId());
        algorithmTaskMapper.updateById(task);

        // 创建明细 —— 新版 JSON 无 elec_forecast，用 production * EC_optimized 计算
        LocalDateTime planStart = parsedStartTime;
        for (Map<String, Object> item : schedule) {
            int hour = ((Number) item.get("hour")).intValue();
            LocalDateTime startTime = planStart.plusHours(hour);
            LocalDateTime endTime = startTime.plusHours(1);
            BigDecimal production = getRequiredDecimal(item, "production");
            BigDecimal demand = getRequiredDecimal(item, "demand");

            ProductionScheduleDetail detail = new ProductionScheduleDetail();
            detail.setScheduleId(plan.getId());
            detail.setHourIndex(hour);
            detail.setStartTime(startTime);
            detail.setEndTime(endTime);
            detail.setDemand(demand);
            detail.setProduction(production);
            // 新版无 elec_forecast：用 production * EC_optimized 推导
            BigDecimal elecForecast = getDecimal(item, "elec_forecast",
                    ecOptimized != null ? production.multiply(ecOptimized) : null);
            detail.setElecForecast(elecForecast);
            detail.setConflictFlag(0);
            scheduleDetailMapper.insert(detail);
        }

        EvaluationMetric metric = new EvaluationMetric();
        metric.setBizType("SCHEDULE");
        metric.setBizId(plan.getId());
        metric.setMape(getDecimal(dailyPlanJson, "MAPE", null));
        metric.setEcBefore(ecBaseline);
        metric.setEcAfter(ecOptimized);
        metric.setEr(new BigDecimal("100.00"));
        metric.setCostSaving(null);
        metric.setCarbonReduction(null);
        metric.setCalculateTime(LocalDateTime.now());
        evaluationMetricMapper.insert(metric);

        ImportPlanResultVO vo = ImportPlanResultVO.builder()
                .taskId(task.getId())
                .scheduleId(plan.getId())
                .detailCount(schedule.size())
                .build();
        return Result.ok("导入成功", vo);
    }

    private BigDecimal getDecimal(Map<String, Object> source, String key, BigDecimal defaultValue) {
        if (source == null || source.get(key) == null) {
            return defaultValue;
        }
        return new BigDecimal(source.get(key).toString());
    }

    private BigDecimal getDecimalAny(Map<String, Object> source, BigDecimal defaultValue, String... keys) {
        if (source == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (source.get(key) != null) {
                return new BigDecimal(source.get(key).toString());
            }
        }
        return defaultValue;
    }

    private BigDecimal getRequiredDecimal(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            throw new BusinessException(400, "缺少字段: " + key);
        }
        return new BigDecimal(source.get(key).toString());
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
}
