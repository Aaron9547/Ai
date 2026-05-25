package com.aaron.cloud.common.api.dto;

import com.aaron.cloud.common.api.enums.intent.IntentHandlerConfigValueKind;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerParamStorage;

/**
 * 意图处理器在管理端暴露的可配置项元数据；写入位置由 {@link #paramStorage()} 与 {@link #name()} 决定
 * （如 {@code extra_config_json.handlerParams.cozeDomain}、{@code extra_config_json.travelRouting.allowDocAdvanceWithAttachmentOnly}）。
 */
public record IntentHandlerConfigFieldMeta(
        String name,
        String labelZh,
        IntentHandlerConfigValueKind valueKind,
        boolean required,
        int sortOrder,
        String placeholder,
        IntentHandlerParamStorage paramStorage,
        Integer intMin,
        Integer intMax) {

    public IntentHandlerConfigFieldMeta {
        if (paramStorage == null) {
            paramStorage = IntentHandlerParamStorage.HANDLER_PARAMS;
        }
    }
}
