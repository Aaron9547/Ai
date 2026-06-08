package com.aaron.cloud.chat.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.document.ExtractedDocumentTexts;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatAttachmentRetrievalSupportTest {

    @Test
    void userTurnPlaintextForRetrieval_ignoresAttachmentBlock() {
        var payload = new ChatSendPayload();
        payload.setContent("帮我看看这个户型");
        String augmented =
                "帮我看看这个户型"
                        + ChatAttachmentRetrievalSupport.USER_ATTACHMENT_BLOCK_MARKER
                        + "\n--- 文件：plan.png ---\n碧桂园江山赋 YJ215\n";
        assertEquals(
                "帮我看看这个户型",
                ChatAttachmentRetrievalSupport.userTurnPlaintextForRetrieval(payload, augmented, null));
    }

    @Test
    void retrievalKeywordSource_mergesUserQuestionWithAttachmentEntities() {
        var payload = new ChatSendPayload();
        payload.setContent("分析这个户型");
        var att = new ChatAttachment();
        att.setFileName("plan.png");
        att.setExtractedText("碧桂园江山赋 1#楼 YJ215 货量区彩户示意图\n三~二十四层平面图\n客厅 主卧");
        String source =
                ChatAttachmentRetrievalSupport.retrievalKeywordSource(
                        payload, payload.getContent(), null, List.of(att));
        assertTrue(source.contains("分析这个户型"));
        assertTrue(source.contains("碧桂园江山赋"));
        assertTrue(source.contains("YJ215"));
    }

    @Test
    void retrievalKeywordSource_fallsBackToFileNameWhenOcrEmpty() {
        var payload = new ChatSendPayload();
        payload.setContent("分析这个户型");
        var att = new ChatAttachment();
        att.setFileName("碧桂园江山赋-YJ215.png");
        att.setExtractedText(ExtractedDocumentTexts.EMPTY_EXTRACT_PLACEHOLDER);
        String source =
                ChatAttachmentRetrievalSupport.retrievalKeywordSource(
                        payload, payload.getContent(), null, List.of(att));
        assertTrue(source.contains("分析这个户型"));
        assertTrue(source.contains("碧桂园江山赋") || source.contains("YJ215"));
    }

    @Test
    void extractRetrievalSignals_filtersMarkdownTableNoise() {
        String ocr = "| 008 | 009E | 002 | 008 |\n| --- | --- | --- | --- |\n| 1200 | | | |";
        assertEquals("", ChatAttachmentRetrievalSupport.extractRetrievalSignals(ocr, 200));
        assertTrue(ChatAttachmentRetrievalSupport.isTableNoise(ocr));
    }

    @Test
    void retrievalKeywordSource_ignoresTableNoiseOcrWithoutMeaningfulFileName() {
        var payload = new ChatSendPayload();
        payload.setContent("这咋样");
        var att = new ChatAttachment();
        att.setFileName("IMG_001.png");
        att.setExtractedText("| 008 | 009E | 002 | 008 |\n| --- | --- | --- | --- |\n| 1200 | | | |");
        String source =
                ChatAttachmentRetrievalSupport.retrievalKeywordSource(
                        payload, payload.getContent(), null, List.of(att));
        assertEquals("这咋样", source);
        assertFalse(source.contains("008"));
        assertFalse(source.contains("上传文件摘要"));
    }

    @Test
    void shouldDeferWebSearch_whenDeicticAndNoUsableHint() {
        var att = new ChatAttachment();
        att.setFileName("IMG_001.png");
        att.setExtractedText("| 008 | 009E |");
        assertTrue(
                ChatAttachmentRetrievalSupport.shouldDeferWebSearch(List.of(att), "这咋样"));
    }

    @Test
    void shouldSkipRag_whenAttachmentsPresent() {
        assertTrue(ChatAttachmentRetrievalSupport.shouldSkipRag(List.of(new ChatAttachment())));
        assertFalse(ChatAttachmentRetrievalSupport.shouldSkipRag(List.of()));
    }

    @Test
    void shouldSkipRagQueryRewrite_whenAttachmentsPresent() {
        assertTrue(ChatAttachmentRetrievalSupport.shouldSkipRagQueryRewrite(List.of(new ChatAttachment())));
    }
}
