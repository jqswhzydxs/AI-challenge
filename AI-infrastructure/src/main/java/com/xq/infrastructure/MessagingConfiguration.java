package com.xq.infrastructure;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class MessagingConfiguration {
  public static final String EXCHANGE = "industrial.algorithm.v1";
  public static final String COMMANDS = "industrial.algorithm.commands.v1";
  public static final String RESULTS = "industrial.algorithm.results.v1";

  @Bean
  Declarables algorithmTopology() {
    var exchange = new DirectExchange(EXCHANGE, true, false);
    var dead = new DirectExchange(EXCHANGE + ".dead", true, false);
    var commands =
        QueueBuilder.durable(COMMANDS)
            .deadLetterExchange(dead.getName())
            .deadLetterRoutingKey("commands")
            .build();
    var results =
        QueueBuilder.durable(RESULTS)
            .deadLetterExchange(dead.getName())
            .deadLetterRoutingKey("results")
            .build();
    var commandDlq = new Queue(COMMANDS + ".dead", true);
    var resultDlq = new Queue(RESULTS + ".dead", true);
    return new Declarables(
        exchange,
        dead,
        commands,
        results,
        commandDlq,
        resultDlq,
        BindingBuilder.bind(commands).to(exchange).with("commands"),
        BindingBuilder.bind(results).to(exchange).with("results"),
        BindingBuilder.bind(commandDlq).to(dead).with("commands"),
        BindingBuilder.bind(resultDlq).to(dead).with("results"));
  }
}
