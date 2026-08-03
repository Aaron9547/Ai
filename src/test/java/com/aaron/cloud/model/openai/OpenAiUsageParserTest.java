package com.aaron.cloud.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiUsageParserTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parseSnakeCase() throws Exception {
        var n = om.readTree("{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}");
        ModelTokenUsage u = OpenAiUsageParser.parse(n);
        assertThat(u.promptTokens()).isEqualTo(1);
        assertThat(u.completionTokens()).isEqualTo(2);
        assertThat(u.totalTokens()).isEqualTo(3);
    }

    @Test
    void parseCamelCase() throws Exception {
        var n = om.readTree("{\"promptTokens\":4,\"completionTokens\":5,\"totalTokens\":9}");
        ModelTokenUsage u = OpenAiUsageParser.parse(n);
        assertThat(u.promptTokens()).isEqualTo(4);
        assertThat(u.completionTokens()).isEqualTo(5);
        assertThat(u.totalTokens()).isEqualTo(9);
    }
}
