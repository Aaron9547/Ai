package com.aaron.cloud.common.metering;

/**
 * {@code metering_usage_event.ref_json} 查询片段：Jackson 紧凑 JSON，避免 {@code JSON_EXTRACT}
 *（MySQL 5.6 / 部分 MariaDB 无 JSON 函数）。
 */
final class MeteringRefJsonSqlSupport {

    private MeteringRefJsonSqlSupport() {}

    /**
     * MyBatis-Plus {@code .apply(sql, value)}：{@code ref_json} 中 {@code conversationId} 与参数等值。
     */
    static final String CONVERSATION_ID_EQUALS =
            "ref_json REGEXP CONCAT('\"conversationId\":', CAST({0} AS CHAR), '(,|})')";

    /**
     * MyBatis-Plus {@code .apply(sql, value)}：{@code usageScene} 字符串等值（{@code ref_json.usageScene}）。
     */
    static final String USAGE_SCENE_EQUALS =
            "SUBSTRING_INDEX(SUBSTRING_INDEX(ref_json, '\"usageScene\":\"', -1), '\"', 1) = {0}";

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
