package com.aaron.cloud.common.security;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 解析 JWT 中 {@code tms}（租户成员关系 JSON 数组）声明。 */
public final class TenantJwtTmsParser {

    private static final ObjectMapper OM = new ObjectMapper();

    private TenantJwtTmsParser() {}

    public record Entry(long tenantId, TenantMemberRole role) {}

    public static List<Entry> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OM.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<Entry> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (!n.isObject()) {
                    continue;
                }
                long tid = n.path("tid").asLong(0);
                TenantMemberRole r = TenantMemberRole.fromClaim(n.path("tmr").asText(null));
                if (tid > 0 && r != null) {
                    out.add(new Entry(tid, r));
                }
            }
            return Collections.unmodifiableList(out);
        } catch (Exception e) {
            return List.of();
        }
    }
}
