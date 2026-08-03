package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.chat.websearch.WebSearchSummarySupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 合并多轮 / 缓存与增量联网结果（按 URL 去重，摘要拼接）。 */
public final class WebSearchGroundingMergeSupport {

    private WebSearchGroundingMergeSupport() {}

    public static WebGroundingBundle merge(WebGroundingBundle base, WebGroundingBundle incremental) {
        if (base == null && incremental == null) {
            return new WebGroundingBundle("", List.of());
        }
        if (base == null) {
            return incremental;
        }
        if (incremental == null) {
            return base;
        }
        List<WebSearchReference> refs = dedupeReferences(
                concatLists(base.references(), incremental.references()));
        String sum =
                WebSearchSummarySupport.joinSummaryText(
                        base.summaryText(), incremental.summaryText());
        return new WebGroundingBundle(sum, refs);
    }

    public static List<WebSearchReference> dedupeReferences(List<WebSearchReference> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        Map<String, WebSearchReference> byUrl = new LinkedHashMap<>();
        for (WebSearchReference r : refs) {
            if (r == null) {
                continue;
            }
            String key = urlKey(r.url());
            if (key.isEmpty()) {
                byUrl.put("nourl:" + byUrl.size(), r);
            } else if (!byUrl.containsKey(key)) {
                byUrl.put(key, r);
            }
        }
        return List.copyOf(byUrl.values());
    }

    private static List<WebSearchReference> concatLists(
            List<WebSearchReference> a, List<WebSearchReference> b) {
        List<WebSearchReference> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out;
    }

    private static String urlKey(String url) {
        if (url == null) {
            return "";
        }
        return url.trim().toLowerCase(Locale.ROOT);
    }
}
