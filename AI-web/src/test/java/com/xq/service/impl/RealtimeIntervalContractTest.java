package com.xq.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xq.mapper.EnergyRealtimeDataMapper;
import com.xq.model.entity.EnergyRealtimeData;
import com.xq.model.dto.PageQueryDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class RealtimeIntervalContractTest {
  @Test void intervalHintDoesNotConvertOrResampleOriginalQuantity() {
    var mapper=mock(EnergyRealtimeDataMapper.class);
    var row=new EnergyRealtimeData();row.setTimestamp(LocalDateTime.of(2026,11,12,0,0));
    row.setElectricityConsumption(BigDecimal.ZERO);row.setSource("VERIFICATION_SYNTHETIC");
    var page=new Page<EnergyRealtimeData>();page.setRecords(new ArrayList<>(List.of(row)));
    when(mapper.selectPage(any(),any())).thenReturn(page);
    var request=new PageQueryDTO();request.setInterval(30);
    var result=new EnergyDataServiceImpl(mapper).getRealtime(request).getData();
    assertEquals("REQUEST_HINT_ONLY_NO_RESAMPLING",result.getIntervalBasis());
    assertEquals(BigDecimal.ZERO,result.getPoints().get(0).getElectricityConsumption());
    assertEquals("VERIFICATION_SYNTHETIC",result.getPoints().get(0).getSource());
    assertEquals(30,result.getTimeInterval());
  }
}
