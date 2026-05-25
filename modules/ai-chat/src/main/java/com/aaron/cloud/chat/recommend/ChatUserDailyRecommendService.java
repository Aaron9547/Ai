package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendClickBody;
import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendItemView;
import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendResponse;
import com.aaron.cloud.chat.recommend.ChatDailyRecommendJsonSupport.DailyRecommendItemRecord;
import com.aaron.cloud.chat.websearch.ChatWebSearchGroundingService;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.chat.ChatStarterDailyBatchStatus;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.chat.ChatUserDailyRecommendRepository;
import com.aaron.cloud.common.chat.entity.ChatUserDailyRecommend;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.UserProfileApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String STRUCTURE_SYSTEM =
            """
            你是资讯推荐编辑。根据联网检索摘要与用户画像，输出今日个性化资讯卡片列表。
            只输出 JSON 数组，不要 markdown，不要解释。每项字段：
            tag（领域标签，2～8字）、title（标题，12～48字）、summary（摘要，24～120字）、
            source（来源媒体名）、date（发布日期 yyyy-MM-dd，未知可写今日）、url（可点击链接，须 http/https）。
            共 5～8 条，内容不重复、与画像相关；若无画像则输出通用热点资讯。
            示例：[{"tag":"科技","title":"…","summary":"…","source":"新华网","date":"2026-05-21","url":"https://…"}]
            """;

    private final ChatUserDailyRecommendRepository recommendRepository;
    private final ChatDailyRecommendJsonSupport jsonSupport;
    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ChatWebSearchGroundingService webSearchGroundingService;
    private final ModelInvokePort modelInvokePort;
    private final UserProfileApplicationService userProfileApplicationService;
    private final ChatDailyRecommendProfileIngest profileIngest;

    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> generationInflight = new ConcurrentHashMap<>();

    public DailyRecommendResponse getOrGenerateForCurrentSubject() {
        return getOrGenerate(false, false);
    }

    public DailyRecommendResponse retryForCurrentSubject() {
        return getOrGenerate(true, false);
    }

    /** 登录后按用户主体强制重新生成（访客阶段推荐不含画像，登录后须换一批）。 */
    public DailyRecommendResponse regenerateForLoggedInUser() {
        TenantSnapshot snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
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
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
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
                    if (row.getStatus() != ChatStarterDailyBatchStatus.PENDING
                            || generationInflight.containsKey(lockKey)) {
                        return toResponse(row);
                    }
                } else {
                    if (row.getStatus() != ChatStarterDailyBatchStatus.FAILED) {
                        return toResponse(row);
                    }
                    if (row.getRetryUsed() != null && row.getRetryUsed() == 1) {
                        return toResponse(row);
                    }
                    row.setRetryUsed(1);
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

    private void generateAndPersist(TenantSnapshot snap, String subjectKey, ChatUserDailyRecommend batch) {
        long tenantId = snap.getTenantId();
        SysLlmModel webModel =
                llmModelRepository
                        .resolveWebSearchModel(
                                tenantId,
                                tenantRuntimeSettingApplicationService.webSearchGroundingModelId(tenantId))
                        .orElse(null);
        if (webModel == null) {
            failBatch(batch, "租户未配置可用的联网搜索模型");
            return;
        }

        String profileHint =
                userProfileApplicationService.buildPromptAddendum(snap, "今日资讯推荐", false);
        String searchQuery = buildSearchQuery(profileHint);
        try {
            var grounding =
                    webSearchGroundingService.groundWithRaw(snap, webModel, searchQuery, 0L);
            String summary =
                    grounding.bundle().summaryText() == null
                            ? ""
                            : grounding.bundle().summaryText().trim();
            if (summary.isBlank()) {
                failBatch(batch, "联网检索未返回可用摘要");
                return;
            }

            List<DailyRecommendItemRecord> items = structureItems(tenantId, profileHint, summary);
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

    private List<DailyRecommendItemRecord> structureItems(
            long tenantId, String profileHint, String webSummary) throws Exception {
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

        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(STRUCTURE_SYSTEM);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        StringBuilder body = new StringBuilder();
        body.append("【联网检索摘要】\n").append(webSummary);
        if (profileHint != null && !profileHint.isBlank()) {
            body.append("\n\n【用户画像与记忆】\n").append(profileHint.trim());
        }
        user.setContent(body.toString());
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(lang.getAlias());
        req.setThinkingEnabled(false);
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return jsonSupport.parseItems(acc.toString());
    }

    private static String buildSearchQuery(String profileHint) {
        if (profileHint != null && !profileHint.isBlank()) {
            String excerpt = profileHint.length() > 200 ? profileHint.substring(0, 200) : profileHint;
            return "今日最新资讯 热点新闻 与以下用户兴趣相关："
                    + excerpt.replace('\n', ' ')
                    + " 2026";
        }
        return "今日中国 科技 财经 教育 社会 校园 热点资讯 最新 2026";
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
                            r.url()));
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
