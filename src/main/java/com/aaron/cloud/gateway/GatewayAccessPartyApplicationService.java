package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository;
import com.aaron.cloud.common.gateway.GwAccessPartyRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiCatalogService;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.gateway.accessparty.AccessPartyIntegrationDocBuilder;
import com.aaron.cloud.gateway.accessparty.AccessPartyIntegrationDocBuilder.IntegrationDoc;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayAccessPartyApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GwAccessPartyRepository accessPartyRepository;
    private final GwAccessPartyGrantRepository grantRepository;
    private final GatewayAccessPartyGrantApplicationService grantApplicationService;
    private final AesSecretCipher secretCipher;
    private final GwApiOpenApiCatalogService openApiCatalogService;

    public Page<GwAccessParty> page(long tenantId, long pageNo, long pageSize) {
        return accessPartyRepository.pageByTenant(tenantId, pageNo, pageSize);
    }

    public GwAccessParty get(long tenantId, long id) {
        GwAccessParty row = accessPartyRepository.findById(id);
        if (row == null || !row.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("access party not found");
        }
        return row;
    }

    public IntegrationDoc buildIntegrationDoc(long tenantId, long id, String baseUrl) {
        GwAccessParty party = get(tenantId, id);
        var grants = grantApplicationService.listByAccessParty(tenantId, id);
        return AccessPartyIntegrationDocBuilder.build(
                party, grants, baseUrl, BeijingTime.nowLocal(), openApiCatalogService);
    }

    public record CreateResult(GwAccessParty party, String plainSecret) {}

    @Transactional
    public CreateResult create(
            long tenantId, String displayName, String appId, Integer totalRpmCap, String remark, ToggleState status) {
        String name = requireText(displayName, "displayName required");
        String aid = appId == null || appId.isBlank() ? generateAppId() : appId.trim();
        if (accessPartyRepository.countByAppId(aid, null) > 0) {
            throw new IllegalArgumentException("duplicate app_id");
        }
        String plainSecret = generatePlainSecret();
        var row = new GwAccessParty();
        row.setTenantId(tenantId);
        row.setAppId(aid);
        row.setDisplayName(name);
        row.setTotalRpmCap(totalRpmCap == null ? 0 : Math.max(0, totalRpmCap));
        row.setRemark(remark);
        row.setStatus(status == null ? ToggleState.ON : status);
        try {
            row.setSecretCipher(secretCipher.encryptToBase64(plainSecret));
        } catch (Exception ex) {
            throw new IllegalStateException("encrypt secret failed", ex);
        }
        row.setLastRotatedAt(BeijingTime.nowLocal());
        accessPartyRepository.insert(row);
        return new CreateResult(Objects.requireNonNull(accessPartyRepository.findById(row.getId())), plainSecret);
    }

    @Transactional
    public GwAccessParty update(
            long tenantId,
            long id,
            String displayName,
            Integer totalRpmCap,
            String remark,
            ToggleState status) {
        GwAccessParty row = get(tenantId, id);
        if (displayName != null) {
            row.setDisplayName(requireText(displayName, "displayName required"));
        }
        if (totalRpmCap != null) {
            int cap = Math.max(0, totalRpmCap);
            int allocated = grantRepository.sumGrantedRpmByAccessParty(id, null);
            if (cap > 0 && allocated > cap) {
                throw new IllegalArgumentException("total_rpm_cap less than allocated sum");
            }
            row.setTotalRpmCap(cap);
        }
        if (remark != null) {
            row.setRemark(remark);
        }
        if (status != null) {
            row.setStatus(status);
        }
        accessPartyRepository.updateById(row);
        return Objects.requireNonNull(accessPartyRepository.findById(id));
    }

    @Transactional
    public String rotateSecret(long tenantId, long id) {
        GwAccessParty row = get(tenantId, id);
        String plainSecret = generatePlainSecret();
        try {
            row.setSecretCipher(secretCipher.encryptToBase64(plainSecret));
        } catch (Exception ex) {
            throw new IllegalStateException("encrypt secret failed", ex);
        }
        row.setLastRotatedAt(BeijingTime.nowLocal());
        accessPartyRepository.updateById(row);
        return plainSecret;
    }

    @Transactional
    public void delete(long tenantId, long id) {
        get(tenantId, id);
        grantRepository.deleteByAccessPartyId(id);
        accessPartyRepository.deleteById(id);
    }

    private static String requireText(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return v.trim();
    }

    private static String generateAppId() {
        return "ap" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String generatePlainSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
