-- Demo behavior data for the analytics/recommendation coursework dashboard.
-- MySQL 5.7 compatible. Safe to run repeatedly: it removes the previous demo
-- events marked by source_page before inserting a fresh behavior set.

DELETE FROM user_behavior_event
WHERE source_page = '/demo/behavior-seed';

DROP TEMPORARY TABLE IF EXISTS demo_users;
DROP TEMPORARY TABLE IF EXISTS demo_products;

CREATE TEMPORARY TABLE demo_users AS
SELECT (@user_rank := @user_rank + 1) AS rn, picked.id AS user_id
FROM (
  SELECT id
  FROM ums_member
  ORDER BY id ASC
  LIMIT 5
) picked
CROSS JOIN (SELECT @user_rank := 0) seed;

CREATE TEMPORARY TABLE demo_products AS
SELECT (@product_rank := @product_rank + 1) AS rn,
       picked.id AS product_id,
       picked.product_category_id AS category_id,
       picked.name AS product_name
FROM (
  SELECT id, product_category_id, name
  FROM pms_product
  WHERE IFNULL(delete_status, 0) = 0
  ORDER BY sort DESC, id DESC
  LIMIT 10
) picked
CROSS JOIN (SELECT @product_rank := 0) seed;

-- Browsing signals: every demo user browses several products.
INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-view-', dp.product_id),
       dp.product_id,
       dp.category_id,
       'view',
       NULL,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL (du.rn + dp.rn) HOUR)
FROM demo_users du
JOIN demo_products dp ON dp.rn <= 8;

-- Search signals: each user contributes a keyword preference.
INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-search'),
       NULL,
       NULL,
       'search',
       CASE du.rn
         WHEN 1 THEN '手机'
         WHEN 2 THEN 'SSD'
         WHEN 3 THEN '华为'
         WHEN 4 THEN 'OPPO'
         ELSE '电脑'
       END,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL du.rn DAY)
FROM demo_users du;

-- Favorites and cart behavior create mid-intent users.
INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-fav-', dp.product_id),
       dp.product_id,
       dp.category_id,
       'fav',
       NULL,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL (du.rn + dp.rn) DAY)
FROM demo_users du
JOIN demo_products dp ON dp.rn <= 4
WHERE du.rn <= 4;

INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-cart-', dp.product_id),
       dp.product_id,
       dp.category_id,
       'cart',
       NULL,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL (du.rn + dp.rn + 1) DAY)
FROM demo_users du
JOIN demo_products dp ON dp.rn <= 3
WHERE du.rn <= 3;

-- Order and pay behavior create high-value users for the RFM ranking.
INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-order-', dp.product_id),
       dp.product_id,
       dp.category_id,
       'order',
       NULL,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL (du.rn + dp.rn) DAY)
FROM demo_users du
JOIN demo_products dp ON dp.rn <= 2
WHERE du.rn <= 2;

INSERT INTO user_behavior_event
  (user_id, session_id, product_id, category_id, event_type, keyword, source_page, device_type, ip, user_agent, event_time)
SELECT du.user_id,
       CONCAT('demo-u', du.user_id, '-pay-', dp.product_id),
       dp.product_id,
       dp.category_id,
       'pay',
       NULL,
       '/demo/behavior-seed',
       CASE WHEN du.rn % 2 = 0 THEN 'pc' ELSE 'h5' END,
       CONCAT('10.0.8.', du.rn),
       'mall-swarm-demo-seed',
       DATE_SUB(NOW(), INTERVAL (du.rn + dp.rn) DAY)
FROM demo_users du
JOIN demo_products dp ON dp.rn <= 2
WHERE du.rn = 1;

DROP TEMPORARY TABLE IF EXISTS demo_users;
DROP TEMPORARY TABLE IF EXISTS demo_products;

SELECT event_type AS eventType, COUNT(*) AS eventCount
FROM user_behavior_event
WHERE source_page = '/demo/behavior-seed'
GROUP BY event_type
ORDER BY FIELD(event_type, 'view', 'search', 'fav', 'cart', 'order', 'pay');
