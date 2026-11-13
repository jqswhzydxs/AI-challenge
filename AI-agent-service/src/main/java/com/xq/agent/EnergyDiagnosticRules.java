package com.xq.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Contextual, conservative rules over a single frozen Core response. */
final class EnergyDiagnosticRules {
  private EnergyDiagnosticRules() {}
  record Diagnosis(String risk, String summary, List<String> suggestions, List<String> basis) {}

  static Diagnosis analyze(Map<String, Object> data, List<String> metrics,
      List<String> problems, List<String> missing) {
    List<String> suggestions = new ArrayList<>();
    List<String> basis = new ArrayList<>(List.of(
        "COST-01：先按费用占比定位成本贡献，成本贡献不等于节约潜力。",
        "LOAD-01：峰均比仅描述已记录时段的负荷集中度，不独立触发业务风险。",
        "LIMIT-01：仅比较同设备、同单位的计划出力与已记录限值；缺失约束保持未知。",
        "PLAN-01：计划指标不代表实测，未重算或模拟不报告收益。"));
    if (data == null) return new Diagnosis("UNKNOWN", "能源证据不足，无法诊断。", List.of("先核对方案和可用数据。"), basis);
    BigDecimal total = number(data.get("totalEnergyCost"));
    BigDecimal electricity = number(data.get("electricityCost"));
    BigDecimal steam = number(data.get("steamCost"));
    BigDecimal peak = number(data.get("peakPowerKw"));
    BigDecimal avg = number(data.get("avgPowerKw"));
    add(metrics, missing, "总能源成本", total, "元");
    add(metrics, missing, "电费", electricity, "元");
    add(metrics, missing, "蒸汽费用", steam, "元");
    add(metrics, missing, "峰时平均功率", peak, "kW");
    add(metrics, missing, "平均负荷", avg, "kW");
    boolean costComplete = total != null && electricity != null && steam != null
        && total.compareTo(electricity.add(steam)) == 0;
    if (!costComplete) missing.add("费用分项缺失或与总成本不一致，无法确认成本贡献");
    if (costComplete && total.signum() > 0) {
      BigDecimal eShare = electricity.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
      BigDecimal sShare = steam.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
      metrics.add("电费占比：" + eShare + "%；蒸汽费用占比：" + sShare + "%（分项费用/总成本）");
      if (steam.compareTo(electricity) > 0) {
        problems.add("成本贡献：蒸汽费用高于电费，优先核查蒸汽计量、单价与工艺需求。");
        suggestions.add("依据费用分项，先检查蒸汽需求、管网损耗及锅炉效率；调整前须满足蒸汽品质、供汽能力和生产交期，不据此估算可节省金额。");
      } else {
        problems.add("成本贡献：电费不低于蒸汽费用，优先核对电量与计费时段。");
        suggestions.add("依据费用分项核对电价及分时用电；负荷调整须满足设备容量、交期与启停约束，重算后再评估收益。");
      }
    } else if (costComplete && total.signum() == 0) {
      metrics.add("总成本为零，费用占比不适用。");
    }
    if (peak != null && avg != null && avg.signum() > 0) {
      BigDecimal ratio = peak.divide(avg, 2, RoundingMode.HALF_UP);
      metrics.add("峰均比：" + ratio + "（峰时平均功率/已记录时段平均功率）");
      if (ratio.compareTo(new BigDecimal("1.50")) > 0) {
        boolean valley = data.get("peakPeriods") instanceof List<?> periods && !periods.isEmpty()
            && periods.stream().allMatch(p -> p instanceof Map<?, ?> m && "VALLEY".equals(m.get("tariffPeriod")));
        problems.add(valley
            ? "负荷集中提示：峰值均落在谷价时段，可能符合移峰安排；不能据此认定业务风险或建议无条件削峰。"
            : "负荷集中提示：峰均比较高，须结合峰值电价与设备约束复核；尚不能认定超限。");
        suggestions.add("检查峰值时段电价、交期与可平移工序；谷价集中负荷可先保留，只有容量或运行约束不满足时再调整。");
      }
    } else if (avg != null && avg.signum() == 0 && peak != null && peak.signum() == 0) {
      metrics.add("计划负荷为零，峰均比不适用。");
    } else missing.add("峰均比：平均负荷不可用或不大于零");
    if (data.get("peakPeriods") instanceof List<?> periods) for (Object entry : periods) {
      if (entry instanceof Map<?, ?> p) metrics.add("峰值时段：" + p.get("start") + " 至 " + p.get("end")
          + " Asia/Shanghai；电价假设：" + (p.get("electricityPrice") == null ? "混合时段，暂无单一电价" : p.get("electricityPrice") + " 元/kWh") + "（" + p.get("tariffPeriod") + "）");
    }
    boolean exceeded = false;
    if (data.get("equipmentLimits") instanceof List<?> limits) for (Object entry : limits) {
      if (!(entry instanceof Map<?, ?> limit)) continue;
      BigDecimal min = number(limit.get("minOutput")), max = number(limit.get("maxOutput"));
      BigDecimal observedMin = number(limit.get("observedMinOutput")), observedMax = number(limit.get("observedMaxOutput"));
      if (!Boolean.TRUE.equals(limit.get("comparable"))) {
        missing.add("设备 " + limit.get("equipmentId") + "：限值、出力或单位缺失，无法确认设备风险");
        continue;
      }
      if (min == null || max == null || observedMin == null || observedMax == null)
        missing.add("设备 " + limit.get("equipmentId") + "：部分限值或出力缺失，仅检查可比较的已知边界");
      metrics.add("设备 " + limit.get("name") + "：计划出力 " + Objects.toString(observedMin, "—") + "–" + Objects.toString(observedMax, "—")
          + " " + limit.get("outputUnit") + "；配置范围 " + Objects.toString(min, "—") + "–" + Objects.toString(max, "—") + " " + limit.get("outputUnit"));
      if ((min != null && observedMin != null && observedMin.compareTo(min) < 0)
          || (max != null && observedMax != null && observedMax.compareTo(max) > 0)) {
        exceeded = true;
        problems.add("设备超限风险：设备 " + limit.get("name") + " 的计划出力超出同单位配置范围。");
        suggestions.add("依据设备限值暂停采用超限时段设置，检查设备配置与原始出力；在供能、交期和运行约束内重算方案，再确认可执行性。");
      }
    }
    if (!Boolean.TRUE.equals(data.get("constraintsComplete"))) {
      missing.add("全厂电力容量、交期、启停、爬坡及连续运行约束不完整；未见已知设备超限也不能确认整体安全");
    }
    BigDecimal expectedDeadline = number(data.get("plannedDeadlineCompliancePct"));
    if (expectedDeadline != null) {
      metrics.add("预计交期满足率：" + expectedDeadline + "%（冻结订单、时段内均匀生产假设；非实际交付）");
      if (expectedDeadline.compareTo(BigDecimal.valueOf(100)) < 0) {
        problems.add("计划交期提示：按已冻结订单与时段内均匀生产假设，部分订单预计不能在交期前完成；需复核小时内生产分布与交期精度。");
        suggestions.add("优先核对冻结订单交期和小时内的产出安排；先确保交期可满足，再在设备及运行约束内评估谷价迁移，不依据峰均比直接削峰。");
      }
    }
    suggestions.add("当前为计划派生证据；先补齐现场约束与实测数据，经过重算或模拟后再判断调整收益。");
    String risk = exceeded ? "HIGH" : Boolean.TRUE.equals(data.get("constraintsComplete")) && missing.isEmpty() ? "LOW" : "UNKNOWN";
    String summary = exceeded ? "计划出力触及设备配置边界之外，存在设备超限风险；请先核查限值并重算。"
        : "已按成本贡献和电价时段分析计划；现有约束不足，整体运行风险未知。";
    return new Diagnosis(risk, summary, List.copyOf(suggestions), List.copyOf(basis));
  }

  static BigDecimal number(Object value) {
    try { return value == null ? null : new BigDecimal(value.toString()); }
    catch (RuntimeException exception) { return null; }
  }
  private static void add(List<String> metrics, List<String> missing, String label, BigDecimal value, String unit) {
    if (value == null) missing.add(label + "：暂无数据");
    else metrics.add(label + "：" + value.stripTrailingZeros().toPlainString() + " " + unit);
  }
}
