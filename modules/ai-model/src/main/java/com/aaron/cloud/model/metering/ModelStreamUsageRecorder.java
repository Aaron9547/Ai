package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** {@link ModelChatRequest} 流式结束后统一落库 token 计量（额度 + {@code metering_usage_event}）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelStreamUsageRecorder {

    private final LlmModelUsageRecorder llmModelUsageRecorder;
    private final SysLlmModelRepository llmModelRepository;

    /** 包装 {@link ModelChatRequest#getStreamUsageConsumer()}，保证引擎解析到的 usage 可被落库。 */
    public void attachUsageCapture(ModelChatRequest request, AtomicReference<ModelTokenUsage> sink) {
        Consumer<ModelTokenUsage> prior = request.getStreamUsageConsumer();
        request.setStreamUsageConsumer(
                usage -> {
                    if (usage != null) {
                        sink.set(usage);
                    }
                    if (prior != null) {
                        prior.accept(usage);
                    }
                });
    }

    public void recordAfterStream(ModelChatRequest request, ModelTokenUsage usage, long durationMs) {
        if (Boolean.TRUE.equals(request.getSkipUsageRecord())) {
            return;
        }
        if (usage == null || usage.totalTokens() <= 0) {
            return;
        }
        String alias = request.getModelAlias();
        if (alias != null && "mock".equalsIgnoreCase(alias.trim())) {
            return;
        }
        TenantSnapshot snap = resolveSnapshot(request);
        if (snap == null) {
            log.debug("skip model usage record: no tenant snapshot modelAlias={}", alias);
            return;
        }
        SysLlmModel modelCfg = resolveModel(snap.getTenantId(), request);
        if (modelCfg == null) {
            log.debug(
                    "skip model usage record: model not resolved tenant={} alias={}",
                    snap.getTenantId(),
                    alias);
            return;
        }
        long conversationId =
                request.getConversationId() != null && request.getConversationId() > 0L
                        ? request.getConversationId()
                        : 0L;
        llmModelUsageRecorder.recordAfterLlmUsage(
                snap,
                modelCfg,
                modelCfg.getAlias(),
                conversationId,
                usage,
                durationMs,
                resolveUsageScene(request, conversationId));
    }

    private static LlmUsageScene resolveUsageScene(ModelChatRequest request, long conversationId) {
        LlmUsageScene explicit = LlmUsageScene.fromCode(request.getUsageScene());
        if (explicit != null) {
            return explicit;
        }
        if (conversationId > 0L) {
            return LlmUsageScene.CHAT;
        }
        return null;
    }

    private static TenantSnapshot resolveSnapshot(ModelChatRequest request) {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        if (snap != null) {
            return snap;
        }
        Long tenantId = request.getTenantId();
        if (tenantId == null || tenantId <= 0L) {
            return null;
        }
        return TenantSnapshot.builder()
                .tenantId(tenantId)
                .userId(request.getUserId())
                .deviceId(request.getDeviceId())
                .build();
    }

    private SysLlmModel resolveModel(long tenantId, ModelChatRequest request) {
        var resolvedRef = request.getResolvedLlmModelIdRef();
        if (resolvedRef != null) {
            Long id = resolvedRef.get();
            if (id != null && id > 0L) {
                return llmModelRepository.findById(tenantId, id).orElse(null);
            }
        }
        String alias = request.getModelAlias();
        if (alias == null || alias.isBlank()) {
            return null;
        }
        return llmModelRepository.findByTenantAndAlias(tenantId, alias.trim()).orElse(null);
    }
}
