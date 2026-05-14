package com.aaron.cloud.chat.intent.flow;

import java.util.Optional;

/**
 * 意图关键词判定阶段携带的流会话上下文（由路由层根据请求体中的票据解析）。
 */
public record IntentMatchContext(Optional<IntentFlowSession> session, String rawFlowTicket) {

    public static IntentMatchContext empty() {
        return new IntentMatchContext(Optional.empty(), null);
    }

    public boolean hasValidSession() {
        return session.isPresent();
    }
}
