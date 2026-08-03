package com.aaron.cloud.chat.intent;

import com.aaron.cloud.common.api.enums.chat.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.chat.ChatIntentMatchSource;

/**
 * 本轮命中意图时的匹配细节（落库到消息 meta、并可对 {@code chat_intent_keyword.hit_count} 累加）。
 *
 * <p>多轮流票据字段见 {@link #intentFlowTicket()} 等；非多轮流命中时为 {@code null}。
 */
public record IntentKeywordMatchHit(
        Long keywordId,
        String matchedPhrase,
        /** 续办正则或 DOC 附件路径时可为 {@code null} */
        ChatIntentKeywordKind keywordKind,
        ChatIntentMatchSource matchSource,
        /** 不透明流票据（与 {@link com.aaron.cloud.chat.dto.ChatSendPayload#getIntentFlowTicket()} 对应） */
        String intentFlowTicket,
        String intentFlowEpisodeId,
        /** 处理器轮次枚举名 */
        String intentFlowRound,
        Integer intentFlowRoundSeq) {

    public static IntentKeywordMatchHit create(
            Long keywordId,
            String matchedPhrase,
            ChatIntentKeywordKind keywordKind,
            ChatIntentMatchSource matchSource) {
        return new IntentKeywordMatchHit(keywordId, matchedPhrase, keywordKind, matchSource, null, null, null, null);
    }

    public IntentKeywordMatchHit withFlowMeta(
            String ticket, String episodeId, String round, Integer roundSeq) {
        return new IntentKeywordMatchHit(
                keywordId, matchedPhrase, keywordKind, matchSource, ticket, episodeId, round, roundSeq);
    }
}
