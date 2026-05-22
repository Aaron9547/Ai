package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.ChatApplicationService;
import com.aaron.cloud.chat.dto.ChatMessageFeedbackRequest;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.dto.ChatRegenerateRequest;
import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.dto.LlmModelOption;
import com.aaron.cloud.chat.dto.WebSearchAvailabilityView;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class ChatConversationController extends OpenV1ControllerBases.Chat {

    private final ChatApplicationService chatApplicationService;

    @GetMapping("/models")
    public List<LlmModelOption> listModels() {
        return chatApplicationService.listModelsForChatPicker();
    }

    @GetMapping("/web-search-availability")
    public WebSearchAvailabilityView webSearchAvailability() {
        return new WebSearchAvailabilityView(chatApplicationService.isWebSearchAvailableForCurrentTenant());
    }

    @PostMapping("/conversations")
    public com.aaron.cloud.common.chat.entity.ChatConversation create(@RequestBody CreateConversationBody body) {
        return chatApplicationService.createConversation(body.getTitle());
    }

    @GetMapping("/conversations")
    public List<com.aaron.cloud.common.chat.entity.ChatConversation> list() {
        return chatApplicationService.listConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageView> listMessages(@PathVariable("id") long id) {
        return chatApplicationService.listConversationMessages(id);
    }

    @PatchMapping("/conversations/{id}")
    public com.aaron.cloud.common.chat.entity.ChatConversation rename(
            @PathVariable("id") long id, @Valid @RequestBody RenameConversationBody body) {
        return chatApplicationService.renameConversation(id, body.getTitle());
    }

    @DeleteMapping("/conversations/{id}")
    public void archive(@PathVariable("id") long id) {
        chatApplicationService.archiveConversation(id);
    }

    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@PathVariable("id") long id, @Valid @RequestBody ChatSendPayload body) {
        return chatApplicationService.streamUserMessage(id, body);
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/feedback")
    public void feedback(
            @PathVariable("id") long conversationId,
            @PathVariable("messageId") long messageId,
            @Valid @RequestBody ChatMessageFeedbackRequest body) {
        chatApplicationService.setAssistantMessageFeedback(conversationId, messageId, body.getVote());
    }

    /** 鍒犻櫎鏈€鍚庝竴鏉″姪鎵嬫秷鎭苟鍩轰簬鍓嶄竴鏉＄敤鎴锋秷鎭噸鏂版祦寮忕敓鎴愶紙SSE 涓?{@code POST .../messages} 涓€鑷达級銆?*/
    @PostMapping(
            value = "/conversations/{id}/messages/{assistantMessageId}/retry",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryAssistant(
            @PathVariable("id") long conversationId,
            @PathVariable("assistantMessageId") long assistantMessageId,
            @RequestBody(required = false) ChatRegenerateRequest body) {
        return chatApplicationService.regenerateAssistantStream(conversationId, assistantMessageId, body);
    }

    @Data
    public static class CreateConversationBody {
        private String title;
    }

    @Data
    public static class RenameConversationBody {
        private String title;
    }
}
