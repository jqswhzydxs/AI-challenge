package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.ConstraintConflictMapper;
import com.xq.mapper.EnergyPlanDetailMapper;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.mapper.JointOptimizationTimeseriesMapper;
import com.xq.mapper.ProductionScheduleDetailMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.model.dto.JointOptimizeCompareDTO;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.ConstraintConflict;
import com.xq.model.entity.JointOptimizationPlan;
import com.xq.model.entity.JointOptimizationTimeseries;
import com.xq.model.vo.JointOptimizeCompareVO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.JointParetoFrontierVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JointOptimizationServiceImplTest {

    @Test
    void getEvaluationReturnsPlanMetricsAndDerivedCounts() {
        JointOptimizationPlanMapper optimizePlanMapper = mock(JointOptimizationPlanMapper.class);
        JointOptimizationTimeseriesMapper timeseriesMapper = mock(JointOptimizationTimeseriesMapper.class);
        ConstraintConflictMapper conflictMapper = mock(ConstraintConflictMapper.class);
        JointOptimizationServiceImpl service = service(optimizePlanMapper, timeseriesMapper, conflictMapper);

        when(optimizePlanMapper.selectById(10L)).thenReturn(plan(10L, "18.50", "9.25", "97.00", "2.30", "0.82", "96.50", 1));
        when(conflictMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(timeseriesMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(24L);

        JointOptimizeEvaluationVO data = service.getEvaluation(10L).getData();

        assertEquals(10L, data.getOptimizeId());
        assertTrue(data.getRecommended());
        assertEquals(new BigDecimal("18.50"), data.getCostReductionRate());
        assertEquals(new BigDecimal("9.25"), data.getEnergyReductionRate());
        assertEquals(new BigDecimal("2.30"), data.getMape());
        assertEquals(2, data.getConflictCount());
        assertEquals(24, data.getTimeseriesCount());
    }

    @Test
    void compareMarksBaselineRecommendedAndBestPlans() {
        JointOptimizationPlanMapper optimizePlanMapper = mock(JointOptimizationPlanMapper.class);
        JointOptimizationTimeseriesMapper timeseriesMapper = mock(JointOptimizationTimeseriesMapper.class);
        ConstraintConflictMapper conflictMapper = mock(ConstraintConflictMapper.class);
        JointOptimizationServiceImpl service = service(optimizePlanMapper, timeseriesMapper, conflictMapper);

        when(optimizePlanMapper.selectById(1L)).thenReturn(plan(1L, "10.00", "8.00", "95.00", "3.00", "0.90", "94.00", 0));
        when(optimizePlanMapper.selectById(2L)).thenReturn(plan(2L, "15.00", "7.00", "96.00", "2.50", "0.88", "95.00", 0));
        when(optimizePlanMapper.selectById(3L)).thenReturn(plan(3L, "12.00", "11.00", "97.00", "2.80", "0.86", "96.00", 1));
        when(conflictMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L, 0L, 2L);

        JointOptimizeCompareDTO dto = new JointOptimizeCompareDTO();
        dto.setOptimizeIds(List.of(1L, 2L, 3L));

        JointOptimizeCompareVO data = service.compare(dto).getData();

        assertEquals(1L, data.getBaselineOptimizeId());
        assertEquals(3L, data.getRecommendedOptimizeId());
        assertEquals(2L, data.getBestCostOptimizeId());
        assertEquals(3L, data.getBestEnergyOptimizeId());
        assertEquals(2L, data.getBestMapeOptimizeId());
        assertEquals(3, data.getRecords().size());
        assertTrue(data.getRecords().get(0).getBaseline());
        assertEquals(new BigDecimal("5.00"), data.getRecords().get(1).getCostReductionDelta());
        assertEquals(new BigDecimal("3.00"), data.getRecords().get(2).getEnergyReductionDelta());
        assertEquals(0, data.getRecords().get(1).getConflictCount());
    }

    @Test
    void getParetoFrontierMarksDominatedPoints() {
        JointOptimizationPlanMapper optimizePlanMapper = mock(JointOptimizationPlanMapper.class);
        JointOptimizationTimeseriesMapper timeseriesMapper = mock(JointOptimizationTimeseriesMapper.class);
        ConstraintConflictMapper conflictMapper = mock(ConstraintConflictMapper.class);
        JointOptimizationServiceImpl service = service(optimizePlanMapper, timeseriesMapper, conflictMapper);

        when(optimizePlanMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                plan(1L, "10.00", "8.00", "95.00", "3.00", "0.90", "94.00", 0),
                plan(2L, "15.00", "9.00", "96.00", "2.50", "0.88", "95.00", 1),
                plan(3L, "18.00", "7.00", "95.00", "2.40", "0.86", "96.00", 0)
        ));

        PageQueryDTO query = new PageQueryDTO();
        query.setPageSize(10);

        JointParetoFrontierVO data = service.getParetoFrontier(query).getData();

        assertEquals("costReductionRate", data.getXAxis());
        assertEquals("energyReductionRate", data.getYAxis());
        assertEquals(3, data.getPoints().size());
        assertEquals(2, data.getFrontier().size());
        assertFalse(data.getPoints().get(0).getParetoOptimal());
        assertTrue(data.getPoints().get(1).getParetoOptimal());
        assertTrue(data.getPoints().get(2).getParetoOptimal());
    }

    private JointOptimizationServiceImpl service(JointOptimizationPlanMapper optimizePlanMapper,
                                                 JointOptimizationTimeseriesMapper timeseriesMapper,
                                                 ConstraintConflictMapper conflictMapper) {
        return new JointOptimizationServiceImpl(
                mock(AlgorithmTaskMapper.class),
                optimizePlanMapper,
                timeseriesMapper,
                conflictMapper,
                mock(ProductionSchedulePlanMapper.class),
                mock(ProductionScheduleDetailMapper.class),
                mock(EnergyPlanMapper.class),
                mock(EnergyPlanDetailMapper.class)
        );
    }

    private JointOptimizationPlan plan(Long id,
                                       String costReductionRate,
                                       String energyReductionRate,
                                       String executeRate,
                                       String mape,
                                       String ec,
                                       String er,
                                       Integer recommended) {
        JointOptimizationPlan plan = new JointOptimizationPlan();
        plan.setId(id);
        plan.setTaskId(id + 100);
        plan.setStatus("SUCCESS");
        plan.setCostReductionRate(new BigDecimal(costReductionRate));
        plan.setEnergyReductionRate(new BigDecimal(energyReductionRate));
        plan.setExecuteRate(new BigDecimal(executeRate));
        plan.setMape(new BigDecimal(mape));
        plan.setEc(new BigDecimal(ec));
        plan.setEr(new BigDecimal(er));
        plan.setRecommended(recommended);
        return plan;
    }
}
