package com.aaron.cloud.rag;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHost;
import org.springframework.util.StringUtils;

/**
 * 将 {@link AiRagProperties.Elasticsearch} 解析为 Elasticsearch {@code RestClient} 所需的 {@link HttpHost} 列表，语义对齐
 * ly-ai-rag-svc {@code elasticsearch.config.hostPorts}：由 {@code config.hostPorts} 拼出 {@code http://host:port} 串再解析为
 * {@link HttpHost}；多节点分隔符为逗号或分号；无 {@code ://} 时默认 http。
 */
public final class ElasticsearchRagHostParser {

    private ElasticsearchRagHostParser() {}

    /**
     * 将 {@code config.hostPorts} 转为逗号分隔的完整 URI 串（供 {@link #parseHttpHosts} 使用）；与 RAG 侧节点串语义一致。
     */
    public static String resolveEffectiveUriString(AiRagProperties.Elasticsearch es) {
        if (es.getConfig() == null) {
            return "";
        }
        String cfgHp = es.getConfig().getHostPorts() == null ? "" : es.getConfig().getHostPorts().trim();
        if (StringUtils.hasText(cfgHp)) {
            return hostPortsToCommaSeparatedHttpUris(cfgHp);
        }
        return "";
    }

    static String hostPortsToCommaSeparatedHttpUris(String hostPorts) {
        if (!StringUtils.hasText(hostPorts)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : hostPorts.split("[,;]")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            if (!part.contains("://")) {
                sb.append("http://").append(part);
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    /**
     * 按逗号或分号切分每一段为 {@link HttpHost}（与 ly-ai-rag-svc {@code parseHosts} 一致）。
     */
    public static List<HttpHost> parseHttpHosts(String uris) {
        List<HttpHost> out = new ArrayList<>();
        if (!StringUtils.hasText(uris)) {
            return out;
        }
        for (String raw : uris.split("[,;]")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                String spec = part.contains("://") ? part : "http://" + part;
                URI uri = URI.create(spec);
                String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    continue;
                }
                int port = uri.getPort();
                if (port < 0) {
                    port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
                }
                out.add(new HttpHost(host, port, scheme));
            } catch (Exception ignored) {
                // skip invalid segment（与 RAG 侧一致）
            }
        }
        return out;
    }
}
