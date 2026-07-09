import type { RagWebCrawlExtractConfig } from "@/api/ragAdmin";

/** 与后端 {@code RagWebCrawlExtractConfig} 对齐的单站覆盖表单（空字段表示沿用租户默认）。 */
export type SiteExtractConfigForm = {
  presetLock: "" | "CONSERVATIVE" | "BALANCED" | "AGGRESSIVE";
  extractor: "" | "jsoup" | "readability";
  contentSelector: string;
  titleSelector: string;
  excludeSelectorsText: string;
  discoveryStrategies: string[];
  discoveryMaxDepth: number | undefined;
  jsRenderEnabled: boolean | undefined;
  jsMaxPagesPerRun: number | undefined;
  jsOnlyWhenLinkCountBelow: number | undefined;
  perHostQps: number | undefined;
  perHostConcurrency: number | undefined;
  globalConcurrency: number | undefined;
};

export const SITE_EXTRACT_CONFIG_DEFAULT: SiteExtractConfigForm = {
  presetLock: "",
  extractor: "",
  contentSelector: "",
  titleSelector: "",
  excludeSelectorsText: "",
  discoveryStrategies: [],
  discoveryMaxDepth: undefined,
  jsRenderEnabled: undefined,
  jsMaxPagesPerRun: undefined,
  jsOnlyWhenLinkCountBelow: undefined,
  perHostQps: undefined,
  perHostConcurrency: undefined,
  globalConcurrency: undefined,
};

export const SITE_CRAWL_DISCOVERY_STRATEGY_IDS = [
  "sitemap",
  "html_bfs",
  "list_pagination",
  "cms_vsb",
  "article_heuristic",
  "rss_atom",
  "js_render_discovery",
] as const;

function isRecord(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === "object" && !Array.isArray(v);
}

function num(v: unknown, fallback: number | undefined): number | undefined {
  if (v == null || v === "") return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function boolOrUndef(v: unknown): boolean | undefined {
  if (v === true || v === false) return v;
  return undefined;
}

function trimOrEmpty(v: unknown): string {
  return typeof v === "string" ? v.trim() : "";
}

export function parseSiteExtractConfigForm(config?: RagWebCrawlExtractConfig | null): SiteExtractConfigForm {
  const c = config ?? {};
  const discovery = c.discovery ?? {};
  const jsRender = discovery.jsRender ?? {};
  const politeness = c.politeness ?? {};
  const preset = trimOrEmpty(c.presetLock).toUpperCase();
  const extractor = trimOrEmpty(c.extractor).toLowerCase();

  return {
    presetLock:
      preset === "CONSERVATIVE" || preset === "BALANCED" || preset === "AGGRESSIVE"
        ? preset
        : "",
    extractor: extractor === "jsoup" || extractor === "readability" ? extractor : "",
    contentSelector: trimOrEmpty(c.contentSelector),
    titleSelector: trimOrEmpty(c.titleSelector),
    excludeSelectorsText: (c.excludeSelectors ?? []).map((s) => s.trim()).filter(Boolean).join("\n"),
    discoveryStrategies: (discovery.strategies ?? []).map((s) => s.trim()).filter(Boolean),
    discoveryMaxDepth: num(discovery.maxDepth, undefined),
    jsRenderEnabled: boolOrUndef(jsRender.enabled),
    jsMaxPagesPerRun: num(jsRender.maxPagesPerRun, undefined),
    jsOnlyWhenLinkCountBelow: num(jsRender.onlyWhenLinkCountBelow, undefined),
    perHostQps: num(politeness.perHostQps, undefined),
    perHostConcurrency: num(politeness.perHostConcurrency, undefined),
    globalConcurrency: num(politeness.globalConcurrency, undefined),
  };
}

function pushDiscovery(
  out: RagWebCrawlExtractConfig,
  patch: NonNullable<RagWebCrawlExtractConfig["discovery"]>,
) {
  out.discovery = { ...(out.discovery ?? {}), ...patch };
}

function pushPoliteness(
  out: RagWebCrawlExtractConfig,
  patch: NonNullable<RagWebCrawlExtractConfig["politeness"]>,
) {
  out.politeness = { ...(out.politeness ?? {}), ...patch };
}

/** 将表单序列化为 API 体；全空时返回 {@code undefined}（仅用租户默认）。 */
export function serializeSiteExtractConfigForm(
  form: SiteExtractConfigForm,
): RagWebCrawlExtractConfig | undefined {
  const out: RagWebCrawlExtractConfig = {};

  if (form.presetLock) out.presetLock = form.presetLock;
  if (form.extractor) out.extractor = form.extractor;

  const contentSelector = form.contentSelector.trim();
  if (contentSelector) out.contentSelector = contentSelector;

  const titleSelector = form.titleSelector.trim();
  if (titleSelector) out.titleSelector = titleSelector;

  const excludeSelectors = form.excludeSelectorsText
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (excludeSelectors.length) out.excludeSelectors = excludeSelectors;

  if (form.discoveryStrategies.length) {
    pushDiscovery(out, { strategies: [...form.discoveryStrategies] });
  }
  if (form.discoveryMaxDepth != null) {
    pushDiscovery(out, { maxDepth: form.discoveryMaxDepth });
  }
  if (
    form.jsRenderEnabled != null ||
    form.jsMaxPagesPerRun != null ||
    form.jsOnlyWhenLinkCountBelow != null
  ) {
    const jsRender: NonNullable<RagWebCrawlExtractConfig["discovery"]>["jsRender"] = {};
    if (form.jsRenderEnabled != null) jsRender.enabled = form.jsRenderEnabled;
    if (form.jsMaxPagesPerRun != null) jsRender.maxPagesPerRun = form.jsMaxPagesPerRun;
    if (form.jsOnlyWhenLinkCountBelow != null) {
      jsRender.onlyWhenLinkCountBelow = form.jsOnlyWhenLinkCountBelow;
    }
    pushDiscovery(out, { jsRender });
  }

  if (form.perHostQps != null) pushPoliteness(out, { perHostQps: form.perHostQps });
  if (form.perHostConcurrency != null) {
    pushPoliteness(out, { perHostConcurrency: form.perHostConcurrency });
  }
  if (form.globalConcurrency != null) {
    pushPoliteness(out, { globalConcurrency: form.globalConcurrency });
  }

  return Object.keys(out).length > 0 ? out : undefined;
}
