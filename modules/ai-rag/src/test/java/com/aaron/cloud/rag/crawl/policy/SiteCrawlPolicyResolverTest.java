package com.aaron.cloud.rag.crawl.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiteCrawlPolicyResolverTest {

    @Mock
    private TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @Mock
    private AiRagProperties aiRagProperties;

    private SiteCrawlPolicyResolver resolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        resolver =
                new SiteCrawlPolicyResolver(
                        tenantRuntimeSettingApplicationService, aiRagProperties, new ObjectMapper());
    }

    @Test
    void balancedPreset_hasPaginationStrategy() {
        when(tenantRuntimeSettingApplicationService.siteCrawlPreset(1L)).thenReturn(SiteCrawlPreset.BALANCED);
        when(tenantRuntimeSettingApplicationService.siteCrawlRuntimeJson(1L)).thenReturn("{}");
        when(aiRagProperties.getSiteCrawl()).thenReturn(new AiRagProperties.SiteCrawl());

        EffectiveSiteCrawlPolicy p = resolver.resolve(1L);
        assertEquals(SiteCrawlPreset.BALANCED, p.preset());
        assertTrue(p.discovery().strategies().contains("list_pagination"));
        assertEquals(0.5, p.politeness().perHostQps(), 0.01);
    }

    @Test
    void conservative_isSlowerThanAggressive() {
        when(tenantRuntimeSettingApplicationService.siteCrawlRuntimeJson(1L)).thenReturn("{}");
        when(aiRagProperties.getSiteCrawl()).thenReturn(new AiRagProperties.SiteCrawl());

        when(tenantRuntimeSettingApplicationService.siteCrawlPreset(1L)).thenReturn(SiteCrawlPreset.CONSERVATIVE);
        double conservativeQps = resolver.resolve(1L).politeness().perHostQps();

        when(tenantRuntimeSettingApplicationService.siteCrawlPreset(1L)).thenReturn(SiteCrawlPreset.AGGRESSIVE);
        double aggressiveQps = resolver.resolve(1L).politeness().perHostQps();

        assertTrue(conservativeQps < aggressiveQps);
    }
}
