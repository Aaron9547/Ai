package com.aaron.cloud.rag.crawl.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 校验 {@code SITE_CRAWL_RUNTIME_JSON} 结构与数值范围。 */
public final class SiteCrawlRuntimeValidator {

    private static final int JSON_MAX_CHARS = 32_000;

    private SiteCrawlRuntimeValidator() {}

    public static JsonNode parseObject(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        String t = raw.trim();
        if (t.length() > JSON_MAX_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "SITE_CRAWL_RUNTIME_JSON 过长（上限 " + JSON_MAX_CHARS + "）");
        }
        try {
            JsonNode n = mapper.readTree(t);
            if (!n.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SITE_CRAWL_RUNTIME_JSON 须为 JSON 对象");
            }
            validateNode(n);
            return n;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SITE_CRAWL_RUNTIME_JSON 非法 JSON");
        }
    }

    private static void validateNode(JsonNode root) {
        JsonNode strategies = root.path("discovery").path("strategies");
        if (strategies.isArray()) {
            for (JsonNode s : strategies) {
                String id = s.asText("").trim();
                if (!id.isEmpty() && !SiteCrawlStrategyIds.ALL.contains(id)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "未知 discovery.strategies: " + id);
                }
            }
        }
        clampPolicyRanges(root);
    }

    private static void clampPolicyRanges(JsonNode root) {
        JsonNode pol = root.path("politeness");
        if (pol.isObject()) {
            double qps = pol.path("perHostQps").asDouble(0.5);
            if (qps < 0.05 || qps > 2.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "perHostQps 须在 0.05～2");
            }
        }
    }

    public static String normalizePresetStorage(String raw) {
        return SiteCrawlPreset.fromStorage(raw).name();
    }

    public static List<String> strategiesFromJson(JsonNode discovery) {
        if (discovery == null || !discovery.isObject()) {
            return List.of();
        }
        JsonNode arr = discovery.path("strategies");
        if (!arr.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode el : arr) {
            String id = el.asText("").trim();
            if (!id.isEmpty() && SiteCrawlStrategyIds.ALL.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public static JsonNode mergePatch(JsonNode base, JsonNode patch) {
        if (patch == null || patch.isEmpty()) {
            return base;
        }
        var out = base.deepCopy();
        mergeInto(out, patch);
        return out;
    }

    private static void mergeInto(JsonNode target, JsonNode patch) {
        Iterator<String> names = patch.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode patchVal = patch.get(name);
            JsonNode targetVal = target.get(name);
            if (patchVal.isObject() && targetVal != null && targetVal.isObject()) {
                mergeInto(targetVal, patchVal);
            } else {
                ((com.fasterxml.jackson.databind.node.ObjectNode) target).set(name, patchVal.deepCopy());
            }
        }
    }
}
