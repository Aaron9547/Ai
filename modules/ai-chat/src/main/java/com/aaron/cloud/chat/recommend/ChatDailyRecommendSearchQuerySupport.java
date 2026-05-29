package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.web.LoginRegionSearchPhrase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 今日智能洞察：联网检索词（本日/昨日、属地与用户画像关键词）。 */
@Component
@RequiredArgsConstructor
public class ChatDailyRecommendSearchQuerySupport {

    private static final int PROFILE_EXCERPT_MAX = 320;
    private static final int PROFILE_EXCERPT_YESTERDAY_MAX = 200;

    private final PromptTemplateResolvePort promptTemplates;
    private final SecUserAccountRepository userAccountRepository;
    private final ChatDailyRecommendProfileIngest profileIngest;

    /** 优先本日资讯的检索词。 */
    public String buildTodayPrimary(TenantSnapshot snap, String profileHint) {
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        String regionPhrase = resolveRegionPhrase(snap);
        String profileExcerpt = buildProfileSearchExcerpt(snap, profileHint, PROFILE_EXCERPT_MAX);
        Map<String, String> vars = baseDateVars(today, yesterday, regionPhrase);
        if (!profileExcerpt.isBlank()) {
            vars.put("profile_excerpt", profileExcerpt);
            return promptTemplates.renderQuery(
                    "daily_recommend_search_query_profile", snap.getTenantId(), vars);
        }
        return promptTemplates.renderQuery(
                "daily_recommend_search_query", snap.getTenantId(), vars);
    }

    /** 次优先昨日资讯的补充检索词（清晨批跑时昨日白天新闻更全）。 */
    public String buildYesterdaySecondary(TenantSnapshot snap, String profileHint) {
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        String regionPhrase = resolveRegionPhrase(snap);
        String profileExcerpt =
                buildProfileSearchExcerpt(snap, profileHint, PROFILE_EXCERPT_YESTERDAY_MAX);
        Map<String, String> vars = baseDateVars(today, yesterday, regionPhrase);
        if (!profileExcerpt.isBlank()) {
            vars.put("profile_excerpt", profileExcerpt);
            return promptTemplates.renderQuery(
                    "daily_recommend_search_query_yesterday_profile", snap.getTenantId(), vars);
        }
        return promptTemplates.renderQuery(
                "daily_recommend_search_query_yesterday", snap.getTenantId(), vars);
    }

    private Map<String, String> baseDateVars(
            LocalDate today, LocalDate yesterday, String regionPhrase) {
        Map<String, String> vars = new HashMap<>();
        vars.put("region_phrase", regionPhrase);
        vars.put("today", today.toString());
        vars.put("yesterday", yesterday.toString());
        vars.put("today_label", BeijingTime.formatChineseDateLabel(today));
        return vars;
    }

    /**
     * 合并画像记忆与资讯点击兴趣标签，供检索词个性化；兴趣标签优先（更贴近「推什么」）。
     */
    String buildProfileSearchExcerpt(TenantSnapshot snap, String profileHint, int maxLen) {
        StringBuilder sb = new StringBuilder();
        if (snap != null) {
            String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
            if (subjectKey != null) {
                List<String> interestTags =
                        profileIngest.readInterestTags(snap.getTenantId(), subjectKey);
                if (!interestTags.isEmpty()) {
                    sb.append(String.join(" ", interestTags));
                }
            }
        }
        if (profileHint != null && !profileHint.isBlank()) {
            String normalized = profileHint.replace('\n', ' ').strip();
            if (!normalized.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(normalized);
            }
        }
        String raw = sb.toString().strip();
        if (raw.isBlank()) {
            return "";
        }
        return raw.length() > maxLen ? raw.substring(0, maxLen) : raw;
    }

    private String resolveRegionPhrase(TenantSnapshot snap) {
        if (snap == null || snap.getUserId() == null) {
            return "";
        }
        return userAccountRepository
                .findById(snap.getUserId())
                .map(u -> LoginRegionSearchPhrase.toSearchPhrase(u.getLastLoginRegion()))
                .orElse("");
    }
}
