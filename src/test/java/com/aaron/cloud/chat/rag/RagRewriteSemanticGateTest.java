package com.aaron.cloud.chat.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagRewriteSemanticGateTest {

    private static final long TENANT_ID = 1L;

    @Mock
    private RagEmbeddingPort ragEmbeddingPort;

    @Mock
    private TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @InjectMocks
    private RagRewriteSemanticGate gate;

    @Test
    void usesKbEmbeddingWhenKbIdPresent() {
        when(ragEmbeddingPort.embed(eq(TENANT_ID), eq(9L), anyString())).thenReturn(vec(1.0f, 0.0f), vec(1.0f, 0.0f));

        var outcome =
                gate.evaluate(
                        TENANT_ID,
                        9L,
                        List.of(turn("user", "你好")),
                        "改写问句",
                        RagRetrievalTuningRuntime.defaults());

        assertEquals(1.0d, outcome.similarity(), 0.001d);
        verify(ragEmbeddingPort).embed(TENANT_ID, 9L, "user: 你好");
        verify(ragEmbeddingPort).embed(TENANT_ID, 9L, "改写问句");
    }

    @Test
    void fallsBackToMemoryEmbeddingModelWhenNoKb() {
        when(tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(TENANT_ID))
                .thenReturn(Optional.of(42L));
        when(ragEmbeddingPort.embedByVectorModelIdOrHash(eq(TENANT_ID), eq(42L), anyString()))
                .thenReturn(vec(1.0f, 0.0f), vec(0.0f, 1.0f));

        var outcome =
                gate.evaluate(
                        TENANT_ID,
                        null,
                        List.of(turn("user", "上下文")),
                        "改写",
                        RagRetrievalTuningRuntime.defaults());

        assertEquals(0.0d, outcome.similarity(), 0.001d);
        verify(ragEmbeddingPort).embedByVectorModelIdOrHash(TENANT_ID, 42L, "user: 上下文");
        verify(ragEmbeddingPort).embedByVectorModelIdOrHash(TENANT_ID, 42L, "改写");
    }

    private static ModelChatRequest.MessageTurn turn(String role, String content) {
        var t = new ModelChatRequest.MessageTurn();
        t.setRole(role);
        t.setContent(content);
        return t;
    }

    private static float[] vec(float a, float b) {
        return new float[] {a, b};
    }
}
