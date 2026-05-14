package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.profile.entity.TenUserMemoryChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** C 端画像与分层记忆的导出、删除（可审计）。仅支持已登录 {@code u:} 主体。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfilePrivacyApplicationService {

    public static final String AUDIT_ACTION_EXPORT = "PROFILE_DATA_EXPORT";
    public static final String AUDIT_ACTION_PURGE = "PROFILE_DATA_PURGE";

    private final TenProfileTagRepository tenProfileTagRepository;
    private final TenUserMemoryAbstractRepository tenUserMemoryAbstractRepository;
    private final TenUserMemoryChunkRepository tenUserMemoryChunkRepository;
    private final TenUserDeviceLinkRepository tenUserDeviceLinkRepository;
    private final SysAuditEventRepository sysAuditEventRepository;
    private final ObjectMapper objectMapper;

    public JsonNode exportJson(long tenantId, long userId) throws Exception {
        String sk = ProfileSubjectKey.userKey(userId);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        root.put("tenantId", tenantId);
        root.put("subjectKey", sk);
        root.put("exportedAtUtc", Instant.now().toString());
        ArrayNode tags = objectMapper.createArrayNode();
        List<TenProfileTag> tagRows = tenProfileTagRepository.listByTenantAndSubjectKey(tenantId, sk);
        for (TenProfileTag t : tagRows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("code", t.getTagCode().name());
            o.put("value", t.getTagValue());
            tags.add(o);
        }
        root.set("profileTags", tags);
        tenUserMemoryAbstractRepository
                .findByTenantAndSubject(tenantId, sk)
                .ifPresent(a -> root.put("memoryAbstractJson", a.getBodyJson()));
        ArrayNode chunks = objectMapper.createArrayNode();
        var page = tenUserMemoryChunkRepository.pageByTenantAndSubject(tenantId, sk, 1, 2000);
        for (TenUserMemoryChunk c : page.getRecords()) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", c.getId());
            if (c.getConversationId() != null) {
                o.put("conversationId", c.getConversationId());
            } else {
                o.putNull("conversationId");
            }
            o.put("snippet", c.getContentSnippet());
            o.put("chunkRole", c.getChunkRole() != null ? c.getChunkRole() : "USER");
            o.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            chunks.add(o);
        }
        root.set("memoryChunks", chunks);
        ObjectNode auditDetail = objectMapper.createObjectNode();
        auditDetail.put("memoryChunkTotal", page.getTotal());
        auditDetail.put("profileTagCount", tagRows.size());
        writeAudit(tenantId, userId, AUDIT_ACTION_EXPORT, objectMapper.writeValueAsString(auditDetail));
        return root;
    }

    @Transactional(rollbackFor = Exception.class)
    public void purgeUserProfile(long tenantId, long userId) {
        String sk = ProfileSubjectKey.userKey(userId);
        long chunkCount = tenUserMemoryChunkRepository.countByTenantAndSubject(tenantId, sk);
        tenProfileTagRepository.deleteByTenantAndSubjectKey(tenantId, sk);
        tenUserMemoryAbstractRepository.deleteByTenantAndSubject(tenantId, sk);
        tenUserMemoryChunkRepository.deleteByTenantAndSubject(tenantId, sk);
        tenUserDeviceLinkRepository.deleteByTenantAndUser(tenantId, userId);
        writeAudit(
                tenantId,
                userId,
                AUDIT_ACTION_PURGE,
                "{\"deletedMemoryChunksApprox\":" + chunkCount + "}");
    }

    private void writeAudit(long tenantId, long userId, String action, String detailJson) {
        try {
            var ev = new SysAuditEvent();
            ev.setTenantId(tenantId);
            ev.setActorType("USER");
            ev.setActorId(String.valueOf(userId));
            ev.setAction(action);
            ev.setResourceType("user_profile_privacy");
            ev.setResourceId(String.valueOf(userId));
            ev.setDetailJson(detailJson);
            sysAuditEventRepository.insert(ev);
        } catch (Exception ex) {
            log.warn("profile privacy audit failed tenantId={} userId={} action={}", tenantId, userId, action, ex);
        }
    }
}
