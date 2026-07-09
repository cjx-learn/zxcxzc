package com.macro.mall.analytics.component;

import com.macro.mall.analytics.service.AnalysisJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnalysisRebuildScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisRebuildScheduler.class);

    @Autowired
    private AnalysisJobService analysisJobService;

    @Value("${analytics.rebuild.auto-enabled:true}")
    private boolean autoEnabled;

    @Scheduled(initialDelayString = "${analytics.rebuild.initial-delay-ms:60000}",
            fixedDelayString = "${analytics.rebuild.fixed-delay-ms:300000}")
    public void rebuildProfilesAndRecommendations() {
        if (!autoEnabled) {
            return;
        }
        try {
            Map<String, Object> result = analysisJobService.rebuildProfilesAndRecommendations("scheduled");
            LOGGER.info("Scheduled analysis rebuild finished: {}", result);
        } catch (Exception ex) {
            LOGGER.error("Scheduled analysis rebuild failed", ex);
        }
    }
}
