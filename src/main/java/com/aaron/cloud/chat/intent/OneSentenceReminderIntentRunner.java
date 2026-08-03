package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.chat.intent.followup.IntentFollowUpContext;
import com.aaron.cloud.chat.intent.followup.IntentFollowUpMetaSupport;
import com.aaron.cloud.chat.intent.followup.IntentFollowUpPromptCatalog;
import com.aaron.cloud.chat.intent.followup.IntentSseTurnFinisher;
import com.aaron.cloud.chat.intent.reminder.OneSentenceReminderHandlerParam;
import com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin;
import com.aaron.cloud.chat.reminder.ChatUserReminderOrchestrator;
import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport;
import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport.IndexParseKind;
import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.chat.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.chat.ChatIntentMatchSource;
import com.aaron.cloud.common.api.enums.chat.ChatIntentRouterParticipation;
import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatIntentKeywordRepository;
import com.aaron.cloud.common.chat.ChatUserReminderRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.entity.LnkChatConversationMessage;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class OneSentenceReminderIntentRunner implements ChatIntentHandlerPlugin {

    private final ChatUserReminderOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final ChatMessageRepository messageRepository;
    private final LnkChatConversationMessageRepository lnkRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatIntentKeywordRepository intentKeywordRepository;
    private final ChatUserReminderRepository reminderRepository;

    @Override
    public ChatIntentHandlerKind kind() {
        return ChatIntentHandlerKind.ONE_SENTENCE_REMINDER;
    }

    @Override
    public ChatIntentRouterParticipation routerParticipation() {
        return ChatIntentRouterParticipation.ALWAYS_SCAN;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends Enum> handlerParamEnumClass() {
        return OneSentenceReminderHandlerParam.class;
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
        String msg = payload.getContent() == null ? "" : payload.getContent();
        Optional<IntentKeywordMatchHit> cancel = matchPhrase(msg, keywords, ChatIntentKeywordKind.CANCEL);
        if (cancel.isPresent()) {
            return cancel;
        }
        Optional<IntentKeywordMatchHit> trigger = matchPhrase(msg, keywords, ChatIntentKeywordKind.TRIGGER);
        if (trigger.isPresent()) {
            return trigger;
        }
        return matchCancelIndexReply(conversationId, tenantId, msg);
    }

    /** 上一轮助手已展示取消编号清单后，用户仅回复「1」「1、2」等仍走取消提醒意图。 */
    private Optional<IntentKeywordMatchHit> matchCancelIndexReply(
            long conversationId, long tenantId, String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (!ReminderCancelSelectionGate.isAwaitingSelection(
                conversationId, tenantId, messageRepository, lnkRepository, objectMapper)) {
            return Optional.empty();
        }
        return conversationRepository
                .findById(conversationId, tenantId)
                .flatMap(
                        conv -> {
                            Long userId = conv.getUserId();
                            if (userId == null) {
                                return Optional.empty();
                            }
                            int activeCount =
                                    reminderRepository.listActiveByUser(tenantId, userId).size();
                            if (activeCount <= 0) {
                                return Optional.empty();
                            }
                            var outcome =
                                    ReminderCancelSelectionSupport.parseListIndices(
                                            ReminderCancelSelectionSupport.stripCancelNoise(message),
                                            activeCount);
                            if (outcome.kind() == IndexParseKind.NOT_SELECTION) {
                                return Optional.empty();
                            }
                            return Optional.of(
                                    IntentKeywordMatchHit.create(
                                                    null,
                                                    message.strip(),
                                                    ChatIntentKeywordKind.CANCEL,
                                                    ChatIntentMatchSource.CANCEL_INDEX_REPLY)
                                            .withFlowMeta(
                                                    null,
                                                    null,
                                                    OneSentenceReminderRound.CANCEL_SELECT.name(),
                                                    null));
                        });
    }

    @Override
    public SseEmitter openStream(
            long conversationId,
            TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit) {
        return openStream(
                conversationId, snap, payload, attachments, def, keywords, matchHit, IntentMatchContext.empty());
    }

    @Override
    public SseEmitter openStream(
            long conversationId,
            TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit,
            IntentMatchContext flowContext) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicInteger seq = new AtomicInteger(0);
        Thread.startVirtualThread(
                () -> {
                    try {
                        ChatUserReminderOrchestrator.TurnResult result =
                                orchestrator.handleTurn(conversationId, snap, payload, def, matchHit);
                        if (result.success()) {
                            log.info(
                                    "[意图·提醒] ⑤ 回合完成 tenantId={} conversationId={} success=true",
                                    snap.getTenantId(),
                                    conversationId);
                        } else {
                            log.info(
                                    "[意图·提醒] ⑤ 回合完成 tenantId={} conversationId={} success=false userMsg={}",
                                    snap.getTenantId(),
                                    conversationId,
                                    result.userFacingError());
                        }
                        String text =
                                result.success()
                                        ? result.assistantText()
                                        : (result.userFacingError() == null
                                                ? "处理失败，请重试"
                                                : result.userFacingError());
                        ChatIntentSseHelper.sendContentChunk(objectMapper, emitter, seq, text);
                        IntentFollowUpContext followUp =
                                result.success()
                                        ? IntentFollowUpContext.reminderAfterSuccess(keywords)
                                        : IntentFollowUpContext.none();
                        List<String> followUpTexts = IntentFollowUpPromptCatalog.resolve(followUp);
                        String metaToSave =
                                result.success()
                                        ? IntentFollowUpMetaSupport.mergeIntoMetaJson(
                                                objectMapper, result.assistantMetaJson(), followUpTexts)
                                        : null;
                        persistAssistant(
                                conversationId,
                                snap,
                                payload,
                                def,
                                matchHit,
                                text,
                                metaToSave);
                        IntentSseTurnFinisher.sendFollowUpAndEnd(objectMapper, emitter, seq, followUpTexts);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error(
                                "[意图·提醒] SSE 失败 tenantId={} conversationId={}",
                                snap.getTenantId(),
                                conversationId,
                                e);
                        try {
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                });
        return emitter;
    }

    private void persistAssistant(
            long conversationId,
            TenantSnapshot snap,
            ChatSendPayload payload,
            ChatIntentDefinition def,
            IntentKeywordMatchHit matchHit,
            String content,
            String metaJson) {
        try {
            var asst = new ChatMessage();
            asst.setTenantId(snap.getTenantId());
            asst.setRole(ChatMessageRole.ASSISTANT);
            asst.setContent(content);
            asst.setMetaJson(metaJson);
            messageRepository.insert(asst);
            var lnk = new LnkChatConversationMessage();
            lnk.setConversationId(conversationId);
            lnk.setMessageId(asst.getId());
            lnk.setSeq(0);
            lnkRepository.insert(lnk);
            conversationRepository.touchUpdatedAt(conversationId, snap.getTenantId());
            if (matchHit.keywordId() != null) {
                intentKeywordRepository.incrementHitCount(snap.getTenantId(), matchHit.keywordId());
            }
        } catch (Exception e) {
            log.error(
                    "[意图·提醒] 持久化助手消息失败 tenantId={} conversationId={}",
                    snap.getTenantId(),
                    conversationId,
                    e);
        }
    }

    private static Optional<IntentKeywordMatchHit> matchPhrase(
            String message, List<ChatIntentKeyword> keywords, ChatIntentKeywordKind kind) {
        if (message == null || message.isEmpty()) {
            return Optional.empty();
        }
        OneSentenceReminderRound round =
                kind == ChatIntentKeywordKind.CANCEL
                        ? OneSentenceReminderRound.CANCEL
                        : OneSentenceReminderRound.CREATE;
        ChatIntentMatchSource source =
                kind == ChatIntentKeywordKind.CANCEL
                        ? ChatIntentMatchSource.CANCEL_PHRASE
                        : ChatIntentMatchSource.TRIGGER_PHRASE;
        for (ChatIntentKeyword k : keywords) {
            if (k.getKeywordKind() != kind || k.getEnabled() != ToggleState.ON) {
                continue;
            }
            String p = k.getPhrase() == null ? "" : k.getPhrase().trim();
            if (!p.isEmpty() && message.contains(p)) {
                return Optional.of(
                        IntentKeywordMatchHit.create(k.getId(), p, kind, source)
                                .withFlowMeta(null, null, round.name(), null));
            }
        }
        return Optional.empty();
    }
}
