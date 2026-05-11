package com.aaron.cloud.common.modelcfg.quota;

import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 额度预检与调用结束后扣量：启用 Redis 时走 Lua；用量落库走异步（RocketMQ 或 Redis List）。
 *
 * <p>与 {@code llm_model.model_kind} 无关：语言 / 向量 / 语音等**各模型行**的 {@code token_quota_total} 与
 * {@code tokens_used} 均适用；内部编排请在发起厂商请求前调用 {@link #assertQuotaAllowsSend}，在拿到 usage 后经与对话相同的落库门面扣减并持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTokenQuotaCoordinator {

    private final ObjectProvider<LlmTokenQuotaRedisOps> redisOps;

    public void warmupFromModel(SysLlmModel model) {
        var ops = redisOps.getIfAvailable();
        if (ops == null) {
            return;
        }
        long dbUsed = model.getTokensUsed() == null ? 0L : model.getTokensUsed();
        ops.warmup(model.getTenantId(), model.getId(), dbUsed);
    }

    public void invalidateQuotaCache(long tenantId, long modelId) {
        var ops = redisOps.getIfAvailable();
        if (ops != null) {
            ops.evict(tenantId, modelId);
        }
    }

    /** 发起对话前：有额度上限且已用尽则抛 {@link IllegalArgumentException} */
    public void assertQuotaAllowsSend(SysLlmModel model) {
        if (model.getTokenQuotaTotal() == null) {
            return;
        }
        warmupFromModel(model);
        long used = readEffectiveUsed(model);
        if (used >= model.getTokenQuotaTotal()) {
            throw new IllegalArgumentException("该模型没额度了");
        }
    }

    /** C 端模型列表：是否额度用尽 */
    public boolean isQuotaExhaustedForCatalog(SysLlmModel model) {
        if (model.getTokenQuotaTotal() == null) {
            return false;
        }
        warmupFromModel(model);
        long used = readEffectiveUsed(model);
        return used >= model.getTokenQuotaTotal();
    }

    /**
     * 流式结束后热路径扣量：仅 Redis Lua；无 Redis 时由异步 {@code appendTokensUsed} 统一落库，避免与持久化双写。
     *
     * @return 是否接受本次增量（无 Redis 时恒为 true）
     */
    public boolean tryConsumeAfterStream(SysLlmModel model, long delta) {
        if (delta <= 0) {
            return true;
        }
        var ops = redisOps.getIfAvailable();
        if (ops == null) {
            return true;
        }
        boolean ok = ops.tryIncrement(model.getTenantId(), model.getId(), delta, model.getTokenQuotaTotal());
        if (!ok && model.getTokenQuotaTotal() != null) {
            log.warn(
                    "redis llm quota rejected tenant={} model={} delta={}",
                    model.getTenantId(),
                    model.getId(),
                    delta);
        }
        return ok;
    }

    private long readEffectiveUsed(SysLlmModel model) {
        var ops = redisOps.getIfAvailable();
        if (ops != null) {
            Long r = ops.getUsedOrNull(model.getTenantId(), model.getId());
            if (r != null) {
                return r;
            }
        }
        return model.getTokensUsed() == null ? 0L : model.getTokensUsed();
    }

    /** 管理端保存后：使缓存与 DB 配置重新对齐 */
    public void onModelConfigChanged(SysLlmModel model) {
        invalidateQuotaCache(model.getTenantId(), model.getId());
        warmupFromModel(model);
    }
}
