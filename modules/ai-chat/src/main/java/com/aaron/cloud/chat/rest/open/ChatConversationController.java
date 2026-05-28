package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.ChatApplicationService;
import com.aaron.cloud.chat.dto.ChatConversationOpenView;
import com.aaron.cloud.chat.dto.ChatMessageFeedbackRequest;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.dto.ChatRegenerateRequest;
import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.dto.ConversationTokenTotalView;
import com.aaron.cloud.chat.dto.LlmModelOption;
import com.aaron.cloud.chat.dto.WebSearchAvailabilityView;
import com.aaron.cloud.common.context.TenantContextHolder;
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
    public ChatConversationOpenView create(@RequestBody CreateConversationBody body) {
        return ChatConversationOpenView.from(chatApplicationService.createConversation(body.getTitle()));
    }

    @GetMapping("/conversations")
    public List<ChatConversationOpenView> list() {
        return chatApplicationService.listConversations().stream()
                .map(ChatConversationOpenView::from)
                .toList();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageView> listMessages(@PathVariable("conversationId") String conversationId) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        return chatApplicationService.listConversationMessages(id);
    }

    /** 会话内全部模型调用 token 计量合计（联网插件、编排 LLM、主回答等）。 */
    @GetMapping("/conversations/{conversationId}/token-total")
    public ConversationTokenTotalView conversationTokenTotal(
            @PathVariable("conversationId") String conversationId) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        var snap = TenantContextHolder.require();
        return new ConversationTokenTotalView(
                chatApplicationService.conversationTokenTotalFromMetering(snap.getTenantId(), id));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ChatConversationOpenView rename(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody RenameConversationBody body) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        return ChatConversationOpenView.from(chatApplicationService.renameConversation(id, body.getTitle()));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void archive(@PathVariable("conversationId") String conversationId) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        chatApplicationService.archiveConversation(id);
    }

    @PostMapping(value = "/conversations/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(
            @PathVariable("conversationId") String conversationId, @Valid @RequestBody ChatSendPayload body) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        return chatApplicationService.streamUserMessage(id, body);
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/feedback")
    public void feedback(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("messageId") long messageId,
            @Valid @RequestBody ChatMessageFeedbackRequest body) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        chatApplicationService.setAssistantMessageFeedback(id, messageId, body.getVote());
    }

    /** 删除最后一条助手消息并基于前一条用户消息重新流式生成（SSE 与 {@code POST .../messages} 一致）。 */
    @PostMapping(
            value = "/conversations/{conversationId}/messages/{assistantMessageId}/retry",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retryAssistant(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("assistantMessageId") long assistantMessageId,
            @RequestBody(required = false) ChatRegenerateRequest body) {
        long id = chatApplicationService.requireOpenConversationId(conversationId);
        return chatApplicationService.regenerateAssistantStream(id, assistantMessageId, body);
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
