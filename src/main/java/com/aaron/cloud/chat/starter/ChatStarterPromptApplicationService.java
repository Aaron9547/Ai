package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.enums.ChatStarterEventType;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.chat.ChatStarterEventRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterEvent;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
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

    private static final List<String> CODE_FALLBACK =
            List.of(
                    "写一首关于春天的诗",
                    "用通俗语言解释量子纠缠",
                    "帮我生成一份周报模板");

    private final ChatStarterPromptRepository promptRepository;
    private final ChatStarterEventRepository eventRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final ChatStarterDailyHotTopicService dailyHotTopicService;

    public ChatStarterPromptDtos.StarterPromptListView listForOpen(
            ChatStarterPromptScene scene,
            int limit,
            boolean refresh,
            Set<Long> excludeIds,
            boolean thinkingEnabled,
            boolean webSearchEnabled) {
        var snap = TenantContextHolder.require();
        long tenantId = snap.getTenantId();
        int cap = Math.max(1, Math.min(limit, 12));

        if (scene == ChatStarterPromptScene.EMPTY && refresh) {
            dailyHotTopicService.refreshForTenant(tenantId, false);
        }

        List<ChatStarterPrompt> pool =
                promptRepository.listEnabledForRuntime(tenantId, scene, BeijingTime.today());
        boolean webAvail = llmModelRepository.hasEnabledWebSearchModel(tenantId);
        List<ChatStarterPrompt> filtered =
                pool.stream()
                        .filter(p -> matchesCaps(p, thinkingEnabled, webSearchEnabled, webAvail))
                        .toList();

        Set<Long> exclude = excludeIds == null ? Set.of() : excludeIds;
        List<ChatStarterPrompt> picked = ChatStarterPromptSampler.sample(filtered, cap, exclude);

        if (picked.isEmpty()) {
            List<String> fallbackTexts =
                    CODE_FALLBACK.stream().limit(cap).collect(Collectors.toList());
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
                        .map(
                                p ->
                                        new ChatStarterPromptDtos.StarterPromptItem(
                                                p.getId(),
                                                p.getPromptText(),
                                                p.getSource() == null
                                                        ? "MANUAL"
                                                        : p.getSource().getCode()))
                        .toList();
        recordImpressions(snap, scene, picked);
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
            TenantContextHolder.TenantSnapshot snap,
            ChatStarterPromptScene scene,
            List<ChatStarterPrompt> picked) {
        for (ChatStarterPrompt p : picked) {
            if (p.getId() == null) {
                continue;
            }
            var row = new ChatStarterEvent();
            row.setTenantId(snap.getTenantId());
            row.setUserId(snap.getUserId());
            row.setDeviceId(snap.getDeviceId());
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
