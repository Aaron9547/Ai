package com.aaron.cloud.chat.remote;

import com.aaron.cloud.common.api.dto.rag.RagQueryCitationHitsRequest;
import com.aaron.cloud.common.api.dto.rag.RagQuerySnippetsRequest;
import com.aaron.cloud.common.api.dto.RagCitationHit;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-rag",
        contextId = "remoteRagQueryClient",
        path = "/internal/v1/rag/query",
        url = "${ai.remoting.rag-base-url:http://127.0.0.1:8080}")
public interface RemoteRagQueryClient {

    @PostMapping("/snippets")
    List<String> searchSnippets(@RequestBody RagQuerySnippetsRequest request);

    @PostMapping("/citation-hits")
    List<RagCitationHit> searchCitationHits(@RequestBody RagQueryCitationHitsRequest request);
}
