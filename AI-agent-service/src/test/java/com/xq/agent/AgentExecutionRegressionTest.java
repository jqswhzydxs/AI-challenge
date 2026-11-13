package com.xq.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.xq.contracts.agent.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;

class AgentExecutionRegressionTest {
  @Test
  void deadlineInterruptsWorkAndSaturationDoesNotQueue() throws Exception {
    AgentModelExecutor executor = new AgentModelExecutor(1);
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<?> first =
          caller.submit(
              () ->
                  executor.call(
                      () -> {
                        entered.countDown();
                        release.await();
                        return null;
                      },
                      2000));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertThrows(
          RejectedExecutionException.class, () -> executor.call(() -> "should not run", 1000));
      release.countDown();
      first.get(2, TimeUnit.SECONDS);
      CountDownLatch interrupted = new CountDownLatch(1);
      assertThrows(
          IllegalStateException.class,
          () ->
              executor.call(
                  () -> {
                    try {
                      new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                      interrupted.countDown();
                      throw exception;
                    }
                    return null;
                  },
                  30));
      assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      executor.close();
      caller.shutdownNow();
    }
  }

  @Test
  void modelFailureReusesFrozenEvidenceAndNeverExposesException() {
    Fixture fixture = new Fixture();
    try {
      when(fixture
              .builder
              .build()
              .prompt()
              .system(anyString())
              .user(anyString())
              .call()
              .entity(AgentAdviceVO.class))
          .thenThrow(new IllegalStateException("secret key and SQL"));
      AgentAdviceVO result = fixture.service.chat(new AgentChatDTO()).getData();
      assertSame(fixture.frozen, result);
      assertTrue(result.getModelAttempted());
      assertFalse(result.getAiEnabled());
      assertEquals(List.of("MODEL_CALL_FAILED"), result.getValidationErrors());
      assertEquals("{}", result.getRawAnswer());
      verify(fixture.fallback, times(1)).prepare(eq("GENERAL_CHAT"), any());
      verifyNoInteractions(fixture.tools);
    } finally {
      fixture.close();
    }
  }

  @Test
  void modelCannotOverrideServerMetadata() {
    Fixture fixture = new Fixture();
    try {
      var model =
          AgentAdviceVO.builder()
              .summary("请结合现场约束复核。")
              .riskLevel("LOW")
              .keyMetrics(fixture.frozen.getKeyMetrics())
              .evidence(fixture.frozen.getEvidence())
              .scene("UNTRUSTED")
              .source("FORGED")
              .runId("other-run")
              .sessionId("other-user-session")
              .generatedAt("wrong-date")
              .build();
      when(fixture
              .builder
              .build()
              .prompt()
              .system(anyString())
              .user(anyString())
              .call()
              .entity(AgentAdviceVO.class))
          .thenReturn(model);
      AgentChatDTO request = new AgentChatDTO();
      request.setSessionId("current-session");
      AgentAdviceVO result = fixture.service.chat(request).getData();
      assertEquals("current-run", result.getRunId());
      assertEquals("GENERAL_CHAT", result.getScene());
      assertEquals("SPRING_AI_VALIDATED", result.getSource());
      assertEquals("current-session", result.getSessionId());
      assertEquals(fixture.frozen.getGeneratedAt(), result.getGeneratedAt());
    } finally {
      fixture.close();
    }
  }

  static class Fixture implements AutoCloseable {
    final ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
    final IndustrialAgentTools tools = mock(IndustrialAgentTools.class);
    final AgentFallbackService fallback = mock(AgentFallbackService.class);
    final AgentModelExecutor executor = new AgentModelExecutor(1);
    final AgentAdviceVO frozen =
        AgentAdviceVO.builder()
            .runId("current-run")
            .scene("GENERAL_CHAT")
            .source("RULE_FALLBACK")
            .riskLevel("LOW")
            .analysisStatus("COMPLETE")
            .summary("规则分析已完成。")
            .keyMetrics(List.of("总成本：100 元"))
            .evidence(List.of("callId=current-call"))
            .problems(List.of())
            .suggestions(List.of())
            .nextActions(List.of())
            .missingEvidence(List.of())
            .validationErrors(List.of())
            .rawAnswer("{}")
            .aiEnabled(false)
            .modelAttempted(false)
            .generatedAt("2026-07-17T00:00:00Z")
            .build();
    final AgentServiceImpl service;

    Fixture() {
      ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(builder);
      IndustrialPromptFactory prompt = new IndustrialPromptFactory();
      service =
          new AgentServiceImpl(
              provider,
              tools,
              prompt,
              fallback,
              new AgentResponseValidator(),
              executor,
              mock(AgentRunRepository.class));
      ReflectionTestUtils.setField(service, "llmEnabled", true);
      ReflectionTestUtils.setField(service, "totalTimeoutSeconds", 2L);
      when(fallback.prepare(anyString(), any())).thenReturn(frozen);
      var request = new MockHttpServletRequest();
      request.setAttribute("verifiedIdentity", new VerifiedIdentity("7", "user", List.of("USER")));
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Override
    public void close() {
      executor.close();
      RequestContextHolder.resetRequestAttributes();
    }
  }
}
