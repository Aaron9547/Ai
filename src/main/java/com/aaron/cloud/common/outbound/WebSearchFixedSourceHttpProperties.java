package com.aaron.cloud.common.outbound;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置固定联网源 / MCP 出站代理（本地开梯子时配置）。
 *
 * <p>进程级回退：租户 Shell {@code WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON}、本类 {@code ai.websearch.fixed.*}、
 * 环境变量 {@code AI_WEB_SEARCH_FIXED_PROXY_*}、JVM {@code -Dai.websearch.fixed.proxyHost} 等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.websearch.fixed")
@RequiredArgsConstructor
public class WebSearchFixedSourceHttpProperties {

  private final WebSearchFixedSourceOutboundResolver outboundResolver;

  /** HTTP/SOCKS 代理主机，如 Clash {@code 127.0.0.1}。 */
  private String proxyHost;

  /** 代理端口，Clash HTTP 常见 {@code 7890}。 */
  private Integer proxyPort;

  /** {@code HTTP}（默认）或 {@code SOCKS}。 */
  private String proxyType;

  /**
   * 为 true 且未填 {@link #proxyHost} 时，使用本地 HTTP 代理 {@code 127.0.0.1:7890}（与 Clash 常见 HTTP 口一致），
   * 不使用 JVM 系统 SOCKS（易 Connect timeout）。
   */
  private Boolean useSystemProxy;

  @PostConstruct
  void apply() {
    outboundResolver.refreshProcessLevel(proxyHost, proxyPort, proxyType, useSystemProxy);
  }
}
