package com.xq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.constant.*;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.*;
import com.xq.model.entity.*;
import com.xq.model.vo.ImportPlanResultVO;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate transactional bean: one task identity, one source request, all hourly writes or none.
 */
@Service
@RequiredArgsConstructor
public class ScheduleResultApplicationService {
  private final AlgorithmTaskMapper tasks;
  private final ProductionSchedulePlanMapper plans;
  private final ProductionScheduleDetailMapper details;
  private final EvaluationMetricMapper metrics;
  private final ProductionOrderMapper orders;
  private final JdbcTemplate jdbc;

  @Transactional
  public Result<ImportPlanResultVO> manual(Map<String, Object> daily) {
    AlgorithmTask task = new AlgorithmTask();
    task.setTaskType(TaskType.PRODUCTION_SCHEDULE);
    task.setStatus(TaskStatus.RUNNING);
    task.setProgress(70);
    task.setRetryCount(0);
    tasks.insert(task);
    return applyExisting(
        task, UUID.randomUUID().toString(), daily, List.of(), JSON.toJSONString(daily));
  }

  @Transactional
  public Result<ImportPlanResultVO> applyExisting(
      AlgorithmTask task,
      String requestId,
      Map<String, Object> daily,
      List<ProductionOrder> inputOrders,
      String raw) {
    jdbc.queryForList("SELECT id FROM algorithm_task WHERE id=? FOR UPDATE", task.getId());
    var existing =
        plans.selectOne(
            new LambdaQueryWrapper<ProductionSchedulePlan>()
                .eq(ProductionSchedulePlan::getSourceRequestId, requestId));
    if (existing != null)
      return Result.ok(
          ImportPlanResultVO.builder()
              .taskId(task.getId())
              .scheduleId(existing.getId())
              .detailCount(24)
              .build());
    if (daily == null
        || !(daily.get("schedule") instanceof List<?> schedule)
        || schedule.size() != 24
        || !(daily.get("plan_horizon") instanceof Number horizon)
        || horizon.intValue() != 24) throw new BusinessException(400, "日排产必须包含完整的 24 小时记录");
    LocalDateTime start;
    try {
      start =
          LocalDateTime.parse(
              Objects.toString(daily.get("timestamp"), ""),
              DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    } catch (RuntimeException exception) {
      throw new BusinessException(400, "排产 timestamp 无效");
    }
    Set<Integer> hours = new HashSet<>();
    for (Object entry : schedule) {
      if (!(entry instanceof Map<?, ?> item)
          || !(item.get("hour") instanceof Number hour)
          || hour.intValue() < 0
          || hour.intValue() > 23
          || hour.doubleValue() != hour.intValue()
          || !hours.add(hour.intValue())) throw new BusinessException(400, "小时序号必须唯一且位于日计划范围内");
      required(item, "production");
      required(item, "demand");
    }
    BigDecimal ec = decimal(daily, "EC_optimized", "ecOptimized");
    BigDecimal production = BigDecimal.ZERO, demand = BigDecimal.ZERO;
    for (Object item : schedule) {
      production = production.add(required((Map<?, ?>) item, "production"));
      demand = demand.add(required((Map<?, ?>) item, "demand"));
    }
    BigDecimal totalEnergy = decimal(daily, "total_energy");
    if (ec == null && totalEnergy != null && production.signum() > 0)
      ec = totalEnergy.divide(production, 6, java.math.RoundingMode.HALF_UP);
    if (ec == null || ec.signum() < 0) throw new BusinessException(400, "缺少有效 EC_optimized");
    ProductionSchedulePlan plan = new ProductionSchedulePlan();
    plan.setTaskId(task.getId());
    plan.setSourceRequestId(requestId);
    plan.setCreateBy(task.getCreateBy());
    plan.setScheduleName("日级排产方案");
    plan.setScheduleDate(start.toLocalDate());
    plan.setPlanStartTime(start);
    plan.setPlanHorizon(24);
    plan.setPlanUnit("hour");
    plan.setDataGranularity(Objects.toString(daily.get("data_granularity"), "unknown"));
    plan.setStatus(TaskStatus.SUCCESS);
    plan.setElecCoefficient(ec);
    plan.setEcOptimized(ec);
    plan.setEcBaseline(decimal(daily, "EC_baseline", "ecBaseline"));
    plan.setEcReduction(decimal(daily, "EC_reduction", "ecReduction"));
    plan.setOptimalTemperature(decimal(daily, "optimal_temperature"));
    plan.setOptimalSpeed(decimal(daily, "optimal_speed"));
    plan.setTotalDemand(demand);
    plan.setTotalProduction(production);
    plan.setTotalEnergy(totalEnergy == null ? production.multiply(ec) : totalEnergy);
    plan.setRawPlanJson(JSON.toJSONString(daily));
    plans.insert(plan);
    for (Object entry : schedule) {
      Map<?, ?> item = (Map<?, ?>) entry;
      int hour = ((Number) item.get("hour")).intValue();
      var detail = new ProductionScheduleDetail();
      detail.setScheduleId(plan.getId());
      detail.setHourIndex(hour);
      detail.setStartTime(start.plusHours(hour));
      detail.setEndTime(start.plusHours(hour + 1));
      detail.setProduction(required(item, "production"));
      detail.setDemand(required(item, "demand"));
      detail.setElecForecast(
          item.get("elec_forecast") == null
              ? detail.getProduction().multiply(ec)
              : required(item, "elec_forecast"));
      detail.setConflictFlag(0);
      details.insert(detail);
    }
    EvaluationMetric metric = new EvaluationMetric();
    metric.setBizType("SCHEDULE");
    metric.setBizId(plan.getId());
    metric.setMape(decimal(daily, "MAPE", "mape"));
    metric.setEr(decimal(daily, "ER", "er"));
    metric.setEcBefore(plan.getEcBaseline());
    metric.setEcAfter(ec);
    metric.setCalculateTime(LocalDateTime.now());
    metrics.insert(metric);
    for (ProductionOrder order : inputOrders) {
      order.setStatus("SCHEDULED");
      orders.updateById(order);
    }
    task.setResultId(plan.getId());
    task.setStatus(TaskStatus.SUCCESS);
    task.setProgress(100);
    task.setAlgorithmResponseJson(raw);
    task.setMessage("排产已导入；能源及协同派生进度单独查看");
    task.setFinishTime(LocalDateTime.now());
    tasks.updateById(task);
    jdbc.update(
        "INSERT INTO plan_derivation_job(request_id,task_id,schedule_id) VALUES(?,?,?)",
        requestId,
        task.getId(),
        plan.getId());
    return Result.ok(
        "导入成功",
        ImportPlanResultVO.builder()
            .taskId(task.getId())
            .scheduleId(plan.getId())
            .detailCount(24)
            .build());
  }

  private BigDecimal required(Map<?, ?> data, String key) {
    try {
      BigDecimal value = new BigDecimal(data.get(key).toString());
      if (value.signum() < 0) throw new IllegalArgumentException();
      return value;
    } catch (RuntimeException exception) {
      throw new BusinessException(400, "缺少有效非负指标: " + key);
    }
  }

  private BigDecimal decimal(Map<String, Object> data, String... keys) {
    for (String key : keys) if (data.get(key) != null) return required(data, key);
    return null;
  }
}
