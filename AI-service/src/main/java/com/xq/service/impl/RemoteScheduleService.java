package com.xq.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.common.constant.*;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.contracts.task.*;
import com.xq.infrastructure.*;
import com.xq.mapper.*;
import com.xq.model.dto.ScheduleGenerateDTO;
import com.xq.model.entity.*;
import com.xq.model.vo.TaskVO;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RemoteScheduleService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RemoteScheduleService.class);
  private final java.time.Clock businessClock;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper;
  private final DurableOutbox outbox;
  private final ArtifactStore artifacts;
  private final AlgorithmTaskMapper tasks;
  private final ProductionOrderMapper orders;
  private final EnergyRealtimeDataMapper energy;
  private final ScheduleResultApplicationService application;

  @Value("${algorithm.execution-mode:LOCAL}")
  private String executionMode;

  @Value("${algorithm.script-file:data/algorithm/generate_plan.py}")
  private String script;

  @Value("${algorithm.training-days:7}")
  private int trainingDays;

  @Value("${algorithm.applications-enabled:true}")
  private boolean applicationsEnabled;

  @Value("${algorithm.max-pending:1000}")
  private int maxPending;

  public RemoteScheduleService(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      ObjectMapper mapper,
      DurableOutbox outbox,
      ArtifactStore artifacts,
      AlgorithmTaskMapper tasks,
      ProductionOrderMapper orders,
      EnergyRealtimeDataMapper energy,
      ScheduleResultApplicationService application,
      java.time.Clock businessClock) {
    this.businessClock = businessClock;
    this.jdbc = jdbc;
    this.tx = tx;
    this.mapper = mapper;
    this.outbox = outbox;
    this.artifacts = artifacts;
    this.tasks = tasks;
    this.orders = orders;
    this.energy = energy;
    this.application = application;
  }

  public boolean useRemote() {
    return "REMOTE".equalsIgnoreCase(executionMode);
  }

  public Result<TaskVO> submit(
      ScheduleGenerateDTO dto, String key, Long userId, String traceparent) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))
      throw new BusinessException(400, "远程任务必须提供有效的 Idempotency-Key");
    LocalDate date;
    try {
      date = LocalDate.parse(dto.getScheduleDate());
    } catch (RuntimeException exception) {
      throw new BusinessException(400, "scheduleDate 必须是有效的 yyyy-MM-dd");
    }
    if (dto.getPlanHorizon() != null && dto.getPlanHorizon() != 24)
      throw new BusinessException(400, "远程日排产固定为 24 小时");
    if (dto.getPlannedQuantity() != null && dto.getPlannedQuantity().signum() <= 0)
      throw new BusinessException(400, "订单产量必须大于零");
    if (dto.getConstraints() != null && !dto.getConstraints().isEmpty())
      throw new BusinessException(400, "当前远程算法不支持自定义约束");
    if (dto.getObjective() != null && !"MIN_COST".equals(dto.getObjective()))
      throw new BusinessException(400, "当前远程算法仅支持固定优化口径");
    if (dto.getPlanUnit() != null && !"hour".equals(dto.getPlanUnit()))
      throw new BusinessException(400, "当前计划单位必须为 hour");
    if (dto.getDataGranularity() != null) throw new BusinessException(400, "数据粒度由冻结采集数据决定，请勿手动指定");
    if (dto.getPlannedQuantity() != null)
      dto.setPlannedQuantity(dto.getPlannedQuantity().stripTrailingZeros());
    dto.setPlanHorizon(24);
    dto.setPlanUnit("hour");
    dto.setObjective("MIN_COST");
    String hash = ArtifactStore.hash(json(dto).getBytes(StandardCharsets.UTF_8));
    return tx.execute(
        status -> {
          jdbc.update(
              "INSERT IGNORE INTO schedule_submission_lock(user_id,idempotency_key) VALUES(?,?)",
              userId.toString(),
              key);
          jdbc.queryForList(
              "SELECT user_id FROM schedule_submission_lock WHERE user_id=? AND idempotency_key=?"
                  + " FOR UPDATE",
              userId.toString(),
              key);
          var existing =
              jdbc.queryForList(
                  "SELECT task_id,request_hash FROM schedule_submission WHERE user_id=? AND"
                      + " idempotency_key=?",
                  userId.toString(),
                  key);
          if (!existing.isEmpty()) {
            if (!hash.equals(existing.get(0).get("request_hash")))
              throw new BusinessException(409, "同一幂等键不能用于不同参数");
            return Result.ok(view(((Number) existing.get(0).get("task_id")).longValue()));
          }
          jdbc.queryForList(
              "SELECT scope FROM task_capacity_lock WHERE scope='remote-schedule' FOR UPDATE");
          Long pending =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM algorithm_task WHERE execution_mode='REMOTE' AND status IN"
                      + " ('PENDING','RUNNING')",
                  Long.class);
          if (pending != null && pending >= maxPending)
            throw new BusinessException(429, "算法任务积压已达上限，请稍后重试");
          String requestId = UUID.randomUUID().toString();
          long taskId = IdWorker.getId();
          if (jdbc.update(
                  "INSERT IGNORE INTO"
                      + " schedule_generation_guard(business_scope,schedule_date,request_id)"
                      + " VALUES('FACTORY',?,?)",
                  date,
                  requestId)
              != 1) throw new BusinessException(409, "该业务日期已有排产请求，请查看原任务");
          Long count =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM production_schedule_plan WHERE schedule_date=? AND"
                      + " deleted=0 AND status<>'FAILED'",
                  Long.class,
                  date);
          if (count != null && count > 0) throw new BusinessException(409, "该日期已有排产方案");
          List<ProductionOrder> inputOrders = ordersFor(date);
          if (inputOrders.isEmpty()) {
            if (dto.getPlannedQuantity() == null)
              throw new BusinessException(400, "该日期没有订单，请填写产量或先导入订单");
            ProductionOrder order = new ProductionOrder();
            order.setId(IdWorker.getId());
            order.setOrderNo("REMOTE-" + requestId);
            order.setProductName(dto.getProductName() == null ? "日排产订单" : dto.getProductName());
            order.setPlannedQuantity(dto.getPlannedQuantity());
            order.setDueTime(date.atTime(23, 59));
            order.setUnit("t");
            order.setPriority(1);
            order.setStatus("PENDING");
            order.setCreateBy(userId);
            orders.insert(order);
          } else if (dto.getPlannedQuantity() != null) {
            var quantity =
                inputOrders.stream()
                    .map(ProductionOrder::getPlannedQuantity)
                    .filter(Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            if (quantity.compareTo(dto.getPlannedQuantity()) != 0)
              throw new BusinessException(409, "输入产量与已有订单合计不一致，请核查订单后提交");
          }
          boolean reserved = date.isAfter(LocalDate.now(businessClock));
          jdbc.update(
              "INSERT INTO"
                  + " algorithm_task(id,task_type,status,progress,message,retry_count,frontend_request_json,create_by,execution_mode,request_id,compute_status,application_status,derivation_status)"
                  + " VALUES(?,'PRODUCTION_SCHEDULE','PENDING',0,?,0,?,?,'REMOTE',?,'WAITING','WAITING','PENDING')",
              taskId,
              reserved ? "已预约，到期后冻结输入并执行" : "请求已持久接管，等待冻结输入",
              json(dto),
              userId,
              requestId);
          jdbc.update(
              "INSERT INTO"
                  + " schedule_submission(user_id,idempotency_key,request_hash,request_id,task_id,request_json,business_date,state)"
                  + " VALUES(?,?,?,?,?,?,?,?)",
              userId.toString(),
              key,
              hash,
              requestId,
              taskId,
              json(
                  Map.of("parameters", dto, "traceparent", traceparent == null ? "" : traceparent)),
              date,
              reserved ? "RESERVED" : "PREPARING");
          log.info(
              "remote request accepted taskId={} requestId={} userId={} businessDate={}",
              taskId,
              requestId,
              userId,
              date);
          return Result.ok("任务已持久接管", view(taskId));
        });
  }

  @Scheduled(fixedDelayString = "${algorithm.prepare-ms:1000}")
  public void prepareInputs() {
    var submissions =
        jdbc.queryForList(
            "SELECT request_id FROM schedule_submission WHERE state IN"
                + " ('RESERVED','PREPARING','PREPARE_RETRY') AND business_date<=? AND"
                + " next_prepare<=UTC_TIMESTAMP(3) ORDER BY business_date LIMIT 5",
            LocalDate.now(businessClock));
    for (var row : submissions) {
      String id = row.get("request_id").toString();
      try {
        tx.executeWithoutResult(status -> prepare(id));
      } catch (RuntimeException exception) {
        tx.executeWithoutResult(
            status -> {
              jdbc.update(
                  "UPDATE schedule_submission SET"
                      + " prepare_attempts=prepare_attempts+1,state=IF(prepare_attempts>=5,'PREPARE_FAILED','PREPARE_RETRY'),next_prepare=TIMESTAMPADD(SECOND,10,UTC_TIMESTAMP(3))"
                      + " WHERE request_id=? AND state IN ('RESERVED','PREPARING','PREPARE_RETRY')",
                  id);
              jdbc.update(
                  "UPDATE algorithm_task t JOIN schedule_submission s ON t.id=s.task_id SET"
                      + " t.status=IF(s.state='PREPARE_FAILED','FAILED','PENDING'),t.message='输入准备失败，可查看任务记录后重试',t.error_message='INPUT_PREPARATION_FAILED'"
                      + " WHERE s.request_id=? AND t.cancel_requested=FALSE",
                  id);
            });
      }
    }
  }

  private void prepare(String id) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM schedule_submission WHERE request_id=? AND state IN"
                + " ('RESERVED','PREPARING','PREPARE_RETRY') FOR UPDATE SKIP LOCKED",
            id);
    if (rows.isEmpty()) return;
    var submission = rows.get(0);
    long taskId = ((Number) submission.get("task_id")).longValue();
    var task = jdbc.queryForMap("SELECT * FROM algorithm_task WHERE id=? FOR UPDATE", taskId);
    if (Boolean.TRUE.equals(task.get("cancel_requested"))) {
      jdbc.update("UPDATE schedule_submission SET state='CANCELED' WHERE request_id=?", id);
      return;
    }
    LocalDate date = ((java.sql.Date) submission.get("business_date")).toLocalDate();
    List<ProductionOrder> inputOrders = ordersFor(date);
    if (inputOrders.isEmpty()) throw new BusinessException(400, "没有可冻结订单");
    LocalDateTime start = date.minusDays(trainingDays).atStartOfDay(), until = date.atStartOfDay();
    List<EnergyRealtimeData> raw =
        energy.selectList(
            new LambdaQueryWrapper<EnergyRealtimeData>()
                .ge(EnergyRealtimeData::getTimestamp, start)
                .lt(EnergyRealtimeData::getTimestamp, until)
                .orderByAsc(EnergyRealtimeData::getTimestamp)
                .orderByAsc(EnergyRealtimeData::getId));
    Map<LocalDateTime, EnergyRealtimeData> unique = new TreeMap<>();
    for (var point : raw)
      if (point.getTimestamp() != null
          && point.getElectricityConsumption() != null
          && point.getSteamConsumption() != null) unique.put(point.getTimestamp(), point);
    if (unique.size() < trainingDays * 96) throw new BusinessException(400, "指定日期之前的历史能源数据不足");
    StringBuilder csv = new StringBuilder("timestamp,elec,steam\n");
    var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    for (var point : unique.values())
      csv.append(point.getTimestamp().format(formatter))
          .append(',')
          .append(point.getElectricityConsumption())
          .append(',')
          .append(point.getSteamConsumption())
          .append('\n');
    StringBuilder orderCsv = new StringBuilder("orderNo,plannedQuantity,dueTime,priority,status\n");
    for (var order : inputOrders)
      orderCsv
          .append(quote(order.getOrderNo()))
          .append(',')
          .append(order.getPlannedQuantity())
          .append(',')
          .append(order.getDueTime().format(formatter))
          .append(',')
          .append(order.getPriority() == null ? 1 : order.getPriority())
          .append(',')
          .append(order.getStatus())
          .append('\n');
    try {
      String prefix = "requests/" + id + "/preparations/" + UUID.randomUUID() + "/";
      String energyRef = prefix + "input.csv",
          ordersRef = prefix + "orders.csv",
          manifestRef = prefix + "input-manifest.json";
      String energyHash =
          artifacts.write(energyRef, csv.toString().getBytes(StandardCharsets.UTF_8));
      String ordersHash =
          artifacts.write(ordersRef, orderCsv.toString().getBytes(StandardCharsets.UTF_8));
      String version = ArtifactStore.hash(Files.readAllBytes(Path.of(script)));
      InputManifest manifest =
          new InputManifest(
              1,
              id,
              date,
              "Asia/Shanghai",
              version,
              energyRef,
              energyHash,
              ordersRef,
              ordersHash,
              start.toString(),
              until.toString(),
              unique.size(),
              inputOrders.stream().map(order -> order.getId().toString()).toList(),
              "COLLECTED_OR_DEMO_SOURCE_RECORDED_IN_CORE");
      String manifestHash = artifacts.write(manifestRef, mapper.writeValueAsBytes(manifest));
      var requestTree = mapper.readTree(submission.get("request_json").toString());
      AlgorithmEvent event =
          new AlgorithmEvent(
              UUID.randomUUID().toString(),
              "AlgorithmRequested.v1",
              1,
              id,
              Long.toString(taskId),
              null,
              null,
              0,
              date,
              manifestRef,
              manifestHash,
              null,
              null,
              version,
              null,
              false,
              requestTree.path("traceparent").asText(),
              Instant.now());
      jdbc.update(
          "UPDATE algorithm_task SET"
              + " input_hash=?,algorithm_version=?,algorithm_request_json=?,compute_status='ACCEPTED',message='输入已冻结，等待"
              + " Worker 接管',training_record_count=? WHERE id=?",
          manifestHash,
          version,
          json(event),
          unique.size(),
          taskId);
      outbox.enqueue(event);
      jdbc.update("UPDATE schedule_submission SET state='DISPATCHED' WHERE request_id=?", id);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("INPUT_PERSISTENCE_FAILED", exception);
    }
  }

  @RabbitListener(queues = MessagingConfiguration.RESULTS)
  public void receive(Message message) {
    AlgorithmEvent event;
    try {
      event = mapper.readValue(message.getBody(), AlgorithmEvent.class);
      event.validate();
    } catch (Exception exception) {
      throw new AmqpRejectAndDontRequeueException("INVALID_RESULT_PROTOCOL", exception);
    }
    try {
      tx.executeWithoutResult(status -> receiveResult(event));
    } catch (DataAccessException exception) {
      throw new ImmediateRequeueAmqpException("DATABASE_UNAVAILABLE", exception);
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException("RESULT_REJECTED", exception);
    }
  }

  private void receiveResult(AlgorithmEvent event) {
    if (jdbc.update(
            "INSERT IGNORE INTO core_inbox(consumer_name,event_id) VALUES('results',?)",
            event.eventId())
        == 0) return;
    var task =
        jdbc.queryForMap(
            "SELECT * FROM algorithm_task WHERE request_id=? FOR UPDATE", event.requestId());
    if (!task.get("id").toString().equals(event.taskId()))
      throw new IllegalArgumentException("TASK_ID_MISMATCH");
    if (event.eventSeq() <= ((Number) task.get("accepted_event_seq")).longValue()) return;
    boolean canceled = Boolean.TRUE.equals(task.get("cancel_requested"));
    if (Set.of("SUCCESS", "FAILED", "CANCELED").contains(task.get("status").toString())) return;
    if ("SUCCEEDED".equals(task.get("compute_status"))
        && !event.eventType().equals("AlgorithmCanceled.v1")) return;
    String taskId = event.taskId();
    log.info(
        "algorithm event received taskId={} requestId={} jobId={} attempt={} eventId={} eventSeq={}"
            + " type={} traceparent={}",
        event.taskId(),
        event.requestId(),
        event.jobId(),
        event.attempt(),
        event.eventId(),
        event.eventSeq(),
        event.eventType(),
        event.traceparent());
    switch (event.eventType()) {
      case "AlgorithmRunning.v1" ->
          jdbc.update(
              "UPDATE algorithm_task SET"
                  + " compute_status='RUNNING',status='RUNNING',progress=20,message='Worker"
                  + " 计算中',accepted_event_seq=? WHERE id=?",
              event.eventSeq(),
              taskId);
      case "AlgorithmSucceeded.v1" -> {
        if (!Objects.equals(task.get("input_hash"), event.inputSha256())
            || !Objects.equals(task.get("algorithm_version"), event.algorithmVersion()))
          throw new IllegalArgumentException("INPUT_OR_VERSION_MISMATCH");
        if (!event
            .outputRef()
            .equals(
                "requests/" + event.requestId() + "/attempts/" + event.attempt() + "/output.json"))
          throw new IllegalArgumentException("OUTPUT_REFERENCE_MISMATCH");
        jdbc.update(
            "INSERT IGNORE INTO algorithm_result_receipt(request_id,task_id,payload,output_hash)"
                + " VALUES(?,?,?,?)",
            event.requestId(),
            taskId,
            json(event),
            event.outputSha256());
        var receipt =
            jdbc.queryForMap(
                "SELECT output_hash FROM algorithm_result_receipt WHERE request_id=?",
                event.requestId());
        if (!Objects.equals(receipt.get("output_hash"), event.outputSha256()))
          throw new IllegalArgumentException("RESULT_HASH_CONFLICT");
        jdbc.update(
            "UPDATE algorithm_task SET"
                + " compute_status='SUCCEEDED',application_status=?,status=?,progress=70,message=?,accepted_event_seq=?"
                + " WHERE id=?",
            canceled ? "CANCELED" : "READY",
            canceled ? "CANCELED" : "RUNNING",
            canceled ? "取消先获准，结果不会导入" : "计算完成，等待业务导入",
            event.eventSeq(),
            taskId);
      }
      case "AlgorithmFailed.v1" ->
          jdbc.update(
              "UPDATE algorithm_task SET"
                  + " compute_status='FAILED',application_status='APPLY_FAILED',status='FAILED',error_message=?,message='计算失败，未生成业务方案',accepted_event_seq=?"
                  + " WHERE id=?",
              event.errorCode(),
              event.eventSeq(),
              taskId);
      case "AlgorithmCanceled.v1" -> {
        if (!canceled) throw new IllegalArgumentException("UNREQUESTED_CANCELLATION");
        jdbc.update(
            "UPDATE algorithm_task SET"
                + " compute_status='CANCELED',application_status='CANCELED',status='CANCELED',message='取消已确认',accepted_event_seq=?"
                + " WHERE id=?",
            event.eventSeq(),
            taskId);
      }
      default -> throw new IllegalArgumentException("INVALID_RESULT_TYPE");
    }
  }

  @Scheduled(fixedDelayString = "${algorithm.apply-ms:1000}")
  public void applyResults() {
    if (!applicationsEnabled) return;
    for (var row :
        jdbc.queryForList(
            "SELECT request_id FROM algorithm_result_receipt WHERE state IN ('READY','APPLY_RETRY')"
                + " AND next_apply<=UTC_TIMESTAMP(3) LIMIT 5")) {
      String id = row.get("request_id").toString();
      try {
        tx.executeWithoutResult(status -> apply(id));
      } catch (RuntimeException exception) {
        tx.executeWithoutResult(
            status -> {
              jdbc.update(
                  "UPDATE algorithm_result_receipt SET"
                      + " apply_attempts=apply_attempts+1,state=IF(apply_attempts>=5,'APPLY_FAILED','APPLY_RETRY'),error_code='RESULT_APPLICATION_FAILED',next_apply=TIMESTAMPADD(SECOND,10,UTC_TIMESTAMP(3))"
                      + " WHERE request_id=? AND state IN ('READY','APPLY_RETRY')",
                  id);
              jdbc.update(
                  "UPDATE algorithm_task t JOIN algorithm_result_receipt r ON t.id=r.task_id SET"
                      + " t.application_status=r.state,t.status=IF(r.state='APPLY_FAILED','FAILED','RUNNING'),t.error_message='RESULT_APPLICATION_FAILED',t.message='计算产物已保存，业务应用待重试'"
                      + " WHERE r.request_id=? AND t.cancel_requested=FALSE",
                  id);
            });
      }
    }
  }

  private void apply(String id) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM algorithm_result_receipt WHERE request_id=? AND state IN"
                + " ('READY','APPLY_RETRY') FOR UPDATE SKIP LOCKED",
            id);
    if (rows.isEmpty()) return;
    var receipt = rows.get(0);
    var task =
        jdbc.queryForMap(
            "SELECT * FROM algorithm_task WHERE id=? FOR UPDATE", receipt.get("task_id"));
    if (Boolean.TRUE.equals(task.get("cancel_requested"))) {
      jdbc.update("UPDATE algorithm_result_receipt SET state='CANCELED' WHERE request_id=?", id);
      return;
    }
    try {
      AlgorithmEvent event =
          mapper.readValue(receipt.get("payload").toString(), AlgorithmEvent.class);
      byte[] bytes = artifacts.verified(event.outputRef(), event.outputSha256());
      var output = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
      var daily = output.getJSONObject("daily_plan");
      if (daily == null
          || !daily.getString("timestamp").startsWith(event.businessDate().toString()))
        throw new IllegalArgumentException("RESULT_DATE_MISMATCH");
      InputManifest manifest =
          mapper.readValue(
              artifacts.verified(event.inputRef(), event.inputSha256()), InputManifest.class);
      List<ProductionOrder> frozenOrders = new ArrayList<>();
      for (String orderId : manifest.orderIds()) {
        var order = orders.selectById(Long.parseLong(orderId));
        if (order == null) throw new IllegalArgumentException("ORDER_NOT_FOUND");
        frozenOrders.add(order);
      }
      AlgorithmTask original = tasks.selectById(((Number) task.get("id")).longValue());
      StringBuilder currentOrders =
          new StringBuilder("orderNo,plannedQuantity,dueTime,priority,status\n");
      var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      for (var order : frozenOrders)
        currentOrders
            .append(quote(order.getOrderNo()))
            .append(',')
            .append(order.getPlannedQuantity())
            .append(',')
            .append(order.getDueTime().format(formatter))
            .append(',')
            .append(order.getPriority() == null ? 1 : order.getPriority())
            .append(',')
            .append(order.getStatus())
            .append('\n');
      if (!ArtifactStore.hash(currentOrders.toString().getBytes(StandardCharsets.UTF_8))
          .equals(manifest.ordersSha256()))
        throw new IllegalArgumentException("ORDER_SNAPSHOT_CHANGED");
      application.applyExisting(
          original, id, daily, frozenOrders, new String(bytes, StandardCharsets.UTF_8));
      jdbc.update(
          "UPDATE algorithm_task SET"
              + " application_status='APPLIED',compute_status='SUCCEEDED',derivation_status='PENDING'"
              + " WHERE id=?",
          original.getId());
      jdbc.update(
          "UPDATE algorithm_result_receipt SET"
              + " state='APPLIED',applied_at=UTC_TIMESTAMP(3),apply_attempts=apply_attempts+1 WHERE"
              + " request_id=?",
          id);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("RESULT_ARTIFACT_UNAVAILABLE", exception);
    }
  }

  public Result<TaskVO> cancel(long taskId, long userId, boolean admin) {
    return tx.execute(
        status -> {
          var submissions =
              jdbc.queryForList(
                  "SELECT * FROM schedule_submission WHERE task_id=? FOR UPDATE", taskId);
          var task = jdbc.queryForMap("SELECT * FROM algorithm_task WHERE id=? FOR UPDATE", taskId);
          authorize(task, userId, admin);
          if (!"REMOTE".equals(task.get("execution_mode")))
            throw new BusinessException(409, "该任务使用本地执行器，不支持远程取消");
          if ("SUCCESS".equals(task.get("status"))) throw new BusinessException(409, "排产已导入，无法取消");
          if (Boolean.TRUE.equals(task.get("cancel_requested"))) return Result.ok(view(taskId));
          if ("FAILED".equals(task.get("status"))) throw new BusinessException(409, "任务已失败");
          var submission = submissions.get(0);
          String id = task.get("request_id").toString();
          boolean notDispatched = !"DISPATCHED".equals(submission.get("state"));
          jdbc.update(
              "UPDATE algorithm_task SET"
                  + " cancel_requested=TRUE,application_status='CANCELED',status=?,message=? WHERE"
                  + " id=?",
              notDispatched ? "CANCELED" : "RUNNING",
              notDispatched ? "预约已取消" : "取消请求已持久化，等待进程退出确认",
              taskId);
          jdbc.update("UPDATE schedule_submission SET state='CANCELED' WHERE request_id=?", id);
          var event =
              new AlgorithmEvent(
                  UUID.randomUUID().toString(),
                  "AlgorithmCancelRequested.v1",
                  1,
                  id,
                  Long.toString(taskId),
                  null,
                  null,
                  0,
                  ((java.sql.Date) submission.get("business_date")).toLocalDate(),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  false,
                  null,
                  Instant.now());
          outbox.enqueue(event);
          return Result.ok(view(taskId));
        });
  }

  public Result<TaskVO> retryApplication(long taskId, long userId, boolean admin) {
    return tx.execute(
        status -> {
          var task = jdbc.queryForMap("SELECT * FROM algorithm_task WHERE id=? FOR UPDATE", taskId);
          authorize(task, userId, admin);
          if (Boolean.TRUE.equals(task.get("cancel_requested")))
            throw new BusinessException(409, "已取消的请求不能重新应用");
          if ("APPLIED".equals(task.get("application_status"))
              && "PARTIAL_FAILED".equals(task.get("derivation_status"))) {
            jdbc.update(
                "UPDATE plan_derivation_job SET"
                    + " state='PARTIAL_FAILED',attempt=0,next_run=UTC_TIMESTAMP(3) WHERE task_id=?",
                taskId);
            return Result.ok(view(taskId));
          }
          if (!"SUCCEEDED".equals(task.get("compute_status"))
              || !"APPLY_FAILED".equals(task.get("application_status")))
            throw new BusinessException(409, "当前任务没有可以重试的应用步骤");
          if (jdbc.update(
                  "UPDATE algorithm_result_receipt SET"
                      + " state='READY',apply_attempts=0,error_code=NULL,next_apply=UTC_TIMESTAMP(3)"
                      + " WHERE task_id=? AND state='APPLY_FAILED'",
                  taskId)
              != 1) throw new BusinessException(409, "没有可恢复的结果收据");
          jdbc.update(
              "UPDATE algorithm_task SET"
                  + " status='RUNNING',application_status='READY',error_message=NULL,message='使用已保存结果重新应用，不重新计算'"
                  + " WHERE id=?",
              taskId);
          return Result.ok(view(taskId));
        });
  }

  public TaskVO authorizedView(long taskId, long userId, boolean admin) {
    var rows = jdbc.queryForList("SELECT * FROM algorithm_task WHERE id=? AND deleted=0", taskId);
    if (rows.isEmpty()) throw new BusinessException(404, "任务不存在");
    authorize(rows.get(0), userId, admin);
    return view(taskId);
  }

  private void authorize(Map<String, Object> task, long userId, boolean admin) {
    if (!admin
        && (task.get("create_by") == null
            || ((Number) task.get("create_by")).longValue() != userId))
      throw new BusinessException(403, "无权访问该任务");
  }

  private TaskVO view(long id) {
    var row = jdbc.queryForMap("SELECT * FROM algorithm_task WHERE id=?", id);
    return TaskVO.builder()
        .taskId(id)
        .taskType(row.get("task_type").toString())
        .status(row.get("status").toString())
        .progress(row.get("progress") == null ? 0 : ((Number) row.get("progress")).intValue())
        .message((String) row.get("message"))
        .resultId(row.get("result_id") == null ? null : ((Number) row.get("result_id")).longValue())
        .errorMessage((String) row.get("error_message"))
        .executionMode((String) row.get("execution_mode"))
        .requestId((String) row.get("request_id"))
        .computeStatus((String) row.get("compute_status"))
        .applicationStatus((String) row.get("application_status"))
        .derivationStatus((String) row.get("derivation_status"))
        .build();
  }

  private List<ProductionOrder> ordersFor(LocalDate date) {
    return orders.selectList(
        new LambdaQueryWrapper<ProductionOrder>()
            .ge(ProductionOrder::getDueTime, date.atStartOfDay())
            .lt(ProductionOrder::getDueTime, date.plusDays(1).atStartOfDay())
            .ne(ProductionOrder::getStatus, "COMPLETED")
            .orderByAsc(ProductionOrder::getDueTime)
            .orderByAsc(ProductionOrder::getId));
  }

  private String quote(String value) {
    return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
  }

  private String json(Object value) {
    try {
      return mapper
          .copy()
          .configure(
              com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
          .writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("INVALID_REQUEST", exception);
    }
  }
}
