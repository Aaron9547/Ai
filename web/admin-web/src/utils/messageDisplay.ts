import type { ComposerTranslation } from "vue-i18n";
import type {
  MessageChannelRow,
  MessageChannelStatus,
  MessageChannelType,
  MessageDeliveryLogRow,
  MessageDeliveryStatus,
  MessageSceneCode,
  MessageTemplateRow,
} from "@/api/message";
import {
  parseAliyunFromConfigJson,
  parseTencentFromConfigJson,
} from "@/utils/messageChannelForm";

export const MESSAGE_CHANNEL_TYPES: readonly MessageChannelType[] = [
  "EMAIL_SMTP",
  "SMS_ALIYUN",
  "SMS_TENCENT",
] as const;

export const MESSAGE_SCENE_CODES: readonly MessageSceneCode[] = [
  "REGISTER_VERIFICATION",
  "KNOWLEDGE_PLANET_WEEKLY",
  "SMS_LOGIN",
  "CHAT_USER_REMINDER",
] as const;

export const MESSAGE_ENABLE_STATUSES: readonly MessageChannelStatus[] = ["ACTIVE", "DISABLED"] as const;

export const MESSAGE_DELIVERY_STATUSES: readonly MessageDeliveryStatus[] = [
  "QUEUED",
  "SENDING",
  "SUCCEEDED",
  "FAILED",
] as const;

export const MESSAGE_LOCALE_OPTIONS = [
  { value: "zh-CN", labelKey: "admin.message.localeZhCn" },
  { value: "en-US", labelKey: "admin.message.localeEnUs" },
] as const;

export const ALIYUN_REGION_OPTIONS = [
  { value: "cn-hangzhou", labelKey: "admin.message.aliyunRegionOptions.cnHangzhou" },
  { value: "cn-shanghai", labelKey: "admin.message.aliyunRegionOptions.cnShanghai" },
  { value: "cn-beijing", labelKey: "admin.message.aliyunRegionOptions.cnBeijing" },
  { value: "cn-shenzhen", labelKey: "admin.message.aliyunRegionOptions.cnShenzhen" },
] as const;

export const TENCENT_REGION_OPTIONS = [
  { value: "ap-guangzhou", labelKey: "admin.message.tencentRegionOptions.apGuangzhou" },
  { value: "ap-shanghai", labelKey: "admin.message.tencentRegionOptions.apShanghai" },
  { value: "ap-beijing", labelKey: "admin.message.tencentRegionOptions.apBeijing" },
  { value: "ap-nanjing", labelKey: "admin.message.tencentRegionOptions.apNanjing" },
] as const;

export function channelTypeLabel(t: ComposerTranslation, type: MessageChannelType | string | null | undefined): string {
  if (!type) return t("common.dash");
  const key = `admin.message.channelType.${type}`;
  return t(key) === key ? String(type) : t(key);
}

export function channelTypeHint(t: ComposerTranslation, type: MessageChannelType): string {
  return t(`admin.message.channelTypeHint.${type}`);
}

export function sceneLabel(t: ComposerTranslation, scene: MessageSceneCode | string | null | undefined): string {
  if (!scene) return t("common.dash");
  const key = `admin.message.scene.${scene}`;
  return t(key) === key ? String(scene) : t(key);
}

export function sceneHint(t: ComposerTranslation, scene: MessageSceneCode): string {
  return t(`admin.message.sceneHint.${scene}`);
}

export function enableStatusLabel(t: ComposerTranslation, status: MessageChannelStatus | string): string {
  const key = `admin.message.enableStatus.${status}`;
  return t(key) === key ? String(status) : t(key);
}

export function enableStatusTagType(status: MessageChannelStatus | string): "success" | "info" | "warning" {
  return status === "ACTIVE" ? "success" : "info";
}

export function deliveryStatusLabel(t: ComposerTranslation, status: MessageDeliveryStatus | string): string {
  const key = `admin.message.deliveryStatus.${status}`;
  return t(key) === key ? String(status) : t(key);
}

export function deliveryStatusTagType(
  status: MessageDeliveryStatus | string,
): "success" | "info" | "warning" | "danger" {
  switch (status) {
    case "SUCCEEDED":
      return "success";
    case "FAILED":
      return "danger";
    case "SENDING":
      return "warning";
    default:
      return "info";
  }
}

/** 场景期望的通道大类：邮件场景仅 SMTP；短信登录仅短信通道。 */
export function channelMatchesScene(channel: MessageChannelRow, scene: MessageSceneCode): boolean {
  if (scene === "SMS_LOGIN") {
    return channel.channelType === "SMS_ALIYUN" || channel.channelType === "SMS_TENCENT";
  }
  return channel.channelType === "EMAIL_SMTP";
}

export function filterChannelsForScene(
  channels: MessageChannelRow[],
  scene: MessageSceneCode,
): MessageChannelRow[] {
  return channels.filter((c) => channelMatchesScene(c, scene));
}

export function channelOptionLabel(t: ComposerTranslation, ch: MessageChannelRow): string {
  return `${ch.name}（${channelTypeLabel(t, ch.channelType)}）`;
}

export function resolveChannelLabel(
  t: ComposerTranslation,
  channelId: number | null | undefined,
  channelById: Map<number, MessageChannelRow>,
): string {
  if (channelId == null) return t("common.dash");
  const ch = channelById.get(channelId);
  if (!ch) return t("admin.message.channelMissing", { id: channelId });
  return channelOptionLabel(t, ch);
}

export function localeLabel(t: ComposerTranslation, locale: string): string {
  const found = MESSAGE_LOCALE_OPTIONS.find((o) => o.value === locale);
  return found ? t(found.labelKey) : locale;
}

export function isEmailChannelType(type: MessageChannelType | null | undefined): boolean {
  return type === "EMAIL_SMTP";
}

export function isSmsChannelType(type: MessageChannelType | null | undefined): boolean {
  return type === "SMS_ALIYUN" || type === "SMS_TENCENT";
}

export function isSmsScene(scene: MessageSceneCode): boolean {
  return scene === "SMS_LOGIN";
}

export function templateRowUsesSms(
  row: MessageTemplateRow,
  channelById: Map<number, MessageChannelRow>,
): boolean {
  if (isSmsScene(row.sceneCode)) return true;
  const ch = channelById.get(row.channelId);
  return ch != null && isSmsChannelType(ch.channelType);
}

export function channelSmsProviderSummary(ch: MessageChannelRow): string {
  if (ch.channelType === "SMS_ALIYUN") {
    const cfg = parseAliyunFromConfigJson(ch.configJson);
    const parts = [cfg.signName, cfg.templateCode].filter(Boolean);
    return parts.join(" · ") || "—";
  }
  if (ch.channelType === "SMS_TENCENT") {
    const cfg = parseTencentFromConfigJson(ch.configJson);
    const parts = [cfg.signName, cfg.templateId].filter(Boolean);
    return parts.join(" · ") || "—";
  }
  return "";
}

export function formatTemplateVarsLine(vars: Record<string, string> | null | undefined): string {
  if (!vars || Object.keys(vars).length === 0) return "";
  return Object.entries(vars)
    .map(([k, v]) => `${k}=${v}`)
    .join("，");
}

export function logMessagePreview(
  row: MessageDeliveryLogRow,
  channelById: Map<number, MessageChannelRow>,
): string {
  const sms =
    isSmsChannelType(row.channelType) ||
    (row.channelId != null && isSmsChannelType(channelById.get(row.channelId)?.channelType));
  if (sms) {
    const body = row.messageBody?.trim();
    const vars = formatTemplateVarsLine(row.templateVars);
    if (body && vars) return `${body}（${vars}）`;
    if (body) return body;
    if (vars) return vars;
    return "";
  }
  const subject = row.messageSubject?.trim();
  const body = row.messageBody?.trim();
  if (subject && body) return `${subject} — ${body}`;
  return subject || body || "";
}
