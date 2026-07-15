package com.xd.smartworksite.policy.infra;

import java.time.LocalDateTime;

public class PolicyCrawlerRequest {
    private Long projectId;
    private Long sourceId;
    private String url;
    private LocalDateTime lastCrawledAt;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getLastCrawledAt() { return lastCrawledAt; }
    public void setLastCrawledAt(LocalDateTime lastCrawledAt) { this.lastCrawledAt = lastCrawledAt; }
}
