package com.aaron.cloud.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiChatStreamClientUrlTest {

    @Test
    void hostOnlyAppendsChatCompletionsWithoutInjectingV1() {
        assertEquals(
                "https://api.openai.com/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl("https://api.openai.com"));
        assertEquals(
                "https://api.openai.com/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl("https://api.openai.com/"));
    }

    @Test
    void volcArkApiV3RootAppendsChatCompletionsOnly() {
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl("https://ark.cn-beijing.volces.com/api/v3"));
    }

    @Test
    void baseAlreadyEndsWithV1AppendsChatCompletionsOnly() {
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/"));
    }

    @Test
    void fullPathUnchanged() {
        assertEquals(
                "https://x.example/v1/chat/completions",
                OpenAiChatStreamClient.resolveChatCompletionsUrl("https://x.example/v1/chat/completions"));
    }

    @Test
    void summarizeChatJsonBodyListsModelAndRolesWithoutContent() throws Exception {
        var om = new ObjectMapper();
        String json =
                "{\"model\":\"qwen-turbo\",\"stream\":true,\"messages\":[{\"role\":\"system\",\"content\":\"secret\"},{\"role\":\"user\",\"content\":\"pii\"}]}";
        String s = OpenAiChatStreamClient.summarizeChatJsonBody(om, json);
        assertTrue(s.contains("qwen-turbo"), s);
        assertTrue(s.contains("msgCount=2"), s);
        assertTrue(s.contains("roles=[system,user]"), s);
        assertFalse(s.contains("secret"), s);
        assertFalse(s.contains("pii"), s);
    }
}
