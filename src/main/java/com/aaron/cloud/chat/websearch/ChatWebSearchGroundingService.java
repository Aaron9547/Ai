package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchConversationReuseService;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingCacheLookup;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingCacheService;
import com.aaron.cloud.common.tenant.runtime.WebSearchCacheTier;
import com.aaron.cloud.common.tenant.runtime.WebSearchGroundingCachePolicy;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingMergeSupport;
import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.LlmWebSearchProvider;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.model.metering.LlmModelUsageRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 对话编排：解析租户默认 {@link com.aaron.cloud.common.api.enums.LlmModelKind#WEB_SEARCH} 行并调用可插拔 Provider；可选解析 usage 并计量。
 * 支持 Redis 缓存（精确 + 语义近邻，滚动 6h/24h/48h）与会话内问句复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWebSearchGroundingService {

    private final AesSecretCipher aesSecretCipher;
    private final WebSearchProviderRegistry registry;
    private final ObjectMapper objectMapper;
    private final LlmModelUsageRecorder llmModelUsageRecorder;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final WebSearchGroundingCacheService webSearchGroundingCacheService;
    private final WebSearchConversationReuseService webSearchConversationReuseService;

    /**
     * 连续多轮调用联网 API（轮数与各轮后缀见租户运行参数 {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT}
     * 与 {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON}）。
     * 引用按轮次顺序追加；每新增一条引用即回调（便于 SSE 渐进下发）。
     *
     * @param onCumulativeReferences 可为 null；每次入参为当前已累积的引用列表副本（只增不减）。
     */
    public WebGroundingBundle groundMultiRoundsWithRaw(
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        WebSearchExecutionContext ctx = resolveExecutionContext(snap, webSearchModel);
        String base = userQueryPlaintext == null ? "" : userQueryPlaintext;
        String normalized = WebSearchQueryNormalizer.normalize(base);
        var multi =
                tenantRuntimeSettingApplicationService.webSearchGroundingMultiRoundConfig(snap.getTenantId());
        List<String> roundSuffixes = multi.suffixes();
        int configuredRounds = multi.rounds();
        WebSearchGroundingCachePolicy cachePolicy = webSearchGroundingCacheService.policy(snap.getTenantId());
        String configScope =
                webSearchGroundingCacheService.configScopeHash(configuredRounds, roundSuffixes);
        long modelId = webSearchModel.getId() == null ? 0L : webSearchModel.getId();

        Optional<WebGroundingBundle> conversationReuse =
                webSearchConversationReuseService.tryReuse(
                        snap.getTenantId(), conversationId, normalized, cachePolicy);
        if (conversationReuse.isPresent()) {
            WebGroundingBundle b = conversationReuse.get();
            emitCumulative(onCumulativeReferences, b.references());
            webSearchGroundingCacheService.store(
                    snap.getTenantId(), modelId, configScope, normalized, b, cachePolicy);
            return b;
        }

        Optional<WebSearchGroundingCacheLookup> cached =
                webSearchGroundingCacheService.lookup(
                        snap.getTenantId(), modelId, configScope, normalized, cachePolicy);
        int effectiveRounds = configuredRounds;
        WebGroundingBundle seed = null;
        WebSearchCacheTier tier = WebSearchCacheTier.MISS;
        if (cached.isPresent() && cached.get().usable()) {
            WebSearchGroundingCacheLookup hit = cached.get();
            tier = hit.tier();
            seed = hit.bundle();
            effectiveRounds = cachePolicy.effectiveRoundsForTier(tier, configuredRounds);
            log.info(
                    "[联网缓存] 命中 {}：租户 {}，会话 {}，配置轮数 {}，实际外呼轮数 {}，语义近邻={}",
                    tier,
                    snap.getTenantId(),
                    conversationId,
                    configuredRounds,
                    effectiveRounds,
                    hit.semanticNearMatch());
        }

        if (effectiveRounds <= 0 && seed != null) {
            emitCumulative(onCumulativeReferences, seed.references());
            return seed;
        }

        WebGroundingBundle live =
                executeRounds(
                        ctx,
                        snap,
                        webSearchModel,
                        base,
                        roundSuffixes,
                        0,
                        effectiveRounds,
                        null,
                        conversationId,
                        onCumulativeReferences);

        WebGroundingBundle merged = seed == null ? live : WebSearchGroundingMergeSupport.merge(seed, live);
        webSearchGroundingCacheService.store(
                snap.getTenantId(), modelId, configScope, normalized, merged, cachePolicy);
        return merged;
    }

    /**
     * 对话流式：首轮（或缓存命中）同步返回以尽快注入主模型；配置多轮时其余轮在虚拟线程中补全并通过 {@code onCumulativeReferences} 渐进下发。
     */
    public WebSearchStreamGroundingSession groundForChatStream(
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        WebSearchExecutionContext ctx = resolveExecutionContext(snap, webSearchModel);
        String base = userQueryPlaintext == null ? "" : userQueryPlaintext;
        String normalized = WebSearchQueryNormalizer.normalize(base);
        var multi =
                tenantRuntimeSettingApplicationService.webSearchGroundingMultiRoundConfig(snap.getTenantId());
        List<String> roundSuffixes = multi.suffixes();
        int configuredRounds = multi.rounds();
        WebSearchGroundingCachePolicy cachePolicy = webSearchGroundingCacheService.policy(snap.getTenantId());
        String configScope =
                webSearchGroundingCacheService.configScopeHash(configuredRounds, roundSuffixes);
        long modelId = webSearchModel.getId() == null ? 0L : webSearchModel.getId();

        Optional<WebGroundingBundle> conversationReuse =
                webSearchConversationReuseService.tryReuse(
                        snap.getTenantId(), conversationId, normalized, cachePolicy);
        if (conversationReuse.isPresent()) {
            WebGroundingBundle b = conversationReuse.get();
            emitCumulative(onCumulativeReferences, b.references());
            webSearchGroundingCacheService.store(
                    snap.getTenantId(), modelId, configScope, normalized, b, cachePolicy);
            return WebSearchStreamGroundingSession.completed(b);
        }

        Optional<WebSearchGroundingCacheLookup> cached =
                webSearchGroundingCacheService.lookup(
                        snap.getTenantId(), modelId, configScope, normalized, cachePolicy);
        int effectiveRounds = configuredRounds;
        WebGroundingBundle seed = null;
        if (cached.isPresent() && cached.get().usable()) {
            WebSearchGroundingCacheLookup hit = cached.get();
            seed = hit.bundle();
            effectiveRounds = cachePolicy.effectiveRoundsForTier(hit.tier(), configuredRounds);
            log.info(
                    "[联网缓存] 命中 {}：租户 {}，会话 {}，配置轮数 {}，实际外呼轮数 {}，语义近邻={}",
                    hit.tier(),
                    snap.getTenantId(),
                    conversationId,
                    configuredRounds,
                    effectiveRounds,
                    hit.semanticNearMatch());
        }

        if (effectiveRounds <= 0 && seed != null) {
            emitCumulative(onCumulativeReferences, seed.references());
            return WebSearchStreamGroundingSession.completed(seed);
        }

        if (effectiveRounds <= 1) {
            WebGroundingBundle live =
                    executeRounds(
                            ctx,
                            snap,
                            webSearchModel,
                            base,
                            roundSuffixes,
                            0,
                            1,
                            null,
                            conversationId,
                            onCumulativeReferences);
            WebGroundingBundle merged =
                    seed == null ? live : WebSearchGroundingMergeSupport.merge(seed, live);
            webSearchGroundingCacheService.store(
                    snap.getTenantId(), modelId, configScope, normalized, merged, cachePolicy);
            return WebSearchStreamGroundingSession.completed(merged);
        }

        WebGroundingBundle afterFirst =
                executeRounds(
                        ctx,
                        snap,
                        webSearchModel,
                        base,
                        roundSuffixes,
                        0,
                        1,
                        null,
                        conversationId,
                        onCumulativeReferences);
        WebGroundingBundle initial =
                seed == null ? afterFirst : WebSearchGroundingMergeSupport.merge(seed, afterFirst);

        final int remainingRounds = effectiveRounds - 1;
        final WebGroundingBundle initialForAsync = initial;
        CompletableFuture<WebGroundingBundle> remainder =
                CompletableFuture.supplyAsync(
                        () -> {
                            WebGroundingBundle rest =
                                    executeRounds(
                                            ctx,
                                            snap,
                                            webSearchModel,
                                            base,
                                            roundSuffixes,
                                            1,
                                            remainingRounds,
                                            initialForAsync,
                                            conversationId,
                                            onCumulativeReferences);
                            webSearchGroundingCacheService.store(
                                    snap.getTenantId(),
                                    modelId,
                                    configScope,
                                    normalized,
                                    rest,
                                    cachePolicy);
                            return rest;
                        },
                        command -> Thread.startVirtualThread(command));

        return new WebSearchStreamGroundingSession(initial, remainder);
    }

    public WebSearchExecutionResult groundWithRaw(
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String userQueryPlaintext,
            long conversationId) {
        WebGroundingBundle b =
                groundMultiRoundsWithRaw(snap, webSearchModel, userQueryPlaintext, conversationId, null);
        return new WebSearchExecutionResult(b, null);
    }

    private WebGroundingBundle executeRounds(
            WebSearchExecutionContext ctx,
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String base,
            List<String> roundSuffixes,
            int startRoundIndex,
            int roundsToRun,
            WebGroundingBundle carryIn,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        List<WebSearchReference> accumulated = new ArrayList<>();
        List<String> summaryOrder = new ArrayList<>();
        if (carryIn != null) {
            String carried = carryIn.summaryText() == null ? "" : carryIn.summaryText().trim();
            if (!carried.isBlank()) {
                summaryOrder.add(carried);
            }
            if (carryIn.references() != null) {
                accumulated.addAll(carryIn.references());
            }
            emitCumulative(onCumulativeReferences, List.copyOf(accumulated));
        }
        int rounds = Math.clamp(roundsToRun, 1, 10);
        for (int i = 0; i < rounds; i++) {
            int round = startRoundIndex + i;
            String suffix = round < roundSuffixes.size() ? roundSuffixes.get(round) : "";
            String q = base + (suffix == null ? "" : suffix);
            WebSearchExecutionResult one =
                    executeSingleRound(
                            snap,
                            webSearchModel,
                            ctx.provider(),
                            ctx.webProv(),
                            ctx.apiKey(),
                            q,
                            conversationId);
            WebGroundingBundle b = one.bundle();
            String piece = b.summaryText() == null ? "" : b.summaryText().trim();
            if (!piece.isBlank()) {
                summaryOrder.add(piece);
            }
            List<WebSearchReference> refs = b.references() == null ? List.of() : b.references();
            for (WebSearchReference r : refs) {
                accumulated.add(r);
                emitCumulative(onCumulativeReferences, List.copyOf(accumulated));
            }
        }
        String mergedSummary = String.join("\n\n---\n\n", summaryOrder);
        return new WebGroundingBundle(mergedSummary, List.copyOf(accumulated));
    }

    private static void emitCumulative(
            Consumer<List<WebSearchReference>> onCumulativeReferences, List<WebSearchReference> accumulated) {
        if (onCumulativeReferences != null && accumulated != null && !accumulated.isEmpty()) {
            onCumulativeReferences.accept(List.copyOf(accumulated));
        }
    }

    private WebSearchExecutionResult executeSingleRound(
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            WebSearchModelProvider provider,
            LlmWebSearchProvider webProv,
            String apiKeyPlaintext,
            String userQueryPlaintext,
            long conversationId) {
        long t0 = System.currentTimeMillis();
        try {
            WebSearchExecutionResult result =
                    provider.execute(webSearchModel, apiKeyPlaintext, userQueryPlaintext);
            recordUsageIfPresent(
                    snap,
                    webSearchModel,
                    result.rawResponseBody(),
                    conversationId,
                    System.currentTimeMillis() - t0);
            return result;
        } catch (RestClientResponseException e) {
            String snippet = responseBodySnippet(e);
            log.warn(
                    "[联网搜索] HTTP {}，提供商 {}，租户 {}，会话 {}，响应摘要：{}",
                    e.getStatusCode().value(),
                    webProv,
                    snap.getTenantId(),
                    conversationId,
                    snippet,
                    e);
            return degradedEmptyResult();
        } catch (RestClientException e) {
            log.warn(
                    "[联网搜索] 客户端异常，提供商 {}，租户 {}，会话 {}",
                    webProv,
                    snap.getTenantId(),
                    conversationId,
                    e);
            return degradedEmptyResult();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("联网检索失败：" + e.getMessage(), e);
        }
    }

    private WebSearchExecutionContext resolveExecutionContext(
            TenantContextHolder.TenantSnapshot snap, SysLlmModel webSearchModel) {
        if (webSearchModel.getApiKeyCipher() == null || webSearchModel.getApiKeyCipher().isBlank()) {
            throw new IllegalStateException("联网搜索模型未配置 API Key");
        }
        var webProv = webSearchModel.resolveWebSearchProvider();
        if (webProv == null) {
            throw new IllegalStateException("联网搜索模型未配置 integration_backend（检索实现）");
        }
        WebSearchModelProvider provider = registry.require(webProv);
        String apiKey;
        try {
            apiKey = aesSecretCipher.decryptFromBase64(webSearchModel.getApiKeyCipher());
        } catch (Exception e) {
            throw new IllegalStateException("联网搜索 API Key 解密失败", e);
        }
        return new WebSearchExecutionContext(provider, webProv, apiKey);
    }

    private record WebSearchExecutionContext(
            WebSearchModelProvider provider, LlmWebSearchProvider webProv, String apiKey) {}

    private static WebSearchExecutionResult degradedEmptyResult() {
        return new WebSearchExecutionResult(new WebGroundingBundle("", List.of()), null);
    }

    private static String responseBodySnippet(RestClientResponseException e) {
        try {
            String raw = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                return "";
            }
            String oneLine = raw.replace('\n', ' ').replace('\r', ' ').trim();
            return oneLine.length() > 400 ? oneLine.substring(0, 400) + "…" : oneLine;
        } catch (Exception ignored) {
            return "";
        }
    }

    void recordUsageIfPresent(
            TenantContextHolder.TenantSnapshot snap,
            SysLlmModel webSearchModel,
            String rawJson,
            long conversationId,
            long durationMs) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            ModelTokenUsage usage = parseWebSearchUsage(root);
            if (usage == null || usage.totalTokens() <= 0) {
                return;
            }
            llmModelUsageRecorder.recordAfterLlmUsage(
                    snap,
                    webSearchModel,
                    webSearchModel.getAlias(),
                    conversationId,
                    usage,
                    durationMs);
        } catch (Exception ex) {
            log.debug("[联网搜索] 跳过用量记录：{}", ex.toString());
        }
    }

    /**
     * OpenAI 兼容根字段 {@code usage}；火山 Ark Bot 为 {@code bot_usage.model_usage[]}（按条累加）。
     */
    static ModelTokenUsage parseWebSearchUsage(JsonNode root) {
        JsonNode u = root.path("usage");
        if (u.isObject()) {
            int total = u.path("total_tokens").asInt(0);
            if (total > 0) {
                return new ModelTokenUsage(
                        u.path("prompt_tokens").asInt(0),
                        u.path("completion_tokens").asInt(0),
                        total);
            }
        }
        JsonNode modelUsage = root.path("bot_usage").path("model_usage");
        if (!modelUsage.isArray() || modelUsage.isEmpty()) {
            return null;
        }
        long prompt = 0L;
        long completion = 0L;
        long total = 0L;
        for (JsonNode item : modelUsage) {
            prompt += item.path("prompt_tokens").asLong(0L);
            completion += item.path("completion_tokens").asLong(0L);
            total += item.path("total_tokens").asLong(0L);
        }
        if (total <= 0L) {
            return null;
        }
        return new ModelTokenUsage(toBoundedInt(prompt), toBoundedInt(completion), toBoundedInt(total));
    }

    private static int toBoundedInt(long v) {
        if (v <= 0L) {
            return 0;
        }
        if (v >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) v;
    }
}
