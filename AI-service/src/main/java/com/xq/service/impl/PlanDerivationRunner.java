package com.xq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.*;
import com.xq.model.entity.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PlanDerivationRunner {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ProductionSchedulePlanMapper schedules;
  private final EnergyPlanMapper plans;
  private final EnergyPlanDetailMapper details;
  private final JointOptimizationPlanMapper evaluations;
  private final EnergyPlanServiceImpl energy;
  private final JointOptimizationServiceImpl joint;

  public PlanDerivationRunner(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      ProductionSchedulePlanMapper schedules,
      EnergyPlanMapper plans,
      EnergyPlanDetailMapper details,
      JointOptimizationPlanMapper evaluations,
      EnergyPlanServiceImpl energy,
      JointOptimizationServiceImpl joint) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.schedules = schedules;
    this.plans = plans;
    this.details = details;
    this.evaluations = evaluations;
    this.energy = energy;
    this.joint = joint;
  }

  @Scheduled(fixedDelayString = "${algorithm.derivation-ms:1500}")
  public void tick() {
    for (var row :
        jdbc.queryForList(
            "SELECT request_id FROM plan_derivation_job WHERE state<>'COMPLETE' AND attempt<5 AND"
                + " next_run<=UTC_TIMESTAMP(3) LIMIT 5")) {
      String id = row.get("request_id").toString();
      try {
        tx.executeWithoutResult(status -> step(id));
      } catch (RuntimeException exception) {
        tx.executeWithoutResult(
            status -> {
              jdbc.update(
                  "UPDATE plan_derivation_job SET"
                      + " state='PARTIAL_FAILED',attempt=attempt+1,error_code='DERIVATION_FAILED',next_run=TIMESTAMPADD(SECOND,10,UTC_TIMESTAMP(3))"
                      + " WHERE request_id=? AND state<>'COMPLETE'",
                  id);
              jdbc.update(
                  "UPDATE algorithm_task t JOIN plan_derivation_job j ON j.task_id=t.id SET"
                      + " t.derivation_status='PARTIAL_FAILED',t.message='排产已导入；能源或协同派生失败，正在按原方案重试'"
                      + " WHERE j.request_id=?",
                  id);
            });
      }
    }
  }

  private void step(String id) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM plan_derivation_job WHERE request_id=? AND state<>'COMPLETE' AND"
                + " attempt<5 FOR UPDATE SKIP LOCKED",
            id);
    if (rows.isEmpty()) return;
    var job = rows.get(0);
    long scheduleId = ((Number) job.get("schedule_id")).longValue();
    ProductionSchedulePlan schedule = schedules.selectById(scheduleId);
    EnergyPlan plan =
        plans.selectOne(
            new LambdaQueryWrapper<EnergyPlan>()
                .eq(EnergyPlan::getSourceScheduleId, scheduleId)
                .ne(EnergyPlan::getStatus, "FAILED")
                .orderByAsc(EnergyPlan::getCreateTime)
                .last("LIMIT 1"));
    if (plan == null) {
      var task =
          jdbc.queryForMap(
              "SELECT algorithm_response_json,create_by FROM algorithm_task WHERE id=?",
              job.get("task_id"));
      var output = JSON.parseObject(Objects.toString(task.get("algorithm_response_json"), "{}"));
      var block = output.getJSONObject("energy_plan");
      if (block == null)
        plan =
            energy.generateDerivedPlanForSchedule(
                schedule, Map.of("steamUnitPrice", "180.00", "equipmentId", 1L));
      else {
        var items = block.getJSONArray("details");
        if (items == null || items.size() != 24)
          throw new IllegalArgumentException("ENERGY_OUTPUT_INVALID");
        plan = new EnergyPlan();
        plan.setSourceScheduleId(scheduleId);
        plan.setTaskId(((Number) job.get("task_id")).longValue());
        plan.setPlanDate(schedule.getScheduleDate());
        plan.setStatus("SUCCESS");
        plan.setObjective("MIN_COST");
        plan.setElectricPriceMode("PEAK_VALLEY");
        plan.setTimeInterval(60);
        plan.setCreateBy(schedule.getCreateBy());
        plan.setTotalEnergyCost(block.getBigDecimal("total_energy_cost"));
        plan.setElectricityCost(block.getBigDecimal("electricity_cost"));
        plan.setSteamCost(block.getBigDecimal("steam_cost"));
        plans.insert(plan);
        Set<LocalDateTime> timestamps = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
          var item = items.getJSONObject(i);
          LocalDateTime timestamp =
              LocalDateTime.parse(
                  item.getString("timestamp"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
          if (!timestamp.toLocalDate().equals(schedule.getScheduleDate())
              || !timestamps.add(timestamp))
            throw new IllegalArgumentException("ENERGY_DATE_INVALID");
          var detail = new EnergyPlanDetail();
          detail.setPlanId(plan.getId());
          detail.setTimestamp(timestamp);
          detail.setEquipmentId(item.getLong("equipment_id"));
          detail.setOutput(item.getBigDecimal("boiler_output"));
          detail.setElectricityConsumption(item.getBigDecimal("electricity_consumption"));
          detail.setSteamConsumption(item.getBigDecimal("steam_consumption"));
          detail.setCarbonEmissionTco2(item.getBigDecimal("carbon_emission_tco2"));
          detail.setEnergyCost(item.getBigDecimal("energy_cost"));
          details.insert(detail);
        }
      }
      jdbc.update(
          "UPDATE plan_derivation_job SET state='EVALUATION_RUNNING',error_code=NULL WHERE"
              + " request_id=?",
          id);
      jdbc.update(
          "UPDATE algorithm_task SET derivation_status='EVALUATION_RUNNING' WHERE id=?",
          job.get("task_id"));
      return;
    }
    var evaluation =
        evaluations.selectOne(
            new LambdaQueryWrapper<JointOptimizationPlan>()
                .eq(JointOptimizationPlan::getScheduleId, scheduleId)
                .eq(JointOptimizationPlan::getEnergyPlanId, plan.getId())
                .eq(JointOptimizationPlan::getStatus, "SUCCESS")
                .last("LIMIT 1"));
    if (evaluation == null) {
      evaluation = joint.generateEvaluationForPlans(scheduleId, plan.getId());
      evaluation.setCreateBy(schedule.getCreateBy());
      evaluations.updateById(evaluation);
    }
    jdbc.update(
        "UPDATE plan_derivation_job SET"
            + " state='COMPLETE',completed_at=UTC_TIMESTAMP(3),error_code=NULL WHERE request_id=?",
        id);
    jdbc.update(
        "UPDATE algorithm_task SET derivation_status='COMPLETE',message='排产已导入，能源与协同派生已完成' WHERE"
            + " id=?",
        job.get("task_id"));
  }
}
