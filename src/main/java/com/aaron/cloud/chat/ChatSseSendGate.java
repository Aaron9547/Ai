package com.aaron.cloud.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 发送门闩：客户端断开或 {@link SseEmitter#complete()} 后跳过后续 send，避免刷屏 WARN。
 *
 * <p>不中断模型上游；用户主动取消经 {@link ChatStreamCancelRegistry} 单独置位。
 */
@Slf4j
public final class ChatSseSendGate {

    private final SseEmitter emitter;
    private final AtomicBoolean open = new AtomicBoolean(true);

    public ChatSseSendGate(SseEmitter emitter) {
        this.emitter = emitter;
        Runnable closeSseOnly = this::markSseClosed;
        emitter.onCompletion(closeSseOnly);
        emitter.onTimeout(closeSseOnly);
        emitter.onError(e -> markSseClosed());
    }

    public boolean isOpen() {
        return open.get();
    }

    /** 客户端断连 / complete：仅关闭 SSE 推送，不取消模型生成。 */
    public void markSseClosed() {
        open.set(false);
    }

    /**
     * @param logContext 非空时在非预期异常时 WARN；{@code null} 表示静默（如思考流在连接已断时）。
     */
    public boolean trySend(SseEmitter.SseEventBuilder event, String logContext) {
        if (!open.get()) {
            return false;
        }
        try {
            emitter.send(event);
            return true;
        } catch (IllegalStateException e) {
            markSseClosed();
            return false;
        } catch (Exception e) {
            markSseClosed();
            if (logContext != null && !logContext.isBlank()) {
                log.warn("[对话] SSE 推送{}失败", logContext, e);
            }
            return false;
        }
    }

    public void complete() {
        markSseClosed();
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // already completed (client disconnect)
        }
    }

    public void completeWithError(Throwable err) {
        markSseClosed();
        try {
            emitter.completeWithError(err);
        } catch (IllegalStateException ignored) {
            // already completed
        }
    }
}
