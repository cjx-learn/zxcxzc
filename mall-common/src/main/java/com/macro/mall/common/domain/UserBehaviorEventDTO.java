package com.macro.mall.common.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Behavior event transferred from business services to analytics service.
 */
@Data
public class UserBehaviorEventDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String sessionId;
    private Long productId;
    private Long categoryId;
    private String eventType;
    private String keyword;
    private String sourcePage;
    private String deviceType;
    private String ip;
    private String userAgent;
    private LocalDateTime eventTime;
}
