package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ReportStatisticMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ReportStatistic;
import com.xq.model.vo.EnergyCarbonReductionVO;
import com.xq.model.vo.ReportEnergyAnalysisVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceImplTest {

    @Test
    void energyAnalysisCarbonReductionAndExportUseReportStatistics() {
        EvaluationMetricMapper evaluationMetricMapper = mock(EvaluationMetricMapper.class);
        ReportStatisticMapper reportStatisticMapper = mock(ReportStatisticMapper.class);
        ReportServiceImpl service = new ReportServiceImpl(evaluationMetricMapper, reportStatisticMapper);

        when(reportStatisticMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                stat("2026-07-01", "100.00", "200.00", "20.00", "1.2500", "50.00"),
                stat("2026-07-02", "140.00", "260.00", "30.00", "1.7500", "70.00")
        ));
        EvaluationMetric metric = new EvaluationMetric();
        metric.setMape(new BigDecimal("2.50"));
        metric.setEcBefore(new BigDecimal("10.00"));
        metric.setEcAfter(new BigDecimal("8.00"));
        metric.setEr(new BigDecimal("98.00"));
        when(evaluationMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(metric);

        PageQueryDTO query = new PageQueryDTO();
        query.setStartDate("2026-07-01");
        query.setEndDate("2026-07-02");

        ReportEnergyAnalysisVO analysis = service.getEnergyAnalysis(query).getData();
        assertEquals(2L, analysis.getStatisticCount());
        assertEquals(new BigDecimal("240.00"), analysis.getTotalEnergyKgce());
        assertEquals(new BigDecimal("460.00"), analysis.getTotalEnergyCost());
        assertEquals(new BigDecimal("50.00"), analysis.getTotalCostSaving());
        assertEquals(new BigDecimal("3.0000"), analysis.getTotalCarbonReduction());
        assertEquals(new BigDecimal("120.00"), analysis.getTotalProductionOutput());
        assertEquals(new BigDecimal("2.0000"), analysis.getEnergyKgcePerTon());

        EnergyCarbonReductionVO carbon = service.getCarbonReduction(query).getData();
        assertEquals(new BigDecimal("3.0000"), carbon.getTotalCarbonReduction());
        assertEquals(new BigDecimal("1.2500"), carbon.getPoints().get(0).getCumulativeCarbonReduction());
        assertEquals(new BigDecimal("3.0000"), carbon.getPoints().get(1).getCumulativeCarbonReduction());

        String csv = new String(service.export("energy-trend", query), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFdate,totalEnergyKgce,energyCost,costSaving,carbonReduction,productionOutput"));
        assertTrue(csv.contains("2026-07-01,100.00,200.00,20.00,1.2500,50.00"));
        assertTrue(csv.contains("2026-07-02,140.00,260.00,30.00,1.7500,70.00"));
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
