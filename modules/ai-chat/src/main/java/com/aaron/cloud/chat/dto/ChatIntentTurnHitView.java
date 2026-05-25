package com.aaron.cloud.chat.dto;

/**
 * 单条消息上展示的「本回合意图命中」摘要（来自 user/assistant 的 {@code meta_json}）。
 */
public record ChatIntentTurnHitView(
        long intentId,
        String intentCode,
        Long keywordId,
        String keywordPhrase,
        /** {@link com.aaron.cloud.common.api.enums.ChatIntentKeywordKind} 名；DOC/正则等无种类时为 {@code null} */
        String keywordKind,
        /** {@link com.aaron.cloud.common.api.enums.ChatIntentMatchSource} 名 */
        String matchSource,
        /** 多轮流票据；无则 {@code null} */
        String intentFlowTicket,
        String intentFlowEpisodeId,
        String intentFlowRound,
        Integer intentFlowRoundSeq) {}
