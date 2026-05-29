package com.aaron.cloud.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE 发送门闩：客户端断开或 {@link SseEmitter#complete()} 后跳过后续 send，避免刷屏 WARN。 */
@Slf4j
public final class ChatSseSendGate {

    private final SseEmitter emitter;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean cancelUpstream;

    public ChatSseSendGate(SseEmitter emitter) {
        this(emitter, null);
    }

    public ChatSseSendGate(SseEmitter emitter, AtomicBoolean cancelUpstream) {
        this.emitter = emitter;
        this.cancelUpstream = cancelUpstream;
        Runnable close = this::markClosed;
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(e -> markClosed());
    }

    public boolean isOpen() {
        return open.get();
    }

    public void markClosed() {
        open.set(false);
        if (cancelUpstream != null) {
            cancelUpstream.set(true);
        }
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
            markClosed();
            return false;
        } catch (Exception e) {
            markClosed();
            if (logContext != null && !logContext.isBlank()) {
                log.warn("[对话] SSE 推送{}失败", logContext, e);
            }
            return false;
        }
    }

    public void complete() {
        markClosed();
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // already completed (client disconnect)
        }
    }

    public void completeWithError(Throwable err) {
        markClosed();
        try {
            emitter.completeWithError(err);
        } catch (IllegalStateException ignored) {
            // already completed
        }
    }
}
