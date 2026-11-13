package com.xq.service.metrics;

import com.xq.model.entity.ProductionScheduleDetail;
import java.math.*;
import java.time.*;
import java.util.*;

/** Expected fulfillment only, assuming constant production within each output interval. */
public final class PlanDeadlineMetrics {
  private PlanDeadlineMetrics() {}

  public static BigDecimal estimate(List<ProductionScheduleDetail> details, List<Map<String, Object>> frozenOrders) {
    if (details.isEmpty() || frozenOrders.isEmpty()) return null;
    if (details.stream().anyMatch(d -> d.getStartTime() == null || d.getEndTime() == null
        || !d.getEndTime().isAfter(d.getStartTime()) || d.getProduction() == null)) return null;
    List<Map<String, Object>> orders = new ArrayList<>(frozenOrders);
    orders.sort(Comparator.comparing(o -> Objects.toString(o.get("due_time"), "")));
    BigDecimal required = BigDecimal.ZERO;
    int satisfied = 0;
    for (var order : orders) {
      LocalDateTime due;
      BigDecimal quantity;
      try {
        due = LocalDateTime.parse(order.get("due_time").toString().replace(' ', 'T'));
        quantity = new BigDecimal(order.get("planned_quantity").toString());
        if (quantity.signum() <= 0) return null;
      } catch (RuntimeException exception) { return null; }
      required = required.add(quantity);
      BigDecimal available = BigDecimal.ZERO;
      for (var detail : details) {
        long seconds = Duration.between(detail.getStartTime(), detail.getEndTime()).toSeconds();
        long elapsed = Math.max(0, Math.min(seconds, Duration.between(detail.getStartTime(), due).toSeconds()));
        available = available.add(detail.getProduction().multiply(BigDecimal.valueOf(elapsed))
            .divide(BigDecimal.valueOf(seconds), 12, RoundingMode.HALF_UP));
      }
      if (available.compareTo(required) >= 0) satisfied++;
    }
    return BigDecimal.valueOf(satisfied).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
  }
}
