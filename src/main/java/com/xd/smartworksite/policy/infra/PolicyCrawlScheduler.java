package com.xd.smartworksite.policy.infra;

import com.xd.smartworksite.policy.application.PolicyApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.policy.scheduler", name = "enabled", havingValue = "true")
public class PolicyCrawlScheduler {
    private static final Logger log = LoggerFactory.getLogger(PolicyCrawlScheduler.class);

    private final PolicyApplicationService policyApplicationService;

    public PolicyCrawlScheduler(PolicyApplicationService policyApplicationService) {
        this.policyApplicationService = policyApplicationService;
    }

    @Scheduled(fixedDelayString = "${app.policy.scheduler.fixed-delay-ms:600000}")
    public void scheduleDueSources() {
        int created = policyApplicationService.createDueScheduledCrawlTasks();
        if (created > 0) {
            log.info("scheduled policy crawl tasks created, count={}", created);
        }
    }
}
