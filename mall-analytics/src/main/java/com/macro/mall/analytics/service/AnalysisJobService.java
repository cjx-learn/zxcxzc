package com.macro.mall.analytics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AnalysisJobService {
    private static final Path PROFILE_SQL = Path.of("/opt/mall-swarm/data-analysis/build_profiles.sql");
    private static final Path RECOMMEND_SQL = Path.of("/opt/mall-swarm/data-analysis/build_recommend_result.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> rebuildProfilesAndRecommendations() throws IOException {
        runSqlFile(PROFILE_SQL);
        runSqlFile(RECOMMEND_SQL);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userProfileCount", tableCount("user_profile"));
        result.put("productProfileCount", tableCount("product_profile"));
        result.put("recommendResultCount", tableCount("recommend_result"));
        result.put("profileSql", PROFILE_SQL.toString());
        result.put("recommendSql", RECOMMEND_SQL.toString());
        result.put("runTime", LocalDateTime.now().toString());
        return result;
    }

    private Long tableCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private void runSqlFile(Path path) throws IOException {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) {
            String trimmed = statement.replaceAll("(?m)^--.*$", "").trim();
            if (StringUtils.hasText(trimmed)) {
                jdbcTemplate.execute(trimmed);
            }
        }
    }
}
