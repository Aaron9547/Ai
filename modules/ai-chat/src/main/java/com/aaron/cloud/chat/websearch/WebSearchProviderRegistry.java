package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebSearchProviderRegistry {

    private final Map<LlmWebSearchProvider, WebSearchModelProvider> byKind = new EnumMap<>(LlmWebSearchProvider.class);

    public WebSearchProviderRegistry(List<WebSearchModelProvider> providers) {
        for (WebSearchModelProvider p : providers) {
            byKind.put(p.supports(), p);
        }
    }

    public WebSearchModelProvider require(LlmWebSearchProvider kind) {
        if (kind == null) {
            throw new IllegalArgumentException("未配置 integration_backend（联网检索实现）");
        }
        WebSearchModelProvider p = byKind.get(kind);
        if (p == null) {
            throw new IllegalArgumentException("不支持的联网搜索实现：" + kind.getCode());
        }
        return p;
    }
}
