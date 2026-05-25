package com.aaron.cloud.rag.crawl.fetch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPresetTemplates;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolitenessGateTest {

    @Test
    void acquire_respectsQpsSpacing() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redis =
                mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        PolitenessGate gate = new PolitenessGate(redis);
        EffectiveSiteCrawlPolicy.PolitenessPolicy pol =
                SiteCrawlPresetTemplates.template(SiteCrawlPreset.CONSERVATIVE).politeness();
        gate.configure(pol);
        AtomicInteger count = new AtomicInteger();
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            gate.acquire("https://example.com/page" + i, pol);
            count.incrementAndGet();
            gate.release();
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(count.get() == 3);
        assertTrue(elapsedMs >= 500, "conservative QPS should space requests");
    }
}
