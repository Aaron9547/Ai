package com.aaron.cloud.chat.intent;

/**
 * 出差报销处理器内固定轮次（与 {@code chat_intent_keyword.target_round}、会话 {@code IntentFlowSession#currentRound} 一致）。
 */
public enum TravelReimbursementRound {
    /** 材料 / 文档工作流 */
    DOC,
    /** 行程与审批编排 */
    PLAN
}
