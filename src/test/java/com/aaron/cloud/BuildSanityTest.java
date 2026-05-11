package com.aaron.cloud;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BuildSanityTest {

    @Test
    void buildOk() {
        assertNotNull(LocalDateTime.now());
    }
}
