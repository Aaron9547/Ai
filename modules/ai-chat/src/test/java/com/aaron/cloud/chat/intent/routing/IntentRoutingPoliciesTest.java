package com.aaron.cloud.chat.intent.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.chat.intent.flow.IntentFlowSession;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin;
import com.aaron.cloud.chat.intent.spi.IntentHandlerPluginRegistry;
import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.chat.ChatIntentRouterParticipation;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntentRoutingPoliciesTest {

    private final IntentRoutingPolicies policies = new IntentRoutingPolicies();

    @Test
    void noFlow_returnsAll() {
        ChatIntentDefinition a = def(1L, ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT);
        ChatIntentDefinition b = def(2L, ChatIntentHandlerKind.ONE_SENTENCE_REMINDER);
        var registry = registry(a, b);
        var out = policies.filterCandidates(List.of(a, b), IntentMatchContext.empty(), registry);
        assertEquals(2, out.size());
    }

    @Test
    void flowPinned_includesPinnedAndAlwaysScan() {
        ChatIntentDefinition travel = def(1L, ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT);
        ChatIntentDefinition reminder = def(2L, ChatIntentHandlerKind.ONE_SENTENCE_REMINDER);
        IntentFlowSession sess =
                IntentFlowSession.builder()
                        .tenantId(1L)
                        .conversationId(10L)
                        .intentDefinitionId(1L)
                        .flowId("f1")
                        .expiresAtEpochMs(System.currentTimeMillis() + 60_000)
                        .build();
        var ctx = new IntentMatchContext(Optional.of(sess), "f1");
        var registry = registry(travel, reminder);
        var out = policies.filterCandidates(List.of(travel, reminder), ctx, registry);
        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(d -> d.getId() == 1L));
        assertTrue(out.stream().anyMatch(d -> d.getId() == 2L));
    }

    private static ChatIntentDefinition def(long id, ChatIntentHandlerKind kind) {
        var d = new ChatIntentDefinition();
        d.setId(id);
        d.setHandlerKind(kind);
        d.setEnabled(ToggleState.ON);
        return d;
    }

    private static IntentHandlerPluginRegistry registry(ChatIntentDefinition... defs) {
        var plugins =
                java.util.Arrays.stream(defs)
                        .map(
                                d -> {
                                    if (d.getHandlerKind() == ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT) {
                                        return stub(
                                                d.getHandlerKind(), ChatIntentRouterParticipation.FLOW_PINNED);
                                    }
                                    return stub(d.getHandlerKind(), ChatIntentRouterParticipation.ALWAYS_SCAN);
                                })
                        .toList();
        return new IntentHandlerPluginRegistry(plugins);
    }

    private static ChatIntentHandlerPlugin stub(
            ChatIntentHandlerKind kind, ChatIntentRouterParticipation participation) {
        return new ChatIntentHandlerPlugin() {
            @Override
            public ChatIntentHandlerKind kind() {
                return kind;
            }

            @Override
            public ChatIntentRouterParticipation routerParticipation() {
                return participation;
            }
        };
    }
}
