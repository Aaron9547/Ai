package com.aaron.cloud.model.document;

import com.aaron.cloud.common.api.ports.ImageTextOcrPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 图片 OCR 云端回退：租户视觉大模型读图（OpenAI 兼容 {@code image_url}）。
 * 本机 RapidOCR/Tesseract 由 {@link com.aaron.cloud.common.document.TikaDocumentTextExtractor} 处理；
 * 对话图片在本机 OCR 质量不足时由 {@link com.aaron.cloud.chat.ChatAttachmentUploadService} 调用。
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
