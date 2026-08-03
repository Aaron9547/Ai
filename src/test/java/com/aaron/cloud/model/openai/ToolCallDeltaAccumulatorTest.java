package com.aaron.cloud.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolCallDeltaAccumulatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void accumulatesToolCallFragments() throws Exception {
        var acc = new ToolCallDeltaAccumulator();
        acc.accept(objectMapper.readTree(
                """
                [{"index":0,"id":"call_1","type":"function","function":{"name":"jina::search_web","arguments":"{\\"q"}}]
                """));
        acc.accept(objectMapper.readTree(
                """
                [{"index":0,"function":{"arguments":"uery\\":\\"x\\"}"}}]
                """));
        var built = acc.build();
        assertEquals(1, built.size());
        assertEquals("call_1", built.get(0).getId());
        assertEquals("jina::search_web", built.get(0).getFunction().getName());
        assertEquals("{\"query\":\"x\"}", built.get(0).getFunction().getArguments());
    }
}
