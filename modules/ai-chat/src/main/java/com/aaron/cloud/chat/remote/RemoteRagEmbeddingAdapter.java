package com.aaron.cloud.chat.remote;

import com.aaron.cloud.common.api.dto.rag.RagEmbedRequest;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.chat.remote.RemoteRagEmbeddingClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class RemoteRagEmbeddingAdapter implements RagEmbeddingPort {

    private final RemoteRagEmbeddingClient remoteRagEmbeddingClient;

    @Override
    public float[] embed(long tenantId, long kbId, String text) {
        try {
            return remoteRagEmbeddingClient.embed(
                    RagEmbedRequest.builder()
                            .tenantId(tenantId)
                            .kbId(kbId)
                            .text(text)
                            .byVectorModelIdOrHash(false)
                            .build());
        } catch (FeignException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "RAG 嵌入服务不可用", e);
        }
    }

    @Override
    public int dimensions(long tenantId) {
        try {
            return remoteRagEmbeddingClient.dimensions(tenantId);
        } catch (FeignException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "RAG 嵌入服务不可用", e);
        }
    }

    @Override
    public float[] embedByVectorModelIdOrHash(long tenantId, Long vectorModelIdOrNull, String text) {
        try {
            return remoteRagEmbeddingClient.embed(
                    RagEmbedRequest.builder()
                            .tenantId(tenantId)
                            .vectorModelIdOrNull(vectorModelIdOrNull)
                            .text(text)
                            .byVectorModelIdOrHash(true)
                            .build());
        } catch (FeignException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "RAG 嵌入服务不可用", e);
        }
    }
}
