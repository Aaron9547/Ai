package com.aaron.cloud.chat.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatDailyRecommendJsonSupportTest {

    private final ChatDailyRecommendJsonSupport json =
            new ChatDailyRecommendJsonSupport(new ObjectMapper());

    @Test
    void coldStartWrapperRoundTrip() {
        var items =
                List.of(
                        new ChatDailyRecommendJsonSupport.DailyRecommendItemRecord(
                                "热点",
                                "标题一",
                                "摘要",
                                "来源",
                                "2026-06-02",
                                "https://example.com/a"));
        String raw = json.toJsonColdStart(items);
        assertTrue(json.isColdStartBatch(raw));
        assertEquals(1, json.parseItems(raw).size());
        assertEquals("标题一", json.parseItems(raw).get(0).title());
    }

    @Test
    void plainArrayIsNotColdStart() {
        var items =
                List.of(
                        new ChatDailyRecommendJsonSupport.DailyRecommendItemRecord(
                                "科技", "t", "s", "src", "2026-06-02", ""));
        assertFalse(json.isColdStartBatch(json.toJson(items)));
    }
}
