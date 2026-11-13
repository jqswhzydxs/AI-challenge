package com.xq.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.contracts.task.AlgorithmEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DurableOutbox {
  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;
  private final String table;
  private final String routing;
  private final String owner = UUID.randomUUID().toString();

  public DurableOutbox(
      JdbcTemplate jdbc,
      RabbitTemplate rabbit,
      ObjectMapper mapper,
      @Value("${messaging.owner}") String prefix) {
    if (!java.util.Set.of("core", "algorithm").contains(prefix))
      throw new IllegalArgumentException("INVALID_OUTBOX_OWNER");
    this.jdbc = jdbc;
    this.rabbit = rabbit;
    this.mapper = mapper;
    this.table = prefix + "_outbox";
    this.routing = prefix.equals("core") ? "commands" : "results";
  }

  public void enqueue(AlgorithmEvent event) {
    event.validate();
    try {
      jdbc.update(
          "INSERT INTO "
              + table
              + "(event_id,request_id,payload,state,next_send)"
              + " VALUES(?,?,?,'PENDING',UTC_TIMESTAMP(3))",
          event.eventId(),
          event.requestId(),
          mapper.writeValueAsString(event));
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException("INVALID_EVENT", exception);
    }
  }

  @Scheduled(fixedDelayString = "${messaging.flush-ms:1000}")
  public void flush() {
    var rows =
        jdbc.queryForList(
            "SELECT event_id,payload FROM "
                + table
                + " WHERE state<>'SENT' AND next_send<=UTC_TIMESTAMP(3) AND (lease_until IS NULL OR"
                + " lease_until<UTC_TIMESTAMP(3)) ORDER BY created_at LIMIT 20");
    for (var row : rows) {
      String id = row.get("event_id").toString();
      int claimed =
          jdbc.update(
              "UPDATE "
                  + table
                  + " SET owner=?,lease_until=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(3)) WHERE"
                  + " event_id=? AND state<>'SENT' AND (lease_until IS NULL OR"
                  + " lease_until<UTC_TIMESTAMP(3))",
              owner,
              id);
      if (claimed != 1) continue;
      try {
        var properties = new MessageProperties();
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType("application/json");
        properties.setMessageId(id);
        var event = mapper.readValue(row.get("payload").toString(), AlgorithmEvent.class);
        if (event.traceparent() != null) properties.setHeader("traceparent", event.traceparent());
        CorrelationData correlation = new CorrelationData(id);
        rabbit.send(
            MessagingConfiguration.EXCHANGE,
            routing,
            new Message(row.get("payload").toString().getBytes(StandardCharsets.UTF_8), properties),
            correlation);
        var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null)
          throw new IllegalStateException("BROKER_NOT_CONFIRMED");
        jdbc.update(
            "UPDATE "
                + table
                + " SET state='SENT',sent_at=UTC_TIMESTAMP(3),lease_until=NULL WHERE event_id=? AND"
                + " owner=?",
            id,
            owner);
      } catch (Exception exception) {
        jdbc.update(
            "UPDATE "
                + table
                + " SET delivery_attempts=delivery_attempts+1,last_error='PUBLISH_UNCONFIRMED',next_send=TIMESTAMPADD(SECOND,LEAST(60,POW(2,LEAST(delivery_attempts,6))),UTC_TIMESTAMP(3)),lease_until=NULL"
                + " WHERE event_id=? AND owner=?",
            id,
            owner);
        break; // A broker outage must not monopolize the scheduler for every queued event.
      }
    }
  }
}
