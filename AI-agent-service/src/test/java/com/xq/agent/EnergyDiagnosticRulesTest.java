package com.xq.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EnergyDiagnosticRulesTest {
  @Test void valleyConcentrationIsNotADeviceRiskAndSteamIsPrioritized() {
    var data=evidence();
    List<String> metrics=new ArrayList<>(), problems=new ArrayList<>(), missing=new ArrayList<>();
    var result=EnergyDiagnosticRules.analyze(data,metrics,problems,missing);
    assertEquals("UNKNOWN",result.risk());
    assertTrue(problems.stream().anyMatch(s->s.contains("谷价")));
    assertTrue(problems.stream().anyMatch(s->s.contains("蒸汽费用高于电费")));
    assertTrue(metrics.stream().anyMatch(s->s.contains("97.69%")));
    assertFalse(problems.stream().anyMatch(s->s.contains("设备超限风险")));
    assertTrue(missing.stream().anyMatch(s->s.contains("约束不完整")));
    assertTrue(result.suggestions().stream().anyMatch(s->s.contains("交期")));
  }
  @Test void comparableDeviceExceedanceIsHighEvenWithOtherConstraintsUnknown() {
    var data=evidence();
    data.put("equipmentLimits",List.of(Map.of("equipmentId","1","name","锅炉","comparable",true,
        "minOutput",20,"maxOutput",80,"observedMinOutput",30,"observedMaxOutput",90,"outputUnit","MW")));
    assertEquals("HIGH", EnergyDiagnosticRules.analyze(data,new ArrayList<>(),new ArrayList<>(),new ArrayList<>()).risk());
  }
  @Test void incomparableOrMissingLimitNeverBecomesSafe() {
    var data=evidence();
    data.put("equipmentLimits",List.of(Map.of("equipmentId","1","name","设备","comparable",false,"maxOutput",1,"observedMaxOutput",100)));
    var missing=new ArrayList<String>();
    assertEquals("UNKNOWN",EnergyDiagnosticRules.analyze(data,new ArrayList<>(),new ArrayList<>(),missing).risk());
    assertTrue(missing.stream().anyMatch(s->s.contains("单位缺失")));
  }
  @Test void zeroCostIsValidButSharesAreNotInvented() {
    var data=evidence();data.put("totalEnergyCost",0);data.put("electricityCost",0);data.put("steamCost",0);
    List<String> metrics=new ArrayList<>(), missing=new ArrayList<>();
    EnergyDiagnosticRules.analyze(data,metrics,new ArrayList<>(),missing);
    assertTrue(metrics.contains("总能源成本：0 元"));
    assertTrue(metrics.stream().anyMatch(s->s.contains("占比不适用")));
    assertFalse(missing.stream().anyMatch(s->s.contains("总能源成本")));
  }
  @Test void missingLowerLimitDoesNotHideKnownUpperLimitExceedance() {
    var data=evidence();
    data.put("equipmentLimits",List.of(Map.of("equipmentId","1","name","锅炉","comparable",true,
        "maxOutput",80,"observedMaxOutput",90,"outputUnit","MW")));
    var missing=new ArrayList<String>();
    assertEquals("HIGH",EnergyDiagnosticRules.analyze(data,new ArrayList<>(),new ArrayList<>(),missing).risk());
    assertTrue(missing.stream().anyMatch(s->s.contains("部分限值")));
  }
  @Test void plannedDeadlineEvidenceConstrainsLoadShiftingAdvice() {
    var data=evidence();data.put("plannedDeadlineCompliancePct",0);
    var problems=new ArrayList<String>();
    var result=EnergyDiagnosticRules.analyze(data,new ArrayList<>(),problems,new ArrayList<>());
    assertEquals("UNKNOWN",result.risk());
    assertTrue(problems.stream().anyMatch(s->s.contains("计划交期提示")));
    assertTrue(result.suggestions().stream().anyMatch(s->s.contains("先确保交期")));
  }
  Map<String,Object> evidence() {
    return new LinkedHashMap<>(Map.of("totalEnergyCost","2056.20","electricityCost","47.40","steamCost","2008.80",
        "peakPowerKw",40,"avgPowerKw",10,"constraintsComplete",false,
        "peakPeriods",List.of(Map.of("start","01:00","end","02:00","tariffPeriod","VALLEY","electricityPrice","0.35"))));
  }
}
