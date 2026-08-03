package com.aaron.cloud.chat.remote;

import com.aaron.cloud.common.api.dto.rag.RagEmbedRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "ai-rag",
        contextId = "remoteRagEmbeddingClient",
        path = "/internal/v1/rag/embed",
        url = "${ai.remoting.rag-base-url:http://127.0.0.1:8080}")
public interface RemoteRagEmbeddingClient {

    @PostMapping
    float[] embed(@RequestBody RagEmbedRequest request);

    @GetMapping("/dimensions")
    int dimensions(@RequestParam("tenantId") long tenantId);
}
