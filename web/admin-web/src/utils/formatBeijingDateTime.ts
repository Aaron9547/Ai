const BEIJING_TZ = "Asia/Shanghai";

/**
 * 管理端时间展示：统一为北京时间（东八区）`yyyy-MM-dd HH:mm:ss`。
 * - 带 `Z` / 偏移量：按 Instant 换算到北京墙钟。
 * - Java `LocalDateTime` 字符串（无偏移）：按东八区墙钟解析（与后端 `LocalDateTime.now()` 落库约定一致）。
 */
export function formatBeijingDateTime(raw: string | null | undefined, fallback = "—"): string {
  if (!raw?.trim()) {
    return fallback;
  }
  const s = raw.trim();
  try {
    const date = parseAdminDateTime(s);
    if (!date || Number.isNaN(date.getTime())) {
      return s;
    }
    const parts = new Intl.DateTimeFormat("zh-CN", {
      timeZone: BEIJING_TZ,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).formatToParts(date);
    const pick = (type: Intl.DateTimeFormatPartTypes) =>
      parts.find((p) => p.type === type)?.value ?? "";
    return `${pick("year")}-${pick("month")}-${pick("day")} ${pick("hour")}:${pick("minute")}:${pick("second")}`;
  } catch {
    return s;
  }
}

function parseAdminDateTime(s: string): Date | null {
  if (/Z$|[+-]\d{2}:?\d{2}$/.test(s)) {
    return new Date(s);
  }
  const noFrac = s.includes(".") ? s.replace(/\.\d+$/, "") : s;
  const normalized = noFrac.includes("T") ? noFrac : noFrac.replace(" ", "T");
  return new Date(`${normalized}+08:00`);
}
