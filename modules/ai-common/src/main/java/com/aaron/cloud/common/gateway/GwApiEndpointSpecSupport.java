package com.aaron.cloud.common.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 解析 gw_api_endpoint.request_spec_json / response_spec_json 并渲染为 Markdown 表格。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GwApiEndpointSpecSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Data
    public static class ParamRow {
        private String name;
        private String in;
        private String type;
        private Boolean required;
        private String description;
    }

    public static List<ParamRow> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ParamRow> rows = MAPPER.readValue(json.trim(), new TypeReference<>() {});
            return rows == null ? List.of() : rows;
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** {@code null}、空白或解析后空数组视为未配置。 */
    public static boolean isBlankSpec(String json) {
        return parse(json).isEmpty();
    }

    public static String toRequestMarkdown(String json) {
        return toMarkdownTable("入参", parse(json), true);
    }

    public static String toResponseMarkdown(String json) {
        return toMarkdownTable("出参", parse(json), false);
    }

    private static String toMarkdownTable(String title, List<ParamRow> rows, boolean includeIn) {
        if (rows == null || rows.isEmpty()) {
            return "**" + title + "**：未配置\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n\n");
        if (includeIn) {
            sb.append("| 参数 | 位置 | 类型 | 必填 | 说明 |\n");
            sb.append("|------|------|------|------|------|\n");
            for (ParamRow r : rows) {
                sb.append("| `").append(nullSafe(r.getName())).append("` | ");
                sb.append(nullSafe(r.getIn())).append(" | ");
                sb.append(nullSafe(r.getType())).append(" | ");
                sb.append(Boolean.TRUE.equals(r.getRequired()) ? "是" : "否").append(" | ");
                sb.append(escape(nullSafe(r.getDescription()))).append(" |\n");
            }
        } else {
            sb.append("| 字段 | 类型 | 说明 |\n");
            sb.append("|------|------|------|\n");
            for (ParamRow r : rows) {
                sb.append("| `").append(nullSafe(r.getName())).append("` | ");
                sb.append(nullSafe(r.getType())).append(" | ");
                sb.append(escape(nullSafe(r.getDescription()))).append(" |\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String nullSafe(String v) {
        return v == null || v.isBlank() ? "-" : v.trim();
    }

    private static String escape(String v) {
        return v.replace("|", "\\|").replace("\n", " ");
    }
}
