package com.aaron.cloud.rag.remote;

import com.aaron.cloud.rag.remote.dto.LocalEmbeddingRpcRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 对齐 ly-ai-rag-svc {@code PrivateModelController}：{@code POST .../{tenantCode}/privateModel/embedding}；路径变量为租户
 * {@code sys_tenant.code}。
 *
 * <p>解析方式：{@code ai.rag.local-embed-feign.base-url} 非空时<strong>直连</strong>（环境变量 {@code AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL} 或
 * {@code AI_RAG_ENGINE_BASE_URL}，与 ly-ai-rag {@code aiengine.domain} 根路径一致）；否则以 {@code name}（即
 * {@code ai.rag.local-embed-feign.service-id}）经 <strong>LoadBalancer + Eureka</strong>（须 {@code ai.discovery.enabled=true}）。
 */
@FeignClient(
        name = "${ai.rag.local-embed-feign.service-id:ly-ai-rag-svc}",
        contextId = "ragLocalEmbedding",
        url = "${ai.rag.local-embed-feign.base-url:}")
public interface RagLocalEmbeddingFeignClient {

    @PostMapping(value = "/{tenantCode}/privateModel/embedding", consumes = MediaType.APPLICATION_JSON_VALUE)
    String callPrivateEmbedding(
            @PathVariable("tenantCode") String tenantCode,
            @RequestBody LocalEmbeddingRpcRequest body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization);
}
