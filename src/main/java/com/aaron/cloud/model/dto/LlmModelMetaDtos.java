package com.aaron.cloud.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 管理端「可配置模型」UI 元数据：由后端枚举与注册表生成，前端按 Tab + 字段描述渲染，避免前后端重复维护类型与列清单。
 */
public final class LlmModelMetaDtos {

    private LlmModelMetaDtos() {}

    /** 通用枚举项（模型类型、向量路径、对接协议等）。 */
    public record EnumOption(String code, String label, int sortOrder) {}

    /**
     * 列表列：{@code prop} 对应 {@link com.aaron.cloud.model.dto.LlmModelAdminDtos.LlmModelAdminView} JSON 字段名；{@code format} 为前端展示策略。
     */
    public record ListColumnMeta(String prop, String label, String format, String optionsKey) {
        public ListColumnMeta(String prop, String label, String format) {
            this(prop, label, format, null);
        }
    }

    /**
     * 表单字段；{@code selectOptionsKey} 非空时选项取自 {@link LlmModelAdminMetaResponse} 的 {@code optionLists} 中同名键。
     *
     * <p>{@code showUnless}：当表单中 {@code field} 取值等于 {@code equalsValue} 时隐藏本字段（JSON 序列化为小对象）。
     */
    public record FormFieldMeta(
            String key,
            String label,
            String control,
            boolean required,
            boolean disabledOnEdit,
            String placeholder,
            String selectOptionsKey,
            ShowUnless showUnless) {

        public FormFieldMeta(
                String key,
                String label,
                String control,
                boolean required,
                boolean disabledOnEdit,
                String placeholder,
                String selectOptionsKey) {
            this(key, label, control, required, disabledOnEdit, placeholder, selectOptionsKey, null);
        }
    }

    public record ShowUnless(String field, Object equalsValue) {}

    public record ModelKindTabMeta(
            String kind,
            String label,
            int sortOrder,
            List<ListColumnMeta> listColumns,
            List<FormFieldMeta> formFields) {}

    public record LlmModelAdminMetaResponse(
            List<ModelKindTabMeta> modelKindTabs,
            Map<String, List<EnumOption>> optionLists,
            /**
             * 服务端解析后的 BCP 47 标签（见 {@link com.aaron.cloud.common.web.locale.AdminUiLocaleResolver}）；仅 meta 接口由控制器写入。
             * 网关剥自定义响应头时仍可从 JSON 判断是否命中本实现。
             */
            String metaResolvedLocale,
            /** 收到的 {@code lang} 查询原始值；无查询参数时为 {@code null}。 */
            String metaLangParamRaw) {}
}
