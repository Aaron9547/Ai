package com.aaron.cloud.chat.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatDailyRecommendSearchQuerySupportTest {

    @Mock private PromptTemplateResolvePort promptTemplates;
    @Mock private SecUserAccountRepository userAccountRepository;
    @Mock private ChatDailyRecommendProfileIngest profileIngest;

    private ChatDailyRecommendSearchQuerySupport support;

    @BeforeEach
    void setUp() {
        support =
                new ChatDailyRecommendSearchQuerySupport(
                        promptTemplates, userAccountRepository, profileIngest);
    }

    @Test
    void buildProfileSearchExcerpt_mergesInterestTagsBeforeMemory() {
        TenantSnapshot snap = TenantSnapshot.builder().tenantId(1L).userId(10L).build();
        when(profileIngest.readInterestTags(1L, "u:10")).thenReturn(List.of("AIGC", "大模型"));

        String excerpt =
                support.buildProfileSearchExcerpt(snap, "近期关注 Java 并发", 320);

        assertTrue(excerpt.startsWith("AIGC 大模型"));
        assertTrue(excerpt.contains("Java 并发"));
    }

    @Test
    void buildTodayPrimary_usesProfileTemplateWhenExcerptPresent() {
        TenantSnapshot snap = TenantSnapshot.builder().tenantId(1L).userId(10L).build();
        when(profileIngest.readInterestTags(1L, "u:10")).thenReturn(List.of("量子计算"));
        when(promptTemplates.renderQuery(
                        org.mockito.ArgumentMatchers.eq("daily_recommend_search_query_profile"),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("rendered");

        String query = support.buildTodayPrimary(snap, "记忆片段");

        assertEquals("rendered", query);
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(promptTemplates)
                .renderQuery(
                        org.mockito.ArgumentMatchers.eq("daily_recommend_search_query_profile"),
                        org.mockito.ArgumentMatchers.eq(1L),
                        captor.capture());
        Map<String, String> vars = captor.getValue();
        assertEquals(BeijingTime.today().toString(), vars.get("today"));
        assertEquals(
                BeijingTime.formatChineseDateLabel(BeijingTime.today()), vars.get("today_label"));
        assertTrue(vars.get("profile_excerpt").contains("量子计算"));
    }

    @Test
    void buildTodayPrimary_usesDefaultTemplateWithoutProfile() {
        TenantSnapshot snap = TenantSnapshot.builder().tenantId(1L).deviceId("d1").build();
        when(promptTemplates.renderQuery(
                        org.mockito.ArgumentMatchers.eq("daily_recommend_search_query"),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("generic");

        assertEquals("generic", support.buildTodayPrimary(snap, ""));
    }
}
