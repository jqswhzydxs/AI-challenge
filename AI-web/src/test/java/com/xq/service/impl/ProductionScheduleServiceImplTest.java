package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.EvaluationMetricMapper;
import com.xq.mapper.ProductionLineMapper;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.EvaluationMetric;
import com.xq.model.entity.ProductionLine;
import com.xq.model.entity.ProductionScheduleDetail;
import com.xq.model.entity.ProductionSchedulePlan;
import com.xq.model.vo.ImportPlanResultVO;
import com.xq.model.vo.SchedulePlanVO;
import com.xq.model.vo.TaskVO;
import com.xq.service.PlanAutoGenerationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionScheduleServiceImplTest {

    @Test
    void getPlanDetailReturnsDerivedPreviewFields() {
        AlgorithmTaskMapper algorithmTaskMapper = mock(AlgorithmTaskMapper.class);
        ProductionSchedulePlanMapper schedulePlanMapper = mock(ProductionSchedulePlanMapper.class);
        ProductionScheduleDetailMapper scheduleDetailMapper = mock(ProductionScheduleDetailMapper.class);
        EvaluationMetricMapper evaluationMetricMapper = mock(EvaluationMetricMapper.class);
        ProductionLineMapper productionLineMapper = mock(ProductionLineMapper.class);
        ProductionScheduleServiceImpl service = new ProductionScheduleServiceImpl(
                algorithmTaskMapper,
                schedulePlanMapper,
                scheduleDetailMapper,
                evaluationMetricMapper,
                productionLineMapper,
                mock(ProductionOrderMapper.class)
        );

        ProductionSchedulePlan plan = new ProductionSchedulePlan();
        plan.setId(100L);
        plan.setScheduleName("daily schedule");
        plan.setScheduleDate(LocalDate.of(2026, 7, 17));
        plan.setPlanStartTime(LocalDateTime.of(2026, 7, 17, 0, 0));
        plan.setEcBaseline(new BigDecimal("100.00"));
        plan.setEcOptimized(new BigDecimal("80.00"));
        plan.setTotalProduction(new BigDecimal("180.00"));
        plan.setTotalEnergy(new BigDecimal("14400.00"));
        when(schedulePlanMapper.selectById(100L)).thenReturn(plan);

        ProductionScheduleDetail first = detail(1L, 0, "80.00", "80.00");
        ProductionScheduleDetail second = detail(2L, 1, "100.00", "100.00");
        when(scheduleDetailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        ProductionLine line = new ProductionLine();
        line.setId(10L);
        line.setLineName("热轧产线");
        when(productionLineMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(line);

        SchedulePlanVO data = service.getPlanDetail(100L).getData();

        assertEquals(new BigDecimal("20.00"), data.getEnergySavingsRate());
        assertEquals(new BigDecimal("90.00"), data.getAvgLoadRate());
        assertEquals(new BigDecimal("100.00"), data.getDeadlineCompliance());
        assertEquals(2, data.getDetailCount());
        assertNotNull(data.getDetails());
        assertEquals(10L, data.getDetails().get(0).getLineId());
        assertEquals("热轧产线", data.getDetails().get(0).getLineName());
        assertEquals(new BigDecimal("90.00"), data.getDetails().get(0).getEquipmentLoadRate());
    }

    @Test
    void generateCreatesTaskPlanAndHourlyDetails() {
        AlgorithmTaskMapper algorithmTaskMapper = mock(AlgorithmTaskMapper.class);
        ProductionSchedulePlanMapper schedulePlanMapper = mock(ProductionSchedulePlanMapper.class);
        ProductionScheduleDetailMapper scheduleDetailMapper = mock(ProductionScheduleDetailMapper.class);
        ProductionScheduleServiceImpl service = service(
                algorithmTaskMapper,
                schedulePlanMapper,
                scheduleDetailMapper,
                mock(EvaluationMetricMapper.class),
                mock(ProductionLineMapper.class)
        );

        doAnswer(invocation -> {
            AlgorithmTask task = invocation.getArgument(0);
            task.setId(10L);
            return 1;
        }).when(algorithmTaskMapper).insert(any(AlgorithmTask.class));
        doAnswer(invocation -> {
            ProductionSchedulePlan plan = invocation.getArgument(0);
            plan.setId(20L);
            return 1;
        }).when(schedulePlanMapper).insert(any(ProductionSchedulePlan.class));

        ScheduleGenerateDTO dto = new ScheduleGenerateDTO();
        dto.setScheduleDate("2026-07-17");
        dto.setPlanHorizon(3);
        dto.setObjective("MIN_COST");
        dto.setConstraints(Map.of("elecCoefficient", "45.00"));

        Result<TaskVO> result = service.generate(dto);

        assertEquals(10L, result.getData().getTaskId());
        assertEquals(20L, result.getData().getResultId());
        assertEquals(100, result.getData().getProgress());

        ArgumentCaptor<ProductionSchedulePlan> planCaptor = ArgumentCaptor.forClass(ProductionSchedulePlan.class);
        verify(schedulePlanMapper).insert(planCaptor.capture());
        ProductionSchedulePlan plan = planCaptor.getValue();
        assertEquals(LocalDate.of(2026, 7, 17), plan.getScheduleDate());
        assertEquals(3, plan.getPlanHorizon());
        assertEquals(new BigDecimal("45.00"), plan.getElecCoefficient());
        assertEquals(new BigDecimal("300.00"), plan.getTotalDemand());
        assertEquals(new BigDecimal("13500.0000"), plan.getTotalEnergy());

        ArgumentCaptor<ProductionScheduleDetail> detailCaptor = ArgumentCaptor.forClass(ProductionScheduleDetail.class);
        verify(scheduleDetailMapper, times(3)).insert(detailCaptor.capture());
        assertEquals(List.of(0, 1, 2), detailCaptor.getAllValues().stream().map(ProductionScheduleDetail::getHourIndex).toList());
        assertEquals(new BigDecimal("4500.0000"), detailCaptor.getAllValues().get(0).getElecForecast());
        verify(algorithmTaskMapper, times(2)).updateById(any(AlgorithmTask.class));
    }

    @Test
    void importDailyPlanCreatesPlanDetailsAndMetric() {
        AlgorithmTaskMapper algorithmTaskMapper = mock(AlgorithmTaskMapper.class);
        ProductionSchedulePlanMapper schedulePlanMapper = mock(ProductionSchedulePlanMapper.class);
        ProductionScheduleDetailMapper scheduleDetailMapper = mock(ProductionScheduleDetailMapper.class);
        EvaluationMetricMapper evaluationMetricMapper = mock(EvaluationMetricMapper.class);
        ProductionScheduleServiceImpl service = service(
                algorithmTaskMapper,
                schedulePlanMapper,
                scheduleDetailMapper,
                evaluationMetricMapper,
                mock(ProductionLineMapper.class)
        );
        PlanAutoGenerationService planAutoGenerationService = mock(PlanAutoGenerationService.class);
        service.setPlanAutoGenerationService(planAutoGenerationService);

        doAnswer(invocation -> {
            AlgorithmTask task = invocation.getArgument(0);
            task.setId(30L);
            return 1;
        }).when(algorithmTaskMapper).insert(any(AlgorithmTask.class));
        doAnswer(invocation -> {
            ProductionSchedulePlan plan = invocation.getArgument(0);
            plan.setId(40L);
            return 1;
        }).when(schedulePlanMapper).insert(any(ProductionSchedulePlan.class));

        Result<ImportPlanResultVO> result = service.importDailyPlan(dailyPlan());

        assertEquals(30L, result.getData().getTaskId());
        assertEquals(40L, result.getData().getScheduleId());
        assertEquals(24, result.getData().getDetailCount());

        ArgumentCaptor<ProductionSchedulePlan> planCaptor = ArgumentCaptor.forClass(ProductionSchedulePlan.class);
        verify(schedulePlanMapper).insert(planCaptor.capture());
        ProductionSchedulePlan plan = planCaptor.getValue();
        assertEquals(LocalDate.of(2026, 7, 17), plan.getScheduleDate());
        assertEquals(new BigDecimal("80.00"), plan.getEcBaseline());
        assertEquals(new BigDecimal("64.00"), plan.getEcOptimized());
        assertEquals(new BigDecimal("20.00"), plan.getEcReduction());
        assertEquals(new BigDecimal("1536.00"), plan.getTotalEnergy());

        ArgumentCaptor<ProductionScheduleDetail> detailCaptor = ArgumentCaptor.forClass(ProductionScheduleDetail.class);
        verify(scheduleDetailMapper, times(24)).insert(detailCaptor.capture());
        assertEquals(0, detailCaptor.getAllValues().get(0).getHourIndex());
        assertEquals(0, new BigDecimal("64.00").compareTo(detailCaptor.getAllValues().get(0).getElecForecast()));
        assertEquals(23, detailCaptor.getAllValues().get(23).getHourIndex());

        ArgumentCaptor<EvaluationMetric> metricCaptor = ArgumentCaptor.forClass(EvaluationMetric.class);
        verify(evaluationMetricMapper).insert(metricCaptor.capture());
        assertEquals("SCHEDULE", metricCaptor.getValue().getBizType());
        assertEquals(40L, metricCaptor.getValue().getBizId());
        assertEquals(new BigDecimal("2.50"), metricCaptor.getValue().getMape());
        verify(planAutoGenerationService).autoGenerateAfterScheduleImported(40L);
    }

    @Test
    void importDailyPlanRejectsScheduleWithoutTwentyFourRecords() {
        ProductionScheduleServiceImpl service = service(
                mock(AlgorithmTaskMapper.class),
                mock(ProductionSchedulePlanMapper.class),
                mock(ProductionScheduleDetailMapper.class),
                mock(EvaluationMetricMapper.class),
                mock(ProductionLineMapper.class)
        );
        Map<String, Object> plan = dailyPlan();
        plan.put("schedule", List.of(Map.of("hour", 0, "demand", "1.00", "production", "1.00")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.importDailyPlan(plan));

        assertEquals(400, error.getCode());
    }

    private ProductionScheduleServiceImpl service(AlgorithmTaskMapper algorithmTaskMapper,
                                                  ProductionSchedulePlanMapper schedulePlanMapper,
                                                  ProductionScheduleDetailMapper scheduleDetailMapper,
                                                  EvaluationMetricMapper evaluationMetricMapper,
                                                  ProductionLineMapper productionLineMapper) {
        return new ProductionScheduleServiceImpl(
                algorithmTaskMapper,
                schedulePlanMapper,
                scheduleDetailMapper,
                evaluationMetricMapper,
                productionLineMapper,
                mock(ProductionOrderMapper.class)
        );
    }

    private Map<String, Object> dailyPlan() {
        List<Map<String, Object>> schedule = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            schedule.add(Map.of(
                    "hour", hour,
                    "demand", "1.00",
                    "production", "1.00"
            ));
        }
        Map<String, Object> plan = new java.util.LinkedHashMap<>();
        plan.put("timestamp", "2026-07-17 00:00:00");
        plan.put("plan_horizon", 24);
        plan.put("unit", "hour");
        plan.put("data_granularity", "1 minute");
        plan.put("EC_baseline", "80.00");
        plan.put("EC_optimized", "64.00");
        plan.put("EC_reduction", "20.00");
        plan.put("optimal_temperature", "920.00");
        plan.put("optimal_speed", "1.20");
        plan.put("total_energy", "1536.00");
        plan.put("MAPE", "2.50");
        plan.put("schedule", schedule);
        return plan;
    }

    private ProductionScheduleDetail detail(Long id, int hour, String demand, String production) {
        ProductionScheduleDetail detail = new ProductionScheduleDetail();
        detail.setId(id);
        detail.setScheduleId(100L);
        detail.setHourIndex(hour);
        detail.setDemand(new BigDecimal(demand));
        detail.setProduction(new BigDecimal(production));
        detail.setElecForecast(new BigDecimal(production).multiply(new BigDecimal("80.00")));
        return detail;
    }
}
