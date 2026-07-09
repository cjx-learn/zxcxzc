package com.macro.mall.analytics.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String BEHAVIOR_EXCHANGE = "mall.behavior.exchange";
    public static final String BEHAVIOR_QUEUE = "mall.behavior.queue";
    public static final String BEHAVIOR_ROUTING_KEY = "mall.behavior";

    @Bean
    DirectExchange behaviorDirect() {
        return (DirectExchange) ExchangeBuilder
                .directExchange(BEHAVIOR_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue behaviorQueue() {
        return QueueBuilder
                .durable(BEHAVIOR_QUEUE)
                .build();
    }

    @Bean
    Binding behaviorBinding(DirectExchange behaviorDirect, Queue behaviorQueue) {
        return BindingBuilder
                .bind(behaviorQueue)
                .to(behaviorDirect)
                .with(BEHAVIOR_ROUTING_KEY);
    }
}
