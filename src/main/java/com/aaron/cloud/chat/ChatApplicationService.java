package com.aaron.cloud.chat;

import com.aaron.cloud.chat.dto.ChatAttachmentMessageView;
import com.aaron.cloud.chat.dto.ChatIntentTurnHitView;
import com.aaron.cloud.chat.dto.ChatWorkflowSegmentView;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.intent.ChatIntentStreamRouter;
import com.aaron.cloud.chat.intent.IntentKeywordMatchHit;
import com.aaron.cloud.chat.intent.IntentSseRoute;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.chat.dto.ChatRegenerateRequest;
import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.websearch.ChatWebSearchGroundingService;
import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.dto.WebSearchReferenceView;
import com.aaron.cloud.chat.websearch.WebSearchReference;
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
import com.aaron.cloud.common.tenant.runtime.ChatPromptLimitsRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.UserMemoryApplicationService;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.profile.memory.MemoryAbstractAsyncPublisher;
import com.aaron.cloud.common.profile.memory.MemoryAbstractRefreshMessage;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagRetrievalHitCounter;
import com.aaron.cloud.common.util.TextClamp;
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
    private final UserMemoryApplicationService userMemoryApplicationService;
    private final MemoryAbstractAsyncPublisher memoryAbstractAsyncPublisher;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ChatWebSearchGroundingService chatWebSearchGroundingService;
    private final ChatTurnDigestApplicationService chatTurnDigestApplicationService;

    public ChatConversation createConversation(String title) {
        var snap = TenantContextHolder.require();
        var c = new ChatConversation();
        c.setTenantId(snap.getTenantId());
        c.setUserId(snap.getUserId());
        c.setDeviceId(snap.getDeviceId());
        c.setTitle(title == null || title.isBlank() ? "新会话" : title);
        c.setStatus(ConversationRecordStatus.ACTIVE);
        conversationRepository.insert(c);
        if (tenantRuntimeSettingApplicationService.memoryPolicy(snap.getTenantId()).enqueueAbstractOnConversationCreate()) {
            String sk = ProfileSubjectKey.fromSnapshot(snap);
            if (sk != null) {
                llmModelRepository
                        .listForCatalog(snap.getTenantId(), snap.getUserId() == null)
                        .stream()
                        .findFirst()
                        .map(SysLlmModel::getAlias)
                        .filter(a -> a != null && !a.isBlank() && !"mock".equalsIgnoreCase(a.trim()))
                        .ifPresent(
                                alias ->
                                        memoryAbstractAsyncPublisher.publish(
                                                new MemoryAbstractRefreshMessage(
                                                        snap.getTenantId(),
                                                        sk,
                                                        alias.trim(),
                                                        "conversation_create")));
            }
        }
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
        List<ChatMessage> rows = messageRepository.listByTenantAndIdsInOrder(tenantId, ids);
        LinkedHashSet<Long> allAttIds = new LinkedHashSet<>();
        for (ChatMessage m : rows) {
            if (m.getRole() == ChatMessageRole.USER) {
                allAttIds.addAll(parseAttachmentIdsFromUserMeta(m.getMetaJson()));
            }
        }
        Map<Long, ChatAttachment> attById = new HashMap<>();
        if (!allAttIds.isEmpty()) {
            for (ChatAttachment a : attachmentRepository.listByIds(tenantId, conversationId, allAttIds)) {
                attById.put(a.getId(), a);
            }
        }
        return rows.stream().map(m -> toChatMessageView(m, attById)).toList();
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

    /** C 端是否展示「联网」开关：租户存在至少一条启用的 {@code WEB_SEARCH} 模型。 */
    public boolean isWebSearchAvailableForCurrentTenant() {
        var snap = TenantContextHolder.require();
        return llmModelRepository.hasEnabledWebSearchModel(snap.getTenantId());
    }

    public SseEmitter streamUserMessage(long conversationId, ChatSendPayload payload) {
        var snap = TenantContextHolder.require();
        long pipelineT0 = System.currentTimeMillis();
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

        if (payload.isWebSearchEnabled()) {
            if (!llmModelRepository.hasEnabledWebSearchModel(snap.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户未配置启用的联网搜索模型，无法开启联网");
            }
            SysLlmModel webRel =
                    llmModelRepository
                            .pickDefaultWebSearchModel(snap.getTenantId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "租户未配置启用的联网搜索模型，无法开启联网"));
            llmTokenQuotaCoordinator.assertQuotaAllowsSend(webRel);
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

        log.info(
                "[对话阶段] phase=preflightDone tenantId={} conversationId={} elapsedMs={} mock={} webSearch={} attachCount={}",
                snap.getTenantId(),
                conversationId,
                millisSince(pipelineT0),
                isMock,
                payload.isWebSearchEnabled(),
                attachments.size());

        var userMsg = new ChatMessage();
        userMsg.setTenantId(snap.getTenantId());
        userMsg.setRole(ChatMessageRole.USER);
        userMsg.setContent(payload.getContent());
        userMsg.setMetaJson(buildUserMetaJson(payload, attIds));
        messageRepository.insert(userMsg);
        linkMessage(conversationId, userMsg.getId(), snap.getTenantId());

        log.info(
                "[对话阶段] phase=userMessageLinked tenantId={} conversationId={} elapsedMs={} userMsgId={}",
                snap.getTenantId(),
                conversationId,
                millisSince(pipelineT0),
                userMsg.getId());

        // 画像/记忆写入含 Milvus 向量嵌入等 IO，同步会阻塞意图路由与主链首包；改为虚拟线程后台执行。
        var snapForIngest = snap;
        long convIdForIngest = conversationId;
        String utteranceForIngest = payload.getContent();
        String modelAliasForIngest = payload.getModelAlias();
        Thread.startVirtualThread(
                () -> {
                    try {
                        userProfileApplicationService.ingestAfterUserUtterance(
                                snapForIngest, utteranceForIngest, convIdForIngest, modelAliasForIngest);
                    } catch (Exception ex) {
                        log.warn(
                                "async profile/memory ingest failed tenantId={} conversationId={}",
                                snapForIngest.getTenantId(),
                                convIdForIngest,
                                ex);
                    }
                });

        Optional<IntentSseRoute> intentRoute =
                chatIntentStreamRouter.maybeRouteIntentStream(conversationId, snap, payload, attachments);
        log.info(
                "[对话阶段] phase=intentRouterDone tenantId={} conversationId={} elapsedMs={} intentSseHit={}",
                snap.getTenantId(),
                conversationId,
                millisSince(pipelineT0),
                intentRoute.isPresent());
        if (intentRoute.isPresent()) {
            IntentSseRoute r = intentRoute.get();
            mergeIntentHitIntoUserMessageMeta(userMsg.getId(), snap.getTenantId(), r.definition(), r.keywordHit());
            log.info(
                    "[意图链路] 已返回意图 SSE，本请求不再进入大模型主链 conversationId={} tenantId={} intentCode={} matchSource={}",
                    conversationId,
                    snap.getTenantId(),
                    r.definition().getCode(),
                    r.keywordHit().matchSource());
            return r.emitter();
        }

        log.info(
                "[意图链路] 未走意图 SSE，进入大模型主链 conversationId={} tenantId={} messagePreview={}",
                conversationId,
                snap.getTenantId(),
                intentChainMessagePreview(payload.getContent()));
        log.info(
                "[对话阶段] phase=enteringOpenAssistant tenantId={} conversationId={} elapsedMsSinceRequest={}",
                snap.getTenantId(),
                conversationId,
                millisSince(pipelineT0));
        return openAssistantSseStream(
                conversationId, snap, payload, augmentedUserText, modelCfg, isMock, attachments, userMsg.getId(), null);
    }

    /**
     * 自意图路由组装 turns、调用模型并以 SSE 下发，最后落库助手消息（调用方已插入 user 行或重试场景下不再插入 user）。
     *
     * <p>在首条 system 之后注入本会话已链接的 user/assistant 历史（不含本轮 user；本轮正文为 {@code augmentedUserText}），条数与字数见
     * {@link TenantRuntimeSettingKey#CHAT_PROMPT_LIMITS_JSON}。命中意图 SSE 走外部长链（如 Coze）时不经本方法。
     *
     * @param pairedUserMessageId 本轮配对的 user 消息 id（非 null 且开启联网时，将 {@code webSearchReferences} 同步写入该 user 行 meta，便于对话记录按轮次留存）
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
            Long pairedUserMessageId,
            ArrayNode priorAssistantVersions) {
        long openT0 = System.currentTimeMillis();
        log.info(
                "[对话阶段] phase=openAssistantEnter tenantId={} conversationId={} elapsedMs=0 mock={}",
                snap.getTenantId(),
                conversationId,
                isMock);
        // 未接 Milvus（local 占位）时：对话侧等同无 RAG，不调检索、不注入片段，用户端无报错。
        // 仅拉取 rag_knowledge_base.chat_retrieval_enabled=ON 的知识库；无开启库时不做 RAG 检索。
        final List<Long> chatRagKbIds =
                aiProvidersProperties.resolvedVectorStore() == VectorStoreProviderMode.milvus
                        ? ragKnowledgeBaseRepository.listIdsWithChatRetrievalEnabled(snap.getTenantId())
                        : List.of();
        log.info(
                "[对话阶段] phase=ragKbListLoaded tenantId={} conversationId={} elapsedMs={} kbCount={} vectorStore={}",
                snap.getTenantId(),
                conversationId,
                millisSince(openT0),
                chatRagKbIds.size(),
                aiProvidersProperties.resolvedVectorStore());
        IntentRoute baseIntent = chatRagKbIds.isEmpty() ? IntentRoute.CHAT_ONLY : IntentRoute.RAG;
        String ragLexicalQuery = buildRagLexicalSearchQuery(payload, augmentedUserText);
        IntentRoute routeIntent =
                baseIntent == IntentRoute.RAG && ragLexicalQuery.isBlank()
                        ? IntentRoute.CHAT_ONLY
                        : baseIntent;
        log.info(
                "[对话阶段] phase=ragLexicalReady tenantId={} conversationId={} elapsedMs={} baseIntent={} routeIntent={} lexicalChars={}",
                snap.getTenantId(),
                conversationId,
                millisSince(openT0),
                baseIntent,
                routeIntent,
                ragLexicalQuery.length());
        List<RagCitationHit> ragCitationHits = List.of();
        List<String> ragSnippets = List.of();
        if (routeIntent == IntentRoute.RAG) {
            log.info(
                    "[对话阶段] phase=ragCitationSearchStart tenantId={} conversationId={} elapsedMs={} topK={}",
                    snap.getTenantId(),
                    conversationId,
                    millisSince(openT0),
                    3);
            long tCit = System.currentTimeMillis();
            ragCitationHits =
                    List.copyOf(
                            ragQueryPort.searchCitationHitsAcrossKnowledgeBases(
                                    snap.getTenantId(), chatRagKbIds, ragLexicalQuery, 3));
            log.info(
                    "[对话阶段] phase=ragCitationSearchDone tenantId={} conversationId={} elapsedMs={} stepMs={} hitCount={}",
                    snap.getTenantId(),
                    conversationId,
                    millisSince(openT0),
                    millisSince(tCit),
                    ragCitationHits.size());
            log.info(
                    "[对话阶段] phase=ragSnippetSearchStart tenantId={} conversationId={} elapsedMs={}",
                    snap.getTenantId(),
                    conversationId,
                    millisSince(openT0));
            long tSnip = System.currentTimeMillis();
            ragSnippets =
                    clampRagSnippetsForPrompt(
                            snap.getTenantId(),
                            ragQueryPort.searchSnippetsAcrossKnowledgeBases(
                                    snap.getTenantId(), chatRagKbIds, ragLexicalQuery, 3));
            log.info(
                    "[对话阶段] phase=ragSnippetSearchDone tenantId={} conversationId={} elapsedMs={} stepMs={} snippetCount={}",
                    snap.getTenantId(),
                    conversationId,
                    millisSince(openT0),
                    millisSince(tSnip),
                    ragSnippets.size());
            // 向量阈值过滤或 ES 未命中后可能两侧皆空：本回合按纯对话编排，避免落库/展示无实质检索的「挂名引用」。
            if (ragCitationHits.isEmpty() && ragSnippets.isEmpty()) {
                routeIntent = IntentRoute.CHAT_ONLY;
            }
        }
        final IntentRoute intent = routeIntent;
        final List<RagCitationHit> ragHitsForStream = ragCitationHits;
        log.info(
                "[对话阶段] phase=ragRouteFinal tenantId={} conversationId={} elapsedMs={} intent={} citationHits={} snippets={}",
                snap.getTenantId(),
                conversationId,
                millisSince(openT0),
                intent,
                ragCitationHits.size(),
                ragSnippets.size());
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
        long tProfile = System.currentTimeMillis();
        String profileAddendum = userProfileApplicationService.buildPromptAddendum(snap, augmentedUserText);
        log.info(
                "[对话阶段] phase=profileAddendumDone tenantId={} conversationId={} elapsedMs={} stepMs={} nonBlank={}",
                snap.getTenantId(),
                conversationId,
                millisSince(openT0),
                millisSince(tProfile),
                !profileAddendum.isBlank());
        StringBuilder sys = new StringBuilder();
        if (!profileAddendum.isBlank()) {
            sys.append("【画像·跨会话】\n").append(profileAddendum).append("\n\n");
        }
        if (intent == IntentRoute.RAG) {
            sys.append("可参考知识片段：");
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
        ChatPromptLimitsRuntime promptLimits =
                tenantRuntimeSettingApplicationService.chatPromptLimits(snap.getTenantId());
        List<ModelChatRequest.MessageTurn> historyTurns =
                buildPromptHistoryTurns(snap.getTenantId(), conversationId, pairedUserMessageId, promptLimits);
        if (!historyTurns.isEmpty()) {
            turns.addAll(historyTurns);
        }
        log.info(
                "[对话阶段] phase=promptHistoryReady tenantId={} conversationId={} elapsedMs={} historyTurns={}",
                snap.getTenantId(),
                conversationId,
                millisSince(openT0),
                historyTurns.size());
        final SysLlmModel webSearchModelForStream =
                payload.isWebSearchEnabled()
                        ? llmModelRepository
                                .pickDefaultWebSearchModel(snap.getTenantId())
                                .orElseThrow(() -> new IllegalStateException("联网搜索模型不可用"))
                        : null;
        final ArrayList<WebSearchReference> webSearchRefsForStream = new ArrayList<>();
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

        final long openAssistantWallMs = openT0;
        Runnable run =
                () -> {
                    try {
                        log.info(
                                "[对话阶段] phase=llmStreamThreadStart tenantId={} conversationId={} elapsedMsSinceOpenAssistant={}",
                                snap.getTenantId(),
                                conversationId,
                                millisSince(openAssistantWallMs));
                        long streamStartedAt = System.currentTimeMillis();
                        if (payload.isWebSearchEnabled() && webSearchModelForStream != null) {
                            log.info(
                                    "[对话阶段] phase=webSearchGroundingStart tenantId={} conversationId={} elapsedMsSinceOpenAssistant={}",
                                    snap.getTenantId(),
                                    conversationId,
                                    millisSince(openAssistantWallMs));
                            long tWeb = System.currentTimeMillis();
                            WebGroundingBundle wb =
                                    chatWebSearchGroundingService.groundMultiRoundsWithRaw(
                                            snap,
                                            webSearchModelForStream,
                                            augmentedUserText,
                                            conversationId,
                                            cumulative ->
                                                    sendSseWebSearchRefFrames(emitter, seq, cumulative));
                            webSearchRefsForStream.clear();
                            if (wb.references() != null) {
                                webSearchRefsForStream.addAll(wb.references());
                            }
                            String webCtx = formatWebGroundingContent(wb, snap.getTenantId());
                            log.info(
                                    "[对话阶段] phase=webSearchGroundingDone tenantId={} conversationId={} elapsedMsSinceOpenAssistant={} stepMs={} refCount={} injected={}",
                                    snap.getTenantId(),
                                    conversationId,
                                    millisSince(openAssistantWallMs),
                                    millisSince(tWeb),
                                    webSearchRefsForStream.size(),
                                    webCtx != null && !webCtx.isBlank());
                            if (webCtx != null && !webCtx.isBlank()) {
                                var webSys = new ModelChatRequest.MessageTurn();
                                webSys.setRole("system");
                                webSys.setContent(webCtx);
                                List<ModelChatRequest.MessageTurn> msgs = modelReq.getMessages();
                                msgs.add(msgs.size() - 1, webSys);
                            }
                        }
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
                                        ragHitsForStream,
                                        webSearchRefsForStream));
                        messageRepository.insert(asst);
                        linkMessage(conversationId, asst.getId(), snap.getTenantId());
                        if (pairedUserMessageId != null && payload.isWebSearchEnabled()) {
                            mergeWebSearchReferencesIntoUserMessageMeta(
                                    pairedUserMessageId, snap.getTenantId(), webSearchRefsForStream);
                        }
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
                        final String assistantTextForMemory = assistantBuf.toString();
                        final String modelAliasForMemory = payload.getModelAlias().trim();
                        final long assistantRowId = asst.getId();
                        Thread.startVirtualThread(
                                () -> {
                                    try {
                                        userMemoryApplicationService.afterAssistantUtterance(
                                                snap,
                                                assistantTextForMemory,
                                                conversationId,
                                                modelAliasForMemory);
                                    } catch (Exception memEx) {
                                        log.warn(
                                                "afterAssistantUtterance failed conversationId={} tenantId={}",
                                                conversationId,
                                                snap.getTenantId(),
                                                memEx);
                                    }
                                    chatTurnDigestApplicationService.scheduleTurnDigest(
                                            snap,
                                            conversationId,
                                            assistantRowId,
                                            payload.getContent(),
                                            assistantTextForMemory,
                                            modelAliasForMemory,
                                            isMock);
                                });
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

        if (payload.isWebSearchEnabled()) {
            if (!llmModelRepository.hasEnabledWebSearchModel(snap.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户未配置启用的联网搜索模型，无法开启联网");
            }
            SysLlmModel webRel =
                    llmModelRepository
                            .pickDefaultWebSearchModel(snap.getTenantId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "租户未配置启用的联网搜索模型，无法开启联网"));
            llmTokenQuotaCoordinator.assertQuotaAllowsSend(webRel);
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
                conversationId,
                snap,
                payload,
                augmentedUserText,
                modelCfg,
                isMock,
                attachments,
                userMsgId,
                priorChain);
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
        if (regenerateOpts.getWebSearchEnabled() != null) {
            payload.setWebSearchEnabled(Boolean.TRUE.equals(regenerateOpts.getWebSearchEnabled()));
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
                if (root.has("webSearchReferences") && root.get("webSearchReferences").isArray()) {
                    snap.set("webSearchReferences", root.get("webSearchReferences").deepCopy());
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
            p.setWebSearchEnabled(n.path("webSearchEnabled").asBoolean(false));
            List<Long> att = new ArrayList<>();
            if (n.has("attachmentIds") && n.get("attachmentIds").isArray()) {
                for (JsonNode x : n.get("attachmentIds")) {
                    if (x.isIntegralNumber()) {
                        att.add(x.longValue());
                    }
                }
            }
            p.setAttachmentIds(att);
            if (n.has("intentFlowTicket") && n.get("intentFlowTicket").isTextual()) {
                String t = n.get("intentFlowTicket").asText();
                if (t != null && !t.isBlank()) {
                    p.setIntentFlowTicket(t.trim());
                }
            }
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
        String template = chatInputGuardService.blockedReplyTemplate(snap.getTenantId());
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
                        try {
                            userMemoryApplicationService.afterAssistantUtterance(
                                    snap, template, conversationId, payload.getModelAlias().trim());
                        } catch (Exception memEx) {
                            log.warn(
                                    "afterAssistantUtterance (input guard) failed conversationId={} tenantId={}",
                                    conversationId,
                                    snap.getTenantId(),
                                    memEx);
                        }
                        chatTurnDigestApplicationService.scheduleTurnDigest(
                                snap,
                                conversationId,
                                asst.getId(),
                                userMsg.getContent(),
                                template,
                                payload.getModelAlias().trim(),
                                "mock".equalsIgnoreCase(payload.getModelAlias().trim()));

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

    /** 主模型流式 token 之前下发联网引用（JSON 在 {@code v} 内，形如 {@code {"references":[...]}}）。 */
    private void sendSseWebSearchRefFrames(
            SseEmitter emitter, AtomicInteger seq, List<WebSearchReference> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("references");
            for (WebSearchReference r : refs) {
                WebSearchReferenceView v = r.toView();
                ObjectNode o = arr.addObject();
                o.put("title", v.title() != null ? v.title() : "");
                o.put("url", v.url() != null ? v.url() : "");
                o.put("summary", v.summary() != null ? v.summary() : "");
                if (v.siteName() != null) {
                    o.put("siteName", v.siteName());
                }
                if (v.logoUrl() != null) {
                    o.put("logoUrl", v.logoUrl());
                }
                if (v.publishTime() != null) {
                    o.put("publishTime", v.publishTime());
                }
                if (v.extraJson() != null) {
                    o.put("extraJson", v.extraJson());
                }
            }
            emitter.send(
                    SseEmitter.event()
                            .data(sseChunk("webSearchRefs", objectMapper.writeValueAsString(root)))
                            .id(String.valueOf(seq.incrementAndGet())));
        } catch (Exception ex) {
            log.warn("sse webSearchRefs frame send failed seq={}", seq.get(), ex);
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

    private List<Long> parseAttachmentIdsFromUserMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode n = objectMapper.readTree(metaJson);
            if (!n.has("attachmentIds") || !n.get("attachmentIds").isArray()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            for (JsonNode x : n.get("attachmentIds")) {
                if (x != null && x.isIntegralNumber()) {
                    out.add(x.longValue());
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("parse attachmentIds from user meta failed", ex);
            return List.of();
        }
    }

    private List<ChatAttachmentMessageView> buildUserMessageAttachmentViews(
            String metaJson, Map<Long, ChatAttachment> attById) {
        List<Long> ids = parseAttachmentIdsFromUserMeta(metaJson);
        if (ids.isEmpty() || attById.isEmpty()) {
            return List.of();
        }
        List<ChatAttachmentMessageView> out = new ArrayList<>();
        for (Long id : ids) {
            ChatAttachment a = attById.get(id);
            if (a != null) {
                out.add(new ChatAttachmentMessageView(a.getId(), a.getFileName(), a.getCharLength()));
            }
        }
        return out;
    }

    private ChatMessageView toChatMessageView(ChatMessage m, Map<Long, ChatAttachment> attById) {
        String role =
                switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    default -> "system";
                };
        ChatIntentTurnHitView intentTurnHit = null;
        if (m.getMetaJson() != null && !m.getMetaJson().isBlank()) {
            intentTurnHit = parseIntentTurnHitFromMeta(m.getMetaJson(), m.getId());
        }
        String reasoning = null;
        Integer pt = null;
        Integer ct = null;
        Integer tt = null;
        String modelAlias = null;
        String userFeedback = null;
        String contentSummary = null;
        List<PriorAssistantVersionView> priorVersions = null;
        List<RagCitationView> ragCitations = null;
        List<ChatWorkflowSegmentView> workflowSegments = null;
        List<WebSearchReferenceView> webSearchReferences = null;
        if (m.getRole() == ChatMessageRole.USER
                && m.getMetaJson() != null
                && !m.getMetaJson().isBlank()) {
            try {
                webSearchReferences =
                        parseWebSearchReferencesFromRoot(objectMapper.readTree(m.getMetaJson()));
            } catch (Exception ex) {
                log.warn("user message meta parse failed messageId={}", m.getId(), ex);
            }
        }
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
                if (root.has(ChatTurnDigestApplicationService.META_CONTENT_SUMMARY)
                        && root.get(ChatTurnDigestApplicationService.META_CONTENT_SUMMARY).isTextual()) {
                    String cs = root.get(ChatTurnDigestApplicationService.META_CONTENT_SUMMARY).asText().trim();
                    if (!cs.isEmpty()) {
                        contentSummary = cs;
                    }
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
                webSearchReferences = parseWebSearchReferencesFromRoot(root);
            } catch (Exception ex) {
                log.warn("chat message meta parse failed messageId={}", m.getId(), ex);
            }
        }
        List<ChatAttachmentMessageView> attachments =
                m.getRole() == ChatMessageRole.USER
                        ? buildUserMessageAttachmentViews(m.getMetaJson(), attById)
                        : List.of();
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
                workflowSegments,
                webSearchReferences,
                contentSummary,
                intentTurnHit,
                attachments);
    }

    private static String metaTextOrNull(JsonNode c, String field) {
        if (!c.has(field) || c.get(field).isNull()) {
            return null;
        }
        JsonNode v = c.get(field);
        if (!v.isTextual()) {
            return null;
        }
        String t = v.asText().trim();
        return t.isEmpty() ? null : t;
    }

    private List<WebSearchReferenceView> parseWebSearchReferencesFromRoot(JsonNode root) {
        if (root == null
                || !root.has("webSearchReferences")
                || !root.get("webSearchReferences").isArray()) {
            return null;
        }
        List<WebSearchReferenceView> wr = new ArrayList<>();
        for (JsonNode c : root.get("webSearchReferences")) {
            if (c == null || !c.isObject()) {
                continue;
            }
            String summary = c.path("summary").asText("");
            if (summary.isEmpty()) {
                summary = c.path("snippet").asText("");
            }
            wr.add(
                    new WebSearchReferenceView(
                            c.path("title").asText(""),
                            c.path("url").asText(""),
                            summary,
                            metaTextOrNull(c, "siteName"),
                            metaTextOrNull(c, "logoUrl"),
                            metaTextOrNull(c, "publishTime"),
                            metaTextOrNull(c, "extraJson")));
        }
        return wr.isEmpty() ? null : List.copyOf(wr);
    }

    private void fillWebSearchReferencesArray(ArrayNode warr, List<WebSearchReference> refs) {
        if (refs == null) {
            return;
        }
        for (WebSearchReference r : refs) {
            WebSearchReferenceView v = r.toView();
            ObjectNode o = warr.addObject();
            o.put("title", v.title() != null ? v.title() : "");
            o.put("url", v.url() != null ? v.url() : "");
            o.put("summary", v.summary() != null ? v.summary() : "");
            if (v.siteName() != null) {
                o.put("siteName", v.siteName());
            }
            if (v.logoUrl() != null) {
                o.put("logoUrl", v.logoUrl());
            }
            if (v.publishTime() != null) {
                o.put("publishTime", v.publishTime());
            }
            if (v.extraJson() != null) {
                o.put("extraJson", v.extraJson());
            }
        }
    }

    private void mergeWebSearchReferencesIntoUserMessageMeta(
            long userMessageId, long tenantId, List<WebSearchReference> refs) {
        var opt = messageRepository.findById(userMessageId, tenantId);
        if (opt.isEmpty() || opt.get().getRole() != ChatMessageRole.USER) {
            return;
        }
        ChatMessage m = opt.get();
        ObjectNode root;
        try {
            if (m.getMetaJson() != null && !m.getMetaJson().isBlank()) {
                root = (ObjectNode) objectMapper.readTree(m.getMetaJson());
            } else {
                root = objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            log.warn(
                    "merge web search user meta: reset meta tenantId={} messageId={}",
                    tenantId,
                    userMessageId,
                    e);
            root = objectMapper.createObjectNode();
        }
        try {
            if (refs == null || refs.isEmpty()) {
                root.remove("webSearchReferences");
            } else {
                ArrayNode warr = root.putArray("webSearchReferences");
                fillWebSearchReferencesArray(warr, refs);
            }
            messageRepository.updateMetaJson(userMessageId, tenantId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error(
                    "merge web search refs into user meta failed tenantId={} messageId={}",
                    tenantId,
                    userMessageId,
                    e);
        }
    }

    private ChatIntentTurnHitView parseIntentTurnHitFromMeta(String metaJson, long messageId) {
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            boolean routed = root.path("intentRouted").asBoolean(false);
            boolean handled = root.path("intentHandled").asBoolean(false);
            boolean hasFlowTicket =
                    root.has("intentFlowTicket")
                            && root.get("intentFlowTicket").isTextual()
                            && !root.get("intentFlowTicket").asText().isBlank();
            if (!routed && !handled && !hasFlowTicket) {
                return null;
            }
            long intentId = root.has("intentId") ? root.get("intentId").asLong(0L) : 0L;
            String code = root.path("intentCode").asText("");
            Long kwId = null;
            if (root.has("intentHitKeywordId") && root.get("intentHitKeywordId").isIntegralNumber()) {
                kwId = root.get("intentHitKeywordId").asLong();
            }
            String phrase =
                    root.has("intentHitPhrase") && root.get("intentHitPhrase").isTextual()
                            ? root.get("intentHitPhrase").asText()
                            : "";
            String kk =
                    root.has("intentHitKeywordKind") && root.get("intentHitKeywordKind").isTextual()
                            ? root.get("intentHitKeywordKind").asText()
                            : null;
            String src =
                    root.has("intentMatchSource") && root.get("intentMatchSource").isTextual()
                            ? root.get("intentMatchSource").asText()
                            : "";
            String flowTicket =
                    root.has("intentFlowTicket") && root.get("intentFlowTicket").isTextual()
                            ? root.get("intentFlowTicket").asText()
                            : null;
            String flowEp =
                    root.has("intentFlowEpisodeId") && root.get("intentFlowEpisodeId").isTextual()
                            ? root.get("intentFlowEpisodeId").asText()
                            : null;
            String flowRound =
                    root.has("intentFlowRound") && root.get("intentFlowRound").isTextual()
                            ? root.get("intentFlowRound").asText()
                            : null;
            Integer flowSeq = null;
            if (root.has("intentFlowRoundSeq") && root.get("intentFlowRoundSeq").isIntegralNumber()) {
                flowSeq = root.get("intentFlowRoundSeq").asInt();
            }
            if (intentId <= 0 && code.isEmpty() && !hasFlowTicket) {
                return null;
            }
            return new ChatIntentTurnHitView(intentId, code, kwId, phrase, kk, src, flowTicket, flowEp, flowRound, flowSeq);
        } catch (Exception ex) {
            log.warn("intent turn meta parse failed messageId={}", messageId, ex);
            return null;
        }
    }

    private void mergeIntentHitIntoUserMessageMeta(
            long userMessageId, long tenantId, ChatIntentDefinition def, IntentKeywordMatchHit hit) {
        var opt = messageRepository.findById(userMessageId, tenantId);
        if (opt.isEmpty()) {
            return;
        }
        ChatMessage m = opt.get();
        ObjectNode root;
        try {
            if (m.getMetaJson() != null && !m.getMetaJson().isBlank()) {
                root = (ObjectNode) objectMapper.readTree(m.getMetaJson());
            } else {
                root = objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            log.warn(
                    "merge intent hit user meta: reset meta tenantId={} messageId={}",
                    tenantId,
                    userMessageId,
                    e);
            root = objectMapper.createObjectNode();
        }
        try {
            root.put("intentRouted", true);
            root.put("intentId", def.getId());
            root.put("intentCode", def.getCode());
            root.put("intentDisplayName", def.getDisplayName());
            root.put("intentMatchSource", hit.matchSource().name());
            if (hit.keywordId() != null) {
                root.put("intentHitKeywordId", hit.keywordId());
            }
            root.put("intentHitPhrase", hit.matchedPhrase() == null ? "" : hit.matchedPhrase());
            if (hit.keywordKind() != null) {
                root.put("intentHitKeywordKind", hit.keywordKind().name());
            }
            if (hit.intentFlowTicket() != null) {
                root.put("intentFlowTicket", hit.intentFlowTicket());
            }
            if (hit.intentFlowEpisodeId() != null) {
                root.put("intentFlowEpisodeId", hit.intentFlowEpisodeId());
            }
            if (hit.intentFlowRound() != null) {
                root.put("intentFlowRound", hit.intentFlowRound());
            }
            if (hit.intentFlowRoundSeq() != null) {
                root.put("intentFlowRoundSeq", hit.intentFlowRoundSeq());
            }
            messageRepository.updateMetaJson(userMessageId, tenantId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error(
                    "merge intent hit user meta failed tenantId={} messageId={}",
                    tenantId,
                    userMessageId,
                    e);
        }
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
            n.put("webSearchEnabled", payload.isWebSearchEnabled());
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
            List<RagCitationHit> ragCitations,
            List<WebSearchReference> webSearchReferences) {
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
            if (webSearchReferences != null && !webSearchReferences.isEmpty()) {
                ArrayNode warr = n.putArray("webSearchReferences");
                fillWebSearchReferencesArray(warr, webSearchReferences);
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 本会话短期记忆：当前 user 之前的已链接消息，按角色展开为模型 turns；条数与总长受 {@link ChatPromptLimitsRuntime} 约束。
     */
    private List<ModelChatRequest.MessageTurn> buildPromptHistoryTurns(
            long tenantId,
            long conversationId,
            Long pairedUserMessageId,
            ChatPromptLimitsRuntime lim) {
        if (pairedUserMessageId == null) {
            return List.of();
        }
        int maxMsgs = lim.resolvedHistoryMaxMessages();
        if (maxMsgs <= 0) {
            return List.of();
        }
        List<Long> linkIds = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (linkIds.isEmpty()) {
            return List.of();
        }
        if (!Objects.equals(linkIds.get(linkIds.size() - 1), pairedUserMessageId)) {
            log.warn(
                    "prompt history skipped: last linked message id {} != pairedUserMessageId {} conversationId={} tenantId={}",
                    linkIds.get(linkIds.size() - 1),
                    pairedUserMessageId,
                    conversationId,
                    tenantId);
            return List.of();
        }
        if (linkIds.size() <= 1) {
            return List.of();
        }
        List<Long> prefix = linkIds.subList(0, linkIds.size() - 1);
        int from = Math.max(0, prefix.size() - maxMsgs);
        List<Long> window = prefix.subList(from, prefix.size());
        List<ChatMessage> rows = messageRepository.listByTenantAndIdsInOrder(tenantId, window);
        int perCap = lim.resolvedHistoryMaxCharsPerMessage();
        var out = new ArrayList<ModelChatRequest.MessageTurn>();
        for (ChatMessage row : rows) {
            if (row.getRole() != ChatMessageRole.USER && row.getRole() != ChatMessageRole.ASSISTANT) {
                continue;
            }
            String raw =
                    row.getRole() == ChatMessageRole.ASSISTANT
                            ? assistantHistoryText(row)
                            : (row.getContent() == null ? "" : row.getContent().trim());
            if (raw.isEmpty()) {
                continue;
            }
            String clipped = TextClamp.ellipsis(raw, perCap);
            var t = new ModelChatRequest.MessageTurn();
            t.setRole(row.getRole() == ChatMessageRole.USER ? "user" : "assistant");
            t.setContent(clipped);
            out.add(t);
        }
        trimHistoryTurnsToTotalCharBudget(out, lim.resolvedHistoryTotalMaxChars());
        return List.copyOf(out);
    }

    /**
     * 短期记忆：已落库的助手消息优先使用 {@code meta.contentSummary}，避免极长回复占满上下文。
     */
    private String assistantHistoryText(ChatMessage row) {
        String sum = parseAssistantContentSummaryMeta(row.getMetaJson());
        if (sum != null && !sum.isBlank()) {
            return sum.trim();
        }
        return row.getContent() == null ? "" : row.getContent().trim();
    }

    private String parseAssistantContentSummaryMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            JsonNode n = root.path(ChatTurnDigestApplicationService.META_CONTENT_SUMMARY);
            if (n.isTextual()) {
                String t = n.asText().trim();
                return t.isEmpty() ? null : t;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void trimHistoryTurnsToTotalCharBudget(List<ModelChatRequest.MessageTurn> turns, int totalMax) {
        if (turns.isEmpty() || totalMax <= 0) {
            return;
        }
        while (true) {
            long sum = 0L;
            for (ModelChatRequest.MessageTurn t : turns) {
                String c = t.getContent();
                sum += c == null ? 0 : c.length();
            }
            if (sum <= totalMax || turns.isEmpty()) {
                break;
            }
            turns.remove(0);
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

    private static long millisSince(long t0) {
        return System.currentTimeMillis() - t0;
    }

    /** 意图链路日志：截取用户输入前 80 字（与 ly DialogueApiService、{@link ChatIntentStreamRouter} 一致）。 */
    private static String intentChainMessagePreview(String message) {
        if (message == null) {
            return "";
        }
        String t = message.strip();
        if (t.length() <= 80) {
            return t;
        }
        return t.substring(0, 80) + "...";
    }

    private List<String> clampRagSnippetsForPrompt(long tenantId, List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        ChatPromptLimitsRuntime lim = tenantRuntimeSettingApplicationService.chatPromptLimits(tenantId);
        int n = Math.min(raw.size(), lim.resolvedRagMaxSnippets());
        int cap = lim.resolvedRagSnippetMaxChars();
        var out = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            out.add(TextClamp.ellipsis(raw.get(i), cap));
        }
        return List.copyOf(out);
    }

    private String formatWebGroundingContent(WebGroundingBundle wb, long tenantId) {
        if (wb == null) {
            return "";
        }
        ChatPromptLimitsRuntime limits = tenantRuntimeSettingApplicationService.chatPromptLimits(tenantId);
        int sumCap = limits.resolvedWebSummaryMaxChars();
        int refCap = limits.resolvedWebMaxReferences();
        int snipCap = limits.resolvedWebReferenceSnippetMaxChars();
        int urlCap = limits.resolvedWebReferenceUrlMaxChars();
        int totalCap = limits.resolvedWebGroundingTotalMaxChars();

        String sum = wb.summaryText() == null ? "" : wb.summaryText().trim();
        StringBuilder sb = new StringBuilder();
        if (!sum.isBlank()) {
            sb.append("【网络检索摘要】\n").append(TextClamp.ellipsis(sum, sumCap));
        }
        if (wb.references() != null && !wb.references().isEmpty() && refCap > 0) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("【引用】\n");
            int i = 1;
            for (WebSearchReference r : wb.references()) {
                if (i > refCap) {
                    break;
                }
                String title = r.title() == null ? "" : r.title().trim();
                String url = r.url() == null ? "" : r.url().trim();
                String snip = r.snippet() == null ? "" : r.snippet().trim();
                title = TextClamp.ellipsis(title, 240);
                url = TextClamp.ellipsis(url, urlCap);
                snip = TextClamp.ellipsis(snip, snipCap);
                sb.append(i++).append(". ").append(title.isBlank() ? url : title).append("\n");
                if (!url.isBlank()) {
                    sb.append("   URL: ").append(url).append("\n");
                }
                if (!snip.isBlank()) {
                    sb.append("   ").append(snip).append("\n");
                }
            }
        }
        String built = sb.toString().trim();
        return TextClamp.ellipsis(built, totalCap);
    }

    /**
     * 重新生成时发给模型的 user 正文：在「我的问题是：」前加重生成引导语，再按 {@link #buildUserMessageWithAttachments} 拼附件。
     */
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
