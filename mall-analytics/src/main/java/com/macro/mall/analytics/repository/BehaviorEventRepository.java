package com.macro.mall.analytics.repository;

import com.macro.mall.common.domain.UserBehaviorEventDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BehaviorEventRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insert(UserBehaviorEventDTO event) {
        LocalDateTime eventTime = event.getEventTime() == null ? LocalDateTime.now() : event.getEventTime();
        jdbcTemplate.update("""
                        INSERT INTO user_behavior_event
                        (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.getUserId(), event.getSessionId(), event.getProductId(), event.getCategoryId(), event.getEventType(),
                event.getKeyword(), event.getSourcePage(), event.getDeviceType(), event.getIp(), event.getUserAgent(), Timestamp.valueOf(eventTime));
    }

    public Map<String, Object> overview(int days, String eventType, Long categoryId) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  COUNT(*) AS eventCount,
                  COUNT(DISTINCT user_id) AS userCount,
                  COUNT(DISTINCT session_id) AS sessionCount,
                  COUNT(DISTINCT product_id) AS productCount,
                  IFNULL(SUM(event_type = 'view'), 0) AS viewCount,
                  IFNULL(SUM(event_type = 'search'), 0) AS searchCount,
                  IFNULL(SUM(event_type = 'fav'), 0) AS favCount,
                  IFNULL(SUM(event_type = 'cart'), 0) AS cartCount,
                  IFNULL(SUM(event_type = 'order'), 0) AS orderCount,
                  IFNULL(SUM(event_type = 'pay'), 0) AS payCount
                FROM user_behavior_event
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        addFilters(sql, args, days, eventType, categoryId);
        return jdbcTemplate.queryForMap(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> dailyTrend(int days, String eventType, Long categoryId) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_date AS eventDate, event_type AS eventType, COUNT(*) AS eventCount
                FROM user_behavior_event
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        addFilters(sql, args, days, eventType, categoryId);
        sql.append("""
                GROUP BY event_date, event_type
                ORDER BY event_date, event_type
                """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> funnel(int days, String eventType, Long categoryId) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_type AS eventType, COUNT(*) AS eventCount, COUNT(DISTINCT user_id) AS userCount
                FROM user_behavior_event
                WHERE event_type IN ('view','fav','cart','order','pay')
                """);
        List<Object> args = new ArrayList<>();
        addFilters(sql, args, days, eventType, categoryId);
        sql.append("""
                GROUP BY event_type
                ORDER BY FIELD(event_type, 'view','fav','cart','order','pay')
                """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> hotProducts(int limit, Long categoryId) {
        StringBuilder sql = new StringBuilder("""
                SELECT pp.product_id AS productId, p.name AS productName, p.pic AS productPic,
                       pp.category_id AS categoryId, c.name AS categoryName,
                       pp.view_count AS viewCount, pp.cart_count AS cartCount, pp.order_count AS orderCount,
                       pp.pay_count AS payCount, pp.hot_score AS hotScore
                FROM product_profile pp
                LEFT JOIN pms_product p ON pp.product_id = p.id
                LEFT JOIN pms_product_category c ON pp.category_id = c.id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND pp.category_id = ?\n");
            args.add(categoryId);
        }
        sql.append("""
                ORDER BY pp.hot_score DESC, pp.view_count DESC
                LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> userProfile(Long userId, int days) {
        List<Map<String, Object>> profiles = jdbcTemplate.queryForList("""
                SELECT up.user_id AS userId, m.username AS username,
                       up.view_count AS viewCount, up.search_count AS searchCount, up.fav_count AS favCount,
                       up.cart_count AS cartCount, up.order_count AS orderCount, up.pay_count AS payCount,
                       up.active_days AS activeDays, up.favorite_category_id AS favoriteCategoryId,
                       c.name AS favoriteCategoryName, up.favorite_category_score AS favoriteCategoryScore,
                       up.last_active_time AS lastActiveTime, up.user_level AS userLevel, up.update_time AS updateTime
                FROM user_profile up
                LEFT JOIN ums_member m ON up.user_id = m.id
                LEFT JOIN pms_product_category c ON up.favorite_category_id = c.id
                WHERE up.user_id = ?
                """, userId);
        Map<String, Object> result = profiles.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(profiles.get(0));
        result.put("eventSummary", userEventSummary(userId, days));
        result.put("recentEvents", recentUserEvents(userId, 10));
        return result;
    }

    public Map<String, Object> productAnalysis(Long productId, int days) {
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.id AS productId, p.name AS productName, p.pic AS productPic, p.price AS productPrice,
                       p.product_category_id AS categoryId, c.name AS categoryName,
                       IFNULL(pp.view_count, 0) AS viewCount, IFNULL(pp.search_count, 0) AS searchCount,
                       IFNULL(pp.fav_count, 0) AS favCount, IFNULL(pp.cart_count, 0) AS cartCount,
                       IFNULL(pp.order_count, 0) AS orderCount, IFNULL(pp.pay_count, 0) AS payCount,
                       IFNULL(pp.hot_score, 0) AS hotScore, pp.cart_rate AS cartRate,
                       pp.order_rate AS orderRate, pp.pay_rate AS payRate, pp.update_time AS updateTime
                FROM pms_product p
                LEFT JOIN product_profile pp ON p.id = pp.product_id
                LEFT JOIN pms_product_category c ON p.product_category_id = c.id
                WHERE p.id = ?
                """, productId);
        Map<String, Object> result = products.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(products.get(0));
        result.put("eventSummary", productEventSummary(productId, days));
        result.put("recentEvents", recentProductEvents(productId, 10));
        return result;
    }

    public List<Map<String, Object>> searchProducts(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        StringBuilder sql = new StringBuilder("""
                SELECT p.id AS productId, p.name AS productName, p.pic AS productPic,
                       p.price AS productPrice, p.product_sn AS productSn,
                       p.product_category_id AS categoryId, c.name AS categoryName,
                       IFNULL(pp.hot_score, 0) AS hotScore,
                       IFNULL(pp.view_count, 0) AS viewCount,
                       IFNULL(pp.cart_count, 0) AS cartCount,
                       IFNULL(pp.order_count, 0) AS orderCount,
                       IFNULL(pp.pay_count, 0) AS payCount
                FROM pms_product p
                LEFT JOIN product_profile pp ON p.id = pp.product_id
                LEFT JOIN pms_product_category c ON p.product_category_id = c.id
                WHERE IFNULL(p.delete_status, 0) = 0
                """);
        List<Object> args = new ArrayList<>();
        Long exactId = parseLong(keyword);
        if (StringUtils.hasText(keyword)) {
            String likeKeyword = "%" + keyword.trim() + "%";
            sql.append("""
                    AND (
                      p.id = ?
                      OR p.name LIKE ?
                      OR p.sub_title LIKE ?
                      OR p.keywords LIKE ?
                      OR p.product_sn LIKE ?
                    )
                    """);
            args.add(exactId == null ? -1L : exactId);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        sql.append("""
                ORDER BY
                  CASE WHEN p.id = ? THEN 0 ELSE 1 END,
                  IFNULL(pp.hot_score, 0) DESC,
                  IFNULL(pp.view_count, 0) DESC,
                  p.sort DESC,
                  p.id DESC
                LIMIT ?
                """);
        args.add(exactId == null ? -1L : exactId);
        args.add(safeLimit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> usersOverview(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*) AS profileUserCount,
                  SUM(user_level = '高价值用户') AS highValueUserCount,
                  SUM(user_level = '潜在购买用户') AS potentialUserCount,
                  SUM(user_level = '普通用户') AS normalUserCount,
                  SUM(user_level = '低活跃用户') AS lowActiveUserCount,
                  IFNULL(ROUND(AVG(active_days), 2), 0) AS avgActiveDays
                FROM user_profile
                """));
        result.put("levelDistribution", jdbcTemplate.queryForList("""
                SELECT user_level AS userLevel, COUNT(*) AS userCount
                FROM user_profile
                GROUP BY user_level
                ORDER BY FIELD(user_level, '高价值用户', '潜在购买用户', '普通用户', '低活跃用户')
                """));
        result.put("favoriteCategoryDistribution", jdbcTemplate.queryForList("""
                SELECT up.favorite_category_id AS categoryId, IFNULL(c.name, '未识别') AS categoryName, COUNT(*) AS userCount
                FROM user_profile up
                LEFT JOIN pms_product_category c ON up.favorite_category_id = c.id
                GROUP BY up.favorite_category_id, c.name
                ORDER BY userCount DESC
                LIMIT 10
                """));
        result.put("activeUsers", activeUsers(days, 10));
        return result;
    }

    public List<Map<String, Object>> activeUsers(int days, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT
                  ranked.userId,
                  ranked.username,
                  ranked.userLevel,
                  ranked.favoriteCategoryId,
                  ranked.favoriteCategoryName,
                  ranked.lastActiveTime,
                  ranked.activeDays,
                  ranked.behaviorScore,
                  ranked.payAmount,
                  ranked.recencyScore,
                  ranked.frequencyScore,
                  ranked.monetaryScore,
                  ranked.rfmScore,
                  CASE
                    WHEN ranked.rfmScore >= 80 THEN '高价值活跃用户'
                    WHEN ranked.rfmScore >= 60 THEN '活跃用户'
                    WHEN ranked.rfmScore >= 40 THEN '一般活跃用户'
                    ELSE '低活跃用户'
                  END AS rfmLevel
                FROM (
                  SELECT
                    up.user_id AS userId,
                    m.username AS username,
                    up.user_level AS userLevel,
                    up.favorite_category_id AS favoriteCategoryId,
                    c.name AS favoriteCategoryName,
                    up.last_active_time AS lastActiveTime,
                    up.active_days AS activeDays,
                    (IFNULL(up.view_count, 0) + IFNULL(up.search_count, 0)
                      + IFNULL(up.fav_count, 0) * 2 + IFNULL(up.cart_count, 0) * 3
                      + IFNULL(up.order_count, 0) * 4 + IFNULL(up.pay_count, 0) * 5
                      + IFNULL(up.active_days, 0) * 2) AS behaviorScore,
                    IFNULL(pay.payAmount, 0) AS payAmount,
                    CASE
                      WHEN up.last_active_time IS NULL THEN 0
                      WHEN DATEDIFF(NOW(), up.last_active_time) <= 7 THEN 100
                      WHEN DATEDIFF(NOW(), up.last_active_time) <= 30 THEN 70
                      WHEN DATEDIFF(NOW(), up.last_active_time) <= 90 THEN 40
                      ELSE 10
                    END AS recencyScore,
                    LEAST(100, (IFNULL(up.view_count, 0) + IFNULL(up.search_count, 0)
                      + IFNULL(up.fav_count, 0) * 2 + IFNULL(up.cart_count, 0) * 3
                      + IFNULL(up.order_count, 0) * 4 + IFNULL(up.pay_count, 0) * 5
                      + IFNULL(up.active_days, 0) * 2) * 10) AS frequencyScore,
                    CASE
                      WHEN IFNULL(pay.payAmount, 0) >= 5000 THEN 100
                      WHEN IFNULL(pay.payAmount, 0) >= 1000 THEN 70
                      WHEN IFNULL(pay.payAmount, 0) > 0 THEN 40
                      ELSE 0
                    END AS monetaryScore,
                    ROUND(
                      CASE
                        WHEN up.last_active_time IS NULL THEN 0
                        WHEN DATEDIFF(NOW(), up.last_active_time) <= 7 THEN 100
                        WHEN DATEDIFF(NOW(), up.last_active_time) <= 30 THEN 70
                        WHEN DATEDIFF(NOW(), up.last_active_time) <= 90 THEN 40
                        ELSE 10
                      END * 0.4
                      + LEAST(100, (IFNULL(up.view_count, 0) + IFNULL(up.search_count, 0)
                        + IFNULL(up.fav_count, 0) * 2 + IFNULL(up.cart_count, 0) * 3
                        + IFNULL(up.order_count, 0) * 4 + IFNULL(up.pay_count, 0) * 5
                        + IFNULL(up.active_days, 0) * 2) * 10) * 0.4
                      + CASE
                          WHEN IFNULL(pay.payAmount, 0) >= 5000 THEN 100
                          WHEN IFNULL(pay.payAmount, 0) >= 1000 THEN 70
                          WHEN IFNULL(pay.payAmount, 0) > 0 THEN 40
                          ELSE 0
                        END * 0.2,
                      2
                    ) AS rfmScore
                  FROM user_profile up
                  LEFT JOIN ums_member m ON up.user_id = m.id
                  LEFT JOIN pms_product_category c ON up.favorite_category_id = c.id
                  LEFT JOIN (
                    SELECT member_id, SUM(pay_amount) AS payAmount
                    FROM oms_order
                    WHERE payment_time IS NOT NULL
                      AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    GROUP BY member_id
                  ) pay ON up.user_id = pay.member_id
                ) ranked
                ORDER BY ranked.rfmScore DESC, ranked.lastActiveTime DESC
                LIMIT ?
                """, days, limit);
    }

    public List<Map<String, Object>> categories() {
        return jdbcTemplate.queryForList("""
                SELECT id AS categoryId, name AS categoryName, parent_id AS parentId, level, sort
                FROM pms_product_category
                WHERE show_status = 1
                ORDER BY sort DESC, id ASC
                LIMIT 200
                """);
    }

    private List<Map<String, Object>> userEventSummary(Long userId, int days) {
        return jdbcTemplate.queryForList("""
                SELECT event_type AS eventType, COUNT(*) AS eventCount, COUNT(DISTINCT product_id) AS productCount
                FROM user_behavior_event
                WHERE user_id = ? AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY event_type
                ORDER BY FIELD(event_type, 'view','search','fav','cart','order','pay')
                """, userId, days);
    }

    private List<Map<String, Object>> productEventSummary(Long productId, int days) {
        return jdbcTemplate.queryForList("""
                SELECT event_type AS eventType, COUNT(*) AS eventCount, COUNT(DISTINCT user_id) AS userCount, COUNT(DISTINCT session_id) AS sessionCount
                FROM user_behavior_event
                WHERE product_id = ? AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                GROUP BY event_type
                ORDER BY FIELD(event_type, 'view','search','fav','cart','order','pay')
                """, productId, days);
    }

    private List<Map<String, Object>> recentUserEvents(Long userId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT e.event_type AS eventType, e.product_id AS productId, p.name AS productName, e.event_time AS eventTime
                FROM user_behavior_event e
                LEFT JOIN pms_product p ON e.product_id = p.id
                WHERE e.user_id = ?
                ORDER BY e.event_time DESC
                LIMIT ?
                """, userId, limit);
    }

    private List<Map<String, Object>> recentProductEvents(Long productId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT e.event_type AS eventType, e.user_id AS userId, e.session_id AS sessionId, e.event_time AS eventTime
                FROM user_behavior_event e
                WHERE e.product_id = ?
                ORDER BY e.event_time DESC
                LIMIT ?
                """, productId, limit);
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addFilters(StringBuilder sql, List<Object> args, int days, String eventType, Long categoryId) {
        sql.append(" AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)\n");
        args.add(days);
        if (StringUtils.hasText(eventType) && !"all".equalsIgnoreCase(eventType)) {
            sql.append(" AND event_type = ?\n");
            args.add(eventType);
        }
        if (categoryId != null) {
            sql.append(" AND category_id = ?\n");
            args.add(categoryId);
        }
    }
}
