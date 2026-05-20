package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.ChatMessageRole;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.ChatStarterPromptSource;
import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.ChatStarterFollowUpCacheRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.ChatStarterFollowUpCache;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.time.BeijingTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStarterFollowUpService {

    private static final String FOLLOW_UP_SYSTEM =
            """
            根据用户问题与助手回复，生成 2～3 条用户可能继续追问的短句。
            只输出 JSON 数组，不要 markdown。每项中文 8～36 字，与上文强相关、不重复。
            """;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatStarterFollowUpCacheRepository cacheRepository;
    private final ChatStarterPromptRepository promptRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final ModelInvokePort modelInvokePort;
    private final ChatStarterPromptJsonSupport jsonSupport;
    private final ChatStarterPromptApplicationService starterPromptApplicationService;

    public ChatStarterPromptDtos.StarterPromptListView listFollowUp(
            long conversationId, long assistantMessageId, int limit) {
        var snap = TenantContextHolder.require();
        long tenantId = snap.getTenantId();
        assertConversation(conversationId, tenantId);

        var cached = cacheRepository.findByAssistantMessage(tenantId, assistantMessageId);
        if (cached.isPresent()) {
            return fromJson(cached.get().getQuestionsJson(), limit);
        }

        ChatMessage assistant =
                messageRepository
                        .findById(assistantMessageId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "消息不存在"));
        if (assistant.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非助手消息");
        }

        String userQ = findPairedUserQuestion(tenantId, conversationId, assistantMessageId);
        String assistantText = assistant.getContent() == null ? "" : assistant.getContent().trim();
        if (assistantText.isBlank()) {
            return starterPromptApplicationService.listForOpen(
                    ChatStarterPromptScene.FOLLOW_UP, limit, false, null, false, false);
        }

        List<String> generated = generateFollowUpQuestions(tenantId, userQ, assistantText);
        if (generated.isEmpty()) {
            return starterPromptApplicationService.listForOpen(
                    ChatStarterPromptScene.FOLLOW_UP, limit, false, null, false, false);
        }

        var cache = new ChatStarterFollowUpCache();
        cache.setTenantId(tenantId);
        cache.setAssistantMessageId(assistantMessageId);
        cache.setQuestionsJson(jsonSupport.toJson(generated));
        cacheRepository.insert(cache);

        syncFollowUpPool(tenantId, generated);

        return fromJson(cache.getQuestionsJson(), limit);
    }

    private void syncFollowUpPool(long tenantId, List<String> questions) {
        for (String q : questions) {
            var p = new ChatStarterPrompt();
            p.setTenantId(tenantId);
            p.setScene(ChatStarterPromptScene.FOLLOW_UP);
            p.setSource(ChatStarterPromptSource.LLM_FOLLOW_UP);
            p.setPromptText(q);
            p.setWeight(70);
            p.setEnabled(1);
            p.setValidFrom(BeijingTime.today());
            p.setValidUntil(BeijingTime.today());
            promptRepository.insert(p);
        }
    }

    private List<String> generateFollowUpQuestions(
            long tenantId, String userQ, String assistantText) {
        SysLlmModel lang = llmModelRepository.pickDefaultLanguageModel(tenantId).orElse(null);
        if (lang == null) {
            return List.of();
        }
        LlmModelKind k = lang.getModelKind() != null ? lang.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            return List.of();
        }
        try {
            LlmModelKindPolicy.assertLanguageModelForChatStream(lang);
        } catch (Exception ex) {
            return List.of();
        }
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(FOLLOW_UP_SYSTEM);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(
                "【用户问题】\n"
                        + userQ
                        + "\n\n【助手回复】\n"
                        + (assistantText.length() > 4000
                                ? assistantText.substring(0, 4000) + "…"
                                : assistantText));
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        try {
            modelInvokePort.streamCompletion(req, acc::append);
        } catch (Exception e) {
            log.warn("[推荐问题] 追问生成失败 tenantId={}", tenantId, e);
            return List.of();
        }
        return jsonSupport.parseQuestions(acc.toString());
    }

    private String findPairedUserQuestion(long tenantId, long conversationId, long assistantMessageId) {
        List<Long> ids = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (ids.isEmpty()) {
            return "";
        }
        List<ChatMessage> msgs = messageRepository.listByTenantAndIdsInOrder(tenantId, ids);
        boolean seenAssistant = false;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage m = msgs.get(i);
            if (m.getId() != null && m.getId() == assistantMessageId) {
                seenAssistant = true;
                continue;
            }
            if (seenAssistant && m.getRole() == ChatMessageRole.USER) {
                return m.getContent() == null ? "" : m.getContent().trim();
            }
        }
        return "";
    }

    private ChatStarterPromptDtos.StarterPromptListView fromJson(String json, int limit) {
        List<String> qs = jsonSupport.parseQuestions(json);
        int cap = Math.max(1, Math.min(limit, 6));
        List<ChatStarterPromptDtos.StarterPromptItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(cap, qs.size()); i++) {
            items.add(new ChatStarterPromptDtos.StarterPromptItem(null, qs.get(i), "LLM_FOLLOW_UP"));
        }
        return new ChatStarterPromptDtos.StarterPromptListView(items, false);
    }

    private void assertConversation(long conversationId, long tenantId) {
        if (conversationRepository.findById(conversationId, tenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }
}
