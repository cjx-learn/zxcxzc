package com.macro.mall.portal.component;

import com.macro.mall.common.domain.UserBehaviorEventDTO;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 将业务动作转换成用户行为事件。
 */
@Component
public class BehaviorEventRecorder {
    private static final String SESSION_HEADER = "X-Session-Id";

    @Autowired
    private BehaviorEventProducer behaviorEventProducer;
    @Autowired
    private UmsMemberService memberService;

    public void record(String eventType, Long productId, Long categoryId, String keyword,
                       String sourcePage, HttpServletRequest request) {
        record(getCurrentMemberId(), eventType, productId, categoryId, keyword, sourcePage, request);
    }

    public void record(Long userId, String eventType, Long productId, Long categoryId, String keyword,
                       String sourcePage, HttpServletRequest request) {
        UserBehaviorEventDTO event = new UserBehaviorEventDTO();
        event.setUserId(userId);
        event.setSessionId(getSessionId(request));
        event.setProductId(productId);
        event.setCategoryId(categoryId);
        event.setEventType(eventType);
        event.setKeyword(keyword);
        event.setSourcePage(sourcePage);
        event.setDeviceType(getDeviceType(request));
        event.setIp(getClientIp(request));
        event.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        event.setEventTime(LocalDateTime.now());
        behaviorEventProducer.send(event);
    }

    private Long getCurrentMemberId() {
        try {
            UmsMember currentMember = memberService.getCurrentMember();
            return currentMember == null ? null : currentMember.getId();
        } catch (Exception e) {
            return null;
        }
    }

    private String getSessionId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String sessionId = request.getHeader(SESSION_HEADER);
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return request.getSession(false) == null ? null : request.getSession(false).getId();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex > -1 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String getDeviceType(HttpServletRequest request) {
        if (request == null || request.getHeader("User-Agent") == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent").toLowerCase();
        return userAgent.contains("mobile") || userAgent.contains("android") || userAgent.contains("iphone") ? "mobile" : "web";
    }
}
