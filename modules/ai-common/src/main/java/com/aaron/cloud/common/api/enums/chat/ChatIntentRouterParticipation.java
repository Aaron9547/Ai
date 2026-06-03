package com.aaron.cloud.common.api.enums.chat;

/**
 * 意图在路由候选筛选中的参与方式；规则真源见 {@code IntentRoutingPolicies}。
 */
public enum ChatIntentRouterParticipation {
    SCAN_WITH_OTHERS,
    FLOW_PINNED,
    ALWAYS_SCAN
}
