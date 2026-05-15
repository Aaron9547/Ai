/** 与后端 {@code AiOutboundResilienceProperties} 可对齐字段一致（不含 circuitBreaker）。 */

export type TenantQuarantineForm = {
  enabled: boolean;
  windowSeconds: number;
  threshold: number;
  cooldownSeconds: number;
  userMessage: string;
};

export type OutboundResilienceForm = {
  enabled: boolean;
  streamMaxAttempts: number;
  streamInitialBackoffMs: number;
  streamMaxBackoffMs: number;
  syncMaxAttempts: number;
  syncInitialBackoffMs: number;
  syncMaxBackoffMs: number;
  jitterRatio: number;
  connectTimeoutSeconds: number;
  streamRequestTimeoutSeconds: number;
  streamFirstLineTimeoutSeconds: number;
  streamLineIdleTimeoutSeconds: number;
  tenantQuarantine: TenantQuarantineForm;
  /** 逗号/空格分隔，如 408, 429, 500 */
  retryableHttpStatusesText: string;
};

const JAVA_DEFAULTS: Omit<OutboundResilienceForm, "retryableHttpStatusesText"> & {
  retryableHttpStatuses: number[];
} = {
  enabled: true,
  streamMaxAttempts: 3,
  streamInitialBackoffMs: 250,
  streamMaxBackoffMs: 8000,
  syncMaxAttempts: 3,
  syncInitialBackoffMs: 200,
  syncMaxBackoffMs: 5000,
  jitterRatio: 0.2,
  connectTimeoutSeconds: 30,
  streamRequestTimeoutSeconds: 300,
  streamFirstLineTimeoutSeconds: 120,
  streamLineIdleTimeoutSeconds: 120,
  tenantQuarantine: {
    enabled: true,
    windowSeconds: 120,
    threshold: 3,
    cooldownSeconds: 180,
    userMessage: "",
  },
  retryableHttpStatuses: [408, 425, 429, 500, 502, 503, 504],
};

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === "object" && !Array.isArray(x);
}

function asBool(v: unknown, d: boolean): boolean {
  if (typeof v === "boolean") return v;
  return d;
}

function asNum(v: unknown, d: number): number {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : d;
}

function asStr(v: unknown, d: string): string {
  if (typeof v === "string") return v;
  if (v == null) return d;
  return String(v);
}

export function parseRetryableStatusesText(text: string): number[] {
  return text
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => parseInt(s, 10))
    .filter((n) => Number.isFinite(n) && n > 0 && n < 1000);
}

export function formatRetryableStatuses(arr: number[]): string {
  return [...new Set(arr)].sort((a, b) => a - b).join(", ");
}

/** 用当前生效配置（effectiveMerged）填充表单展示。 */
export function readOutboundFormFromEffective(effective: unknown): OutboundResilienceForm {
  const o = isRecord(effective) ? effective : {};
  const tq = isRecord(o.tenantQuarantine) ? o.tenantQuarantine : {};
  const d = JAVA_DEFAULTS;
  let statuses: number[] = [];
  if (Array.isArray(o.retryableHttpStatuses)) {
    statuses = o.retryableHttpStatuses.map((x) => Number(x)).filter((n) => Number.isFinite(n));
  }
  if (!statuses.length) {
    statuses = [...d.retryableHttpStatuses];
  }
  return {
    enabled: asBool(o.enabled, d.enabled),
    streamMaxAttempts: asNum(o.streamMaxAttempts, d.streamMaxAttempts),
    streamInitialBackoffMs: asNum(o.streamInitialBackoffMs, d.streamInitialBackoffMs),
    streamMaxBackoffMs: asNum(o.streamMaxBackoffMs, d.streamMaxBackoffMs),
    syncMaxAttempts: asNum(o.syncMaxAttempts, d.syncMaxAttempts),
    syncInitialBackoffMs: asNum(o.syncInitialBackoffMs, d.syncInitialBackoffMs),
    syncMaxBackoffMs: asNum(o.syncMaxBackoffMs, d.syncMaxBackoffMs),
    jitterRatio: asNum(o.jitterRatio, d.jitterRatio),
    connectTimeoutSeconds: asNum(o.connectTimeoutSeconds, d.connectTimeoutSeconds),
    streamRequestTimeoutSeconds: asNum(o.streamRequestTimeoutSeconds, d.streamRequestTimeoutSeconds),
    streamFirstLineTimeoutSeconds: asNum(o.streamFirstLineTimeoutSeconds, d.streamFirstLineTimeoutSeconds),
    streamLineIdleTimeoutSeconds: asNum(o.streamLineIdleTimeoutSeconds, d.streamLineIdleTimeoutSeconds),
    tenantQuarantine: {
      enabled: asBool(tq.enabled, d.tenantQuarantine.enabled),
      windowSeconds: asNum(tq.windowSeconds, d.tenantQuarantine.windowSeconds),
      threshold: asNum(tq.threshold, d.tenantQuarantine.threshold),
      cooldownSeconds: asNum(tq.cooldownSeconds, d.tenantQuarantine.cooldownSeconds),
      userMessage: asStr(tq.userMessage, d.tenantQuarantine.userMessage),
    },
    retryableHttpStatusesText: formatRetryableStatuses(statuses),
  };
}

function numClose(a: unknown, b: unknown, eps: number): boolean {
  const x = typeof a === "number" ? a : Number(a);
  const y = typeof b === "number" ? b : Number(b);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return false;
  return Math.abs(x - y) <= eps;
}

function sortedStatusKey(arr: unknown): string {
  if (!Array.isArray(arr)) return "";
  const nums = arr.map((x) => Number(x)).filter((n) => Number.isFinite(n));
  return [...new Set(nums)].sort((a, b) => a - b).join(",");
}

function baselineRetryable(baseline: Record<string, unknown>): number[] {
  const raw = baseline.retryableHttpStatuses;
  if (!Array.isArray(raw)) return [...JAVA_DEFAULTS.retryableHttpStatuses];
  const nums = raw.map((x) => Number(x)).filter((n) => Number.isFinite(n));
  return nums.length ? nums : [...JAVA_DEFAULTS.retryableHttpStatuses];
}

/** 由表单构造完整对象（不含 circuitBreaker），用于与 baseline 做差分。 */
export function fullOutboundFromForm(form: OutboundResilienceForm): Record<string, unknown> {
  const retry = parseRetryableStatusesText(form.retryableHttpStatusesText);
  const retryFinal = retry.length ? retry : [...JAVA_DEFAULTS.retryableHttpStatuses];
  return {
    enabled: form.enabled,
    streamMaxAttempts: form.streamMaxAttempts,
    streamInitialBackoffMs: form.streamInitialBackoffMs,
    streamMaxBackoffMs: form.streamMaxBackoffMs,
    syncMaxAttempts: form.syncMaxAttempts,
    syncInitialBackoffMs: form.syncInitialBackoffMs,
    syncMaxBackoffMs: form.syncMaxBackoffMs,
    jitterRatio: form.jitterRatio,
    connectTimeoutSeconds: form.connectTimeoutSeconds,
    streamRequestTimeoutSeconds: form.streamRequestTimeoutSeconds,
    streamFirstLineTimeoutSeconds: form.streamFirstLineTimeoutSeconds,
    streamLineIdleTimeoutSeconds: form.streamLineIdleTimeoutSeconds,
    tenantQuarantine: {
      enabled: form.tenantQuarantine.enabled,
      windowSeconds: form.tenantQuarantine.windowSeconds,
      threshold: form.tenantQuarantine.threshold,
      cooldownSeconds: form.tenantQuarantine.cooldownSeconds,
      userMessage: form.tenantQuarantine.userMessage.trim(),
    },
    retryableHttpStatuses: retryFinal,
  };
}

/**
 * 相对服务器默认（baselineJson）生成租户覆盖 JSON；与全量相同则返回 "{}".
 * baseline 中可能含 circuitBreaker，比较时忽略。
 */
export function buildTenantOutboundOverlayJson(form: OutboundResilienceForm, baseline: unknown): string {
  const b = isRecord(baseline) ? baseline : {};
  const full = fullOutboundFromForm(form);

  const overlay: Record<string, unknown> = {};

  const scalarKeys = [
    "enabled",
    "streamMaxAttempts",
    "streamInitialBackoffMs",
    "streamMaxBackoffMs",
    "syncMaxAttempts",
    "syncInitialBackoffMs",
    "syncMaxBackoffMs",
    "connectTimeoutSeconds",
    "streamRequestTimeoutSeconds",
    "streamFirstLineTimeoutSeconds",
    "streamLineIdleTimeoutSeconds",
  ] as const;

  for (const k of scalarKeys) {
    if (full[k] !== b[k]) {
      overlay[k] = full[k];
    }
  }

  if (!numClose(full.jitterRatio, b.jitterRatio, 1e-6)) {
    overlay.jitterRatio = full.jitterRatio;
  }

  const bq = isRecord(b.tenantQuarantine) ? b.tenantQuarantine : {};
  const fq = full.tenantQuarantine as Record<string, unknown>;
  const tqOverlay: Record<string, unknown> = {};
  for (const k of ["enabled", "windowSeconds", "threshold", "cooldownSeconds", "userMessage"] as const) {
    const fv = fq[k];
    const bv = bq[k];
    if (k === "userMessage") {
      const fs = typeof fv === "string" ? fv.trim() : "";
      const bs = typeof bv === "string" ? bv.trim() : bv == null ? "" : String(bv);
      if (fs !== bs) tqOverlay[k] = fv;
    } else if (fv !== bv) {
      tqOverlay[k] = fv;
    }
  }
  if (Object.keys(tqOverlay).length) {
    overlay.tenantQuarantine = tqOverlay;
  }

  const fr = full.retryableHttpStatuses as number[];
  const br = baselineRetryable(b);
  if (sortedStatusKey(fr) !== sortedStatusKey(br)) {
    overlay.retryableHttpStatuses = [...new Set(fr)].sort((a, b) => a - b);
  }

  return Object.keys(overlay).length ? JSON.stringify(overlay) : "{}";
}

/** 保存前校验：数值范围与可重试状态码非空。 */
export function validateOutboundForm(form: OutboundResilienceForm): string | null {
  const retry = parseRetryableStatusesText(form.retryableHttpStatusesText);
  if (!retry.length) {
    return "retryStatuses";
  }
  if (form.streamMaxAttempts < 0 || form.syncMaxAttempts < 0) return "attempts";
  if (
    form.connectTimeoutSeconds < 1 ||
    form.streamRequestTimeoutSeconds < 1 ||
    form.streamFirstLineTimeoutSeconds < 1 ||
    form.streamLineIdleTimeoutSeconds < 1
  ) {
    return "timeouts";
  }
  if (form.tenantQuarantine.windowSeconds < 1 || form.tenantQuarantine.threshold < 1) return "quarantine";
  if (form.tenantQuarantine.cooldownSeconds < 1) return "quarantine";
  if (form.jitterRatio < 0 || form.jitterRatio > 1) return "jitter";
  return null;
}
