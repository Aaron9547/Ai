package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchQueryRewriteServiceTest {

    @Test
    void heuristicRewrite_stripsPolitenessAndQuestionMark() {
        String out =
                WebSearchQueryRewriteService.heuristicRewrite(
                        "请帮我介绍一下2025年人工智能在医疗领域的最新进展？");
        assertTrue(out.contains("人工智能") || out.contains("医疗"));
        assertTrue(!out.startsWith("请"));
    }

    @Test
    void sanitizeLlmOutput_takesFirstLine() {
        String out = WebSearchQueryRewriteService.sanitizeLlmOutput("  人工智能 医疗 2025\n多余说明 ");
        assertEquals("人工智能 医疗 2025", out);
    }

    @Test
    void clampQuery_truncatesLongText() {
        String longText = "a".repeat(200);
        assertEquals(120, WebSearchQueryRewriteService.clampQuery(longText).length());
    }

    @Test
    void parseKeywordJson_readsArray() {
        List<String> keys =
                WebSearchQueryRewriteService.parseKeywordJson(
                        "说明 [\"6G 进展\", \"中国 通信\", \"2026 试点\"] 结束");
        assertEquals(3, keys.size());
        assertEquals("6G 进展", keys.get(0));
    }

    @Test
    void heuristicKeywordSource_mergesRecentHistory() {
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent("上海天气怎么样");
        var assistant = new ModelChatRequest.MessageTurn();
        assistant.setRole("assistant");
        assistant.setContent("今天上海晴，25度。");
        String merged =
                WebSearchQueryRewriteService.heuristicKeywordSource(
                        "那昨天呢", List.of(user, assistant));
        assertTrue(merged.contains("上海"));
        assertTrue(merged.contains("那昨天呢"));
    }

    @Test
    void heuristicKeywords_returnsThree() {
        List<String> keys =
                WebSearchQueryRewriteService.heuristicKeywords(
                        "请介绍一下2025年人工智能在医疗领域的最新进展");
        assertEquals(3, keys.size());
        assertTrue(keys.stream().noneMatch(String::isBlank));
    }
}
