package com.xd.smartworksite.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PolicySourceQueryRequest {
    private Long projectId;
    private String keyword;
    private String status;
    @Min(1)
    private int pageNo = 1;
    @Min(1)
    @Max(200)
    private int pageSize = 20;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPageNo() { return pageNo; }
    public void setPageNo(int pageNo) { this.pageNo = pageNo; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
