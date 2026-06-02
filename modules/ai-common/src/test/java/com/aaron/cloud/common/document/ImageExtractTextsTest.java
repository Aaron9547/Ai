package com.aaron.cloud.common.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImageExtractTextsTest {

    @Test
    void pickRicher_prefersLonger() {
        assertEquals("abcdef", ImageExtractTexts.pickRicher("abc", "abcdef"));
        assertEquals("abcdef", ImageExtractTexts.pickRicher("abcdef", "abc"));
    }

    @Test
    void pickRicher_ignoresPlaceholder() {
        assertEquals(
                "vision",
                ImageExtractTexts.pickRicher(
                        ExtractedDocumentTexts.EMPTY_EXTRACT_PLACEHOLDER, "vision"));
    }
}
