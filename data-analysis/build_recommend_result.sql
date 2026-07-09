USE mall;

DELETE FROM recommend_result WHERE recommend_type IN ('hot', 'rule');

INSERT INTO recommend_result (user_id, product_id, recommend_score, rank_no, recommend_type, reason, create_time)
SELECT 0, t.product_id, t.hot_score, (@hot_rank := @hot_rank + 1) AS rank_no, 'hot', '全站热门商品', NOW()
FROM (
  SELECT product_id, hot_score
  FROM product_profile
  WHERE hot_score > 0
  ORDER BY hot_score DESC, product_id DESC
  LIMIT 50
) t
CROSS JOIN (SELECT @hot_rank := 0) vars;

INSERT INTO recommend_result (user_id, product_id, recommend_score, rank_no, recommend_type, reason, create_time)
SELECT ranked.user_id, ranked.product_id, ranked.hot_score, ranked.rank_no, 'rule', '基于偏好分类的热门商品', NOW()
FROM (
  SELECT
    x.user_id,
    x.product_id,
    x.hot_score,
    @rule_rank := IF(@current_user = x.user_id, @rule_rank + 1, 1) AS rank_no,
    @current_user := x.user_id AS current_user_marker
  FROM (
    SELECT up.user_id, pp.product_id, pp.hot_score
    FROM user_profile up
    JOIN product_profile pp ON pp.category_id = up.favorite_category_id
    WHERE pp.hot_score > 0
      AND NOT EXISTS (
        SELECT 1 FROM user_behavior_event e
        WHERE e.user_id = up.user_id
          AND e.product_id = pp.product_id
          AND e.event_type IN ('order', 'pay')
      )
    ORDER BY up.user_id ASC, pp.hot_score DESC, pp.product_id DESC
  ) x
  CROSS JOIN (SELECT @rule_rank := 0, @current_user := NULL) vars
) ranked
WHERE ranked.rank_no <= 10;
