package com.xq.algorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.contracts.task.*;
import com.xq.infrastructure.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ComputeWorker {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ComputeWorker.class);
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final DurableOutbox outbox;
  private final ArtifactStore artifacts;
  private final String owner = UUID.randomUUID().toString();
  private final String node = System.getenv().getOrDefault("HOSTNAME", "local-worker");
  private final ExecutorService pool;
  private final Semaphore slots;
  private final Map<String, Process> processes = new ConcurrentHashMap<>();

  @Value("${algorithm.command:python}")
  private String command;

  @Value("${algorithm.script:data/algorithm/generate_plan.py}")
  private String script;

  @Value("${algorithm.timeout-seconds:180}")
  private int timeout;

  @Value("${algorithm.lease-seconds:30}")
  private int lease;

  @Value("${algorithm.max-attempts:3}")
  private int maxAttempts;

  @org.springframework.beans.factory.annotation.Autowired private org.springframework.core.env.Environment environment;
  @Value("${verification.after-command-commit-delay-ms:0}") private long verificationCommitDelay;
  @Value("${verification.before-terminal-delay-ms:0}") private long verificationTerminalDelay;
  private volatile boolean closing;

  public ComputeWorker(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      TransactionTemplate tx,
      DurableOutbox outbox,
      ArtifactStore artifacts,
      @Value("${algorithm.concurrency:2}") int concurrency) {
    if (concurrency < 1 || concurrency > 16)
      throw new IllegalArgumentException("INVALID_CONCURRENCY");
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.tx = tx;
    this.outbox = outbox;
    this.artifacts = artifacts;
    this.slots = new Semaphore(concurrency);
    this.pool = Executors.newFixedThreadPool(concurrency);
  }

  @RabbitListener(queues = MessagingConfiguration.COMMANDS)
  public void receive(Message message) {
    AlgorithmEvent event;
    try {
      event = mapper.readValue(message.getBody(), AlgorithmEvent.class);
      event.validate();
    } catch (Exception exception) {
      throw new AmqpRejectAndDontRequeueException("INVALID_COMMAND_PROTOCOL", exception);
    }
    try {
      tx.executeWithoutResult(
          status -> {
            if (jdbc.update(
                    "INSERT IGNORE INTO algorithm_inbox(consumer_name,event_id)"
                        + " VALUES('commands',?)",
                    event.eventId())
                == 0) return;
            if (event.eventType().equals("AlgorithmRequested.v1")) accept(event);
            else if (event.eventType().equals("AlgorithmCancelRequested.v1")) cancel(event);
            else throw new IllegalArgumentException("INVALID_COMMAND_TYPE");
          });
      // Verification profile only: lets the fault script kill the consumer after COMMIT and before AUTO ACK.
      if (verificationCommitDelay > 0 && environment.matchesProfiles("verification")) {
        try { Thread.sleep(Math.min(verificationCommitDelay, 30000)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("VERIFICATION_INTERRUPTED"); }
      }
    } catch (DataAccessException exception) {
      throw new ImmediateRequeueAmqpException("DATABASE_UNAVAILABLE", exception);
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException("INVALID_COMMAND", exception);
    }
  }

  private void accept(AlgorithmEvent event) {
    jdbc.update(
        "INSERT IGNORE INTO compute_job(request_id,job_id,task_id,state,request_payload,input_hash)"
            + " VALUES(?,?,?,'ACCEPTED',?,?)",
        event.requestId(),
        UUID.randomUUID().toString(),
        event.taskId(),
        json(event),
        event.inputSha256());
    var job =
        jdbc.queryForMap(
            "SELECT * FROM compute_job WHERE request_id=? FOR UPDATE", event.requestId());
    if (Boolean.TRUE.equals(job.get("cancel_requested"))) return;
    if (!event.inputSha256().equals(job.get("input_hash")))
      throw new IllegalArgumentException("REQUEST_HASH_CONFLICT");
  }

  private void cancel(AlgorithmEvent event) {
    jdbc.update(
        "INSERT IGNORE INTO compute_job(request_id,job_id,task_id,state,cancel_requested)"
            + " VALUES(?,?,?,'CANCELED',TRUE)",
        event.requestId(),
        UUID.randomUUID().toString(),
        event.taskId());
    var job =
        jdbc.queryForMap(
            "SELECT * FROM compute_job WHERE request_id=? FOR UPDATE", event.requestId());
    String state = job.get("state").toString();
    long seq = ((Number) job.get("event_seq")).longValue() + 1;
    boolean active = Set.of("RUNNING", "CANCEL_REQUESTED").contains(state);
    jdbc.update(
        "UPDATE compute_job SET cancel_requested=TRUE,state=?,event_seq=? WHERE request_id=?",
        active ? "CANCEL_REQUESTED" : "CANCELED",
        seq,
        event.requestId());
    if (!active)
      outbox.enqueue(
          event.transition(
              "AlgorithmCanceled.v1",
              job.get("job_id").toString(),
              ((Number) job.get("attempt")).intValue(),
              seq,
              null,
              null,
              null,
              false));
  }

  @Scheduled(fixedDelayString = "${algorithm.poll-ms:500}")
  public void poll() {
    if (closing) return;
    reapLocalAttempts();
    while (slots.tryAcquire()) {
      Map<String, Object> job;
      try {
        job = tx.execute(status -> claim());
      } catch (RuntimeException exception) {
        slots.release();
        return;
      }
      if (job == null) {
        slots.release();
        return;
      }
      try {
        pool.submit(
            () -> {
              try {
                execute(job);
              } finally {
                slots.release();
              }
            });
      } catch (RejectedExecutionException exception) {
        slots.release();
        return;
      }
    }
  }

  private Map<String, Object> claim() {
    var jobs =
        jdbc.queryForList(
            "SELECT * FROM compute_job WHERE ((state IN ('ACCEPTED','RETRY_WAIT') AND"
                + " next_run<=UTC_TIMESTAMP(3)) OR (state IN ('RUNNING','CANCEL_REQUESTED') AND"
                + " lease_until<UTC_TIMESTAMP(3))) ORDER BY created_at LIMIT 1 FOR UPDATE SKIP"
                + " LOCKED");
    if (jobs.isEmpty()) return null;
    var job = jobs.get(0);
    String id = job.get("request_id").toString();
    int oldAttempt = ((Number) job.get("attempt")).intValue();
    if (oldAttempt > 0)
      jdbc.update(
          "UPDATE compute_attempt SET state='LEASE_EXPIRED',error_code='LEASE_EXPIRED' WHERE"
              + " job_id=? AND attempt=? AND state='RUNNING'",
          job.get("job_id"),
          oldAttempt);
    if (Boolean.TRUE.equals(job.get("cancel_requested"))) {
      Long active =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM compute_attempt WHERE job_id=? AND state IN"
                  + " ('RUNNING','LEASE_EXPIRED')",
              Long.class,
              job.get("job_id"));
      if (active != null && active > 0) {
        jdbc.update(
            "UPDATE compute_job SET"
                + " lease_until=TIMESTAMPADD(SECOND,5,UTC_TIMESTAMP(3)),error_code='CANCEL_PROCESS_UNCONFIRMED'"
                + " WHERE request_id=?",
            id);
        return null;
      }
      completeCanceled(job);
      return null;
    }
    if (oldAttempt >= maxAttempts) {
      finish(job, "FAILED", null, null, "ATTEMPTS_EXHAUSTED", false, false);
      return null;
    }
    int attempt = oldAttempt + 1;
    long seq = ((Number) job.get("event_seq")).longValue() + 1;
    jdbc.update(
        "UPDATE compute_job SET"
            + " state='RUNNING',attempt=?,event_seq=?,lease_owner=?,lease_until=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(3))"
            + " WHERE request_id=?",
        attempt,
        seq,
        owner,
        lease,
        id);
    jdbc.update(
        "INSERT INTO compute_attempt(job_id,attempt,owner,node,state,runtime_version)"
            + " VALUES(?,?,?,?,'RUNNING',?)",
        job.get("job_id"),
        attempt,
        owner,
        node,
        "Java "
            + System.getProperty("java.version")
            + "; Python "
            + System.getenv().getOrDefault("PYTHON_VERSION", "unknown"));
    job.put("attempt", attempt);
    job.put("event_seq", seq);
    job.put("lease_owner", owner);
    job.put("state", "RUNNING");
    outbox.enqueue(
        event(job)
            .transition(
                "AlgorithmRunning.v1",
                job.get("job_id").toString(),
                attempt,
                seq,
                null,
                null,
                null,
                false));
    log.info(
        "compute claimed requestId={} taskId={} jobId={} attempt={} owner={} traceparent={}",
        id,
        job.get("task_id"),
        job.get("job_id"),
        attempt,
        owner,
        event(job).traceparent());
    return job;
  }

  private void execute(Map<String, Object> job) {
    Process process = null;
    String requestId = job.get("request_id").toString();
    String outputRef = "requests/" + requestId + "/attempts/" + job.get("attempt") + "/output.json";
    try {
      AlgorithmEvent commandEvent = event(job);
      InputManifest input =
          mapper.readValue(
              artifacts.verified(commandEvent.inputRef(), commandEvent.inputSha256()),
              InputManifest.class);
      if (input.schemaVersion() != 1
          || !requestId.equals(input.requestId())
          || !commandEvent.businessDate().equals(input.businessDate()))
        throw new IllegalArgumentException("INVALID_INPUT_MANIFEST");
      Path scriptPath = Path.of(script).toAbsolutePath().normalize();
      if (!ArtifactStore.hash(Files.readAllBytes(scriptPath)).equals(input.algorithmVersion())
          || !input.algorithmVersion().equals(commandEvent.algorithmVersion()))
        throw new IllegalArgumentException("ALGORITHM_VERSION_MISMATCH");
      byte[] energyBytes = artifacts.verified(input.energyRef(), input.energySha256());
      byte[] orderBytes = artifacts.verified(input.ordersRef(), input.ordersSha256());
      Path output = artifacts.path(outputRef);
      Files.createDirectories(output.getParent());
      Path partial = output.getParent().resolve("output.partial.json");
      Path localEnergy = output.getParent().resolve("input.csv"),
          localOrders = output.getParent().resolve("orders.csv");
      Files.write(localEnergy, energyBytes, StandardOpenOption.CREATE_NEW);
      Files.write(localOrders, orderBytes, StandardOpenOption.CREATE_NEW);
      var builder =
          new ProcessBuilder(
              command,
              scriptPath.toString(),
              localEnergy.toString(),
              partial.toString(),
              localOrders.toString());
      builder.directory(output.getParent().toFile());
      builder.redirectErrorStream(true);
      process = builder.start();
      processes.put(requestId, process);
      jdbc.update(
          "UPDATE compute_attempt SET process_pid=?,process_started=? WHERE job_id=? AND attempt=?"
              + " AND owner=?",
          process.pid(),
          process.info().startInstant().map(Instant::toString).orElse(""),
          job.get("job_id"),
          job.get("attempt"),
          owner);
      Process running = process;
      Thread logs =
          new Thread(
              () -> copyLog(running.getInputStream(), output.getParent().resolve("algorithm.log")),
              "algorithm-log");
      logs.setDaemon(true);
      logs.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
      while (!process.waitFor(1, TimeUnit.SECONDS)) {
        if (System.nanoTime() >= deadline) throw new IOException("COMPUTE_TIMEOUT");
        if (Files.exists(partial) && Files.size(partial) > ArtifactStore.MAX_BYTES)
          throw new IllegalArgumentException("OUTPUT_TOO_LARGE");
        var state =
            jdbc.queryForMap(
                "SELECT lease_owner,attempt,cancel_requested FROM compute_job WHERE request_id=?",
                requestId);
        if (!owner.equals(state.get("lease_owner"))
            || !job.get("attempt").equals(state.get("attempt")))
          throw new IOException("LEASE_LOST");
        if (Boolean.TRUE.equals(state.get("cancel_requested"))) {
          if (!stop(process)) throw new IOException("CANCEL_PROCESS_UNCONFIRMED");
          tx.executeWithoutResult(status -> finish(job, "CANCELED", null, null, null, false, true));
          return;
        }
        if (jdbc.update(
                "UPDATE compute_job SET lease_until=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(3)) WHERE"
                    + " request_id=? AND lease_owner=? AND attempt=? AND"
                    + " lease_until>UTC_TIMESTAMP(3)",
                lease,
                requestId,
                owner,
                job.get("attempt"))
            != 1) throw new IOException("LEASE_LOST");
      }
      logs.join(1000);
      jdbc.update(
          "UPDATE compute_attempt SET exit_code=? WHERE job_id=? AND attempt=?",
          process.exitValue(),
          job.get("job_id"),
          job.get("attempt"));
      if (process.exitValue() != 0) throw new IllegalArgumentException("ALGORITHM_NONZERO_EXIT");
      if (!Files.exists(partial)) throw new IllegalArgumentException("OUTPUT_MISSING");
      if (Files.size(partial) > ArtifactStore.MAX_BYTES)
        throw new IllegalArgumentException("OUTPUT_TOO_LARGE");
      byte[] bytes = Files.readAllBytes(partial);
      var root = mapper.readTree(bytes);
      if (root.has("status") && root.get("status").asText().equalsIgnoreCase("error"))
        throw new IllegalArgumentException("ALGORITHM_REJECTED_INPUT");
      var plan = root.get("daily_plan");
      if (plan == null
          || plan.path("plan_horizon").asInt() != 24
          || plan.path("schedule").size() != 24
          || !plan.path("timestamp").asText().startsWith(input.businessDate().toString()))
        throw new IllegalArgumentException("OUTPUT_SCHEMA_INVALID");
      String hash = artifacts.write(outputRef, bytes);
      // Delay the first attempt's CAS only in the explicit fault profile; attempts after lease recovery remain normal.
      if (verificationTerminalDelay>0 && environment.matchesProfiles("verification") && ((Number)job.get("attempt")).intValue()==1)
        Thread.sleep(Math.min(verificationTerminalDelay,60000));
      tx.executeWithoutResult(
          status -> finish(job, "SUCCEEDED", outputRef, hash, null, false, true));
    } catch (Exception exception) {
      boolean stopped = process == null || stop(process);
      if (stopped)
        jdbc.update(
            "UPDATE compute_attempt SET state='STOPPED',completed_at=UTC_TIMESTAMP(3) WHERE"
                + " job_id=? AND attempt=? AND owner=?",
            job.get("job_id"),
            job.get("attempt"),
            owner);
      String message = Objects.toString(exception.getMessage(), "");
      String code =
          Set.of(
                      "COMPUTE_TIMEOUT",
                      "LEASE_LOST",
                      "CANCEL_PROCESS_UNCONFIRMED",
                      "OUTPUT_MISSING",
                      "OUTPUT_TOO_LARGE",
                      "ALGORITHM_VERSION_MISMATCH",
                      "ALGORITHM_NONZERO_EXIT",
                      "ALGORITHM_REJECTED_INPUT",
                      "OUTPUT_SCHEMA_INVALID",
                      "INVALID_INPUT_MANIFEST",
                      "ARTIFACT_HASH_MISMATCH",
                      "ARTIFACT_TOO_LARGE")
                  .contains(message)
              ? message
              : exception instanceof IllegalArgumentException
                  ? "INVALID_COMPUTE_INPUT"
                  : "COMPUTE_INTERRUPTED_OR_IO";
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      boolean retry = !(exception instanceof IllegalArgumentException) && stopped;
      try {
        tx.executeWithoutResult(status -> finish(job, "FAILED", null, null, code, retry, true));
      } catch (RuntimeException failure) {
        /* Lease recovery owns the durable attempt after a DB outage. */
      }
    } finally {
      processes.remove(requestId);
    }
  }

  private void finish(
      Map<String, Object> claimed,
      String target,
      String output,
      String hash,
      String error,
      boolean retry,
      boolean fence) {
    String requestId = claimed.get("request_id").toString();
    var current =
        jdbc.queryForMap("SELECT * FROM compute_job WHERE request_id=? FOR UPDATE", requestId);
    int attempt = ((Number) claimed.get("attempt")).intValue();
    if (fence
        && (!owner.equals(current.get("lease_owner"))
            || attempt != ((Number) current.get("attempt")).intValue())) return;
    if (fence) {
      Integer valid =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM compute_job WHERE request_id=? AND"
                  + " lease_until>UTC_TIMESTAMP(3)",
              Integer.class,
              requestId);
      if (valid == null || valid == 0) return;
    }
    if (Boolean.TRUE.equals(current.get("cancel_requested"))) {
      Long active =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM compute_attempt WHERE job_id=? AND attempt<>? AND state IN"
                  + " ('RUNNING','LEASE_EXPIRED')",
              Long.class,
              current.get("job_id"),
              attempt);
      if (active != null && active > 0) {
        jdbc.update(
            "UPDATE compute_job SET"
                + " state='CANCEL_REQUESTED',lease_until=UTC_TIMESTAMP(3),error_code='CANCEL_PROCESS_UNCONFIRMED'"
                + " WHERE request_id=?",
            requestId);
        jdbc.update(
            "UPDATE compute_attempt SET state='STOPPED' WHERE job_id=? AND attempt=?",
            current.get("job_id"),
            attempt);
        return;
      }
      target = "CANCELED";
      retry = false;
    }
    if (retry && attempt < maxAttempts) {
      jdbc.update(
          "UPDATE compute_job SET"
              + " state='RETRY_WAIT',error_code=?,next_run=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(3)),lease_until=NULL"
              + " WHERE request_id=?",
          error,
          Math.min(30, attempt * 3),
          requestId);
      jdbc.update(
          "UPDATE compute_attempt SET state='FAILED',error_code=?,completed_at=UTC_TIMESTAMP(3)"
              + " WHERE job_id=? AND attempt=?",
          error,
          current.get("job_id"),
          attempt);
      return;
    }
    long seq = ((Number) current.get("event_seq")).longValue() + 1;
    jdbc.update(
        "UPDATE compute_job SET"
            + " state=?,event_seq=?,output_ref=?,output_hash=?,error_code=?,lease_until=NULL WHERE"
            + " request_id=?",
        target,
        seq,
        output,
        hash,
        error,
        requestId);
    jdbc.update(
        "UPDATE compute_attempt SET state=?,error_code=?,output_ref=?,completed_at=UTC_TIMESTAMP(3)"
            + " WHERE job_id=? AND attempt=?",
        target,
        error,
        output,
        current.get("job_id"),
        attempt);
    String type =
        switch (target) {
          case "SUCCEEDED" -> "AlgorithmSucceeded.v1";
          case "CANCELED" -> "AlgorithmCanceled.v1";
          default -> "AlgorithmFailed.v1";
        };
    log.info(
        "compute terminal requestId={} taskId={} jobId={} attempt={} eventSeq={} state={}"
            + " errorCode={} traceparent={}",
        requestId,
        current.get("task_id"),
        current.get("job_id"),
        attempt,
        seq,
        target,
        error,
        event(current).traceparent());
    outbox.enqueue(
        event(current)
            .transition(
                type, current.get("job_id").toString(), attempt, seq, output, hash, error, false));
  }

  private void completeCanceled(Map<String, Object> job) {
    long seq = ((Number) job.get("event_seq")).longValue() + 1;
    jdbc.update(
        "UPDATE compute_job SET state='CANCELED',event_seq=?,lease_until=NULL WHERE request_id=?",
        seq,
        job.get("request_id"));
    outbox.enqueue(
        event(job)
            .transition(
                "AlgorithmCanceled.v1",
                job.get("job_id").toString(),
                ((Number) job.get("attempt")).intValue(),
                seq,
                null,
                null,
                null,
                false));
  }

  private void reapLocalAttempts() {
    var rows =
        jdbc.queryForList(
            "SELECT a.* FROM compute_attempt a JOIN compute_job j ON j.job_id=a.job_id WHERE"
                + " a.node=? AND a.state IN ('RUNNING','LEASE_EXPIRED') AND (j.lease_until IS NULL"
                + " OR j.lease_until<UTC_TIMESTAMP(3))",
            node);
    for (var row : rows) {
      Number pid = (Number) row.get("process_pid");
      boolean alive =
          pid != null
              && ProcessHandle.of(pid.longValue())
                  .filter(ProcessHandle::isAlive)
                  .filter(
                      handle ->
                          handle
                              .info()
                              .startInstant()
                              .map(Instant::toString)
                              .orElse("")
                              .equals(row.get("process_started")))
                  .isPresent();
      if (!alive)
        jdbc.update(
            "UPDATE compute_attempt SET state='STOPPED',completed_at=UTC_TIMESTAMP(3) WHERE"
                + " job_id=? AND attempt=? AND node=? AND state IN ('RUNNING','LEASE_EXPIRED')",
            row.get("job_id"),
            row.get("attempt"),
            node);
    }
  }

  private boolean stop(Process process) {
    var children = process.descendants().toList();
    children.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
      children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
      process.waitFor(5, TimeUnit.SECONDS);
      return !process.isAlive() && children.stream().noneMatch(ProcessHandle::isAlive);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void copyLog(InputStream input, Path path) {
    try (input;
        var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
      byte[] buffer = new byte[4096];
      int count;
      long written = 0;
      while ((count = input.read(buffer)) != -1) {
        int keep = (int) Math.min(count, Math.max(0, 1024 * 1024 - written));
        if (keep > 0) output.write(buffer, 0, keep);
        written += keep;
      }
    } catch (IOException exception) {
      /* bounded diagnostic log is not a result */
    }
  }

  private AlgorithmEvent event(Map<String, Object> job) {
    try {
      return mapper.readValue(job.get("request_payload").toString(), AlgorithmEvent.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("INVALID_DURABLE_REQUEST", exception);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException(exception);
    }
  }

  @PreDestroy
  public void close() {
    closing = true;
    processes.values().forEach(this::stop);
    pool.shutdownNow();
  }
}
