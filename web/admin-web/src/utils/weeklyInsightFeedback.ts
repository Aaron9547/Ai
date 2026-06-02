/** 解析画像标签 {@code WEEKLY_INSIGHT_FEEDBACK_JSON} 的 JSON 数组。 */
export type WeeklyInsightFeedbackEntry = {
  weekStart: string;
  helpful: boolean;
  at: string;
};

export function parseWeeklyInsightFeedbackJson(raw: string | null | undefined): WeeklyInsightFeedbackEntry[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const arr = JSON.parse(raw) as unknown;
    if (!Array.isArray(arr)) {
      return [];
    }
    const out: WeeklyInsightFeedbackEntry[] = [];
    for (const item of arr) {
      if (item === null || typeof item !== "object") continue;
      const o = item as Record<string, unknown>;
      const weekStart = String(o.weekStart ?? "").trim();
      const at = String(o.at ?? "").trim();
      if (!weekStart && !at) continue;
      out.push({
        weekStart,
        helpful: Boolean(o.helpful),
        at,
      });
    }
    return out;
  } catch {
    return [];
  }
}

export function isWeeklyInsightFeedbackTag(code: string): boolean {
  return (code ?? "").trim() === "WEEKLY_INSIGHT_FEEDBACK_JSON";
}
