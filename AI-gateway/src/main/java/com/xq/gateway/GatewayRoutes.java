package com.xq.gateway;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutes {
  // Compose service IPs change on recreation; a long DNS TTL must not delay recovery.
  @Bean
  HttpClientCustomizer serviceDnsCache() {
    return client ->
        client.resolver(
            resolver ->
                resolver
                    .cacheMinTimeToLive(Duration.ZERO)
                    .cacheMaxTimeToLive(Duration.ofSeconds(2))
                    .cacheNegativeTimeToLive(Duration.ofSeconds(1)));
  }

  @Bean
  RouteLocator routes(
      RouteLocatorBuilder builder,
      @Value("${services.core}") String core,
      @Value("${services.agent}") String agent) {
    return builder
        .routes()
        .route("agent", r -> r.path("/api/agent/**").uri(agent))
        .route("core", r -> r.path("/api/**").uri(core))
        .build();
  }

  @Bean
  GlobalFilter identityHeaders() {
    return (exchange, chain) ->
        chain.filter(
            exchange
                .mutate()
                .request(
                    request ->
                        request.headers(
                            headers -> {
                              headers.remove("X-User-Id");
                              headers.remove("X-Username");
                              headers.remove("X-Role");
                              headers.remove("X-Service-Token");
                              headers.remove("X-Agent-User");
                              headers.set("X-Request-Id", UUID.randomUUID().toString());
                              String parent = headers.getFirst("traceparent");
                              if (parent == null
                                  || !parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"))
                                headers.set(
                                    "traceparent",
                                    "00-"
                                        + UUID.randomUUID().toString().replace("-", "")
                                        + "-"
                                        + UUID.randomUUID()
                                            .toString()
                                            .replace("-", "")
                                            .substring(0, 16)
                                        + "-01");
                            }))
                .build());
  }
}
