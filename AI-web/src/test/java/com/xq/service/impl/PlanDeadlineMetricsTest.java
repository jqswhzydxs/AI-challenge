package com.xq.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import com.xq.service.metrics.PlanDeadlineMetrics;
import com.xq.model.entity.ProductionScheduleDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanDeadlineMetricsTest {
  @Test void usesFrozenDeadlinesAndWithinIntervalAssumption() {
    var d=new ProductionScheduleDetail();
    d.setStartTime(LocalDateTime.of(2026,11,12,0,0));d.setEndTime(d.getStartTime().plusHours(1));
    d.setProduction(BigDecimal.TEN);
    assertEquals(new BigDecimal("100.00"),PlanDeadlineMetrics.estimate(List.of(d),List.of(order("5","00:30:00"))));
    assertEquals(new BigDecimal("0.00"),PlanDeadlineMetrics.estimate(List.of(d),List.of(order("6","00:30:00"))));
    assertNull(PlanDeadlineMetrics.estimate(List.of(d),List.of()));
  }
  Map<String,Object> order(String quantity,String due) { return Map.of("planned_quantity",quantity,"due_time","2026-11-12 "+due); }
}
