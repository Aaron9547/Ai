package com.aaron.cloud.common.outbound;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

/** 将 {@link WebSearchFixedSourceOutboundConfig} 应用到 {@link HttpClient.Builder}（MCP 等）。 */
public final class HttpClientProxySupport {

  private HttpClientProxySupport() {}

  public static void apply(HttpClient.Builder builder, WebSearchFixedSourceOutboundConfig config) {
    if (builder == null || config == null) {
      return;
    }
    switch (config.mode()) {
      case DIRECT -> {
        // 不强制 NO_PROXY：允许 JVM / OS 系统代理（仅开 Clash 系统代理、未配 yml 时仍可出站）
      }
      case EXPLICIT, JVM_PROPERTIES -> builder.proxy(fixedProxySelector(config.proxy()));
      case SYSTEM -> {
        // 依赖 JVM / OS 代理发现
      }
      default -> {
        // 未知模式：保持 HttpClient 默认 ProxySelector
      }
    }
  }

  private static ProxySelector fixedProxySelector(Proxy proxy) {
    Proxy effective = proxy == null ? Proxy.NO_PROXY : proxy;
    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        return List.of(effective);
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
    };
  }
}
