package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagEmbeddingHttpSupportTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void buildBody_openAiCompatible_stringInput() throws Exception {
        String json =
                RagEmbeddingHttpSupport.buildEmbeddingsRequestBody(
                        om, "text-embedding-3-small", "hello", LlmVectorBackend.VOLCENGINE_ARK);
        JsonNode n = om.readTree(json);
        assertEquals("text-embedding-3-small", n.path("model").asText());
        assertEquals("hello", n.path("input").asText());
    }

    @Test
    void buildBody_multimodal_textBlock() throws Exception {
        String json =
                RagEmbeddingHttpSupport.buildEmbeddingsRequestBody(
                        om, "doubao-embedding-vision-251215", "天很蓝", LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL);
        JsonNode n = om.readTree(json);
        assertEquals("doubao-embedding-vision-251215", n.path("model").asText());
        assertTrue(n.path("input").isArray());
        assertEquals(1, n.path("input").size());
        assertEquals("text", n.path("input").path(0).path("type").asText());
        assertEquals("天很蓝", n.path("input").path(0).path("text").asText());
    }

    @Test
    void parse_openAiListShape() throws Exception {
        String raw =
                "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.25,-0.5],\"index\":0}]}";
        List<Float> v = RagEmbeddingHttpSupport.parseEmbeddingsResponse(om.readTree(raw), LlmVectorBackend.VOLCENGINE_ARK);
        assertEquals(2, v.size());
        assertEquals(0.25f, v.get(0), 1e-6f);
        assertEquals(-0.5f, v.get(1), 1e-6f);
    }

    @Test
    void parse_multimodalObjectShape() throws Exception {
        String raw = "{\"data\":{\"embedding\":[0.1,0.2,0.3]},\"object\":\"list\"}";
        List<Float> v =
                RagEmbeddingHttpSupport.parseEmbeddingsResponse(
                        om.readTree(raw), LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL);
        assertEquals(3, v.size());
    }

    @Test
    void parse_missingEmbedding_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        RagEmbeddingHttpSupport.parseEmbeddingsResponse(
                                om.createObjectNode(), LlmVectorBackend.VOLCENGINE_ARK));
    }
}
