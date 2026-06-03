package com.aaron.cloud.chat.intent.routing;

import com.aaron.cloud.chat.intent.flow.IntentFlowSession;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.chat.intent.spi.ChatIntentHandlerPlugin;
import com.aaron.cloud.chat.intent.spi.IntentHandlerPluginRegistry;
import com.aaron.cloud.common.api.enums.chat.ChatIntentRouterParticipation;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 多意图路由候选筛选；规则变更只改本类。 */
@Component
public class IntentRoutingPolicies {

    public List<ChatIntentDefinition> filterCandidates(
            List<ChatIntentDefinition> sortedEnabledDefs,
            IntentMatchContext flowContext,
            IntentHandlerPluginRegistry registry) {
        if (sortedEnabledDefs == null || sortedEnabledDefs.isEmpty()) {
            return List.of();
        }
        if (!flowContext.hasValidSession()) {
            return List.copyOf(sortedEnabledDefs);
        }
        IntentFlowSession session = flowContext.session().orElseThrow();
        long pinnedId = session.getIntentDefinitionId();
        List<ChatIntentDefinition> out = new ArrayList<>();
        for (ChatIntentDefinition def : sortedEnabledDefs) {
            if (def.getId() == pinnedId) {
                out.add(def);
                continue;
            }
            var plugin = registry.get(def.getHandlerKind());
            if (plugin.isEmpty()) {
                continue;
            }
            if (plugin.get().routerParticipation() == ChatIntentRouterParticipation.ALWAYS_SCAN) {
                out.add(def);
            }
        }
        return List.copyOf(out);
    }
}
