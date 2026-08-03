package com.aaron.cloud.common.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttachmentOcrTextQualityTest {

    @Test
    void needsVisionFallback_whenMarkdownTableNoise() {
        String ocr = "| 008 | 009E | 002 | 008 |\n| --- | --- | --- | --- |\n| 1200 | | | |";
        assertTrue(AttachmentOcrTextQuality.needsVisionFallback(ocr));
        assertTrue(AttachmentOcrTextQuality.isTableNoise(ocr));
    }

    @Test
    void isUsableEntityHint_whenFloorPlanTitlePresent() {
        String ocr = "碧桂园江山赋 1#楼 YJ215 货量区彩户示意图\n客厅 主卧";
        assertTrue(AttachmentOcrTextQuality.isUsableEntityHint(ocr));
        assertFalse(AttachmentOcrTextQuality.needsVisionFallback(ocr));
    }
}
