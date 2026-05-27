import { http } from "../plugins/http";

export type MessageChannelType = "EMAIL_SMTP" | "SMS_ALIYUN" | "SMS_TENCENT";
export type MessageChannelStatus = "ACTIVE" | "DISABLED";
export type MessageTemplateStatus = "ACTIVE" | "DISABLED";
export type MessageDeliveryStatus = "QUEUED" | "SENDING" | "SUCCEEDED" | "FAILED";
export type MessageSceneCode =
  | "REGISTER_VERIFICATION"
  | "KNOWLEDGE_PLANET_WEEKLY"
  | "SMS_LOGIN";

export type MessageChannelRow = {
  id: number;
  channelCode: string;
  channelType: MessageChannelType;
  name: string;
  configJson: string;
  secretConfigured: boolean;
  status: MessageChannelStatus;
  updatedAt: string;
};

export type MessageChannelCreateBody = {
  channelCode: string;
  channelType: MessageChannelType;
  name: string;
  configJson: string;
  secretJson?: string;
  status?: MessageChannelStatus;
};

export type MessageChannelUpdateBody = {
  name?: string;
  configJson?: string;
  secretJson?: string;
  status?: MessageChannelStatus;
};

export type MessageTemplateRow = {
  id: number;
  sceneCode: MessageSceneCode;
  channelId: number;
  subjectTemplate: string;
  bodyTemplate: string;
  locale: string;
  status: MessageTemplateStatus;
  updatedAt: string;
};

export type MessageTemplateCreateBody = {
  sceneCode: MessageSceneCode;
  channelId: number;
  subjectTemplate?: string;
  bodyTemplate: string;
  locale?: string;
  status?: MessageTemplateStatus;
};

export type MessageTemplateUpdateBody = {
  channelId?: number;
  subjectTemplate?: string;
  bodyTemplate?: string;
  locale?: string;
  status?: MessageTemplateStatus;
};

export type MessageDeliveryLogRow = {
  id: number;
  sceneCode: MessageSceneCode;
  channelId: number | null;
  channelType: MessageChannelType | null;
  recipient: string;
  requestJson: string;
  messageSubject: string;
  messageBody: string;
  templateVars: Record<string, string>;
  providerMsgId: string | null;
  status: MessageDeliveryStatus;
  errorCode: string | null;
  errorMessage: string | null;
  attemptCount: number;
  idempotencyKey: string | null;
  createdAt: string;
  finishedAt: string | null;
};

export type MessageDeliveryLogPage = {
  total: number;
  records: MessageDeliveryLogRow[];
};

export function listMessageChannels() {
  return http.get<MessageChannelRow[]>("/api/v1/admin/message/channels").then((r) => r.data);
}

export function createMessageChannel(body: MessageChannelCreateBody) {
  return http.post<MessageChannelRow>("/api/v1/admin/message/channels", body).then((r) => r.data);
}

export function updateMessageChannel(id: number, body: MessageChannelUpdateBody) {
  return http.put<MessageChannelRow>(`/api/v1/admin/message/channels/${id}`, body).then((r) => r.data);
}

export function deleteMessageChannel(id: number) {
  return http.delete(`/api/v1/admin/message/channels/${id}`);
}

export function testMessageChannel(id: number, recipient: string, templateVars?: Record<string, string>) {
  return http.post(`/api/v1/admin/message/channels/${id}/test`, { recipient, templateVars });
}

export function listMessageTemplates() {
  return http.get<MessageTemplateRow[]>("/api/v1/admin/message/templates").then((r) => r.data);
}

export function createMessageTemplate(body: MessageTemplateCreateBody) {
  return http.post<MessageTemplateRow>("/api/v1/admin/message/templates", body).then((r) => r.data);
}

export function updateMessageTemplate(id: number, body: MessageTemplateUpdateBody) {
  return http.put<MessageTemplateRow>(`/api/v1/admin/message/templates/${id}`, body).then((r) => r.data);
}

export function deleteMessageTemplate(id: number) {
  return http.delete(`/api/v1/admin/message/templates/${id}`);
}

export function pageMessageDeliveryLogs(params: {
  page?: number;
  pageSize?: number;
  sceneCode?: string;
  status?: string;
  recipient?: string;
}) {
  return http
    .get<MessageDeliveryLogPage>("/api/v1/admin/message/delivery-logs", { params })
    .then((r) => r.data);
}

export function getMessageDeliveryLog(id: number) {
  return http.get<MessageDeliveryLogRow>(`/api/v1/admin/message/delivery-logs/${id}`).then((r) => r.data);
}
