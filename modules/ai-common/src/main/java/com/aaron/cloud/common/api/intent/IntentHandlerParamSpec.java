package com.aaron.cloud.common.api.intent;

import com.aaron.cloud.common.api.dto.IntentHandlerConfigOption;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerConfigValueKind;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerParamStorage;
import java.util.List;

/**
 * 意图处理器可配置项在 Java 侧的唯一真源：每个处理器定义一个枚举，枚举常量实现本接口；管理端通过
 * {@link com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin#handlerParamEnumClass()} 暴露，经 REST 转为
 * {@link com.aaron.cloud.common.api.dto.IntentHandlerConfigFieldMeta} 驱动动态表单。
 */
public interface IntentHandlerParamSpec {

    /** JSON 键名（与 {@code handlerParams.*} / {@code travelRouting.*} 一致）；勿命名为 {@code name}，避免与 {@link Enum#name()} 冲突。 */
    String paramName();

    String labelZh();

    IntentHandlerConfigValueKind valueKind();

    boolean required();

    int sortOrder();

    default String placeholder() {
        return "";
    }

    default IntentHandlerParamStorage paramStorage() {
        return IntentHandlerParamStorage.HANDLER_PARAMS;
    }

    /** 仅当 {@link #valueKind()} 为 {@link IntentHandlerConfigValueKind#INT} 时生效；均可为 null 表示不限制 */
    default Integer intMin() {
        return null;
    }

    default Integer intMax() {
        return null;
    }

    /** 仅当 {@link #valueKind()} 为 {@link IntentHandlerConfigValueKind#SELECT} 时生效。 */
    default List<IntentHandlerConfigOption> selectOptions() {
        return List.of();
    }
}
