package com.aaron.cloud.common.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpClientProxySupportTest {

  @Test
  void resolveClashShortcut() {
    WebSearchFixedSourceOutboundConfig config =
        WebSearchFixedSourceOutboundConfig.resolve(null, null, null, true);
    assertEquals(WebSearchFixedSourceOutboundConfig.Mode.EXPLICIT, config.mode());
    assertTrue(config.label().contains("127.0.0.1"));
  }

  @Test
  void applyDoesNotThrowForDirect() {
    HttpClientProxySupport.apply(
        java.net.http.HttpClient.newBuilder(), WebSearchFixedSourceOutboundConfig.direct());
  }
}
