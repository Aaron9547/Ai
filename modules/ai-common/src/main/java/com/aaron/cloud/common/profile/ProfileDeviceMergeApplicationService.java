package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.knowledgeplanet.TenUserKnowledgeNodeRepository;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.profile.entity.TenUserDeviceLink;
import com.aaron.cloud.common.profile.entity.TenUserMemoryAbstract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册、登录或显式接口 {@code /open/v1/profile/merge-guest-device} 时，将访客设备上的会话、画像标签、分层记忆、知识星球节点归并到用户主体，并写入审计。
 * 同一用户可对<strong>不同</strong> {@code device_id} 多次归并；库表 {@code ten_user_device_link} 以 {@code (tenant_id, user_id, device_id)} 唯一，允许多条设备绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileDeviceMergeApplicationService {

    public static final String AUDIT_ACTION_MERGE = "PROFILE_DEVICE_MERGE";
    public static final String AUDIT_RESOURCE_TYPE = "ten_profile_merge";

    private final ChatConversationRepository chatConversationRepository;
    private final TenUserKnowledgeNodeRepository tenUserKnowledgeNodeRepository;
    private final TenProfileTagRepository tenProfileTagRepository;
    private final TenUserMemoryChunkRepository tenUserMemoryChunkRepository;
    private final TenUserMemoryAbstractRepository tenUserMemoryAbstractRepository;
    private final TenUserDeviceLinkRepository tenUserDeviceLinkRepository;
    private final SysAuditEventRepository sysAuditEventRepository;
    private final ObjectMapper objectMapper;
    private final UserMemoryApplicationService userMemoryApplicationService;

    @Transactional(rollbackFor = Exception.class)
    public MergeOutcome mergeGuestDeviceToUser(long tenantId, long userId, String deviceIdRaw) {
        if (deviceIdRaw == null || deviceIdRaw.isBlank()) {
            return MergeOutcome.skipped();
        }
        String deviceId = deviceIdRaw.trim();
        if (deviceId.length() > 64) {
            deviceId = deviceId.substring(0, 64);
        }
        String dKey = ProfileSubjectKey.deviceKey(deviceId);
        String uKey = ProfileSubjectKey.userKey(userId);

        int conversations = chatConversationRepository.attachGuestConversationsToUser(tenantId, deviceId, userId);
        int chunks = tenUserMemoryChunkRepository.reassignSubject(tenantId, dKey, uKey);
        int knowledgeNodes = tenUserKnowledgeNodeRepository.reassignSubject(tenantId, dKey, uKey);

        mergeProfileTags(tenantId, dKey, uKey);
        mergeAbstractRows(tenantId, dKey, uKey);
        tenProfileTagRepository.deleteByTenantAndSubjectKey(tenantId, dKey);

        var link = new TenUserDeviceLink();
        link.setTenantId(tenantId);
        link.setUserId(userId);
        link.setDeviceId(deviceId);
        tenUserDeviceLinkRepository.insertIfAbsent(link);

        writeAudit(tenantId, userId, deviceId, conversations, chunks, knowledgeNodes);
        try {
            userMemoryApplicationService.repairMilvusAfterGuestMerge(tenantId, dKey, uKey);
        } catch (Exception ex) {
            log.warn("repairMilvusAfterGuestMerge failed tenantId={} userId={}", tenantId, userId, ex);
        }
        return new MergeOutcome(conversations, chunks, knowledgeNodes, true);
    }

    private void mergeProfileTags(long tenantId, String deviceKey, String userKey) {
        List<TenProfileTag> deviceTags = tenProfileTagRepository.listByTenantAndSubjectKey(tenantId, deviceKey);
        for (TenProfileTag dt : deviceTags) {
            ProfileTagCode code = dt.getTagCode();
            var userOpt = tenProfileTagRepository.find(tenantId, userKey, code);
            if (userOpt.isEmpty()) {
                TenProfileTag copy = new TenProfileTag();
                copy.setTenantId(tenantId);
                copy.setSubjectKey(userKey);
                copy.setTagCode(code);
                copy.setTagValue(dt.getTagValue());
                tenProfileTagRepository.insert(copy);
            } else {
                TenProfileTag ut = userOpt.get();
                if (code == ProfileTagCode.TURN_COUNT) {
                    int a = parseNonNegInt(ut.getTagValue(), 0);
                    int b = parseNonNegInt(dt.getTagValue(), 0);
                    ut.setTagValue(String.valueOf(a + b));
                } else if (code == ProfileTagCode.LAST_USER_EXCERPT) {
                    String uv = ut.getTagValue() == null ? "" : ut.getTagValue();
                    String dv = dt.getTagValue() == null ? "" : dt.getTagValue();
                    ut.setTagValue(uv.length() >= dv.length() ? uv : dv);
                } else {
                    String uv = ut.getTagValue() == null ? "" : ut.getTagValue();
                    String dv = dt.getTagValue() == null ? "" : dt.getTagValue();
                    ut.setTagValue(uv.isBlank() ? dv : uv);
                }
                tenProfileTagRepository.updateById(ut);
            }
        }
    }

    private void mergeAbstractRows(long tenantId, String deviceKey, String userKey) {
        var devOpt = tenUserMemoryAbstractRepository.findByTenantAndSubject(tenantId, deviceKey);
        if (devOpt.isEmpty()) {
            return;
        }
        TenUserMemoryAbstract dev = devOpt.get();
        var usrOpt = tenUserMemoryAbstractRepository.findByTenantAndSubject(tenantId, userKey);
        if (usrOpt.isEmpty()) {
            dev.setSubjectKey(userKey);
            tenUserMemoryAbstractRepository.updateById(dev);
            return;
        }
        TenUserMemoryAbstract usr = usrOpt.get();
        try {
            usr.setBodyJson(mergeJsonPreferUserKeys(usr.getBodyJson(), dev.getBodyJson()));
            tenUserMemoryAbstractRepository.updateById(usr);
        } catch (Exception ex) {
            log.warn("memory abstract json merge failed tenantId={} userKey={}", tenantId, userKey, ex);
            usr.setBodyJson(dev.getBodyJson());
            tenUserMemoryAbstractRepository.updateById(usr);
        }
        tenUserMemoryAbstractRepository.deleteById(dev.getId());
    }

    private String mergeJsonPreferUserKeys(String userJson, String deviceJson) throws Exception {
        ObjectNode u =
                userJson == null || userJson.isBlank()
                        ? objectMapper.createObjectNode()
                        : (ObjectNode) objectMapper.readTree(userJson);
        ObjectNode d =
                deviceJson == null || deviceJson.isBlank()
                        ? objectMapper.createObjectNode()
                        : (ObjectNode) objectMapper.readTree(deviceJson);
        Iterator<String> it = d.fieldNames();
        while (it.hasNext()) {
            String k = it.next();
            if (!u.has(k)) {
                JsonNode v = d.get(k);
                if (v != null) {
                    u.set(k, v);
                }
            }
        }
        return objectMapper.writeValueAsString(u);
    }

    private static int parseNonNegInt(String raw, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void writeAudit(
            long tenantId, long userId, String deviceId, int conversations, int chunks, int knowledgeNodes) {
        try {
            ObjectNode detail = objectMapper.createObjectNode();
            detail.put("userId", userId);
            detail.put("deviceId", deviceId);
            detail.put("conversationsReassigned", conversations);
            detail.put("memoryChunksReassigned", chunks);
            detail.put("knowledgeNodesReassigned", knowledgeNodes);
            var ev = new SysAuditEvent();
            ev.setTenantId(tenantId);
            ev.setActorType("USER");
            ev.setActorId(String.valueOf(userId));
            ev.setAction(AUDIT_ACTION_MERGE);
            ev.setResourceType(AUDIT_RESOURCE_TYPE);
            ev.setResourceId(deviceId);
            ev.setDetailJson(objectMapper.writeValueAsString(detail));
            sysAuditEventRepository.insert(ev);
        } catch (Exception ex) {
            log.warn("profile merge audit insert failed tenantId={} userId={}", tenantId, userId, ex);
        }
    }

    public record MergeOutcome(
            int conversationsReassigned, int memoryChunksReassigned, int knowledgeNodesReassigned, boolean ran) {
        public static MergeOutcome skipped() {
            return new MergeOutcome(0, 0, 0, false);
        }
    }
}
