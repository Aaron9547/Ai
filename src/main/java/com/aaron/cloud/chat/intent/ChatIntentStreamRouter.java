package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 在对话 SSE 主链之前尝试命中租户已启用意图；命中则返回专用 {@link SseEmitter}，否则由调用方走大模型流。
 *
 * <p>新增处理器：扩展 {@link ChatIntentHandlerKind}、实现对应 Runner 并在本类分支注册。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntentStreamRouter {

    private final ChatIntentDefinitionRepository intentDefinitionRepository;
    private final ChatIntentKeywordRepository intentKeywordRepository;
    private final TravelReimbursementIntentRunner travelReimbursementIntentRunner;

    public Optional<SseEmitter> maybeRouteIntentStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments) {
        List<ChatIntentDefinition> defs = intentDefinitionRepository.listEnabledForRuntime(snap.getTenantId());
        if (defs.isEmpty()) {
            return Optional.empty();
        }
        for (ChatIntentDefinition def : defs) {
            var kws = intentKeywordRepository.listByIntent(snap.getTenantId(), def.getId());
            if (def.getHandlerKind() == ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT) {
                if (travelReimbursementIntentRunner.shouldHandle(
                        conversationId, snap.getTenantId(), def, kws, payload, attachments)) {
                    log.info(
                            "chat intent routed tenantId={} conversationId={} intentCode={} handler={}",
                            snap.getTenantId(),
                            conversationId,
                            def.getCode(),
                            def.getHandlerKind());
                    return Optional.of(
                            travelReimbursementIntentRunner.openStream(
                                    conversationId, snap, payload, attachments, def, kws));
                }
            } else {
                log.debug("chat intent handler not wired tenantId={} code={}", snap.getTenantId(), def.getCode());
            }
        }
        return Optional.empty();
    }
}
