package com.aaron.cloud.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatSseSendGateTest {

    @Test
    void trySend_skipsWhenGateClosed() {
        SseEmitter emitter = new SseEmitter(60_000L);
        ChatSseSendGate gate = new ChatSseSendGate(emitter);
        gate.markClosed();
        assertFalse(gate.trySend(SseEmitter.event().data("x"), null));
        assertFalse(gate.isOpen());
    }
}
