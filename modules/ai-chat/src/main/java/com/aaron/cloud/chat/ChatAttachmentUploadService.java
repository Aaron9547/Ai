package com.aaron.cloud.chat;

import com.aaron.cloud.common.chat.ChatAttachmentRepository;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.document.ExtractedDocumentTexts;
import com.aaron.cloud.common.document.TikaDocumentTextExtractor;
import com.aaron.cloud.common.document.UploadFileNames;
import com.aaron.cloud.common.document.UploadedFileKind;
import com.aaron.cloud.chat.support.ChatAttachmentExtractLog;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentUploadService {

    private static final long MAX_BYTES_PER_FILE = 15L * 1024 * 1024;

    private final ChatConversationRepository conversationRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatAttachmentBinStore attachmentBinStore;
    private final TikaDocumentTextExtractor documentTextExtractor;

    public record SaveResult(ChatAttachment attachment, boolean textExtracted, String kind) {}

    public SaveResult save(long conversationId, MultipartFile file) throws Exception {
        var snap = TenantContextHolder.require();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("空文件");
        }
        if (file.getSize() > MAX_BYTES_PER_FILE) {
            throw new IllegalArgumentException("单文件超过 15MB 上限");
        }
        if (!conversationRepository
                .findById(conversationId, snap.getTenantId())
                .isPresent()) {
            throw new IllegalArgumentException("会话不存在");
        }
        String mimeType =
                file.getContentType() == null || file.getContentType().isBlank()
                        ? "application/octet-stream"
                        : file.getContentType();
        String original =
                UploadFileNames.normalize(
                        file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(),
                        mimeType);
        if (!isAllowedExtension(original)) {
            throw new IllegalArgumentException("不支持的文件类型：" + original);
        }
        String kind = UploadedFileKind.resolve(original);
        byte[] bytes = file.getBytes();
        String text =
                documentTextExtractor.extract(bytes, original, mimeType);
        boolean textExtracted = !text.isBlank();
        if (!textExtracted) {
            log.warn(
                    "[对话附件] 未解析出可读文本：文件名={}，字节数={}，MIME={}，类型={}",
                    original,
                    bytes.length,
                    mimeType,
                    kind);
            text = ExtractedDocumentTexts.EMPTY_EXTRACT_PLACEHOLDER;
        }
        var row = new ChatAttachment();
        row.setTenantId(snap.getTenantId());
        row.setConversationId(conversationId);
        row.setFileName(original);
        row.setMimeType(mimeType);
        row.setCharLength(text.length());
        row.setExtractedText(text);
        attachmentRepository.insert(row);
        attachmentBinStore.persist(snap.getTenantId(), conversationId, row.getId(), bytes);
        ChatAttachmentExtractLog.logAfterSave(
                row.getId(), original, mimeType, kind, textExtracted, text);
        return new SaveResult(row, textExtracted, kind);
    }

    private static boolean isAllowedExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf",
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "ppt",
                    "pptx",
                    "txt",
                    "md",
                    "csv",
                    "png",
                    "jpg",
                    "jpeg",
                    "gif",
                    "webp",
                    "bmp" -> true;
            default -> false;
        };
    }
}
