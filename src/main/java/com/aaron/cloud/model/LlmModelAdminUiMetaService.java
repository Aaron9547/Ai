package com.aaron.cloud.model;

import com.aaron.cloud.common.api.enums.llm.LlmConnectorKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmVectorBackend;
import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.EnumOption;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.FormFieldMeta;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ListColumnMeta;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.LlmModelAdminMetaResponse;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ModelKindTabMeta;
import com.aaron.cloud.common.web.locale.AdminUiLocaleResolver;
import com.aaron.cloud.model.config.LlmAdminMetaMessageSourceConfiguration;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ShowUnless;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import static com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource.*;

/**
 * 管理端可配置模型 UI 元数据：Tab、列表列、表单字段与下拉选项均由本服务从枚举/注册表生成；新增 {@link LlmModelKind} 或对接枚举时在此补全描述即可驱动前端。
 *
 * <p>文案由 {@code llm-admin-meta*.properties} + {@link MessageSource} 按请求 {@code Accept-Language} 解析；表单 {@code label}、列表列名、下拉项 {@code label} 须简短（见仓库根目录
 * {@code .cursorrules} §7.3）；长说明放 {@code placeholder} 或文档。
 */
@Service
public class LlmModelAdminUiMetaService {

    /** 与 {@code llm-admin-meta_zh_CN.properties}、{@link AdminUiLocaleResolver#CHINESE_ADMIN_UI} 一致 */
    private static final Locale FALLBACK_LOCALE = AdminUiLocaleResolver.CHINESE_ADMIN_UI;

    private final MessageSource messageSource;

    LlmModelAdminUiMetaService(
            @Qualifier(LlmAdminMetaMessageSourceConfiguration.BEAN_NAME) MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public LlmModelAdminMetaResponse buildMeta(Locale locale) {
        Locale effective = locale != null ? locale : FALLBACK_LOCALE;
        Map<String, List<EnumOption>> optionLists = new LinkedHashMap<>();
        optionLists.put("vectorBackends", vectorBackendOptions(effective));
        optionLists.put("connectorKinds", connectorKindOptions(effective));
        optionLists.put("webSearchProviders", webSearchProviderOptions(effective));

        List<ModelKindTabMeta> tabs = new ArrayList<>();
        for (LlmModelKind k : LlmModelKind.values()) {
            tabs.add(
                    new ModelKindTabMeta(
                            k.getCode(),
                            adminTabLabel(k, effective),
                            adminTabOrder(k),
                            listColumns(k, effective),
                            formFields(k, effective)));
        }
        tabs.sort(Comparator.comparingInt(ModelKindTabMeta::sortOrder));
        return new LlmModelAdminMetaResponse(List.copyOf(tabs), Map.copyOf(optionLists), null, null);
    }

    private String msg(String code, Locale locale) {
        try {
            return messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException ex) {
            try {
                return messageSource.getMessage(code, null, FALLBACK_LOCALE);
            } catch (NoSuchMessageException ex2) {
                return code;
            }
        }
    }

    private List<EnumOption> vectorBackendOptions(Locale locale) {
        List<EnumOption> out = new ArrayList<>();
        int i = 0;
        for (LlmVectorBackend b : LlmVectorBackend.values()) {
            out.add(new EnumOption(b.getCode(), msg(vectorBackendMessageKey(b), locale), i++));
        }
        return out;
    }

    /** 使用完整 key 字面量，便于 IDE 在 {@code llm-admin-meta_*.properties} 中建立引用（勿改前缀/后缀与枚举名不一致）。 */
    private static String vectorBackendMessageKey(LlmVectorBackend b) {
        return switch (b) {
            case OPENAI_COMPATIBLE -> "llm.meta.vectorBackend.OPENAI_COMPATIBLE";
            case VOLCENGINE_ARK -> "llm.meta.vectorBackend.VOLCENGINE_ARK";
            case VOLCENGINE_ARK_MULTIMODAL -> "llm.meta.vectorBackend.VOLCENGINE_ARK_MULTIMODAL";
            case DASHSCOPE_COMPATIBLE -> "llm.meta.vectorBackend.DASHSCOPE_COMPATIBLE";
            case DASHSCOPE_TEXT_EMBEDDING -> "llm.meta.vectorBackend.DASHSCOPE_TEXT_EMBEDDING";
            case DASHSCOPE_MULTIMODAL_EMBEDDING -> "llm.meta.vectorBackend.DASHSCOPE_MULTIMODAL_EMBEDDING";
        };
    }

    private List<EnumOption> webSearchProviderOptions(Locale locale) {
        List<EnumOption> out = new ArrayList<>();
        int i = 0;
        for (LlmWebSearchProvider p : LlmWebSearchProvider.values()) {
            out.add(new EnumOption(p.getCode(), msg(webSearchProviderMessageKey(p), locale), i++));
        }
        return out;
    }

    private static String webSearchProviderMessageKey(LlmWebSearchProvider p) {
        return "llm.meta.webSearchProvider." + p.name();
    }

    private List<EnumOption> connectorKindOptions(Locale locale) {
        List<EnumOption> out = new ArrayList<>();
        int i = 0;
        for (LlmConnectorKind c : LlmConnectorKind.values()) {
            out.add(new EnumOption(c.getCode(), msg(connectorKindMessageKey(c), locale), i++));
        }
        return out;
    }

    private static String connectorKindMessageKey(LlmConnectorKind c) {
        return switch (c) {
            case OPENAI_COMPATIBLE_JSON -> "llm.meta.connector.OPENAI_COMPATIBLE_JSON";
        };
    }

    /** 使用完整 key 字面量，便于 IDE 在 {@code llm-admin-meta_*.properties} 中建立引用。 */
    private String adminTabLabel(LlmModelKind k, Locale locale) {
        String code =
                switch (k) {
                    case LANGUAGE -> "llm.meta.modelKind.LANGUAGE";
                    case SPEECH -> "llm.meta.modelKind.SPEECH";
                    case VISION -> "llm.meta.modelKind.VISION";
                    case VECTOR -> "llm.meta.modelKind.VECTOR";
                    case SMART_ROUTING -> "llm.meta.modelKind.SMART_ROUTING";
                    case WEB_SEARCH -> "llm.meta.modelKind.WEB_SEARCH";
                };
        return msg(code, locale);
    }

    private static int adminTabOrder(LlmModelKind k) {
        return switch (k) {
            case LANGUAGE -> 0;
            case WEB_SEARCH -> 1;
            case VECTOR -> 2;
            case SPEECH -> 3;
            case VISION -> 4;
            case SMART_ROUTING -> 5;
        };
    }

    private List<ListColumnMeta> listColumns(LlmModelKind k, Locale locale) {
        List<ListColumnMeta> c = new ArrayList<>();
        c.add(new ListColumnMeta("alias", msg("llm.meta.col.alias", locale), "text"));
        c.add(new ListColumnMeta("displayName", msg("llm.meta.col.displayName", locale), "text"));
        if (k == LlmModelKind.VECTOR) {
            c.add(new ListColumnMeta("integrationBackend", msg("llm.meta.col.integrationBackend", locale), "enum", "vectorBackends"));
        }
        if (k == LlmModelKind.WEB_SEARCH) {
            c.add(
                    new ListColumnMeta(
                            "integrationBackend",
                            msg("llm.meta.col.integrationBackend", locale),
                            "enum",
                            "webSearchProviders"));
        }
        c.add(new ListColumnMeta("openaiBaseUrl", msg("llm.meta.col.openaiBaseUrl", locale), "text"));
        c.add(new ListColumnMeta("openaiModelId", msg("llm.meta.col.openaiModelId", locale), "text"));
        if (k == LlmModelKind.LANGUAGE) {
            c.add(new ListColumnMeta("fallbackModelAlias", msg("llm.meta.col.fallbackModelAlias", locale), "text"));
        }
        if (k != LlmModelKind.WEB_SEARCH) {
            c.add(new ListColumnMeta("localDeploy", msg("llm.meta.col.localDeploy", locale), "boolTag"));
        }
        c.add(new ListColumnMeta("apiKeyConfigured", msg("llm.meta.col.apiKeyConfigured", locale), "boolTag"));
        c.add(new ListColumnMeta("allowAnonymous", msg("llm.meta.col.allowAnonymous", locale), "boolTag"));
        if (k != LlmModelKind.VECTOR && k != LlmModelKind.WEB_SEARCH) {
            c.add(new ListColumnMeta("maxAttachments", msg("llm.meta.col.maxAttachments", locale), "text"));
            c.add(new ListColumnMeta("supportsThinking", msg("llm.meta.col.supportsThinking", locale), "boolTag"));
        }
        c.add(new ListColumnMeta("enabled", msg("llm.meta.col.enabled", locale), "boolSwitch"));
        c.add(new ListColumnMeta("tokensUsed", msg("llm.meta.col.tokensUsed", locale), "usage"));
        return c;
    }

    private List<FormFieldMeta> formFields(LlmModelKind k, Locale locale) {
        List<FormFieldMeta> f = new ArrayList<>();
        f.add(
                new FormFieldMeta(
                        "alias",
                        msg("llm.meta.form.alias.label", locale),
                        "text",
                        true,
                        true,
                        msg("llm.meta.form.alias.placeholder", locale),
                        null));
        f.add(
                new FormFieldMeta(
                        "displayName",
                        msg("llm.meta.form.displayName.label", locale),
                        "text",
                        true,
                        false,
                        msg("llm.meta.form.displayName.placeholder", locale),
                        null));
        if (k == LlmModelKind.VECTOR) {
            f.add(
                    new FormFieldMeta(
                            "integrationBackend",
                            msg("llm.meta.form.integrationBackend.label", locale),
                            "select",
                            true,
                            false,
                            msg("llm.meta.form.integrationBackend.placeholder.vector", locale),
                            "vectorBackends"));
            f.add(
                    new FormFieldMeta(
                            "openaiBaseUrl",
                            msg("llm.meta.form.openaiBaseUrl.label", locale),
                            "text",
                            true,
                            false,
                            msg("llm.meta.form.openaiBaseUrl.placeholder.vector", locale),
                            null));
        } else if (k == LlmModelKind.WEB_SEARCH) {
            f.add(
                    new FormFieldMeta(
                            "integrationBackend",
                            msg("llm.meta.form.integrationBackend.label", locale),
                            "select",
                            true,
                            false,
                            msg("llm.meta.form.integrationBackend.placeholder.webSearch", locale),
                            "webSearchProviders"));
            f.add(
                    new FormFieldMeta(
                            "openaiBaseUrl",
                            msg("llm.meta.form.openaiBaseUrl.label", locale),
                            "text",
                            true,
                            false,
                            msg("llm.meta.form.openaiBaseUrl.placeholder.webSearch", locale),
                            null));
        } else {
            f.add(
                    new FormFieldMeta(
                            "openaiBaseUrl",
                            msg("llm.meta.form.openaiBaseUrl.label", locale),
                            "text",
                            true,
                            false,
                            msg("llm.meta.form.openaiBaseUrl.placeholder.default", locale),
                            null));
        }
        f.add(
                new FormFieldMeta(
                        "openaiModelId",
                        msg("llm.meta.form.openaiModelId.label", locale),
                        "text",
                        true,
                        false,
                        k == LlmModelKind.WEB_SEARCH
                                ? msg("llm.meta.form.openaiModelId.placeholder.webSearch", locale)
                                : msg("llm.meta.form.openaiModelId.placeholder", locale),
                        null));
        if (k == LlmModelKind.LANGUAGE) {
            f.add(
                    new FormFieldMeta(
                            "fallbackModelAlias",
                            msg("llm.meta.form.fallbackModelAlias.label", locale),
                            "text",
                            false,
                            false,
                            msg("llm.meta.form.fallbackModelAlias.placeholder", locale),
                            null));
        }
        if (k != LlmModelKind.WEB_SEARCH) {
            f.add(
                    new FormFieldMeta(
                            "localDeploy",
                            msg("llm.meta.form.localDeploy.label", locale),
                            "switch",
                            false,
                            false,
                            msg("llm.meta.form.localDeploy.placeholder", locale),
                            null));
        }
        f.add(
                new FormFieldMeta(
                        "apiKey",
                        msg("llm.meta.form.apiKey.label", locale),
                        "password",
                        k != LlmModelKind.VECTOR,
                        false,
                        msg(
                                k == LlmModelKind.VECTOR
                                        ? "llm.meta.form.apiKey.placeholder.vector"
                                        : "llm.meta.form.apiKey.placeholder.nonVector",
                                locale),
                        null));
        if (k == LlmModelKind.VECTOR) {
            f.add(
                    new FormFieldMeta(
                            "clearApiKey",
                            msg("llm.meta.form.clearApiKey.label", locale),
                            "checkbox",
                            false,
                            false,
                            msg("llm.meta.form.clearApiKey.placeholder", locale),
                            null));
        }
        f.add(
                new FormFieldMeta(
                        "allowAnonymous",
                        msg("llm.meta.form.allowAnonymous.label", locale),
                        "switch",
                        false,
                        false,
                        null,
                        null));
        if (k != LlmModelKind.VECTOR && k != LlmModelKind.WEB_SEARCH) {
            f.add(
                    new FormFieldMeta(
                            "maxAttachments",
                            msg("llm.meta.form.maxAttachments.label", locale),
                            "number",
                            false,
                            false,
                            null,
                            null));
            f.add(
                    new FormFieldMeta(
                            "supportsThinking",
                            msg("llm.meta.form.supportsThinking.label", locale),
                            "switch",
                            false,
                            false,
                            msg("llm.meta.form.supportsThinking.placeholder", locale),
                            null));
        }
        f.add(
                new FormFieldMeta(
                        "enabled",
                        msg("llm.meta.form.enabled.label", locale),
                        "switch",
                        false,
                        false,
                        null,
                        null));
        f.add(
                new FormFieldMeta(
                        "sortOrder",
                        msg("llm.meta.form.sortOrder.label", locale),
                        "number",
                        false,
                        false,
                        msg("llm.meta.form.sortOrder.placeholder", locale),
                        null));
        f.add(
                new FormFieldMeta(
                        "tokenQuotaUnlimited",
                        msg("llm.meta.form.tokenQuotaUnlimited.label", locale),
                        "switch",
                        false,
                        false,
                        null,
                        null));
        f.add(
                new FormFieldMeta(
                        "tokenQuotaTotal",
                        msg("llm.meta.form.tokenQuotaTotal.label", locale),
                        "number",
                        false,
                        false,
                        msg("llm.meta.form.tokenQuotaTotal.placeholder", locale),
                        null,
                        new ShowUnless("tokenQuotaUnlimited", true)));
        return f;
    }
}
