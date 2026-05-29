package com.aaron.cloud.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChatTemporalContextPromptTest {

    @Test
    void webGroundingPreamble_mentionsTodayAndRealSearchResults() {
        LocalDate today = ChatTemporalContextPrompt.today();
        String zh = ChatTemporalContextPrompt.webGroundingPreamble("zh-CN");
        assertTrue(zh.contains("时间上下文"));
        assertTrue(zh.contains(today.toString()));
        assertTrue(zh.contains("真实检索结果"));
    }

    @Test
    void appendToSystemPrompt_includesTemporalBlock() {
        StringBuilder sb = new StringBuilder("你是助手。");
        ChatTemporalContextPrompt.appendToSystemPrompt(sb, "zh-CN");
        assertTrue(sb.toString().contains("时间上下文"));
        assertTrue(sb.toString().contains(ChatTemporalContextPrompt.today().toString()));
    }
}
