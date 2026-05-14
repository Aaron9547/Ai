package com.aaron.cloud.common.modelcfg.quota;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 租户模型 token 共用额度在 Redis 中的热路径计数（Lua 原子扣减），避免高 QPS 直打 MySQL 行更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class LlmTokenQuotaRedisOps {

    private static final String LUA =
            """
            local cur = tonumber(redis.call('GET', KEYS[1])) or 0
            local delta = tonumber(ARGV[1])
            if ARGV[2] == 'inf' then
              redis.call('INCRBY', KEYS[1], delta)
              return 1
            end
            local q = tonumber(ARGV[2])
            if cur + delta > q then
              return 0
            end
            redis.call('INCRBY', KEYS[1], delta)
            return 1
            """;

    private final StringRedisTemplate redis;

    private static String tokensKey(long tenantId, long modelId) {
        return "ai:llm:tokens:" + tenantId + ":" + modelId;
    }

    public void warmup(long tenantId, long modelId, long dbUsed) {
        String k = tokensKey(tenantId, modelId);
        Boolean nx = redis.opsForValue().setIfAbsent(k, Long.toString(Math.max(0, dbUsed)));
        if (Boolean.TRUE.equals(nx)) {
            log.debug("llm quota redis warm tenant={} model={} used={}", tenantId, modelId, dbUsed);
        }
    }

    public void evict(long tenantId, long modelId) {
        redis.delete(tokensKey(tenantId, modelId));
    }

    /** 当前计数；无 key 时返回 {@code null} */
    public Long getUsedOrNull(long tenantId, long modelId) {
        String v = redis.opsForValue().get(tokensKey(tenantId, modelId));
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param quotaTotal {@code null} 表示不限制
     * @return 是否成功增加（未超限）
     */
    public boolean tryIncrement(long tenantId, long modelId, long delta, Long quotaTotal) {
        if (delta <= 0) {
            return true;
        }
        var script = new DefaultRedisScript<Long>();
        script.setScriptText(LUA);
        script.setResultType(Long.class);
        List<String> keys = Collections.singletonList(tokensKey(tenantId, modelId));
        String qarg = quotaTotal == null ? "inf" : Long.toString(quotaTotal);
        Long ok = redis.execute(script, keys, Long.toString(delta), qarg);
        return ok != null && ok == 1L;
    }
}
