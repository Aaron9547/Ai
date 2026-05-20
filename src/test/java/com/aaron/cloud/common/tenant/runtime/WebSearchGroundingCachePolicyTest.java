package com.aaron.cloud.common.tenant.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebSearchGroundingCachePolicyTest {

  private final WebSearchGroundingCachePolicy policy = WebSearchGroundingCachePolicy.defaults();

  @Test
  void freshTierSkipsOutboundRounds() {
    assertEquals(0, policy.effectiveRoundsForTier(WebSearchCacheTier.FRESH, 3));
  }

  @Test
  void warmTierRunsAtMostOneRound() {
    assertEquals(1, policy.effectiveRoundsForTier(WebSearchCacheTier.WARM, 3));
    assertEquals(1, policy.effectiveRoundsForTier(WebSearchCacheTier.STALE, 3));
  }

  @Test
  void missUsesConfiguredRounds() {
    assertEquals(3, policy.effectiveRoundsForTier(WebSearchCacheTier.MISS, 3));
    assertEquals(5, policy.effectiveRoundsForTier(WebSearchCacheTier.EXPIRED, 5));
  }

  @Test
  void tierBoundariesUseRollingHours() {
    long h = 3_600_000L;
    assertEquals(WebSearchCacheTier.FRESH, policy.tierForAgeMs(6 * h));
    assertEquals(WebSearchCacheTier.WARM, policy.tierForAgeMs(6 * h + 1));
    assertEquals(WebSearchCacheTier.STALE, policy.tierForAgeMs(24 * h + 1));
    assertEquals(WebSearchCacheTier.EXPIRED, policy.tierForAgeMs(48 * h + 1));
  }
}
