package com.aaron.cloud.chat.intent.spi;

import com.aaron.cloud.chat.dto.ChatIntentAdminDtos;
import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IntentHandlerPluginRegistry {

    private final Map<ChatIntentHandlerKind, ChatIntentHandlerPlugin> byKind;

    public IntentHandlerPluginRegistry(List<ChatIntentHandlerPlugin> plugins) {
        EnumMap<ChatIntentHandlerKind, ChatIntentHandlerPlugin> m = new EnumMap<>(ChatIntentHandlerKind.class);
        for (ChatIntentHandlerPlugin p : plugins) {
            ChatIntentHandlerKind k = p.kind();
            if (m.put(k, p) != null) {
                throw new IllegalStateException("Duplicate ChatIntentHandlerPlugin for kind " + k);
            }
        }
        this.byKind = Map.copyOf(m);
    }

    public Optional<ChatIntentHandlerPlugin> get(ChatIntentHandlerKind kind) {
        return Optional.ofNullable(byKind.get(kind));
    }

    /** 已注册处理器（用于管理端下拉）；展示文案取自 {@link ChatIntentHandlerKind}。 */
    public List<ChatIntentAdminDtos.IntentHandlerKindOption> listRegisteredKindOptions() {
        List<ChatIntentHandlerKind> kinds = new ArrayList<>(byKind.keySet());
        kinds.sort(Comparator.comparing(Enum::name));
        return kinds.stream()
                .map(
                        k -> new ChatIntentAdminDtos.IntentHandlerKindOption(
                                k.name(), k.getAdminLabelZh(), k.getAdminDescription()))
                .toList();
    }
}
