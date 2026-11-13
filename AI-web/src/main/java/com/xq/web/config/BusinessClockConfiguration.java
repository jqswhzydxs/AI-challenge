package com.xq.web.config;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class BusinessClockConfiguration {
  @Bean
  Clock businessClock(
      Environment environment, @Value("${verification.business-instant:}") String instant) {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    return environment.matchesProfiles("verification") && !instant.isBlank()
        ? Clock.fixed(Instant.parse(instant), zone)
        : Clock.system(zone);
  }
}
