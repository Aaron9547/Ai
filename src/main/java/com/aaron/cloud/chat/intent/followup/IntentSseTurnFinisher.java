package com.aaron.cloud.chat.intent.followup;

import com.aaron.cloud.chat.intent.ChatIntentSseHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 意图 SSE 回合统一收尾：可选追问 chip + {@code end} 帧。 */
@UtilityClass
public final class IntentSseTurnFinisher {

    /**
     * 在正文已下发后调用：按 {@link IntentFollowUpContext} 解析并推送 {@code followUpPrompts}，再发送 {@code end}。
     */
    public static void sendFollowUpAndEnd(
            ObjectMapper objectMapper,
            SseEmitter emitter,
            AtomicInteger seq,
            IntentFollowUpContext followUpContext)
            throws IOException {
        sendFollowUpAndEnd(objectMapper, emitter, seq, IntentFollowUpPromptCatalog.resolve(followUpContext));
    }

    /** 使用已解析的追问文案（与落库 meta 一致），避免 catalog 解析两次结果漂移。 */
    public static void sendFollowUpAndEnd(
            ObjectMapper objectMapper,
            SseEmitter emitter,
            AtomicInteger seq,
            List<String> promptTexts)
            throws IOException {
        if (promptTexts != null && !promptTexts.isEmpty()) {
            ChatIntentSseHelper.sendFollowUpPrompts(objectMapper, emitter, seq, promptTexts);
        }
        emitter.send(
                SseEmitter.event()
                        .data(ChatIntentSseHelper.sseEnd(objectMapper, seq))
                        .id(String.valueOf(seq.incrementAndGet())));
    }
}
