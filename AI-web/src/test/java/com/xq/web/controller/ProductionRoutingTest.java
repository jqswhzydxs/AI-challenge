package com.xq.web.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xq.common.result.Result;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.vo.LoginVO;
import com.xq.service.*;
import com.xq.service.impl.RemoteScheduleService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProductionRoutingTest {
  @Test
  void remoteModeKeepsExistingJavaOnlyScheduleEntryLocal() {
    var local = mock(ProductionScheduleService.class);
    var remote = mock(RemoteScheduleService.class);
    when(remote.useRemote()).thenReturn(true);
    var controller =
        new ProductionController(
            mock(ProductionOrderService.class), local, remote, mock(AuthService.class));
    var request = new ScheduleGenerateDTO();
    request.setScheduleDate("2026-07-17");
    request.setPlanHorizon(3);
    controller.generateSchedule(request, null, new MockHttpServletRequest());
    verify(local).generate(request);
    verify(remote, never()).submit(any(), any(), any(), any());
  }

  @Test
  void quantityBasedPythonEntryUsesRemoteSubmission() {
    var local = mock(ProductionScheduleService.class);
    var remote = mock(RemoteScheduleService.class);
    var auth = mock(AuthService.class);
    when(remote.useRemote()).thenReturn(true);
    when(auth.currentUser(any())).thenReturn(Result.ok(LoginVO.builder().userId(7L).build()));
    var controller =
        new ProductionController(mock(ProductionOrderService.class), local, remote, auth);
    var request = new ScheduleGenerateDTO();
    request.setScheduleDate("2026-07-17");
    request.setPlannedQuantity(BigDecimal.TEN);
    controller.generateSchedule(request, "stable-key", new MockHttpServletRequest());
    verify(remote).submit(eq(request), eq("stable-key"), eq(7L), isNull());
    verify(local, never()).generate(any());
  }
}
