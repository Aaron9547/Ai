package com.aaron.cloud.model;

import com.aaron.cloud.common.api.enums.llm.LlmAnonymousAccess;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.llm.LlmThinkingCapability;
import com.aaron.cloud.common.api.enums.llm.LlmVectorBackend;
import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.quota.LlmTokenQuotaCoordinator;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.CreateLlmModelRequest;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.LlmModelAdminView;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.UpdateLlmModelRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmModelAdminApplicationService {

    private final SysLlmModelRepository llmModelRepository;
    private final AesSecretCipher aesSecretCipher;
    private final LlmTokenQuotaCoordinator llmTokenQuotaCoordinator;

    public List<LlmModelAdminView> list() {
        return list(null);
    }

    /** @param modelKind 非空时仅返回该类型（管理端 Tab）。 */
    public List<LlmModelAdminView> list(LlmModelKind modelKind) {
        long tenantId = TenantContextHolder.require().getTenantId();
        return llmModelRepository.listAllForAdmin(tenantId, modelKind).stream().map(this::toView).toList();
    }

    public LlmModelAdminView create(CreateLlmModelRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        String alias = req.getAlias().trim();
        validateAlias(alias);
        if (llmModelRepository.existsAlias(tenantId, alias, null)) {
            throw new IllegalStateException("alias 已存在");
        }
        var row = new SysLlmModel();
        row.setTenantId(tenantId);
        row.setAlias(alias);
        row.setDisplayName(req.getDisplayName().trim());
        row.setOpenaiBaseUrl(req.getOpenaiBaseUrl().trim());
        row.setOpenaiModelId(req.getOpenaiModelId().trim());
        LlmModelKind kind = req.getModelKind() != null ? req.getModelKind() : LlmModelKind.LANGUAGE;
        row.setModelKind(kind);
        row.setIntegrationBackend(normalizeIntegrationBackendForCreate(kind, req.getIntegrationBackend()));
        String apiKeyRaw = req.getApiKey() == null ? "" : req.getApiKey().trim();
        if (kind != LlmModelKind.VECTOR && apiKeyRaw.isEmpty() && !allowsEmptyWebSearchApiKey(kind, row.getIntegrationBackend())) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        row.setApiKeyCipher(apiKeyRaw.isEmpty() ? null : aesSecretCipher.encryptToBase64(apiKeyRaw));
        row.setAllowAnonymous(
                req.isAllowAnonymous() ? LlmAnonymousAccess.ALLOWED : LlmAnonymousAccess.DISALLOWED);
        row.setMaxAttachments(
                kind == LlmModelKind.VECTOR || kind == LlmModelKind.WEB_SEARCH
                        ? 0
                        : clampMax(req.getMaxAttachments()));
        row.setSupportsThinking(
                kind == LlmModelKind.VECTOR || kind == LlmModelKind.WEB_SEARCH
                        ? LlmThinkingCapability.NONE
                        : (req.isSupportsThinking()
                                ? LlmThinkingCapability.SUPPORTED
                                : LlmThinkingCapability.NONE));
        row.setStatus(req.isEnabled() ? LlmModelStatus.ACTIVE : LlmModelStatus.DISABLED);
        row.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        row.setTokenQuotaTotal(req.getTokenQuotaTotal());
        row.setTokensUsed(0L);
        row.setLocalDeploy(Boolean.TRUE.equals(req.getLocalDeploy()));
        row.setFallbackModelAlias(normalizeAndValidateFallback(tenantId, kind, alias, req.getFallbackModelAlias()));
        llmModelRepository.insert(row);
        var created = llmModelRepository.findById(tenantId, row.getId()).orElseThrow();
        llmTokenQuotaCoordinator.onModelConfigChanged(created);
        return toView(created);
    }

    public LlmModelAdminView update(long id, UpdateLlmModelRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        SysLlmModel row =
                llmModelRepository.findById(tenantId, id).orElseThrow(() -> new IllegalArgumentException("not found"));
        if (req.getDisplayName() != null) {
            row.setDisplayName(req.getDisplayName().trim());
        }
        if (req.getOpenaiBaseUrl() != null) {
            row.setOpenaiBaseUrl(req.getOpenaiBaseUrl().trim());
        }
        if (req.getOpenaiModelId() != null) {
            row.setOpenaiModelId(req.getOpenaiModelId().trim());
        }
        if (req.getModelKind() != null) {
            row.setModelKind(req.getModelKind());
        }
        if (req.getIntegrationBackend() != null) {
            LlmModelKind kind = row.getModelKind() != null ? row.getModelKind() : LlmModelKind.LANGUAGE;
            row.setIntegrationBackend(
                    normalizeIntegrationBackendForCreate(kind, req.getIntegrationBackend()));
        }
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            row.setApiKeyCipher(aesSecretCipher.encryptToBase64(req.getApiKey().trim()));
        } else if (Boolean.TRUE.equals(req.getClearApiKey())) {
            row.setApiKeyCipher(null);
        }
        if (req.getAllowAnonymous() != null) {
            row.setAllowAnonymous(
                    req.getAllowAnonymous() ? LlmAnonymousAccess.ALLOWED : LlmAnonymousAccess.DISALLOWED);
        }
        if (req.getMaxAttachments() != null) {
            row.setMaxAttachments(clampMax(req.getMaxAttachments()));
        }
        if (req.getSupportsThinking() != null) {
            row.setSupportsThinking(
                    req.getSupportsThinking() ? LlmThinkingCapability.SUPPORTED : LlmThinkingCapability.NONE);
        }
        if (req.getEnabled() != null) {
            row.setStatus(req.getEnabled() ? LlmModelStatus.ACTIVE : LlmModelStatus.DISABLED);
        }
        if (req.getSortOrder() != null) {
            row.setSortOrder(req.getSortOrder());
        }
        if (Boolean.TRUE.equals(req.getTokenQuotaUnlimited())) {
            row.setTokenQuotaTotal(null);
        } else if (req.getTokenQuotaTotal() != null) {
            row.setTokenQuotaTotal(req.getTokenQuotaTotal());
        }
        if (req.getLocalDeploy() != null) {
            row.setLocalDeploy(req.getLocalDeploy());
        }
        if (req.getFallbackModelAlias() != null) {
            LlmModelKind effKind = row.getModelKind() != null ? row.getModelKind() : LlmModelKind.LANGUAGE;
            if (effKind != LlmModelKind.LANGUAGE) {
                throw new IllegalArgumentException("仅对话语言模型可配置主备 fallback");
            }
            String v = req.getFallbackModelAlias().trim();
            if (v.isEmpty()) {
                row.setFallbackModelAlias(null);
            } else {
                row.setFallbackModelAlias(
                        normalizeAndValidateFallback(tenantId, LlmModelKind.LANGUAGE, row.getAlias(), v));
            }
        }
        llmModelRepository.updateById(row);
        var saved = llmModelRepository.findById(tenantId, id).orElseThrow();
        llmTokenQuotaCoordinator.onModelConfigChanged(saved);
        return toView(saved);
    }

    public void delete(long id) {
        long tenantId = TenantContextHolder.require().getTenantId();
        SysLlmModel row =
                llmModelRepository.findById(tenantId, id).orElseThrow(() -> new IllegalArgumentException("not found"));
        if ("mock".equalsIgnoreCase(row.getAlias())) {
            throw new IllegalArgumentException("不可删除保留别名");
        }
        llmModelRepository.delete(tenantId, id);
        llmTokenQuotaCoordinator.invalidateQuotaCache(tenantId, id);
    }

    private LlmModelAdminView toView(SysLlmModel m) {
        boolean hasKey = m.getApiKeyCipher() != null && !m.getApiKeyCipher().isBlank();
        String ib = m.getIntegrationBackend();
        if (ib == null || ib.isBlank()) {
            ib = LlmVectorBackend.OPENAI_COMPATIBLE.getCode();
        } else {
            ib = ib.trim();
        }
        return new LlmModelAdminView(
                m.getId(),
                m.getAlias(),
                m.getDisplayName(),
                m.getOpenaiBaseUrl(),
                m.getOpenaiModelId(),
                m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE,
                ib,
                hasKey,
                m.getAllowAnonymous() == LlmAnonymousAccess.ALLOWED,
                m.getMaxAttachments() == null ? 10 : m.getMaxAttachments(),
                m.getSupportsThinking() == LlmThinkingCapability.SUPPORTED,
                m.getStatus() == LlmModelStatus.ACTIVE,
                m.getSortOrder(),
                m.getTokenQuotaTotal(),
                m.getTokensUsed() == null ? 0L : m.getTokensUsed(),
                m.getFallbackModelAlias(),
                Boolean.TRUE.equals(m.getLocalDeploy()));
    }

    private String normalizeAndValidateFallback(long tenantId, LlmModelKind kind, String selfAlias, String raw) {
        if (kind != LlmModelKind.LANGUAGE) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String fb = raw.trim();
        if (fb.equalsIgnoreCase(selfAlias)) {
            throw new IllegalArgumentException("备用别名不能与自身相同");
        }
        assertFallbackLanguageTarget(tenantId, fb);
        assertFallbackNoCycle(tenantId, selfAlias, fb);
        return fb;
    }

    private void assertFallbackLanguageTarget(long tenantId, String fbAlias) {
        SysLlmModel t =
                llmModelRepository
                        .findByTenantAndAlias(tenantId, fbAlias)
                        .orElseThrow(() -> new IllegalArgumentException("备用模型不存在或未启用：" + fbAlias));
        LlmModelKind k = t.getModelKind() != null ? t.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            throw new IllegalArgumentException("备用模型须为 LANGUAGE 类型");
        }
    }

    private void assertFallbackNoCycle(long tenantId, String selfAlias, String firstHop) {
        String next = firstHop;
        for (int i = 0; i < 8 && next != null; i++) {
            if (next.trim().equalsIgnoreCase(selfAlias)) {
                throw new IllegalArgumentException("主备链成环：请勿使备用链回到当前模型");
            }
            SysLlmModel m = llmModelRepository.findByTenantAndAlias(tenantId, next.trim()).orElse(null);
            if (m == null) {
                return;
            }
            String fb = m.getFallbackModelAlias();
            next = (fb == null || fb.isBlank()) ? null : fb.trim();
        }
    }

    /**
     * 写入 {@code llm_model.integration_backend}：VECTOR 为 {@link LlmVectorBackend} 码；WEB_SEARCH 为 {@link LlmWebSearchProvider} 码；其余类型默认
     * OPENAI_COMPATIBLE。
     */
    private static String normalizeIntegrationBackendForCreate(LlmModelKind kind, String raw) {
        String s = raw == null ? "" : raw.trim();
        return switch (kind) {
            case VECTOR -> {
                if (s.isEmpty()) {
                    yield LlmVectorBackend.OPENAI_COMPATIBLE.getCode();
                }
                if (LlmWebSearchProvider.fromCode(s) != null) {
                    throw new IllegalArgumentException("向量模型的集成策略不能选择联网检索实现");
                }
                yield LlmVectorBackend.fromCode(s).getCode();
            }
            case WEB_SEARCH -> {
                if (s.isEmpty()) {
                    throw new IllegalArgumentException("联网搜索模型须指定 integrationBackend（检索实现）");
                }
                LlmWebSearchProvider p = LlmWebSearchProvider.fromCode(s);
                if (p == null) {
                    throw new IllegalArgumentException("不支持的联网检索实现码：" + s);
                }
                if (p != LlmWebSearchProvider.VOLCENGINE_ARK_BOT) {
                    throw new IllegalArgumentException(
                            "联网搜索模型仅支持火山 Ark Bot（VOLCENGINE_ARK_BOT）；免费固定源在租户 Shell 勾选");
                }
                yield p.getCode();
            }
            default -> LlmVectorBackend.OPENAI_COMPATIBLE.getCode();
        };
    }

    private static boolean allowsEmptyWebSearchApiKey(LlmModelKind kind, String integrationBackend) {
        if (kind != LlmModelKind.WEB_SEARCH) {
            return false;
        }
        LlmWebSearchProvider p = LlmWebSearchProvider.fromCode(integrationBackend);
        return p != null && !p.isRequiresApiKey();
    }

    private static void validateAlias(String alias) {
        if (alias == null || alias.isBlank() || "mock".equalsIgnoreCase(alias.trim())) {
            throw new IllegalArgumentException("非法别名（mock 为系统保留）");
        }
    }

    private static int clampMax(Integer v) {
        if (v == null) {
            return 10;
        }
        return Math.max(0, Math.min(10, v));
    }
}
