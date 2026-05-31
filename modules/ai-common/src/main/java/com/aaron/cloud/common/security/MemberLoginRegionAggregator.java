package com.aaron.cloud.common.security;

import com.aaron.cloud.common.web.ClientIpRegionLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 在册成员最近登录地区聚合：库内 {@code last_login_region} 优先，空则按 {@code last_login_ip} 离线解析。 */
public final class MemberLoginRegionAggregator {

    private static final int MAX_BUCKETS = 80;

    private MemberLoginRegionAggregator() {}

    public static List<Map<String, Object>> aggregate(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String bucket = effectiveBucket(str(row.get("region")), str(row.get("ip")));
            counts.merge(bucket, 1L, Long::sum);
        }
        List<Map.Entry<String, Long>> sorted =
                counts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                        .limit(MAX_BUCKETS)
                        .toList();
        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Long> e : sorted) {
            out.add(Map.of("bucket", e.getKey(), "cnt", e.getValue()));
        }
        return out;
    }

    private static String effectiveBucket(String region, String ip) {
        if (region != null && !region.isBlank()) {
            return region.trim();
        }
        String fromIp = ClientIpRegionLookup.resolveRegion(ip);
        if (fromIp != null && !fromIp.isBlank()) {
            return fromIp.trim();
        }
        return "—";
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
