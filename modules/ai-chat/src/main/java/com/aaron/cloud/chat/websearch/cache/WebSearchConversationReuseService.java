package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.common.tenant.runtime.WebSearchGroundingCachePolicy;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 方案 D：同一会话内，规范化问句相同且用户消息 meta 已含联网引用时直接复用（滚动小时窗口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchConversationReuseService {

    private static final int MAX_MESSAGES_SCAN = 40;

    private final LnkChatConversationMessageRepository linkRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public Optional<WebGroundingBundle> tryReuse(
            long tenantId,
            long conversationId,
            String normalizedQuery,
            WebSearchGroundingCachePolicy policy) {
        if (conversationId <= 0L || normalizedQuery == null || normalizedQuery.isBlank()) {
            return Optional.empty();
        }
        int hours = Math.max(0, policy.conversationReuseHours());
        if (hours <= 0) {
            return Optional.empty();
        }
        long maxAgeMs = Duration.ofHours(hours).toMillis();
        List<Long> ids = linkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        int from = Math.max(0, ids.size() - MAX_MESSAGES_SCAN);
        for (int i = ids.size() - 1; i >= from; i--) {
            Long mid = ids.get(i);
            Optional<ChatMessage> opt = messageRepository.findById(mid, tenantId);
            if (opt.isEmpty()) {
                continue;
            }
            ChatMessage m = opt.get();
            if (m.getRole() != ChatMessageRole.USER) {
                continue;
            }
            if (!matchesNormalizedWebQuery(objectMapper, m, normalizedQuery)) {
                continue;
            }
            if (m.getCreatedAt() == null) {
                continue;
            }
            long ageMs =
                    Duration.between(m.getCreatedAt().toInstant(ZoneOffset.UTC), Instant.now())
                            .toMillis();
            if (ageMs > maxAgeMs) {
                continue;
            }
            List<WebSearchReference> refs =
                    WebSearchGroundingMetaSupport.parseReferencesFromMeta(objectMapper, m.getMetaJson());
            if (refs.isEmpty()) {
                continue;
            }
            log.info(
                    "[联网缓存] 会话内复用：租户 {}，会话 {}，消息 {}，引用 {} 条，ageMs={}",
                    tenantId,
                    conversationId,
                    mid,
                    refs.size(),
                    ageMs);
            return Optional.of(new WebGroundingBundle("", List.copyOf(refs)));
        }
        return Optional.empty();
    }

    private static boolean matchesNormalizedWebQuery(
            ObjectMapper objectMapper, ChatMessage m, String normalizedQuery) {
        String metaNorm = WebSearchGroundingMetaSupport.readQueryNormFromMeta(objectMapper, m.getMetaJson());
        if (metaNorm != null && !metaNorm.isBlank()) {
            return normalizedQuery.equals(metaNorm);
        }
        String contentNorm = WebSearchQueryNormalizer.normalize(m.getContent());
        return normalizedQuery.equals(contentNorm);
    }
}
