package com.xd.smartworksite.policy.infra;

import java.util.ArrayList;
import java.util.List;

public class PolicyCrawlerResponse {
    private Integer fetchedCount;
    private String message;
    private List<PolicyCrawlerArticle> articles = new ArrayList<>();

    public Integer getFetchedCount() { return fetchedCount; }
    public void setFetchedCount(Integer fetchedCount) { this.fetchedCount = fetchedCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<PolicyCrawlerArticle> getArticles() { return articles; }
    public void setArticles(List<PolicyCrawlerArticle> articles) { this.articles = articles; }
}
