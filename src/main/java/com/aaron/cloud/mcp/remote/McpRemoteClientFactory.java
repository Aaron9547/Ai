package com.aaron.cloud.mcp.remote;

import com.aaron.cloud.common.api.enums.mcp.McpTransportKind;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.common.outbound.HttpClientProxySupport;
import com.aaron.cloud.common.outbound.WebSearchFixedSourceOutboundConfig;
import com.aaron.cloud.common.outbound.WebSearchFixedSourceOutboundResolver;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpRemoteClientFactory {

    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;
    private final AiOutboundResilienceProperties outboundResilienceProperties;
    private final WebSearchFixedSourceOutboundResolver outboundResolver;

    public McpSyncClient open(long tenantId, McpServerRegistry server, Duration requestTimeout) throws Exception {
        McpTransportKind kind =
                server.getTransportKind() == null ? McpTransportKind.STREAMABLE_HTTP : server.getTransportKind();
        if (kind != McpTransportKind.STREAMABLE_HTTP) {
            throw new IllegalArgumentException("unsupported mcp transport: " + kind);
        }
        ParsedUrl parsed = parseBaseUrl(server.getBaseUrl());
        Map<String, String> headers = decryptAuthHeaders(server.getAuthHeadersCipher());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        headers.forEach(requestBuilder::header);
        Duration connectTimeout = connectTimeout();
        WebSearchFixedSourceOutboundConfig outbound = outboundResolver.resolve(tenantId);
        HttpClient.Builder clientBuilder =
                HttpClient.newBuilder().version(Version.HTTP_1_1).connectTimeout(connectTimeout);
        HttpClientProxySupport.apply(clientBuilder, outbound);
        log.info(
                "mcp client open tenantId={} server={} baseUrl={} outbound={}",
                tenantId,
                server.getName(),
                server.getBaseUrl(),
                outbound.label());
        McpClientTransport transport = HttpClientStreamableHttpTransport.builder(parsed.origin())
                .endpoint(parsed.endpoint())
                .requestBuilder(requestBuilder)
                .clientBuilder(clientBuilder)
                .connectTimeout(connectTimeout)
                .build();
        Duration initTimeout = requestTimeout.compareTo(connectTimeout) >= 0 ? requestTimeout : connectTimeout;
        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .initializationTimeout(initTimeout)
                .build();
    }

    private Duration connectTimeout() {
        int sec = outboundResilienceProperties.getConnectTimeoutSeconds();
        if (sec < 5) {
            sec = 5;
        }
        if (sec > 600) {
            sec = 600;
        }
        return Duration.ofSeconds(sec);
    }

    Map<String, String> decryptAuthHeaders(String cipher) throws Exception {
        if (cipher == null || cipher.isBlank()) {
            return Collections.emptyMap();
        }
        String json = aesSecretCipher.decryptFromBase64(cipher);
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    public String encryptAuthHeadersFromApiKey(String apiKeyPlain) throws Exception {
        if (apiKeyPlain == null || apiKeyPlain.isBlank()) {
            return null;
        }
        String bearer = apiKeyPlain.trim();
        if (!bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            bearer = "Bearer " + bearer;
        }
        return aesSecretCipher.encryptToBase64(
                objectMapper.writeValueAsString(Map.of("Authorization", bearer)));
    }

    static ParsedUrl parseBaseUrl(String baseUrl) {
        URI uri = URI.create(baseUrl.trim());
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("invalid mcp baseUrl: " + baseUrl);
        }
        int port = uri.getPort();
        String origin = uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String endpoint = path;
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            endpoint = endpoint + "?" + uri.getRawQuery();
        }
        return new ParsedUrl(origin, endpoint);
    }

    record ParsedUrl(String origin, String endpoint) {}
}
