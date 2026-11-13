package com.xq.agent;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class AgentHttpConfiguration {
  @Bean
  RestClientCustomizer modelHttpTimeouts() {
    return builder -> {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(2));
      factory.setReadTimeout(Duration.ofSeconds(20));
      builder.requestFactory(factory);
    };
  }
}
