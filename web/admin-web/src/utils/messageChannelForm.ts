import type { MessageChannelType } from "@/api/message";

export type SmtpChannelFields = {
  smtpHost: string;
  smtpPort: number;
  username: string;
  from: string;
  ssl: boolean;
};

export type AliyunSmsChannelFields = {
  accessKeyId: string;
  region: string;
  signName: string;
  templateCode: string;
};

export type TencentSmsChannelFields = {
  sdkAppId: string;
  signName: string;
  templateId: string;
  region: string;
};

export function defaultSmtpFields(): SmtpChannelFields {
  return { smtpHost: "", smtpPort: 465, username: "", from: "", ssl: true };
}

export function defaultAliyunFields(): AliyunSmsChannelFields {
  return { accessKeyId: "", region: "cn-hangzhou", signName: "", templateCode: "" };
}

export function defaultTencentFields(): TencentSmsChannelFields {
  return { sdkAppId: "", signName: "", templateId: "", region: "ap-guangzhou" };
}

function parseJson<T extends object>(json: string | null | undefined, fallback: T): T {
  if (!json?.trim()) return { ...fallback };
  try {
    const parsed = JSON.parse(json) as Partial<T>;
    return { ...fallback, ...parsed };
  } catch {
    return { ...fallback };
  }
}

export function parseSmtpFromConfigJson(configJson: string): SmtpChannelFields {
  return parseJson(configJson, defaultSmtpFields());
}

export function parseAliyunFromConfigJson(configJson: string): AliyunSmsChannelFields {
  return parseJson(configJson, defaultAliyunFields());
}

export function parseTencentFromConfigJson(configJson: string): TencentSmsChannelFields {
  return parseJson(configJson, defaultTencentFields());
}

export function buildConfigJson(
  channelType: MessageChannelType,
  smtp: SmtpChannelFields,
  aliyun: AliyunSmsChannelFields,
  tencent: TencentSmsChannelFields,
): string {
  if (channelType === "EMAIL_SMTP") {
    return JSON.stringify(smtp);
  }
  if (channelType === "SMS_ALIYUN") {
    return JSON.stringify(aliyun);
  }
  return JSON.stringify(tencent);
}

export function buildSecretJson(
  channelType: MessageChannelType,
  smtpPassword: string,
  aliyunSecret: string,
  tencentSecretId: string,
  tencentSecretKey: string,
): string | undefined {
  if (channelType === "EMAIL_SMTP") {
    const p = smtpPassword.trim();
    return p ? JSON.stringify({ password: p }) : undefined;
  }
  if (channelType === "SMS_ALIYUN") {
    const s = aliyunSecret.trim();
    return s ? JSON.stringify({ accessKeySecret: s }) : undefined;
  }
  const id = tencentSecretId.trim();
  const key = tencentSecretKey.trim();
  if (!id && !key) return undefined;
  return JSON.stringify({ secretId: id, secretKey: key });
}
