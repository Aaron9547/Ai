package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 联网检索 Redis 缓存 scope 键（Ark 模型 id 列表 + 已启用固定源代码）。 */
public final class WebSearchModelScopeSupport {

    private WebSearchModelScopeSupport() {}

    public static List<Long> sortedArkModelIds(List<SysLlmModel> arkModels) {
        if (arkModels == null || arkModels.isEmpty()) {
            return List.of();
        }
        return arkModels.stream()
                .map(SysLlmModel::getId)
                .filter(id -> id != null && id > 0L)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<String> sortedFixedSourceCodes(List<WebSearchFixedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .map(WebSearchFixedSource::getCode)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static long cacheScopeModelKey(List<Long> arkModelIds, List<String> sortedFixedCodes) {
        StringBuilder sb = new StringBuilder();
        if (arkModelIds != null) {
            for (Long id : arkModelIds.stream().sorted().toList()) {
                if (id != null && id > 0L) {
                    sb.append("ark:").append(id);
                }
            }
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

    /** @deprecated 使用 {@link #cacheScopeModelKey(List, List)} */
    @Deprecated
    public static long cacheScopeModelKey(Long arkModelId, List<String> sortedFixedCodes) {
        List<Long> ids =
                arkModelId != null && arkModelId > 0L ? List.of(arkModelId) : List.of();
        return cacheScopeModelKey(ids, sortedFixedCodes);
    }
}
