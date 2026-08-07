package com.xq.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xq.mapper.AlgorithmTaskMapper;
import com.xq.mapper.MpcRealtimeControlMapper;
import com.xq.model.entity.AlgorithmTask;
import com.xq.model.entity.MpcRealtimeControl;
import com.xq.model.vo.RealtimeControlImportResultVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeControlServiceImplTest {

    @Test
    void importRealtimeControlParsesRecordsAndReturnsCompatibleFields() {
        AlgorithmTaskMapper algorithmTaskMapper = mock(AlgorithmTaskMapper.class);
        MpcRealtimeControlMapper controlMapper = mock(MpcRealtimeControlMapper.class);
        RealtimeControlServiceImpl service = new RealtimeControlServiceImpl(algorithmTaskMapper, controlMapper);

        doAnswer(invocation -> {
            AlgorithmTask task = invocation.getArgument(0);
            task.setId(100L);
            return 1;
        }).when(algorithmTaskMapper).insert(any(AlgorithmTask.class));
        doAnswer(invocation -> {
            MpcRealtimeControl control = invocation.getArgument(0);
            control.setId(201L);
            return 1;
        }).when(controlMapper).insert(any(MpcRealtimeControl.class));

        MpcRealtimeControl existing = new MpcRealtimeControl();
        existing.setId(202L);
        when(controlMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, existing);

        Map<String, Object> payload = Map.of("records", List.of(
                Map.of(
                        "timestamp", "08:00:00",
                        "control", Map.of(
                                "boiler_load", "25.5",
                                "turbine_output", "8.2",
                                "grid_purchase", "31.0",
                                "power_factor_target", "0.96"
                        ),
                        "forecast", Map.of(
                                "elec_next_5min", "5.5",
                                "steam_next_5min", "2.2"
                        )
                ),
                Map.of(
                        "timestamp", "2026-07-17 09:00:00",
                        "boilerLoad", "26.5",
                        "turbineOutput", "8.8",
                        "gridPurchase", "28.0",
                        "powerFactorTarget", "0.97",
                        "elecNext5min", "5.8",
                        "steamNext5min", "2.4"
                )
        ));

        RealtimeControlImportResultVO result = service.importRealtimeControl(
                payload,
                "2026-07-17",
                "realtime_control.json"
        ).getData();

        assertTrue(result.getSuccess());
        assertEquals(100L, result.getTaskId());
        assertEquals(202L, result.getLatestControlId());
        assertEquals(202L, result.getControlId());
        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(2, result.getTotalCount());

        ArgumentCaptor<MpcRealtimeControl> insertedCaptor = ArgumentCaptor.forClass(MpcRealtimeControl.class);
        verify(controlMapper).insert(insertedCaptor.capture());
        MpcRealtimeControl inserted = insertedCaptor.getValue();
        assertEquals(LocalDate.of(2026, 7, 17), inserted.getControlDate());
        assertEquals("08:00:00", inserted.getControlTime());
        assertEquals(new BigDecimal("25.5"), inserted.getBoilerLoadMw());
        assertEquals(new BigDecimal("5.5"), inserted.getElecNext5minKwh());

        verify(controlMapper, times(1)).updateById(any(MpcRealtimeControl.class));
        verify(algorithmTaskMapper).updateById(any(AlgorithmTask.class));
    }
}
