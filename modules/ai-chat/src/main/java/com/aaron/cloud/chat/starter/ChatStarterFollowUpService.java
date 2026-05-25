package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.ChatStarterFollowUpCacheRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.time.BeijingTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private static final List<String> BUILTIN_FOLLOW_UP_FALLBACK =
            List.of("能再具体说说吗？", "还有其他需要注意的吗？", "请举一个实际例子");

    /** 流式结束前与联网收尾并行等待 LLM 追问的最长时间（毫秒）。 */
    private static final long STREAM_FOLLOW_UP_WAIT_MS = 500L;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatStarterFollowUpCacheRepository cacheRepository;
    private final ChatStarterPromptRepository promptRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final ModelInvokePort modelInvokePort;
    private final ChatStarterPromptJsonSupport jsonSupport;
    private final ChatStarterPromptApplicationService starterPromptApplicationService;
    private final ChatStarterPromptSimilarityService starterPromptSimilarityService;

    public ChatStarterPromptDtos.StarterPromptListView listFollowUp(
            long conversationId, long assistantMessageId, int limit) {
        var snap = TenantContextHolder.require();
        long tenantId = snap.getTenantId();
        assertConversation(conversationId, tenantId);

        var cached = cacheRepository.findByAssistantMessage(tenantId, assistantMessageId);
        if (cached.isPresent()) {
            return ensureNonEmpty(fromJson(cached.get().getQuestionsJson(), limit), limit);
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
            return ensureNonEmpty(
                    starterPromptApplicationService.listForTenant(
                            tenantId,
                            snap.getUserId(),
                            snap.getDeviceId(),
                            ChatStarterPromptScene.FOLLOW_UP,
                            limit,
                            false,
                            null,
                            false,
                            false),
                    limit);
        }

        var localSimilar =
                starterPromptSimilarityService.pickForConversation(
                        tenantId, userQ, assistantText, limit);
        if (localSimilar.isPresent()) {
            scheduleFollowUpLlmCache(tenantId, assistantMessageId, userQ, assistantText);
            return ensureNonEmpty(localSimilar.get(), limit);
        }

        List<String> generated = generateFollowUpQuestions(tenantId, userQ, assistantText);
        if (generated.isEmpty()) {
            return ensureNonEmpty(
                    starterPromptApplicationService.listForTenant(
                            tenantId,
                            snap.getUserId(),
                            snap.getDeviceId(),
                            ChatStarterPromptScene.FOLLOW_UP,
                            limit,
                            false,
                            null,
                            false,
                            false),
                    limit);
        }

        String questionsJson = jsonSupport.toJson(generated);
        cacheRepository.saveQuestions(tenantId, assistantMessageId, questionsJson);

        syncFollowUpPool(tenantId, generated);

        return ensureNonEmpty(fromJson(questionsJson, limit), limit);
    }

    /**
     * 主模型流式结束后立即启动追问 LLM（与联网后续轮 {@code join} 并行，缩短端到端等待）。
     */
    public CompletableFuture<List<String>> startFollowUpGeneration(
            long tenantId, String userQ, String assistantText) {
        String u = userQ == null ? "" : userQ.trim();
        String a = assistantText == null ? "" : assistantText.trim();
        if (a.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(
                () -> generateFollowUpQuestions(tenantId, u, a),
                command -> Thread.startVirtualThread(command));
    }

    /**
     * 流式收尾（即时）：不阻塞 SSE；若追问 LLM 已结束则用其结果，否则立即运营池/内置兜底，后台写完缓存供 REST 复用。
     */
    public ChatStarterPromptDtos.StarterPromptListView resolveForStreamEndImmediate(
            long tenantId,
            long assistantMessageId,
            CompletableFuture<List<String>> inflight,
            String userQ,
            String assistantText,
            int limit) {
        int cap = Math.max(1, Math.min(limit, 6));
        if (inflight != null) {
            if (inflight.isDone()) {
                try {
                    List<String> generated = inflight.get();
                    if (generated != null && !generated.isEmpty()) {
                        persistFollowUpCache(tenantId, assistantMessageId, generated);
                        syncFollowUpPool(tenantId, generated);
                        return fromJson(jsonSupport.toJson(generated), cap);
                    }
                } catch (Exception ex) {
                    log.debug(
                            "[推荐问题] 流式追问已完成但读取失败 assistantMessageId={}",
                            assistantMessageId,
                            ex);
                }
            } else {
                inflight.whenComplete(
                        (qs, ex) -> {
                            if (ex == null && qs != null && !qs.isEmpty()) {
                                persistFollowUpCache(tenantId, assistantMessageId, qs);
                                syncFollowUpPool(tenantId, qs);
                            }
                        });
                // 追问 LLM 未完成时不推送运营池占位，由前端骨架 + REST 拉取本轮结果
                return new ChatStarterPromptDtos.StarterPromptListView(List.of(), false);
            }
        }
        var local =
                starterPromptSimilarityService.pickForConversation(
                        tenantId, userQ, assistantText, cap);
        if (local.isPresent()) {
            return ensureNonEmpty(local.get(), cap);
        }
        if (assistantText == null || assistantText.isBlank()) {
            return fastFallback(tenantId, cap);
        }
        return new ChatStarterPromptDtos.StarterPromptListView(List.of(), false);
    }

    /**
     * 流式收尾：优先采用已完成的 LLM 结果（限时等待），否则运营池/内置兜底；超时后后台写完缓存供 REST 复用。
     */
    public ChatStarterPromptDtos.StarterPromptListView resolveForStreamEnd(
            long tenantId,
            long assistantMessageId,
            CompletableFuture<List<String>> inflight,
            int limit) {
        int cap = Math.max(1, Math.min(limit, 6));
        if (inflight != null) {
            try {
                List<String> generated = inflight.get(STREAM_FOLLOW_UP_WAIT_MS, TimeUnit.MILLISECONDS);
                if (!generated.isEmpty()) {
                    persistFollowUpCache(tenantId, assistantMessageId, generated);
                    syncFollowUpPool(tenantId, generated);
                    return fromJson(jsonSupport.toJson(generated), cap);
                }
            } catch (TimeoutException te) {
                inflight.whenComplete(
                        (qs, ex) -> {
                            if (ex == null && qs != null && !qs.isEmpty()) {
                                persistFollowUpCache(tenantId, assistantMessageId, qs);
                                syncFollowUpPool(tenantId, qs);
                            }
                        });
            } catch (Exception ex) {
                log.debug("[推荐问题] 流式追问等待失败 assistantMessageId={}", assistantMessageId, ex);
            }
        }
        return fastFallback(tenantId, cap);
    }

    private ChatStarterPromptDtos.StarterPromptListView fastFallback(long tenantId, int limit) {
        return ensureNonEmpty(
                starterPromptApplicationService.listForTenant(
                        tenantId,
                        null,
                        null,
                        ChatStarterPromptScene.FOLLOW_UP,
                        limit,
                        false,
                        null,
                        false,
                        false),
                limit);
    }

    private void scheduleFollowUpLlmCache(
            long tenantId, long assistantMessageId, String userQ, String assistantText) {
        Thread.startVirtualThread(
                () -> {
                    try {
                        List<String> generated =
                                generateFollowUpQuestions(tenantId, userQ, assistantText);
                        if (!generated.isEmpty()) {
                            persistFollowUpCache(tenantId, assistantMessageId, generated);
                            syncFollowUpPool(tenantId, generated);
                        }
                    } catch (Exception ex) {
                        log.debug(
                                "[推荐问题] 后台 LLM 追问缓存失败 assistantMessageId={}",
                                assistantMessageId,
                                ex);
                    }
                });
    }

    private void persistFollowUpCache(long tenantId, long assistantMessageId, List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        cacheRepository.saveQuestions(tenantId, assistantMessageId, jsonSupport.toJson(questions));
    }

    private ChatStarterPromptDtos.StarterPromptListView ensureNonEmpty(
            ChatStarterPromptDtos.StarterPromptListView view, int limit) {
        if (view != null && view.items() != null && !view.items().isEmpty()) {
            return view;
        }
        int cap = Math.max(1, Math.min(limit, 6));
        List<ChatStarterPromptDtos.StarterPromptItem> items = new ArrayList<>();
        for (int i = 0; i < Math.min(cap, BUILTIN_FOLLOW_UP_FALLBACK.size()); i++) {
            items.add(
                    new ChatStarterPromptDtos.StarterPromptItem(
                            null, BUILTIN_FOLLOW_UP_FALLBACK.get(i), "BUILTIN_FOLLOW_UP"));
        }
        return new ChatStarterPromptDtos.StarterPromptListView(items, false);
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
