package com.aaron.cloud.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagWebCrawlExtractConfigSupport {

    private final ObjectMapper objectMapper;

    public String toJson(RagWebCrawlExtractConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid extract config", e);
        }
    }

    public RagWebCrawlExtractConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return RagWebCrawlExtractConfig.empty();
        }
        try {
            RagWebCrawlExtractConfig c = objectMapper.readValue(json, RagWebCrawlExtractConfig.class);
            return c != null ? c : RagWebCrawlExtractConfig.empty();
        } catch (Exception e) {
            return RagWebCrawlExtractConfig.empty();
        }
    }
}
