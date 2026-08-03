package com.aaron.cloud.chat;

import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.dto.ChatShareCreateView;
import com.aaron.cloud.chat.dto.ChatSharePublicView;
import com.aaron.cloud.chat.dto.CreateChatShareRequest;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatConversationShareRepository;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.chat.entity.ChatConversationShare;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatConversationShareService {

    private static final String CODE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatApplicationService chatApplicationService;
    private final ChatConversationRepository conversationRepository;
    private final ChatConversationShareRepository shareRepository;
    private final SysTenantRepository sysTenantRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai.chat.share-expire-days:90}")
    private int shareExpireDays;

    public ChatShareCreateView createShare(long conversationId, CreateChatShareRequest body) {
        var snap = TenantContextHolder.require();
        ChatConversation conv =
                conversationRepository
                        .findById(conversationId, snap.getTenantId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        chatApplicationService.assertConversationAccess(conv);

        List<Long> requestedIds = body.getMessageIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageIds 不能为空");
        }

        List<ChatMessageView> all = chatApplicationService.listConversationMessages(conversationId);
        Map<Long, ChatMessageView> byId = new LinkedHashMap<>();
        for (ChatMessageView v : all) {
            byId.put(v.id(), v);
        }
        List<ChatMessageView> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long mid : requestedIds) {
            if (mid == null || !seen.add(mid)) {
                continue;
            }
            ChatMessageView v = byId.get(mid);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息不属于当前会话: " + mid);
            }
            picked.add(v);
        }
        if (picked.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无有效消息");
        }

        String title = conv.getTitle() != null && !conv.getTitle().isBlank() ? conv.getTitle().trim() : "对话分享";
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", title);
        ArrayNode arr = root.putArray("messages");
        for (ChatMessageView v : picked) {
            arr.add(objectMapper.valueToTree(v));
        }
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize share snapshot", e);
        }

        String code = generateShareCode();
        LocalDateTime expiresAt =
                shareExpireDays > 0 ? LocalDateTime.now().plusDays(shareExpireDays) : null;

        ChatConversationShare row = new ChatConversationShare();
        row.setTenantId(snap.getTenantId());
        row.setConversationId(conversationId);
        row.setShareCode(code);
        row.setTitle(title);
        row.setSnapshotJson(snapshotJson);
        row.setExpiresAt(expiresAt);
        shareRepository.insert(row);

        String tenantCode =
                sysTenantRepository
                        .findById(snap.getTenantId())
                        .map(t -> t.getCode())
                        .filter(c -> c != null && !c.isBlank())
                        .orElse("default");
        String sharePath = "/" + tenantCode + "/share/" + code;
        return new ChatShareCreateView(code, sharePath, expiresAt);
    }

    public ChatSharePublicView getPublicShare(String shareCode) {
        var snap = TenantContextHolder.require();
        ChatConversationShare row =
                shareRepository
                        .findActiveByCode(snap.getTenantId(), shareCode)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分享不存在或已过期"));
        try {
            JsonNode root = objectMapper.readTree(row.getSnapshotJson());
            String title = root.path("title").asText(row.getTitle());
            List<ChatMessageView> messages = new ArrayList<>();
            JsonNode arr = root.path("messages");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    messages.add(objectMapper.treeToValue(n, ChatMessageView.class));
                }
            }
            return new ChatSharePublicView(
                    title,
                    messages,
                    row.getCreatedAt(),
                    conversationRepository
                            .findById(row.getConversationId(), snap.getTenantId())
                            .map(c -> c.getPublicId())
                            .orElse(null));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "分享数据损坏");
        }
    }

    private static String generateShareCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
