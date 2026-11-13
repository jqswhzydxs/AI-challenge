package com.xq.service.agent;

import com.alibaba.fastjson2.JSON;
import com.xq.mapper.*;
import com.xq.model.entity.*;
import com.xq.service.metrics.EnergyMetrics;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read only, server-owned evidence; no client-provided limits become business facts. */
@Component
@RequiredArgsConstructor
public class EnergyEvidenceService {
  private final EnergyEquipmentMapper equipmentMapper;
  private final ProductionSchedulePlanMapper schedules;
  private final ProductionScheduleDetailMapper scheduleDetails;

  public Map<String, Object> describe(EnergyPlan plan, List<EnergyPlanDetail> details) {
    Map<String, Object> data = EnergyMetrics.summarize(plan, details);
    List<Map<String, Object>> limits = new ArrayList<>();
    for (Long id : details.stream().map(EnergyPlanDetail::getEquipmentId).filter(Objects::nonNull).distinct().toList()) {
      EnergyEquipment equipment = equipmentMapper.selectById(id);
      if (equipment == null) continue;
      Map<String, Object> limit = new LinkedHashMap<>();
      limit.put("equipmentId", id.toString());
      limit.put("name", equipment.getEquipmentName());
      limit.put("equipmentType", equipment.getEquipmentType());
      limit.put("minOutput", equipment.getMinOutput());
      limit.put("maxOutput", equipment.getMaxOutput());
      // BOILER output is explicitly MW in both generators. Other device units are not recorded.
      boolean comparable = "BOILER".equalsIgnoreCase(equipment.getEquipmentType());
      limit.put("outputUnit", comparable ? "MW" : "UNKNOWN");
      limit.put("comparable", comparable);
      limit.put("observedMinOutput", details.stream().filter(d -> id.equals(d.getEquipmentId())).map(EnergyPlanDetail::getOutput).filter(Objects::nonNull).min(java.math.BigDecimal::compareTo).orElse(null));
      limit.put("observedMaxOutput", details.stream().filter(d -> id.equals(d.getEquipmentId())).map(EnergyPlanDetail::getOutput).filter(Objects::nonNull).max(java.math.BigDecimal::compareTo).orElse(null));
      limit.put("source", "energy_equipment：设备配置，非全厂电网容量");
      limits.add(limit);
    }
    data.put("equipmentLimits", limits);
    List<Map<String, Object>> available = new ArrayList<>();
    for (var equipment : equipmentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EnergyEquipment>())) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("equipmentId", equipment.getId().toString());
      item.put("name", equipment.getEquipmentName());
      item.put("minOutput", equipment.getMinOutput());
      item.put("maxOutput", equipment.getMaxOutput());
      item.put("unit", equipment.getRemark() != null && equipment.getRemark().contains("MW") ? "MW" : "UNKNOWN");
      item.put("basis", "配置记录；未关联到方案明细时不能据此判断方案超限");
      available.add(item);
    }
    data.put("availableEquipmentCapacities", available);
    data.put("electricalCapacityKw", null); // No electrical contract/capacity field exists.
    data.put("operatingConstraints", List.of("设备出力需在已配置的同单位上下限内", "移峰前须核对交期、启停、爬坡与最小连续运行时间；当前未记录完整约束"));
    data.put("constraintsComplete", false);
    data.put("deadlineEvidence", "UNKNOWN");
    data.put("rawCollectionInterval", "UNKNOWN");
    data.put("calculationInterval", "UNKNOWN");
    if (plan.getSourceScheduleId() != null) {
      var schedule = schedules.selectById(plan.getSourceScheduleId());
      if (schedule != null && schedule.getRawPlanJson() != null) {
        var raw = JSON.parseObject(schedule.getRawPlanJson());
        data.put("rawCollectionInterval", raw.getOrDefault("raw_data_granularity", "UNKNOWN"));
        data.put("calculationInterval", raw.getOrDefault("calculation_granularity", raw.getOrDefault("data_granularity", "UNKNOWN")));
        data.put("solverStatus", raw.getOrDefault("solver_status", "UNKNOWN"));
        if (raw.get("order_snapshot") instanceof List<?> orders) {
          List<Map<String, Object>> frozen = new ArrayList<>();
          for (Object order : orders) frozen.add(JSON.parseObject(JSON.toJSONString(order)));
          data.put("orderDeadlines", frozen);
          data.put("deadlineEvidence", frozen.isEmpty() ? "UNKNOWN" : "FROZEN_ORDER_SNAPSHOT");
          var production = scheduleDetails.selectList(
              new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductionScheduleDetail>()
                  .eq(ProductionScheduleDetail::getScheduleId, schedule.getId())
                  .orderByAsc(ProductionScheduleDetail::getHourIndex));
          data.put("plannedDeadlineCompliancePct", com.xq.service.metrics.PlanDeadlineMetrics.estimate(production, frozen));
          data.put("deadlineAssumption", "冻结订单按交期累计分配，假设排产时段内均匀生产；预计满足不代表实际交付");
        }
      }
    }
    return data;
  }
}
