package com.xq.web.task;

import com.xq.service.EnergyDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后端内置的模拟采集装置.
 * <p>
 * 真实现场可关闭该任务，改由外部采集程序调用 /api/energy/realtime/push。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mock-device", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MockEnergyDeviceTask {

    private final EnergyDataService energyDataService;

    @Value("${mock-device.backfill-days:7}")
    private int backfillDays;

    @Value("${mock-device.backfill-on-startup:true}")
    private boolean backfillOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void backfillHistory() {
        if (!backfillOnStartup) {
            return;
        }
        int inserted = energyDataService.backfillMockRealtimeIfNeeded(backfillDays);
        log.info("mock energy device backfill finished, inserted {} rows", inserted);
    }

    @Scheduled(fixedDelayString = "${mock-device.interval-ms:60000}", initialDelayString = "${mock-device.initial-delay-ms:5000}")
    public void pushRealtimePoint() {
        try {
            energyDataService.pushMockRealtime();
        } catch (RuntimeException e) {
            log.warn("mock energy device push failed: {}", e.getMessage());
        }
    }
}
