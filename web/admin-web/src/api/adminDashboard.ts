import { http } from "../plugins/http";
import { normalizeAdminDashboardSummary } from "./normalizeAdminDashboardSummary";

export interface TokenTotals {
  promptTokens: number;
  completionTokens: number;
}

export interface ModelDailyTokenSeries {
  modelAlias: string;
  daily: { day: string; promptTokens: number; completionTokens: number }[];
}

export interface AdminDashboardSummary {
  tenantId: number;
  /** 接口生成时间，北京时间，格式 yyyy-MM-dd HH:mm:ss */
  generatedAt: string;
  kpi: {
    activeMemberCount: number;
    conversationCount: number;
    chatMessageCount: number;
    llmModelTotal: number;
    llmModelActive: number;
    jobTaskPendingOrRunning: number;
    jobTasksCreatedLast7d: number;
  };
  recent24h: {
    httpAccessCount: number;
    meteringEventCount: number;
    promptTokens24h: number;
    completionTokens24h: number;
    auditEventCount: number;
  };
  httpAccessByDay: { day: string; count: number }[];
  meteringTokensByDay: { day: string; promptTokens: number; completionTokens: number }[];
  meteringEventsByDay: { day: string; count: number }[];
  tenantTokens7d: TokenTotals;
  /** 近 30 日用量 Top3 模型按日 Token；前端按 7/14/30 日截取展示 */
  topModelTokenTrend30d: ModelDailyTokenSeries[];
  /** 在册成员按账号最近登录地区码聚合 */
  memberLoginRegionCounts: { name: string; value: number }[];
  /** 近 7 日已登录请求的客户端 IP TOP */
  topMemberClientIpsLast7d: { clientIp: string; distinctUsers: number; hits: number }[];
}

export async function fetchDashboardSummary(): Promise<AdminDashboardSummary> {
  const { data } = await http.get<unknown>("/api/v1/admin/dashboard/summary");
  return normalizeAdminDashboardSummary(data);
}
