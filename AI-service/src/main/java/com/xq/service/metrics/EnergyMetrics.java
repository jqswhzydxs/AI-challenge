package com.xq.service.metrics;

import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** The persisted consumption fields are interval quantities, never instantaneous loads. */
public final class EnergyMetrics {
  private EnergyMetrics() {}

  public static BigDecimal averageRate(BigDecimal quantity, Integer minutes) {
    if (quantity == null || minutes == null || minutes <= 0) return null;
    return quantity.multiply(BigDecimal.valueOf(60))
        .divide(BigDecimal.valueOf(minutes), 6, RoundingMode.HALF_UP);
  }

  public static BigDecimal share(BigDecimal part, BigDecimal total) {
    if (part == null || total == null || total.signum() <= 0) return null;
    return part.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
  }

  public static BigDecimal completeSum(List<BigDecimal> values) {
    if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) return null;
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public static BigDecimal price(LocalDateTime time) {
    if (time == null) return null;
    return new BigDecimal(time.getHour() < 8 || time.getHour() >= 22 ? "0.35" : "1.05");
  }

  public static BigDecimal uniformIntervalPrice(LocalDateTime start, int minutes) {
    if (start == null || minutes <= 0 || minutes >= 1440) return null;
    BigDecimal first = price(start);
    LocalDateTime end = start.plusMinutes(minutes);
    LocalDateTime boundary = start.withMinute(0).withSecond(0).withNano(0).plusHours(1);
    while (boundary.isBefore(end)) {
      if (price(boundary).compareTo(first) != 0) return null;
      boundary = boundary.plusHours(1);
    }
    return first;
  }

  public static Map<String, Object> summarize(EnergyPlan plan, List<EnergyPlanDetail> details) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("planId", plan.getId() == null ? null : plan.getId().toString());
    result.put("planDate", plan.getPlanDate());
    result.put("timezone", "Asia/Shanghai");
    result.put("measurementType", "DERIVED_FROM_PLAN");
    result.put("executionStatus", "UNKNOWN");
    result.put("dataSource", plan.getSourceScheduleId() == null
        ? "能源方案明细（历史来源未记录）" : "排产方案派生：" + plan.getSourceScheduleId());
    result.put("timeIntervalMinutes", plan.getTimeInterval());
    result.put("quantityUnits", Map.of("electricity", "kWh", "steam", "t"));
    result.put("rateUnits", Map.of("electricity", "kW", "steam", "t/h"));
    result.put("rateFormula", "平均功率/流量 = 分时用量 × 60 / 时段分钟数；均值只覆盖有记录时段");
    result.put("totalEnergyCost", plan.getTotalEnergyCost());
    result.put("electricityCost", plan.getElectricityCost());
    result.put("steamCost", plan.getSteamCost());
    result.put("electricityCostSharePct", share(plan.getElectricityCost(), plan.getTotalEnergyCost()));
    result.put("steamCostSharePct", share(plan.getSteamCost(), plan.getTotalEnergyCost()));
    result.put("costBreakdownComplete", plan.getElectricityCost() != null && plan.getSteamCost() != null
        && plan.getTotalEnergyCost() != null
        && plan.getElectricityCost().add(plan.getSteamCost()).compareTo(plan.getTotalEnergyCost()) == 0);
    result.put("priceSource", "生成器固定电价假设：00–08、22–24 谷价，其余峰价；非现场合同核验");
    result.put("detailCount", details.size());
    result.put("totalCarbonEmissionTco2", completeSum(details.stream().map(EnergyPlanDetail::getCarbonEmissionTco2).toList()));
    result.put("totalElectricityKwh", completeSum(details.stream().map(EnergyPlanDetail::getElectricityConsumption).toList()));
    result.put("totalSteamTonnes", completeSum(details.stream().map(EnergyPlanDetail::getSteamConsumption).toList()));
    // Multiple devices at a timestamp contribute to the same factory interval.
    Map<LocalDateTime, List<EnergyPlanDetail>> groups = new TreeMap<>();
    for (var detail : details) if (detail.getTimestamp() != null)
      groups.computeIfAbsent(detail.getTimestamp(), key -> new ArrayList<>()).add(detail);
    List<BigDecimal> quantities = new ArrayList<>();
    List<Map<String, Object>> intervals = new ArrayList<>();
    List<Map<String, Object>> peaks = new ArrayList<>();
    BigDecimal peak = null;
    boolean complete = !details.isEmpty() && details.stream().allMatch(d -> d.getTimestamp() != null);
    for (var entry : groups.entrySet()) {
      BigDecimal quantity = completeSum(entry.getValue().stream().map(EnergyPlanDetail::getElectricityConsumption).toList());
      quantities.add(quantity);
      BigDecimal power = averageRate(quantity, plan.getTimeInterval());
      Map<String, Object> interval = new LinkedHashMap<>();
      interval.put("timestamp", entry.getKey());
      interval.put("electricityConsumption", quantity);
      interval.put("steamConsumption", completeSum(entry.getValue().stream().map(EnergyPlanDetail::getSteamConsumption).toList()));
      interval.put("electricityLoad", power);
      interval.put("steamLoad", averageRate((BigDecimal) interval.get("steamConsumption"), plan.getTimeInterval()));
      intervals.add(interval);
      if (power == null) { complete = false; continue; }
      if (peak == null || power.compareTo(peak) > 0) { peak = power; peaks.clear(); }
      if (power.compareTo(peak) == 0) {
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", entry.getKey());
        period.put("end", entry.getKey().plusMinutes(plan.getTimeInterval()));
        period.put("averagePowerKw", power);
        BigDecimal intervalPrice = uniformIntervalPrice(entry.getKey(), plan.getTimeInterval());
        period.put("electricityPrice", intervalPrice);
        period.put("tariffPeriod", intervalPrice == null ? "MIXED"
            : intervalPrice.compareTo(new BigDecimal("0.35")) == 0 ? "VALLEY" : "PEAK");
        peaks.add(period);
      }
    }
    BigDecimal total = completeSum(quantities);
    BigDecimal avg = complete && total != null && !groups.isEmpty()
        ? averageRate(total.divide(BigDecimal.valueOf(groups.size()), 10, RoundingMode.HALF_UP), plan.getTimeInterval()) : null;
    result.put("peakPowerKw", complete ? peak : null);
    result.put("avgPowerKw", avg);
    result.put("intervals", intervals);
    var valley = complete ? intervals.stream().min(Comparator.comparing(p -> (BigDecimal) p.get("electricityLoad"))).orElse(null) : null;
    result.put("valleyPowerKw", valley == null ? null : valley.get("electricityLoad"));
    result.put("valleyPeriodStart", valley == null ? null : valley.get("timestamp"));
    result.put("peakPeriods", complete ? peaks : List.of());
    result.put("coveredIntervalCount", groups.size());
    result.put("loadEvidenceComplete", complete && avg != null);
    // Backward-compatible aliases retain interval-quantity semantics.
    result.put("peakElectricity", complete ? quantities.stream().filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null) : null);
    result.put("avgElectricity", total == null || groups.isEmpty() ? null : total.divide(BigDecimal.valueOf(groups.size()), 6, RoundingMode.HALF_UP));
    return result;
  }
}
