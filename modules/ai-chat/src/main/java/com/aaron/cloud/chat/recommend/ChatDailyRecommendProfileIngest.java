package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenProfileTagRepository;
import com.aaron.cloud.common.profile.UserMemoryApplicationService;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 画像推荐资讯点击写入 {@code ten_profile_tag} 与分层记忆；失败仅记日志。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDailyRecommendProfileIngest {

    private static final int MAX_INTEREST_ENTRIES = 20;

    private final TenProfileTagRepository tenProfileTagRepository;
    private final UserMemoryApplicationService userMemoryApplicationService;
    private final ObjectMapper objectMapper;

    public void ingestClick(
            TenantSnapshot snap, String itemId, String tag, String title, String summary, String url) {
        if (snap == null) {
            return;
        }
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null) {
            return;
        }
        long tenantId = snap.getTenantId();
        try {
            appendInterestNewsJson(tenantId, subjectKey, itemId, tag, title, summary, url);
            userMemoryApplicationService.afterInterestNewsClick(snap, title, tag, url);
        } catch (Exception ex) {
            log.warn(
                    "daily recommend profile ingest failed tenantId={} subject={} itemId={}",
                    tenantId,
                    subjectKey,
                    itemId,
                    ex);
        }
    }

    public List<String> readInterestTags(long tenantId, String subjectKey) {
        return tenProfileTagRepository
                .find(tenantId, subjectKey, ProfileTagCode.INTEREST_NEWS_JSON)
                .map(t -> parseTagLabels(t.getTagValue()))
                .orElse(List.of());
    }

    private void appendInterestNewsJson(
            long tenantId,
            String subjectKey,
            String itemId,
            String tag,
            String title,
            String summary,
            String url) throws Exception {
        List<Map<String, String>> list = new ArrayList<>();
        var existing = tenProfileTagRepository.find(tenantId, subjectKey, ProfileTagCode.INTEREST_NEWS_JSON);
        if (existing.isPresent() && existing.get().getTagValue() != null && !existing.get().getTagValue().isBlank()) {
            try {
                list.addAll(
                        objectMapper.readValue(
                                existing.get().getTagValue(), new TypeReference<List<Map<String, String>>>() {}));
            } catch (Exception ignored) {
                list.clear();
            }
        }
        String normUrl = url == null ? "" : url.trim();
        list.removeIf(
                e ->
                        normUrl.length() > 0
                                && normUrl.equals(e.getOrDefault("url", "").trim()));
        var entry =
                Map.of(
                        "itemId", itemId == null ? "" : itemId,
                        "tag", tag == null ? "" : tag.trim(),
                        "title", title == null ? "" : title.trim(),
                        "summary", summary == null ? "" : summary.trim(),
                        "url", normUrl);
        list.add(0, entry);
        while (list.size() > MAX_INTEREST_ENTRIES) {
            list.remove(list.size() - 1);
        }
        String json = objectMapper.writeValueAsString(list);
        if (existing.isPresent()) {
            TenProfileTag u = existing.get();
            u.setTagValue(json);
            tenProfileTagRepository.updateById(u);
        } else {
            var n = new TenProfileTag();
            n.setTenantId(tenantId);
            n.setSubjectKey(subjectKey);
            n.setTagCode(ProfileTagCode.INTEREST_NEWS_JSON);
            n.setTagValue(json);
            tenProfileTagRepository.insert(n);
        }
    }

    private List<String> parseTagLabels(String json) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        try {
            List<Map<String, String>> list =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            for (Map<String, String> e : list) {
                String t = e.getOrDefault("tag", "").trim();
                if (!t.isBlank()) {
                    tags.add(t);
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(tags);
    }
}
