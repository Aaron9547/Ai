package com.aaron.cloud.chat.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import org.junit.jupiter.api.Test;

class ChatAttachmentVisionModelHintsTest {

    @Test
    void visionKindAlwaysCapable() {
        SysLlmModel m = new SysLlmModel();
        m.setModelKind(LlmModelKind.VISION);
        m.setOpenaiModelId("ep-20250101000000-abcde");
        assertTrue(ChatAttachmentVisionModelHints.looksVisionCapable(m));
    }

    @Test
    void languageWithEndpointIdInAlias() {
        SysLlmModel m = new SysLlmModel();
        m.setModelKind(LlmModelKind.LANGUAGE);
        m.setOpenaiModelId("ep-20250101000000-abcde");
        m.setAlias("doubao-vision-pro");
        assertTrue(ChatAttachmentVisionModelHints.looksVisionCapable(m));
    }

    @Test
    void plainLanguageModelNotCapable() {
        SysLlmModel m = new SysLlmModel();
        m.setModelKind(LlmModelKind.LANGUAGE);
        m.setOpenaiModelId("deepseek-chat");
        m.setAlias("deepseek");
        assertFalse(ChatAttachmentVisionModelHints.looksVisionCapable(m));
    }
}
