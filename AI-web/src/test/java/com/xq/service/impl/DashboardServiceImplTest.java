package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.mapper.ProductionSchedulePlanMapper;
import com.xq.mapper.ReportStatisticMapper;
import com.xq.mapper.WarningRecordMapper;
import com.xq.model.vo.DashboardVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Test
    void overviewReturnsZeroInsteadOfDemoDefaultsWhenDataIsEmpty() {
        ReportStatisticMapper reportStatisticMapper = mock(ReportStatisticMapper.class);
        ProductionOrderMapper productionOrderMapper = mock(ProductionOrderMapper.class);
        ProductionSchedulePlanMapper schedulePlanMapper = mock(ProductionSchedulePlanMapper.class);
        JointOptimizationPlanMapper optimizePlanMapper = mock(JointOptimizationPlanMapper.class);
        WarningRecordMapper warningRecordMapper = mock(WarningRecordMapper.class);
        DashboardServiceImpl service = new DashboardServiceImpl(
                reportStatisticMapper,
                productionOrderMapper,
                schedulePlanMapper,
                optimizePlanMapper,
                warningRecordMapper
        );

        when(reportStatisticMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(productionOrderMapper.selectCount(null)).thenReturn(0L);
        when(schedulePlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(optimizePlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(warningRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        DashboardVO data = service.overview().getData();

        assertEquals(BigDecimal.ZERO, data.getTotalEnergyKgceToday());
        assertEquals(BigDecimal.ZERO, data.getTotalEnergyKgceMonth());
        assertEquals(BigDecimal.ZERO, data.getProductionProgressRate());
        assertEquals(BigDecimal.ZERO, data.getEnergyLoadRate());
        assertEquals(BigDecimal.ZERO, data.getSchemeExecuteRate());
        assertEquals(0, data.getWarningCount());
    }
}
