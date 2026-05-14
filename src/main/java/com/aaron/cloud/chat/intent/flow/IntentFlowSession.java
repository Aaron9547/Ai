package com.aaron.cloud.chat.intent.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图多轮流在 Redis/本地缓存中的会话快照；对外票据为不透明 {@code flowId}（见 {@link IntentFlowSessionStore}）。
 *
 * <p>{@link #handlerStateJson} 由各 {@link com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin} 自行约定结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentFlowSession {

    private long tenantId;
    private long conversationId;
    private long intentDefinitionId;
    /** {@link com.aaron.cloud.common.api.enums.ChatIntentHandlerKind#name()} */
    private String handlerKind;
    /** 一局（从首轮触发到流结束）的唯一标识 */
    private String episodeId;
    /** 处理器内轮次枚举名，例如差旅 {@code DOC} / {@code PLAN} */
    private String currentRound;
    private long createdAtMs;
    private long updatedAtMs;
    /** 业务过期时间（与 Redis TTL 对齐或略早） */
    private long expiresAtEpochMs;
    /**
     * 本局内已完成意图 SSE 并落库的次数（每轮用户消息经 openStream 成功结束后自增），供 meta 展示「第几次」。
     */
    private int roundInteractionSeq;
    /** 与 Redis 键、客户端回传票据一致 */
    private String flowId;
    /** 插件私有状态 JSON */
    private String handlerStateJson;
}
