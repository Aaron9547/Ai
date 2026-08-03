package com.aaron.cloud.notification.message.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;

final class MessageDeliveryRequestJsonSupport {

    private MessageDeliveryRequestJsonSupport() {}

    record Parsed(String messageSubject, String messageBody, Map<String, String> templateVars) {}

    static Parsed parse(ObjectMapper objectMapper, String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            return new Parsed("", "", Map.of());
        }
        try {
            Map<String, Object> map = objectMapper.readValue(requestJson, new TypeReference<>() {});
            String subject = stringVal(map.get("subject"));
            String body = stringVal(map.get("body"));
            Map<String, String> vars = parseTemplateVars(map.get("templateVars"));
            return new Parsed(subject, body, vars);
        } catch (Exception e) {
            return new Parsed("", "", Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseTemplateVars(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> m) {
            Map<String, String> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
            return Collections.unmodifiableMap(out);
        }
        return Map.of();
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }
}
