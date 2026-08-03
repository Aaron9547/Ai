package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 从 {@code sys_tenant} 解析管理端外观（LOGO / 标题 / 页脚），供 Shell 与 C 端 Open API 共用。 */
@Component
@RequiredArgsConstructor
public class TenantBrandingResolver {

    private final SysTenantRepository sysTenantRepository;

    public TenantBrandingSnapshot resolve(long tenantId) {
        return sysTenantRepository.findById(tenantId).map(this::fromTenant).orElse(TenantBrandingSnapshot.empty());
    }

    public TenantBrandingSnapshot fromTenant(SysTenant t) {
        String baseName = t.getName() == null ? "" : t.getName().trim();
        String rawLogo = t.getAdminLogoUrl() == null ? "" : t.getAdminLogoUrl().trim();
        String rawTitle = t.getAdminPortalTitle() == null ? "" : t.getAdminPortalTitle().trim();
        String rawFooter = t.getAdminFooterText() == null ? "" : t.getAdminFooterText().trim();
        String portalResolved = rawTitle.isEmpty() ? baseName : rawTitle;
        return new TenantBrandingSnapshot(rawLogo, rawTitle, rawFooter, portalResolved);
    }

    public record TenantBrandingSnapshot(
            String logoUrl, String portalTitle, String footerText, String portalTitleResolved) {

        public static TenantBrandingSnapshot empty() {
            return new TenantBrandingSnapshot("", "", "", "");
        }
    }
}
