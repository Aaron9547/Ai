package com.aaron.cloud.chat;

import com.aaron.cloud.chat.dto.ChatWorkflowSegmentView;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.intent.ChatIntentStreamRouter;
import com.aaron.cloud.chat.dto.ChatRegenerateRequest;
import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.dto.PriorAssistantVersionView;
import com.aaron.cloud.chat.dto.RagCitationView;
import com.aaron.cloud.chat.dto.LlmModelOption;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.ChatInputBlockReason;
import com.aaron.cloud.common.api.enums.ChatMessageRole;
import com.aaron.cloud.common.api.enums.ChatMessageUserFeedback;
import com.aaron.cloud.common.api.enums.ConversationRecordStatus;
import com.aaron.cloud.common.api.enums.IntentRoute;
import com.aaron.cloud.common.api.enums.LlmAnonymousAccess;
import com.aaron.cloud.common.api.enums.LlmThinkingCapability;
import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.RagQueryPort;
import com.aaron.cloud.common.chat.ChatAttachmentRepository;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.LnkChatConversationMessage;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagRetrievalHitCounter;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.modelcfg.quota.LlmTokenQuotaCoordinator;
import com.aaron.cloud.model.metering.LlmModelUsageRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final LlmTokenQuotaCoordinator llmTokenQuotaCoordinator;
    private final LlmModelUsageRecorder llmModelUsageRecorder;
    private final ModelInvokePort modelInvokePort;
    private final RagQueryPort ragQueryPort;
    private final RagRetrievalHitCounter ragRetrievalHitCounter;
    private final UserProfileApplicationService userProfileApplicationService;
    private final ChatInputGuardService chatInputGuardService;
    private final ObjectMapper objectMapper;
    private final SysTenantRepository sysTenantRepository;
    private final SecUserAccountRepository secUserAccountRepository;
    private final SysTenantMemberRepository sysTenantMemberRepository;
    private final AiRagProperties aiRagProperties;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final AiProvidersProperties aiProvidersProperties;
    private final ChatIntentStreamRouter chatIntentStreamRouter;

    public ChatConversation createConversation(String title) {
        var snap = TenantContextHolder.require();
        var c = new ChatConversation();
        c.setTenantId(snap.getTenantId());
        c.setUserId(snap.getUserId());
        c.setDeviceId(snap.getDeviceId());
        c.setTitle(title == null || title.isBlank() ? "新会话" : title);
        c.setStatus(ConversationRecordStatus.ACTIVE);
        conversationRepository.insert(c);
        return c;
    }

    public List<ChatConversation> listConversations() {
        var snap = TenantContextHolder.require();
        return conversationRepository.listForSubject(
                snap.getTenantId(), snap.getUserId(), snap.getDeviceId(), 50);
    }

    public List<ChatMessageView> listConversationMessages(long conversationId) {
        var snap = TenantContextHolder.require();
        var convOpt = conversationRepository.findById(conversationId, snap.getTenantId());
        if (convOpt.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        assertConversationAccess(convOpt.get());
        return listConversationMessagesInternal(snap.getTenantId(), conversationId);
    }

    /** 绠＄悊绔細鎸変細璇濈湡瀹炵鎴锋媺娑堟伅锛涢潪鍒涘浜轰粎鍏佽鏈細璇濇墍灞炵鎴枫€?*/
    public List<ChatMessageView> listConversationMessagesForAdmin(long conversationId) {
        var snap = TenantContextHolder.require();
        var conv =
                conversationRepository
                        .findByIdForAdmin(conversationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        var role = snap.getMemberRole();
        if (role == null || !role.isFounder()) {
            if (!conv.getTenantId().equals(snap.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该会话");
            }
        }
        return listConversationMessagesInternal(conv.getTenantId(), conversationId);
    }

    public Page<ChatConversation> listConversationsForAdmin(long page, long size, Long filterTenantId) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        Page<ChatConversation> p = conversationRepository.pageForAdmin(tid, page, size);
        if (p.getRecords().isEmpty()) {
            return p;
        }
        var tenantIds = p.getRecords().stream().map(ChatConversation::getTenantId).filter(Objects::nonNull).distinct().toList();
        var tenants = sysTenantRepository.mapTenantsByIds(tenantIds);
        var userIds =
                p.getRecords().stream().map(ChatConversation::getUserId).filter(Objects::nonNull).distinct().toList();
        var userLabels = secUserAccountRepository.mapUserDisplayLabelByIds(userIds);
        /** 浠呭綋浼氳瘽鎵€灞炵鎴蜂笅瀛樺湪鎴愬憳琛屾椂锛屾墠鍥炲～鍏ㄥ眬璐﹀彿灞曠ず鍚嶏紝閬垮厤璺ㄧ鎴疯鍏宠仈鍚屼竴 userId銆?*/
        Map<Long, Set<Long>> tenantToUserIds = new HashMap<>();
        for (ChatConversation c : p.getRecords()) {
            if (c.getTenantId() != null && c.getUserId() != null) {
                tenantToUserIds
                        .computeIfAbsent(c.getTenantId(), k -> new HashSet<>())
                        .add(c.getUserId());
            }
        }
        Map<String, Boolean> membershipOk = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : tenantToUserIds.entrySet()) {
            Set<Long> ok = sysTenantMemberRepository.listUserIdsHavingMembership(e.getKey(), e.getValue());
            for (Long uid : ok) {
                membershipOk.put(e.getKey() + ":" + uid, Boolean.TRUE);
            }
        }
        for (ChatConversation c : p.getRecords()) {
            if (c.getTenantId() != null) {
                var t = tenants.get(c.getTenantId());
                if (t != null) {
                    c.setTenantCode(t.getCode());
                    c.setTenantName(t.getName());
                }
            }
            if (c.getUserId() != null && c.getTenantId() != null) {
                if (Boolean.TRUE.equals(membershipOk.get(c.getTenantId() + ":" + c.getUserId()))) {
                    c.setUserDisplayName(userLabels.get(c.getUserId()));
                } else {
                    c.setUserDisplayName(null);
                }
            } else if (c.getUserId() != null) {
                c.setUserDisplayName(userLabels.get(c.getUserId()));
            }
            c.setTotalTokensInConversation(
                    sumAssistantTokensInConversation(c.getTenantId(), c.getId()));
        }
        return p;
    }

    /**
     * 鍔╂墜娑堟伅褰撳墠鐗堜笌鍘嗗彶绋?{@code meta.usage.totalTokens} 涔嬪拰锛堢鐞嗙鎶芥杩戜技鍊硷級銆傛秷鎭潯鏁拌繃澶ф椂璺宠繃鑱氬悎浠ュ厤鍒楄〃椤佃秴鏃躲€?     */
    private Integer sumAssistantTokensInConversation(long tenantId, long conversationId) {
        List<Long> ids = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (ids.size() > 400) {
            return null;
        }
        int sum = 0;
        for (ChatMessageView v : listConversationMessagesInternal(tenantId, conversationId)) {
            if (!"assistant".equals(v.role())) {
                continue;
            }
            if (v.totalTokens() != null && v.totalTokens() > 0) {
                sum += v.totalTokens();
            }
            if (v.priorVersions() != null) {
                for (PriorAssistantVersionView pv : v.priorVersions()) {
                    if (pv.totalTokens() != null && pv.totalTokens() > 0) {
                        sum += pv.totalTokens();
                    }
                }
            }
        }
        return sum > 0 ? sum : 0;
    }

    private List<ChatMessageView> listConversationMessagesInternal(long tenantId, long conversationId) {
        List<Long> ids = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return messageRepository.listByTenantAndIdsInOrder(tenantId, ids).stream()
                .map(this::toChatMessageView)
                .toList();
    }

    public List<LlmModelOption> listModelsForChatPicker() {
        var snap = TenantContextHolder.require();
        boolean guest = snap.getUserId() == null;
        List<LlmModelOption> out = new ArrayList<>();
        for (SysLlmModel m : llmModelRepository.listForCatalog(snap.getTenantId(), guest)) {
            out.add(
                    new LlmModelOption(
                            m.getAlias(),
                            m.getDisplayName(),
                            m.getMaxAttachments(),
                            m.getSupportsThinking() == LlmThinkingCapability.SUPPORTED,
                            m.getAllowAnonymous() == LlmAnonymousAccess.ALLOWED,
                            llmTokenQuotaCoordinator.isQuotaExhaustedForCatalog(m)));
        }
        return out;
    }

    public SseEmitter streamUserMessage(long conversationId, ChatSendPayload payload) {
        var snap = TenantContextHolder.require();
        var convOpt = conversationRepository.findById(conversationId, snap.getTenantId());
        if (convOpt.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        assertConversationAccess(convOpt.get());
        Optional<ChatInputGuardService.InputGuardOutcome> blocked =
                chatInputGuardService.evaluate(snap.getTenantId(), payload.getContent());
        if (blocked.isPresent()) {
            log.warn(
                    "chat input blocked tenantId={} conversationId={} reason={} guardRule={}",
                    snap.getTenantId(),
                    conversationId,
                    blocked.get().reason(),
                    blocked.get().guardrailRuleName());
            return streamInputGuardRejected(conversationId, snap, payload, blocked.get());
        }

        boolean isMock = "mock".equalsIgnoreCase(payload.getModelAlias().trim());
        final SysLlmModel modelCfg;
        int maxAttachmentsAllowed = 10;
        if (!isMock) {
            SysLlmModel m = resolveModelForSend(snap.getTenantId(), snap.getUserId(), payload.getModelAlias());
            maxAttachmentsAllowed = m.getMaxAttachments() == null ? 10 : m.getMaxAttachments();
            llmTokenQuotaCoordinator.assertQuotaAllowsSend(m);
            modelCfg = m;
        } else {
            modelCfg = null;
        }

        List<Long> attIds = payload.getAttachmentIds() == null ? List.of() : payload.getAttachmentIds();
        if (attIds.size() > maxAttachmentsAllowed) {
            throw new IllegalArgumentException("附件数量超过该模型允许上限：" + maxAttachmentsAllowed);
        }
        List<ChatAttachment> attachments = attachmentRepository.listByIds(snap.getTenantId(), conversationId, attIds);
        if (attachments.size() != new LinkedHashSet<>(attIds).size()) {
            throw new IllegalArgumentException("附件不存在或不属于当前会话");
        }

        String augmentedUserText = buildUserMessageWithAttachments(payload.getContent(), attachments);

        var userMsg = new ChatMessage();
        userMsg.setTenantId(snap.getTenantId());
        userMsg.setRole(ChatMessageRole.USER);
        userMsg.setContent(payload.getContent());
        userMsg.setMetaJson(buildUserMetaJson(payload, attIds));
        messageRepository.insert(userMsg);
        linkMessage(conversationId, userMsg.getId(), snap.getTenantId());
        maybeRenameConversationFromFirstUserMessage(
                convOpt.get(), conversationId, payload.getContent());

        userProfileApplicationService.ingestAfterUserUtterance(snap, payload.getContent());

        var intentEmitter = chatIntentStreamRouter.maybeRouteIntentStream(conversationId, snap, payload, attachments);
        if (intentEmitter.isPresent()) {
            return intentEmitter.get();
        }

        return openAssistantSseStream(
                conversationId, snap, payload, augmentedUserText, modelCfg, isMock, attachments, null);
    }

    /**
     * 自意图路由组装 turns、调用模型并以 SSE 下发，最后落库助手消息（调用方已插入 user 行或重试场景下不再插入 user）。
     *
     * @param priorAssistantVersions 非 null 且非空时写入助手 meta {@code priorVersions}（重新生成链）
     */
    private SseEmitter openAssistantSseStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            String augmentedUserText,
            SysLlmModel modelCfg,
            boolean isMock,
            List<ChatAttachment> attachments,
            ArrayNode priorAssistantVersions) {
        // 未接 Milvus（local 占位）时：对话侧等同无 RAG，不调检索、不注入片段，用户端无报错。
        // 仅拉取 rag_knowledge_base.chat_retrieval_enabled=ON 的知识库；无开启库时不做 RAG 检索。
        final List<Long> chatRagKbIds =
                aiProvidersProperties.resolvedVectorStore() == VectorStoreProviderMode.milvus
                        ? ragKnowledgeBaseRepository.listIdsWithChatRetrievalEnabled(snap.getTenantId())
                        : List.of();
        IntentRoute baseIntent = chatRagKbIds.isEmpty() ? IntentRoute.CHAT_ONLY : IntentRoute.RAG;
        String ragLexicalQuery = buildRagLexicalSearchQuery(payload, augmentedUserText);
        IntentRoute routeIntent =
                baseIntent == IntentRoute.RAG && ragLexicalQuery.isBlank()
                        ? IntentRoute.CHAT_ONLY
                        : baseIntent;
        List<RagCitationHit> ragCitationHits = List.of();
        List<String> ragSnippets = List.of();
        if (routeIntent == IntentRoute.RAG) {
            ragCitationHits =
                    List.copyOf(
                            ragQueryPort.searchCitationHitsAcrossKnowledgeBases(
                                    snap.getTenantId(), chatRagKbIds, ragLexicalQuery, 3));
            ragSnippets =
                    List.copyOf(
                            ragQueryPort.searchSnippetsAcrossKnowledgeBases(
                                    snap.getTenantId(), chatRagKbIds, ragLexicalQuery, 3));
            // 向量阈值过滤或 ES 未命中后可能两侧皆空：本回合按纯对话编排，避免落库/展示无实质检索的「挂名引用」。
            if (ragCitationHits.isEmpty() && ragSnippets.isEmpty()) {
                routeIntent = IntentRoute.CHAT_ONLY;
            }
        }
        final IntentRoute intent = routeIntent;
        final List<RagCitationHit> ragHitsForStream = ragCitationHits;
        log.info(
                "chatOpenStream start conversationId={} tenantId={} userId={} deviceIdPresent={} intent={} vectorStore={} retrievalMode={} chatRagKbCount={} userTextChars={} ragLexicalChars={} ragSnippetCount={} ragCitationCount={}",
                conversationId,
                snap.getTenantId(),
                snap.getUserId(),
                snap.getDeviceId() != null && !snap.getDeviceId().isBlank(),
                intent,
                aiProvidersProperties.resolvedVectorStore(),
                aiRagProperties.resolvedRetrievalMode(),
                chatRagKbIds.size(),
                payload.getContent() != null ? payload.getContent().length() : 0,
                ragLexicalQuery.length(),
                ragSnippets.size(),
                ragCitationHits.size());
        List<ModelChatRequest.MessageTurn> turns = new ArrayList<>();
        // 画像为 ten_profile_tag 跨会话累计与最近摘要，非本会话消息列表；具体措辞见 UserProfileApplicationService.buildPromptAddendum
        String profileAddendum = userProfileApplicationService.buildPromptAddendum(snap);
        StringBuilder sys = new StringBuilder();
        if (!profileAddendum.isBlank()) {
            sys.append("【用户画像（跨会话统计，非本会话消息条数）】\n").append(profileAddendum).append("\n\n");
        }
        if (intent == IntentRoute.RAG) {
            sys.append("你是助手。可参考片段：");
            for (String s : ragSnippets) {
                sys.append("\n- ").append(s);
            }
            var sysTurn = new ModelChatRequest.MessageTurn();
            sysTurn.setRole("system");
            sysTurn.setContent(sys.toString());
            turns.add(sysTurn);
        } else {
            sys.append("你是 Ai 中台助手。");
            var sysTurn = new ModelChatRequest.MessageTurn();
            sysTurn.setRole("system");
            sysTurn.setContent(sys.toString());
            turns.add(sysTurn);
        }
        if (!attachments.isEmpty()) {
            var attSys = new ModelChatRequest.MessageTurn();
            attSys.setRole("system");
            attSys.setContent(buildAttachmentContextBlock(attachments));
            turns.add(attSys);
        }
        var userTurn = new ModelChatRequest.MessageTurn();
        userTurn.setRole("user");
        userTurn.setContent(augmentedUserText);
        turns.add(userTurn);

        var modelReq = new ModelChatRequest();
        modelReq.setTenantId(snap.getTenantId());
        modelReq.setUserId(snap.getUserId());
        modelReq.setDeviceId(snap.getDeviceId());
        modelReq.setModelAlias(payload.getModelAlias().trim());
        modelReq.setThinkingEnabled(!isMock && shouldStreamThinking(payload, modelCfg));
        modelReq.setMessages(turns);

        AtomicReference<ModelTokenUsage> usageRef = new AtomicReference<>();
        if (!isMock && modelCfg != null) {
            modelReq.setStreamUsageConsumer(usageRef::set);
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        StringBuilder assistantBuf = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        AtomicInteger seq = new AtomicInteger(0);
        if (Boolean.TRUE.equals(modelReq.getThinkingEnabled())) {
            modelReq.setReasoningTokenConsumer(
                    t -> {
                        reasoningBuf.append(t);
                        try {
                            emitter.send(
                                    org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .data(sseChunk("reasoning", t))
                                            .id(String.valueOf(seq.incrementAndGet())));
                        } catch (Exception e) {
                            log.warn("sse reasoning send failed", e);
                        }
                    });
        }

        Runnable run =
                () -> {
                    try {
                        long streamStartedAt = System.currentTimeMillis();
                        sendSseRagDocTitleFrames(emitter, seq, ragHitsForStream);
                        modelInvokePort.streamCompletion(
                                modelReq,
                                token -> {
                                    try {
                                        assistantBuf.append(token);
                                        emitter.send(
                                                org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                                                        .event()
                                                        .data(sseChunk("content", token))
                                                        .id(String.valueOf(seq.incrementAndGet())));
                                    } catch (Exception e) {
                                        log.error(
                                                "SSE send token failed conversationId={} tenantId={} modelAlias={} llmModelId={} seq={}",
                                                conversationId,
                                                snap.getTenantId(),
                                                payload.getModelAlias(),
                                                modelCfg != null ? modelCfg.getId() : null,
                                                seq.get(),
                                                e);
                                        emitter.completeWithError(e);
                                    }
                                });
                        long durationMs = System.currentTimeMillis() - streamStartedAt;
                        if (!isMock && modelCfg != null) {
                            llmModelUsageRecorder.recordAfterLlmUsage(
                                    snap,
                                    modelCfg,
                                    payload.getModelAlias().trim(),
                                    conversationId,
                                    usageRef.get(),
                                    durationMs);
                        }
                        emitter.send(
                                org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                        .data(sseEndPayload(usageRef.get()))
                                        .id(String.valueOf(seq.incrementAndGet())));
                        var asst = new ChatMessage();
                        asst.setTenantId(snap.getTenantId());
                        asst.setRole(ChatMessageRole.ASSISTANT);
                        asst.setContent(assistantBuf.toString());
                        asst.setMetaJson(
                                buildAssistantMetaJson(
                                        payload,
                                        payload.getModelAlias().trim(),
                                        reasoningBuf.toString(),
                                        usageRef.get(),
                                        priorAssistantVersions,
                                        ragHitsForStream));
                        messageRepository.insert(asst);
                        linkMessage(conversationId, asst.getId(), snap.getTenantId());
                        ragRetrievalHitCounter.recordHits(snap.getTenantId(), ragHitsForStream);
                        log.info(
                                "chatOpenStream completed conversationId={} tenantId={} durationMs={} intent={} assistantChars={} usageTotalTokens={}",
                                conversationId,
                                snap.getTenantId(),
                                durationMs,
                                intent,
                                assistantBuf.length(),
                                usageRef.get() != null ? usageRef.get().totalTokens() : 0);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error(
                                "stream completion failed conversationId={} tenantId={} userId={} modelAlias={} mock={} llmModelId={} intent={} thinking={} msgTurns={} userTextChars={}",
                                conversationId,
                                snap.getTenantId(),
                                snap.getUserId(),
                                payload.getModelAlias(),
                                isMock,
                                modelCfg != null ? modelCfg.getId() : null,
                                intent,
                                payload.isThinkingEnabled(),
                                modelReq.getMessages() != null ? modelReq.getMessages().size() : 0,
                                payload.getContent() != null ? payload.getContent().length() : 0,
                                e);
                        // 不向 Dispatcher 抛错：Accept 为 text/event-stream 时 completeWithError 会触发
                        // GlobalExceptionHandler 写 JSON，导致 HttpMediaTypeNotAcceptableException。
                        String hint =
                                e.getMessage() == null || e.getMessage().isBlank()
                                        ? "模型调用失败"
                                        : e.getMessage();
                        try {
                            emitter.send(
                                    SseEmitter.event()
                                            .data(sseChunk("content", "\n\n（调用失败）" + hint))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.send(
                                    SseEmitter.event()
                                            .data(sseEndPayload(usageRef.get()))
                                            .id(String.valueOf(seq.incrementAndGet())));
                        } catch (Exception sendEx) {
                            log.warn("sse error frame send failed", sendEx);
                        }
                        emitter.complete();
                    }
                };
        Thread.startVirtualThread(run);
        return emitter;
    }

    /**
     * 鍒犻櫎浼氳瘽涓渶鍚庝竴鏉″姪鎵嬫秷鎭苟鍩轰簬鍏跺墠涓€鏉＄敤鎴锋秷鎭噸鏂版祦寮忕敓鎴愶紙涓嶉噸澶嶆彃鍏?user 琛岋級銆?     *
     * <p>鍙€夎姹備綋瑕嗙洊妯″瀷涓庢€濊€冨紑鍏筹紱鏃у姪鎵嬬閾惧啓鍏ユ柊鍔╂墜 meta {@code priorVersions}銆?     *
     * <p>妯″瀷渚?user 杞浼氬湪銆屾垜鐨勯棶棰樻槸锛氥€嶅墠闄勫姞涓€娈甸噸鏂扮敓鎴愬紩瀵艰锛堣 {@link #buildRegenerateUserPromptForModel}锛夛紝鐢ㄦ埛娑堟伅搴撹〃
     * {@code content} 浠嶄繚鐣欏師鏂囷紝閬垮厤澶氭閲嶈瘯鍙犲姞銆?     */
    public SseEmitter regenerateAssistantStream(
            long conversationId, long assistantMessageId, ChatRegenerateRequest regenerateOpts) {
        var snap = TenantContextHolder.require();
        var convOpt = conversationRepository.findById(conversationId, snap.getTenantId());
        if (convOpt.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        assertConversationAccess(convOpt.get());
        List<Long> ids = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (ids.size() < 2) {
            throw new IllegalArgumentException("会话消息不足，无法重新生成");
        }
        long lastId = ids.get(ids.size() - 1);
        if (lastId != assistantMessageId) {
            throw new IllegalArgumentException("只能重新生成会话最后一条助手回复");
        }
        long userMsgId = ids.get(ids.size() - 2);
        ChatMessage asst =
                messageRepository
                        .findById(assistantMessageId, snap.getTenantId())
                        .orElseThrow(() -> new IllegalArgumentException("message not found"));
        ChatMessage user =
                messageRepository
                        .findById(userMsgId, snap.getTenantId())
                        .orElseThrow(() -> new IllegalArgumentException("message not found"));
        if (asst.getRole() != ChatMessageRole.ASSISTANT || user.getRole() != ChatMessageRole.USER) {
            throw new IllegalArgumentException("消息顺序无效，无法重新生成");
        }
        ChatSendPayload payload = parseChatSendFromStoredUser(user);
        applyRegenerateOverrides(payload, regenerateOpts);
        Optional<ChatInputGuardService.InputGuardOutcome> blocked =
                chatInputGuardService.evaluate(snap.getTenantId(), payload.getContent());
        if (blocked.isPresent()) {
            throw new IllegalArgumentException("该条用户内容已不再允许发送至模型，无法重新生成");
        }

        ArrayNode priorChain = buildPriorVersionsChainBeforeReplace(asst);

        boolean isMock = "mock".equalsIgnoreCase(payload.getModelAlias().trim());
        final SysLlmModel modelCfg;
        int maxAttachmentsAllowed = 10;
        if (!isMock) {
            SysLlmModel m = resolveModelForSend(snap.getTenantId(), snap.getUserId(), payload.getModelAlias());
            maxAttachmentsAllowed = m.getMaxAttachments() == null ? 10 : m.getMaxAttachments();
            llmTokenQuotaCoordinator.assertQuotaAllowsSend(m);
            modelCfg = m;
        } else {
            modelCfg = null;
        }

        List<Long> attIds = payload.getAttachmentIds() == null ? List.of() : payload.getAttachmentIds();
        if (attIds.size() > maxAttachmentsAllowed) {
            throw new IllegalArgumentException("附件数量超过该模型允许上限：" + maxAttachmentsAllowed);
        }
        List<ChatAttachment> attachments = attachmentRepository.listByIds(snap.getTenantId(), conversationId, attIds);
        if (attachments.size() != new LinkedHashSet<>(attIds).size()) {
            throw new IllegalArgumentException("附件不存在或不属于当前会话");
        }
        // 仅本轮模型请求使用；不落库覆盖用户行 content，避免多次重试叠加引导语。
        String augmentedUserText = buildRegenerateUserPromptForModel(payload.getContent(), attachments);

        lnkRepository.deleteByConversationIdAndMessageId(conversationId, assistantMessageId);
        messageRepository.deleteById(assistantMessageId, snap.getTenantId());

        return openAssistantSseStream(
                conversationId, snap, payload, augmentedUserText, modelCfg, isMock, attachments, priorChain);
    }

    private static void applyRegenerateOverrides(ChatSendPayload payload, ChatRegenerateRequest regenerateOpts) {
        if (regenerateOpts == null) {
            return;
        }
        if (regenerateOpts.getModelAlias() != null && !regenerateOpts.getModelAlias().isBlank()) {
            payload.setModelAlias(regenerateOpts.getModelAlias().trim());
        }
        if (regenerateOpts.getThinkingEnabled() != null) {
            payload.setThinkingEnabled(Boolean.TRUE.equals(regenerateOpts.getThinkingEnabled()));
        }
    }

    /** 鍒犻櫎褰撳墠鍔╂墜琛屽墠锛屾妸宸叉湁 priorVersions 涓庡綋鍓嶇蹇収涓叉垚閾惧啓鍏ヤ笅涓€杞姪鎵?meta銆?*/
    private ArrayNode buildPriorVersionsChainBeforeReplace(ChatMessage asst) {
        ArrayNode chain = objectMapper.createArrayNode();
        if (asst.getMetaJson() != null && !asst.getMetaJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(asst.getMetaJson());
                if (root.has("priorVersions") && root.get("priorVersions").isArray()) {
                    for (JsonNode x : root.get("priorVersions")) {
                        chain.add(x.deepCopy());
                    }
                }
            } catch (Exception e) {
                log.warn("failed to read priorVersions from assistant meta messageId={}", asst.getId(), e);
            }
        }
        ObjectNode snap = objectMapper.createObjectNode();
        snap.put("content", asst.getContent() == null ? "" : asst.getContent());
        if (asst.getMetaJson() != null && !asst.getMetaJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(asst.getMetaJson());
                if (root.has("modelAlias") && root.get("modelAlias").isTextual()) {
                    snap.put("modelAlias", root.get("modelAlias").asText());
                }
                if (root.has("reasoning") && root.get("reasoning").isTextual()) {
                    String r = root.get("reasoning").asText();
                    if (!r.isBlank()) {
                        snap.put("reasoning", r);
                    }
                }
                JsonNode usage = root.path("usage");
                if (usage.isObject() && !usage.isMissingNode()) {
                    snap.set("usage", usage.deepCopy());
                }
                if (root.has("ragCitations") && root.get("ragCitations").isArray()) {
                    snap.set("ragCitations", root.get("ragCitations").deepCopy());
                }
            } catch (Exception e) {
                log.warn("failed to parse assistant meta for prior snapshot messageId={}", asst.getId(), e);
            }
        }
        chain.add(snap);
        return chain;
    }

    private ChatSendPayload parseChatSendFromStoredUser(ChatMessage user) {
        ChatSendPayload p = new ChatSendPayload();
        p.setContent(user.getContent());
        if (user.getMetaJson() == null || user.getMetaJson().isBlank()) {
            throw new IllegalArgumentException("缺少发送上下文元数据，无法重试");
        }
        try {
            JsonNode n = objectMapper.readTree(user.getMetaJson());
            if (!n.path("modelAlias").isTextual() || n.path("modelAlias").asText().isBlank()) {
                throw new IllegalArgumentException("缺少 modelAlias，无法重新生成");
            }
            p.setModelAlias(n.path("modelAlias").asText());
            p.setThinkingEnabled(n.path("thinkingEnabled").asBoolean(false));
            List<Long> att = new ArrayList<>();
            if (n.has("attachmentIds") && n.get("attachmentIds").isArray()) {
                for (JsonNode x : n.get("attachmentIds")) {
                    if (x.isIntegralNumber()) {
                        att.add(x.longValue());
                    }
                }
            }
            p.setAttachmentIds(att);
            return p;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析用户消息元数据", e);
        }
    }

    /**
     * 杈撳叆鎶ゆ爮鍛戒腑锛氫粛钀藉簱<strong>鐢ㄦ埛鍘熸枃</strong>锛堜究浜庝細璇濈暀瀛樹笌瀹¤锛夛紝{@code meta_json} 鍚?{@code inputGuardBlocked}锛涗笉璋冪敤妯″瀷锛屼粎杩藉姞鍥哄畾鍔濆璇姪鎵嬭锛屽苟浠?     * SSE 涓嬪彂锛堝惈 {@code inputBlocked} 甯т究浜庡墠绔尯鍒嗭級銆傞噸鏂扮敓鎴愭椂浼氬璇ョ敤鎴疯鍐嶆璺戞姢鏍忥紝浠嶅懡涓垯鎷掔粷銆?     */
    private SseEmitter streamInputGuardRejected(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            ChatInputGuardService.InputGuardOutcome outcome) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String template = chatInputGuardService.blockedReplyTemplate();
        AtomicInteger seq = new AtomicInteger(0);
        Runnable run =
                () -> {
                    try {
                        var userMsg = new ChatMessage();
                        userMsg.setTenantId(snap.getTenantId());
                        userMsg.setRole(ChatMessageRole.USER);
                        userMsg.setContent(payload.getContent() == null ? "" : payload.getContent());
                        userMsg.setMetaJson(buildBlockedUserMetaJson(payload, outcome));
                        messageRepository.insert(userMsg);
                        linkMessage(conversationId, userMsg.getId(), snap.getTenantId());

                        var asst = new ChatMessage();
                        asst.setTenantId(snap.getTenantId());
                        asst.setRole(ChatMessageRole.ASSISTANT);
                        asst.setContent(template);
                        asst.setMetaJson(buildAssistantGuardTemplateMeta(payload.getModelAlias().trim()));
                        messageRepository.insert(asst);
                        linkMessage(conversationId, asst.getId(), snap.getTenantId());

                        emitter.send(
                                SseEmitter.event()
                                        .data(sseInputBlockedFrame(outcome.reason()))
                                        .id(String.valueOf(seq.incrementAndGet())));
                        emitter.send(
                                SseEmitter.event()
                                        .data(sseChunk("content", template))
                                        .id(String.valueOf(seq.incrementAndGet())));
                        emitter.send(
                                SseEmitter.event()
                                        .data(sseEndPayload(null))
                                        .id(String.valueOf(seq.incrementAndGet())));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error(
                                "input guard sse persist/send failed conversationId={} tenantId={} reason={}",
                                conversationId,
                                snap.getTenantId(),
                                outcome.reason(),
                                e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception completeEx) {
                            log.warn("emitter completeWithError failed", completeEx);
                            emitter.complete();
                        }
                    }
                };
        Thread.startVirtualThread(run);
        return emitter;
    }

    /**
     * 鍔╂墜娑堟伅鐢ㄦ埛璇勪环锛堝啓鍏?{@code meta_json#userFeedback}锛寋@link ChatMessageUserFeedback#LIKE} /
     * {@link ChatMessageUserFeedback#DISLIKE} 浜掓枼锛夛紱浠呮湰浼氳瘽鍐呭姪鎵嬭鍙啓銆?     */
    public void setAssistantMessageFeedback(long conversationId, long messageId, ChatMessageUserFeedback vote) {
        var snap = TenantContextHolder.require();
        var convOpt = conversationRepository.findById(conversationId, snap.getTenantId());
        if (convOpt.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        assertConversationAccess(convOpt.get());
        if (!lnkRepository.existsMessageInConversation(conversationId, messageId)) {
            throw new IllegalArgumentException("message not found in conversation");
        }
        ChatMessage m =
                messageRepository
                        .findById(messageId, snap.getTenantId())
                        .orElseThrow(() -> new IllegalArgumentException("message not found"));
        if (m.getRole() != ChatMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("only assistant messages support feedback");
        }
        ObjectNode root = readAssistantMetaForUpdate(m.getMetaJson(), messageId);
        if (vote == ChatMessageUserFeedback.NONE) {
            root.remove("userFeedback");
        } else {
            root.put("userFeedback", vote.name());
        }
        try {
            messageRepository.updateMetaJson(messageId, snap.getTenantId(), objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode readAssistantMetaForUpdate(String metaJson, long messageId) {
        if (metaJson == null || metaJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode tree = objectMapper.readTree(metaJson);
            if (tree instanceof ObjectNode on) {
                return on;
            }
        } catch (Exception e) {
            log.warn("malformed assistant metaJson, reset messageId={}", messageId, e);
        }
        return objectMapper.createObjectNode();
    }

    private String sseInputBlockedFrame(ChatInputBlockReason reason) throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", "inputBlocked");
        o.put("reason", reason.name());
        return objectMapper.writeValueAsString(o);
    }

    private String buildBlockedUserMetaJson(ChatSendPayload payload, ChatInputGuardService.InputGuardOutcome outcome)
            throws JsonProcessingException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("modelAlias", payload.getModelAlias());
        n.put("thinkingEnabled", payload.isThinkingEnabled());
        n.put("inputGuardBlocked", true);
        n.put("inputGuardReason", outcome.reason().name());
        if (outcome.guardrailRuleName() != null && !outcome.guardrailRuleName().isBlank()) {
            n.put("inputGuardRuleName", outcome.guardrailRuleName());
        }
        n.putArray("attachmentIds");
        return objectMapper.writeValueAsString(n);
    }

    private String buildAssistantGuardTemplateMeta(String modelAlias) throws JsonProcessingException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("modelAlias", modelAlias);
        n.put("inputGuardTemplate", true);
        return objectMapper.writeValueAsString(n);
    }

    /**
     * 鍦ㄦā鍨?token 娴佷箣鍓嶆寜鍛戒腑椤哄簭閫愭潯涓嬪彂寮曠敤鏂囨。鏍囬锛圝SON 鍦?{@code v} 鍐咃級锛屽抚闂寸煭闂撮殧渚夸簬 C 绔劅鐭ユ绱㈣繘搴︺€?     */
    private void sendSseRagDocTitleFrames(
            SseEmitter emitter, AtomicInteger seq, List<RagCitationHit> ragHitsForStream) {
        if (ragHitsForStream == null || ragHitsForStream.isEmpty()) {
            return;
        }
        Map<Long, String> byDoc = new LinkedHashMap<>();
        for (RagCitationHit h : ragHitsForStream) {
            String t = h.documentTitle();
            if (t == null || t.isBlank()) {
                t = "鏂囨。 " + h.documentId();
            }
            byDoc.putIfAbsent(h.documentId(), t);
        }
        for (Map.Entry<Long, String> e : byDoc.entrySet()) {
            try {
                ObjectNode doc = objectMapper.createObjectNode();
                doc.put("documentId", e.getKey());
                doc.put("title", e.getValue());
                emitter.send(
                        SseEmitter.event()
                                .data(sseChunk("ragDoc", objectMapper.writeValueAsString(doc)))
                                .id(String.valueOf(seq.incrementAndGet())));
            } catch (Exception ex) {
                log.warn("sse ragDoc frame send failed seq={}", seq.get(), ex);
                break;
            }
            try {
                Thread.sleep(42);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String sseChunk(String type, String value) throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", type);
        o.put("v", value == null ? "" : value);
        return objectMapper.writeValueAsString(o);
    }

    private String sseEndPayload(ModelTokenUsage usage) throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", "end");
        if (usage != null && usage.totalTokens() > 0) {
            ObjectNode u = o.putObject("usage");
            u.put("promptTokens", usage.promptTokens());
            u.put("completionTokens", usage.completionTokens());
            u.put("totalTokens", usage.totalTokens());
        }
        return objectMapper.writeValueAsString(o);
    }

    /**
     * 浼氳瘽鏍囬浠嶄负榛樿鍗犱綅锛堝銆屾柊浼氳瘽銆嶃€屾柊瀵硅瘽 鈥︺€嶏級涓旀湰浼氳瘽浠呮湁鍒氭彃鍏ョ殑涓€鏉℃秷鎭椂锛岀敤鐢ㄦ埛棣栨潯闂鐢熸垚鏍囬銆?     */
    private void maybeRenameConversationFromFirstUserMessage(
            ChatConversation conv, long conversationId, String plainUserText) {
        if (!isDefaultConversationTitle(conv.getTitle())) {
            return;
        }
        int n = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId).size();
        if (n != 1) {
            return;
        }
        String newTitle = titleFromFirstUserQuestion(plainUserText);
        conversationRepository.updateTitle(conversationId, conv.getTenantId(), newTitle);
    }

    private static boolean isDefaultConversationTitle(String title) {
        if (title == null) {
            return true;
        }
        String t = title.trim();
        if (t.isEmpty()) {
            return true;
        }
        if ("新会话".equals(t)) {
            return true;
        }
        return t.startsWith("新对话");
    }

    private static String titleFromFirstUserQuestion(String raw) {
        if (raw == null) {
            return "新会话";
        }
        String normalized = raw.replace('\r', '\n');
        int nl = normalized.indexOf('\n');
        String firstLine = (nl >= 0 ? normalized.substring(0, nl) : normalized).trim();
        if (firstLine.isEmpty()) {
            return "新会话";
        }
        int max = 120;
        if (firstLine.length() > max) {
            return firstLine.substring(0, max) + "…";
        }
        return firstLine;
    }

    private void assertConversationAccess(ChatConversation conv) {
        var snap = TenantContextHolder.require();
        if (!snap.getTenantId().equals(conv.getTenantId())) {
            throw new IllegalArgumentException("conversation not found");
        }
        Long uid = snap.getUserId();
        String did = snap.getDeviceId();
        if (uid != null) {
            if (!uid.equals(conv.getUserId())) {
                throw new IllegalArgumentException("conversation not found");
            }
        } else {
            if (conv.getUserId() != null) {
                throw new IllegalArgumentException("conversation not found");
            }
            if (did == null || did.isBlank() || !did.equals(conv.getDeviceId())) {
                throw new IllegalArgumentException("conversation not found");
            }
        }
    }

    private ChatMessageView toChatMessageView(ChatMessage m) {
        String role =
                switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    default -> "system";
                };
        String reasoning = null;
        Integer pt = null;
        Integer ct = null;
        Integer tt = null;
        String modelAlias = null;
        String userFeedback = null;
        List<PriorAssistantVersionView> priorVersions = null;
        List<RagCitationView> ragCitations = null;
        List<ChatWorkflowSegmentView> workflowSegments = null;
        if (m.getRole() == ChatMessageRole.ASSISTANT
                && m.getMetaJson() != null
                && !m.getMetaJson().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(m.getMetaJson());
                if (root.has("reasoning") && root.get("reasoning").isTextual()) {
                    reasoning = root.get("reasoning").asText();
                }
                JsonNode usage = root.path("usage");
                if (usage.isObject() && !usage.isMissingNode()) {
                    pt = usage.has("promptTokens") ? usage.get("promptTokens").asInt() : null;
                    ct = usage.has("completionTokens") ? usage.get("completionTokens").asInt() : null;
                    tt = usage.has("totalTokens") ? usage.get("totalTokens").asInt() : null;
                }
                if (root.has("modelAlias") && root.get("modelAlias").isTextual()) {
                    modelAlias = root.get("modelAlias").asText();
                }
                if (root.has("userFeedback") && root.get("userFeedback").isTextual()) {
                    String uf = root.get("userFeedback").asText();
                    if (ChatMessageUserFeedback.LIKE.name().equals(uf)
                            || ChatMessageUserFeedback.DISLIKE.name().equals(uf)) {
                        userFeedback = uf;
                    }
                }
                if (root.has("priorVersions") && root.get("priorVersions").isArray()) {
                    List<PriorAssistantVersionView> list = new ArrayList<>();
                    for (JsonNode x : root.get("priorVersions")) {
                        PriorAssistantVersionView pv = parsePriorAssistantVersionSnapshot(x);
                        if (pv != null) {
                            list.add(pv);
                        }
                    }
                    if (!list.isEmpty()) {
                        priorVersions = List.copyOf(list);
                    }
                }
                if (root.has("ragCitations") && root.get("ragCitations").isArray()) {
                    List<RagCitationView> rc = new ArrayList<>();
                    for (JsonNode c : root.get("ragCitations")) {
                        if (c == null || !c.isObject()) {
                            continue;
                        }
                        rc.add(
                                new RagCitationView(
                                        c.path("kbId").asLong(0L),
                                        c.path("documentId").asLong(0L),
                                        c.path("documentTitle").asText(""),
                                        c.path("chunkId").asLong(0L),
                                        c.path("chunkSeq").asInt(0),
                                        c.path("contentPreview").asText("")));
                    }
                    if (!rc.isEmpty()) {
                        ragCitations = List.copyOf(rc);
                    }
                }
                if (root.has("workflowSegments") && root.get("workflowSegments").isArray()) {
                    List<ChatWorkflowSegmentView> ws = new ArrayList<>();
                    for (JsonNode s : root.get("workflowSegments")) {
                        if (s == null || !s.isObject()) {
                            continue;
                        }
                        String t = null;
                        if (s.has("title") && s.get("title").isTextual()) {
                            t = s.get("title").asText();
                        }
                        ws.add(
                                new ChatWorkflowSegmentView(
                                        s.path("segmentId").asText(""),
                                        t,
                                        s.path("mode").asText("block"),
                                        s.path("status").asText("done"),
                                        s.path("text").asText("")));
                    }
                    if (!ws.isEmpty()) {
                        workflowSegments = List.copyOf(ws);
                    }
                }
            } catch (Exception ex) {
                log.warn("chat message meta parse failed messageId={}", m.getId(), ex);
            }
        }
        return new ChatMessageView(
                m.getId(),
                role,
                m.getContent(),
                reasoning,
                pt,
                ct,
                tt,
                m.getCreatedAt(),
                modelAlias,
                userFeedback,
                priorVersions,
                ragCitations,
                workflowSegments);
    }

    private static PriorAssistantVersionView parsePriorAssistantVersionSnapshot(JsonNode x) {
        if (x == null || !x.isObject()) {
            return null;
        }
        String content = x.has("content") && x.get("content").isTextual() ? x.get("content").asText() : "";
        String rsn = x.has("reasoning") && x.get("reasoning").isTextual() ? x.get("reasoning").asText() : null;
        String ma = x.has("modelAlias") && x.get("modelAlias").isTextual() ? x.get("modelAlias").asText() : null;
        Integer pt = null;
        Integer ct = null;
        Integer tt = null;
        JsonNode usage = x.path("usage");
        if (usage.isObject() && !usage.isMissingNode()) {
            pt = usage.has("promptTokens") ? usage.get("promptTokens").asInt() : null;
            ct = usage.has("completionTokens") ? usage.get("completionTokens").asInt() : null;
            tt = usage.has("totalTokens") ? usage.get("totalTokens").asInt() : null;
        }
        return new PriorAssistantVersionView(content, rsn, ma, pt, ct, tt);
    }

    private static boolean shouldStreamThinking(ChatSendPayload payload, SysLlmModel modelCfg) {
        if (modelCfg == null) {
            return false;
        }
        if (modelCfg.getSupportsThinking() != LlmThinkingCapability.SUPPORTED) {
            return false;
        }
        return payload.isThinkingEnabled();
    }

    private SysLlmModel resolveModelForSend(long tenantId, Long userId, String alias) {
        var opt = llmModelRepository.findByTenantAndAlias(tenantId, alias.trim());
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("模型不存在或未启用：" + alias);
        }
        SysLlmModel m = opt.get();
        LlmModelKindPolicy.assertLanguageModelForChatStream(m);
        if (m.getAllowAnonymous() == LlmAnonymousAccess.DISALLOWED && userId == null) {
            throw new IllegalArgumentException("该模型不允许访客使用，请登录后再选择");
        }
        return m;
    }

    private String buildUserMetaJson(ChatSendPayload payload, List<Long> attIds) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("modelAlias", payload.getModelAlias());
            n.put("thinkingEnabled", payload.isThinkingEnabled());
            n.putPOJO("attachmentIds", attIds);
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String buildAssistantMetaJson(
            ChatSendPayload payload,
            String modelAlias,
            String reasoning,
            ModelTokenUsage usage,
            ArrayNode priorAssistantVersions,
            List<RagCitationHit> ragCitations) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("modelAlias", modelAlias);
            n.put("thinkingEnabled", payload.isThinkingEnabled());
            if (reasoning != null && !reasoning.isEmpty()) {
                n.put("reasoning", reasoning);
            }
            if (usage != null && usage.totalTokens() > 0) {
                ObjectNode u = n.putObject("usage");
                u.put("promptTokens", usage.promptTokens());
                u.put("completionTokens", usage.completionTokens());
                u.put("totalTokens", usage.totalTokens());
            }
            if (priorAssistantVersions != null && priorAssistantVersions.size() > 0) {
                n.set("priorVersions", priorAssistantVersions);
            }
            if (ragCitations != null && !ragCitations.isEmpty()) {
                ArrayNode arr = n.putArray("ragCitations");
                for (RagCitationHit h : ragCitations) {
                    ObjectNode o = arr.addObject();
                    o.put("kbId", h.kbId());
                    o.put("documentId", h.documentId());
                    o.put("documentTitle", h.documentTitle() != null ? h.documentTitle() : "");
                    o.put("chunkId", h.chunkId());
                    o.put("chunkSeq", h.chunkSeq());
                    o.put("contentPreview", h.contentPreview() != null ? h.contentPreview() : "");
                }
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String ASSISTANT_REGENERATE_INSTRUCTION =
            "请重新生成回复，确保内容更加清晰、简洁且结构化。避免冗余信息，重点突出核心要点，并使用分点或分段提升可读性。若涉及专业术语，需附带简要解释。";

    /** 涓?{@link #buildRegenerateUserPromptForModel} 鎷兼帴涓€鑷达紝渚?RAG 璇嶆硶妫€绱㈡娊鍙栫湡瀹炵敤鎴烽棶鍙ワ紙閬垮厤鎸囦护鍓嶇紑鍗犳弧 LIKE 绐楀彛锛夈€?*/
    private static final String REGENERATE_USER_QUESTION_MARKER = "我的问题是：";

    private static final String USER_ATTACHMENT_BLOCK_MARKER = "\n\n【以下为用户上传文档摘要，请结合回答】";

    /**
     * RAG 检索（向量 + 词法）使用的查询串：仅本轮用户输入 {@link ChatSendPayload#getContent()}，不包含合并进 user
     * 消息的附件长文，避免嵌入/关键词被文摘稀释或与「本轮一句话」语义无关仍强相关。重新生成场景仍从合成 user 正文中截取「我的问题是：」后的真实提问。
     */
    private static String buildRagLexicalSearchQuery(ChatSendPayload payload, String augmentedUserText) {
        String aug = augmentedUserText == null ? "" : augmentedUserText.trim();
        if (aug.startsWith(ASSISTANT_REGENERATE_INSTRUCTION)) {
            int mi = aug.indexOf(REGENERATE_USER_QUESTION_MARKER);
            if (mi >= 0) {
                int bodyStart = mi + REGENERATE_USER_QUESTION_MARKER.length();
                int att = aug.indexOf(USER_ATTACHMENT_BLOCK_MARKER);
                String slice =
                        att >= bodyStart ? aug.substring(bodyStart, att).trim() : aug.substring(bodyStart).trim();
                if (!slice.isEmpty()) {
                    return slice;
                }
            }
        }
        String userOnly = payload.getContent() == null ? "" : payload.getContent().trim();
        if (!userOnly.isEmpty()) {
            return userOnly;
        }
        if (!aug.isEmpty()) {
            int att = aug.indexOf(USER_ATTACHMENT_BLOCK_MARKER);
            if (att > 0) {
                return aug.substring(0, att).trim();
            }
        }
        return "";
    }

    /**
     * 閲嶆柊鐢熸垚鍔╂墜鍥炲鏃讹紝鍙戠粰妯″瀷鐨?user 杞锛氬湪銆屾垜鐨勯棶棰樻槸锛氥€嶅悗鎺ョ敤鎴峰師濮嬭緭鍏ワ紝鍐嶆寜闇€鎷兼帴闄勪欢鎽樺綍锛堜笌
     * {@link #buildUserMessageWithAttachments} 瑙勫垯涓€鑷达級銆備細璇濊〃涓敤鎴锋秷鎭鏂囦繚鎸佸師鏍枫€?     */
    private static String buildRegenerateUserPromptForModel(String rawUserQuestion, List<ChatAttachment> attachments) {
        String q = rawUserQuestion == null ? "" : rawUserQuestion.trim();
        String head =
                ASSISTANT_REGENERATE_INSTRUCTION + "\n\n" + REGENERATE_USER_QUESTION_MARKER + (q.isEmpty() ? "（空）" : q);
        return buildUserMessageWithAttachments(head, attachments);
    }

    private static String buildUserMessageWithAttachments(String userText, List<ChatAttachment> attachments) {
        if (attachments.isEmpty()) {
            return userText;
        }
        StringBuilder sb = new StringBuilder(userText);
        sb.append(USER_ATTACHMENT_BLOCK_MARKER).append("\n");
        for (ChatAttachment a : attachments) {
            sb.append("\n--- 文件：")
                    .append(a.getFileName())
                    .append(" ---\n")
                    .append(truncate(a.getExtractedText(), 120_000))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String buildAttachmentContextBlock(List<ChatAttachment> attachments) {
        StringBuilder sb = new StringBuilder(
                "用户上传了以下文档，正文已合并进 user 消息；请遵守引用边界，勿编造未出现的事实。");
        for (ChatAttachment a : attachments) {
            sb.append("\n- ").append(a.getFileName()).append(" (").append(a.getCharLength()).append(" 瀛楃)");
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…（已截断）";
    }

    /** 鍏宠仈浼氳瘽涓庢秷鎭紱娴佸紡瀹屾垚鍦ㄨ櫄鎷熺嚎绋嬫墽琛岋紝椤绘樉寮忎紶鍏?tenantId锛堝嬁渚濊禆 {@link TenantContextHolder}锛夈€?*/
    private void linkMessage(long conversationId, long messageId, long tenantId) {
        var lnk = new LnkChatConversationMessage();
        lnk.setConversationId(conversationId);
        lnk.setMessageId(messageId);
        lnk.setSeq(0);
        lnkRepository.insert(lnk);
        conversationRepository.touchUpdatedAt(conversationId, tenantId);
    }

}
