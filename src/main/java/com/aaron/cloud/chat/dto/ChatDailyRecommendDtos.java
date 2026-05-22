package com.aaron.cloud.chat.dto;

import java.util.List;

public final class ChatDailyRecommendDtos {

    private ChatDailyRecommendDtos() {}

    public record DailyRecommendItemView(
            String id,
            String tag,
            String title,
            String summary,
            String source,
            String date,
            String url) {}

    public record DailyRecommendResponse(
            String cacheKey,
            String recommendDate,
            String status,
            List<DailyRecommendItemView> list,
            List<String> profileTags,
            String errorMessage,
            boolean retryAllowed,
            boolean refreshAllowed) {}

    public record DailyRecommendClickBody(
            String itemId, String tag, String title, String summary, String source, String date, String url) {}
}
