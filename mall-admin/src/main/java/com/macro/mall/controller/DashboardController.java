package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ????????????
 */
@Controller
@Tag(name = "DashboardController", description = "????")
@RequestMapping("/dashboard")
public class DashboardController {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "??????")
    @GetMapping("/summary")
    @ResponseBody
    public CommonResult<Map<String, Object>> summary(@RequestParam(value = "startDate", required = false)
                                                     @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                                     @RequestParam(value = "endDate", required = false)
                                                     @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate chartStart = startDate == null ? today.minusDays(6) : startDate;
        LocalDate chartEnd = endDate == null ? today : endDate;
        if (chartStart.isAfter(chartEnd)) {
            LocalDate temp = chartStart;
            chartStart = chartEnd;
            chartEnd = temp;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND DATE(create_time) = ?", today));
        data.put("todaySalesAmount", money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND DATE(COALESCE(payment_time, create_time)) = ?", today));
        data.put("yesterdaySalesAmount", money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND DATE(COALESCE(payment_time, create_time)) = ?", yesterday));

        data.put("pendingPaymentOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 0"));
        data.put("waitingDeliveryOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 1"));
        data.put("deliveredOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 2"));
        data.put("completedOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 3"));
        data.put("waitingConfirmOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 2 AND IFNULL(confirm_status, 0) = 0"));
        data.put("returnApplyCount", count("SELECT COUNT(*) FROM oms_order_return_apply WHERE status = 0"));
        data.put("returnOrderCount", count("SELECT COUNT(*) FROM oms_order_return_apply WHERE status IN (0,1,2)"));
        data.put("lowStockProductCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND IFNULL(stock, 0) <= IFNULL(low_stock, 0)"));
        data.put("expiringAdvertiseCount", safeCount("SELECT COUNT(*) FROM sms_home_advertise WHERE status = 1 AND end_time >= NOW() AND end_time < DATE_ADD(NOW(), INTERVAL 7 DAY)"));

        data.put("unpublishedProductCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND IFNULL(publish_status, 0) = 0"));
        data.put("publishedProductCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND publish_status = 1"));
        data.put("totalProductCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0"));

        LocalDate monthStart = today.withDayOfMonth(1);
        data.put("todayMemberCount", count("SELECT COUNT(*) FROM ums_member WHERE DATE(create_time) = ?", today));
        data.put("yesterdayMemberCount", count("SELECT COUNT(*) FROM ums_member WHERE DATE(create_time) = ?", yesterday));
        data.put("monthMemberCount", count("SELECT COUNT(*) FROM ums_member WHERE create_time >= ? AND create_time < ?", monthStart, today.plusDays(1)));
        data.put("totalMemberCount", count("SELECT COUNT(*) FROM ums_member"));

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekStart = weekStart.minusWeeks(1);
        LocalDate lastWeekEnd = weekStart;
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = monthStart;

        long monthOrderCount = count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= ? AND create_time < ?", monthStart, today.plusDays(1));
        long weekOrderCount = count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= ? AND create_time < ?", weekStart, today.plusDays(1));
        BigDecimal monthSalesAmount = money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND COALESCE(payment_time, create_time) >= ? AND COALESCE(payment_time, create_time) < ?", monthStart, today.plusDays(1));
        BigDecimal weekSalesAmount = money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND COALESCE(payment_time, create_time) >= ? AND COALESCE(payment_time, create_time) < ?", weekStart, today.plusDays(1));
        data.put("monthOrderCount", monthOrderCount);
        data.put("weekOrderCount", weekOrderCount);
        data.put("monthSalesAmount", monthSalesAmount);
        data.put("weekSalesAmount", weekSalesAmount);
        data.put("monthOrderRate", rate(monthOrderCount, count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= ? AND create_time < ?", lastMonthStart, lastMonthEnd)));
        data.put("weekOrderRate", rate(weekOrderCount, count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= ? AND create_time < ?", lastWeekStart, lastWeekEnd)));
        data.put("monthSalesRate", rate(monthSalesAmount, money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND COALESCE(payment_time, create_time) >= ? AND COALESCE(payment_time, create_time) < ?", lastMonthStart, lastMonthEnd)));
        data.put("weekSalesRate", rate(weekSalesAmount, money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND COALESCE(payment_time, create_time) >= ? AND COALESCE(payment_time, create_time) < ?", lastWeekStart, lastWeekEnd)));
        data.put("trend", trend(chartStart, chartEnd));
        return CommonResult.success(data);
    }

    private List<Map<String, Object>> trend(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", cursor.toString());
            item.put("orderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND DATE(create_time) = ?", cursor));
            item.put("orderAmount", money("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND DATE(COALESCE(payment_time, create_time)) = ?", cursor));
            result.add(item);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private long count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.longValue();
    }

    private long safeCount(String sql, Object... args) {
        try {
            return count(sql, args);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long current, long previous) {
        return rate(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private BigDecimal rate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }
}
