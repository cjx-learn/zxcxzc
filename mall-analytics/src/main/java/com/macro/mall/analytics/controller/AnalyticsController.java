package com.macro.mall.analytics.controller;

import com.macro.mall.analytics.repository.BehaviorEventRepository;
import com.macro.mall.analytics.service.AnalysisJobService;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.domain.UserBehaviorEventDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
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
    public CommonResult<Integer> event(@RequestBody UserBehaviorEventDTO event, HttpServletRequest request) {
        if (event == null || event.getProductId() == null || event.getEventType() == null) {
            return CommonResult.failed("事件参数不完整");
        }
        if (event.getIp() == null) {
            event.setIp(clientIp(request));
        }
        if (event.getUserAgent() == null) {
            event.setUserAgent(request.getHeader("User-Agent"));
        }
        behaviorEventRepository.insert(event);
        behaviorEventRepository.refreshRealtimeProfiles(event);
        return CommonResult.success(1);
    }

    @GetMapping("/rebuild/status")
    public CommonResult<Map<String, Object>> rebuildStatus() {
        return CommonResult.success(analysisJobService.rebuildStatus());
    }

    @PostMapping("/rebuild")
    public CommonResult<Map<String, Object>> rebuild() throws IOException {
        return CommonResult.success(analysisJobService.rebuildProfilesAndRecommendations());
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
