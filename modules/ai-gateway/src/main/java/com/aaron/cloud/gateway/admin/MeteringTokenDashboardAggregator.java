package com.aaron.cloud.gateway.admin;

import com.aaron.cloud.common.metering.MeteringUsageEventRepository;
import com.aaron.cloud.common.metering.entity.MeteringUsageEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据概览 Token 聚合：在应用层解析 {@code ref_json}，避免 MariaDB 等环境 JSON 函数不可用或
 * {@code promptTokens} 解析为 0 的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeteringTokenDashboardAggregator {

    static final int TOP_MODEL_LIMIT = 3;
    static final int TREND_DAYS = 30;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MeteringUsageEventRepository meteringUsageEventRepository;
    private final ObjectMapper objectMapper;

    record Aggregated(
            long prompt24,
            long completion24,
            List<Map<String, Object>> dailyTokenRows7d,
            Map<String, Object> tenant7dRaw,
            List<Map<String, Object>> modelTrendRaw30d) {}

    Aggregated aggregate(
            long tenantId,
            LocalDateTime since24h,
            LocalDate start7Inclusive,
            LocalDate start30Inclusive,
            LocalDate endInclusive,
            LocalDateTime rangeEndExclusive) {
        LocalDateTime rangeStart30 = start30Inclusive.atStartOfDay();
        LocalDateTime start7Dt = start7Inclusive.atStartOfDay();
        List<MeteringUsageEvent> events =
                meteringUsageEventRepository.listTokenEventsInRange(
                        tenantId, rangeStart30, rangeEndExclusive);

        Map<String, long[]> daily7d = new HashMap<>();
        long prompt7d = 0;
        long completion7d = 0;
        long prompt24 = 0;
        long completion24 = 0;
        Map<String, Map<String, long[]>> modelDaily30 = new HashMap<>();
        Map<String, long[]> byModel30 = new HashMap<>();

        for (MeteringUsageEvent ev : events) {
            if (ev.getCreatedAt() == null) {
                continue;
            }
            TokenSlice slice = parseSlice(ev.getRefJson(), ev.getQuantity());
            String day = ev.getCreatedAt().toLocalDate().format(DAY_FMT);
            String model = slice.modelAlias();

            modelDaily30
                    .computeIfAbsent(model, k -> new HashMap<>())
                    .merge(day, new long[] {slice.prompt(), slice.completion()}, MeteringTokenDashboardAggregator::addPair);
            byModel30.merge(model, new long[] {slice.prompt(), slice.completion()}, MeteringTokenDashboardAggregator::addPair);

            if (!ev.getCreatedAt().isBefore(start7Dt)) {
                daily7d.merge(day, new long[] {slice.prompt(), slice.completion()}, MeteringTokenDashboardAggregator::addPair);
                prompt7d += slice.prompt();
                completion7d += slice.completion();
            }
            if (!ev.getCreatedAt().isBefore(since24h)) {
                prompt24 += slice.prompt();
                completion24 += slice.completion();
            }
        }

        List<Map<String, Object>> dailyRows7d = new ArrayList<>();
        for (Map.Entry<String, long[]> e : daily7d.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("bucket", e.getKey());
            row.put("prompt_sum", e.getValue()[0]);
            row.put("completion_sum", e.getValue()[1]);
            dailyRows7d.add(row);
        }

        List<String> topModelAliases =
                byModel30.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                                (Map.Entry<String, long[]> e) ->
                                                        e.getValue()[0] + e.getValue()[1])
                                        .reversed())
                        .limit(TOP_MODEL_LIMIT)
                        .map(Map.Entry::getKey)
                        .toList();

        List<Map<String, Object>> modelTrendRaw = new ArrayList<>();
        for (String alias : topModelAliases) {
            Map<String, long[]> dayMap = modelDaily30.getOrDefault(alias, Map.of());
            List<Map<String, Object>> dailyRows = new ArrayList<>();
            for (LocalDate d = start30Inclusive; !d.isAfter(endInclusive); d = d.plusDays(1)) {
                String key = d.format(DAY_FMT);
                long[] v = dayMap.getOrDefault(key, new long[] {0, 0});
                Map<String, Object> row = new HashMap<>();
                row.put("bucket", key);
                row.put("prompt_sum", v[0]);
                row.put("completion_sum", v[1]);
                dailyRows.add(row);
            }
            Map<String, Object> modelRow = new HashMap<>();
            modelRow.put("model_alias", alias);
            modelRow.put("daily", dailyRows);
            modelTrendRaw.add(modelRow);
        }

        Map<String, Object> tenant7d = new HashMap<>();
        tenant7d.put("prompt_sum", prompt7d);
        tenant7d.put("completion_sum", completion7d);

        return new Aggregated(prompt24, completion24, dailyRows7d, tenant7d, modelTrendRaw);
    }

    private TokenSlice parseSlice(String refJson, BigDecimal quantity) {
        int prompt = 0;
        int completion = 0;
        String modelAlias = "—";
        int totalFromJson = 0;
        if (refJson != null && !refJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(refJson);
                JsonNode tokenNode =
                        root.has("usage") && root.get("usage").isObject() ? root.get("usage") : root;
                prompt = intField(tokenNode, "promptTokens", "prompt_tokens");
                completion = intField(tokenNode, "completionTokens", "completion_tokens");
                if (prompt == 0 && completion == 0 && tokenNode != root) {
                    prompt = intField(root, "promptTokens", "prompt_tokens");
                    completion = intField(root, "completionTokens", "completion_tokens");
                }
                totalFromJson = intField(root, "totalTokens", "total_tokens");
                if (totalFromJson == 0) {
                    totalFromJson = intField(tokenNode, "totalTokens", "total_tokens");
                }
                modelAlias = textField(root, "modelAlias", "model_alias");
            } catch (Exception ex) {
                log.trace("metering ref_json parse skip: {}", ex.getMessage());
            }
        }
        if (prompt == 0 && completion == 0) {
            int total = totalFromJson;
            if (total <= 0 && quantity != null) {
                total = quantity.intValue();
            }
            if (total > 0) {
                completion = total;
            }
        }
        if (modelAlias.isBlank()) {
            modelAlias = "—";
        }
        return new TokenSlice(prompt, completion, modelAlias);
    }

    private static int intField(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode n = root.get(name);
            if (n != null && n.isNumber()) {
                return n.intValue();
            }
            if (n != null && n.isTextual()) {
                try {
                    return Integer.parseInt(n.asText().trim());
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return 0;
    }

    private static String textField(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode n = root.get(name);
            if (n != null && n.isTextual()) {
                return n.asText().trim();
            }
        }
        return "";
    }

    private static long[] addPair(long[] a, long[] b) {
        return new long[] {a[0] + b[0], a[1] + b[1]};
    }

    private record TokenSlice(int prompt, int completion, String modelAlias) {}
}
