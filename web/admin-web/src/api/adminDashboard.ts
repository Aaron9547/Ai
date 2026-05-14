import { http } from "../plugins/http";

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
    meteringQuantitySum: number;
    auditEventCount: number;
  };
  httpAccessByDay: { day: string; count: number }[];
  meteringQuantityByDay: { day: string; total: number }[];
  meteringEventsByDay: { day: string; count: number }[];
  /** 在册成员按账号最近登录地区码聚合 */
  memberLoginRegionCounts: { name: string; value: number }[];
  /** 近 7 日已登录请求的客户端 IP TOP */
  topMemberClientIpsLast7d: { clientIp: string; distinctUsers: number; hits: number }[];
}

export async function fetchDashboardSummary(): Promise<AdminDashboardSummary> {
  const { data } = await http.get<AdminDashboardSummary>("/api/v1/admin/dashboard/summary");
  return data;
}
