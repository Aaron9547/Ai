package com.aaron.cloud.common.metering;

/**
 * {@code metering_usage_event.ref_json} 中 prompt/completion 的 SQL 聚合片段（仅 token 计量）。
 *
 * <p>不使用 {@code JSON_UNQUOTE}：部分 MariaDB / 旧版 MySQL 会报 {@code FUNCTION … JSON_UNQUOTE does not exist}。
 * 数值字段直接 {@code CAST(JSON_EXTRACT(…) AS SIGNED)}；字符串别名用 {@code TRIM(BOTH '"' FROM CAST(… AS CHAR))}。
 */
final class MeteringTokenAggregationSql {

    private static final String PROMPT_JSON = "JSON_EXTRACT(ref_json, '$.promptTokens')";
    private static final String COMPLETION_JSON = "JSON_EXTRACT(ref_json, '$.completionTokens')";

    private static final String PROMPT_FIELD =
            "COALESCE(CAST(" + PROMPT_JSON + " AS SIGNED), 0)";
    private static final String COMPLETION_FIELD =
            "COALESCE(CAST(" + COMPLETION_JSON + " AS SIGNED), 0)";
    private static final String SPLIT_SUM = PROMPT_FIELD + " + " + COMPLETION_FIELD;

    static final String SUM_PROMPT =
            "COALESCE(SUM(CASE WHEN " + SPLIT_SUM + " > 0 THEN " + PROMPT_FIELD + " ELSE 0 END), 0) AS prompt_sum";

    static final String SUM_COMPLETION =
            "COALESCE(SUM(CASE WHEN "
                    + SPLIT_SUM
                    + " > 0 THEN "
                    + COMPLETION_FIELD
                    + " ELSE COALESCE(quantity, 0) END), 0) AS completion_sum";

    static final String MODEL_ALIAS_EXPR =
            "COALESCE(NULLIF(TRIM(BOTH '\"' FROM CAST(JSON_EXTRACT(ref_json, '$.modelAlias') AS CHAR(255))), ''), '-')";

    static final String USAGE_SCENE_EXPR =
            "COALESCE(NULLIF(TRIM(BOTH '\"' FROM CAST(JSON_EXTRACT(ref_json, '$.usageScene') AS CHAR(64))), ''), '-')";

    private MeteringTokenAggregationSql() {}
}
