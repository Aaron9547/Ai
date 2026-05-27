package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.enums.chat.ChatStarterEventType;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.chat.ChatStarterEventRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterEvent;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.chat.websearch.WebSearchGroundingPlanResolver;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.chat.prompt.ChatPromptTemplateSupport;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.time.BeijingTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatStarterPromptApplicationService {

    private final ChatStarterPromptRepository promptRepository;
    private final ChatStarterEventRepository eventRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final WebSearchGroundingPlanResolver webSearchGroundingPlanResolver;
    private final ChatStarterDailyHotTopicService dailyHotTopicService;
    private final ChatPromptTemplateSupport chatPromptTemplateSupport;

    public ChatStarterPromptDtos.StarterPromptListView listForOpen(
            ChatStarterPromptScene scene,
            int limit,
            boolean refresh,
            Set<Long> excludeIds,
            boolean thinkingEnabled,
            boolean webSearchEnabled) {
        var snap = TenantContextHolder.require();
        return listForTenant(
                snap.getTenantId(),
                snap.getUserId(),
                snap.getDeviceId(),
                scene,
                limit,
                refresh,
                excludeIds,
                thinkingEnabled,
                webSearchEnabled);
    }

    /**
     * 开放接口推荐问句（显式租户，供流式虚拟线程等无 {@link TenantContextHolder} 场景调用）。
     */
    public ChatStarterPromptDtos.StarterPromptListView listForTenant(
            long tenantId,
            Long userId,
            String deviceId,
            ChatStarterPromptScene scene,
            int limit,
            boolean refresh,
            Set<Long> excludeIds,
            boolean thinkingEnabled,
            boolean webSearchEnabled) {
        int cap = Math.max(1, Math.min(limit, 12));

        if (scene == ChatStarterPromptScene.EMPTY && refresh) {
            Thread.startVirtualThread(() -> dailyHotTopicService.refreshForTenant(tenantId, false));
        }

        List<ChatStarterPrompt> pool =
                promptRepository.listEnabledForRuntime(tenantId, scene, BeijingTime.today());
        boolean webAvail = webSearchGroundingPlanResolver.isAvailable(tenantId);
        List<ChatStarterPrompt> filtered =
                pool.stream()
                        .filter(p -> matchesCaps(p, thinkingEnabled, webSearchEnabled, webAvail))
                        .toList();

        Set<Long> exclude = excludeIds == null ? Set.of() : excludeIds;
        List<ChatStarterPrompt> picked = ChatStarterPromptSampler.sample(filtered, cap, exclude);

        if (picked.isEmpty()) {
            List<String> fallbackTexts =
                    chatPromptTemplateSupport.starterEmptyFallbacks(tenantId).stream()
                            .limit(cap)
                            .collect(Collectors.toList());
            List<ChatStarterPromptDtos.StarterPromptItem> items =
                    fallbackTexts.stream()
                            .map(
                                    t ->
                                            new ChatStarterPromptDtos.StarterPromptItem(
                                                    null, t, "FALLBACK"))
                            .toList();
            return new ChatStarterPromptDtos.StarterPromptListView(items, true);
        }

        List<ChatStarterPromptDtos.StarterPromptItem> items =
                picked.stream()
                        .filter(
                                p ->
                                        ChatStarterFollowUpTextSupport.isDisplayableChip(
                                                p.getPromptText()))
                        .map(
                                p ->
                                        new ChatStarterPromptDtos.StarterPromptItem(
                                                p.getId(),
                                                p.getPromptText(),
                                                p.getSource() == null
                                                        ? "MANUAL"
                                                        : p.getSource().getCode()))
                        .toList();
        if (userId != null || deviceId != null) {
            recordImpressions(tenantId, userId, deviceId, scene, picked);
        }
        return new ChatStarterPromptDtos.StarterPromptListView(items, false);
    }

    public void recordEvent(
            Long promptId,
            ChatStarterPromptScene scene,
            ChatStarterEventType eventType) {
        var snap = TenantContextHolder.require();
        var row = new ChatStarterEvent();
        row.setTenantId(snap.getTenantId());
        row.setUserId(snap.getUserId());
        row.setDeviceId(snap.getDeviceId());
        row.setPromptId(promptId);
        row.setScene(scene);
        row.setEventType(eventType);
        eventRepository.insert(row);
    }

    private void recordImpressions(
            long tenantId,
            Long userId,
            String deviceId,
            ChatStarterPromptScene scene,
            List<ChatStarterPrompt> picked) {
        for (ChatStarterPrompt p : picked) {
            if (p.getId() == null) {
                continue;
            }
            var row = new ChatStarterEvent();
            row.setTenantId(tenantId);
            row.setUserId(userId);
            row.setDeviceId(deviceId);
            row.setPromptId(p.getId());
            row.setScene(scene);
            row.setEventType(ChatStarterEventType.IMPRESSION);
            eventRepository.insert(row);
        }
    }

    private static boolean matchesCaps(
            ChatStarterPrompt p,
            boolean thinkingEnabled,
            boolean webSearchEnabled,
            boolean webAvail) {
        if (p.getRequireThinking() != null && p.getRequireThinking() == 1 && !thinkingEnabled) {
            return false;
        }
        if (p.getRequireWebSearch() != null && p.getRequireWebSearch() == 1) {
            return webSearchEnabled && webAvail;
        }
        return true;
    }

    public static Set<Long> parseExcludeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(raw);
    }
}
