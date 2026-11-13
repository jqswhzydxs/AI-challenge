package com.xq.agent;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** No unbounded waiting: saturated model slots immediately select the frozen rule result. */
@Component
public class AgentModelExecutor {
  private final ThreadPoolExecutor executor;

  public AgentModelExecutor(@Value("${agent.concurrency:4}") int concurrency) {
    if (concurrency < 1 || concurrency > 32)
      throw new IllegalArgumentException("Invalid Agent concurrency");
    executor =
        new ThreadPoolExecutor(
            concurrency,
            concurrency,
            0,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            runnable -> {
              Thread thread = new Thread(runnable, "agent-model");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public <T> T call(Callable<T> work, long timeoutMillis) {
    if (timeoutMillis <= 0) throw new IllegalStateException("AGENT_DEADLINE_EXCEEDED");
    Future<T> future = executor.submit(work);
    try {
      return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AGENT_INTERRUPTED");
    } catch (ExecutionException | TimeoutException exception) {
      throw new IllegalStateException("MODEL_CALL_FAILED");
    } finally {
      if (!future.isDone()) future.cancel(true);
    }
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
