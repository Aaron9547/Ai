package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.enums.LlmModelStatus;
import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.outbound.LlmOutboundException;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.outbound.OutboundKind;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.aaron.cloud.common.outbound.OutboundTenantUpstreamQuarantine;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.rag.remote.RagLocalEmbeddingFeignClient;
import com.aaron.cloud.rag.remote.dto.LocalEmbeddingRpcRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * RAG 向量化：按知识库绑定的 {@link LlmModelKind#VECTOR} 与 {@link LlmVectorBackend} 解析 {@code POST} URL（见
 * {@link VectorEmbeddingsUrl}）；请求体与响应解析见 {@link RagEmbeddingHttpSupport}（OpenAI 兼容 / 方舟多模态等）。若模型
 * {@code local_deploy=true}，则经 {@link RagLocalEmbeddingFeignClient} 调对端嵌入接口（路径变量为
 * {@code sys_tenant.code}）。内网免鉴权可不配 Key，此时不发送 {@code Authorization}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEmbeddingService implements RagEmbeddingPort {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private final AiProvidersProperties aiProvidersProperties;
    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final OutboundCircuitBreakerSupport outboundCircuitBreakerSupport;
    private final OutboundMetricsSupport outboundMetricsSupport;
    private final OutboundTenantUpstreamQuarantine outboundTenantUpstreamQuarantine;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final SysLlmModelRepository sysLlmModelRepository;
    private final SysTenantRepository sysTenantRepository;
    private final ObjectProvider<RagLocalEmbeddingFeignClient> ragLocalEmbeddingFeignClient;
    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    public int dimensions() {
        int d = aiProvidersProperties.getMilvus().getVectorDimension();
        return d > 0 ? d : RagQueryEmbeddingHasher.DEFAULT_DIM;
    }

    @Override
    public float[] embed(long tenantId, long kbId, String text) {
        String t = text == null ? "" : text;
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        if (kb == null) {
            throw new IllegalStateException("知识库不存在，无法解析嵌入模型");
        }
        Long mid = kb.getAssignedEmbeddingModelId();
        if (mid == null) {
            throw new IllegalStateException("知识库未绑定向量模型（llm_model VECTOR），无法计算嵌入向量");
        }
        SysLlmModel m =
                sysLlmModelRepository
                        .findById(tenantId, mid)
                        .orElseThrow(() -> new IllegalStateException("知识库绑定的向量模型不存在"));
        LlmModelKind k = m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.VECTOR) {
            throw new IllegalStateException("知识库嵌入模型类型须为 VECTOR");
        }
        if (m.getStatus() != LlmModelStatus.ACTIVE) {
            throw new IllegalStateException("知识库绑定的向量模型未启用");
        }
        String apiKey = "";
        if (m.getApiKeyCipher() != null && !m.getApiKeyCipher().isBlank()) {
            try {
                apiKey = aesSecretCipher.decryptFromBase64(m.getApiKeyCipher());
            } catch (Exception e) {
                log.error("解密向量模型 API Key 失败 llmModelId={}", m.getId(), e);
                throw new IllegalStateException("向量模型 API Key 解密失败");
            }
        }
        try {
            outboundTenantUpstreamQuarantine.assertStreamAllowed(tenantId);
            float[] out;
            if (Boolean.TRUE.equals(m.getLocalDeploy())) {
                out = embedViaLocalDeployFeign(tenantId, m, t, apiKey);
            } else {
                LlmVectorBackend vb = m.resolveVectorBackend();
                out =
                        callEmbeddingsUpstream(
                                tenantId,
                                m.getId(),
                                m.getOpenaiBaseUrl().trim(),
                                apiKey,
                                m.getOpenaiModelId().trim(),
                                t,
                                vb);
            }
            outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
            return out;
        } catch (LlmOutboundException e) {
            throw toResponseStatus(e);
        } catch (ResponseStatusException e) {
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tenantId, e);
            throw e;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("向量模型嵌入调用失败 tenantId={} kbId={} llmModelId={}", tenantId, kbId, mid, e);
            throw new IllegalStateException("向量模型嵌入调用失败: " + e.getMessage(), e);
        }
    }

    private static ResponseStatusException toResponseStatus(LlmOutboundException e) {
        Integer st = e.getHttpStatus();
        org.springframework.http.HttpStatus hs =
                st != null ? org.springframework.http.HttpStatus.resolve(st) : null;
        if (hs == null) {
            hs = org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
        }
        return new ResponseStatusException(
                hs, e.getMessage() != null ? e.getMessage() : "模型上游暂不可用", e);
    }

    private float[] embedViaLocalDeployFeign(long tenantId, SysLlmModel m, String text, String apiKey)
            throws Exception {
        RagLocalEmbeddingFeignClient client = ragLocalEmbeddingFeignClient.getIfAvailable();
        if (client == null) {
            boolean discoveryOn =
                    Boolean.parseBoolean(environment.getProperty("ai.discovery.enabled", "false"));
            String baseConfigured = environment.getProperty("ai.rag.local-embed-feign.base-url", "");
            throw new IllegalStateException(
                    discoveryOn
                            ? ("模型已标记本地部署向量化，但未注册本地嵌入 Feign：请确认 Eureka 上已注册 "
                                    + environment.getProperty(
                                            "ai.rag.local-embed-feign.service-id", "rag-embedding-svc")
                                    + "，或改为配置直连环境变量 AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL（与对端网关根地址一致，如 AI_RAG_ENGINE_BASE_URL）后重启。")
                            : ("模型已标记本地部署向量化，但未注册本地嵌入 Feign：已关闭服务发现（ai.discovery.enabled=false），"
                                    + "须配置直连 RAG 网关根地址：环境变量 AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL 或 AI_RAG_ENGINE_BASE_URL"
                                    + (baseConfigured.isBlank()
                                            ? "（当前为空；请设为对端网关根 URL，含尾斜杠，见 application.yml 示例）"
                                            : "")
                                    + "，对应 ai.rag.local-embed-feign.base-url。若改用 Eureka，请设置 AI_DISCOVERY_ENABLED=true 并保证 RAG 服务已注册。"));
        }
        String tenantCode =
                sysTenantRepository
                        .findById(tenantId)
                        .map(t -> t.getCode() == null ? "" : t.getCode().trim())
                        .filter(StringUtils::hasText)
                        .orElseThrow(() -> new IllegalStateException("租户缺少 code，无法调用本地嵌入 Feign"));
        String modelId = m.getOpenaiModelId() == null ? "" : m.getOpenaiModelId().trim();
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalStateException("向量模型 Model ID 为空，无法经本地部署 Feign 向量化");
        }
        LocalEmbeddingRpcRequest req = new LocalEmbeddingRpcRequest(modelId, List.of(text));
        String auth = StringUtils.hasText(apiKey) ? "Bearer " + apiKey : null;
        String raw;
        Optional<CircuitBreaker> cb =
                outboundResilienceRuntime.effective(tenantId).isEnabled()
                        ? outboundCircuitBreakerSupport.forEmbeddingFeign(tenantId, m.getId())
                        : Optional.empty();
        long t0 = System.nanoTime();
        if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
            outboundMetricsSupport.recordError(OutboundKind.LLM_EMBEDDING_FEIGN, "CIRCUIT_OPEN", 503);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "本地嵌入上游繁忙，请稍后重试");
        }
        try {
            raw = client.callPrivateEmbedding(tenantCode, req, auth);
            cb.ifPresent(c -> c.onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS));
        } catch (FeignException e) {
            cb.ifPresent(c -> c.onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e));
            outboundMetricsSupport.recordError(
                    OutboundKind.LLM_EMBEDDING_FEIGN, "HTTP_" + e.status(), e.status());
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tenantId, e);
            log.warn(
                    "本地嵌入 Feign 调用失败 status={} tenantCode={} llmModelId={}",
                    e.status(),
                    tenantCode,
                    m.getId(),
                    e);
            if (e.status() >= 500) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "本地嵌入网关异常（HTTP " + e.status() + "），请稍后重试或联系管理员。");
            }
            String base =
                    "本地嵌入网关返回错误，请核对直连 base-url 或 Eureka service-id、租户 code 与向量模型配置。";
            String hint = e.contentUTF8() == null ? "" : e.contentUTF8();
            hint = hint.length() > 320 ? hint.substring(0, 320) + "…" : hint;
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, hint.isBlank() ? base : base + " 详情：" + hint.replace('\n', ' '));
        }
        List<Float> floats = RagLocalEmbeddingFeignSupport.parseFirstEmbeddingVector(raw, objectMapper);
        return floatsToVector(floats);
    }

    private float[] floatsToVector(List<Float> floats) {
        int dim = dimensions();
        if (floats.size() != dim) {
            log.error(
                    "embedding 维数 {} 与 ai.providers.milvus.vector-dimension={} 不一致",
                    floats.size(),
                    dim);
            throw new IllegalStateException(
                    "嵌入向量维数 "
                            + floats.size()
                            + " 与 Milvus 配置 "
                            + dim
                            + " 不一致。请将 application.yml 的 ai.providers.milvus.vector-dimension"
                            + "（或环境变量 AI_MILVUS_VECTOR_DIM）改为 "
                            + floats.size()
                            + " 后重启并全库重建索引；若模型支持 dimensions 参数（如百炼 text-embedding-v4），"
                            + "请确认集成策略为百炼 OpenAI 兼容或百炼原生文本并已重启服务。");
        }
        float[] out = new float[dim];
        for (int i = 0; i < dim; i++) {
            out[i] = floats.get(i);
        }
        return out;
    }

    private float[] callEmbeddingsUpstream(
            long tenantId,
            long llmModelId,
            String openaiBaseUrl,
            String apiKey,
            String modelId,
            String text,
            LlmVectorBackend vectorBackend)
            throws Exception {
        String url = VectorEmbeddingsUrl.resolve(openaiBaseUrl, vectorBackend);
        if (url.isBlank()) {
            throw new IllegalStateException("向量模型 Base URL 为空");
        }
        int targetDim = dimensions();
        String body =
                RagEmbeddingHttpSupport.buildEmbeddingsRequestBody(
                        objectMapper, modelId, text, vectorBackend, targetDim);
        HttpRequest.Builder rb =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest req = rb.build();
        HttpResponse<String> resp;
        if (outboundResilienceRuntime.effective(tenantId).isEnabled()) {
            var cb = outboundCircuitBreakerSupport.forEmbeddingHttp(tenantId, llmModelId);
            long t0 = System.nanoTime();
            if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
                outboundMetricsSupport.recordError(OutboundKind.LLM_EMBEDDING, "CIRCUIT_OPEN", 503);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "嵌入上游繁忙，请稍后重试");
            }
            try {
                resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                cb.ifPresent(c -> c.onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS));
            } catch (Exception e) {
                cb.ifPresent(c -> c.onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e));
                if (!(e instanceof IllegalArgumentException)) {
                    outboundMetricsSupport.recordError(
                            OutboundKind.LLM_EMBEDDING, e.getClass().getSimpleName(), null);
                }
                throw e;
            }
        } else {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String bodyStr = resp.body() == null ? "" : resp.body();
            String prefix = bodyStr.substring(0, Math.min(400, bodyStr.length()));
            log.warn(
                    "embeddings upstream HTTP {} url={} modelId={} vectorBackend={} bodyPrefix={}",
                    resp.statusCode(),
                    url,
                    modelId,
                    vectorBackend,
                    prefix);
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(
                    tenantId,
                    LlmOutboundException.upstreamHttp(
                            OutboundKind.LLM_EMBEDDING,
                            resp.statusCode(),
                            "embeddings HTTP " + resp.statusCode() + " url=" + url,
                            prefix,
                            "",
                            null));
            String vendor = extractEmbeddingsProviderErrorHint(bodyStr);
            if (resp.statusCode() >= 500) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "嵌入服务返回异常（HTTP " + resp.statusCode() + "），请稍后重试或联系管理员。");
            }
            String base = "向量化失败：请核对模型配置中的访问地址、模型标识与密钥。";
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, vendor.isBlank() ? base : base + " 详情：" + vendor);
        }
        JsonNode root = objectMapper.readTree(resp.body());
        List<Float> floats = RagEmbeddingHttpSupport.parseEmbeddingsResponse(root, vectorBackend);
        return floatsToVector(floats);
    }

    /**
     * 从 JSON 错误体提取简短说明（OpenAI 嵌套 {@code error}、或 Spring 式 {@code error} 字符串 + {@code path}）。
     */
    private String extractEmbeddingsProviderErrorHint(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode err = root.path("error");
            if (err.isObject()) {
                String code = err.path("code").asText("").trim();
                String msg = err.path("message").asText("").trim();
                StringBuilder sb = new StringBuilder();
                if (!code.isEmpty()) {
                    sb.append(code);
                }
                if (!msg.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(" — ");
                    }
                    sb.append(msg);
                }
                String s = sb.toString().replace('\r', ' ').replace('\n', ' ');
                return s.length() > 320 ? s.substring(0, 320) + "…" : s;
            }
            if (err.isTextual()) {
                String ev = err.asText("").trim();
                String msg = root.path("message").asText("").trim();
                String path = root.path("path").asText("").trim();
                StringBuilder sb = new StringBuilder();
                if (!ev.isEmpty()) {
                    sb.append(ev);
                }
                if (!msg.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(" — ");
                    }
                    sb.append(msg);
                }
                if (!path.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append("path=").append(path);
                }
                String s = sb.toString().replace('\r', ' ').replace('\n', ' ');
                return s.length() > 320 ? s.substring(0, 320) + "…" : s;
            }
            return "";
        } catch (Exception e) {
            log.warn("解析嵌入错误响应 JSON 失败 bodyLen={}", body.length(), e);
            return "";
        }
    }

    /**
     * 记忆层等场景：按租户下指定 {@link LlmModelKind#VECTOR} 模型主键嵌入；模型不可用或调用失败时退化为
     * {@link RagQueryEmbeddingHasher} 占位向量，避免阻塞主流程。
     */
    public float[] embedByVectorModelIdOrHash(long tenantId, Long vectorModelIdOrNull, String text) {
        String t = text == null ? "" : text;
        if (vectorModelIdOrNull == null) {
            return RagQueryEmbeddingHasher.hashToVector(t, dimensions());
        }
        SysLlmModel m = sysLlmModelRepository.findById(tenantId, vectorModelIdOrNull).orElse(null);
        if (m == null) {
            log.debug("memory embed fallback hash: model missing tenantId={} id={}", tenantId, vectorModelIdOrNull);
            return RagQueryEmbeddingHasher.hashToVector(t, dimensions());
        }
        LlmModelKind k = m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.VECTOR || m.getStatus() != LlmModelStatus.ACTIVE) {
            return RagQueryEmbeddingHasher.hashToVector(t, dimensions());
        }
        String apiKey = "";
        if (m.getApiKeyCipher() != null && !m.getApiKeyCipher().isBlank()) {
            try {
                apiKey = aesSecretCipher.decryptFromBase64(m.getApiKeyCipher());
            } catch (Exception e) {
                log.warn("memory embed fallback hash: decrypt key failed llmModelId={}", m.getId(), e);
                return RagQueryEmbeddingHasher.hashToVector(t, dimensions());
            }
        }
        try {
            outboundTenantUpstreamQuarantine.assertStreamAllowed(tenantId);
            float[] out;
            if (Boolean.TRUE.equals(m.getLocalDeploy())) {
                out = embedViaLocalDeployFeign(tenantId, m, t, apiKey);
            } else {
                LlmVectorBackend vb = m.resolveVectorBackend();
                out =
                        callEmbeddingsUpstream(
                                tenantId,
                                m.getId(),
                                m.getOpenaiBaseUrl().trim(),
                                apiKey,
                                m.getOpenaiModelId().trim(),
                                t,
                                vb);
            }
            outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
            return out;
        } catch (LlmOutboundException e) {
            throw toResponseStatus(e);
        } catch (Exception e) {
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tenantId, e);
            log.warn("memory embed fallback hash tenantId={} llmModelId={}", tenantId, m.getId(), e);
            return RagQueryEmbeddingHasher.hashToVector(t, dimensions());
        }
    }
}
