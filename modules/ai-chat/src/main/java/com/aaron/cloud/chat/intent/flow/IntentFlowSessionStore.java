package com.aaron.cloud.chat.intent.flow;

import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 意图流会话持久化：优先 Redis（多副本一致），无 Bean 或 Redis 异常时退化到进程内 Map（仅单机）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentFlowSessionStore {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

    private final ConcurrentHashMap<String, CacheEntry> local = new ConcurrentHashMap<>();

    private static String redisKeyPrefix() {
        return AiInternalResourceNames.RedisKeyPrefixes.INTENT_FLOW;
    }

    private static final class CacheEntry {
        final String json;
        final long expiresAtMs;

        CacheEntry(String json, long expiresAtMs) {
            this.json = json;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private String redisKey(long tenantId, String flowId) {
        return redisKeyPrefix() + tenantId + ":" + flowId;
    }

    private String localKey(long tenantId, String flowId) {
        return tenantId + ":" + flowId;
    }

    public String newFlowId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 读取并校验：租户、会话、意图一致且未过期。
     */
    public Optional<IntentFlowSession> findValid(
            long tenantId, long conversationId, long intentDefinitionId, String flowId) {
        if (flowId == null || flowId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = flowId.trim();
        Optional<IntentFlowSession> loaded = loadRaw(tenantId, trimmed);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        IntentFlowSession s = loaded.get();
        long now = System.currentTimeMillis();
        if (now > s.getExpiresAtEpochMs()) {
            remove(tenantId, trimmed);
            return Optional.empty();
        }
        if (s.getTenantId() != tenantId
                || s.getConversationId() != conversationId
                || s.getIntentDefinitionId() != intentDefinitionId) {
            log.info(
                    "[意图流] 流票据与当前会话/意图不一致，已拒绝：当前租户 {} 会话 {} 意图 {}；票据内租户 {} 会话 {} 意图 {}",
                    tenantId,
                    conversationId,
                    intentDefinitionId,
                    s.getTenantId(),
                    s.getConversationId(),
                    s.getIntentDefinitionId());
            return Optional.empty();
        }
        return Optional.of(s);
    }

    /** 仅按租户+票据加载（用于路由阶段尚未确定 intentDefinitionId 时）。 */
    public Optional<IntentFlowSession> findByTicket(long tenantId, long conversationId, String flowId) {
        if (flowId == null || flowId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = flowId.trim();
        Optional<IntentFlowSession> loaded = loadRaw(tenantId, trimmed);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        IntentFlowSession s = loaded.get();
        long now = System.currentTimeMillis();
        if (now > s.getExpiresAtEpochMs()) {
            remove(tenantId, trimmed);
            return Optional.empty();
        }
        if (s.getTenantId() != tenantId || s.getConversationId() != conversationId) {
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void save(IntentFlowSession session, String flowId, long ttlMs) {
        long ttl = Math.max(30_000L, ttlMs);
        long now = System.currentTimeMillis();
        session.setUpdatedAtMs(now);
        if (session.getExpiresAtEpochMs() <= 0) {
            session.setExpiresAtEpochMs(now + ttl);
        }
        try {
            String json = objectMapper.writeValueAsString(session);
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                redis.opsForValue().set(redisKey(session.getTenantId(), flowId), json, Duration.ofMillis(ttl));
            }
            local.put(
                    localKey(session.getTenantId(), flowId),
                    new CacheEntry(json, session.getExpiresAtEpochMs()));
        } catch (Exception e) {
            log.warn(
                    "[意图流] 保存会话状态失败：租户 {}，流编号 {}",
                    session.getTenantId(),
                    flowId,
                    e);
        }
    }

    public void remove(long tenantId, String flowId) {
        if (flowId == null || flowId.isBlank()) {
            return;
        }
        String trimmed = flowId.trim();
        try {
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                redis.delete(redisKey(tenantId, trimmed));
            }
        } catch (Exception e) {
            log.warn("[意图流] Redis 删除会话失败：租户 {}，流编号 {}", tenantId, trimmed, e);
        }
        local.remove(localKey(tenantId, trimmed));
    }

    private Optional<IntentFlowSession> loadRaw(long tenantId, String flowId) {
        try {
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                String json = redis.opsForValue().get(redisKey(tenantId, flowId));
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, IntentFlowSession.class));
                }
            }
        } catch (Exception e) {
            log.warn("[意图流] Redis 加载会话失败：租户 {}，流编号 {}", tenantId, flowId, e);
        }
        CacheEntry le = local.get(localKey(tenantId, flowId));
        if (le == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > le.expiresAtMs) {
            local.remove(localKey(tenantId, flowId), le);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(le.json, IntentFlowSession.class));
        } catch (Exception e) {
            log.warn("[意图流] 本地缓存解析会话失败：租户 {}，流编号 {}", tenantId, flowId, e);
            return Optional.empty();
        }
    }
}
