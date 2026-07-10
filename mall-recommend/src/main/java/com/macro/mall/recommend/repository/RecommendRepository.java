package com.macro.mall.recommend.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RecommendRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> hot(int limit) {
        return recommendationList(0L, "hot", limit);
    }

    public List<Map<String, Object>> userRecommend(Long userId, String type, int limit) {
        List<Map<String, Object>> list = recommendationList(userId, type, limit);
        if (!list.isEmpty()) {
            return list;
        }
        list = personalizedFallback(userId, type, limit);
        return list.isEmpty() ? hot(limit) : list;
    }

    public List<Map<String, Object>> similar(Long productId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT p.id AS productId, p.name AS productName, p.pic AS productPic,
                       p.price AS productPrice, p.sub_title AS productSubTitle,
                       c.name AS categoryName,
                       ROUND(
                         CASE
                           WHEN p.product_category_id = src.product_category_id THEN 100
                           WHEN IFNULL(c.parent_id, 0) = IFNULL(src_c.parent_id, 0) THEN 65
                           ELSE 20
                         END * 0.30
                         + CASE
                             WHEN IFNULL(p.price, 0) > 0 AND IFNULL(src.price, 0) > 0
                               THEN GREATEST(0, 100 - ABS(p.price - src.price) / GREATEST(p.price, src.price) * 100)
                             ELSE 45
                           END * 0.15
                         + LEAST(IFNULL(pp.hot_score, 0), 100) * 0.20
                         + LEAST(IFNULL(behavior.behavior_score, 0), 100) * 0.25
                         + CASE
                             WHEN LOWER(SUBSTRING_INDEX(p.name, ' ', 1)) = LOWER(SUBSTRING_INDEX(src.name, ' ', 1)) THEN 100
                             ELSE 35
                           END * 0.10
                       , 2) AS recommendScore,
                       'similar' AS recommendType,
                       CASE
                         WHEN p.product_category_id = src.product_category_id THEN '\u540c\u5c0f\u7c7b'
                         WHEN IFNULL(c.parent_id, 0) = IFNULL(src_c.parent_id, 0) THEN '\u540c\u5927\u7c7b'
                         WHEN IFNULL(behavior.common_user_count, 0) > 0 THEN '\u884c\u4e3a\u76f8\u4f3c'
                         ELSE '\u70ed\u95e8\u8865\u5145'
                       END AS similarType,
                       ROUND(CASE
                         WHEN p.product_category_id = src.product_category_id THEN 100
                         WHEN IFNULL(c.parent_id, 0) = IFNULL(src_c.parent_id, 0) THEN 65
                         ELSE 20
                       END, 2) AS categoryScore,
                       ROUND(CASE
                         WHEN IFNULL(p.price, 0) > 0 AND IFNULL(src.price, 0) > 0
                           THEN GREATEST(0, 100 - ABS(p.price - src.price) / GREATEST(p.price, src.price) * 100)
                         ELSE 45
                       END, 2) AS priceScore,
                       ROUND(LEAST(IFNULL(pp.hot_score, 0), 100), 2) AS hotScore,
                       ROUND(LEAST(IFNULL(behavior.behavior_score, 0), 100), 2) AS behaviorScore,
                       IFNULL(behavior.common_user_count, 0) AS commonUserCount,
                       CONCAT(
                         CASE
                           WHEN p.product_category_id = src.product_category_id THEN '\u540c\u5c0f\u7c7b'
                           WHEN IFNULL(c.parent_id, 0) = IFNULL(src_c.parent_id, 0) THEN '\u540c\u5927\u7c7b'
                           ELSE '\u8de8\u7c7b\u70ed\u95e8'
                         END,
                         '\uff5c\u4ef7\u683c\u76f8\u4f3c\u5ea6',
                         ROUND(CASE
                           WHEN IFNULL(p.price, 0) > 0 AND IFNULL(src.price, 0) > 0
                             THEN GREATEST(0, 100 - ABS(p.price - src.price) / GREATEST(p.price, src.price) * 100)
                           ELSE 45
                         END, 0),
                         '\uff5c\u5171\u540c\u884c\u4e3a\u7528\u6237',
                         IFNULL(behavior.common_user_count, 0),
                         '\uff5c\u70ed\u5ea6',
                         ROUND(LEAST(IFNULL(pp.hot_score, 0), 100), 0)
                       ) AS reason
                FROM pms_product src
                LEFT JOIN pms_product_category src_c ON src_c.id = src.product_category_id
                JOIN pms_product p ON p.id <> src.id
                LEFT JOIN pms_product_category c ON c.id = p.product_category_id
                LEFT JOIN product_profile pp ON pp.product_id = p.id
                LEFT JOIN (
                  SELECT cand.product_id,
                         COUNT(*) AS common_user_count,
                         SUM(LEAST(src_score.score, cand.score)) * 12 AS behavior_score
                  FROM user_product_score src_score
                  JOIN user_product_score cand ON cand.user_id = src_score.user_id
                                                AND cand.product_id <> src_score.product_id
                  WHERE src_score.product_id = ?
                  GROUP BY cand.product_id
                ) behavior ON behavior.product_id = p.id
                WHERE src.id = ?
                  AND p.delete_status = 0
                  AND p.publish_status = 1
                  AND (
                    p.product_category_id = src.product_category_id
                    OR IFNULL(c.parent_id, 0) = IFNULL(src_c.parent_id, 0)
                    OR IFNULL(pp.hot_score, 0) > 0
                    OR IFNULL(behavior.common_user_count, 0) > 0
                  )
                ORDER BY recommendScore DESC,
                  IFNULL(behavior.common_user_count, 0) DESC,
                  p.sort DESC,
                  p.id DESC
                LIMIT ?
                """, productId, productId, limit);
    }

    public List<Map<String, Object>> evaluate() {
        return jdbcTemplate.queryForList("""
                SELECT algorithm,
                       algorithm_label AS algorithmLabel,
                       k_value AS k,
                       evaluated_user_count AS evaluatedUserCount,
                       hit_user_count AS hitUserCount,
                       hit_rate AS hitRate,
                       category_hit_rate AS categoryHitRate,
                       precision_at_k AS precisionAtK,
                       category_precision_at_k AS categoryPrecisionAtK,
                       category_ndcg_at_k AS categoryNdcgAtK,
                       recall_at_k AS recallAtK,
                       ndcg_at_k AS ndcgAtK,
                       coverage,
                       total_hit_count AS totalHitCount,
                       total_category_hit_count AS totalCategoryHitCount,
                       total_recommend_count AS totalRecommendCount,
                       evaluation_note AS evaluationNote,
                       create_time AS createTime
                FROM recommend_evaluation
                ORDER BY create_time DESC,
                         CASE algorithm
                           WHEN 'model_deepfm' THEN 1
                           WHEN 'model_ncf' THEN 2
                           WHEN 'model_itemcf' THEN 3
                           WHEN 'hot' THEN 4
                           ELSE 9
                         END,
                         algorithm ASC
                """);
    }

    private List<Map<String, Object>> recommendationList(Long userId, String type, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT rr.product_id AS productId, p.name AS productName, p.pic AS productPic,
                       p.price AS productPrice, p.sub_title AS productSubTitle,
                       c.name AS categoryName,
                       rr.recommend_score AS recommendScore, rr.rank_no AS rankNo,
                       rr.recommend_type AS recommendType,
                       CASE rr.recommend_type
                         WHEN 'model_deepfm' THEN CONCAT(
                           'DeepFM特征交叉：用户偏好',
                           IFNULL(favc.name, '未识别分类'),
                           '，候选商品属于',
                           IFNULL(c.name, '未识别分类'),
                           '，综合热度',
                           ROUND(IFNULL(pp.hot_score, 0), 0),
                           '，预测兴趣分',
                           ROUND(rr.recommend_score, 4)
                         )
                         WHEN 'model_ncf' THEN CONCAT(
                           'NCF深度协同过滤：用户向量与',
                           IFNULL(c.name, '该类目'),
                           '商品向量匹配度较高，排名TOP',
                           rr.rank_no,
                           '，匹配分',
                           ROUND(rr.recommend_score, 4)
                         )
                         WHEN 'model_itemcf' THEN CONCAT(
                           'ItemCF协同过滤：',
                           CASE
                             WHEN LOCATE('与用户历史商品', IFNULL(rr.reason, '')) > 0
                               THEN SUBSTRING(rr.reason, LOCATE('与用户历史商品', rr.reason))
                             ELSE '与用户历史兴趣商品协同相似'
                           END,
                           '；候选类目',
                           IFNULL(c.name, '未识别分类'),
                           '，商品热度',
                           ROUND(IFNULL(pp.hot_score, 0), 0)
                         )
                         WHEN 'rule' THEN CONCAT(
                           '规则推荐：命中用户偏好类目',
                           IFNULL(favc.name, IFNULL(c.name, '未识别分类')),
                           '，同类热度排名TOP',
                           rr.rank_no,
                           '，热度分',
                           ROUND(IFNULL(pp.hot_score, rr.recommend_score), 0)
                         )
                         WHEN 'hot' THEN CONCAT(
                           '热门推荐：全站热度靠前，类目',
                           IFNULL(c.name, '未识别分类'),
                           '，热度分',
                           ROUND(IFNULL(pp.hot_score, rr.recommend_score), 0)
                         )
                         ELSE IFNULL(NULLIF(rr.reason, ''), '暂无推荐说明')
                       END AS reason
                FROM recommend_result rr
                LEFT JOIN pms_product p ON rr.product_id = p.id
                LEFT JOIN product_profile pp ON rr.product_id = pp.product_id
                LEFT JOIN pms_product_category c ON p.product_category_id = c.id
                LEFT JOIN user_profile up ON rr.user_id = up.user_id
                LEFT JOIN pms_product_category favc ON up.favorite_category_id = favc.id
                WHERE rr.user_id = ? AND rr.recommend_type = ?
                ORDER BY rr.rank_no ASC, rr.recommend_score DESC
                LIMIT ?
                """, userId, type, limit);
    }

    private List<Map<String, Object>> personalizedFallback(Long userId, String type, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT ranked.productId, ranked.productName, ranked.productPic,
                       ranked.productPrice, ranked.productSubTitle, ranked.categoryName,
                       ranked.recommendScore, ranked.rankNo, ranked.recommendType, ranked.reason
                FROM (
                  SELECT candidates.*,
                         @rank_no := @rank_no + 1 AS rankNo
                  FROM (
                    SELECT p.id AS productId, p.name AS productName, p.pic AS productPic,
                           p.price AS productPrice, p.sub_title AS productSubTitle,
                           c.name AS categoryName,
                           ROUND(
                             CASE
                               WHEN p.product_category_id = profile.favoriteCategoryId THEN 80
                               WHEN IFNULL(c.parent_id, 0) = profile.favoriteCategoryId THEN 68
                               WHEN IFNULL(c.parent_id, 0) = IFNULL(favc.parent_id, -1) THEN 58
                               ELSE 30
                             END
                             + LEAST(IFNULL(pp.hot_score, 0), 120) * 0.35
                             + (MOD(p.id * 37 + ? * 17, 100) / 100) * 12,
                             2
                           ) AS recommendScore,
                           ? AS recommendType,
                           CONCAT(
                             CASE ?
                               WHEN 'model_deepfm' THEN 'DeepFM候选扩展'
                               WHEN 'model_ncf' THEN 'NCF候选扩展'
                               WHEN 'model_itemcf' THEN 'ItemCF候选扩展'
                               WHEN 'rule' THEN '规则个性化推荐'
                               ELSE '个性化推荐'
                             END,
                             '：结合用户画像实时召回；偏好',
                             IFNULL(favc.name, '未识别分类'),
                             '，候选商品属于',
                             IFNULL(c.name, '未识别分类'),
                             '，热度分',
                             ROUND(IFNULL(pp.hot_score, 0), 0)
                           ) AS reason
                    FROM (
                      SELECT up.user_id AS userId,
                             COALESCE(
                               up.favorite_category_id,
                               (
                                 SELECT p2.product_category_id
                                 FROM user_behavior_event e2
                                 JOIN pms_product p2 ON p2.id = e2.product_id
                                 WHERE e2.user_id = ?
                                   AND e2.product_id IS NOT NULL
                                 GROUP BY p2.product_category_id
                                 ORDER BY SUM(CASE e2.event_type
                                   WHEN 'pay' THEN 6
                                   WHEN 'order' THEN 5
                                   WHEN 'cart' THEN 4
                                   WHEN 'fav' THEN 3
                                   WHEN 'view' THEN 1
                                   ELSE 1
                                 END) DESC, MAX(e2.create_time) DESC
                                 LIMIT 1
                               )
                             ) AS favoriteCategoryId
                      FROM (SELECT ? AS user_id) seed
                      LEFT JOIN user_profile up ON up.user_id = seed.user_id
                    ) profile
                    LEFT JOIN pms_product_category favc ON favc.id = profile.favoriteCategoryId
                    JOIN pms_product p
                    LEFT JOIN pms_product_category c ON c.id = p.product_category_id
                    LEFT JOIN product_profile pp ON pp.product_id = p.id
                    WHERE IFNULL(p.delete_status, 0) = 0
                      AND IFNULL(p.publish_status, 1) = 1
                      AND NOT EXISTS (
                        SELECT 1
                        FROM user_behavior_event bought
                        WHERE bought.user_id = profile.userId
                          AND bought.product_id = p.id
                          AND bought.event_type IN ('order', 'pay')
                      )
                      AND (
                        profile.favoriteCategoryId IS NULL
                        OR p.product_category_id = profile.favoriteCategoryId
                        OR IFNULL(c.parent_id, 0) = profile.favoriteCategoryId
                        OR IFNULL(c.parent_id, 0) = IFNULL(favc.parent_id, -1)
                        OR IFNULL(pp.hot_score, 0) > 0
                      )
                    ORDER BY recommendScore DESC, IFNULL(pp.hot_score, 0) DESC, p.sort DESC, p.id DESC
                    LIMIT ?
                  ) candidates
                  CROSS JOIN (SELECT @rank_no := 0) vars
                ) ranked
                """, userId, type, type, userId, userId, limit);
    }
}
