package com.xq.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CoreApiClient {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final ObjectMapper mapper;
  private final String baseUrl;
  private final String credential;

  public CoreApiClient(
      ObjectMapper mapper,
      @Value("${agent.core-url}") String baseUrl,
      @Value("${agent.service-token:}") String credential) {
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.credential = credential;
  }

  public <T> T post(String path, Object body, String authorization, Class<T> responseType) {
    try {
      var requestBuilder =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .header("X-Service-Token", credential)
              .header("Authorization", authorization == null ? "" : authorization)
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      var attributes =
          org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
      if (attributes
          instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
        String parent = servlet.getRequest().getHeader("traceparent");
        if (parent != null && parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"))
          requestBuilder.header("traceparent", parent);
      }
      HttpRequest request = requestBuilder.build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      // Core's established wrapper encodes business errors in code even when HTTP remains 200.
      var tree = mapper.readTree(response.body());
      int code = tree.has("code") ? tree.get("code").asInt() : response.statusCode();
      if (code == 401 || code == 403 || code == 404 || code == 400)
        throw new ResponseStatusException(HttpStatus.valueOf(code), "CORE_ACCESS_REJECTED");
      if (response.statusCode() != 200 || (tree.has("code") && code != 200))
        throw new IllegalStateException("CORE_UNAVAILABLE");
      return mapper.treeToValue(tree.has("code") ? tree.get("data") : tree, responseType);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CORE_UNAVAILABLE");
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CORE_UNAVAILABLE");
    }
  }
}
