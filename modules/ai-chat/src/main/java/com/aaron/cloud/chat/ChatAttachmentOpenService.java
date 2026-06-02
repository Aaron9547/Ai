package com.aaron.cloud.chat;

import com.aaron.cloud.common.chat.ChatAttachmentRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatAttachmentOpenService {

    private final ChatApplicationService chatApplicationService;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatAttachmentBinStore attachmentBinStore;

    public record OpenAttachment(byte[] bytes, String fileName, String mimeType) {}

    public OpenAttachment open(String publicConversationId, long attachmentId) {
        long conversationId = chatApplicationService.requireOpenConversationId(publicConversationId);
        long tenantId = TenantContextHolder.require().getTenantId();
        ChatAttachment row =
                attachmentRepository
                        .findById(tenantId, attachmentId)
                        .filter(a -> a.getConversationId().equals(conversationId))
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "附件不存在"));
        byte[] bytes =
                attachmentBinStore
                        .load(tenantId, conversationId, attachmentId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "附件文件不存在"));
        String mime =
                row.getMimeType() == null || row.getMimeType().isBlank()
                        ? "application/octet-stream"
                        : row.getMimeType();
        return new OpenAttachment(bytes, row.getFileName(), mime);
    }
}
