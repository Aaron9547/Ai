package com.aaron.cloud.prompt;

/** 从 {@link PromptTemplateBuiltinCatalog} 输出 migrate 用 {@code INSERT IGNORE} 语句（stdout）。 */
public final class PromptTemplateSeedGenerator {

    private PromptTemplateSeedGenerator() {}

    public static void main(String[] args) {
        System.out.println("-- generated from PromptTemplateBuiltinCatalog");
        for (PromptTemplateBuiltinCatalog.Entry e : PromptTemplateBuiltinCatalog.allEntries()) {
            System.out.println(toInsert(e));
        }
    }

    static String toInsert(PromptTemplateBuiltinCatalog.Entry e) {
        String content = sqlEscape(e.content());
        String locale = sqlEscape(e.locale());
        String code = sqlEscape(e.promptCode());
        return "INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES "
                + "(0, '"
                + code
                + "', '"
                + e.kind().getCode()
                + "', '"
                + e.domain().getCode()
                + "', '"
                + locale
                + "', '"
                + content
                + "', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));";
    }

    private static String sqlEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("'", "''");
    }
}
