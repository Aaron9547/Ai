package com.aaron.cloud.chat;

import com.aaron.cloud.common.chat.ChatAttachmentRepository;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.chat.support.DocumentTextExtractor;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ChatAttachmentUploadService {

    private static final long MAX_BYTES_PER_FILE = 15L * 1024 * 1024;

    private final ChatConversationRepository conversationRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final DocumentTextExtractor documentTextExtractor;

    public ChatAttachment save(long conversationId, MultipartFile file) throws Exception {
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
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        if (!isAllowedExtension(original)) {
            throw new IllegalArgumentException("不支持的文件类型：" + original);
        }
        byte[] bytes = file.getBytes();
        String text = documentTextExtractor.extract(bytes, original);
        if (text.isBlank()) {
            text =
                    "（未能从该文件中解析出可读文本；若为扫描件或图片请改用 OCR 管线。）";
        }
        var row = new ChatAttachment();
        row.setTenantId(snap.getTenantId());
        row.setConversationId(conversationId);
        row.setFileName(original);
        row.setMimeType(
                file.getContentType() == null || file.getContentType().isBlank()
                        ? "application/octet-stream"
                        : file.getContentType());
        row.setCharLength(text.length());
        row.setExtractedText(text);
        attachmentRepository.insert(row);
        return row;
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
