import { http } from "../plugins/http";

export type TenantShellBranding = {
  logoUrl: string;
  portalTitle: string;
  footerText: string;
  /** 展示用标题（空配置时回退租户名）；与 {@link #portalTitle} 区分 */
  portalTitleResolved?: string;
};

export type TenantShellOutbound = {
  tenantJson: string;
  baselineJson: unknown;
  effectiveMerged: unknown;
};

/** 与 {@code ten_runtime_setting} 中对话/记忆/联网相关键一致 */
export type TenantShellModelCallingRuntime = {
  memoryEmbeddingVectorModelId: string;
  chatPromptLimitsJson: string;
  memoryPolicyJson: string;
  chatInputGuardJson: string;
  webSearchGroundingMultiRoundCount: string;
  webSearchGroundingRoundSuffixesJson: string;
};

export type TenantShellConfig = {
  branding: TenantShellBranding;
  modelCallingRuntime: TenantShellModelCallingRuntime;
  outbound: TenantShellOutbound;
};

export type TenantShellPutBody = {
  logoUrl: string | null;
  portalTitle: string | null;
  footerText: string | null;
  outboundResilienceJson: string;
};

export type TenantShellBrandingPutBody = {
  logoUrl: string | null;
  portalTitle: string | null;
  footerText: string | null;
};

export type TenantShellOutboundPutBody = {
  outboundResilienceJson: string;
};

export type TenantShellModelCallingPutBody = TenantShellModelCallingRuntime;

export async function getTenantShellConfig(): Promise<TenantShellConfig> {
  const { data } = await http.get<TenantShellConfig>("/api/v1/admin/tenant-shell-config");
  return data;
}

/** 仅保存管理端外观；空值会存为 null，展示走系统默认。 */
export async function putTenantShellBranding(body: TenantShellBrandingPutBody): Promise<TenantShellConfig> {
  const { data } = await http.put<TenantShellConfig>("/api/v1/admin/tenant-shell-config/branding", body);
  return data;
}

/** 仅保存大模型调用策略覆盖 JSON。 */
export async function putTenantShellOutbound(body: TenantShellOutboundPutBody): Promise<TenantShellConfig> {
  const { data } = await http.put<TenantShellConfig>("/api/v1/admin/tenant-shell-config/outbound", body);
  return data;
}

/** 保存记忆嵌入、对话 system 预算、记忆策略、输入护栏、联网多轮与问句后缀。 */
export async function putTenantShellModelCallingRuntime(
  body: TenantShellModelCallingPutBody,
): Promise<TenantShellConfig> {
  const { data } = await http.put<TenantShellConfig>(
    "/api/v1/admin/tenant-shell-config/model-calling-runtime",
    body,
  );
  return data;
}

/** 兼容：一次保存外观与出站（新界面请优先使用分项接口）。 */
export async function putTenantShellConfig(body: TenantShellPutBody): Promise<TenantShellConfig> {
  const { data } = await http.put<TenantShellConfig>("/api/v1/admin/tenant-shell-config", body);
  return data;
}

/** 侧栏 LOGO 图片上传；成功返回可写入 logoUrl 的路径（一般为 /open/v1/admin-brand-logos/...） */
export async function postTenantShellLogo(file: File): Promise<{ url: string }> {
  const fd = new FormData();
  fd.append("file", file);
  const { data } = await http.post<{ url: string }>("/api/v1/admin/tenant-shell-config/logo", fd, {
    timeout: 120_000,
  });
  return data;
}
