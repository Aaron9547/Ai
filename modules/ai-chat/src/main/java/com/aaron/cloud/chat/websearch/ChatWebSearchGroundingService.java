package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.starter.ChatWebSearchKnowledgeService;
import com.aaron.cloud.chat.websearch.cache.WebSearchConversationReuseService;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingCacheLookup;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingCacheService;
import com.aaron.cloud.common.api.enums.chat.WebSearchCacheTier;
import com.aaron.cloud.common.tenant.runtime.WebSearchGroundingCachePolicy;
import com.aaron.cloud.chat.websearch.cache.WebSearchGroundingMergeSupport;
import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 对话编排：火山 Ark（{@link com.aaron.cloud.common.api.enums.LlmModelKind#WEB_SEARCH} 可配置行）+ 内置固定源（代码注册）并行检索；可选解析 usage 并计量。
 * 支持 Redis 缓存（精确 + 语义近邻，滚动 6h/24h/48h）与会话内问句复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWebSearchGroundingService {

    /** 内置固定源单源并行上限（略大于 {@link WebSearchFixedSourceHttp#TIMEOUT_MS}）。 */
    private static final long FIXED_SOURCE_DEADLINE_SEC = 12L;

    /** 火山 Ark 联网插件通常慢于 HTML/RSS 固定源，单独放宽截止避免首轮被误杀。 */
    private static final long ARK_SOURCE_DEADLINE_SEC = 90L;

    private final AesSecretCipher aesSecretCipher;
    private final WebSearchProviderRegistry registry;
    private final WebSearchFixedSourceRegistry fixedSourceRegistry;
    private final WebSearchGroundingPlanResolver planResolver;
    private final ObjectMapper objectMapper;
    private final LlmModelUsageRecorder llmModelUsageRecorder;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final WebSearchGroundingCacheService webSearchGroundingCacheService;
    private final WebSearchConversationReuseService webSearchConversationReuseService;
    private final ChatWebSearchKnowledgeService webSearchKnowledgeService;
    private final WebSearchQueryRewriteService webSearchQueryRewriteService;
    private final WebSearchFixedSourceHttpService webSearchFixedSourceHttpService;

    /**
     * 连续多轮调用联网 API（轮数与各轮后缀见租户运行参数 {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT}
     * 与 {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON}）。
     * 引用按轮次顺序追加；每新增一条引用即回调（便于 SSE 渐进下发）。
     *
     * @param onCumulativeReferences 可为 null；每次入参为当前已累积的引用列表副本（只增不减）。
     */
    public WebGroundingBundle groundMultiRoundsWithRaw(
            TenantSnapshot snap,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        WebSearchGroundingPlan plan = planResolver.resolve(snap.getTenantId());
        if (!plan.hasAnySource()) {
            throw new IllegalStateException("未配置联网检索（火山模型或内置固定源至少启用一项）");
        }
        List<GroundingSourceExecution> executions = buildExecutions(snap, plan);
        String base = userQueryPlaintext == null ? "" : userQueryPlaintext;
        String normalized = WebSearchQueryNormalizer.normalize(base);
        final boolean persistWebKnowledge = conversationId > 0;
        var multi =
                tenantRuntimeSettingApplicationService.webSearchGroundingMultiRoundConfig(snap.getTenantId());
        List<String> roundSuffixes = multi.suffixes();
        int configuredRounds = multi.rounds();
        WebSearchGroundingCachePolicy cachePolicy = webSearchGroundingCacheService.policy(snap.getTenantId());
        List<String> fixedCodes = WebSearchModelScopeSupport.sortedFixedSourceCodes(plan.fixedSources());
        Long arkId = plan.arkModel().map(SysLlmModel::getId).orElse(null);
        String configScope =
                webSearchGroundingCacheService.configScopeHash(configuredRounds, roundSuffixes, fixedCodes, arkId);
        long cacheModelKey = WebSearchModelScopeSupport.cacheScopeModelKey(arkId, fixedCodes);

        Optional<WebGroundingBundle> conversationReuse =
                webSearchConversationReuseService.tryReuse(
                        snap.getTenantId(), conversationId, normalized, cachePolicy);
        if (conversationReuse.isPresent()) {
            WebGroundingBundle b = conversationReuse.get();
            return finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    b,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
        }

        Optional<WebGroundingBundle> knowledgeHit =
                webSearchKnowledgeService.tryLookup(snap.getTenantId(), normalized);
        if (knowledgeHit.isPresent()) {
            WebGroundingBundle b = knowledgeHit.get();
            log.info("[联网知识库] 本地命中：租户 {}，会话 {}", snap.getTenantId(), conversationId);
            return finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    b,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
        }

        Optional<WebSearchGroundingCacheLookup> cached =
                webSearchGroundingCacheService.lookup(
                        snap.getTenantId(), cacheModelKey, configScope, normalized, cachePolicy);
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
            return finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    seed,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
        }

        String searchQuery = resolveSearchQueryForLive(snap, base, effectiveRounds, seed);
        WebGroundingBundle live =
                executeRounds(
                        executions,
                        snap,
                        searchQuery,
                        roundSuffixes,
                        0,
                        effectiveRounds,
                        null,
                        conversationId,
                        onCumulativeReferences,
                        false);

        WebGroundingBundle merged = seed == null ? live : WebSearchGroundingMergeSupport.merge(seed, live);
        return finalizeGrounding(
                snap,
                cacheModelKey,
                configScope,
                normalized,
                base,
                merged,
                cachePolicy,
                persistWebKnowledge,
                onCumulativeReferences);
    }

    private WebGroundingBundle finalizeGrounding(
            TenantSnapshot snap,
            long modelId,
            String configScope,
            String normalized,
            String rawQuery,
            WebGroundingBundle bundle,
            WebSearchGroundingCachePolicy cachePolicy,
            boolean persistWebKnowledge,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        emitCumulative(onCumulativeReferences, bundle.references());
        webSearchGroundingCacheService.store(
                snap.getTenantId(), modelId, configScope, normalized, bundle, cachePolicy);
        if (persistWebKnowledge) {
            webSearchKnowledgeService.ingestAsync(
                    snap.getTenantId(), rawQuery, normalized, bundle);
        } else {
            log.debug(
                    "[联网知识库] 跳过沉淀（系统联网任务，非用户对话）：租户 {}，检索词 [{}]",
                    snap.getTenantId(),
                    WebSearchQueryRewriteService.clipForLog(rawQuery));
        }
        return bundle;
    }

    /**
     * 对话流式：首轮（或缓存命中）同步返回以尽快注入主模型；配置多轮时其余轮在虚拟线程中补全并通过 {@code onCumulativeReferences} 渐进下发。
     */
    public WebSearchStreamGroundingSession groundForChatStream(
            TenantSnapshot snap,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        WebSearchGroundingPlan plan = planResolver.resolve(snap.getTenantId());
        if (!plan.hasAnySource()) {
            throw new IllegalStateException("未配置联网检索（火山模型或内置固定源至少启用一项）");
        }
        List<GroundingSourceExecution> executions = buildExecutions(snap, plan);
        String base = userQueryPlaintext == null ? "" : userQueryPlaintext;
        String normalized = WebSearchQueryNormalizer.normalize(base);
        final boolean persistWebKnowledge = conversationId > 0;
        var multi =
                tenantRuntimeSettingApplicationService.webSearchGroundingMultiRoundConfig(snap.getTenantId());
        List<String> roundSuffixes = multi.suffixes();
        int configuredRounds = multi.rounds();
        WebSearchGroundingCachePolicy cachePolicy = webSearchGroundingCacheService.policy(snap.getTenantId());
        List<String> fixedCodes = WebSearchModelScopeSupport.sortedFixedSourceCodes(plan.fixedSources());
        Long arkId = plan.arkModel().map(SysLlmModel::getId).orElse(null);
        String configScope =
                webSearchGroundingCacheService.configScopeHash(configuredRounds, roundSuffixes, fixedCodes, arkId);
        long cacheModelKey = WebSearchModelScopeSupport.cacheScopeModelKey(arkId, fixedCodes);

        Optional<WebGroundingBundle> conversationReuse =
                webSearchConversationReuseService.tryReuse(
                        snap.getTenantId(), conversationId, normalized, cachePolicy);
        if (conversationReuse.isPresent()) {
            WebGroundingBundle b = conversationReuse.get();
            finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    b,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
            return WebSearchStreamGroundingSession.completed(b);
        }

        Optional<WebGroundingBundle> knowledgeHit =
                webSearchKnowledgeService.tryLookup(snap.getTenantId(), normalized);
        if (knowledgeHit.isPresent()) {
            WebGroundingBundle b = knowledgeHit.get();
            log.info("[联网知识库] 本地命中：租户 {}，会话 {}", snap.getTenantId(), conversationId);
            finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    b,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
            return WebSearchStreamGroundingSession.completed(b);
        }

        Optional<WebSearchGroundingCacheLookup> cached =
                webSearchGroundingCacheService.lookup(
                        snap.getTenantId(), cacheModelKey, configScope, normalized, cachePolicy);
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
            finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    seed,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
            return WebSearchStreamGroundingSession.completed(seed);
        }

        String searchQuery = resolveSearchQueryForLive(snap, base, effectiveRounds, seed);

        if (effectiveRounds <= 1) {
            WebGroundingBundle live =
                    executeRounds(
                            executions,
                            snap,
                            searchQuery,
                            roundSuffixes,
                            0,
                            1,
                            null,
                            conversationId,
                            onCumulativeReferences,
                            true);
            WebGroundingBundle merged =
                    seed == null ? live : WebSearchGroundingMergeSupport.merge(seed, live);
            finalizeGrounding(
                    snap,
                    cacheModelKey,
                    configScope,
                    normalized,
                    base,
                    merged,
                    cachePolicy,
                    persistWebKnowledge,
                    onCumulativeReferences);
            return WebSearchStreamGroundingSession.completed(merged);
        }

        WebGroundingBundle afterFirst =
                executeRounds(
                        executions,
                        snap,
                        searchQuery,
                        roundSuffixes,
                        0,
                        1,
                        null,
                        conversationId,
                        onCumulativeReferences,
                        true);
        WebGroundingBundle initial =
                seed == null ? afterFirst : WebSearchGroundingMergeSupport.merge(seed, afterFirst);

        final int remainingRounds = effectiveRounds - 1;
        final WebGroundingBundle initialForAsync = initial;
        final String searchQueryForAsync = searchQuery;
        CompletableFuture<WebGroundingBundle> remainder =
                CompletableFuture.supplyAsync(
                        () -> {
                            WebGroundingBundle rest =
                                    executeRounds(
                                            executions,
                                            snap,
                                            searchQueryForAsync,
                                            roundSuffixes,
                                            1,
                                            remainingRounds,
                                            initialForAsync,
                                            conversationId,
                                            onCumulativeReferences,
                                            false);
                            finalizeGrounding(
                                    snap,
                                    cacheModelKey,
                                    configScope,
                                    normalized,
                                    base,
                                    rest,
                                    cachePolicy,
                                    persistWebKnowledge,
                                    onCumulativeReferences);
                            return rest;
                        },
                        command -> Thread.startVirtualThread(command));

        return new WebSearchStreamGroundingSession(initial, remainder);
    }

    public WebSearchExecutionResult groundWithRaw(
            TenantSnapshot snap, String userQueryPlaintext, long conversationId) {
        WebGroundingBundle b = groundMultiRoundsWithRaw(snap, userQueryPlaintext, conversationId, null);
        return new WebSearchExecutionResult(b, null);
    }

    /**
     * 缓存未完全兜底、需要外呼第三方源时，将用户问句改写为检索词（见 {@link WebSearchQueryRewriteService}）。
     */
    private String resolveSearchQueryForLive(
            TenantSnapshot snap, String base, int effectiveRounds, WebGroundingBundle seed) {
        if (base == null || base.isBlank()) {
            return "";
        }
        if (effectiveRounds <= 0 && seed != null) {
            return base;
        }
        return webSearchQueryRewriteService.rewriteForSearch(snap, base);
    }

    private WebGroundingBundle executeRounds(
            List<GroundingSourceExecution> executions,
            TenantSnapshot snap,
            String searchQueryBase,
            List<String> roundSuffixes,
            int startRoundIndex,
            int roundsToRun,
            WebGroundingBundle carryIn,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences,
            boolean chatStreamGate) {
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
            String q = searchQueryBase + (suffix == null ? "" : suffix);
            WebSearchExecutionResult one =
                    chatStreamGate
                            ? executeMultiSourceSingleRoundChatGate(
                                    snap, executions, q, conversationId, onCumulativeReferences, accumulated)
                            : executeMultiSourceSingleRound(
                                    snap, executions, q, conversationId, onCumulativeReferences, accumulated);
            WebGroundingBundle b = one.bundle();
            String piece = b.summaryText() == null ? "" : b.summaryText().trim();
            if (!piece.isBlank()) {
                summaryOrder.add(piece);
            }
            mergeReferencesDistinct(accumulated, b.references());
            emitCumulative(onCumulativeReferences, List.copyOf(accumulated));
        }
        String mergedSummary = WebSearchSummarySupport.joinSummaryPieces(summaryOrder);
        return new WebGroundingBundle(mergedSummary, List.copyOf(accumulated));
    }

    private static void emitCumulative(
            Consumer<List<WebSearchReference>> onCumulativeReferences, List<WebSearchReference> accumulated) {
        if (onCumulativeReferences != null && accumulated != null && !accumulated.isEmpty()) {
            onCumulativeReferences.accept(List.copyOf(accumulated));
        }
    }

    private WebSearchExecutionResult executeMultiSourceSingleRound(
            TenantSnapshot snap,
            List<GroundingSourceExecution> executions,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences,
            List<WebSearchReference> accumulatedForEmit) {
        List<String> sourceLabels =
                executions.stream().map(GroundingSourceExecution::logLabel).toList();
        log.info(
                "[联网搜索] 本轮并行开始：租户 {}，会话 {}，共 {} 源 {}",
                snap.getTenantId(),
                conversationId,
                executions.size(),
                sourceLabels);
        Object emitLock = new Object();
        List<CompletableFuture<WebGroundingBundle>> bundleFutures =
                executions.stream()
                        .map(
                                exec ->
                                        CompletableFuture.supplyAsync(
                                                        () ->
                                                                executeOneSource(
                                                                        snap,
                                                                        exec,
                                                                        userQueryPlaintext,
                                                                        conversationId),
                                                        command -> Thread.startVirtualThread(command))
                                                .orTimeout(
                                                        sourceDeadlineSec(exec), TimeUnit.SECONDS)
                                                .handle(
                                                        (bundle, ex) -> {
                                                            if (ex == null) {
                                                                return bundle;
                                                            }
                                                            logSourceFailure(
                                                                    snap.getTenantId(),
                                                                    conversationId,
                                                                    exec,
                                                                    "并行截止 "
                                                                            + sourceDeadlineSec(exec)
                                                                            + "s",
                                                                    ex);
                                                            return emptyBundle();
                                                        }))
                        .toList();
        List<CompletableFuture<Void>> emitFutures = new ArrayList<>(bundleFutures.size());
        for (CompletableFuture<WebGroundingBundle> bundleFuture : bundleFutures) {
            emitFutures.add(
                    bundleFuture.thenAccept(
                            bundle -> {
                                if (accumulatedForEmit == null || onCumulativeReferences == null) {
                                    return;
                                }
                                synchronized (emitLock) {
                                    mergeReferencesDistinct(
                                            accumulatedForEmit, bundle.references());
                                    emitCumulative(
                                            onCumulativeReferences, List.copyOf(accumulatedForEmit));
                                }
                            }));
        }
        CompletableFuture.allOf(bundleFutures.toArray(CompletableFuture[]::new)).join();
        CompletableFuture.allOf(emitFutures.toArray(CompletableFuture[]::new)).join();

        WebGroundingBundle merged = emptyBundle();
        int arkRefs = 0;
        boolean arkSummary = false;
        for (int i = 0; i < bundleFutures.size(); i++) {
            WebGroundingBundle one = bundleFutures.get(i).join();
            GroundingSourceExecution exec = executions.get(i);
            if (exec.arkModel() != null) {
                arkRefs += one.references() == null ? 0 : one.references().size();
                arkSummary =
                        arkSummary
                                || (one.summaryText() != null && !one.summaryText().isBlank());
            }
            merged = WebSearchGroundingMergeSupport.merge(merged, one);
        }
        int mergedRefs = merged.references() == null ? 0 : merged.references().size();
        log.info(
                "[联网搜索] 本轮并行结束：租户 {}，会话 {}，合并引用 {} 条（火山 Ark 引用 {} 条，Ark 摘要 {}）",
                snap.getTenantId(),
                conversationId,
                mergedRefs,
                arkRefs,
                arkSummary ? "有" : "无");
        return new WebSearchExecutionResult(merged, null);
    }

    /**
     * 对话流首轮：已配置火山 Ark 时必须等其外呼结束；合并结果须至少一个源可用。
     * Ark 无摘要/引用时再等到租户已启用的内置固定源至少一个有结果或全部结束；其余固定源可在主对话开始后继续 SSE 补全。
     */
    private WebSearchExecutionResult executeMultiSourceSingleRoundChatGate(
            TenantSnapshot snap,
            List<GroundingSourceExecution> executions,
            String userQueryPlaintext,
            long conversationId,
            Consumer<List<WebSearchReference>> onCumulativeReferences,
            List<WebSearchReference> accumulatedForEmit) {
        List<String> sourceLabels =
                executions.stream().map(GroundingSourceExecution::logLabel).toList();
        log.info(
                "[联网搜索] 对话闸门并行开始：租户 {}，会话 {}，共 {} 源 {}",
                snap.getTenantId(),
                conversationId,
                executions.size(),
                sourceLabels);

        Optional<GroundingSourceExecution> arkExec =
                executions.stream().filter(e -> e.arkModel() != null).findFirst();
        List<GroundingSourceExecution> fixedExecs =
                executions.stream().filter(e -> e.fixedSource() != null).toList();

        Object emitLock = new Object();
        Optional<CompletableFuture<WebGroundingBundle>> arkFuture =
                arkExec.map(
                        exec ->
                                startBundleFuture(
                                        snap, exec, userQueryPlaintext, conversationId, emitLock,
                                        accumulatedForEmit, onCumulativeReferences));
        List<CompletableFuture<WebGroundingBundle>> fixedFutures = new ArrayList<>();
        for (GroundingSourceExecution exec : fixedExecs) {
            fixedFutures.add(
                    startBundleFuture(
                            snap, exec, userQueryPlaintext, conversationId, emitLock,
                            accumulatedForEmit, onCumulativeReferences));
        }

        WebGroundingBundle gateMerged = emptyBundle();

        if (arkFuture.isPresent()) {
            WebGroundingBundle arkBundle = arkFuture.get().join();
            gateMerged = WebSearchGroundingMergeSupport.merge(gateMerged, arkBundle);
            log.info(
                    "[联网搜索] 对话闸门：已等待火山 Ark 返回，租户 {}，会话 {}，Ark 可用={}",
                    snap.getTenantId(),
                    conversationId,
                    hasUsableGrounding(arkBundle));
        }

        gateMerged = mergeCompletedBundles(gateMerged, fixedFutures);

        if (!hasUsableGrounding(gateMerged)) {
            if (arkFuture.isPresent() && !fixedFutures.isEmpty()) {
                log.info(
                        "[联网搜索] 对话闸门：火山 Ark 无可用结果，等待内置固定源，租户 {}，会话 {}",
                        snap.getTenantId(),
                        conversationId);
            }
            gateMerged = waitForAtLeastOneFixedUsable(gateMerged, fixedFutures);
        } else if (arkFuture.isPresent()) {
            log.info(
                    "[联网搜索] 对话闸门：火山已有可用结果，主对话即将注入；未完成的固定源继续后台补全，租户 {}，会话 {}",
                    snap.getTenantId(),
                    conversationId);
        } else if (!fixedExecs.isEmpty()) {
            log.info(
                    "[联网搜索] 对话闸门：未配置火山模型，已等待至少一个内置固定源，租户 {}，会话 {}，可用={}",
                    snap.getTenantId(),
                    conversationId,
                    hasUsableGrounding(gateMerged));
        }

        WebGroundingBundle finalMerged = buildChatGateInjectionBundle(arkFuture, fixedFutures);

        drainRemainingFixedForSse(
                fixedFutures, accumulatedForEmit, onCumulativeReferences, emitLock);

        int mergedRefs = finalMerged.references() == null ? 0 : finalMerged.references().size();
        log.info(
                "[联网搜索] 对话闸门结束：租户 {}，会话 {}，注入合并引用 {} 条，可用={}",
                snap.getTenantId(),
                conversationId,
                mergedRefs,
                hasUsableGrounding(finalMerged));
        return new WebSearchExecutionResult(finalMerged, null);
    }

    private CompletableFuture<WebGroundingBundle> startBundleFuture(
            TenantSnapshot snap,
            GroundingSourceExecution exec,
            String userQueryPlaintext,
            long conversationId,
            Object emitLock,
            List<WebSearchReference> accumulatedForEmit,
            Consumer<List<WebSearchReference>> onCumulativeReferences) {
        return CompletableFuture.supplyAsync(
                        () -> executeOneSource(snap, exec, userQueryPlaintext, conversationId),
                        command -> Thread.startVirtualThread(command))
                .orTimeout(sourceDeadlineSec(exec), TimeUnit.SECONDS)
                .handle(
                        (bundle, ex) -> {
                            if (ex == null) {
                                return bundle;
                            }
                            logSourceFailure(
                                    snap.getTenantId(),
                                    conversationId,
                                    exec,
                                    "并行截止 " + sourceDeadlineSec(exec) + "s",
                                    ex);
                            return emptyBundle();
                        })
                .thenApply(
                        bundle -> {
                            if (accumulatedForEmit != null && onCumulativeReferences != null) {
                                synchronized (emitLock) {
                                    mergeReferencesDistinct(accumulatedForEmit, bundle.references());
                                    emitCumulative(
                                            onCumulativeReferences, List.copyOf(accumulatedForEmit));
                                }
                            }
                            return bundle;
                        });
    }

    private static WebGroundingBundle mergeCompletedBundles(
            WebGroundingBundle base, List<CompletableFuture<WebGroundingBundle>> futures) {
        WebGroundingBundle merged = base == null ? emptyBundle() : base;
        if (futures == null) {
            return merged;
        }
        for (CompletableFuture<WebGroundingBundle> f : futures) {
            if (f.isDone()) {
                merged = WebSearchGroundingMergeSupport.merge(merged, f.join());
            }
        }
        return merged;
    }

    private static WebGroundingBundle waitForAtLeastOneFixedUsable(
            WebGroundingBundle current, List<CompletableFuture<WebGroundingBundle>> fixedFutures) {
        WebGroundingBundle merged = current == null ? emptyBundle() : current;
        if (fixedFutures == null || fixedFutures.isEmpty()) {
            return merged;
        }
        if (hasUsableGrounding(merged)) {
            return merged;
        }
        List<CompletableFuture<WebGroundingBundle>> pending = new ArrayList<>();
        java.util.Set<CompletableFuture<WebGroundingBundle>> folded = new java.util.HashSet<>();
        for (CompletableFuture<WebGroundingBundle> f : fixedFutures) {
            if (f.isDone() && folded.add(f)) {
                merged = WebSearchGroundingMergeSupport.merge(merged, f.join());
            } else if (!f.isDone()) {
                pending.add(f);
            }
        }
        while (!hasUsableGrounding(merged) && !pending.isEmpty()) {
            CompletableFuture.anyOf(pending.toArray(CompletableFuture[]::new)).join();
            for (CompletableFuture<WebGroundingBundle> f : fixedFutures) {
                if (f.isDone() && folded.add(f)) {
                    merged = WebSearchGroundingMergeSupport.merge(merged, f.join());
                }
            }
            pending.removeIf(CompletableFuture::isDone);
        }
        if (!hasUsableGrounding(merged)) {
            CompletableFuture.allOf(fixedFutures.toArray(CompletableFuture[]::new)).join();
            merged = mergeCompletedBundles(merged, fixedFutures);
        }
        return merged;
    }

    /** 主对话注入：火山 Ark 优先，仅合并已完成的固定源（未完成的由 {@link #drainRemainingFixedForSse} 补 SSE）。 */
    private static WebGroundingBundle buildChatGateInjectionBundle(
            Optional<CompletableFuture<WebGroundingBundle>> arkFuture,
            List<CompletableFuture<WebGroundingBundle>> fixedFutures) {
        WebGroundingBundle merged = emptyBundle();
        if (arkFuture.isPresent()) {
            merged = WebSearchGroundingMergeSupport.merge(merged, arkFuture.get().join());
        }
        if (fixedFutures != null) {
            for (CompletableFuture<WebGroundingBundle> f : fixedFutures) {
                if (f.isDone()) {
                    merged = WebSearchGroundingMergeSupport.merge(merged, f.join());
                }
            }
        }
        return merged;
    }

    private void drainRemainingFixedForSse(
            List<CompletableFuture<WebGroundingBundle>> fixedFutures,
            List<WebSearchReference> accumulatedForEmit,
            Consumer<List<WebSearchReference>> onCumulativeReferences,
            Object emitLock) {
        if (fixedFutures == null
                || fixedFutures.isEmpty()
                || accumulatedForEmit == null
                || onCumulativeReferences == null) {
            return;
        }
        boolean anyPending = fixedFutures.stream().anyMatch(f -> !f.isDone());
        if (!anyPending) {
            return;
        }
        CompletableFuture.runAsync(
                () -> {
                    for (CompletableFuture<WebGroundingBundle> f : fixedFutures) {
                        try {
                            WebGroundingBundle bundle = f.join();
                            synchronized (emitLock) {
                                mergeReferencesDistinct(accumulatedForEmit, bundle.references());
                                emitCumulative(
                                        onCumulativeReferences, List.copyOf(accumulatedForEmit));
                            }
                        } catch (RuntimeException ignored) {
                            // 单源失败已在 handle 中记日志
                        }
                    }
                },
                command -> Thread.startVirtualThread(command));
    }

    private static boolean hasUsableGrounding(WebGroundingBundle bundle) {
        if (bundle == null) {
            return false;
        }
        if (bundle.references() != null && !bundle.references().isEmpty()) {
            return true;
        }
        String summary = bundle.summaryText();
        return summary != null && !summary.isBlank();
    }

    private static long sourceDeadlineSec(GroundingSourceExecution exec) {
        return exec.arkModel() != null ? ARK_SOURCE_DEADLINE_SEC : FIXED_SOURCE_DEADLINE_SEC;
    }

    private static void mergeReferencesDistinct(
            List<WebSearchReference> target, List<WebSearchReference> incoming) {
        if (target == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        for (WebSearchReference r : incoming) {
            if (r == null) {
                continue;
            }
            boolean exists =
                    target.stream()
                            .anyMatch(
                                    existing ->
                                            (existing.url() != null
                                                            && existing.url().equals(r.url()))
                                                    || (existing.title() != null
                                                            && existing.title().equals(r.title())
                                                            && existing.url() == null
                                                            && r.url() == null));
            if (!exists) {
                target.add(r);
            }
        }
    }

    private WebGroundingBundle executeOneSource(
            TenantSnapshot snap,
            GroundingSourceExecution exec,
            String userQueryPlaintext,
            long conversationId) {
        long t0 = System.currentTimeMillis();
        log.info(
                "[联网搜索] 源开始：{}，租户 {}，会话 {}",
                exec.logLabel(),
                snap.getTenantId(),
                conversationId);
        try {
            WebSearchExecutionResult result = exec.invoke(snap, userQueryPlaintext);
            if (exec.arkModel() != null) {
                recordUsageIfPresent(
                        snap,
                        exec.arkModel(),
                        result.rawResponseBody(),
                        conversationId,
                        System.currentTimeMillis() - t0);
            }
            WebGroundingBundle labeled = labelBundle(result.bundle(), exec.label(), exec.sourceKey());
            long elapsed = System.currentTimeMillis() - t0;
            int refs = labeled.references() == null ? 0 : labeled.references().size();
            boolean hasSummary =
                    labeled.summaryText() != null && !labeled.summaryText().isBlank();
            if (refs > 0 || hasSummary) {
                log.info(
                        "[联网搜索] 源成功：{}，租户 {}，会话 {}，引用 {} 条，摘要 {}，耗时 {}ms",
                        exec.logLabel(),
                        snap.getTenantId(),
                        conversationId,
                        refs,
                        hasSummary ? "有" : "无",
                        elapsed);
            } else {
                log.warn(
                        "[联网搜索] 源无结果：{}，租户 {}，会话 {}，耗时 {}ms（请求可能成功但解析为空）",
                        exec.logLabel(),
                        snap.getTenantId(),
                        conversationId,
                        elapsed);
            }
            return labeled;
        } catch (RestClientResponseException e) {
            String snippet = responseBodySnippet(e);
            logSourceFailure(
                    snap.getTenantId(),
                    conversationId,
                    exec,
                    "HTTP " + e.getStatusCode().value() + "，响应摘要=" + snippet,
                    e);
            return emptyBundle();
        } catch (RestClientException e) {
            logSourceFailure(snap.getTenantId(), conversationId, exec, "RestClient", e);
            return emptyBundle();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logSourceFailure(snap.getTenantId(), conversationId, exec, e.getClass().getSimpleName(), e);
            return emptyBundle();
        }
    }

    private void logSourceFailure(
            long tenantId, long conversationId, GroundingSourceExecution exec, String kind, Throwable e) {
        log.warn(
                "[联网搜索] 源失败：{}，租户 {}，会话 {}，类型 {}，原因 {}，出站 [{}]",
                exec.logLabel(),
                tenantId,
                conversationId,
                kind,
                rootMessage(e),
                webSearchFixedSourceHttpService.outboundDiagnostics(tenantId),
                e);
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        if (c.getMessage() != null && !c.getMessage().isBlank()) {
            return c.getMessage();
        }
        return c.getClass().getSimpleName();
    }

    private static WebGroundingBundle labelBundle(
            WebGroundingBundle bundle, String sourceLabel, String sourceKey) {
        if (bundle == null) {
            return emptyBundle();
        }
        String sum = bundle.summaryText() == null ? "" : bundle.summaryText().trim();
        if (!sum.isEmpty()) {
            sum = WebSearchSummarySupport.prefixSummary(sourceLabel, sum);
        }
        List<WebSearchReference> refs = bundle.references() == null ? List.of() : bundle.references();
        if (sourceKey != null && !sourceKey.isBlank() && !refs.isEmpty()) {
            refs = refs.stream().map(r -> withSourceKey(r, sourceKey)).toList();
        }
        return new WebGroundingBundle(sum, refs);
    }

    private static WebSearchReference withSourceKey(WebSearchReference r, String sourceKey) {
        if (r == null) {
            return null;
        }
        if (r.sourceKey() != null && !r.sourceKey().isBlank()) {
            return r;
        }
        return new WebSearchReference(
                r.title(),
                r.url(),
                r.snippet(),
                r.siteName(),
                r.logoUrl(),
                r.publishTime(),
                r.extraJson(),
                sourceKey);
    }

    private static String sourceLabel(SysLlmModel model) {
        if (model.getAlias() != null && !model.getAlias().isBlank()) {
            return model.getAlias().trim();
        }
        if (model.getDisplayName() != null && !model.getDisplayName().isBlank()) {
            return model.getDisplayName().trim();
        }
        return "web-search";
    }

    private static WebGroundingBundle emptyBundle() {
        return new WebGroundingBundle("", List.of());
    }

    private List<GroundingSourceExecution> buildExecutions(TenantSnapshot snap, WebSearchGroundingPlan plan) {
        List<GroundingSourceExecution> out = new ArrayList<>();
        plan.arkModel()
                .ifPresent(
                        model -> {
                            var webProv = model.resolveWebSearchProvider();
                            if (webProv == null || webProv != LlmWebSearchProvider.VOLCENGINE_ARK_BOT) {
                                throw new IllegalStateException(
                                        "联网搜索模型须为火山 Ark Bot：" + model.getAlias());
                            }
                            if (model.getApiKeyCipher() == null || model.getApiKeyCipher().isBlank()) {
                                throw new IllegalStateException("联网搜索模型未配置 API Key：" + model.getAlias());
                            }
                            String apiKey;
                            try {
                                apiKey = aesSecretCipher.decryptFromBase64(model.getApiKeyCipher());
                            } catch (Exception e) {
                                throw new IllegalStateException(
                                        "联网搜索 API Key 解密失败：" + model.getAlias(), e);
                            }
                            WebSearchModelProvider provider = registry.require(webProv);
                            out.add(new GroundingSourceExecution(
                                    sourceLabel(model),
                                    snap,
                                    model,
                                    provider,
                                    apiKey,
                                    null,
                                    null));
                        });
        if (plan.fixedSources() != null) {
            for (WebSearchFixedSource src : plan.fixedSources()) {
                WebSearchFixedSourceProvider provider = fixedSourceRegistry.require(src);
                out.add(new GroundingSourceExecution(
                        fixedSourceLabel(src), snap, null, null, "", provider, src));
            }
        }
        return List.copyOf(out);
    }

    private static String fixedSourceLabel(WebSearchFixedSource src) {
        return switch (src) {
            case DUCKDUCKGO_HTML -> "DuckDuckGo";
            case WIKIPEDIA_REST -> "Wikipedia";
            case GOOGLE_NEWS_RSS -> "Google News";
            case BAIDU_NEWS_HTML -> "百度新闻";
        };
    }

    private record GroundingSourceExecution(
            String label,
            TenantSnapshot snap,
            SysLlmModel arkModel,
            WebSearchModelProvider arkProvider,
            String arkApiKey,
            WebSearchFixedSourceProvider fixedProvider,
            WebSearchFixedSource fixedSource) {

        String logLabel() {
            if (fixedSource != null) {
                return label + "(" + fixedSource.getCode() + ")";
            }
            if (arkModel != null) {
                return label + "(ark:" + arkModel.getAlias() + ",id=" + arkModel.getId() + ")";
            }
            return label;
        }

        String sourceKey() {
            if (fixedSource != null) {
                return fixedSource.getCode();
            }
            if (arkModel != null) {
                return "VOLCENGINE_ARK_BOT";
            }
            return null;
        }

        WebSearchExecutionResult invoke(TenantSnapshot snap, String query) throws Exception {
            if (arkModel != null && arkProvider != null) {
                return arkProvider.execute(arkModel, arkApiKey, query);
            }
            if (fixedProvider != null && fixedSource != null) {
                return fixedProvider.execute(snap.getTenantId(), query);
            }
            throw new IllegalStateException("无效的联网检索执行项");
        }
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
            TenantSnapshot snap,
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
