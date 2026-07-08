package com.macro.mall.analytics.controller;

import com.macro.mall.analytics.repository.BehaviorEventRepository;
import com.macro.mall.analytics.service.AnalysisJobService;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.domain.UserBehaviorEventDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private static final Set<String> VALID_EVENT_TYPES = Set.of("view", "search", "fav", "cart", "order", "pay");

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;
    @Autowired
    private AnalysisJobService analysisJobService;

    @GetMapping("/overview")
    public CommonResult<Map<String, Object>> overview(@RequestParam(defaultValue = "7") Integer days,
                                                      @RequestParam(required = false) String eventType,
                                                      @RequestParam(required = false) Long categoryId) {
        return CommonResult.success(behaviorEventRepository.overview(days, eventType, categoryId));
    }

    @GetMapping("/daily-trend")
    public CommonResult<List<Map<String, Object>>> dailyTrend(@RequestParam(defaultValue = "7") Integer days,
                                                              @RequestParam(required = false) String eventType,
                                                              @RequestParam(required = false) Long categoryId) {
        return CommonResult.success(behaviorEventRepository.dailyTrend(days, eventType, categoryId));
    }

    @GetMapping("/funnel")
    public CommonResult<List<Map<String, Object>>> funnel(@RequestParam(defaultValue = "7") Integer days,
                                                          @RequestParam(required = false) String eventType,
                                                          @RequestParam(required = false) Long categoryId) {
        return CommonResult.success(behaviorEventRepository.funnel(days, eventType, categoryId));
    }

    @GetMapping("/hot-products")
    public CommonResult<List<Map<String, Object>>> hotProducts(@RequestParam(defaultValue = "10") Integer limit,
                                                               @RequestParam(required = false) Long categoryId) {
        return CommonResult.success(behaviorEventRepository.hotProducts(limit, categoryId));
    }

    @GetMapping("/products/search")
    public CommonResult<List<Map<String, Object>>> searchProducts(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(behaviorEventRepository.searchProducts(keyword, limit));
    }

    @GetMapping("/users/overview")
    public CommonResult<Map<String, Object>> usersOverview(@RequestParam(defaultValue = "90") Integer days) {
        return CommonResult.success(behaviorEventRepository.usersOverview(days));
    }

    @GetMapping("/users/active")
    public CommonResult<List<Map<String, Object>>> activeUsers(@RequestParam(defaultValue = "90") Integer days,
                                                               @RequestParam(defaultValue = "20") Integer limit) {
        return CommonResult.success(behaviorEventRepository.activeUsers(days, limit));
    }

    @GetMapping("/user-profile/{userId}")
    public CommonResult<Map<String, Object>> userProfile(@PathVariable Long userId,
                                                         @RequestParam(defaultValue = "30") Integer days) {
        return CommonResult.success(behaviorEventRepository.userProfile(userId, days));
    }

    @GetMapping("/product/{productId}")
    public CommonResult<Map<String, Object>> productAnalysis(@PathVariable Long productId,
                                                             @RequestParam(defaultValue = "30") Integer days) {
        return CommonResult.success(behaviorEventRepository.productAnalysis(productId, days));
    }

    @GetMapping("/categories")
    public CommonResult<List<Map<String, Object>>> categories() {
        return CommonResult.success(behaviorEventRepository.categories());
    }

    @PostMapping("/event")
    public CommonResult<Map<String, Object>> collectEvent(@RequestBody UserBehaviorEventDTO event) {
        String error = validateEvent(event);
        if (error != null) {
            return CommonResult.failed(error);
        }
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }
        behaviorEventRepository.insert(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("eventType", event.getEventType());
        result.put("userId", event.getUserId());
        result.put("productId", event.getProductId());
        result.put("eventTime", event.getEventTime());
        return CommonResult.success(result);
    }

    @PostMapping("/rebuild")
    public CommonResult<Map<String, Object>> rebuild() throws IOException {
        return CommonResult.success(analysisJobService.rebuildProfilesAndRecommendations());
    }

    private String validateEvent(UserBehaviorEventDTO event) {
        if (event == null) {
            return "行为事件不能为空";
        }
        if (event.getUserId() == null) {
            return "用户ID不能为空";
        }
        if (!StringUtils.hasText(event.getEventType()) || !VALID_EVENT_TYPES.contains(event.getEventType())) {
            return "行为类型不合法";
        }
        boolean searchEvent = "search".equals(event.getEventType());
        if (!searchEvent && event.getProductId() == null) {
            return "非搜索行为必须包含商品ID";
        }
        if (searchEvent && !StringUtils.hasText(event.getKeyword()) && event.getProductId() == null) {
            return "搜索行为必须包含关键词或商品ID";
        }
        return null;
    }
}
