package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.chat.websearch.ChatWebSearchGroundingService;
import com.aaron.cloud.chat.websearch.WebSearchExecutionResult;
import com.aaron.cloud.chat.websearch.WebSearchGroundingPlanResolver;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.util.TextClamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyBookSearchSupport {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final WebSearchGroundingPlanResolver webSearchGroundingPlanResolver;
    private final ChatWebSearchGroundingService webSearchGroundingService;

    public record BookSearchContext(String query, String summaryBlock, List<WebSearchReference> references) {}

    public BookSearchContext searchIfEnabled(TenantSnapshot snap, String topPlanetOrGap) {
        long tenantId = snap.getTenantId();
        if (!planetRuntime.isWeeklyBookSearchEnabled(tenantId)) {
            return empty("");
        }
        if (!webSearchGroundingPlanResolver.isAvailable(tenantId)) {
            return empty("");
        }
        String topic = topPlanetOrGap == null ? "" : topPlanetOrGap.trim();
        if (topic.isEmpty()) {
            return empty("");
        }
        String query = topic + " 书籍 推荐 入门";
        try {
            WebSearchExecutionResult result =
                    webSearchGroundingService.groundWithRaw(
                            snap, query, 0L, ChatStarterPromptSource.KNOWLEDGE_PLANET_WEEKLY);
            String summary =
                    result.bundle().summaryText() == null ? "" : result.bundle().summaryText().trim();
            List<WebSearchReference> refs =
                    result.bundle().references() == null ? List.of() : result.bundle().references();
            if (summary.isBlank() && refs.isEmpty()) {
                return new BookSearchContext(query, "", List.of());
            }
            String block = formatBlock(query, summary, refs);
            return new BookSearchContext(query, block, refs);
        } catch (Exception ex) {
            log.warn("[知识星球] 周报书目检索失败 tenantId={} query={}", tenantId, query, ex);
            return new BookSearchContext(query, "", List.of());
        }
    }

    private static BookSearchContext empty(String query) {
        return new BookSearchContext(query, "", List.of());
    }

    private static String formatBlock(String query, String summary, List<WebSearchReference> refs) {
        var sb = new StringBuilder("【联网书目摘要】\n");
        sb.append("检索词：").append(query).append('\n');
        if (!summary.isBlank()) {
            sb.append(TextClamp.ellipsis(summary, 600)).append('\n');
        }
        int n = 0;
        for (WebSearchReference r : refs) {
            if (n >= 6) {
                break;
            }
            String title = r.title() == null ? "" : r.title().trim();
            if (title.isEmpty()) {
                continue;
            }
            sb.append("- ").append(title);
            String snip = r.snippet() == null ? "" : r.snippet().trim();
            if (!snip.isBlank()) {
                sb.append("：").append(TextClamp.ellipsis(snip, 80));
            }
            sb.append('\n');
            n++;
        }
        return sb.toString().trim();
    }
}
