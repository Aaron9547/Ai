package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendClickBody;
import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendItemView;
import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendResponse;
import com.aaron.cloud.chat.recommend.ChatDailyRecommendJsonSupport.DailyRecommendItemRecord;
import com.aaron.cloud.chat.websearch.ChatWebSearchGroundingService;
import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.chat.websearch.WebSearchGroundingPlan;
import com.aaron.cloud.chat.websearch.WebSearchGroundingPlanResolver;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.chat.websearch.WebSearchUserContext;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterDailyBatchStatus;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatUserDailyRecommendRepository;
import com.aaron.cloud.common.chat.entity.ChatUserDailyRecommend;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 对话页「今日 AI 推荐」：联网检索 + 用户画像 + 语言模型结构化，按主体每日去重。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUserDailyRecommendService {

    private final ChatUserDailyRecommendRepository recommendRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatDailyRecommendJsonSupport jsonSupport;
    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ChatWebSearchGroundingService webSearchGroundingService;
    private final WebSearchGroundingPlanResolver webSearchGroundingPlanResolver;
    private final ModelInvokePort modelInvokePort;
    private final UserProfileApplicationService userProfileApplicationService;
    private final ChatDailyRecommendProfileIngest profileIngest;
    private final PromptTemplateResolvePort promptTemplates;
    private final ChatDailyRecommendSearchQuerySupport dailyRecommendSearchQuery;

    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> generationInflight = new ConcurrentHashMap<>();

    /** 异步任务异常退出后 {@code generationInflight} 可能残留，超时后允许重新调度。 */
    private static final Duration GENERATION_INFLIGHT_STALE = Duration.ofMinutes(6);

    public DailyRecommendResponse getOrGenerateForCurrentSubject() {
        return getOrGenerate(false, false);
    }

    public DailyRecommendResponse retryForCurrentSubject() {
        return getOrGenerate(true, false);
    }

    /**
     * 登录后：仅有活跃会话的主体才强制按画像重生；无会话仍读租户共用冷启动（与访客一致）。
     */
    public DailyRecommendResponse regenerateForLoggedInUser() {
        TenantSnapshot snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            return getOrGenerate(false, false);
        }
        if (!hasPersonalizedRecommendSubject(snap)) {
            return getOrGenerate(false, false);
        }
        return getOrGenerate(false, true);
    }

    /** 资讯卡片点击埋点；写入画像与记忆，失败仅记日志，不向客户端抛错。 */
    public void recordClick(DailyRecommendClickBody body) {
        if (body == null) {
            return;
        }
        try {
            TenantSnapshot snap = TenantContextHolder.require();
            profileIngest.ingestClick(
                    snap,
                    body.itemId(),
                    body.tag(),
                    body.title(),
                    body.summary(),
                    body.url());
        } catch (Exception ex) {
            log.warn("[今日推荐] 点击埋点失败 itemId={}", body.itemId(), ex);
        }
    }

    private DailyRecommendResponse getOrGenerate(boolean forceRetry, boolean forceRegenerate) {
        TenantSnapshot snap = TenantContextHolder.require();
        String subjectKey = resolveRecommendSubjectKey(snap);
        if (subjectKey == null) {
            return emptyResponse(null, BeijingTime.today().toString(), "EMPTY", "未识别用户或设备");
        }
        long tenantId = snap.getTenantId();
        LocalDate today = BeijingTime.today();
        String lockKey = tenantId + ":" + subjectKey + ":" + today;

        Object lock = generationLocks.computeIfAbsent(lockKey, k -> new Object());
        boolean scheduleGeneration = false;
        synchronized (lock) {
            var existing = recommendRepository.findByTenantSubjectAndDate(tenantId, subjectKey, today);
            if (forceRegenerate && existing.isPresent()) {
                recommendRepository.deleteByTenantSubjectAndDate(tenantId, subjectKey, today);
                generationInflight.remove(lockKey);
                existing = java.util.Optional.empty();
            }
            if (existing.isPresent()) {
                ChatUserDailyRecommend row = existing.get();
                if (!forceRetry && !forceRegenerate) {
                    if (row.getStatus() == ChatStarterDailyBatchStatus.OK) {
                        return toResponse(row);
                    } else if (row.getStatus() != ChatStarterDailyBatchStatus.PENDING) {
                        return toResponse(row);
                    } else if (generationInflight.containsKey(lockKey)
                            && !isGenerationInflightStale(row)) {
                        return toResponse(row);
                    } else {
                        generationInflight.remove(lockKey);
                    }
                } else {
                    if (row.getStatus() == ChatStarterDailyBatchStatus.OK) {
                        return toResponse(row);
                    }
                    if (row.getStatus() == ChatStarterDailyBatchStatus.FAILED) {
                        if (row.getRetryUsed() != null && row.getRetryUsed() == 1) {
                            return toResponse(row);
                        }
                        row.setRetryUsed(1);
                    }
                    generationInflight.remove(lockKey);
                    resetBatchToPending(row);
                    existing = recommendRepository.findByTenantSubjectAndDate(tenantId, subjectKey, today);
                }
            }

            ChatUserDailyRecommend batch =
                    existing.orElseGet(() -> insertPending(tenantId, subjectKey, today));

            if (!forceRetry && !forceRegenerate && batch.getStatus() == ChatStarterDailyBatchStatus.OK) {
                return toResponse(batch);
            }
            if (!forceRetry
                    && !forceRegenerate
                    && batch.getStatus() == ChatStarterDailyBatchStatus.FAILED
                    && batch.getRetryUsed() != null
                    && batch.getRetryUsed() == 1) {
                return toResponse(batch);
            }

            if (batch.getStatus() == ChatStarterDailyBatchStatus.FAILED) {
                resetBatchToPending(batch);
            }

            scheduleGeneration = generationInflight.putIfAbsent(lockKey, Boolean.TRUE) == null;
        }

        if (scheduleGeneration) {
            scheduleGenerationJob(lockKey, snap, subjectKey, tenantId, today);
        }

        return recommendRepository
                .findByTenantSubjectAndDate(tenantId, subjectKey, today)
                .map(this::toResponse)
                .orElseGet(
                        () ->
                                emptyResponse(
                                        subjectKey, today.toString(), "LOADING", null));
    }

    private void resetBatchToPending(ChatUserDailyRecommend batch) {
        batch.setStatus(ChatStarterDailyBatchStatus.PENDING);
        batch.setErrorMessage(null);
        batch.setItemsJson(null);
        recommendRepository.updateById(batch);
    }

    private boolean isGenerationInflightStale(ChatUserDailyRecommend row) {
        LocalDateTime ref = row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt();
        if (ref == null) {
            return true;
        }
        return ref.isBefore(LocalDateTime.now().minus(GENERATION_INFLIGHT_STALE));
    }

    /** 异步联网+结构化，HTTP 立即返回 {@code LOADING}，供前端轮询。 */
    private void scheduleGenerationJob(
            String lockKey,
            TenantSnapshot snap,
            String subjectKey,
            long tenantId,
            LocalDate today) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        TenantContextHolder.set(snap);
                        ChatUserDailyRecommend batch =
                                recommendRepository
                                        .findByTenantSubjectAndDate(tenantId, subjectKey, today)
                                        .orElse(null);
                        if (batch == null || batch.getStatus() != ChatStarterDailyBatchStatus.PENDING) {
                            return;
                        }
                        generateAndPersist(snap, subjectKey, batch);
                    } catch (Exception ex) {
                        log.warn("[今日推荐] 异步生成异常 lockKey={}", lockKey, ex);
                    } finally {
                        TenantContextHolder.clear();
                        generationInflight.remove(lockKey);
                    }
                },
                Thread::startVirtualThread);
    }

    private ChatUserDailyRecommend insertPending(long tenantId, String subjectKey, LocalDate today) {
        var row = new ChatUserDailyRecommend();
        row.setTenantId(tenantId);
        row.setSubjectKey(subjectKey);
        row.setRecommendDate(today);
        row.setStatus(ChatStarterDailyBatchStatus.PENDING);
        row.setRetryUsed(0);
        recommendRepository.insert(row);
        return row;
    }

    /**
     * 今日推荐主体：无活跃会话 → 租户共用 {@code tc:{tenantId}}；有会话 → {@code u:}/{@code d:} 按主体每日一批。
     */
    private String resolveRecommendSubjectKey(TenantSnapshot snap) {
        long tenantId = snap.getTenantId();
        if (!hasPersonalizedRecommendSubject(snap)) {
            return ProfileSubjectKey.tenantColdStartKey(tenantId);
        }
        return ProfileSubjectKey.fromSnapshot(snap);
    }

    /** 已登录或访客，只要存在活跃会话即走画像推荐（每日首次进入才触发生成）。 */
    private boolean hasPersonalizedRecommendSubject(TenantSnapshot snap) {
        return conversationRepository.hasActiveForSubject(
                snap.getTenantId(), snap.getUserId(), snap.getDeviceId());
    }

    private void generateAndPersist(TenantSnapshot snap, String subjectKey, ChatUserDailyRecommend batch) {
        long tenantId = snap.getTenantId();
        WebSearchGroundingPlan groundingPlan = webSearchGroundingPlanResolver.resolveModelsOnly(tenantId);
        if (!groundingPlan.hasAnyModel()) {
            failBatch(batch, "租户未配置联网检索模型（请在「联网检索源」勾选联网模型）");
            return;
        }

        if (!hasPersonalizedRecommendSubject(snap)) {
            generateColdStartHotNewsAndPersist(snap, subjectKey, batch, groundingPlan);
            return;
        }

        String profileHint =
                userProfileApplicationService.buildPromptAddendum(snap, "今日资讯推荐", false);
        try {
            var mergedBundle = fetchDailyRecommendGrounding(snap, profileHint, groundingPlan);
            String summary =
                    mergedBundle.summaryText() == null ? "" : mergedBundle.summaryText().trim();
            if (summary.isBlank()) {
                failBatch(batch, "联网检索未返回可用摘要");
                return;
            }

            List<DailyRecommendItemRecord> items =
                    structureItems(
                            tenantId,
                            profileHint,
                            summary,
                            mergedBundle.references());
            items = ChatDailyRecommendUrlSupport.attachReferenceUrls(items, mergedBundle.references());
            items = normalizeItemDates(items, BeijingTime.today());
            if (items.isEmpty()) {
                failBatch(batch, "语言模型未解析出有效推荐条目");
                return;
            }

            batch.setStatus(ChatStarterDailyBatchStatus.OK);
            batch.setItemsJson(jsonSupport.toJson(items));
            batch.setErrorMessage(null);
            batch.setFetchedAt(LocalDateTime.now());
            recommendRepository.updateById(batch);
            log.info(
                    "[今日推荐] 已生成 tenantId={} subject={} count={}",
                    tenantId,
                    subjectKey,
                    items.size());
        } catch (Exception e) {
            log.warn("[今日推荐] 生成失败 tenantId={} subject={}", tenantId, subjectKey, e);
            failBatch(batch, truncate(e.getMessage(), 480));
        }
    }

    /**
     * 无历史对话：单次「今日热点」联网检索 + 结构化，不读画像、不跑昨日补充；登录用户与访客同一策略。
     */
    private void generateColdStartHotNewsAndPersist(
            TenantSnapshot snap,
            String subjectKey,
            ChatUserDailyRecommend batch,
            WebSearchGroundingPlan groundingPlan) {
        long tenantId = snap.getTenantId();
        try {
            var groundingBundle = fetchDailyRecommendGrounding(snap, "", groundingPlan);
            String summary =
                    groundingBundle.summaryText() == null ? "" : groundingBundle.summaryText().trim();
            if (summary.isBlank()) {
                failBatch(batch, "联网检索未返回可用摘要");
                return;
            }

            List<DailyRecommendItemRecord> items =
                    structureItems(tenantId, "", summary, groundingBundle.references());
            items =
                    ChatDailyRecommendUrlSupport.attachReferenceUrls(items, groundingBundle.references());
            items = normalizeItemDates(items, BeijingTime.today());
            if (items.isEmpty()) {
                failBatch(batch, "语言模型未解析出有效推荐条目");
                return;
            }

            batch.setStatus(ChatStarterDailyBatchStatus.OK);
            batch.setItemsJson(jsonSupport.toJsonColdStart(items));
            batch.setErrorMessage(null);
            batch.setFetchedAt(LocalDateTime.now());
            recommendRepository.updateById(batch);
            log.info(
                    "[今日推荐] 冷启动热点 tenantId={} subject={} count={}",
                    tenantId,
                    subjectKey,
                    items.size());
        } catch (Exception e) {
            log.warn("[今日推荐] 冷启动热点失败 tenantId={} subject={}", tenantId, subjectKey, e);
            failBatch(batch, truncate(e.getMessage(), 480));
        }
    }

    /** 今日洞察联网：单次本日检索词；仅用联网模型（忽略内置固定源），多模型并行外呼后合并。 */
    private WebGroundingBundle fetchDailyRecommendGrounding(
            TenantSnapshot snap, String profileHint, WebSearchGroundingPlan groundingPlan)
            throws Exception {
        String queryToday = dailyRecommendSearchQuery.buildTodayPrimary(snap, profileHint);
        return webSearchGroundingService
                .groundWithRaw(
                        snap,
                        WebSearchUserContext.of(queryToday),
                        0L,
                        ChatStarterPromptSource.DAILY_RECOMMEND,
                        groundingPlan)
                .bundle();
    }

    private List<DailyRecommendItemRecord> structureItems(
            long tenantId,
            String profileHint,
            String webSummary,
            List<WebSearchReference> references)
            throws Exception {
        SysLlmModel lang = llmModelRepository.pickDefaultLanguageModel(tenantId).orElse(null);
        if (lang == null) {
            return List.of();
        }
        LlmModelKind k = lang.getModelKind() != null ? lang.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            return List.of();
        }
        try {
            LlmModelKindPolicy.assertLanguageModelForChatStream(lang);
        } catch (Exception ex) {
            log.debug("[今日推荐] 跳过非对话语言模型 tenantId={}", tenantId);
            return List.of();
        }

        LocalDate today = BeijingTime.today();
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(
                promptTemplates.renderSystem(
                        "daily_recommend_structure",
                        tenantId,
                        "zh-CN",
                        Map.of(
                                "today",
                                today.toString(),
                                "today_label",
                                BeijingTime.formatChineseDateLabel(today))));
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        StringBuilder body = new StringBuilder();
        body.append("【联网检索摘要】\n").append(webSummary);
        String refBlock = ChatDailyRecommendUrlSupport.formatReferencesForPrompt(references);
        if (!refBlock.isBlank()) {
            body.append("\n\n【联网引用列表】（每条推荐的 url 必须从下列 url 中选取）\n")
                    .append(refBlock);
        }
        if (profileHint != null && !profileHint.isBlank()) {
            body.append("\n\n【用户画像与记忆】\n").append(profileHint.trim());
        }
        user.setContent(body.toString());
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setUsageScene(LlmUsageScene.DAILY_RECOMMEND.getCode());
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return jsonSupport.parseItems(acc.toString());
    }

    private static List<DailyRecommendItemRecord> normalizeItemDates(
            List<DailyRecommendItemRecord> items, LocalDate today) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DailyRecommendItemRecord> out = new ArrayList<>(items.size());
        for (DailyRecommendItemRecord item : items) {
            out.add(
                    new DailyRecommendItemRecord(
                            item.tag(),
                            item.title(),
                            item.summary(),
                            item.source(),
                            normalizePublishDate(item.date(), today),
                            item.url()));
        }
        return out;
    }

    private static String normalizePublishDate(String raw, LocalDate today) {
        String todayStr = today.toString();
        if (raw == null || raw.isBlank() || "—".equals(raw.trim())) {
            return todayStr;
        }
        try {
            LocalDate parsed = LocalDate.parse(raw.trim());
            if (parsed.isAfter(today)) {
                return todayStr;
            }
            return parsed.toString();
        } catch (Exception ignored) {
            return todayStr;
        }
    }

    private void failBatch(ChatUserDailyRecommend batch, String message) {
        batch.setStatus(ChatStarterDailyBatchStatus.FAILED);
        batch.setErrorMessage(message);
        batch.setItemsJson(null);
        batch.setFetchedAt(LocalDateTime.now());
        recommendRepository.updateById(batch);
    }

    private DailyRecommendResponse toResponse(ChatUserDailyRecommend row) {
        String cacheKey = row.getSubjectKey();
        String dateStr =
                row.getRecommendDate() == null ? BeijingTime.today().toString() : row.getRecommendDate().toString();
        ChatStarterDailyBatchStatus st = row.getStatus();
        if (st == null) {
            st = ChatStarterDailyBatchStatus.PENDING;
        }
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return switch (st) {
            case OK -> {
                List<DailyRecommendItemRecord> parsed = jsonSupport.parseItems(row.getItemsJson());
                yield new DailyRecommendResponse(
                        cacheKey,
                        dateStr,
                        "OK",
                        toViews(parsed),
                        buildProfileTags(snap, parsed),
                        null,
                        false,
                        false);
            }
            case FAILED -> {
                boolean retryAllowed = row.getRetryUsed() == null || row.getRetryUsed() == 0;
                yield new DailyRecommendResponse(
                        cacheKey,
                        dateStr,
                        "ERROR",
                        List.of(),
                        buildProfileTags(snap, List.of()),
                        row.getErrorMessage(),
                        retryAllowed,
                        retryAllowed);
            }
            case PENDING ->
                    new DailyRecommendResponse(
                            cacheKey,
                            dateStr,
                            "LOADING",
                            List.of(),
                            buildProfileTags(snap, List.of()),
                            null,
                            false,
                            false);
        };
    }

    private List<String> buildProfileTags(TenantSnapshot snap, List<DailyRecommendItemRecord> items) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (snap != null) {
            String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
            if (subjectKey != null) {
                tags.addAll(profileIngest.readInterestTags(snap.getTenantId(), subjectKey));
            }
        }
        for (DailyRecommendItemRecord r : items) {
            if (r.tag() != null && !r.tag().isBlank() && !"—".equals(r.tag().trim())) {
                tags.add(r.tag().trim());
            }
        }
        if (!tags.isEmpty()) {
            return List.copyOf(tags);
        }
        if (snap != null) {
            String hint = userProfileApplicationService.buildPromptAddendum(snap, "", false);
            if (hint != null && !hint.isBlank()) {
                String condensed = hint.replace('\n', ' ').strip();
                if (condensed.length() > 10) {
                    tags.add(condensed.substring(0, Math.min(8, condensed.length())));
                }
            }
        }
        if (tags.isEmpty()) {
            return List.of("热点", "资讯");
        }
        return List.copyOf(tags);
    }

    private static List<DailyRecommendItemView> toViews(List<DailyRecommendItemRecord> items) {
        List<DailyRecommendItemView> out = new ArrayList<>();
        int i = 0;
        for (DailyRecommendItemRecord r : items) {
            String id = "rec-" + (++i);
            out.add(
                    new DailyRecommendItemView(
                            id,
                            blankToDash(r.tag()),
                            r.title(),
                            r.summary(),
                            blankToDash(r.source()),
                            blankToDash(r.date()),
                            ChatDailyRecommendUrlSupport.normalizeHttpUrl(r.url())));
        }
        return out;
    }

    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private DailyRecommendResponse emptyResponse(
            String cacheKey, String dateStr, String status, String message) {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return new DailyRecommendResponse(
                cacheKey,
                dateStr,
                status,
                List.of(),
                buildProfileTags(snap, List.of()),
                message,
                false,
                false);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
