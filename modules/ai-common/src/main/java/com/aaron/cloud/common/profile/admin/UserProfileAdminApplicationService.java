package com.aaron.cloud.common.profile.admin;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import com.aaron.cloud.common.profile.TenProfileTagRepository;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.profile.TenUserMemoryChunkRepository;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.profile.entity.TenUserMemoryChunk;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.PutItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 管理端：租户内用户画像列表、详情与记忆向量化模型（{@code ten_runtime_setting}）维护。 */
@Service
@RequiredArgsConstructor
public class UserProfileAdminApplicationService {

    private final SysTenantMemberRepository tenantMemberRepository;
    private final SecUserAccountRepository userAccountRepository;
    private final TenProfileTagRepository tenProfileTagRepository;
    private final TenUserMemoryAbstractRepository tenUserMemoryAbstractRepository;
    private final TenUserMemoryChunkRepository tenUserMemoryChunkRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final SysLlmModelRepository sysLlmModelRepository;
    private final ObjectMapper objectMapper;

    public UserProfilePageResult listSummaries(long tenantId, long page, long size, String keyword) {
        long pageNo = Math.max(1, page);
        long pageSize = Math.min(100, Math.max(1, size));
        var p = tenantMemberRepository.pageByTenantAndUserKeyword(tenantId, pageNo, pageSize, keyword);
        List<UserProfileSummaryRow> rows = new ArrayList<>();
        for (SysTenantMember m : p.getRecords()) {
            SecUserAccount u = userAccountRepository.findById(m.getUserId()).orElse(null);
            if (u == null) {
                continue;
            }
            String sk = ProfileSubjectKey.userKey(u.getId());
            long tagCount = tenProfileTagRepository.countByTenantAndSubject(tenantId, sk);
            boolean hasAbstract = tenUserMemoryAbstractRepository.findByTenantAndSubject(tenantId, sk).isPresent();
            long chunkCount = tenUserMemoryChunkRepository.countByTenantAndSubject(tenantId, sk);
            rows.add(
                    new UserProfileSummaryRow(
                            u.getId(),
                            u.getLoginName(),
                            u.getDisplayName(),
                            tagCount,
                            hasAbstract,
                            chunkCount));
        }
        return new UserProfilePageResult(rows, p.getTotal(), pageNo, pageSize);
    }

    public JsonNode getUserProfileDetail(long tenantId, long userId) {
        tenantMemberRepository
                .find(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不在当前租户"));
        SecUserAccount u =
                userAccountRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
        String sk = ProfileSubjectKey.userKey(userId);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("userId", userId);
        root.put("loginName", u.getLoginName() == null ? "" : u.getLoginName());
        root.put("displayName", u.getDisplayName() == null ? "" : u.getDisplayName());
        ArrayNode tags = objectMapper.createArrayNode();
        for (TenProfileTag t : tenProfileTagRepository.listByTenantAndSubjectKey(tenantId, sk)) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("code", t.getTagCode().name());
            o.put("value", t.getTagValue() == null ? "" : t.getTagValue());
            tags.add(o);
        }
        root.set("profileTags", tags);
        tenUserMemoryAbstractRepository
                .findByTenantAndSubject(tenantId, sk)
                .ifPresentOrElse(
                        a -> {
                            try {
                                root.set("memoryAbstract", objectMapper.readTree(a.getBodyJson()));
                            } catch (Exception e) {
                                root.put("memoryAbstractRaw", a.getBodyJson());
                            }
                        },
                        () -> root.putNull("memoryAbstract"));
        long chunkTotal = tenUserMemoryChunkRepository.countByTenantAndSubject(tenantId, sk);
        root.put("memoryChunkTotal", chunkTotal);
        ArrayNode recent = objectMapper.createArrayNode();
        for (TenUserMemoryChunk c : tenUserMemoryChunkRepository.listRecent(tenantId, sk, 30)) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", c.getId());
            o.put("chunkRole", c.getChunkRole() == null ? "USER" : c.getChunkRole());
            o.put("snippet", c.getContentSnippet() == null ? "" : c.getContentSnippet());
            if (c.getConversationId() != null) {
                o.put("conversationId", c.getConversationId());
            } else {
                o.putNull("conversationId");
            }
            if (c.getCreatedAt() != null) {
                o.put("createdAt", c.getCreatedAt().toString());
            }
            recent.add(o);
        }
        root.set("recentMemoryChunks", recent);
        return root;
    }

    public MemoryEmbeddingModelSettingView getMemoryEmbeddingSetting(long tenantId) {
        Optional<Long> selected = tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId);
        List<VectorModelOption> options = new ArrayList<>();
        for (SysLlmModel m : sysLlmModelRepository.listAllForAdmin(tenantId, LlmModelKind.VECTOR)) {
            options.add(
                    new VectorModelOption(
                            m.getId(),
                            m.getAlias() == null ? "" : m.getAlias(),
                            m.getDisplayName() == null ? "" : m.getDisplayName(),
                            m.getStatus() == LlmModelStatus.ACTIVE));
        }
        return new MemoryEmbeddingModelSettingView(selected.orElse(null), options);
    }

    public void putMemoryEmbeddingModel(long tenantId, Long llmModelIdOrNull) {
        if (llmModelIdOrNull == null) {
            tenantRuntimeSettingApplicationService.replace(
                    tenantId,
                    List.of(
                            newPutItem(
                                    TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID.getStorage(),
                                    "")));
            return;
        }
        SysLlmModel m =
                sysLlmModelRepository
                        .findById(tenantId, llmModelIdOrNull)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型不存在"));
        LlmModelKind k = m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.VECTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "须选择 VECTOR 类型模型");
        }
        if (m.getStatus() != LlmModelStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型须为启用状态");
        }
        tenantRuntimeSettingApplicationService.replace(
                tenantId,
                List.of(
                        newPutItem(
                                TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID.getStorage(),
                                String.valueOf(llmModelIdOrNull))));
    }

    private static PutItem newPutItem(String key, String valueText) {
        var p = new PutItem();
        p.setKey(key);
        p.setValueText(valueText);
        return p;
    }

    public record UserProfilePageResult(
            List<UserProfileSummaryRow> records, long total, long page, long size) {}

    public record UserProfileSummaryRow(
            long userId,
            String loginName,
            String displayName,
            long profileTagCount,
            boolean memoryAbstractPresent,
            long memoryChunkCount) {}

    public record MemoryEmbeddingModelSettingView(Long selectedLlmModelId, List<VectorModelOption> vectorModels) {}

    public record VectorModelOption(long id, String alias, String displayName, boolean active) {}
}
