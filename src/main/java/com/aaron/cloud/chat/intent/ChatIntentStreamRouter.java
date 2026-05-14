package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.intent.flow.IntentFlowSession;
import com.aaron.cloud.chat.intent.flow.IntentFlowSessionStore;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin;
import com.aaron.cloud.chat.intent.spi.IntentHandlerPluginRegistry;
import com.aaron.cloud.common.chat.ChatIntentDefinitionRepository;
import com.aaron.cloud.common.chat.ChatIntentKeywordRepository;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.context.TenantContextHolder;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 在对话 SSE 主链之前尝试命中租户已启用意图；命中则返回专用路由结果，否则由调用方走大模型流。
 *
 * <p>处理器由 {@link IntentHandlerPluginRegistry} 按 {@link com.aaron.cloud.common.api.enums.ChatIntentHandlerKind} 解析，禁止在本类写死分支。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntentStreamRouter {

    private final ChatIntentDefinitionRepository intentDefinitionRepository;
    private final ChatIntentKeywordRepository intentKeywordRepository;
    private final IntentHandlerPluginRegistry intentHandlerPluginRegistry;
    private final IntentFlowSessionStore intentFlowSessionStore;

    public Optional<IntentSseRoute> maybeRouteIntentStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments) {
        List<ChatIntentDefinition> defs = intentDefinitionRepository.listEnabledForRuntime(snap.getTenantId());
        if (defs.isEmpty()) {
            log.info(
                    "[意图链路] 当前租户无启用意图定义，跳过意图路由 tenantId={} conversationId={}",
                    snap.getTenantId(),
                    conversationId);
            return Optional.empty();
        }
        IntentMatchContext flowContext = resolveFlowContext(snap.getTenantId(), conversationId, payload);
        List<ChatIntentDefinition> defsToScan = defs;
        if (flowContext.hasValidSession()) {
            IntentFlowSession fs = flowContext.session().get();
            defsToScan =
                    defs.stream()
                            .filter(d -> d.getId() == fs.getIntentDefinitionId())
                            .toList();
            if (defsToScan.isEmpty()) {
                log.info(
                        "[意图链路] 票据有效但意图未启用或不存在，忽略票据 tenantId={} conversationId={} intentId={}",
                        snap.getTenantId(),
                        conversationId,
                        fs.getIntentDefinitionId());
                flowContext = IntentMatchContext.empty();
                defsToScan = defs;
            }
        }
        String preview = intentMessagePreview(payload.getContent());
        for (ChatIntentDefinition def : defsToScan) {
            Optional<ChatIntentHandlerPlugin> plugin = intentHandlerPluginRegistry.get(def.getHandlerKind());
            if (plugin.isEmpty()) {
                log.debug(
                        "chat intent handler not registered tenantId={} code={} kind={}",
                        snap.getTenantId(),
                        def.getCode(),
                        def.getHandlerKind());
                continue;
            }
            var kws = intentKeywordRepository.listByIntent(snap.getTenantId(), def.getId());
            Optional<IntentKeywordMatchHit> hit =
                    plugin.get()
                            .evaluateKeywordMatch(
                                    conversationId,
                                    snap.getTenantId(),
                                    def,
                                    kws,
                                    payload,
                                    attachments,
                                    flowContext);
            if (hit.isEmpty()) {
                continue;
            }
            IntentKeywordMatchHit h = hit.get();
            log.info(
                    "[意图链路] 命中意图 SSE 路由 tenantId={} conversationId={} intentId={} intentCode={} handler={} keywordId={} matchSource={} matchedPhrasePreview={} attachmentCount={} messagePreview={} flowTicket={}",
                    snap.getTenantId(),
                    conversationId,
                    def.getId(),
                    def.getCode(),
                    def.getHandlerKind(),
                    h.keywordId(),
                    h.matchSource(),
                    intentMessagePreview(h.matchedPhrase()),
                    attachments == null ? 0 : attachments.size(),
                    preview,
                    h.intentFlowTicket() != null ? "yes" : "no");
            return Optional.of(
                    new IntentSseRoute(
                            plugin.get()
                                    .openStream(
                                            conversationId,
                                            snap,
                                            payload,
                                            attachments,
                                            def,
                                            kws,
                                            h,
                                            flowContext),
                            def,
                            h));
        }
        log.info(
                "[意图链路] 已检查 {} 条启用意图但未命中关键词匹配，继续大模型主链 tenantId={} conversationId={} messagePreview={}",
                defsToScan.size(),
                snap.getTenantId(),
                conversationId,
                preview);
        return Optional.empty();
    }

    private IntentMatchContext resolveFlowContext(long tenantId, long conversationId, ChatSendPayload payload) {
        String ticket = payload.getIntentFlowTicket();
        if (ticket == null || ticket.isBlank()) {
            return IntentMatchContext.empty();
        }
        Optional<IntentFlowSession> s = intentFlowSessionStore.findByTicket(tenantId, conversationId, ticket);
        if (s.isEmpty()) {
            log.info(
                    "[意图链路] 意图流票据无效或已过期 tenantId={} conversationId={}",
                    tenantId,
                    conversationId);
            return IntentMatchContext.empty();
        }
        return new IntentMatchContext(s, ticket.trim());
    }

    /** 意图链路日志用：截断用户原文，避免日志过长（与 ly DialogueApiService 预览长度对齐）。 */
    private static String intentMessagePreview(String message) {
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
