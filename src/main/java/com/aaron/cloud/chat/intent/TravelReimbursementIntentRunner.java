package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.dto.ChatWorkflowSegmentView;
import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ChatMessageRole;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.LnkChatConversationMessage;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>默认在无 Coze 配置（{@code extra_config_json} 未填工作流密钥）时走<strong>可演示模拟路径</strong>，仍下发 {@code workflowStage}
 * 帧以便 C 端专用样式展示；接入 Coze 时在同一状态机内替换解析实现即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelReimbursementIntentRunner {

    private static final String STAGE_DOC = "DOC";
    private static final String STAGE_PLAN = "PLAN";
    private static final int CACHE_TTL_MS = 600_000;

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
    private static final String PLAN_CONFLICT_CHECK_TITLE = "判断是否有行程冲突";
    private static final String PLAN_CONFLICT_NOTICE_FULL =
            "行程冲突通知：您在系统登记的出差期间，检测到存在行程冲突信息，可能影响正常出行与工作安排。请您及时核对行程明细，对出差时间、出行计划进行合理调整，避免因冲突造成不便。如需协助处理行程调整事宜，可联系行政部门沟通协调。";
    private static final String PLAN_NO_CONFLICT_NOTICE_FULL =
            "无行程冲突通知：经系统核查，您在本次出差期间无其他行程冲突，行程安排顺畅有序。请您安心做好出行准备，按时开展相关工作。祝您一路顺风，出差工作顺利、平安返程。";

    private static final ConcurrentHashMap<String, TravelFlowState> CACHE = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatConversationRepository conversationRepository;

    private static String cacheKey(long tenantId, long conversationId, long intentId) {
        return tenantId + ":" + conversationId + ":" + intentId;
    }

    private static void touch(TravelFlowState s) {
        s.setUpdatedAtMs(System.currentTimeMillis());
    }

    private static boolean isStale(TravelFlowState s) {
        return System.currentTimeMillis() - s.getUpdatedAtMs() > CACHE_TTL_MS;
    }

    public boolean shouldHandle(
            long conversationId,
            long tenantId,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            ChatSendPayload payload,
            List<ChatAttachment> attachments) {
        if (def.getHandlerKind() != ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT) {
            return false;
        }
        List<String> triggers = triggerPhrases(keywords);
        List<String> planKws = planContinuePhrases(keywords);
        String msg = payload.getContent() == null ? "" : payload.getContent();
        String key = cacheKey(tenantId, conversationId, def.getId());
        TravelFlowState st = CACHE.get(key);
        if (st != null && isStale(st)) {
            CACHE.remove(key, st);
            st = null;
        }
        if (st != null
                && STAGE_PLAN.equals(st.getStage())
                && st.getDocSummary() != null
                && !st.getDocSummary().isBlank()
                && planContinueMatch(msg, planKws)
                && !st.isPlanDirectConsumed()) {
            return true;
        }
        if (st != null && STAGE_DOC.equals(st.getStage()) && attachments != null && !attachments.isEmpty()) {
            return true;
        }
        return messageContainsAny(msg, triggers);
    }

    public SseEmitter openStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords) {
        List<String> triggers = triggerPhrases(keywords);
        List<String> planKws = planContinuePhrases(keywords);
        String msg = payload.getContent() == null ? "" : payload.getContent();
        String key = cacheKey(snap.getTenantId(), conversationId, def.getId());
        TravelFlowState st = CACHE.get(key);
        if (st != null && isStale(st)) {
            CACHE.remove(key, st);
            st = null;
        }

        boolean planDirect =
                st != null
                        && STAGE_PLAN.equals(st.getStage())
                        && st.getDocSummary() != null
                        && !st.getDocSummary().isBlank()
                        && planContinueMatch(msg, planKws)
                        && !st.isPlanDirectConsumed();

        boolean triggerHit = messageContainsAny(msg, triggers);
        if (triggerHit) {
            st = new TravelFlowState(STAGE_DOC, null, false, false, System.currentTimeMillis());
            CACHE.put(key, st);
        } else if (st == null) {
            log.warn(
                    "travel intent stream missing cache state tenantId={} conversationId={} intentId={}",
                    snap.getTenantId(),
                    conversationId,
                    def.getId());
            SseEmitter err = new SseEmitter(60_000L);
            Thread.startVirtualThread(
                    () -> {
                        try {
                            ObjectNode wrap = objectMapper.createObjectNode();
                            wrap.put("type", "content");
                            wrap.put("v", "出差办理会话已过期，请再次发送「出差报销」等触发语后开始。");
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

        final TravelFlowState state = st;
        touch(state);

        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicInteger seq = new AtomicInteger(0);
        Thread.startVirtualThread(
                () -> {
                    List<ChatWorkflowSegmentView> segments = new ArrayList<>();
                    StringBuilder plainBuf = new StringBuilder();
                    try {
                        if (planDirect) {
                            state.setPlanDirectConsumed(true);
                            runPlanPhases(
                                    emitter,
                                    seq,
                                    segments,
                                    plainBuf,
                                    msg,
                                    state,
                                    key,
                                    snap,
                                    payload,
                                    conversationId);
                            finishPersist(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                            return;
                        }

                        if (STAGE_DOC.equals(state.getStage())) {
                            if (attachments == null || attachments.isEmpty()) {
                                blockWithSpinner(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-need-file",
                                        null,
                                        "请先上传出差相关文件后再开始办理");
                                touch(state);
                                finishPersist(
                                        snap,
                                        conversationId,
                                        payload,
                                        def,
                                        plainBuf.toString(),
                                        segments);
                                emitter.send(
                                        SseEmitter.event()
                                                .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                                .id(String.valueOf(seq.incrementAndGet())));
                                emitter.complete();
                                return;
                            }
                            String docSimulated = simulateDocSummary(attachments, msg);
                            streamTyping(
                                    emitter,
                                    seq,
                                    segments,
                                    plainBuf,
                                    "doc-parse",
                                    null,
                                    docSimulated);
                            state.setDocSummary(docSimulated);
                            state.setStage(STAGE_PLAN);
                            touch(state);
                            runPlanPhases(
                                    emitter,
                                    seq,
                                    segments,
                                    plainBuf,
                                    msg,
                                    state,
                                    key,
                                    snap,
                                    payload,
                                    conversationId);
                            finishPersist(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                            return;
                        }

                        if (STAGE_PLAN.equals(state.getStage())) {
                            if (state.getDocSummary() == null || state.getDocSummary().isBlank()) {
                                state.setStage(STAGE_DOC);
                                blockWithSpinner(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        "doc-expired",
                                        null,
                                        "文件解析结果已过期，请重新上传文件后继续办理");
                            } else {
                                runPlanPhases(
                                        emitter,
                                        seq,
                                        segments,
                                        plainBuf,
                                        msg,
                                        state,
                                        key,
                                        snap,
                                        payload,
                                        conversationId);
                            }
                            finishPersist(
                                    snap,
                                    conversationId,
                                    payload,
                                    def,
                                    plainBuf.toString(),
                                    segments);
                            emitter.send(
                                    SseEmitter.event()
                                            .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                                            .id(String.valueOf(seq.incrementAndGet())));
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        log.error(
                                "travel intent stream failed tenantId={} conversationId={} intentId={}",
                                snap.getTenantId(),
                                conversationId,
                                def.getId(),
                                e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {
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
            TravelFlowState state,
            String cacheKey,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            long conversationId)
            throws Exception {
        if (messageContainsAny(query, APPLY_CONFIRMED_KEYWORDS)) {
            state.setApplyConfirmed(true);
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
        if (!hasApplyForm && state.isApplyConfirmed()) {
            hasApplyForm = true;
        }
        if (!hasApplyForm) {
            blockWithSpinner(
                    emitter, seq, segments, plainBuf, "plan-no-apply", null, "您还未发起出差申请单");
            CACHE.remove(cacheKey);
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
        blockWithSpinner(emitter, seq, segments, plainBuf, "plan-kb", "知识库检索中...", "知识库检索中...");
        String planResult =
                "【行程草案（模拟）】\n根据材料摘要与您的提问，建议行程如下：\n1）出发地与目的地已纳入统筹；\n2）请按单位差旅标准预订交通与住宿；\n3）如需改签请在系统内提交变更单。\n（连接 Coze 行程工作流后，本段将替换为工作流输出。）";
        streamTyping(emitter, seq, segments, plainBuf, "plan-final", null, planResult);
        CACHE.remove(cacheKey);
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
            List<ChatWorkflowSegmentView> segments) {
        try {
            var asst = new ChatMessage();
            asst.setTenantId(snap.getTenantId());
            asst.setRole(ChatMessageRole.ASSISTANT);
            asst.setContent(content);
            asst.setMetaJson(buildIntentAssistantMeta(payload, def, segments));
            messageRepository.insert(asst);
            var lnk = new LnkChatConversationMessage();
            lnk.setConversationId(conversationId);
            lnk.setMessageId(asst.getId());
            lnk.setSeq(0);
            lnkRepository.insert(lnk);
            conversationRepository.touchUpdatedAt(conversationId, snap.getTenantId());
        } catch (Exception e) {
            log.error(
                    "persist travel intent assistant message failed tenantId={} conversationId={}",
                    snap.getTenantId(),
                    conversationId,
                    e);
        }
    }

    private String buildIntentAssistantMeta(
            ChatSendPayload payload, ChatIntentDefinition def, List<ChatWorkflowSegmentView> segments)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("modelAlias", payload.getModelAlias());
        n.put("thinkingEnabled", payload.isThinkingEnabled());
        n.put("intentHandled", true);
        n.put("intentCode", def.getCode());
        n.put("intentDisplayName", def.getDisplayName());
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

    private static List<String> triggerPhrases(List<ChatIntentKeyword> keywords) {
        return keywords.stream()
                .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.TRIGGER)
                .map(ChatIntentKeyword::getPhrase)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> planContinuePhrases(List<ChatIntentKeyword> keywords) {
        List<String> fromDb =
                keywords.stream()
                        .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.PLAN_CONTINUE)
                        .map(ChatIntentKeyword::getPhrase)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return List.of(DEFAULT_PLAN_CONTINUE_KEYWORDS);
    }

    private static boolean planContinueMatch(String message, List<String> planKws) {
        if (messageContainsAny(message, planKws.toArray(new String[0]))) {
            return true;
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
}
