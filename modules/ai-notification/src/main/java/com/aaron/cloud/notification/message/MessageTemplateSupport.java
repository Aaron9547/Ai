package com.aaron.cloud.notification.message;

import java.util.Map;

public final class MessageTemplateSupport {

    private MessageTemplateSupport() {}

    public static String applyTemplate(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }
}
