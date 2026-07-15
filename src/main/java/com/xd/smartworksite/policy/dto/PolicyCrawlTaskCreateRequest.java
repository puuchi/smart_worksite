package com.xd.smartworksite.policy.dto;

import jakarta.validation.constraints.NotNull;

public class PolicyCrawlTaskCreateRequest {
    @NotNull
    private Long projectId;
    private Long sourceId;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
}
