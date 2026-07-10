package com.macro.mall.portal.component;

import com.macro.mall.common.domain.UserBehaviorEventDTO;
import com.macro.mall.portal.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户行为消息发送器。
 */
@Component
public class BehaviorEventProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorEventProducer.class);

    @Autowired
    private AmqpTemplate amqpTemplate;

    public void send(UserBehaviorEventDTO event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }
        try {
            amqpTemplate.convertAndSend(RabbitMqConfig.BEHAVIOR_EXCHANGE, RabbitMqConfig.BEHAVIOR_ROUTING_KEY, event);
        } catch (AmqpException e) {
            LOGGER.warn("send behavior event failed, eventType:{}, productId:{}, userId:{}",
                    event.getEventType(), event.getProductId(), event.getUserId(), e);
        }
    }
}
