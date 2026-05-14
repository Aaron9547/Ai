package com.aaron.cloud.chat.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * 仅当 JSON 为字面量 {@code true} 时视为开启联网；{@code false}、{@code null}、数字、字符串等一律视为关闭，避免意外类型强制为 true。
 */
public final class WebSearchFlagDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
