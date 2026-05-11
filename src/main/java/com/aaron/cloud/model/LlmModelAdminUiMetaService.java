package com.aaron.cloud.model;

import com.aaron.cloud.common.api.enums.LlmConnectorKind;
import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.EnumOption;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.FormFieldMeta;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ListColumnMeta;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.LlmModelAdminMetaResponse;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ModelKindTabMeta;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.ShowUnless;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 管理端可配置模型 UI 元数据：Tab、列表列、表单字段与下拉选项均由本服务从枚举/注册表生成；新增 {@link LlmModelKind} 或对接枚举时在此补全描述即可驱动前端。
 *
 * <p>表单 {@code label}、列表列名、下拉项 {@code label} 须简短（见仓库根目录 {@code .cursorrules} §7.3）；长说明放 {@code placeholder} 或文档。
 */
@Service
public class LlmModelAdminUiMetaService {

    public LlmModelAdminMetaResponse buildMeta() {
        Map<String, List<EnumOption>> optionLists = new LinkedHashMap<>();
        optionLists.put("vectorBackends", vectorBackendOptions());
        optionLists.put("connectorKinds", connectorKindOptions());

        List<ModelKindTabMeta> tabs = new ArrayList<>();
        for (LlmModelKind k : LlmModelKind.values()) {
            tabs.add(
                    new ModelKindTabMeta(
                            k.getCode(), adminTabLabel(k), adminTabOrder(k), listColumns(k), formFields(k)));
        }
        tabs.sort(Comparator.comparingInt(ModelKindTabMeta::sortOrder));
        return new LlmModelAdminMetaResponse(List.copyOf(tabs), Map.copyOf(optionLists));
    }

    private static List<EnumOption> vectorBackendOptions() {
        List<EnumOption> out = new ArrayList<>();
        out.add(new EnumOption(LlmVectorBackend.OPENAI_COMPATIBLE.getCode(), "OpenAI（/v1/embeddings）", 0));
        out.add(new EnumOption(LlmVectorBackend.VOLCENGINE_ARK.getCode(), "方舟文本（/embeddings）", 1));
        out.add(
                new EnumOption(
                        LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL.getCode(),
                        "方舟多模态（/embeddings/multimodal）",
                        2));
        return out;
    }

    private static List<EnumOption> connectorKindOptions() {
        List<EnumOption> out = new ArrayList<>();
        int i = 0;
        for (LlmConnectorKind c : LlmConnectorKind.values()) {
            out.add(new EnumOption(c.getCode(), connectorLabel(c), i++));
        }
        return out;
    }

    private static String connectorLabel(LlmConnectorKind c) {
        return switch (c) {
            case OPENAI_COMPATIBLE_JSON -> "OpenAI 兼容（JSON）";
        };
    }

    private static String adminTabLabel(LlmModelKind k) {
        return switch (k) {
            case LANGUAGE -> "语言模型（对话）";
            case SPEECH -> "语音模型（内部）";
            case VISION -> "视觉模型（内部）";
            case VECTOR -> "向量模型（嵌入）";
            case SMART_ROUTING -> "智能路由（内部）";
        };
    }

    private static int adminTabOrder(LlmModelKind k) {
        return switch (k) {
            case LANGUAGE -> 0;
            case VECTOR -> 1;
            case SPEECH -> 2;
            case VISION -> 3;
            case SMART_ROUTING -> 4;
        };
    }

    private static List<ListColumnMeta> listColumns(LlmModelKind k) {
        List<ListColumnMeta> c = new ArrayList<>();
        c.add(new ListColumnMeta("alias", "别名", "text"));
        c.add(new ListColumnMeta("displayName", "显示名", "text"));
        if (k == LlmModelKind.VECTOR) {
            c.add(new ListColumnMeta("vectorBackend", "嵌入策略", "enum", "vectorBackends"));
        }
        c.add(new ListColumnMeta("openaiBaseUrl", "Base URL", "text"));
        c.add(new ListColumnMeta("openaiModelId", "模型 ID", "text"));
        c.add(new ListColumnMeta("localDeploy", "本地部署", "boolTag"));
        c.add(new ListColumnMeta("apiKeyConfigured", "Key", "boolTag"));
        c.add(new ListColumnMeta("allowAnonymous", "访客", "boolTag"));
        if (k != LlmModelKind.VECTOR) {
            c.add(new ListColumnMeta("maxAttachments", "附件上限", "text"));
            c.add(new ListColumnMeta("supportsThinking", "思考", "boolTag"));
        }
        c.add(new ListColumnMeta("enabled", "启用", "boolSwitch"));
        c.add(new ListColumnMeta("tokensUsed", "Token 用量", "usage"));
        return c;
    }

    private static List<FormFieldMeta> formFields(LlmModelKind k) {
        List<FormFieldMeta> f = new ArrayList<>();
        f.add(new FormFieldMeta("alias", "别名", "text", true, true, "租户内唯一，如 deepseek-main", null));
        f.add(new FormFieldMeta("displayName", "显示名", "text", true, false, "列表与对话中展示的名称", null));
        if (k == LlmModelKind.VECTOR) {
            f.add(
                    new FormFieldMeta(
                            "vectorBackend",
                            "嵌入策略",
                            "select",
                            true,
                            false,
                            "选择向量接口形态；与 Base URL 路径需一致",
                            "vectorBackends"));
            f.add(
                    new FormFieldMeta(
                            "openaiBaseUrl",
                            "Base URL",
                            "text",
                            true,
                            false,
                            "服务根。OpenAI 示例：https://api.openai.com ；方舟文本/多模态：https://…/api/v3",
                            null));
        } else {
            f.add(
                    new FormFieldMeta(
                            "openaiBaseUrl",
                            "Base URL",
                            "text",
                            true,
                            false,
                            "须配到 chat/completions 之前的路径；OpenAI 用 …/v1 ，方舟用 …/api/v3",
                            null));
        }
        f.add(new FormFieldMeta("openaiModelId", "模型 ID", "text", true, false, "厂商侧模型名或 endpoint id", null));
        f.add(
                new FormFieldMeta(
                        "localDeploy",
                        "本地部署",
                        "switch",
                        false,
                        false,
                        "向量模型开启后走 Feign：配 base-url 直连，或开 Eureka 并用 service-id（默认 ly-ai-rag-svc）",
                        null));
        f.add(
                new FormFieldMeta(
                        "apiKey",
                        "API Key",
                        "password",
                        k != LlmModelKind.VECTOR,
                        false,
                        k == LlmModelKind.VECTOR
                                ? "可留空（免鉴权）；编辑留空表示不轮换"
                                : "新建必填；编辑留空表示不轮换",
                        null));
        if (k == LlmModelKind.VECTOR) {
            f.add(
                    new FormFieldMeta(
                            "clearApiKey",
                            "清除已存密钥",
                            "checkbox",
                            false,
                            false,
                            "勾选保存后清除库中密钥，改为免鉴权",
                            null));
        }
        f.add(new FormFieldMeta("allowAnonymous", "允许访客", "switch", false, false, null, null));
        if (k != LlmModelKind.VECTOR) {
            f.add(new FormFieldMeta("maxAttachments", "附件上限", "number", false, false, null, null));
            f.add(new FormFieldMeta("supportsThinking", "思考链", "switch", false, false, "是否展示思考过程（若模型支持）", null));
        }
        f.add(new FormFieldMeta("enabled", "启用", "switch", false, false, null, null));
        f.add(new FormFieldMeta("sortOrder", "排序", "number", false, false, "列表与选择器中的顺序，数字越小越靠前", null));
        f.add(new FormFieldMeta("tokenQuotaUnlimited", "Token 不限", "switch", false, false, null, null));
        f.add(
                new FormFieldMeta(
                        "tokenQuotaTotal",
                        "Token 上限",
                        "number",
                        false,
                        false,
                        "关闭「Token 不限」时生效；单位与厂商账单一致",
                        null,
                        new ShowUnless("tokenQuotaUnlimited", true)));
        return f;
    }
}
