package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 联网检索 Redis 缓存 scope 键（Ark 模型 id + 已启用固定源代码）。 */
public final class WebSearchModelScopeSupport {

    private WebSearchModelScopeSupport() {}

    public static List<String> sortedFixedSourceCodes(List<WebSearchFixedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .map(WebSearchFixedSource::getCode)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static long cacheScopeModelKey(Long arkModelId, List<String> sortedFixedCodes) {
        StringBuilder sb = new StringBuilder();
        if (arkModelId != null && arkModelId > 0L) {
            sb.append("ark:").append(arkModelId);
        }
        if (sortedFixedCodes != null) {
            for (String code : sortedFixedCodes) {
                sb.append("|f:").append(code);
            }
        }
        if (sb.isEmpty()) {
            return 0L;
        }
        String hex = WebSearchQueryNormalizer.sha256Hex(sb.toString()).substring(0, 16);
        return Long.parseUnsignedLong(hex, 16) & Long.MAX_VALUE;
    }
}
