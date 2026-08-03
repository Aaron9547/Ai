package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolcArkBotReferenceParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mergeReferences_readsRootReferencesAndMobileUrl() throws Exception {
        String json =
                """
                {
                  "references": [
                    {
                      "title": "DDR5 报价",
                      "mobile_url": "https://m.example.com/ddr5",
                      "summary": "32GB 套条约 700 元",
                      "site_name": "示例站"
                    }
                  ]
                }
                """;
        List<WebSearchReference> refs =
                VolcArkBotReferenceParser.mergeReferences(objectMapper, objectMapper.readTree(json));
        assertEquals(1, refs.size());
        assertEquals("https://m.example.com/ddr5", refs.get(0).url());
        assertTrue(refs.get(0).snippet().contains("700"));
    }

    @Test
    void mergeReferences_parsesBotUsageOutputString() throws Exception {
        String json =
                """
                {
                  "bot_usage": {
                    "action_details": [
                      {
                        "tool_details": [
                          {
                            "output": "{\\"data\\":{\\"results\\":[{\\"title\\":\\"B850 兼容\\",\\"url\\":\\"https://example.com/b850\\",\\"summary\\":\\"AM5 平台\\"}]}}"
                          }
                        ]
                      }
                    ]
                  }
                }
                """;
        List<WebSearchReference> refs =
                VolcArkBotReferenceParser.mergeReferences(objectMapper, objectMapper.readTree(json));
        assertEquals(1, refs.size());
        assertEquals("https://example.com/b850", refs.get(0).url());
    }

    @Test
    void extractAssistantText_readsArrayContentParts() throws Exception {
        String json =
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": [
                          {"type":"text","text":"联网摘要第一段"},
                          {"type":"text","text":"联网摘要第二段"}
                        ]
                      }
                    }
                  ]
                }
                """;
        String text =
                VolcArkBotReferenceParser.extractAssistantText(objectMapper.readTree(json));
        assertTrue(text.contains("第一段"));
        assertTrue(text.contains("第二段"));
    }
}
