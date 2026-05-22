/**
 * 与后端 SITE_CRAWL_RUNTIME_JSON（discovery / politeness / fetch / extract / ingest）对齐。
 */

export type SiteCrawlRuntimeForm = {
  maxDepthDefault: number;
  maxExplorePages: number;
  maxArticlesPerRun: number;
  jsRenderEnabled: boolean;
  perHostQps: number;
  perHostConcurrency: number;
  globalConcurrency: number;
  retryMax: number;
  maxBodyBytes: number;
  timeoutMs: number;
  readabilityFallbackEnabled: boolean;
  minMarkdownChars: number;
};

/** 与 BALANCED 档位模板一致，作解析兜底。 */
export const SITE_CRAWL_RUNTIME_DEFAULT: SiteCrawlRuntimeForm = {
  maxDepthDefault: 2,
  maxExplorePages: 2000,
  maxArticlesPerRun: 2500,
  jsRenderEnabled: false,
  perHostQps: 0.5,
  perHostConcurrency: 2,
  globalConcurrency: 16,
  retryMax: 3,
  maxBodyBytes: 5_242_880,
  timeoutMs: 15_000,
  readabilityFallbackEnabled: false,
  minMarkdownChars: 48,
};

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === "object" && !Array.isArray(x);
}

function num(v: unknown, d: number): number {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    if (Number.isFinite(n)) return n;
  }
  return d;
}

function bool(v: unknown, d: boolean): boolean {
  if (typeof v === "boolean") return v;
  return d;
}

export function parseSiteCrawlRuntimeForm(raw: string | undefined | null): SiteCrawlRuntimeForm {
  const d = { ...SITE_CRAWL_RUNTIME_DEFAULT };
  const t = String(raw ?? "").trim();
  if (!t || t === "{}") return d;
  try {
    const root = JSON.parse(t) as Record<string, unknown>;
    const discovery = isRecord(root.discovery) ? root.discovery : {};
    const politeness = isRecord(root.politeness) ? root.politeness : {};
    const fetch = isRecord(root.fetch) ? root.fetch : {};
    const extract = isRecord(root.extract) ? root.extract : {};
    const ingest = isRecord(root.ingest) ? root.ingest : {};
    const jsRender = isRecord(discovery.jsRender) ? discovery.jsRender : {};
    d.maxDepthDefault = num(discovery.maxDepthDefault, d.maxDepthDefault);
    d.maxExplorePages = num(discovery.maxExplorePages, d.maxExplorePages);
    d.maxArticlesPerRun = num(discovery.maxArticlesPerRun, d.maxArticlesPerRun);
    d.jsRenderEnabled = bool(jsRender.enabled, d.jsRenderEnabled);
    d.perHostQps = num(politeness.perHostQps, d.perHostQps);
    d.perHostConcurrency = num(politeness.perHostConcurrency, d.perHostConcurrency);
    d.globalConcurrency = num(politeness.globalConcurrency, d.globalConcurrency);
    d.retryMax = num(politeness.retryMax, d.retryMax);
    d.maxBodyBytes = num(fetch.maxBodyBytes, d.maxBodyBytes);
    d.timeoutMs = num(fetch.timeoutMs, d.timeoutMs);
    d.readabilityFallbackEnabled = bool(extract.readabilityFallbackEnabled, d.readabilityFallbackEnabled);
    d.minMarkdownChars = num(ingest.minMarkdownChars, d.minMarkdownChars);
    return d;
  } catch {
    return d;
  }
}

/** 写出完整运行时对象（保存时与档位模板同形，便于服务端 merge）。 */
export function serializeSiteCrawlRuntimeJson(form: SiteCrawlRuntimeForm): string {
  return JSON.stringify({
    discovery: {
      maxDepthDefault: Math.max(1, Math.min(6, Math.floor(form.maxDepthDefault))),
      maxExplorePages: Math.max(50, Math.min(20_000, Math.floor(form.maxExplorePages))),
      maxArticlesPerRun: Math.max(0, Math.min(20_000, Math.floor(form.maxArticlesPerRun))),
      jsRender: { enabled: !!form.jsRenderEnabled },
    },
    politeness: {
      perHostQps: Math.max(0.05, Math.min(5, form.perHostQps)),
      perHostConcurrency: Math.max(1, Math.min(64, Math.floor(form.perHostConcurrency))),
      globalConcurrency: Math.max(1, Math.min(128, Math.floor(form.globalConcurrency))),
      retryMax: Math.max(0, Math.min(8, Math.floor(form.retryMax))),
    },
    fetch: {
      maxBodyBytes: Math.max(65536, Math.min(20_971_520, Math.floor(form.maxBodyBytes))),
      timeoutMs: Math.max(3000, Math.min(120_000, Math.floor(form.timeoutMs))),
    },
    extract: {
      readabilityFallbackEnabled: !!form.readabilityFallbackEnabled,
    },
    ingest: {
      minMarkdownChars: Math.max(1, Math.min(5000, Math.floor(form.minMarkdownChars))),
    },
  });
}
