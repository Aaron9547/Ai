package com.aaron.cloud.rag.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.tenant.runtime.TenRuntimeSettingRepository;
import com.aaron.cloud.common.tenant.runtime.entity.TenRuntimeSetting;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TenantRagRuntimeResolverTest {

    private AiProvidersProperties providersProperties;
    private PlatformSettingApplicationService platformSettings;
    private TenRuntimeSettingRepository settingRepository;
    private RagChunkRepository ragChunkRepository;
    private TenantRagRuntimeResolver resolver;

    @BeforeEach
    void setUp() {
        providersProperties = new AiProvidersProperties();
        providersProperties.getMilvus().setVectorDimension(2048);
        platformSettings = mock(PlatformSettingApplicationService.class);
        when(platformSettings.getEffectiveValueText(PlatformSettingKey.RAG_RETRIEVAL_MODE))
                .thenReturn("milvus_es_hybrid");
        settingRepository = mock(TenRuntimeSettingRepository.class);
        ragChunkRepository = mock(RagChunkRepository.class);
        resolver =
                new TenantRagRuntimeResolver(
                        providersProperties, platformSettings, settingRepository, ragChunkRepository);
        when(ragChunkRepository.tenantHasAnyChunks(any(Long.class))).thenReturn(false);
    }

    @Test
    void resolveVectorDimension_fallsBackToProcessDefault() {
        when(settingRepository.find(eq(1L), eq(TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION)))
                .thenReturn(Optional.empty());
        assertEquals(2048, resolver.resolveVectorDimension(1L));
    }

    @Test
    void resolveVectorDimension_usesTenantOverride() {
        TenRuntimeSetting row = new TenRuntimeSetting();
        row.setValueText("1024");
        when(settingRepository.find(eq(2L), eq(TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION)))
                .thenReturn(Optional.of(row));
        assertEquals(1024, resolver.resolveVectorDimension(2L));
    }

    @Test
    void validateVectorDimensionSave_rejectsChangeWhenLocked() {
        TenRuntimeSetting row = new TenRuntimeSetting();
        row.setValueText("2048");
        when(settingRepository.find(eq(3L), eq(TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION)))
                .thenReturn(Optional.of(row));
        assertThrows(
                ResponseStatusException.class,
                () -> resolver.validateVectorDimensionSave(3L, "1024"));
    }

    @Test
    void resolveRetrievalMode_usesTenantOverride() {
        TenRuntimeSetting row = new TenRuntimeSetting();
        row.setValueText("milvus");
        when(settingRepository.find(eq(4L), eq(TenantRuntimeSettingKey.RAG_RETRIEVAL_MODE)))
                .thenReturn(Optional.of(row));
        assertEquals(RagRetrievalMode.MILVUS, resolver.resolveRetrievalMode(4L));
    }
}
