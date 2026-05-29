package com.aaron.cloud.mcp.remote;

import com.aaron.cloud.common.api.enums.mcp.McpTransportKind;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class McpRemoteClientFactory {

    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;

    public McpSyncClient open(McpServerRegistry server, Duration requestTimeout) throws Exception {
        McpTransportKind kind =
                server.getTransportKind() == null ? McpTransportKind.STREAMABLE_HTTP : server.getTransportKind();
        if (kind != McpTransportKind.STREAMABLE_HTTP) {
            throw new IllegalArgumentException("unsupported mcp transport: " + kind);
        }
        ParsedUrl parsed = parseBaseUrl(server.getBaseUrl());
        Map<String, String> headers = decryptAuthHeaders(server.getAuthHeadersCipher());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        headers.forEach(requestBuilder::header);
        McpClientTransport transport = HttpClientStreamableHttpTransport.builder(parsed.origin())
                .endpoint(parsed.endpoint())
                .requestBuilder(requestBuilder)
                .build();
        return McpClient.sync(transport).requestTimeout(requestTimeout).build();
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
