package com.aaron.cloud.chat.dto;

/**
 * 联网引用回显（用户端 / 开放接口与助手消息 {@code meta_json#webSearchReferences} 数组项一致）。
 */
public record WebSearchReferenceView(
        String title,
        String url,
        String summary,
        String siteName,
        String logoUrl,
        String publishTime,
        String extraJson) {}
