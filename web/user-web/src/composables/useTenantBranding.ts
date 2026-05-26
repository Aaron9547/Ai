import { getTenantBranding } from "@/api/tenantBranding";
import { resolvePublicAssetUrl } from "@/utils/publicAssetUrl";
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";

const FALLBACK_BRAND_KEY = "chat.emptyBrand";

export function useTenantBranding() {
  const { t } = useI18n();
  const logoUrl = ref("");
  const portalTitleResolved = ref("");

  const brandLogoSrc = computed(() => resolvePublicAssetUrl(logoUrl.value));

  const displayBrandTitle = computed(() => {
    const resolved = portalTitleResolved.value.trim();
    if (resolved) return resolved;
    return t(FALLBACK_BRAND_KEY);
  });

  async function loadTenantBranding(): Promise<void> {
    try {
      const data = await getTenantBranding();
      logoUrl.value = data.logoUrl ?? "";
      portalTitleResolved.value = data.portalTitleResolved ?? "";
    } catch {
      logoUrl.value = "";
      portalTitleResolved.value = "";
    }
  }

  return {
    logoUrl,
    brandLogoSrc,
    displayBrandTitle,
    loadTenantBranding,
  };
}
