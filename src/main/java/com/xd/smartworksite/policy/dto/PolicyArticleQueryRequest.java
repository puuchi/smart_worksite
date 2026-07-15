package com.xd.smartworksite.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public class PolicyArticleQueryRequest {
    private Long projectId;
    private Long sourceId;
    private String keyword;
    private String indexStatus;
    private LocalDate publishDateFrom;
    private LocalDate publishDateTo;
    @Min(1)
    private int pageNo = 1;
    @Min(1)
    @Max(200)
    private int pageSize = 20;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public LocalDate getPublishDateFrom() { return publishDateFrom; }
    public void setPublishDateFrom(LocalDate publishDateFrom) { this.publishDateFrom = publishDateFrom; }
    public LocalDate getPublishDateTo() { return publishDateTo; }
    public void setPublishDateTo(LocalDate publishDateTo) { this.publishDateTo = publishDateTo; }
    public int getPageNo() { return pageNo; }
    public void setPageNo(int pageNo) { this.pageNo = pageNo; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
