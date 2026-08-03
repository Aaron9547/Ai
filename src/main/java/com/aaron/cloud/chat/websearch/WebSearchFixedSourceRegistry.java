package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebSearchFixedSourceRegistry {

    private final Map<WebSearchFixedSource, WebSearchFixedSourceProvider> byKind =
            new EnumMap<>(WebSearchFixedSource.class);

    public WebSearchFixedSourceRegistry(List<WebSearchFixedSourceProvider> providers) {
        for (WebSearchFixedSourceProvider p : providers) {
            byKind.put(p.supports(), p);
        }
    }

    public WebSearchFixedSourceProvider require(WebSearchFixedSource kind) {
        if (kind == null) {
            throw new IllegalArgumentException("未识别的固定联网检索源");
        }
        WebSearchFixedSourceProvider p = byKind.get(kind);
        if (p == null) {
            throw new IllegalArgumentException("不支持的固定联网检索源：" + kind.getCode());
        }
        return p;
    }
}
