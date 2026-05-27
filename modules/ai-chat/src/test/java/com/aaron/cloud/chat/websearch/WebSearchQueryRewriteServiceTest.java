package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
