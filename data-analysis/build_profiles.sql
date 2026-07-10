USE mall;

REPLACE INTO user_profile (
  user_id, view_count, search_count, fav_count, cart_count, order_count, pay_count,
  active_days, favorite_category_id, favorite_category_score, last_active_time, user_level, update_time
)
SELECT
  ranked.user_id,
  ranked.view_count,
  ranked.search_count,
  ranked.fav_count,
  ranked.cart_count,
  ranked.order_count,
  ranked.pay_count,
  ranked.active_days,
  ranked.favorite_category_id,
  IFNULL(ranked.favorite_category_score, 0),
  ranked.last_active_time,
  CASE
    WHEN ranked.total_users <= 1 THEN '高价值用户'
    WHEN ranked.rfm_percent <= 0.20 THEN '高价值用户'
    WHEN ranked.rfm_percent > 0.80 THEN '低价值用户'
    ELSE '中价值用户'
  END AS user_level,
  NOW()
FROM (
  SELECT
    scored.*,
    (@rfm_rank := @rfm_rank + 1) AS rfm_rank,
    (@rfm_rank / scored.total_users) AS rfm_percent
  FROM (
    SELECT
      rfm_base.*,
      ROUND(rfm_base.recency_score * 0.4 + rfm_base.frequency_score * 0.4 + rfm_base.monetary_score * 0.2, 2) AS rfm_score,
      totals.total_users
    FROM (
      SELECT
        profile_base.*,
        fav.favorite_category_id,
        IFNULL(fav.favorite_category_score, 0) AS favorite_category_score,
        CASE
          WHEN profile_base.last_active_time IS NULL THEN 0
          WHEN DATEDIFF(NOW(), profile_base.last_active_time) <= 7 THEN 100
          WHEN DATEDIFF(NOW(), profile_base.last_active_time) <= 30 THEN 70
          WHEN DATEDIFF(NOW(), profile_base.last_active_time) <= 90 THEN 40
          ELSE 10
        END AS recency_score,
        LEAST(100, (profile_base.view_count + profile_base.search_count
          + profile_base.fav_count * 2 + profile_base.cart_count * 3
          + profile_base.order_count * 4 + profile_base.pay_count * 5
          + profile_base.active_days * 2) * 10) AS frequency_score,
        CASE
          WHEN IFNULL(pay.pay_amount, 0) >= 5000 THEN 100
          WHEN IFNULL(pay.pay_amount, 0) >= 1000 THEN 70
          WHEN IFNULL(pay.pay_amount, 0) > 0 THEN 40
          ELSE 0
        END AS monetary_score,
        IFNULL(pay.pay_amount, 0) AS pay_amount
      FROM (
        SELECT
          user_ids.user_id,
          IFNULL(behavior_stats.view_count, 0) AS view_count,
          IFNULL(behavior_stats.search_count, 0) AS search_count,
          IFNULL(behavior_stats.fav_count, 0) AS fav_count,
          IFNULL(behavior_stats.cart_count, 0) AS cart_count,
          IFNULL(order_stats.order_count, 0) AS order_count,
          IFNULL(order_stats.pay_count, 0) AS pay_count,
          IFNULL(activity_stats.active_days, 0) AS active_days,
          activity_stats.last_active_time
        FROM (
          SELECT id AS user_id
          FROM ums_member
          WHERE IFNULL(status, 1) = 1
          UNION
          SELECT user_id
          FROM user_behavior_event
          WHERE user_id IS NOT NULL
          GROUP BY user_id
          UNION
          SELECT member_id AS user_id
          FROM oms_order
          WHERE member_id IS NOT NULL
            AND IFNULL(delete_status, 0) = 0
          GROUP BY member_id
        ) user_ids
        LEFT JOIN (
          SELECT
            user_id,
            SUM(event_type='view') AS view_count,
            SUM(event_type='search') AS search_count,
            SUM(event_type='fav') AS fav_count,
            SUM(event_type='cart') AS cart_count
          FROM user_behavior_event
          WHERE user_id IS NOT NULL
          GROUP BY user_id
        ) behavior_stats ON user_ids.user_id = behavior_stats.user_id
        LEFT JOIN (
          SELECT
            member_id AS user_id,
            SUM(status IN (0, 1, 2, 3)) AS order_count,
            SUM(status IN (1, 2, 3)) AS pay_count
          FROM oms_order
          WHERE member_id IS NOT NULL
            AND IFNULL(delete_status, 0) = 0
          GROUP BY member_id
        ) order_stats ON user_ids.user_id = order_stats.user_id
        LEFT JOIN (
          SELECT
            activity.user_id,
            COUNT(DISTINCT activity.active_date) AS active_days,
            MAX(activity.active_time) AS last_active_time
          FROM (
            SELECT user_id, event_date AS active_date, event_time AS active_time
            FROM user_behavior_event
            WHERE user_id IS NOT NULL
            UNION ALL
            SELECT member_id AS user_id, DATE(create_time) AS active_date, create_time AS active_time
            FROM oms_order
            WHERE member_id IS NOT NULL
              AND IFNULL(delete_status, 0) = 0
              AND status IN (0, 1, 2, 3)
          ) activity
          GROUP BY activity.user_id
        ) activity_stats ON user_ids.user_id = activity_stats.user_id
      ) profile_base
      LEFT JOIN (
        SELECT member_id, SUM(pay_amount) AS pay_amount
        FROM oms_order
        WHERE IFNULL(delete_status, 0) = 0
          AND status IN (1, 2, 3)
        GROUP BY member_id
      ) pay ON profile_base.user_id = pay.member_id
      LEFT JOIN (
        SELECT
          cps.user_id,
          CAST(SUBSTRING_INDEX(GROUP_CONCAT(cps.category_id ORDER BY cps.category_score DESC, cps.last_event_time DESC, cps.category_id ASC), ',', 1) AS UNSIGNED) AS favorite_category_id,
          MAX(cps.category_score) AS favorite_category_score
        FROM (
          SELECT
            user_id,
            category_id,
            SUM(CASE event_type
              WHEN 'view' THEN 1
              WHEN 'search' THEN 1
              WHEN 'fav' THEN 2
              WHEN 'cart' THEN 3
              WHEN 'order' THEN 4
              WHEN 'pay' THEN 5
              ELSE 0 END) AS category_score,
            MAX(event_time) AS last_event_time
          FROM user_behavior_event
          WHERE user_id IS NOT NULL AND category_id IS NOT NULL
          GROUP BY user_id, category_id
        ) cps
        GROUP BY cps.user_id
      ) fav ON profile_base.user_id = fav.user_id
    ) rfm_base
    CROSS JOIN (
      SELECT COUNT(*) AS total_users
      FROM (
        SELECT id AS user_id
        FROM ums_member
        WHERE IFNULL(status, 1) = 1
        UNION
        SELECT user_id
        FROM user_behavior_event
        WHERE user_id IS NOT NULL
        GROUP BY user_id
        UNION
        SELECT member_id AS user_id
        FROM oms_order
        WHERE member_id IS NOT NULL
          AND IFNULL(delete_status, 0) = 0
        GROUP BY member_id
      ) user_count
    ) totals
    ORDER BY rfm_score DESC, monetary_score DESC, frequency_score DESC, recency_score DESC,
      pay_count DESC, order_count DESC, cart_count DESC, last_active_time DESC, user_id ASC
  ) scored
  CROSS JOIN (SELECT @rfm_rank := 0) vars
) ranked;

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
WHERE product_id IS NOT NULL
GROUP BY product_id;

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
WHERE user_id IS NOT NULL AND product_id IS NOT NULL
GROUP BY user_id, product_id;
