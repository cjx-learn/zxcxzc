package com.macro.mall.analytics.repository;

import com.macro.mall.common.domain.UserBehaviorEventDTO;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BehaviorEventRepository {
    private static final String REAL_BEHAVIOR_SOURCE_CONDITION = """
                  AND (
                    source_page IS NULL
                    OR source_page NOT IN ('seed-category-products', '/demo/behavior-seed')
                  )
                """;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

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

    public void refreshRealtimeProfiles(UserBehaviorEventDTO event) {
        if (event == null || event.getProductId() == null) {
            return;
        }
        refreshProductProfile(event.getProductId());
        if (event.getUserId() != null) {
            refreshUserProductScore(event.getUserId(), event.getProductId());
        }
    }

    private void refreshProductProfile(Long productId) {
        jdbcTemplate.update("""
                REPLACE INTO product_profile (
                  product_id, category_id, view_count, search_count, fav_count, cart_count,
                  order_count, pay_count, hot_score, cart_rate, order_rate, pay_rate, update_time
                )
                SELECT
                  product_id,
                  MAX(category_id) AS category_id,
                  SUM(event_type='view') AS view_count,
                  SUM(event_type='search') AS search_count,
                  SUM(event_type='fav') AS fav_count,
                  SUM(event_type='cart') AS cart_count,
                  SUM(event_type='order') AS order_count,
                  SUM(event_type='pay') AS pay_count,
                  SUM(CASE event_type
                    WHEN 'view' THEN 1
                    WHEN 'search' THEN 1
                    WHEN 'fav' THEN 2
                    WHEN 'cart' THEN 3
                    WHEN 'order' THEN 4
                    WHEN 'pay' THEN 5
                    ELSE 0 END) AS hot_score,
                  ROUND(SUM(event_type='cart') / NULLIF(SUM(event_type='view'), 0), 4) AS cart_rate,
                  ROUND(SUM(event_type='order') / NULLIF(SUM(event_type='view'), 0), 4) AS order_rate,
                  ROUND(SUM(event_type='pay') / NULLIF(SUM(event_type='view'), 0), 4) AS pay_rate,
                  NOW()
                FROM user_behavior_event
                WHERE product_id = ?
                GROUP BY product_id
                """, productId);
    }

    private void refreshUserProductScore(Long userId, Long productId) {
        jdbcTemplate.update("""
                REPLACE INTO user_product_score (
                  user_id, product_id, score, view_count, search_count, fav_count, cart_count, order_count, pay_count, update_time
                )
                SELECT
                  user_id,
                  product_id,
                  SUM(CASE event_type
                    WHEN 'view' THEN 1
                    WHEN 'search' THEN 1
                    WHEN 'fav' THEN 2
                    WHEN 'cart' THEN 3
                    WHEN 'order' THEN 4
                    WHEN 'pay' THEN 5
                    ELSE 0 END) AS score,
                  SUM(event_type='view') AS view_count,
                  SUM(event_type='search') AS search_count,
                  SUM(event_type='fav') AS fav_count,
                  SUM(event_type='cart') AS cart_count,
                  SUM(event_type='order') AS order_count,
                  SUM(event_type='pay') AS pay_count,
                  NOW()
                FROM user_behavior_event
                WHERE user_id = ? AND product_id = ?
                GROUP BY user_id, product_id
                """, userId, productId);
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
        result.put("eventCategorySummary", userEventCategorySummary(userId, days));
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
        int safeLimit = Math.max(1, Math.min(limit, 100));
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
                  SUM(user_level = '中价值用户') AS middleValueUserCount,
                  SUM(user_level = '低价值用户') AS lowValueUserCount,
                  SUM(user_level = '中价值用户') AS potentialUserCount,
                  0 AS normalUserCount,
                  SUM(user_level = '低价值用户') AS lowActiveUserCount,
                  IFNULL(ROUND(AVG(active_days), 2), 0) AS avgActiveDays
                FROM user_profile
                """));
        result.put("levelDistribution", jdbcTemplate.queryForList("""
                SELECT user_level AS userLevel, COUNT(*) AS userCount
                FROM user_profile
                GROUP BY user_level
                ORDER BY FIELD(user_level, '高价值用户', '中价值用户', '低价值用户')
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
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(summaryRow("view",
                countBehaviorEvents(userId, days, "view", " AND product_id IS NOT NULL"),
                countDistinctBehaviorProducts(userId, days, "view", " AND product_id IS NOT NULL")));
        result.add(summaryRow("search",
                countKeywordSearches(userId, days),
                countDistinctSearchKeywords(userId, days)));
        result.add(summaryRow("fav",
                countMongoDocuments("memberProductCollection", userId, days),
                countMongoDistinctProducts("memberProductCollection", userId, days)));
        result.add(summaryRow("cart",
                countBehaviorEvents(userId, days, "cart", " AND product_id IS NOT NULL\n AND source_page = 'order_reorder_pay'"),
                countDistinctBehaviorProducts(userId, days, "cart", " AND product_id IS NOT NULL\n AND source_page = 'order_reorder_pay'")));
        result.add(summaryRow("order",
                countOrders(userId, days, "0,1,2,3"),
                countOrders(userId, days, "0,1,2,3")));
        result.add(summaryRow("pay",
                countOrders(userId, days, "1,2,3"),
                countOrders(userId, days, "1,2,3")));
        return result;
    }

    private Map<String, Object> summaryRow(String eventType, long eventCount, long productCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventType", eventType);
        row.put("eventCount", eventCount);
        row.put("productCount", productCount);
        return row;
    }

    private long countBehaviorEvents(Long userId, int days, String eventType, String extraCondition) {
        return queryLong("""
                SELECT COUNT(*)
                FROM user_behavior_event
                WHERE user_id = ?
                  AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND event_type = ?
                """ + extraCondition, userId, days, eventType);
    }

    private long countDistinctBehaviorProducts(Long userId, int days, String eventType, String extraCondition) {
        return queryLong("""
                SELECT COUNT(DISTINCT product_id)
                FROM user_behavior_event
                WHERE user_id = ?
                  AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND event_type = ?
                """ + extraCondition, userId, days, eventType);
    }

    private long countKeywordSearches(Long userId, int days) {
        return queryLong("""
                SELECT COUNT(*)
                FROM user_behavior_event
                WHERE user_id = ?
                  AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND event_type = 'search'
                  AND keyword IS NOT NULL
                  AND TRIM(keyword) <> ''
                """, userId, days);
    }

    private long countDistinctSearchKeywords(Long userId, int days) {
        return queryLong("""
                SELECT COUNT(DISTINCT TRIM(keyword))
                FROM user_behavior_event
                WHERE user_id = ?
                  AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND event_type = 'search'
                  AND keyword IS NOT NULL
                  AND TRIM(keyword) <> ''
                """, userId, days);
    }

    private long countOrders(Long userId, int days, String statuses) {
        return queryLong("""
                SELECT COUNT(*)
                FROM oms_order
                WHERE member_id = ?
                  AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND IFNULL(delete_status, 0) = 0
                  AND status IN (
                """ + statuses + ")", userId, days);
    }

    private long countCurrentCartQuantity(Long userId) {
        return queryLong("""
                SELECT IFNULL(SUM(IFNULL(quantity, 0)), 0)
                FROM oms_cart_item
                WHERE member_id = ?
                  AND IFNULL(delete_status, 0) = 0
                """, userId);
    }

    private long countCurrentCartDistinctProducts(Long userId) {
        return queryLong("""
                SELECT COUNT(DISTINCT product_id)
                FROM oms_cart_item
                WHERE member_id = ?
                  AND IFNULL(delete_status, 0) = 0
                """, userId);
    }

    private long countMongoDocuments(String collectionName, Long userId, int days) {
        if (mongoTemplate == null) {
            return 0L;
        }
        return mongoTemplate.count(recentMemberQuery(userId, days), collectionName);
    }

    private long countMongoDistinctProducts(String collectionName, Long userId, int days) {
        if (mongoTemplate == null) {
            return 0L;
        }
        return mongoTemplate.findDistinct(recentMemberQuery(userId, days), "productId", collectionName, Long.class).size();
    }

    private Query recentMemberQuery(Long userId, int days) {
        Date startTime = Date.from(LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant());
        return new Query()
                .addCriteria(Criteria.where("memberId").is(userId))
                .addCriteria(Criteria.where("createTime").gte(startTime));
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private List<Map<String, Object>> userEventCategorySummary(Long userId, int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(behaviorEventCategorySummary(userId, days, "view", " AND e.product_id IS NOT NULL"));
        result.addAll(searchEventCategorySummary(userId, days));
        result.addAll(mongoCollectionCategorySummary("fav", "memberProductCollection", userId, days));
        result.addAll(cartBehaviorEventCategorySummary(userId, days));
        result.addAll(orderCategorySummary("order", userId, days, "0,1,2,3"));
        result.addAll(orderCategorySummary("pay", userId, days, "1,2,3"));
        return result;
    }

    private List<Map<String, Object>> behaviorEventCategorySummary(Long userId, int days, String eventType, String extraCondition) {
        String safeExtraCondition = StringUtils.hasText(extraCondition) ? extraCondition + "\n" : "";
        return jdbcTemplate.queryForList("""
                SELECT
                  ? AS eventType,
                  COALESCE(c.id, pc.id) AS categoryId,
                  COALESCE(c.name, pc.name, '\u672a\u8bc6\u522b') AS categoryName,
                  COUNT(*) AS eventCount,
                  COUNT(DISTINCT e.product_id) AS productCount
                FROM user_behavior_event e
                LEFT JOIN pms_product p ON e.product_id = p.id
                LEFT JOIN pms_product_category c ON e.category_id = c.id
                LEFT JOIN pms_product_category pc ON p.product_category_id = pc.id
                WHERE e.user_id = ?
                  AND e.event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND e.event_type = ?
                """ + safeExtraCondition + """
                GROUP BY COALESCE(c.id, pc.id), COALESCE(c.name, pc.name, '\u672a\u8bc6\u522b')
                ORDER BY eventCount DESC
                """, eventType, userId, days, eventType);
    }

    private List<Map<String, Object>> cartBehaviorEventCategorySummary(Long userId, int days) {
        return jdbcTemplate.queryForList("""
                SELECT
                  'cart' AS eventType,
                  COALESCE(c.id, pc.id) AS categoryId,
                  COALESCE(c.name, pc.name, '\u672a\u8bc6\u522b') AS categoryName,
                  COUNT(*) AS eventCount,
                  COUNT(DISTINCT e.product_id) AS productCount
                FROM user_behavior_event e
                LEFT JOIN pms_product p ON e.product_id = p.id
                LEFT JOIN pms_product_category c ON e.category_id = c.id
                LEFT JOIN pms_product_category pc ON p.product_category_id = pc.id
                WHERE e.user_id = ?
                  AND e.event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND e.event_type = 'cart'
                  AND e.product_id IS NOT NULL
                  AND e.source_page = 'order_reorder_pay'
                GROUP BY COALESCE(c.id, pc.id), COALESCE(c.name, pc.name, '\u672a\u8bc6\u522b')
                ORDER BY eventCount DESC
                """, userId, days);
    }

    private List<Map<String, Object>> searchEventCategorySummary(Long userId, int days) {
        return jdbcTemplate.queryForList("""
                SELECT
                  'search' AS eventType,
                  NULL AS categoryId,
                  TRIM(e.keyword) AS categoryName,
                  COUNT(*) AS eventCount,
                  1 AS productCount
                FROM user_behavior_event e
                WHERE e.user_id = ?
                  AND e.event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                  AND e.event_type = 'search'
                  AND e.keyword IS NOT NULL
                  AND TRIM(e.keyword) <> ''
                GROUP BY TRIM(e.keyword)
                ORDER BY eventCount DESC, categoryName ASC
                """, userId, days);
    }

    private List<Map<String, Object>> cartCategorySummary(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT
                  'cart' AS eventType,
                  ci.product_category_id AS categoryId,
                  COALESCE(c.name, '\u672a\u8bc6\u522b') AS categoryName,
                  IFNULL(SUM(IFNULL(ci.quantity, 0)), 0) AS eventCount,
                  COUNT(DISTINCT ci.product_id) AS productCount
                FROM oms_cart_item ci
                LEFT JOIN pms_product_category c ON ci.product_category_id = c.id
                WHERE ci.member_id = ?
                  AND IFNULL(ci.delete_status, 0) = 0
                GROUP BY ci.product_category_id, c.name
                HAVING IFNULL(SUM(IFNULL(ci.quantity, 0)), 0) > 0
                ORDER BY eventCount DESC
                """, userId);
    }

    private List<Map<String, Object>> orderCategorySummary(String eventType, Long userId, int days, String statuses) {
        return jdbcTemplate.queryForList("""
                SELECT
                  ? AS eventType,
                  oc.categoryId AS categoryId,
                  COALESCE(c.name, '\u672a\u8bc6\u522b') AS categoryName,
                  COUNT(*) AS eventCount,
                  COUNT(*) AS productCount
                FROM (
                  SELECT
                    o.id AS orderId,
                    MIN(oi.product_category_id) AS categoryId
                  FROM oms_order o
                  LEFT JOIN oms_order_item oi ON o.id = oi.order_id
                  WHERE o.member_id = ?
                    AND o.create_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    AND IFNULL(o.delete_status, 0) = 0
                    AND o.status IN (
                """ + statuses + """
                    )
                  GROUP BY o.id
                ) oc
                LEFT JOIN pms_product_category c ON oc.categoryId = c.id
                GROUP BY oc.categoryId, c.name
                ORDER BY eventCount DESC
                """, eventType, userId, days);
    }

    private List<Map<String, Object>> mongoCollectionCategorySummary(String eventType, String collectionName, Long userId, int days) {
        List<Map<String, Object>> empty = new ArrayList<>();
        if (mongoTemplate == null) {
            return empty;
        }
        Query query = recentMemberQuery(userId, days);
        query.fields().include("productId");
        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);
        List<Long> productIds = new ArrayList<>();
        for (Document document : documents) {
            Long productId = asLong(document.get("productId"));
            if (productId != null && !productIds.contains(productId)) {
                productIds.add(productId);
            }
        }
        if (productIds.isEmpty()) {
            return empty;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < productIds.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        List<Object> args = new ArrayList<>();
        args.add(eventType);
        args.addAll(productIds);
        return jdbcTemplate.queryForList("""
                SELECT
                  ? AS eventType,
                  p.product_category_id AS categoryId,
                  COALESCE(c.name, '\u672a\u8bc6\u522b') AS categoryName,
                  COUNT(*) AS eventCount,
                  COUNT(*) AS productCount
                FROM pms_product p
                LEFT JOIN pms_product_category c ON p.product_category_id = c.id
                WHERE p.id IN (
                """ + placeholders + """
                )
                GROUP BY p.product_category_id, c.name
                ORDER BY eventCount DESC
                """, args.toArray());
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
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
