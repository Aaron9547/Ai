package com.aaron.cloud.chat.intent;

import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.LnkChatConversationMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/** 一句话提醒：取消选号续轮是否已开启（助手 meta {@code reminderCancelSelectionPending}）。 */
@UtilityClass
public final class ReminderCancelSelectionGate {

    public static final String META_CANCEL_SELECTION_PENDING = "reminderCancelSelectionPending";

    public static boolean isAwaitingSelection(
            long conversationId,
            long tenantId,
            ChatMessageRepository messageRepository,
            LnkChatConversationMessageRepository lnkRepository,
            ObjectMapper objectMapper) {
        List<Long> ids = lnkRepository.listMessageIdsByConversationOrderByLinkIdAsc(conversationId);
        for (int i = ids.size() - 1; i >= 0; i--) {
            Optional<ChatMessage> opt = messageRepository.findById(ids.get(i), tenantId);
            if (opt.isEmpty()) {
                continue;
            }
            ChatMessage m = opt.get();
            if (m.getRole() == ChatMessageRole.USER) {
                continue;
            }
            if (m.getRole() != ChatMessageRole.ASSISTANT) {
                continue;
            }
            return parsePending(m.getMetaJson(), objectMapper);
        }
        return false;
    }

    static boolean parsePending(String metaJson, ObjectMapper objectMapper) {
        if (metaJson == null || metaJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            return root.path(META_CANCEL_SELECTION_PENDING).asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }
}
