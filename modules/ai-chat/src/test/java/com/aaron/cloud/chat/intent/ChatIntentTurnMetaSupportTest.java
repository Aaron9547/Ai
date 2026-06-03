package com.aaron.cloud.chat.intent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatIntentTurnMetaSupportTest {

    @Test
    void detectsIntentHandled() {
        assertTrue(ChatIntentTurnMetaSupport.isIntentTurnMeta("{\"intentHandled\":true}"));
    }

    @Test
    void detectsIntentRouted() {
        assertTrue(ChatIntentTurnMetaSupport.isIntentTurnMeta("{\"intentRouted\":true}"));
    }

    @Test
    void ignoresNormalAssistantMeta() {
        assertFalse(ChatIntentTurnMetaSupport.isIntentTurnMeta("{\"modelAlias\":\"gpt\"}"));
    }
}
