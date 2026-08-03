package com.aaron.cloud.chat.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
            throws JsonProcessingException, IOException {
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

    public static void sendContentChunk(ObjectMapper objectMapper, SseEmitter emitter, AtomicInteger seq, String text)
            throws JsonProcessingException, IOException {
        ObjectNode wrap = objectMapper.createObjectNode();
        wrap.put("type", "content");
        wrap.put("v", text == null ? "" : text);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(wrap)).id(String.valueOf(seq.incrementAndGet())));
    }

    public static String sseEnd(ObjectMapper objectMapper, AtomicInteger seq) throws JsonProcessingException {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("type", "end");
        return objectMapper.writeValueAsString(o);
    }

    /** 与开放对话 {@code followUpPrompts} 帧格式一致，供意图专用流下发可点击追问。 */
    public static void sendFollowUpPrompts(
            ObjectMapper objectMapper, SseEmitter emitter, AtomicInteger seq, List<String> promptTexts)
            throws java.io.IOException {
        if (promptTexts == null || promptTexts.isEmpty()) {
            return;
        }
        ObjectNode root = objectMapper.createObjectNode();
        com.aaron.cloud.chat.intent.followup.IntentFollowUpSseItems.writeItems(root, promptTexts);
        if (!root.has("items") || root.get("items").isEmpty()) {
            return;
        }
        ObjectNode wrap = objectMapper.createObjectNode();
        wrap.put("type", "followUpPrompts");
        wrap.put("v", objectMapper.writeValueAsString(root));
        emitter.send(
                SseEmitter.event()
                        .data(objectMapper.writeValueAsString(wrap))
                        .id(String.valueOf(seq.incrementAndGet())));
    }
}
