package com.xd.smartworksite.policy.infra;

import java.time.LocalDate;

public class PolicyCrawlerArticle {
    private String title;
    private String url;
    private String summary;
    private String content;
    private LocalDate publishDate;
    private String category;
    private String policyNo;
    private String sourceName;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPolicyNo() { return policyNo; }
    public void setPolicyNo(String policyNo) { this.policyNo = policyNo; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}
