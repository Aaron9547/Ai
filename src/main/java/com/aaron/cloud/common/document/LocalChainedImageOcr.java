package com.aaron.cloud.common.document;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 本机图片 OCR 链：RapidOCR（ONNX，Markdown 布局）优先，失败时回退 Tesseract。
 *
 * <p>不调用视觉大模型；供 {@link TikaDocumentTextExtractor} 与 RAG 上传共用。
 */
@Component
@RequiredArgsConstructor
public class LocalChainedImageOcr {

    private final LocalRapidOcrOnnxImageOcr localRapidOcrOnnxImageOcr;
    private final LocalTesseractImageOcr localTesseractImageOcr;

    public boolean isEnabled() {
        return localRapidOcrOnnxImageOcr.isEnabled() || localTesseractImageOcr.isEnabled();
    }

    public Optional<String> tryExtract(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        if (localRapidOcrOnnxImageOcr.isEnabled()) {
            Optional<String> rapid = localRapidOcrOnnxImageOcr.tryExtract(bytes);
            if (rapid.isPresent() && !rapid.get().isBlank()) {
                return rapid;
            }
        }
        if (localTesseractImageOcr.isEnabled()) {
            return localTesseractImageOcr.tryExtract(bytes);
        }
        return Optional.empty();
    }
}
