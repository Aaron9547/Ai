package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.web.LoginRegionSearchPhrase;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 今日智能洞察：联网检索词（含本日/昨日与登录属地）。 */
@Component
@RequiredArgsConstructor
public class ChatDailyRecommendSearchQuerySupport {

    private final PromptTemplateResolvePort promptTemplates;
    private final SecUserAccountRepository userAccountRepository;

    /** 优先本日资讯的检索词。 */
    public String buildTodayPrimary(TenantSnapshot snap, String profileHint) {
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        String regionPhrase = resolveRegionPhrase(snap);
        if (profileHint != null && !profileHint.isBlank()) {
            String excerpt = profileHint.length() > 200 ? profileHint.substring(0, 200) : profileHint;
            return promptTemplates.renderQuery(
                    "daily_recommend_search_query_profile",
                    snap.getTenantId(),
                    Map.of(
                            "region_phrase",
                            regionPhrase,
                            "today",
                            today.toString(),
                            "yesterday",
                            yesterday.toString(),
                            "profile_excerpt",
                            excerpt.replace('\n', ' ')));
        }
        return promptTemplates.renderQuery(
                "daily_recommend_search_query",
                snap.getTenantId(),
                Map.of(
                        "region_phrase",
                        regionPhrase,
                        "today",
                        today.toString(),
                        "yesterday",
                        yesterday.toString()));
    }

    /** 次优先昨日资讯的补充检索词（清晨批跑时昨日白天新闻更全）。 */
    public String buildYesterdaySecondary(TenantSnapshot snap, String profileHint) {
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        String regionPhrase = resolveRegionPhrase(snap);
        if (profileHint != null && !profileHint.isBlank()) {
            String excerpt = profileHint.length() > 120 ? profileHint.substring(0, 120) : profileHint;
            return promptTemplates.renderQuery(
                    "daily_recommend_search_query_yesterday_profile",
                    snap.getTenantId(),
                    Map.of(
                            "region_phrase",
                            regionPhrase,
                            "yesterday",
                            yesterday.toString(),
                            "profile_excerpt",
                            excerpt.replace('\n', ' ')));
        }
        return promptTemplates.renderQuery(
                "daily_recommend_search_query_yesterday",
                snap.getTenantId(),
                Map.of("region_phrase", regionPhrase, "yesterday", yesterday.toString()));
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
