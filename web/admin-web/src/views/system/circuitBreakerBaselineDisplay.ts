/** 从 {@code outbound.baselineJson} 解析熔断只读行；兼容 Duration 的 ISO-8601 或 {@code { seconds, nano }} 形态。 */

export type CircuitBreakerReadonlyRow = { key: string; label: string; value: string };

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === "object" && !Array.isArray(x);
}

function formatScalar(v: unknown): string {
  if (v == null) return "—";
  if (typeof v === "boolean") return v ? "true" : "false";
  if (typeof v === "number" && Number.isFinite(v)) {
    if (Number.isInteger(v)) return String(v);
    const t = v.toFixed(4).replace(/\.?0+$/, "");
    return t;
  }
  const s = String(v).trim();
  return s.length ? s : "—";
}

function formatPercent(v: unknown): string {
  if (typeof v !== "number" || !Number.isFinite(v)) return "—";
  const t = v.toFixed(2).replace(/\.?0+$/, "");
  return `${t}%`;
}

function secondsToHuman(totalSec: number, zh: boolean): string {
  const s = Math.max(0, Math.round(totalSec));
  if (s < 60) return zh ? `${s} 秒` : `${s} s`;
  const m = Math.floor(s / 60);
  const r = s % 60;
  if (r === 0) return zh ? `${m} 分钟` : `${m} min`;
  return zh ? `${m} 分钟 ${r} 秒` : `${m} min ${r} s`;
}

/** ISO-8601 duration：PT30S、PT15M、PT1H2M3S 等 */
function isoDurationToSeconds(iso: string): number | null {
  const t = iso.trim();
  if (!/^PT/i.test(t)) return null;
  let sec = 0;
  const h = t.match(/(\d+)H/i);
  const m = t.match(/(\d+)M/i);
  const s = t.match(/(\d+(?:\.\d+)?)S/i);
  if (h) sec += parseInt(h[1], 10) * 3600;
  if (m) sec += parseInt(m[1], 10) * 60;
  if (s) sec += parseFloat(s[1]);
  if (!h && !m && !s) return null;
  return sec;
}

export function formatJavaDurationLike(v: unknown, zh: boolean): string {
  if (v == null) return "—";
  if (Array.isArray(v) && v.length >= 1) {
    const sec = Number(v[0]);
    const nano = v.length >= 2 ? Number(v[1]) : 0;
    if (Number.isFinite(sec) && Number.isFinite(nano)) {
      return secondsToHuman(sec + nano / 1e9, zh);
    }
  }
  if (typeof v === "string") {
    const st = v.trim();
    if (!st) return "—";
    if (/^PT/i.test(st)) {
      const sec = isoDurationToSeconds(st);
      return sec == null ? st : secondsToHuman(sec, zh);
    }
    return st;
  }
  if (isRecord(v) && typeof v.seconds === "number" && Number.isFinite(v.seconds)) {
    const nano = typeof v.nano === "number" && Number.isFinite(v.nano) ? v.nano : 0;
    const sec = v.seconds + nano / 1e9;
    return secondsToHuman(sec, zh);
  }
  return formatScalar(v);
}

const CB_KEYS = [
  "enabled",
  "slidingWindowSize",
  "minimumNumberOfCalls",
  "failureRateThreshold",
  "waitDurationInOpenState",
  "slowCallDurationThreshold",
  "slowCallRateThreshold",
  "permittedNumberOfCallsInHalfOpenState",
] as const;

export function buildCircuitBreakerBaselineRows(
  baseline: unknown,
  translate: (key: string) => string,
  zh: boolean,
): CircuitBreakerReadonlyRow[] {
  if (!isRecord(baseline)) return [];
  const cb = baseline.circuitBreaker;
  if (!isRecord(cb)) return [];

  const tf = (v: unknown) => (v === true ? translate("common.yes") : translate("common.no"));

  const rows: CircuitBreakerReadonlyRow[] = [];
  for (const key of CB_KEYS) {
    const raw = cb[key];
    let value: string;
    if (key === "enabled") {
      if (raw === true || raw === "true") value = tf(true);
      else if (raw === false || raw === "false") value = tf(false);
      else value = formatScalar(raw);
    } else if (key === "waitDurationInOpenState" || key === "slowCallDurationThreshold") {
      value = formatJavaDurationLike(raw, zh);
    } else if (key === "failureRateThreshold" || key === "slowCallRateThreshold") {
      value = formatPercent(raw);
    } else {
      value = formatScalar(raw);
    }
    rows.push({
      key,
      label: translate(`admin.shell.circuitBreaker.fields.${key}`),
      value,
    });
  }
  return rows;
}
