package com.aaron.cloud.common.metering;

/**
 * {@code metering_usage_event.ref_json} 查询片段：Jackson 紧凑 JSON，避免 {@code JSON_EXTRACT}
 *（MySQL 5.6 / 部分 MariaDB 无 JSON 函数）。
 */
final class MeteringRefJsonSqlSupport {

    /** 与 {@code metering_usage_event.ref_json} 列及 {@code schema_v1.sql} 表级排序规则一致。 */
    private static final String REF_JSON_COLLATE = "utf8mb4_unicode_ci";

    private MeteringRefJsonSqlSupport() {}

    /**
     * MyBatis-Plus {@code .apply(sql, value)}：{@code ref_json} 中 {@code conversationId} 与参数等值。
     *
     * <p>Jackson 紧凑 JSON 写入数值型 {@code conversationId}（无引号）；用 {@link #jsonLongField} 同款
     * {@code SUBSTRING_INDEX} 提取，避免 MySQL 8 ICU {@code REGEXP} 将 {@code (,|})} 中 {@code }} 解析为
     * 非法量词语法（error 3688）。
     */
    static final String CONVERSATION_ID_EQUALS =
            "CAST(TRIM(TRAILING '}' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(ref_json, '\"conversationId\":', -1), ',', 1)) AS UNSIGNED) = CAST({0} AS UNSIGNED)";

    /**
     * MyBatis-Plus {@code .apply(sql, value)}：{@code usageScene} 字符串等值（{@code ref_json.usageScene}）。
     */
    static final String USAGE_SCENE_EQUALS =
            "SUBSTRING_INDEX(SUBSTRING_INDEX(ref_json, '\"usageScene\":\"', -1), '\"', 1) = CAST({0} AS CHAR"
                    + " CHARACTER SET utf8mb4) COLLATE "
                    + REF_JSON_COLLATE;

    /** 从紧凑 JSON 取数值字段（后接 {@code ,} 或 {@code }}）。 */
    static String jsonLongField(String fieldName) {
        return "CAST(TRIM(TRAILING '}' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(ref_json, '"
                + fieldName
                + "', -1), ',', 1)) AS SIGNED)";
    }

    /** 从紧凑 JSON 取引号包裹的字符串字段。 */
    static String jsonQuotedStringField(String fieldName) {
        return "NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(ref_json, '"
                + fieldName
                + "', -1), '\"', 1), '')";
    }
}
