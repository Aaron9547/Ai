package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.ChatApplicationService;
import com.aaron.cloud.chat.ChatAttachmentUploadService;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ChatAttachmentController extends OpenV1ControllerBases.ChatConversations {

    private final ChatApplicationService chatApplicationService;
    private final ChatAttachmentUploadService chatAttachmentUploadService;

    @PostMapping(value = "/{conversationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<AttachmentUploadView> upload(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(value = "files", required = false) MultipartFile[] files)
            throws Exception {
        long convId = chatApplicationService.requireOpenConversationId(conversationId);
        List<AttachmentUploadView> views = new ArrayList<>();
        if (files == null) {
            return views;
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            ChatAttachmentUploadService.SaveResult saved = chatAttachmentUploadService.save(convId, f);
            var row = saved.attachment();
            views.add(
                    new AttachmentUploadView(
                            row.getId(),
                            row.getFileName(),
                            row.getCharLength(),
                            saved.textExtracted(),
                            saved.kind()));
        }
        return views;
    }

    public record AttachmentUploadView(
            long id, String fileName, int charLength, boolean textExtracted, String kind) {}
}
