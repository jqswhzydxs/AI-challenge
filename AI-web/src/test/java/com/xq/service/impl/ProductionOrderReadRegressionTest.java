package com.xq.service.impl;
import com.xq.mapper.ProductionOrderMapper;
import com.xq.model.dto.PageQueryDTO;
import com.xq.model.entity.ProductionOrder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProductionOrderReadRegressionTest {
 @Test void readingAnOverdueOrderCannotCompleteOrMutateItsInputSnapshot(){
  ProductionOrderMapper mapper=mock(ProductionOrderMapper.class);
  ProductionOrder order=new ProductionOrder();order.setId(7L);order.setDueTime(LocalDateTime.of(2026,1,1,0,0));order.setStatus("PENDING");
  Page<ProductionOrder> page=new Page<>();page.setRecords(List.of(order));page.setTotal(1);
  when(mapper.selectPage(any(Page.class),any())).thenReturn(page);
  var result=new ProductionOrderServiceImpl(mapper).listOrders(new PageQueryDTO());
  assertEquals("PENDING",result.getData().getRecords().get(0).getStatus());
  verify(mapper,never()).updateById(any(ProductionOrder.class));
 }
}