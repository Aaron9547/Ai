package com.aaron.cloud.model.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReasoningDeltaNormalizerTest {

    @Test
    void passesThroughTrueDelta() {
        ReasoningDeltaNormalizer n = new ReasoningDeltaNormalizer();
        assertEquals("ab", n.toDelta("ab"));
        assertEquals("c", n.toDelta("c"));
    }

    @Test
    void stripsCumulativePrefix() {
        ReasoningDeltaNormalizer n = new ReasoningDeltaNormalizer();
        assertEquals("Hello", n.toDelta("Hello"));
        assertEquals(" world", n.toDelta("Hello world"));
        assertEquals("!", n.toDelta("Hello world!"));
    }
}
