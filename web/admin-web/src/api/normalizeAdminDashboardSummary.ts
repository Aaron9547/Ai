import type { AdminDashboardSummary, ModelDailyTokenSeries, TokenTotals } from "./adminDashboard";

function num(v: unknown): number {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    if (Number.isFinite(n)) return n;
  }
  return 0;
}

function pick(obj: Record<string, unknown>, camel: string, snake: string): unknown {
  if (camel in obj) return obj[camel];
  if (snake in obj) return obj[snake];
  return undefined;
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

function normalizeTokenTotals(raw: unknown): TokenTotals {
  const r = asRecord(raw);
  return {
    promptTokens: num(pick(r, "promptTokens", "prompt_tokens")),
    completionTokens: num(pick(r, "completionTokens", "completion_tokens")),
  };
}

function normalizeModelTrendRow(raw: unknown): ModelDailyTokenSeries {
  const r = asRecord(raw);
  const alias = pick(r, "modelAlias", "model_alias");
  const dailyRaw = pick(r, "daily", "daily");
  const daily = Array.isArray(dailyRaw) ? normalizeDailyTokens(dailyRaw) : [];
  return {
    modelAlias: typeof alias === "string" && alias.trim() ? alias.trim() : "—",
    daily,
  };
}

function normalizeDailyTokens(raw: unknown): AdminDashboardSummary["meteringTokensByDay"] {
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => {
    const d = asRecord(item);
    const day = pick(d, "day", "day");
    return {
      day: typeof day === "string" ? day : String(day ?? ""),
      promptTokens: num(pick(d, "promptTokens", "prompt_tokens")),
      completionTokens: num(pick(d, "completionTokens", "completion_tokens")),
    };
  });
}

/** 兼容 camelCase / snake_case，并兜底旧版 quantity 字段。 */
export function normalizeAdminDashboardSummary(raw: unknown): AdminDashboardSummary {
  const r = asRecord(raw);
  const kpi = asRecord(pick(r, "kpi", "kpi"));
  const recent = asRecord(pick(r, "recent24h", "recent_24h"));

  const legacyQty24 = num(pick(recent, "meteringQuantitySum", "metering_quantity_sum"));
  let prompt24 = num(pick(recent, "promptTokens24h", "prompt_tokens_24h"));
  let completion24 = num(pick(recent, "completionTokens24h", "completion_tokens_24h"));
  if (prompt24 === 0 && completion24 === 0 && legacyQty24 > 0) {
    completion24 = legacyQty24;
  }

  let meteringTokensByDay = normalizeDailyTokens(pick(r, "meteringTokensByDay", "metering_tokens_by_day"));
  if (meteringTokensByDay.length === 0) {
    const legacyDaily = pick(r, "meteringQuantityByDay", "metering_quantity_by_day");
    if (Array.isArray(legacyDaily)) {
      meteringTokensByDay = legacyDaily.map((item) => {
        const d = asRecord(item);
        const day = pick(d, "day", "day");
        const total = num(pick(d, "total", "total"));
        return {
          day: typeof day === "string" ? day : String(day ?? ""),
          promptTokens: 0,
          completionTokens: total,
        };
      });
    }
  }

  const httpAccessByDay = Array.isArray(pick(r, "httpAccessByDay", "http_access_by_day"))
    ? (pick(r, "httpAccessByDay", "http_access_by_day") as unknown[]).map((item) => {
        const d = asRecord(item);
        return { day: String(pick(d, "day", "day") ?? ""), count: num(pick(d, "count", "count")) };
      })
    : [];

  const meteringEventsByDay = Array.isArray(pick(r, "meteringEventsByDay", "metering_events_by_day"))
    ? (pick(r, "meteringEventsByDay", "metering_events_by_day") as unknown[]).map((item) => {
        const d = asRecord(item);
        return { day: String(pick(d, "day", "day") ?? ""), count: num(pick(d, "count", "count")) };
      })
    : [];

  let tenantTokens7d = normalizeTokenTotals(pick(r, "tenantTokens7d", "tenant_tokens_7d"));
  if (tenantTokens7d.promptTokens === 0 && tenantTokens7d.completionTokens === 0) {
    const sum = meteringTokensByDay.reduce(
      (acc, d) => ({ prompt: acc.prompt + d.promptTokens, completion: acc.completion + d.completionTokens }),
      { prompt: 0, completion: 0 },
    );
    tenantTokens7d = { promptTokens: sum.prompt, completionTokens: sum.completion };
  }

  const trendRaw = pick(r, "topModelTokenTrend30d", "top_model_token_trend_30d");
  let topModelTokenTrend30d = Array.isArray(trendRaw) ? trendRaw.map(normalizeModelTrendRow) : [];

  const regionsRaw = pick(r, "memberLoginRegionCounts", "member_login_region_counts");
  const memberLoginRegionCounts = Array.isArray(regionsRaw)
    ? regionsRaw.map((item) => {
        const d = asRecord(item);
        return { name: String(pick(d, "name", "name") ?? "—"), value: num(pick(d, "value", "value")) };
      })
    : [];

  const ipsRaw = pick(r, "topMemberClientIpsLast7d", "top_member_client_ips_last_7d");
  const topMemberClientIpsLast7d = Array.isArray(ipsRaw)
    ? ipsRaw.map((item) => {
        const d = asRecord(item);
        return {
          clientIp: String(pick(d, "clientIp", "client_ip") ?? ""),
          distinctUsers: num(pick(d, "distinctUsers", "distinct_users")),
          hits: num(pick(d, "hits", "hits")),
        };
      })
    : [];

  return {
    tenantId: num(pick(r, "tenantId", "tenant_id")),
    generatedAt: String(pick(r, "generatedAt", "generated_at") ?? ""),
    kpi: {
      activeMemberCount: num(pick(kpi, "activeMemberCount", "active_member_count")),
      conversationCount: num(pick(kpi, "conversationCount", "conversation_count")),
      chatMessageCount: num(pick(kpi, "chatMessageCount", "chat_message_count")),
      llmModelTotal: num(pick(kpi, "llmModelTotal", "llm_model_total")),
      llmModelActive: num(pick(kpi, "llmModelActive", "llm_model_active")),
      jobTaskPendingOrRunning: num(pick(kpi, "jobTaskPendingOrRunning", "job_task_pending_or_running")),
      jobTasksCreatedLast7d: num(pick(kpi, "jobTasksCreatedLast7d", "job_tasks_created_last_7d")),
    },
    recent24h: {
      httpAccessCount: num(pick(recent, "httpAccessCount", "http_access_count")),
      meteringEventCount: num(pick(recent, "meteringEventCount", "metering_event_count")),
      promptTokens24h: prompt24,
      completionTokens24h: completion24,
      auditEventCount: num(pick(recent, "auditEventCount", "audit_event_count")),
    },
    httpAccessByDay,
    meteringTokensByDay,
    meteringEventsByDay,
    tenantTokens7d,
    topModelTokenTrend30d,
    memberLoginRegionCounts,
    topMemberClientIpsLast7d,
  };
}
