package com.aaron.cloud.chat.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class ChatIntentSseHelper {

    private ChatIntentSseHelper() {}

    public static void sendWorkflowStage(
            ObjectMapper objectMapper,
            SseEmitter emitter,
            AtomicInteger seq,
            String segmentId,
            String title,
            String mode,
            String status,
            String text)
            throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("segmentId", segmentId);
        if (title != null) {
            o.put("title", title);
        }
        o.put("mode", mode == null ? "block" : mode);
        o.put("status", status);
        o.put("text", text == null ? "" : text);
        ObjectNode wrap = objectMapper.createObjectNode();
        wrap.put("type", "workflowStage");
        wrap.put("v", objectMapper.writeValueAsString(o));
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(wrap)).id(String.valueOf(seq.incrementAndGet())));
    }

    public static String sseEnd(ObjectMapper objectMapper, AtomicInteger seq) throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", "end");
        return objectMapper.writeValueAsString(o);
    }
}
