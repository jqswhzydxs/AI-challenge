package com.xq.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.contracts.agent.VerifiedIdentity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentAuthenticationFilter extends OncePerRequestFilter {
  private final CoreApiClient core;
  private final ObjectMapper mapper;

  @org.springframework.beans.factory.annotation.Value("${agent.total-timeout-seconds:30}")
  private long timeoutSeconds;

  public AgentAuthenticationFilter(CoreApiClient core, ObjectMapper mapper) {
    this.core = core;
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/agent");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    request.setAttribute("agentDeadline", System.nanoTime() + timeoutSeconds * 1_000_000_000L);
    try {
      VerifiedIdentity identity =
          core.post(
              "/internal/v1/auth/verify",
              Map.of(),
              request.getHeader("Authorization"),
              VerifiedIdentity.class);
      request.setAttribute("verifiedIdentity", identity);
    } catch (ResponseStatusException exception) {
      response.setStatus(exception.getStatusCode().value());
      response.setContentType("application/json;charset=UTF-8");
      mapper.writeValue(
          response.getWriter(),
          Map.of("code", exception.getStatusCode().value(), "message", "身份校验失败，请稍后重试"));
      return;
    }
    chain.doFilter(request, response);
  }
}
