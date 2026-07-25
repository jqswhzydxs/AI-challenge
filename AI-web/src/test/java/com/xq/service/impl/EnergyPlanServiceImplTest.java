package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EnergyEquipmentMapper;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ReportStatisticMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EnergyEquipment;
import com.xq.model.entity.EnergyPlan;
import com.xq.model.entity.EnergyPlanDetail;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ReportStatistic;
import com.xq.model.vo.EnergyAnalysisVO;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.EnergyConsumptionTrendVO;
import com.xq.model.vo.EnergyDeviceStatusVO;
import com.xq.model.vo.EnergyLoadForecastVO;
import com.xq.model.vo.EnergyPlanVO;
import com.xq.model.vo.EnergyTrendVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnergyPlanServiceImplTest {

    @Test
    void getPlanDetailReturnsForecastOptimizationStrategiesAndPricePolicy() {
        AlgorithmTaskMapper algorithmTaskMapper = mock(AlgorithmTaskMapper.class);
        EnergyPlanMapper energyPlanMapper = mock(EnergyPlanMapper.class);
        EnergyPlanDetailMapper energyPlanDetailMapper = mock(EnergyPlanDetailMapper.class);
        EnergyEquipmentMapper energyEquipmentMapper = mock(EnergyEquipmentMapper.class);
        EnergyRealtimeDataMapper energyRealtimeDataMapper = mock(EnergyRealtimeDataMapper.class);
        ProductionSchedulePlanMapper schedulePlanMapper = mock(ProductionSchedulePlanMapper.class);
        ProductionScheduleDetailMapper scheduleDetailMapper = mock(ProductionScheduleDetailMapper.class);
        ReportStatisticMapper reportStatisticMapper = mock(ReportStatisticMapper.class);
        EvaluationMetricMapper evaluationMetricMapper = mock(EvaluationMetricMapper.class);
        EnergyPlanServiceImpl service = new EnergyPlanServiceImpl(
                algorithmTaskMapper,
                energyPlanMapper,
                energyPlanDetailMapper,
                energyEquipmentMapper,
                energyRealtimeDataMapper,
                schedulePlanMapper,
                scheduleDetailMapper,
                reportStatisticMapper,
                evaluationMetricMapper
        );

        EnergyPlan plan = new EnergyPlan();
        plan.setId(200L);
        plan.setTaskId(201L);
        plan.setPlanDate(LocalDate.of(2026, 7, 17));
        plan.setStatus("SUCCESS");
        plan.setElectricPriceMode("PEAK_VALLEY");
        plan.setElectricityCost(new BigDecimal("65.00"));
        plan.setSteamCost(new BigDecimal("15.00"));
        plan.setTotalEnergyCost(new BigDecimal("80.00"));
        when(energyPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        when(energyPlanDetailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                detail(1L, 0, "60.00", "3.00", "21.00"),
                detail(2L, 18, "120.00", "4.00", "126.00"),
                detail(3L, 22, "90.00", "3.50", "58.50")
        ));

        EnergyEquipment equipment = new EnergyEquipment();
        equipment.setId(10L);
        equipment.setEquipmentName("锅炉 A");
        when(energyEquipmentMapper.selectById(10L)).thenReturn(equipment);

        EnergyPlanVO data = service.getPlanDetail("2026-07-17").getData();

        assertEquals(200L, data.getPlanId());
        assertEquals(new BigDecimal("120.00"), data.getLoadForecast().getPeakLoad());
        assertEquals("18:00-19:00", data.getLoadForecast().getPeakHour());
        assertEquals(new BigDecimal("60.00"), data.getLoadForecast().getValleyLoad());
        assertEquals("00:00-01:00", data.getLoadForecast().getValleyHour());
        assertNotNull(data.getOptimizationResult());
        assertEquals(new BigDecimal("80.00"), data.getOptimizationResult().getTotalCost());
        assertFalse(data.getStrategies().isEmpty());
        assertEquals("PEAK_VALLEY", data.getPricePolicy().getMode());
        assertEquals(3, data.getPricePolicy().getPeriods().size());
        assertEquals("锅炉 A", data.getDetails().get(0).getEquipmentName());
    }

    @Test
    void deviceStatusUsesLatestDetailAndWarningLevel() {
        EnergyPlanDetailMapper energyPlanDetailMapper = mock(EnergyPlanDetailMapper.class);
        EnergyEquipmentMapper energyEquipmentMapper = mock(EnergyEquipmentMapper.class);
        EnergyPlanServiceImpl service = service(
                mock(EnergyPlanMapper.class),
                energyPlanDetailMapper,
                energyEquipmentMapper,
                mock(EnergyRealtimeDataMapper.class),
                mock(ReportStatisticMapper.class),
                mock(EvaluationMetricMapper.class)
        );

        EnergyEquipment equipment = new EnergyEquipment();
        equipment.setId(10L);
        equipment.setEquipmentCode("BOILER-1");
        equipment.setEquipmentName("Boiler 1");
        equipment.setEquipmentType("BOILER");
        equipment.setMaxOutput(new BigDecimal("100.00"));
        equipment.setEfficiency(new BigDecimal("92.50"));
        when(energyEquipmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(equipment));

        EnergyPlanDetail latest = new EnergyPlanDetail();
        latest.setEquipmentId(10L);
        latest.setOutput(new BigDecimal("96.00"));
        when(energyPlanDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latest);

        List<EnergyDeviceStatusVO> data = service.getDeviceStatus().getData();

        assertEquals(1, data.size());
        assertEquals("RUNNING", data.get(0).getStatus());
        assertEquals("HIGH", data.get(0).getWarningLevel());
        assertEquals(new BigDecimal("96.00"), data.get(0).getLoadRate());
        assertEquals(new BigDecimal("92.50"), data.get(0).getEfficiency());
    }

    @Test
    void loadForecastReturnsSummaryAndHourlyPoints() {
        EnergyPlanMapper energyPlanMapper = mock(EnergyPlanMapper.class);
        EnergyPlanDetailMapper energyPlanDetailMapper = mock(EnergyPlanDetailMapper.class);
        EnergyPlanServiceImpl service = service(
                energyPlanMapper,
                energyPlanDetailMapper,
                mock(EnergyEquipmentMapper.class),
                mock(EnergyRealtimeDataMapper.class),
                mock(ReportStatisticMapper.class),
                mock(EvaluationMetricMapper.class)
        );

        EnergyPlan plan = new EnergyPlan();
        plan.setId(200L);
        plan.setPlanDate(LocalDate.of(2026, 7, 17));
        when(energyPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);
        when(energyPlanDetailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                detail(1L, 0, "60.00", "3.00", "21.00"),
                detail(2L, 18, "120.00", "4.00", "126.00"),
                detail(3L, 22, "90.00", "3.50", "58.50")
        ));

        EnergyLoadForecastVO data = service.getLoadForecast("2026-07-17").getData();

        assertEquals(3, data.getPoints().size());
        assertEquals(18, data.getPoints().get(1).getHourIndex());
        assertEquals(new BigDecimal("120.00"), data.getSummary().getPeakLoad());
        assertEquals(new BigDecimal("60.00"), data.getSummary().getValleyLoad());
        assertEquals(new BigDecimal("90.00"), data.getSummary().getAvgLoad());
    }

    @Test
    void trendAnalysisAndCarbonReductionAggregateSourceData() {
        EnergyRealtimeDataMapper energyRealtimeDataMapper = mock(EnergyRealtimeDataMapper.class);
        ReportStatisticMapper reportStatisticMapper = mock(ReportStatisticMapper.class);
        EvaluationMetricMapper evaluationMetricMapper = mock(EvaluationMetricMapper.class);
        EnergyPlanServiceImpl service = service(
                mock(EnergyPlanMapper.class),
                mock(EnergyPlanDetailMapper.class),
                mock(EnergyEquipmentMapper.class),
                energyRealtimeDataMapper,
                reportStatisticMapper,
                evaluationMetricMapper
        );

        when(energyRealtimeDataMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                realtime(0, 0, "10.00", "1.00", "0.1000", "0.9200", "0.9700"),
                realtime(0, 30, "15.00", "2.00", "0.1500", "0.9400", "0.9600"),
                realtime(1, 0, "20.00", "3.00", "0.2000", "0.9600", "0.9500")
        ));
        EvaluationMetric metric = new EvaluationMetric();
        metric.setMape(new BigDecimal("2.50"));
        metric.setEcBefore(new BigDecimal("90.00"));
        metric.setEcAfter(new BigDecimal("72.00"));
        metric.setEr(new BigDecimal("98.00"));
        when(evaluationMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(metric);
        when(reportStatisticMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                stat("2026-07-17", "100.00", "200.00", "20.00", "1.2500", "50.00"),
                stat("2026-07-18", "140.00", "260.00", "30.00", "1.7500", "70.00")
        ));

        EnergyConsumptionTrendVO hourly = service.getConsumptionTrend(new PageQueryDTO()).getData();
        assertEquals("HOUR", hourly.getGranularity());
        assertEquals(2, hourly.getPoints().size());
        assertEquals("2026-07-17 00:00", hourly.getPoints().get(0).getTime());
        assertEquals(new BigDecimal("25.00"), hourly.getPoints().get(0).getElectricityConsumption());
        assertEquals(new BigDecimal("548.75"), hourly.getPoints().get(0).getEnergyCost());

        PageQueryDTO dailyQuery = new PageQueryDTO();
        dailyQuery.setInterval(1440);
        EnergyConsumptionTrendVO daily = service.getConsumptionTrend(dailyQuery).getData();
        assertEquals("DAY", daily.getGranularity());
        assertEquals(1, daily.getPoints().size());
        assertEquals(new BigDecimal("45.00"), daily.getPoints().get(0).getElectricityConsumption());

        EnergyAnalysisVO analysis = service.getAnalysis(new PageQueryDTO()).getData();
        assertEquals(3L, analysis.getSampleCount());
        assertEquals(new BigDecimal("45.00"), analysis.getTotalElectricityConsumption());
        assertEquals(new BigDecimal("6.00"), analysis.getTotalSteamConsumption());
        assertEquals(new BigDecimal("0.4500"), analysis.getTotalCarbonEmissionTco2());
        assertEquals(new BigDecimal("1095.75"), analysis.getTotalEnergyCost());
        assertEquals(new BigDecimal("0.9400"), analysis.getAvgLaggingPowerFactor());
        assertEquals(new BigDecimal("2.50"), analysis.getMape());

        EnergyTrendVO trend = service.getTrend(new PageQueryDTO()).getData();
        assertEquals(2, trend.getPoints().size());
        assertEquals(new BigDecimal("140.00"), trend.getPoints().get(1).getTotalEnergyKgce());

        EnergyCarbonReductionVO carbon = service.getCarbonReduction(new PageQueryDTO()).getData();
        assertEquals(new BigDecimal("3.0000"), carbon.getTotalCarbonReduction());
        assertEquals(new BigDecimal("1.2500"), carbon.getPoints().get(0).getCumulativeCarbonReduction());
        assertEquals(new BigDecimal("3.0000"), carbon.getPoints().get(1).getCumulativeCarbonReduction());
    }

    private EnergyPlanServiceImpl service(EnergyPlanMapper energyPlanMapper,
                                          EnergyPlanDetailMapper energyPlanDetailMapper,
                                          EnergyEquipmentMapper energyEquipmentMapper,
                                          EnergyRealtimeDataMapper energyRealtimeDataMapper,
                                          ReportStatisticMapper reportStatisticMapper,
                                          EvaluationMetricMapper evaluationMetricMapper) {
        return new EnergyPlanServiceImpl(
                mock(AlgorithmTaskMapper.class),
                energyPlanMapper,
                energyPlanDetailMapper,
                energyEquipmentMapper,
                energyRealtimeDataMapper,
                mock(ProductionSchedulePlanMapper.class),
                mock(ProductionScheduleDetailMapper.class),
                reportStatisticMapper,
                evaluationMetricMapper
        );
    }

    private EnergyPlanDetail detail(Long id, int hour, String electricity, String steam, String cost) {
        EnergyPlanDetail detail = new EnergyPlanDetail();
        detail.setId(id);
        detail.setPlanId(200L);
        detail.setTimestamp(LocalDateTime.of(2026, 7, 17, hour, 0));
        detail.setEquipmentId(10L);
        detail.setOutput(new BigDecimal("100.00"));
        detail.setElectricityConsumption(new BigDecimal(electricity));
        detail.setSteamConsumption(new BigDecimal(steam));
        detail.setCarbonEmissionTco2(new BigDecimal("0.05"));
        detail.setEnergyCost(new BigDecimal(cost));
        return detail;
    }

    private EnergyRealtimeData realtime(int hour,
                                        int minute,
                                        String electricity,
                                        String steam,
                                        String carbon,
                                        String laggingPowerFactor,
                                        String leadingPowerFactor) {
        EnergyRealtimeData data = new EnergyRealtimeData();
        data.setTimestamp(LocalDateTime.of(2026, 7, 17, hour, minute));
        data.setElectricityConsumption(new BigDecimal(electricity));
        data.setSteamConsumption(new BigDecimal(steam));
        data.setCarbonEmissionTco2(new BigDecimal(carbon));
        data.setLaggingPowerFactor(new BigDecimal(laggingPowerFactor));
        data.setLeadingPowerFactor(new BigDecimal(leadingPowerFactor));
        return data;
    }

    private ReportStatistic stat(String date,
                                 String totalEnergy,
                                 String energyCost,
                                 String costSaving,
                                 String carbonReduction,
                                 String productionOutput) {
        ReportStatistic stat = new ReportStatistic();
        stat.setStatDate(LocalDate.parse(date));
        stat.setStatType("DAY");
        stat.setTotalEnergyKgce(new BigDecimal(totalEnergy));
        stat.setEnergyCost(new BigDecimal(energyCost));
        stat.setCostSaving(new BigDecimal(costSaving));
        stat.setCarbonReduction(new BigDecimal(carbonReduction));
        stat.setProductionOutput(new BigDecimal(productionOutput));
        return stat;
    }
}
