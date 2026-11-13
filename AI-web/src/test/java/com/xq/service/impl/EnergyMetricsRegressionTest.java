package com.xq.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import com.xq.model.entity.*;
import com.xq.service.metrics.EnergyMetrics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnergyMetricsRegressionTest {
  @ParameterizedTest
  @CsvSource({"15,40", "30,20", "60,10", "120,5"})
  void quantityToAverageRateUsesActualInterval(int minutes, String expected) {
    assertEquals(0, new BigDecimal(expected).compareTo(EnergyMetrics.averageRate(BigDecimal.TEN, minutes)));
  }

  @Test void missingIsUnknownAndZeroIsValid() {
    assertNull(EnergyMetrics.averageRate(null, 15));
    assertNull(EnergyMetrics.averageRate(BigDecimal.TEN, null));
    assertNull(EnergyMetrics.averageRate(BigDecimal.TEN, 0));
    assertEquals(0, BigDecimal.ZERO.compareTo(EnergyMetrics.averageRate(BigDecimal.ZERO, 15)));
    assertNull(EnergyMetrics.completeSum(Arrays.asList(BigDecimal.ONE, null)));
    assertNull(EnergyMetrics.completeSum(List.of()));
    assertEquals(BigDecimal.ZERO, EnergyMetrics.completeSum(List.of(BigDecimal.ZERO)));
    assertNull(EnergyMetrics.share(BigDecimal.ZERO, BigDecimal.ZERO));
  }

  @Test void costsAndSharesAreCalculatedFromCurrentPlan() {
    var plan = plan();
    plan.setElectricityCost(new BigDecimal("47.40"));
    plan.setSteamCost(new BigDecimal("2008.80"));
    plan.setTotalEnergyCost(new BigDecimal("2056.20"));
    var data = EnergyMetrics.summarize(plan, List.of(detail(1, "10")));
    assertEquals(Boolean.TRUE, data.get("costBreakdownComplete"));
    assertEquals(new BigDecimal("2.3052"), data.get("electricityCostSharePct"));
    assertEquals(new BigDecimal("97.6948"), data.get("steamCostSharePct"));
    plan.setSteamCost(new BigDecimal("47.40"));
    plan.setTotalEnergyCost(new BigDecimal("94.80"));
    assertEquals(new BigDecimal("50.0000"), EnergyMetrics.summarize(plan, List.of()).get("steamCostSharePct"));
  }

  @Test void sameTimestampIsAggregatedBeforePeakAndAverage() {
    var data = EnergyMetrics.summarize(plan(), List.of(detail(1, "5"), detail(1, "5"), detail(2, "2")));
    assertEquals(new BigDecimal("40.000000"), data.get("peakPowerKw"));
    assertEquals(new BigDecimal("24.000000"), data.get("avgPowerKw"));
    assertEquals("DERIVED_FROM_PLAN", data.get("measurementType"));
    assertEquals("UNKNOWN", data.get("executionStatus"));
    var peaks=(List<Map<String,Object>>) data.get("peakPeriods");
    assertEquals("VALLEY", peaks.get(0).get("tariffPeriod"));
    assertEquals(new BigDecimal("0.35"), peaks.get(0).get("electricityPrice"));
  }

  @Test void incompleteIntervalsDoNotYieldKnownPeak() {
    var data = EnergyMetrics.summarize(plan(), List.of(detail(1, "5"), detail(2, null)));
    assertNull(data.get("peakPowerKw")); assertNull(data.get("avgPowerKw"));
    assertEquals(Boolean.FALSE, data.get("loadEvidenceComplete"));
  }

  @Test void intervalCrossingTariffBoundaryIsNotLabeledValley() {
    var plan=plan();plan.setTimeInterval(120);
    var data=EnergyMetrics.summarize(plan,List.of(detail(7,"10")));
    var peaks=(List<Map<String,Object>>)data.get("peakPeriods");
    assertEquals("MIXED",peaks.get(0).get("tariffPeriod"));
    assertNull(peaks.get(0).get("electricityPrice"));
  }

  EnergyPlan plan() { var p=new EnergyPlan(); p.setTimeInterval(15); return p; }
  EnergyPlanDetail detail(int hour, String value) {
    var d=new EnergyPlanDetail(); d.setTimestamp(LocalDateTime.of(2026,11,12,hour,0));
    d.setElectricityConsumption(value == null ? null : new BigDecimal(value)); return d;
  }
}
