package com.aaron.cloud.common.metering;



/**

 * {@code metering_usage_event.ref_json} 中 prompt/completion 的 SQL 聚合片段（仅 token 计量）。

 *

 * <p>不使用 {@code JSON_EXTRACT}/{@code JSON_UNQUOTE}：兼容 MySQL 5.6 与无 JSON 函数的 MariaDB。

 * {@code ref_json} 由应用层 Jackson 按固定键顺序紧凑序列化（见 {@code LlmUsagePersistenceService}）。

 */

final class MeteringTokenAggregationSql {



    private static final String PROMPT_FIELD =

            "COALESCE(" + MeteringRefJsonSqlSupport.jsonLongField("\"promptTokens\":") + ", 0)";

    private static final String COMPLETION_FIELD =

            "COALESCE(" + MeteringRefJsonSqlSupport.jsonLongField("\"completionTokens\":") + ", 0)";

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

            "COALESCE("

                    + MeteringRefJsonSqlSupport.jsonQuotedStringField("\"modelAlias\":\"")

                    + ", '-')";



    static final String USAGE_SCENE_EXPR =

            "COALESCE("

                    + MeteringRefJsonSqlSupport.jsonQuotedStringField("\"usageScene\":\"")

                    + ", '-')";



    private MeteringTokenAggregationSql() {}

}

