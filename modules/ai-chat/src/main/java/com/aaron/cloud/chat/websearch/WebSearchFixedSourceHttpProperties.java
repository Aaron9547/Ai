package com.aaron.cloud.chat.websearch;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置固定联网源出站代理（本地开梯子时配置，与 Postman System Proxy 对齐）。
 *
 * <p>示例 {@code application-local.yml}：{@code ai.websearch.fixed.proxy-host: 127.0.0.1}、{@code proxy-port: 7890}
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.websearch.fixed")
public class WebSearchFixedSourceHttpProperties {

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
        WebSearchFixedSourceHttp.applySpringSettings(proxyHost, proxyPort, proxyType, useSystemProxy);
    }
}
