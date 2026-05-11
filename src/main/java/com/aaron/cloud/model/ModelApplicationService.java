package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.model.spi.ModelCompletionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelApplicationService {

    private final ModelCompletionEngine modelCompletionEngine;

    public void streamCompletion(ModelChatRequest request, java.util.function.Consumer<String> onToken)
            throws Exception {
        long start = System.currentTimeMillis();
        try {
            modelCompletionEngine.streamCompletion(request, onToken);
        } catch (Exception e) {
            var snap = TenantContextHolder.getOrNull();
            log.error(
                    "model.streamCompletion failed tenantId={} userId={} deviceId={} modelAlias={} thinking={} {}",
                    snap != null ? snap.getTenantId() : request.getTenantId(),
                    snap != null ? snap.getUserId() : request.getUserId(),
                    snap != null ? snap.getDeviceId() : request.getDeviceId(),
                    request.getModelAlias(),
                    request.getThinkingEnabled(),
                    summarizeModelRequest(request),
                    e);
            throw e;
        }
        long duration = System.currentTimeMillis() - start;
        var snap = TenantContextHolder.getOrNull();
        log.info(
                "model.completion tenantId={} deviceId={} durationMs={} modelAlias={}",
                snap != null ? snap.getTenantId() : request.getTenantId(),
                snap != null ? snap.getDeviceId() : request.getDeviceId(),
                duration,
                request.getModelAlias());
    }

    private static String summarizeModelRequest(ModelChatRequest request) {
        var turns = request.getMessages();
        int n = turns == null ? 0 : turns.size();
        if (n == 0) {
            return "turns=0 roles=[]";
        }
        StringBuilder roles = new StringBuilder();
        int cap = Math.min(n, 12);
        for (int i = 0; i < cap; i++) {
            if (i > 0) {
                roles.append(',');
            }
            var t = turns.get(i);
            roles.append(t.getRole() == null ? "?" : t.getRole());
        }
        if (n > cap) {
            roles.append(",…");
        }
        return "turns=" + n + " roles=[" + roles + "] (content omitted)";
    }

}
