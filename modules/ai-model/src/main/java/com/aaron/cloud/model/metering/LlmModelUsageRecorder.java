package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.metering.MeteringMeterType;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.modelcfg.quota.LlmTokenQuotaCoordinator;
import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任意 {@link LlmModelKind} 在产生 usage 后：Redis 额度增量 + {@code llm_model.tokens_used} + 计量流水。
 *
 * <p>C 端对话与内部编排（向量、语音、视觉、路由等）应统一走本入口，保证各类型模型均参与 token 累计与共用配额（与
 * {@link LlmTokenQuotaCoordinator} 一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmModelUsageRecorder {

    private final LlmTokenQuotaCoordinator llmTokenQuotaCoordinator;
    private final LlmUsagePersistenceService llmUsagePersistenceService;
    private final LlmUsageAsyncPublisher llmUsageAsyncPublisher;

    /**
     * @param conversationId 对话场景传会话 id；非对话编排可传 {@code 0}
     */
    public void recordAfterLlmUsage(
            TenantSnapshot snap,
            SysLlmModel modelCfg,
            String modelAliasTrim,
            long conversationId,
            ModelTokenUsage usage,
            long durationMs) {
        if (usage == null || usage.totalTokens() <= 0) {
            return;
        }
        LlmModelKind kind =
                modelCfg.getModelKind() != null ? modelCfg.getModelKind() : LlmModelKind.LANGUAGE;
        String meterCode =
                kind == LlmModelKind.LANGUAGE
                        ? MeteringMeterType.LLM_CHAT_COMPLETION.getCode()
                        : MeteringMeterType.LLM_MODEL_USAGE.getCode();
        int total = usage.totalTokens();
        boolean accepted = llmTokenQuotaCoordinator.tryConsumeAfterStream(modelCfg, total);
        if (!accepted) {
            log.warn(
                    "llm quota redis rejected post-call; skip usage persist tenant={} modelId={} kind={} tokens={}",
                    snap.getTenantId(),
                    modelCfg.getId(),
                    kind.getCode(),
                    total);
            return;
        }
        var digest =
                new LlmUsageDigestMessage(
                        snap.getTenantId(),
                        snap.getUserId(),
                        snap.getDeviceId(),
                        modelCfg.getId(),
                        modelAliasTrim,
                        conversationId,
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        meterCode,
                        kind.getCode(),
                        durationMs > 0 ? durationMs : null);
        try {
            llmUsagePersistenceService.persist(digest);
        } catch (Exception ex) {
            log.error("llm usage sync persist failed, enqueue async retry", ex);
            llmUsageAsyncPublisher.publish(digest);
        }
    }
}
