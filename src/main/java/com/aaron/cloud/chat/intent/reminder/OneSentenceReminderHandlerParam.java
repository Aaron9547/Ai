package com.aaron.cloud.chat.intent.reminder;

import com.aaron.cloud.common.api.dto.IntentHandlerConfigOption;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerConfigValueKind;
import com.aaron.cloud.common.api.enums.intent.IntentHandlerParamStorage;
import com.aaron.cloud.common.api.intent.IntentHandlerParamSpec;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseToolCatalog;
import java.util.List;

public enum OneSentenceReminderHandlerParam implements IntentHandlerParamSpec {
    DEFAULT_MAX_DAYS(
            "defaultMaxDays",
            "默认截止天数",
            IntentHandlerConfigValueKind.INT,
            false,
            0,
            "90",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            1,
            365),
    MAX_ACTIVE_REMINDERS(
            "maxActiveReminders",
            "每用户有效提醒上限",
            IntentHandlerConfigValueKind.INT,
            false,
            10,
            "20",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            1,
            100),
    PARSE_TOOL_KIND(
            "parseToolKind",
            "话术解析方式",
            IntentHandlerConfigValueKind.SELECT,
            false,
            20,
            "",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null) {
        @Override
        public List<IntentHandlerConfigOption> selectOptions() {
            return ReminderParseToolCatalog.configOptions();
        }
    },
    PARSE_MODEL_ALIAS(
            "parseModelAlias",
            "智能解析模型别名",
            IntentHandlerConfigValueKind.STRING,
            false,
            25,
            "留空则使用租户默认对话模型",
            IntentHandlerParamStorage.HANDLER_PARAMS,
            null,
            null);

    private final String paramName;
    private final String labelZh;
    private final IntentHandlerConfigValueKind valueKind;
    private final boolean required;
    private final int sortOrder;
    private final String placeholder;
    private final IntentHandlerParamStorage paramStorage;
    private final Integer intMin;
    private final Integer intMax;

    OneSentenceReminderHandlerParam(
            String paramName,
            String labelZh,
            IntentHandlerConfigValueKind valueKind,
            boolean required,
            int sortOrder,
            String placeholder,
            IntentHandlerParamStorage paramStorage,
            Integer intMin,
            Integer intMax) {
        this.paramName = paramName;
        this.labelZh = labelZh;
        this.valueKind = valueKind;
        this.required = required;
        this.sortOrder = sortOrder;
        this.placeholder = placeholder;
        this.paramStorage = paramStorage;
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
        return paramStorage;
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
