package com.aaron.cloud.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatReasoningStreamGuardTest {

    @Test
    void continuesForNormalGrowth() {
        ChatReasoningStreamGuard guard = new ChatReasoningStreamGuard(10_000);
        assertEquals(ChatReasoningStreamGuard.Verdict.CONTINUE, guard.appendDelta("分析用户请求："));
        assertEquals(ChatReasoningStreamGuard.Verdict.CONTINUE, guard.appendDelta("价格区间约 700 元。"));
    }

    @Test
    void stopsWhenExceedingMaxLength() {
        ChatReasoningStreamGuard guard = new ChatReasoningStreamGuard(200);
        assertEquals(ChatReasoningStreamGuard.Verdict.CONTINUE, guard.appendDelta("a".repeat(150)));
        assertEquals(ChatReasoningStreamGuard.Verdict.STOP_LENGTH, guard.appendDelta("b".repeat(80)));
    }

    @Test
    void stopsWhenSameBlockRepeats() {
        ChatReasoningStreamGuard guard = new ChatReasoningStreamGuard(50_000);
        String block = "冲突解决： 我必须报告搜索结果，因为它们是外部现实核查。".repeat(3);
        guard.appendDelta("x".repeat(800));
        assertEquals(ChatReasoningStreamGuard.Verdict.CONTINUE, guard.appendDelta(block));
        assertEquals(ChatReasoningStreamGuard.Verdict.CONTINUE, guard.appendDelta(block));
        assertEquals(ChatReasoningStreamGuard.Verdict.STOP_REPETITION, guard.appendDelta(block));
    }
}
