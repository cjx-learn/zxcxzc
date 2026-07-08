package com.macro.mall.analytics.component;

import com.macro.mall.analytics.config.RabbitMqConfig;
import com.macro.mall.analytics.repository.BehaviorEventRepository;
import com.macro.mall.common.domain.UserBehaviorEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BehaviorEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorEventListener.class);

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @RabbitListener(queues = RabbitMqConfig.BEHAVIOR_QUEUE)
    public void handle(UserBehaviorEventDTO event) {
        behaviorEventRepository.insert(event);
        LOGGER.debug("saved behavior event, type:{}, userId:{}, productId:{}", event.getEventType(), event.getUserId(), event.getProductId());
    }
}
