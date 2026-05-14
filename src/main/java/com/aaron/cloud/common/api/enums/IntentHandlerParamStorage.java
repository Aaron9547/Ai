package com.aaron.cloud.common.api.enums;

/**
 * 意图处理器表单项写入 {@code chat_intent_definition.extra_config_json} 的顶层键；由参数枚举 {@link com.aaron.cloud.common.api.intent.IntentHandlerParamSpec#paramStorage()} 与 {@link com.aaron.cloud.common.api.intent.IntentHandlerParamSpec#paramName()} 声明。
 */
public enum IntentHandlerParamStorage {
    /** 与 {@code TravelHandlerParams} 等解析路径一致：{@code extra_config_json.handlerParams} */
    HANDLER_PARAMS,
    /** 出差多轮路由：{@code extra_config_json.travelRouting}，见 {@link com.aaron.cloud.chat.intent.TravelIntentRoutingConfig} */
    TRAVEL_ROUTING
}
