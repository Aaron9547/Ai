package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey;
import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey.SettingValueKind;
import com.aaron.cloud.common.tenant.runtime.entity.TenRuntimeSetting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 租户运行时键值配置：权威存储为 MySQL {@code ten_runtime_setting}（{@link TenRuntimeSettingRepository}）。
 * 若装配了 {@link TenantRuntimeSettingRedisCache}，读路径可命中缓存；{@link #replace(long, List)} 写库前后对变更键各执行一次
 * {@code evict}，避免「只改 Redis 不落库」的误解。
 */
@Service
@RequiredArgsConstructor
public class TenantRuntimeSettingApplicationService {

    private static final int WEB_SEARCH_GROUNDING_ROUND_COUNT_MAX = 10;
    private static final int RUNTIME_JSON_MAX_CHARS = 65_000;

    private final TenRuntimeSettingRepository repository;
    private final ObjectProvider<TenantRuntimeSettingRedisCache> redisCache;
    private final ObjectMapper objectMapper;

    public boolean isAuthOpenRegistrationEnabled(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.AUTH_OPEN_REGISTRATION);
        return Boolean.parseBoolean(raw.trim());
    }

    /** 用户记忆 Milvus 嵌入：{@code sys_llm_model.id}，未配置或非法时为空。 */
    public java.util.Optional<Long> memoryEmbeddingVectorModelId(long tenantId) {
        return parseOptionalLlmModelId(tenantId, TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID);
    }

    /** 对话联网检索：{@code sys_llm_model.id}，未配置或非法时为空（由仓储回退 {@code sort_order} 默认）。 */
    public java.util.Optional<Long> webSearchGroundingModelId(long tenantId) {
        return parseOptionalLlmModelId(tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID);
    }

    private java.util.Optional<Long> parseOptionalLlmModelId(long tenantId, TenantRuntimeSettingKey key) {
        String raw = effectiveValueText(tenantId, key).trim();
        if (raw.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * 联网前置多轮检索：轮数与每轮用户检索文本后缀（来自 {@code ten_runtime_setting}，免重启）。
     */
    public WebSearchGroundingMultiRoundConfig webSearchGroundingMultiRoundConfig(long tenantId) {
        int rounds =
                parseWebSearchGroundingRoundCount(
                        effectiveValueText(tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT));
        List<String> suffixes =
                parseWebSearchGroundingRoundSuffixesJson(
                        effectiveValueText(tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON),
                        rounds);
        return new WebSearchGroundingMultiRoundConfig(rounds, suffixes);
    }

    /** 联网检索 Redis 缓存策略（{@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_CACHE_JSON}）。 */
    public WebSearchGroundingCachePolicy webSearchGroundingCachePolicy(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_CACHE_JSON);
        return parseWebSearchGroundingCachePolicy(raw);
    }

    private WebSearchGroundingCachePolicy parseWebSearchGroundingCachePolicy(String raw) {
        WebSearchGroundingCachePolicy d = WebSearchGroundingCachePolicy.defaults();
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return d;
        }
        try {
            JsonNode n = objectMapper.readTree(raw.trim());
            if (!n.isObject()) {
                return d;
            }
            boolean enabled = n.path("enabled").asBoolean(d.enabled());
            int fresh = clampHours(n.path("freshHours").asInt(d.freshHours()));
            int warm = clampHours(n.path("warmHours").asInt(d.warmHours()));
            int stale = clampHours(n.path("staleHours").asInt(d.staleHours()));
            if (warm < fresh) {
                warm = fresh;
            }
            if (stale < warm) {
                stale = warm;
            }
            boolean semantic = n.path("semanticEnabled").asBoolean(d.semanticEnabled());
            double sim = n.path("similarityThreshold").asDouble(d.similarityThreshold());
            if (sim < 0.5 || sim > 0.999) {
                sim = d.similarityThreshold();
            }
            int indexMax = n.path("indexMaxEntries").asInt(d.indexMaxEntries());
            indexMax = Math.clamp(indexMax, 10, 500);
            int convHours = clampHours(n.path("conversationReuseHours").asInt(d.conversationReuseHours()));
            return new WebSearchGroundingCachePolicy(
                    enabled, fresh, warm, stale, semantic, sim, indexMax, convHours);
        } catch (Exception e) {
            return d;
        }
    }

    private static int clampHours(int h) {
        return Math.clamp(h, 0, 24 * 14);
    }

    private static int parseWebSearchGroundingRoundCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 3;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return Math.clamp(n, 1, WEB_SEARCH_GROUNDING_ROUND_COUNT_MAX);
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    private List<String> parseWebSearchGroundingRoundSuffixesJson(String raw, int rounds) {
        ArrayList<String> out = new ArrayList<>(rounds);
        for (int i = 0; i < rounds; i++) {
            out.add("");
        }
        if (raw == null || raw.isBlank()) {
            return List.copyOf(out);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                return List.copyOf(out);
            }
            for (int i = 0; i < rounds && i < root.size(); i++) {
                JsonNode el = root.get(i);
                if (el != null && !el.isNull()) {
                    if (el.isTextual()) {
                        out.set(i, el.asText());
                    } else if (el.isValueNode()) {
                        out.set(i, el.asText());
                    }
                }
            }
            return List.copyOf(out);
        } catch (Exception ignored) {
            return List.copyOf(out);
        }
    }

    public ChatPromptLimitsRuntime chatPromptLimits(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON);
        return ChatPromptLimitsRuntime.parse(raw, objectMapper);
    }

    public MemoryPolicyRuntime memoryPolicy(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.MEMORY_POLICY_JSON);
        return MemoryPolicyRuntime.parse(raw, objectMapper);
    }

    public ChatInputGuardRuntime chatInputGuardEffective(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON);
        return ChatInputGuardRuntime.parse(raw, objectMapper);
    }

    public List<TenantRuntimeSettingRow> listEffectiveRows(long tenantId) {
        return List.copyOf(buildAllRows(tenantId));
    }

    /**
     * 读取某键当前有效文本（Redis / DB）；供出站合并等只读路径。
     */
    public String getEffectiveValueText(long tenantId, TenantRuntimeSettingKey key) {
        return effectiveValueText(tenantId, key);
    }

    /**
     * 管理端列表：关键词匹配「键 / 说明 / 值」子串（值与说明同时支持原文与 ASCII 小写匹配）；可选按 {@code valueKind}
     *（{@code STRING} / {@code BOOLEAN}）筛选。分页字段与 MyBatis-Plus {@code Page} JSON 对齐。
     */
    public TenantRuntimeSettingsPage pageEffectiveRows(
            long tenantId, long current, long size, String keyword, String valueKind) {
        List<TenantRuntimeSettingRow> all = buildAllRows(tenantId);
        String q = keyword == null ? "" : keyword.trim();
        String qLower = q.toLowerCase(Locale.ROOT);
        String kind = valueKind == null ? "" : valueKind.trim().toUpperCase(Locale.ROOT);
        boolean kindFilter = "STRING".equals(kind) || "BOOLEAN".equals(kind);
        List<TenantRuntimeSettingRow> filtered = new ArrayList<>();
        for (TenantRuntimeSettingRow r : all) {
            if (kindFilter && !kind.equals(r.valueKind())) {
                continue;
            }
            if (!q.isEmpty() && !runtimeSettingRowMatchesKeyword(r, q, qLower)) {
                continue;
            }
            filtered.add(r);
        }
        long total = filtered.size();
        long sz = Math.min(100L, Math.max(1L, size));
        long maxPage = total == 0 ? 1L : (total + sz - 1L) / sz;
        long c = Math.max(1L, current);
        if (c > maxPage) {
            c = maxPage;
        }
        int from = (int) Math.min((c - 1L) * sz, Integer.MAX_VALUE);
        int to = (int) Math.min(from + sz, total);
        List<TenantRuntimeSettingRow> slice = from >= to ? List.of() : filtered.subList(from, to);
        return new TenantRuntimeSettingsPage(List.copyOf(slice), total, sz, c);
    }

    private static boolean runtimeSettingRowMatchesKeyword(TenantRuntimeSettingRow r, String q, String qLower) {
        String key = r.key() == null ? "" : r.key();
        String desc = r.descriptionZh() == null ? "" : r.descriptionZh();
        String vt = r.valueText() == null ? "" : r.valueText();
        return key.toLowerCase(Locale.ROOT).contains(qLower)
                || desc.contains(q)
                || desc.toLowerCase(Locale.ROOT).contains(qLower)
                || vt.contains(q)
                || vt.toLowerCase(Locale.ROOT).contains(qLower);
    }

    private List<TenantRuntimeSettingRow> buildAllRows(long tenantId) {
        List<TenantRuntimeSettingRow> out = new ArrayList<>();
        for (TenantRuntimeSettingKey key : TenantRuntimeSettingKey.values()) {
            if (key.excludedFromAdminRuntimeList()) {
                continue;
            }
            String vt = effectiveValueText(tenantId, key);
            out.add(
                    new TenantRuntimeSettingRow(
                            key.getStorage(),
                            vt,
                            key.getDescriptionZh(),
                            key.getValueKind().name(),
                            key.isMaskSensitiveInAdminUi()));
        }
        return out;
    }

    /**
     * 批量写入指定租户的运行参数（仅操作传入的 {@code tenantId} 对应行）。启用 Redis 时对该租户每个变更键执行
     * <strong>缓存双删</strong>：写库前 {@code evict} 一次、持久化后再 {@code evict} 一次，避免并发读穿把旧值写回缓存。
     */
    public void replace(long tenantId, List<PutItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items required");
        }
        for (PutItem item : items) {
            TenantRuntimeSettingKey key =
                    TenantRuntimeSettingKey.fromStorage(item.getKey())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST, "unknown setting key: " + item.getKey()));
            String normalized = validateAndNormalize(key, item.getValueText());
            evictRedis(tenantId, key);
            upsertRow(tenantId, key, normalized);
            evictRedis(tenantId, key);
        }
    }

    private void evictRedis(long tenantId, TenantRuntimeSettingKey key) {
        redisCache.ifAvailable(c -> c.evict(tenantId, key));
    }

    private void upsertRow(long tenantId, TenantRuntimeSettingKey key, String valueText) {
        Optional<TenRuntimeSetting> existing = repository.find(tenantId, key);
        if (existing.isPresent()) {
            TenRuntimeSetting row = existing.get();
            row.setValueText(valueText);
            repository.updateById(row);
        } else {
            TenRuntimeSetting row = new TenRuntimeSetting();
            row.setTenantId(tenantId);
            row.setSettingKey(key);
            row.setValueText(valueText);
            repository.insert(row);
        }
    }

    private String effectiveValueText(long tenantId, TenantRuntimeSettingKey key) {
        TenantRuntimeSettingRedisCache cache = redisCache.getIfAvailable();
        if (cache != null) {
            String cached = cache.getOrNull(tenantId, key);
            if (cached != null) {
                return cached;
            }
        }
        String fromDb =
                repository.find(tenantId, key).map(TenRuntimeSetting::getValueText).orElse(key.getDefaultValueText());
        if (cache != null) {
            cache.put(tenantId, key, fromDb);
        }
        return fromDb;
    }

    private String validateAndNormalize(TenantRuntimeSettingKey key, String valueText) {
        if (key.getValueKind() == SettingValueKind.BOOLEAN) {
            if (valueText == null || valueText.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valueText required for " + key.getStorage());
            }
            String t = valueText.trim().toLowerCase(Locale.ROOT);
            if (!"true".equals(t) && !"false".equals(t)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "boolean setting must be true or false: " + key.getStorage());
            }
            return t;
        }
        if (key == TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID
                || key == TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID) {
            if (valueText == null || valueText.isBlank()) {
                return "";
            }
            String t = valueText.trim();
            if (!t.matches("[0-9]{1,19}")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, key.getStorage() + " 须为数字主键或留空");
            }
            return t;
        }
        if (key == TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT) {
            if (valueText == null || valueText.isBlank()) {
                return "3";
            }
            String t = valueText.trim();
            if (!t.matches("10|[1-9]")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT 须为 1～10 的整数");
            }
            return t;
        }
        if (key == TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON) {
            if (valueText == null || valueText.isBlank()) {
                return "[]";
            }
            String t = valueText.trim();
            if (t.length() > RUNTIME_JSON_MAX_CHARS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON 过长（上限 " + RUNTIME_JSON_MAX_CHARS + " 字符）");
            }
            try {
                JsonNode n = objectMapper.readTree(t);
                if (!n.isArray()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON 须为 JSON 数组");
                }
                for (JsonNode el : n) {
                    if (el != null
                            && !el.isNull()
                            && !el.isTextual()
                            && !el.isNumber()
                            && !el.isBoolean()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON 数组元素须为字符串或数字、布尔");
                    }
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON 非法 JSON");
            }
            return t;
        }
        if (key == TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_CACHE_JSON) {
            if (valueText == null || valueText.isBlank()) {
                return "{}";
            }
            String t = valueText.trim();
            if (t.length() > RUNTIME_JSON_MAX_CHARS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "WEB_SEARCH_GROUNDING_CACHE_JSON 过长（上限 " + RUNTIME_JSON_MAX_CHARS + " 字符）");
            }
            try {
                JsonNode n = objectMapper.readTree(t);
                if (!n.isObject()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_CACHE_JSON 须为 JSON 对象");
                }
                parseWebSearchGroundingCachePolicy(t);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_CACHE_JSON 非法 JSON");
            }
            return t;
        }
        if (key == TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON
                || key == TenantRuntimeSettingKey.MEMORY_POLICY_JSON
                || key == TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON
                || key == TenantRuntimeSettingKey.OUTBOUND_RESILIENCE_JSON) {
            if (valueText == null || valueText.isBlank()) {
                return "{}";
            }
            String t = valueText.trim();
            if (t.length() > RUNTIME_JSON_MAX_CHARS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, key.getStorage() + " 过长（上限 " + RUNTIME_JSON_MAX_CHARS + " 字符）");
            }
            try {
                JsonNode n = objectMapper.readTree(t);
                if (!n.isObject()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key.getStorage() + " 须为 JSON 对象");
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key.getStorage() + " 非法 JSON");
            }
            return t;
        }
        if (valueText == null) {
            return "";
        }
        return valueText.trim();
    }

    /**
     * @param sensitive 管理端「值」列是否建议默认遮罩（来自 {@link TenantRuntimeSettingKey#isMaskSensitiveInAdminUi()}）；API 仍返回明文
     *     {@code valueText}，由前端决定是否展示。
     */
    public record TenantRuntimeSettingRow(
            String key, String valueText, String descriptionZh, String valueKind, boolean sensitive) {}

    /** 与前端 {@code MybatisPage} 字段对齐，便于复用分页条组件。 */
    public record TenantRuntimeSettingsPage(
            List<TenantRuntimeSettingRow> records, long total, long size, long current) {}

    @Data
    public static final class PutItem {
        private String key;
        private String valueText;
    }
}
