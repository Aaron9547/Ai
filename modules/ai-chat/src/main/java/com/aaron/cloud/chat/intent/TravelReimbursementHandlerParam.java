package com.aaron.cloud.chat.intent;

import com.aaron.cloud.common.api.enums.intent.IntentHandlerConfigValueKind;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerParamStorage;
import com.aaron.cloud.common.api.intent.IntentHandlerParamSpec;

/**
 * 出差报销意图在管理端的可配置项真源（枚举常量）；键名与 {@link TravelHandlerParams}、{@link TravelIntentRoutingConfig} 解析路径一致。
 */
public enum TravelReimbursementHandlerParam implements IntentHandlerParamSpec {
    ALLOW_DOC_ADVANCE_WITH_ATTACHMENT_ONLY(
            "allowDocAdvanceWithAttachmentOnly",
            "材料阶段：允许仅附件继续",
            IntentHandlerConfigValueKind.BOOLEAN,
            false,
            0,
            "关闭后须同轮正文含首轮触发词；仅附件走大模型",
            IntentHandlerParamStorage.TRAVEL_ROUTING,
            null,
            null),
    SESSION_EXPIRED_USER_HINT(
            "sessionExpiredUserHint",
            "会话失效提示文案",
            IntentHandlerConfigValueKind.STRING,
            false,
            1,
            "可选；勿写密钥",
            IntentHandlerParamStorage.TRAVEL_ROUTING,
            null,
            null),
    COZE_DOMAIN(
            "cozeDomain",
            "Coze API 域名",
            IntentHandlerConfigValueKind.STRING,
            false,
            10,
            "如 https://api.coze.cn；留空则用官方默认",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null),
    DOC_WORKFLOW_ID(
            "docWorkflowId",
            "文档解析工作流 ID",
            IntentHandlerConfigValueKind.STRING,
            false,
            20,
            "",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null),
    PLAN_WORKFLOW_ID(
            "planWorkflowId",
            "行程规划工作流 ID",
            IntentHandlerConfigValueKind.STRING,
            false,
            30,
            "",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null),
    DOC_COZE_API_KEY(
            "docCozeApiKey",
            "文档工作流 API Key",
            IntentHandlerConfigValueKind.SECRET_STRING,
            false,
            40,
            "Bearer PAT",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null),
    PLAN_COZE_API_KEY(
            "planCozeApiKey",
            "行程工作流 API Key",
            IntentHandlerConfigValueKind.SECRET_STRING,
            false,
            50,
            "Bearer PAT",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null);

    private final String paramName;
    private final String labelZh;
    private final IntentHandlerConfigValueKind valueKind;
    private final boolean required;
    private final int sortOrder;
    private final String placeholder;
    private final IntentHandlerParamStorage storage;
    private final Integer intMin;
    private final Integer intMax;

    TravelReimbursementHandlerParam(
            String paramName,
            String labelZh,
            IntentHandlerConfigValueKind valueKind,
            boolean required,
            int sortOrder,
            String placeholder,
            IntentHandlerParamStorage storage,
            Integer intMin,
            Integer intMax) {
        this.paramName = paramName;
        this.labelZh = labelZh;
        this.valueKind = valueKind;
        this.required = required;
        this.sortOrder = sortOrder;
        this.placeholder = placeholder;
        this.storage = storage;
        this.intMin = intMin;
        this.intMax = intMax;
    }

    @Override
    public String paramName() {
        return paramName;
    }

    @Override
    public String labelZh() {
        return labelZh;
    }

    @Override
    public IntentHandlerConfigValueKind valueKind() {
        return valueKind;
    }

    @Override
    public boolean required() {
        return required;
    }

    @Override
    public int sortOrder() {
        return sortOrder;
    }

    @Override
    public String placeholder() {
        return placeholder;
    }

    @Override
    public IntentHandlerParamStorage paramStorage() {
        return storage;
    }

    @Override
    public Integer intMin() {
        return intMin;
    }

    @Override
    public Integer intMax() {
        return intMax;
    }
}
