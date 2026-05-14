package com.aaron.cloud.common.api.enums;

/**
 * 管理端意图 {@code extra_config_json.handlerParams} 动态表单项类型；与 {@link com.aaron.cloud.common.api.dto.IntentHandlerConfigFieldMeta} 对齐。
 */
public enum IntentHandlerConfigValueKind {
    STRING,
    BOOLEAN,
    /** 管理端数字输入；落库为 JSON 整数 */
    INT,
    /** 密码框展示；仍明文存 JSON，须配合 HTTPS 与库权限 */
    SECRET_STRING
}

