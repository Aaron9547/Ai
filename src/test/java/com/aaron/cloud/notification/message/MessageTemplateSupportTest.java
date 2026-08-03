package com.aaron.cloud.notification.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageTemplateSupportTest {

    @Test
    void applyTemplate_unescapesLiteralNewlinesInTemplateAndVars() {
        String template = "Hi {user}\\n\\n{list}";
        String out =
                MessageTemplateSupport.applyTemplate(
                        template, Map.of("user", "A", "list", "· one\\n· two"));
        assertTrue(out.contains("Hi A\n\n"));
        assertTrue(out.contains("· one\n· two"));
        assertTrue(!out.contains("\\n"));
    }

    @Test
    void plainTextToSimpleHtml_wrapsLinesWithBr() {
        String html = MessageTemplateSupport.plainTextToSimpleHtml("a\nb");
        assertTrue(html.contains("a<br/>"));
        assertTrue(html.contains("b<br/>"));
    }
}
