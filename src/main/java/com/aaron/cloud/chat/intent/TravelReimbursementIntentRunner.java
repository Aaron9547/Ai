package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.dto.ChatWorkflowSegmentView;
import com.aaron.cloud.chat.ChatAttachmentBinStore;
import com.aaron.cloud.chat.ChatTurnDigestApplicationService;
import com.aaron.cloud.chat.intent.coze.TravelCozeWorkflowClient;
import com.aaron.cloud.chat.intent.flow.IntentFlowSession;
import com.aaron.cloud.chat.intent.flow.IntentFlowSessionStore;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin;
import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ChatIntentMatchSource;
import com.aaron.cloud.common.api.enums.ChatMessageRole;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatIntentKeywordRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.LnkChatConversationMessage;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.profile.UserMemoryApplicationService;
import com.aaron.cloud.common.tenant.runtime.TravelCozeRuntimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 出差报销意图：分 DOC（材料）/ PLAN（审批与行程）阶段；与 ly-ai-application {@code TravelReimbursementService} 语义对齐。
 *
 * <p>Coze 启用条件：意图 {@code extra_config_json.handlerParams} 中文档/行程的 API Key 与 workflow id 四项均非空则 DOC/PLAN 各调用 Coze
 * {@code /v1/workflow/stream_run}；否则走<strong>演示模拟</strong>。
 * <strong>两阶段与 ly 一致</strong>：首轮 SSE 仅跑 DOC（材料工作流），结束后会话进入 PLAN、等待用户<strong>再发一条消息</strong>；下一轮再跑 PLAN（行程工作流），不在同一次 SSE 内串行两段 Coze。
 * DOC 入参与 ly {@code TravelReimbursementService#buildDocParameters} 对齐：{@code file}（先 {@code /v1/files/upload}，再传 {@code {"file_id":"..."}} 或列表）、{@code input}；
 * 另附 {@code document_text} 便于工作流选用。原始文件按会话附件 id 从 {@link com.aaron.cloud.chat.ChatAttachmentBinStore} 落盘目录读取后上传 Coze（不入库）；无落盘文件时（例如旧数据）用抽取文本生成
 * {@code .txt} 上传作为兜底，避免 Coze 报 miss params file。
 *
 * <p>多轮流：会话状态由 {@link IntentFlowSessionStore} 持久化，客户端回传 {@link com.aaron.cloud.chat.dto.ChatSendPayload#getIntentFlowTicket()}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelReimbursementIntentRunner implements ChatIntentHandlerPlugin {

    private static final Pattern[] PLAN_CONTINUE_PATTERNS = {
        Pattern.compile(".*(?:帮我)?安排从\\S+出发(?:的)?(?:出差)?行程.*"),
        Pattern.compile(".*从\\S+出发(?:的)?(?:出差)?(?:安排|行程).*"),
        Pattern.compile(".*(?:去|到)\\S+(?:出差|行程).*(?:怎么安排|咋安排|如何安排|怎么走).*"),
        Pattern.compile(".*(?:帮我|给我|麻烦|请)?(?:规划|安排|排一下|排个|做个).*(?:出差|行程|路线).*"),
        Pattern.compile(".*\\S+(?:出差|行程|路线).*(?:规划一下|安排一下|帮我看看|怎么弄|怎么搞).*")
    };

    private static final String[] DEFAULT_PLAN_CONTINUE_KEYWORDS = {
        "继续", "下一步", "安排行程", "提交审批", "我要出差", "出差申请", "出发的行程", "出差的行程"
    };

    private static final String[] APPLY_CONFIRMED_KEYWORDS = {
        "我已提交申请单", "已提交申请单", "出差申请", "我已提交出差申请", "已提交出差申请"
    };

    private static final String PLAN_APPLY_CHECK_TITLE = "判断是否提交了出差申请单";
    private static final String PLAN_APPLY_CHECK_CONTENT_FULL =
            "在系统中查询到您已提交出差申请，您的出差相关信息已成功录入系统并提交。系统将按照内部出差管理规定，推进后续审批、行程备案及差旅相关事宜。请您留意系统站内通知，及时关注申请的后续进展。请提前做好出行规划与准备，祝您此次出差工作顺利、行程平安。";
    /** 前端工作流条标题（与 ly 先 DOC 后 PLAN 顺序一致；避免 title 为空时无栏可点）。 */
    private static final String DOC_WORKFLOW_STEP_TITLE = "文档解析（工作流）";

    private static final String PLAN_WORKFLOW_STEP_TITLE = "行程规划（工作流）";
    private static final String MATERIAL_STEP_TITLE = "材料准备";

    private static final String PLAN_CONFLICT_CHECK_TITLE = "判断是否有行程冲突";
    private static final String PLAN_CONFLICT_NOTICE_FULL =
            "行程冲突通知：您在系统登记的出差期间，检测到存在行程冲突信息，可能影响正常出行与工作安排。请您及时核对行程明细，对出差时间、出行计划进行合理调整，避免因冲突造成不便。如需协助处理行程调整事宜，可联系行政部门沟通协调。";
    private static final String PLAN_NO_CONFLICT_NOTICE_FULL =
            "无行程冲突通知：经系统核查，您在本次出差期间无其他行程冲突，行程安排顺畅有序。请您安心做好出行准备，按时开展相关工作。祝您一路顺风，出差工作顺利、平安返程。";

    private final ObjectMapper objectMapper;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatIntentKeywordRepository intentKeywordRepository;
    private final TravelCozeWorkflowClient travelCozeWorkflowClient;
    private final ChatAttachmentBinStore attachmentBinStore;
    private final UserMemoryApplicationService userMemoryApplicationService;
    private final IntentFlowSessionStore intentFlowSessionStore;
    private final ChatTurnDigestApplicationService chatTurnDigestApplicationService;

    private TravelIntentHandlerState readHandlerState(IntentFlowSession s) {
        try {
            if (s.getHandlerStateJson() == null || s.getHandlerStateJson().isBlank()) {
                return new TravelIntentHandlerState(null, false, false);
            }
            return objectMapper.readValue(s.getHandlerStateJson(), TravelIntentHandlerState.class);
        } catch (Exception e) {
            log.warn(
                    "[意图·出差报销] 会话状态 JSON 解析失败，已使用空状态：意图编号 {}",
                    s.getIntentDefinitionId(),
                    e);
            return new TravelIntentHandlerState(null, false, false);
        }
    }

    private void persistTravelSession(IntentFlowSession s, TravelIntentHandlerState st, String flowId, long ttlMs)
            throws Exception {
        long now = System.currentTimeMillis();
        s.setHandlerStateJson(objectMapper.writeValueAsString(st));
        s.setUpdatedAtMs(now);
        s.setExpiresAtEpochMs(now + ttlMs);
        intentFlowSessionStore.save(s, flowId, ttlMs);
    }

    private IntentKeywordMatchHit withFlow(IntentKeywordMatchHit base, IntentFlowSession s, int displaySeq) {
        return base.withFlowMeta(s.getFlowId(), s.getEpisodeId(), s.getCurrentRound(), displaySeq);
    }

    private void bumpCompletedInteraction(long tenantId, long conversationId, long intentId, String flowId, long ttlMs) {
        if (flowId == null || flowId.isBlank()) {
            return;
        }
        intentFlowSessionStore
                .findValid(tenantId, conversationId, intentId, flowId)
                .ifPresent(
                        s -> {
                            s.setRoundInteractionSeq(s.getRoundInteractionSeq() + 1);
                            long now = System.currentTimeMillis();
                            s.setUpdatedAtMs(now);
                            s.setExpiresAtEpochMs(now + ttlMs);
                            intentFlowSessionStore.save(s, flowId, ttlMs);
                        });
    }

    @Override
    public ChatIntentHandlerKind kind() {
        return ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends Enum> handlerParamEnumClass() {
        return TravelReimbursementHandlerParam.class;
    }

    private TravelCozeRuntimeConfig resolveTravelCoze(ChatIntentDefinition def) {
        return TravelHandlerParams.parse(def.getExtraConfigJson(), objectMapper).toRuntimeConfig();
    }

    @Override
    public Optional<IntentKeywordMatchHit> evaluateKeywordMatch(
            long conversationId,
            long tenantId,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            ChatSendPayload payload,
            List<ChatAttachment> attachments) {
        return evaluateKeywordMatch(
                conversationId, tenantId, def, keywords, payload, attachments, IntentMatchContext.empty());
    }

    @Override
    public Optional<IntentKeywordMatchHit> evaluateKeywordMatch(
            long conversationId,
            long tenantId,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            IntentMatchContext flowContext) {
        TravelIntentRoutingConfig routing =
                TravelIntentRoutingConfig.parse(def.getExtraConfigJson(), objectMapper);
        long ttlMs = routing.sessionTtlMs();
        String msg = payload.getContent() == null ? "" : payload.getContent();

        Optional<TriggerScan> triggerScan = findFirstTriggerScan(msg, keywords);
        if (triggerScan.isPresent()) {
            IntentKeywordMatchHit th = triggerScan.get().hit();
            String entryRound = triggerScan.get().entryRoundName();
            String flowId = intentFlowSessionStore.newFlowId();
            String episode = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            TravelIntentHandlerState emptyHs = new TravelIntentHandlerState(null, false, false);
            IntentFlowSession sess;
            try {
                sess =
                        IntentFlowSession.builder()
                                .tenantId(tenantId)
                                .conversationId(conversationId)
                                .intentDefinitionId(def.getId())
                                .handlerKind(ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT.name())
                                .episodeId(episode)
                                .currentRound(entryRound)
                                .createdAtMs(now)
                                .updatedAtMs(now)
                                .expiresAtEpochMs(now + ttlMs)
                                .roundInteractionSeq(0)
                                .flowId(flowId)
                                .handlerStateJson(objectMapper.writeValueAsString(emptyHs))
                                .build();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            intentFlowSessionStore.save(sess, flowId, ttlMs);
            return Optional.of(
                    withFlow(
                            IntentKeywordMatchHit.create(
                                    th.keywordId(),
                                    th.matchedPhrase(),
                                    th.keywordKind(),
                                    th.matchSource()),
                            sess,
                            1));
        }

        Optional<IntentFlowSession> fsOpt = Optional.empty();
        if (flowContext.hasValidSession()) {
            IntentFlowSession fs = flowContext.session().get();
            if (fs.getIntentDefinitionId() == def.getId()) {
                fsOpt = Optional.of(fs);
            }
        }
        if (fsOpt.isEmpty()) {
            String ticket = payload.getIntentFlowTicket();
            if (ticket != null && !ticket.isBlank()) {
                fsOpt = intentFlowSessionStore.findValid(tenantId, conversationId, def.getId(), ticket.trim());
            }
        }
        if (fsOpt.isEmpty()) {
            return Optional.empty();
        }
        IntentFlowSession fs = fsOpt.get();
        TravelIntentHandlerState st = readHandlerState(fs);
        TravelReimbursementRound round;
        try {
            round = TravelReimbursementRound.valueOf(fs.getCurrentRound());
        } catch (Exception e) {
            return Optional.empty();
        }

        if (round == TravelReimbursementRound.PLAN
                && st.getDocSummary() != null
                && !st.getDocSummary().isBlank()) {
            String roundName = fs.getCurrentRound();
            List<String> planKws = planContinuePhrases(keywords, roundName);
            IntentKeywordMatchHit base =
                    planContinueMatch(msg, planKws, roundName)
                            ? resolvePlanContinueHit(msg, keywords, roundName)
                            : IntentKeywordMatchHit.create(
                                    null,
                                    "（材料已就绪·继续办理）",
                                    ChatIntentKeywordKind.PLAN_CONTINUE,
                                    ChatIntentMatchSource.PLAN_CONTINUE_DEFAULT_PHRASE);
            return Optional.of(
                    withFlow(
                            IntentKeywordMatchHit.create(
                                    base.keywordId(),
                                    base.matchedPhrase(),
                                    base.keywordKind(),
                                    base.matchSource()),
                            fs,
                            fs.getRoundInteractionSeq() + 1));
        }

        if (round == TravelReimbursementRound.DOC && attachments != null && !attachments.isEmpty()) {
            if (!routing.allowDocAdvanceWithAttachmentOnly()) {
                log.info(
                        "[意图·出差报销] 材料阶段须同时含触发短语，本轮仅上传附件未命中：租户 {}，会话 {}，意图编号 {}",
                        tenantId,
                        conversationId,
                        def.getId());
                return Optional.empty();
            }
            return Optional.of(
                    withFlow(
                            IntentKeywordMatchHit.create(
                                    null, "(上传附件)", null, ChatIntentMatchSource.DOC_ATTACHMENT),
                            fs,
                            fs.getRoundInteractionSeq() + 1));
        }
        return Optional.empty();
    }

    @Override
    public SseEmitter openStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit) {
        return openStream(
                conversationId,
                snap,
                payload,
                attachments,
                def,
                keywords,
                matchHit,
                IntentMatchContext.empty());
    }

    @Override
    public SseEmitter openStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit,
            IntentMatchContext flowContext) {
        TravelIntentRoutingConfig routing =
                TravelIntentRoutingConfig.parse(def.getExtraConfigJson(), objectMapper);
        long ttlMs = routing.sessionTtlMs();
        List<String> triggers = triggerPhrases(keywords);
        String msg = payload.getContent() == null ? "" : payload.getContent();

        String flowId =
                matchHit.intentFlowTicket() != null && !matchHit.intentFlowTicket().isBlank()
                        ? matchHit.intentFlowTicket().trim()
                        : (payload.getIntentFlowTicket() == null ? "" : payload.getIntentFlowTicket().trim());
        boolean triggerHit = messageContainsAny(msg, triggers);

        Optional<IntentFlowSession> loaded =
                flowId.isEmpty()
                        ? Optional.empty()
                        : intentFlowSessionStore.findValid(
                                snap.getTenantId(), conversationId, def.getId(), flowId);

        if (triggerHit && loaded.isPresent()) {
            IntentFlowSession s = loaded.get();
            TravelIntentHandlerState freshHs = new TravelIntentHandlerState(null, false, false);
            String resetRound = TravelReimbursementRound.DOC.name();
            if (matchHit.keywordId() != null) {
                for (ChatIntentKeyword k : keywords) {
                    if (k.getId() != null && k.getId().longValue() == matchHit.keywordId().longValue()) {
                        resetRound =
                                resolvedTargetRoundLabel(k, ChatIntentKeywordKind.TRIGGER)
                                        .filter(TravelReimbursementIntentRunner::isValidTravelEntryRound)
                                        .orElse(TravelReimbursementRound.DOC.name());
                        break;
                    }
                }
            }
            s.setCurrentRound(resetRound);
            try {
                persistTravelSession(s, freshHs, flowId, ttlMs);
            } catch (Exception e) {
                log.warn(
                        "[意图·出差报销] 触发词命中后重置会话失败：租户 {}",
                        snap.getTenantId(),
                        e);
            }
            loaded = intentFlowSessionStore.findValid(snap.getTenantId(), conversationId, def.getId(), flowId);
        }

        if (loaded.isEmpty()) {
            String expiredHint = routing.resolvedSessionExpiredHint();
            log.warn(
                    "[意图·出差报销] 未找到有效会话（可能已过期）：租户 {}，会话 {}，意图编号 {}",
                    snap.getTenantId(),
                    conversationId,
                    def.getId());
            log.info(
                    "[意图·出差报销] 会话无效，向用户返回过期提示：租户 {}，会话 {}，用户消息「{}」",
                    snap.getTenantId(),
                    conversationId,
                    intentChainPreview(msg));
            SseEmitter err = new SseEmitter(60_000L);
            Thread.startVirtualThread(
                    () -> {
                        try {
                            ObjectNode wrap = objectMapper.createObjectNode();
                            wrap.put("type", "content");
                            wrap.put("v", expiredHint);
                            err.send(
                                    org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .data(objectMapper.writeValueAsString(wrap))
                                            .id("1"));
                            err.send(
                                    org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, new AtomicInteger(1)))
                                            .id("2"));
                            err.complete();
                        } catch (Exception e) {
                            err.completeWithError(e);
                        }
                    });
            return err;
        }

        IntentFlowSession session = loaded.get();
        TravelIntentHandlerState handlerState = readHandlerState(session);
        String sessionRoundName = session.getCurrentRound();
        List<String> planKws = planContinuePhrases(keywords, sessionRoundName);

        boolean planDirect =
                !triggerHit
                        && TravelReimbursementRound.PLAN.name().equals(sessionRoundName)
                        && handlerState.getDocSummary() != null
                        && !handlerState.getDocSummary().isBlank()
                        && planContinueMatch(msg, planKws, sessionRoundName)
                        && !handlerState.isPlanDirectConsumed();

        log.info(
                "[意图·出差报销] 开始流式处理：租户 {}，会话 {}，意图编号 {}，编码 {}，阶段={}，行程直达={}，触发词命中={}，附件 {} 个，用户消息「{}」",
                snap.getTenantId(),
                conversationId,
                def.getId(),
                def.getCode(),
                IntentLogZh.travelRound(session.getCurrentRound()),
                IntentLogZh.yesNo(planDirect),
                IntentLogZh.yesNo(triggerHit),
                attachments == null ? 0 : attachments.size(),
                intentChainPreview(msg));

        final String flowIdFinal = flowId;
        final IntentFlowSession sessionFinal = session;
        final TravelIntentHandlerState handlerStateFinal = handlerState;

        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicInteger seq = new AtomicInteger(0);
        Thread.startVirtualThread(
                () -> {
                    TravelCozeRuntimeConfig cozeCfg = resolveTravelCoze(def);
                    List<ChatWorkflowSegmentView> segments = new ArrayList<>();
                    StringBuilder plainBuf = new StringBuilder();
                    try {
                        if (planDirect) {
                            log.info(
                                    "[意图·出差报销] 命中行程阶段直达，开始编排：租户 {}，会话 {}",
                                    snap.getTenantId(),
                                    conversationId);
                            handlerStateFinal.setPlanDirectConsumed(true);
                            persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                            runPlanPhases(
                                    emitter,
                                    seq,
                                    segments,
                                    plainBuf,
                                    msg,
                                    handlerStateFinal,
                                    sessionFinal,
                                    flowIdFinal,
                                    ttlMs,
                                    snap,
                                    payload,
                                    conversationId,
                                    cozeCfg);
                            finishPersistWithFlow(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments,
                                    matchHit,
                                    flowIdFinal,
                                    ttlMs);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                            return;
                        }

                        if (TravelReimbursementRound.DOC.name().equals(sessionFinal.getCurrentRound())) {
                            if (attachments == null || attachments.isEmpty()) {
                                log.info(
                                        "[意图·出差报销] 材料阶段缺少附件，返回上传引导：租户 {}，会话 {}",
                                        snap.getTenantId(),
                                        conversationId);
                                blockWithSpinner(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-need-file",
                                        MATERIAL_STEP_TITLE,
                                        "请先上传出差相关文件后再开始办理");
                                persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                                finishPersistWithFlow(
                                        snap,
                                        conversationId,
                                        payload,
                                        def,
                                        plainBuf.toString(),
                                        segments,
                                        matchHit,
                                        flowIdFinal,
                                        ttlMs);
                                emitter.send(
                                        SseEmitter.event()
                                                .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                                .id(String.valueOf(seq.incrementAndGet())));
                                emitter.complete();
                                return;
                            }
                            String docBody;
                            if (cozeCfg.allWorkflowKeysConfigured()) {
                                try {
                                    docBody =
                                            travelCozeWorkflowClient.collectWorkflowOutput(
                                                    cozeCfg.resolvedDomain(),
                                                    cozeCfg.docApiKey(),
                                                    cozeCfg.docWorkflowId(),
                                                    buildDocWorkflowParameters(
                                                            cozeCfg.resolvedDomain(),
                                                            cozeCfg.docApiKey(),
                                                            attachments,
                                                            msg));
                                    if (docBody == null || docBody.isBlank()) {
                                        log.warn(
                                                "[意图·Coze] 文档工作流无有效输出：租户 {}，会话 {}",
                                                snap.getTenantId(),
                                                conversationId);
                                        blockWithSpinner(
                                                emitter,
                                                seq,
                                                segments,
                                                plainBuf,
                                                "doc-parse-empty",
                                                DOC_WORKFLOW_STEP_TITLE,
                                                "文档解析未返回有效内容，请稍后重试或更换材料");
                                        persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                                        finishPersistWithFlow(
                                                snap,
                                                conversationId,
                                                payload,
                                                def,
                                                plainBuf.toString(),
                                                segments,
                                                matchHit,
                                                flowIdFinal,
                                                ttlMs);
                                        emitter.send(
                                                SseEmitter.event()
                                                        .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                                        .id(String.valueOf(seq.incrementAndGet())));
                                        emitter.complete();
                                        return;
                                    }
                                } catch (Exception e) {
                                    log.error(
                                            "[意图·Coze] 文档工作流调用失败：租户 {}，会话 {}",
                                            snap.getTenantId(),
                                            conversationId,
                                            e);
                                    blockWithSpinner(
                                            emitter,
                                            seq,
                                            segments,
                                            plainBuf,
                                            "doc-parse-error",
                                            DOC_WORKFLOW_STEP_TITLE,
                                            "文档解析暂时不可用，请稍后重试");
                                    persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                                    finishPersistWithFlow(
                                            snap,
                                            conversationId,
                                            payload,
                                            def,
                                            plainBuf.toString(),
                                            segments,
                                            matchHit,
                                            flowIdFinal,
                                            ttlMs);
                                    emitter.send(
                                            SseEmitter.event()
                                                    .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                                    .id(String.valueOf(seq.incrementAndGet())));
                                    emitter.complete();
                                    return;
                                }
                                streamTyping(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-parse",
                                        DOC_WORKFLOW_STEP_TITLE,
                                        docBody);
                                handlerStateFinal.setDocSummary(docBody);
                            } else {
                                docBody = simulateDocSummary(attachments, msg);
                                streamTyping(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-parse",
                                        DOC_WORKFLOW_STEP_TITLE,
                                        docBody);
                                handlerStateFinal.setDocSummary(docBody);
                            }
                            sessionFinal.setCurrentRound(TravelReimbursementRound.PLAN.name());
                            persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                            log.info(
                                    "[意图·出差报销] 材料阶段完成，已切换至行程阶段；请用户再发一条消息继续：租户 {}，会话 {}",
                                    snap.getTenantId(),
                                    conversationId);
                            finishPersistWithFlow(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments,
                                    matchHit,
                                    flowIdFinal,
                                    ttlMs);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                            return;
                        }

                        if (TravelReimbursementRound.PLAN.name().equals(sessionFinal.getCurrentRound())) {
                            if (handlerStateFinal.getDocSummary() == null
                                    || handlerStateFinal.getDocSummary().isBlank()) {
                                sessionFinal.setCurrentRound(TravelReimbursementRound.DOC.name());
                                blockWithSpinner(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-expired",
                                        MATERIAL_STEP_TITLE,
                                        "文件解析结果已过期，请重新上传文件后继续办理");
                            } else {
                                runPlanPhases(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        msg,
                                        handlerStateFinal,
                                        sessionFinal,
                                        flowIdFinal,
                                        ttlMs,
                                        snap,
                                        payload,
                                        conversationId,
                                        cozeCfg);
                            }
                            persistTravelSession(sessionFinal, handlerStateFinal, flowIdFinal, ttlMs);
                            finishPersistWithFlow(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments,
                                    matchHit,
                                    flowIdFinal,
                                    ttlMs);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        log.error(
                                "[意图·出差报销] 流式处理异常：租户 {}，会话 {}，意图编号 {}",
                                snap.getTenantId(),
                                conversationId,
                                def.getId(),
                                e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception secondary) {
                            log.warn(
                                    "[意图·出差报销] SSE 结束流失败：租户 {}，会话 {}",
                                    snap.getTenantId(),
                                    conversationId,
                                    secondary);
                            emitter.complete();
                        }
                    }
                });
        return emitter;
    }

    private void runPlanPhases(
            SseEmitter emitter,
            AtomicInteger seq,
            List<ChatWorkflowSegmentView> segments,
            StringBuilder plainBuf,
            String query,
            TravelIntentHandlerState handlerState,
            IntentFlowSession session,
            String flowId,
            long ttlMs,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            long conversationId,
            TravelCozeRuntimeConfig cozeCfg)
            throws Exception {
        if (messageContainsAny(query, APPLY_CONFIRMED_KEYWORDS)) {
            handlerState.setApplyConfirmed(true);
        }
        streamTyping(
                emitter,
                seq,
                segments,
                plainBuf,
                "plan-apply",
                PLAN_APPLY_CHECK_TITLE,
                PLAN_APPLY_CHECK_CONTENT_FULL);
        boolean hasApplyForm = ThreadLocalRandom.current().nextDouble() < 0.7d;
        if (!hasApplyForm && handlerState.isApplyConfirmed()) {
            hasApplyForm = true;
        }
        if (!hasApplyForm) {
            blockWithSpinner(
                    emitter,
                    seq,
                    segments,
                    plainBuf,
                    "plan-no-apply",
                    "出差申请提示",
                    "您还未发起出差申请单");
            persistTravelSession(session, handlerState, flowId, ttlMs);
            log.info(
                    "[意图·出差报销] 行程阶段：未命中申请单（模拟），会话保持行程阶段：租户 {}，会话 {}",
                    snap.getTenantId(),
                    conversationId);
            return;
        }
        boolean hasConflict = ThreadLocalRandom.current().nextDouble() < 0.5d;
        String conflictBody = hasConflict ? PLAN_CONFLICT_NOTICE_FULL : PLAN_NO_CONFLICT_NOTICE_FULL;
        streamTyping(
                emitter,
                seq,
                segments,
                plainBuf,
                "plan-conflict",
                PLAN_CONFLICT_CHECK_TITLE,
                conflictBody);
        String finalQuery = hasConflict ? query + "（有行程冲突）" : query;
        blockWithSpinner(emitter, seq, segments, plainBuf, "plan-kb", "知识库检索中...", "知识库检索中...");
        if (cozeCfg.allWorkflowKeysConfigured()) {
            try {
                String planResult =
                        travelCozeWorkflowClient.collectWorkflowOutput(
                                cozeCfg.resolvedDomain(),
                                cozeCfg.planApiKey(),
                                cozeCfg.planWorkflowId(),
                                buildPlanWorkflowParameters(handlerState.getDocSummary(), finalQuery));
                if (planResult == null || planResult.isBlank()) {
                    log.warn(
                            "[意图·Coze] 行程工作流无有效输出：租户 {}，会话 {}",
                            snap.getTenantId(),
                            conversationId);
                    streamTyping(
                            emitter,
                            seq,
                            segments,
                            plainBuf,
                            "plan-final",
                            PLAN_WORKFLOW_STEP_TITLE,
                            "行程规划未返回有效内容，请稍后重试");
                    persistTravelSession(session, handlerState, flowId, ttlMs);
                    return;
                }
                streamTyping(
                        emitter, seq, segments, plainBuf, "plan-final", PLAN_WORKFLOW_STEP_TITLE, planResult);
            } catch (Exception e) {
                log.error(
                        "[意图·Coze] 行程工作流调用失败：租户 {}，会话 {}",
                        snap.getTenantId(),
                        conversationId,
                        e);
                streamTyping(
                        emitter,
                        seq,
                        segments,
                        plainBuf,
                        "plan-final",
                        PLAN_WORKFLOW_STEP_TITLE,
                        "行程规划暂时不可用，请稍后重试");
                persistTravelSession(session, handlerState, flowId, ttlMs);
                return;
            }
            intentFlowSessionStore.remove(snap.getTenantId(), flowId);
            return;
        }
        String planResult =
                "【行程草案（模拟）】\n根据材料摘要与您的提问，建议行程如下：\n1）出发地与目的地已纳入统筹；\n2）请按单位差旅标准预订交通与住宿；\n3）如需改签请在系统内提交变更单。\n（租户未配置完整 Coze 工作流键时始终为模拟输出。）";
        streamTyping(emitter, seq, segments, plainBuf, "plan-final", PLAN_WORKFLOW_STEP_TITLE, planResult);
        intentFlowSessionStore.remove(snap.getTenantId(), flowId);
    }

    /**
     * Coze 文档工作流入参：与 ly {@code TravelReimbursementService#buildDocParameters} 对齐（{@code file} + {@code input}），并附带
     * {@code document_text}。
     */
    private Map<String, Object> buildDocWorkflowParameters(
            String cozeDomain, String docApiKey, List<ChatAttachment> attachments, String query) throws Exception {
        Map<String, Object> params = new HashMap<>(8);
        List<String> fileJsonParts = new ArrayList<>();
        StringBuilder docText = new StringBuilder();
        for (ChatAttachment a : attachments) {
            docText.append("### ")
                    .append(a.getFileName() == null ? "未命名附件" : a.getFileName())
                    .append("\n");
            String t = a.getExtractedText();
            docText.append(t == null || t.isBlank() ? "（无抽取文本）\n" : t).append("\n\n");

            byte[] stored =
                    attachmentBinStore
                            .load(
                                    a.getTenantId(),
                                    a.getConversationId(),
                                    a.getId() == null ? 0L : a.getId())
                            .orElse(null);
            byte[] uploadBytes;
            String uploadName;
            if (stored != null && stored.length > 0) {
                uploadBytes = stored;
                uploadName =
                        a.getFileName() == null || a.getFileName().isBlank() ? "upload.bin" : a.getFileName().trim();
            } else {
                String body = t == null || t.isBlank() ? "（无抽取文本）\n" : t;
                uploadBytes = body.getBytes(StandardCharsets.UTF_8);
                long aid = a.getId() == null ? 0L : a.getId();
                uploadName = "attachment-" + aid + "-extracted.txt";
            }
            String cozeFileId = travelCozeWorkflowClient.uploadFile(cozeDomain, docApiKey, uploadName, uploadBytes);
            fileJsonParts.add(toWorkflowFileParamJson(cozeFileId));
        }
        if (!fileJsonParts.isEmpty()) {
            if (fileJsonParts.size() == 1) {
                params.put("file", fileJsonParts.get(0));
            } else {
                params.put("file", fileJsonParts);
            }
        }
        params.put("document_text", docText.toString());
        params.put("input", query == null || query.isBlank() ? "帮我安排" : query.trim());
        return params;
    }

    private String toWorkflowFileParamJson(String cozeFileId) throws Exception {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("file_id", cozeFileId);
        return objectMapper.writeValueAsString(o);
    }

    /** 与 ly {@code TravelReimbursementService#buildPlanParameters} 一致。 */
    private static Map<String, Object> buildPlanWorkflowParameters(String docResult, String query) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("input", docResult);
        params.put("query", query);
        return params;
    }

    private static String simulateDocSummary(List<ChatAttachment> attachments, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("【材料解析摘要（模拟）】\n");
        for (ChatAttachment a : attachments) {
            sb.append("- 文件：")
                    .append(a.getFileName() == null ? "(未命名)" : a.getFileName())
                    .append("；类型：")
                    .append(a.getMimeType() == null ? "未知" : a.getMimeType())
                    .append("\n");
        }
        if (query != null && !query.isBlank()) {
            sb.append("用户补充说明：").append(query.trim()).append("\n");
        }
        sb.append("以上摘要用于后续行程规划工作流入参。");
        return sb.toString();
    }

    private void blockWithSpinner(
            SseEmitter emitter,
            AtomicInteger seq,
            List<ChatWorkflowSegmentView> segments,
            StringBuilder plainBuf,
            String segmentId,
            String title,
            String fullText)
            throws Exception {
        ChatIntentSseHelper.sendWorkflowStage(
                objectMapper, emitter, seq, segmentId, title, "block", "loading", "");
        Thread.sleep(380);
        ChatIntentSseHelper.sendWorkflowStage(
                objectMapper, emitter, seq, segmentId, title, "block", "done", fullText);
        segments.add(new ChatWorkflowSegmentView(segmentId, title, "block", "done", fullText));
        if (plainBuf.length() > 0) {
            plainBuf.append("\n\n");
        }
        plainBuf.append(fullText);
    }

    private void streamTyping(
            SseEmitter emitter,
            AtomicInteger seq,
            List<ChatWorkflowSegmentView> segments,
            StringBuilder plainBuf,
            String segmentId,
            String title,
            String fullText)
            throws Exception {
        ChatIntentSseHelper.sendWorkflowStage(
                objectMapper, emitter, seq, segmentId, title, "block", "loading", "");
        Thread.sleep(200);
        StringBuilder acc = new StringBuilder();
        int i = 0;
        while (i < fullText.length()) {
            int span = Math.min(3 + ThreadLocalRandom.current().nextInt(4), fullText.length() - i);
            acc.append(fullText, i, i + span);
            i += span;
            ChatIntentSseHelper.sendWorkflowStage(
                    objectMapper,
                    emitter,
                    seq,
                    segmentId,
                    title,
                    "block",
                    "streaming",
                    acc.toString());
            Thread.sleep(12 + ThreadLocalRandom.current().nextInt(40));
        }
        ChatIntentSseHelper.sendWorkflowStage(
                objectMapper, emitter, seq, segmentId, title, "block", "done", fullText);
        segments.add(new ChatWorkflowSegmentView(segmentId, title, "block", "done", fullText));
        if (plainBuf.length() > 0) {
            plainBuf.append("\n\n");
        }
        plainBuf.append(fullText);
    }

    private void finishPersist(
            TenantContextHolder.TenantSnapshot snap,
            long conversationId,
            ChatSendPayload payload,
            ChatIntentDefinition def,
            String content,
            List<ChatWorkflowSegmentView> segments,
            IntentKeywordMatchHit matchHit) {
        try {
            var asst = new ChatMessage();
            asst.setTenantId(snap.getTenantId());
            asst.setRole(ChatMessageRole.ASSISTANT);
            asst.setContent(content);
            asst.setMetaJson(buildIntentAssistantMeta(payload, def, segments, matchHit));
            messageRepository.insert(asst);
            var lnk = new LnkChatConversationMessage();
            lnk.setConversationId(conversationId);
            lnk.setMessageId(asst.getId());
            lnk.setSeq(0);
            lnkRepository.insert(lnk);
            conversationRepository.touchUpdatedAt(conversationId, snap.getTenantId());
            try {
                userMemoryApplicationService.afterAssistantUtterance(
                        snap, content, conversationId, payload.getModelAlias().trim());
            } catch (Exception memEx) {
                log.warn(
                        "[意图·出差报销] 助手回复后写入长期记忆失败：租户 {}，会话 {}",
                        snap.getTenantId(),
                        conversationId,
                        memEx);
            }
            chatTurnDigestApplicationService.scheduleTurnDigest(
                    snap,
                    conversationId,
                    asst.getId(),
                    payload.getContent(),
                    content,
                    payload.getModelAlias().trim(),
                    "mock".equalsIgnoreCase(payload.getModelAlias().trim()));
            if (matchHit.keywordId() != null) {
                int n = intentKeywordRepository.incrementHitCount(snap.getTenantId(), matchHit.keywordId());
                if (n == 0) {
                    log.warn(
                            "[意图·出差报销] 关键词命中次数未更新（记录不存在或租户不一致）：租户 {}，关键词编号 {}",
                            snap.getTenantId(),
                            matchHit.keywordId());
                }
            }
        } catch (Exception e) {
            log.error(
                    "[意图·出差报销] 持久化助手消息失败：租户 {}，会话 {}",
                    snap.getTenantId(),
                    conversationId,
                    e);
        }
    }

    private void finishPersistWithFlow(
            TenantContextHolder.TenantSnapshot snap,
            long conversationId,
            ChatSendPayload payload,
            ChatIntentDefinition def,
            String content,
            List<ChatWorkflowSegmentView> segments,
            IntentKeywordMatchHit matchHit,
            String flowId,
            long ttlMs) {
        finishPersist(snap, conversationId, payload, def, content, segments, matchHit);
        bumpCompletedInteraction(snap.getTenantId(), conversationId, def.getId(), flowId, ttlMs);
    }

    private String buildIntentAssistantMeta(
            ChatSendPayload payload,
            ChatIntentDefinition def,
            List<ChatWorkflowSegmentView> segments,
            IntentKeywordMatchHit matchHit)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("modelAlias", payload.getModelAlias());
        n.put("thinkingEnabled", payload.isThinkingEnabled());
        n.put("intentHandled", true);
        n.put("intentId", def.getId());
        n.put("intentCode", def.getCode());
        n.put("intentDisplayName", def.getDisplayName());
        n.put("intentMatchSource", matchHit.matchSource().name());
        if (matchHit.keywordId() != null) {
            n.put("intentHitKeywordId", matchHit.keywordId());
        }
        n.put("intentHitPhrase", matchHit.matchedPhrase() == null ? "" : matchHit.matchedPhrase());
        if (matchHit.keywordKind() != null) {
            n.put("intentHitKeywordKind", matchHit.keywordKind().name());
        }
        if (matchHit.intentFlowTicket() != null) {
            n.put("intentFlowTicket", matchHit.intentFlowTicket());
        }
        if (matchHit.intentFlowEpisodeId() != null) {
            n.put("intentFlowEpisodeId", matchHit.intentFlowEpisodeId());
        }
        if (matchHit.intentFlowRound() != null) {
            n.put("intentFlowRound", matchHit.intentFlowRound());
        }
        if (matchHit.intentFlowRoundSeq() != null) {
            n.put("intentFlowRoundSeq", matchHit.intentFlowRoundSeq());
        }
        ArrayNode arr = n.putArray("workflowSegments");
        for (ChatWorkflowSegmentView s : segments) {
            ObjectNode o = arr.addObject();
            o.put("segmentId", s.segmentId());
            if (s.title() != null) {
                o.put("title", s.title());
            }
            o.put("mode", s.mode());
            o.put("status", s.status());
            o.put("text", s.text());
        }
        return objectMapper.writeValueAsString(n);
    }

    /**
     * 配置的关键词目标轮次：空白则按 kind 推断（TRIGGER→DOC，PLAN_CONTINUE→PLAN）；非空须为 {@link TravelReimbursementRound}
     * 枚举名，否则该关键词不参与匹配（便于与其它多轮意图共用同一套「轮次标签」约定）。
     */
    private static Optional<String> resolvedTargetRoundLabel(ChatIntentKeyword k, ChatIntentKeywordKind kind) {
        String raw = k.getTargetRound();
        if (raw != null && !raw.isBlank()) {
            String t = raw.trim();
            try {
                TravelReimbursementRound.valueOf(t);
                return Optional.of(t);
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        if (kind == ChatIntentKeywordKind.TRIGGER) {
            return Optional.of(TravelReimbursementRound.DOC.name());
        }
        if (kind == ChatIntentKeywordKind.PLAN_CONTINUE) {
            return Optional.of(TravelReimbursementRound.PLAN.name());
        }
        return Optional.empty();
    }

    private static boolean isApplicableForRound(
            ChatIntentKeyword k, ChatIntentKeywordKind kind, String sessionRoundName) {
        return resolvedTargetRoundLabel(k, kind).filter(sessionRoundName::equals).isPresent();
    }

    /** 当前差旅处理器允许的「新开一局」入口轮次；扩展枚举后在此放宽。 */
    private static boolean isValidTravelEntryRound(String roundName) {
        return TravelReimbursementRound.DOC.name().equals(roundName);
    }

    private record TriggerScan(IntentKeywordMatchHit hit, String entryRoundName) {}

    private static Optional<TriggerScan> findFirstTriggerScan(String message, List<ChatIntentKeyword> keywords) {
        if (message == null || message.isEmpty()) {
            return Optional.empty();
        }
        for (ChatIntentKeyword k : keywords) {
            if (k.getKeywordKind() != ChatIntentKeywordKind.TRIGGER) {
                continue;
            }
            if (k.getEnabled() != ToggleState.ON) {
                continue;
            }
            Optional<String> entry = resolvedTargetRoundLabel(k, ChatIntentKeywordKind.TRIGGER);
            if (entry.isEmpty() || !isValidTravelEntryRound(entry.get())) {
                continue;
            }
            String p = k.getPhrase() == null ? "" : k.getPhrase().trim();
            if (!p.isEmpty() && message.contains(p)) {
                return Optional.of(
                        new TriggerScan(
                                IntentKeywordMatchHit.create(
                                        k.getId(),
                                        p,
                                        ChatIntentKeywordKind.TRIGGER,
                                        ChatIntentMatchSource.TRIGGER_PHRASE),
                                entry.get()));
            }
        }
        return Optional.empty();
    }

    private static IntentKeywordMatchHit resolvePlanContinueHit(
            String message, List<ChatIntentKeyword> keywords, String sessionRoundName) {
        String msg = message == null ? "" : message;
        for (ChatIntentKeyword k : keywords) {
            if (k.getKeywordKind() != ChatIntentKeywordKind.PLAN_CONTINUE) {
                continue;
            }
            if (k.getEnabled() != ToggleState.ON) {
                continue;
            }
            if (!isApplicableForRound(k, ChatIntentKeywordKind.PLAN_CONTINUE, sessionRoundName)) {
                continue;
            }
            String p = k.getPhrase() == null ? "" : k.getPhrase().trim();
            if (!p.isEmpty() && msg.contains(p)) {
                return IntentKeywordMatchHit.create(
                        k.getId(),
                        p,
                        ChatIntentKeywordKind.PLAN_CONTINUE,
                        ChatIntentMatchSource.PLAN_CONTINUE_PHRASE);
            }
        }
        if (TravelReimbursementRound.PLAN.name().equals(sessionRoundName)) {
            for (String d : DEFAULT_PLAN_CONTINUE_KEYWORDS) {
                if (d != null && !d.isEmpty() && msg.contains(d)) {
                    return IntentKeywordMatchHit.create(
                            null,
                            d,
                            ChatIntentKeywordKind.PLAN_CONTINUE,
                            ChatIntentMatchSource.PLAN_CONTINUE_DEFAULT_PHRASE);
                }
            }
            for (Pattern pat : PLAN_CONTINUE_PATTERNS) {
                if (pat.matcher(msg).matches()) {
                    return IntentKeywordMatchHit.create(
                            null,
                            "(行程续办正则)",
                            ChatIntentKeywordKind.PLAN_CONTINUE,
                            ChatIntentMatchSource.PLAN_CONTINUE_REGEX);
                }
            }
        }
        return IntentKeywordMatchHit.create(
                null,
                "",
                ChatIntentKeywordKind.PLAN_CONTINUE,
                ChatIntentMatchSource.PLAN_CONTINUE_REGEX);
    }

    private static List<String> triggerPhrases(List<ChatIntentKeyword> keywords) {
        return keywords.stream()
                .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.TRIGGER)
                .filter(k -> k.getEnabled() == ToggleState.ON)
                .filter(
                        k ->
                                resolvedTargetRoundLabel(k, ChatIntentKeywordKind.TRIGGER)
                                        .filter(TravelReimbursementIntentRunner::isValidTravelEntryRound)
                                        .isPresent())
                .map(ChatIntentKeyword::getPhrase)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> planContinuePhrases(List<ChatIntentKeyword> keywords, String sessionRoundName) {
        List<String> fromDb =
                keywords.stream()
                        .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.PLAN_CONTINUE)
                        .filter(k -> k.getEnabled() == ToggleState.ON)
                        .filter(k -> isApplicableForRound(k, ChatIntentKeywordKind.PLAN_CONTINUE, sessionRoundName))
                        .map(ChatIntentKeyword::getPhrase)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        if (TravelReimbursementRound.PLAN.name().equals(sessionRoundName)) {
            return List.of(DEFAULT_PLAN_CONTINUE_KEYWORDS);
        }
        return List.of();
    }

    private static boolean planContinueMatch(String message, List<String> planKws, String sessionRoundName) {
        if (messageContainsAny(message, planKws.toArray(new String[0]))) {
            return true;
        }
        if (!TravelReimbursementRound.PLAN.name().equals(sessionRoundName)) {
            return false;
        }
        for (Pattern p : PLAN_CONTINUE_PATTERNS) {
            if (p.matcher(message).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageContainsAny(String message, String[] keywords) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && message.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageContainsAny(String message, List<String> keywords) {
        return messageContainsAny(message, keywords.toArray(new String[0]));
    }

    /** 与 {@link ChatIntentStreamRouter} 一致：意图链路日志截取用户输入前 80 字。 */
    private static String intentChainPreview(String message) {
        if (message == null) {
            return "";
        }
        String t = message.strip();
        if (t.length() <= 80) {
            return t;
        }
        return t.substring(0, 80) + "...";
    }
}
