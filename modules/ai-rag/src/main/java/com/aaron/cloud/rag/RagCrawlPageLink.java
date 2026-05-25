package com.aaron.cloud.rag;

/** 页面内抽取的链接（标题 + 绝对地址），用于本地规则爬取发现。 */
public record RagCrawlPageLink(String href, String title) {}
