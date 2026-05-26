import { http } from "@/plugins/http";

export type TenantBranding = {
  logoUrl: string;
  portalTitle: string;
  footerText: string;
  portalTitleResolved: string;
};

export async function getTenantBranding(): Promise<TenantBranding> {
  const { data } = await http.get<TenantBranding>("/open/v1/system/tenant-branding");
  return {
    logoUrl: data?.logoUrl ?? "",
    portalTitle: data?.portalTitle ?? "",
    footerText: data?.footerText ?? "",
    portalTitleResolved: data?.portalTitleResolved ?? "",
  };
}
