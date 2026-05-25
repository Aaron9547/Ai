package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagLocalEmbeddingFeignSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesLyWebResultFirstRow() throws Exception {
        String json = "{\"code\":0,\"data\":[[0.25,0.5,0.75]],\"msg\":\"ok\"}";
        List<Float> v = RagLocalEmbeddingFeignSupport.parseFirstEmbeddingVector(json, objectMapper);
        assertEquals(3, v.size());
        assertEquals(0.25f, v.get(0), 1e-5f);
        assertEquals(0.5f, v.get(1), 1e-5f);
        assertEquals(0.75f, v.get(2), 1e-5f);
    }
}
