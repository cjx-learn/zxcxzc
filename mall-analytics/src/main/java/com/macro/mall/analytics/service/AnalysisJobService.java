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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AnalysisJobService {
    private static final Path PROFILE_SQL = Path.of("/opt/mall-swarm/data-analysis/build_profiles.sql");
    private static final Path RECOMMEND_SQL = Path.of("/opt/mall-swarm/data-analysis/build_recommend_result.sql");

    private final AtomicBoolean rebuilding = new AtomicBoolean(false);
    private volatile Map<String, Object> lastRebuildResult = new LinkedHashMap<>();
    private volatile String lastRebuildError;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> rebuildProfilesAndRecommendations() throws IOException {
        return rebuildProfilesAndRecommendations("manual");
    }

    public Map<String, Object> rebuildProfilesAndRecommendations(String trigger) throws IOException {
        if (!rebuilding.compareAndSet(false, true)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "skipped");
            result.put("message", "analysis rebuild is already running");
            result.put("trigger", trigger);
            result.put("runTime", LocalDateTime.now().toString());
            result.put("lastResult", lastRebuildResult);
            return result;
        }
        try {
            runSqlFile(PROFILE_SQL);
            runSqlFile(RECOMMEND_SQL);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("trigger", trigger);
            result.put("userProfileCount", tableCount("user_profile"));
            result.put("productProfileCount", tableCount("product_profile"));
            result.put("recommendResultCount", tableCount("recommend_result"));
            result.put("profileSql", PROFILE_SQL.toString());
            result.put("recommendSql", RECOMMEND_SQL.toString());
            result.put("runTime", LocalDateTime.now().toString());
            lastRebuildResult = result;
            lastRebuildError = null;
            return result;
        } catch (IOException | RuntimeException ex) {
            lastRebuildError = ex.getMessage();
            throw ex;
        } finally {
            rebuilding.set(false);
        }
    }

    public Map<String, Object> rebuildStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", rebuilding.get());
        status.put("lastResult", lastRebuildResult);
        status.put("lastError", lastRebuildError);
        status.put("checkTime", LocalDateTime.now().toString());
        return status;
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
