package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ChatKnowledgePlanetDtos {

    private ChatKnowledgePlanetDtos() {}

    public record SummaryResponse(
            boolean enabled,
            long nodeCount,
            int planetCount,
            String dominantPlanetName,
            List<NodeSummaryView> recentNodes,
            String weeklySummary,
            boolean emailEligible) {

        public static SummaryResponse disabled() {
            return new SummaryResponse(false, 0, 0, "", List.of(), "", false);
        }
    }

    public record NodeSummaryView(Long id, String title, String summary, LocalDateTime createdAt) {}

    /** 三级钻取：星系（planets）+ 知识点（knowledge nodes）+ 连线。 */
    public record UniverseGraphResponse(
            List<PlanetView> planets, List<GraphNodeView> nodes, List<GraphLinkView> links) {}

    /** 主题分类星球（Galaxy 层可见的大球）。 */
    public record PlanetView(
            String id,
            String name,
            int nodeCount,
            int colorRgb,
            double displaySize,
            String summary) {}

    /**
     * @param id 图内唯一 id（planet:* 或 node:*）
     * @param kind planet | knowledge
     * @param planetId 知识点所属星球 id；星球自身为 null
     */
    public record GraphNodeView(
            String id,
            String kind,
            String planetId,
            Long knowledgeNodeId,
            String title,
            String summary,
            List<String> topicTags) {}

    /** @param kind orbit=星球→知识点；relation=知识点间 */
    public record GraphLinkView(String sourceId, String targetId, String kind) {}

    public record WeeklyLatestResponse(
            LocalDate weekStart, String status, KnowledgeWeeklyPlan plan, Boolean feedbackHelpful) {}

    public record WeeklyFeedbackBody(boolean helpful, String weekStart) {}

    public record LearningGoalBody(String goal) {}
}
