package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchSummarySupportTest {

    @Test
    void dedupeSummaryPieces_sameDateKeepsLonger() {
        String shortPiece = "以下是2026年5月22日科技资讯：\n- 6G试验频率获批。";
        String longPiece =
                "以下是2026年5月22日科技、财经资讯：\n- 6G试验频率获批。\n- AI换脸诈骗预警。\n- 油价上调。";
        List<String> out =
                WebSearchSummarySupport.dedupeSummaryPieces(List.of(shortPiece, longPiece));
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("AI换脸"));
    }

    @Test
    void joinSummaryText_mergesAndDedupes() {
        String a = "以下是2026年5月22日资讯：\n- A";
        String b = "以下是2026年5月23日资讯：\n- B";
        String c = "以下是2026年5月22日资讯：\n- A扩展";
        String joined = WebSearchSummarySupport.joinSummaryText(a, b + "\n\n---\n\n" + c);
        List<String> pieces = WebSearchSummarySupport.splitSummaryPieces(joined);
        assertEquals(2, pieces.size());
    }
}
