package com.xq.web.task;

import com.xq.service.RealtimeControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分钟级滚动 MPC 控制任务.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "realtime-mpc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeMpcControlTask {

    private final RealtimeControlService realtimeControlService;

    @Scheduled(fixedDelayString = "${realtime-mpc.interval-ms:60000}", initialDelayString = "${realtime-mpc.initial-delay-ms:12000}")
    public void runRealtimeMpc() {
        try {
            realtimeControlService.runRealtimeMpcTick();
        } catch (RuntimeException e) {
            log.warn("realtime mpc tick skipped: {}", e.getMessage());
        }
    }
}
