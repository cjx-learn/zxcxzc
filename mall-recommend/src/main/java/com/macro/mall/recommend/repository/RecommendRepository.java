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
                       rr.recommend_score AS recommendScore, rr.rank_no AS rankNo,
                       rr.recommend_type AS recommendType, rr.reason AS reason
                FROM recommend_result rr
                LEFT JOIN pms_product p ON rr.product_id = p.id
                WHERE rr.user_id = ? AND rr.recommend_type = ?
                ORDER BY rr.rank_no ASC, rr.recommend_score DESC
                LIMIT ?
                """, userId, type, limit);
    }
}
