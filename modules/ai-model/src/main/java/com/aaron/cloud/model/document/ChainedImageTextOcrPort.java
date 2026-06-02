package com.aaron.cloud.model.document;

import com.aaron.cloud.common.api.ports.ImageTextOcrPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 图片 OCR 云端回退（可选）。本机 RapidOCR / Tesseract 已由
 * {@link com.aaron.cloud.common.document.TikaDocumentTextExtractor} 处理；对话附件上传默认不注入此链。
 */
@Primary
@Service
@RequiredArgsConstructor
public class ChainedImageTextOcrPort implements ImageTextOcrPort {

    private final VisionImageTextOcrService visionImageTextOcrService;

    @Override
    public Optional<String> tryExtract(long tenantId, byte[] bytes, String mimeType) {
        return visionImageTextOcrService.tryExtract(tenantId, bytes, mimeType);
    }
}
