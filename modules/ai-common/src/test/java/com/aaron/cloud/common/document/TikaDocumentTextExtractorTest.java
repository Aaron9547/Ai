package com.aaron.cloud.common.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TikaDocumentTextExtractorTest {

    private final TikaDocumentTextExtractor extractor =
            new TikaDocumentTextExtractor(
                    new LocalChainedImageOcr(
                            new LocalRapidOcrOnnxImageOcr(false, "ONNX_PPOCR_V3"),
                            new LocalTesseractImageOcr(false, "eng")));

    @Test
    void extractTxt_sampleContainsChinese() throws Exception {
        byte[] bytes =
                Files.readAllBytes(new ClassPathResource("document-samples/sample.txt").getFile().toPath());
        String text = extractor.extract(bytes, "sample.txt", "text/plain");
        assertFalse(text.isBlank());
        assertTrue(text.contains("可读中文"));
    }

    @Test
    void extractInlineUtf8() throws Exception {
        String raw = "inline-附件-测试";
        String text = extractor.extract(raw.getBytes(StandardCharsets.UTF_8), "note.txt", "text/plain");
        assertTrue(text.contains("附件"));
    }

    @Test
    void extractTinyPng_isBlankWithoutOcr() throws Exception {
        byte[] png =
                new byte[] {
                    (byte) 0x89,
                    0x50,
                    0x4e,
                    0x47,
                    0x0d,
                    0x0a,
                    0x1a,
                    0x0a,
                    0x00,
                    0x00,
                    0x00,
                    0x0d
                };
        String text = extractor.extract(png, "dot.png", "image/png");
        assertTrue(text.isBlank());
    }
}
