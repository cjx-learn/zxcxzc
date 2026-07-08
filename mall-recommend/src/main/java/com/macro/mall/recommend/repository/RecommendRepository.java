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
                       IFNULL(pp.hot_score, 0) AS recommendScore,
                       'similar' AS recommendType,
                       '同分类相似商品' AS reason
                FROM pms_product src
                JOIN pms_product p ON p.product_category_id = src.product_category_id AND p.id <> src.id
                LEFT JOIN product_profile pp ON pp.product_id = p.id
                WHERE src.id = ? AND p.delete_status = 0 AND p.publish_status = 1
                ORDER BY IFNULL(pp.hot_score, 0) DESC, p.sort DESC, p.id DESC
                LIMIT ?
                """, productId, limit);
    }

    public List<Map<String, Object>> evaluation() {
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
                ORDER BY category_hit_rate DESC, category_ndcg_at_k DESC, category_precision_at_k DESC, coverage DESC, algorithm
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
