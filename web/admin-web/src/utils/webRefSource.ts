import type { WebGroundingReferenceItem } from "@/api/chatStarterPrompt";

/** 与后端 {@code WebSearchFixedSource} / Ark 落库 {@code sourceKey} 对齐 */
export type WebRefSourceKey =
  | "VOLCENGINE_ARK_BOT"
  | "BAIDU_NEWS_HTML"
  | "DUCKDUCKGO_HTML"
  | "GOOGLE_NEWS_RSS"
  | "WIKIPEDIA_REST"
  | "OTHER";

export const WEB_REF_SOURCE_TAB_ORDER: WebRefSourceKey[] = [
  "VOLCENGINE_ARK_BOT",
  "BAIDU_NEWS_HTML",
  "DUCKDUCKGO_HTML",
  "GOOGLE_NEWS_RSS",
  "WIKIPEDIA_REST",
  "OTHER",
];

export interface WebRefSourceGroup {
  key: WebRefSourceKey;
  items: WebGroundingReferenceItem[];
}

export interface WebSummarySection {
  label: string;
  body: string;
  sourceKey: WebRefSourceKey;
}

const SOURCE_KEYS = new Set<string>(WEB_REF_SOURCE_TAB_ORDER);

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return "";
  }
}

/** 无 {@code sourceKey} 时按 URL / 站点名推断（历史数据兼容） */
export function classifyWebRefSource(ref: {
  url?: string;
  siteName?: string | null;
  sourceKey?: string | null;
}): WebRefSourceKey {
  const explicit = (ref.sourceKey ?? "").trim();
  if (explicit && SOURCE_KEYS.has(explicit)) {
    return explicit as WebRefSourceKey;
  }
  const url = (ref.url ?? "").toLowerCase();
  const host = hostOf(url);
  const site = (ref.siteName ?? "").toLowerCase();

  if (
    host.includes("baidu.com") ||
    url.includes("baidu.com") ||
    site.includes("百度")
  ) {
    return "BAIDU_NEWS_HTML";
  }
  if (
    host.includes("duckduckgo.com") ||
    url.includes("duckduckgo.com") ||
    site.includes("duckduckgo")
  ) {
    return "DUCKDUCKGO_HTML";
  }
  if (
    host.includes("wikipedia.org") ||
    host.includes("wikimedia.org") ||
    site.includes("wikipedia") ||
    site.includes("维基")
  ) {
    return "WIKIPEDIA_REST";
  }
  if (
    host.includes("google.com") ||
    host.includes("news.google") ||
    url.includes("news.google") ||
    site.includes("google news")
  ) {
    return "GOOGLE_NEWS_RSS";
  }
  if (
    host.includes("volces.com") ||
    host.includes("doubao.com") ||
    url.includes("volcengine") ||
    url.includes("upstream_biz=volcengine")
  ) {
    return "VOLCENGINE_ARK_BOT";
  }
  return "OTHER";
}

/** 按 URL（及无 URL 时标题）去重，保留先出现的条目。 */
export function dedupeWebRefs(
  refs: WebGroundingReferenceItem[] | undefined | null,
): WebGroundingReferenceItem[] {
  const seen = new Set<string>();
  const out: WebGroundingReferenceItem[] = [];
  for (const ref of refs ?? []) {
    const url = (ref.url ?? "").trim().toLowerCase();
    const key = url || `title:${(ref.title ?? "").trim()}`;
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(ref);
  }
  return out;
}

function normalizeSummaryCompare(text: string): string {
  return text.replace(/\s+/g, "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
}

function extractSummaryDateKey(text: string): string | null {
  const head = text.slice(0, 280);
  const m = head.match(/(\d{4})年(\d{1,2})月(\d{1,2})日/);
  if (!m) return null;
  const month = m[2].padStart(2, "0");
  const day = m[3].padStart(2, "0");
  return `${m[1]}-${month}-${day}`;
}

function tokenOverlapRatio(a: string, b: string): number {
  if (!a || !b) return 0;
  const grams = (s: string) => {
    const set = new Set<string>();
    for (let i = 0; i + 4 <= s.length; i += 4) {
      set.add(s.slice(i, i + 4));
    }
    return set;
  };
  const ta = grams(a);
  const tb = grams(b);
  if (!ta.size || !tb.size) return 0;
  let inter = 0;
  for (const g of ta) {
    if (tb.has(g)) inter++;
  }
  return (2 * inter) / (ta.size + tb.size);
}

function findDuplicateSummaryIndex(kept: WebSummarySection[], candidate: WebSummarySection): number {
  const normC = normalizeSummaryCompare(candidate.body);
  const dateC = extractSummaryDateKey(candidate.body);
  for (let i = 0; i < kept.length; i++) {
    const existing = kept[i];
    const normE = normalizeSummaryCompare(existing.body);
    if (normE === normC) return i;
    if (normE.includes(normC) || normC.includes(normE)) return i;
    if (dateC && dateC === extractSummaryDateKey(existing.body)) return i;
    if (normC.length >= 120 && normE.length >= 120 && tokenOverlapRatio(normE, normC) >= 0.55) {
      return i;
    }
  }
  return -1;
}

/** 展示用：分段、去重、生成可读标题（优先日期 / [来源] / 首行）。 */
export function buildDisplaySummarySections(summaryText: string): {
  sections: WebSummarySection[];
  rawCount: number;
} {
  const raw = parseSummarySections(summaryText);
  const kept: WebSummarySection[] = [];
  for (const sec of raw) {
    const labeled = {
      ...sec,
      label: sec.label || extractSummarySectionLabel(sec.body),
    };
    const idx = findDuplicateSummaryIndex(kept, labeled);
    if (idx >= 0) {
      if (labeled.body.length > kept[idx].body.length) {
        kept[idx] = labeled;
      }
      continue;
    }
    kept.push(labeled);
  }
  return { sections: kept, rawCount: raw.length };
}

export function extractSummarySectionLabel(body: string): string {
  const bracket = body.match(/^\[([^\]]+)\]/);
  if (bracket) return bracket[1].trim();
  const first = (body.split("\n").find((l) => l.trim()) ?? "").trim();
  const dateMatch = first.match(/(\d{4})年(\d{1,2})月(\d{1,2})日/);
  if (dateMatch) {
    const month = dateMatch[2].padStart(2, "0");
    const day = dateMatch[3].padStart(2, "0");
    return `${dateMatch[1]}-${month}-${day}`;
  }
  if (first.length > 56) return `${first.slice(0, 52)}…`;
  return first || "";
}

export function groupWebRefsBySource(
  refs: WebGroundingReferenceItem[] | undefined | null,
): WebRefSourceGroup[] {
  const buckets = new Map<WebRefSourceKey, WebGroundingReferenceItem[]>();
  for (const key of WEB_REF_SOURCE_TAB_ORDER) {
    buckets.set(key, []);
  }
  for (const ref of dedupeWebRefs(refs)) {
    const key = classifyWebRefSource(ref);
    buckets.get(key)!.push(ref);
  }
  return WEB_REF_SOURCE_TAB_ORDER.filter((key) => (buckets.get(key)?.length ?? 0) > 0).map(
    (key) => ({ key, items: buckets.get(key)! }),
  );
}

const SUMMARY_LABEL_TO_KEY: Record<string, WebRefSourceKey> = {
  百度新闻: "BAIDU_NEWS_HTML",
  duckduckgo: "DUCKDUCKGO_HTML",
  wikipedia: "WIKIPEDIA_REST",
  "google news": "GOOGLE_NEWS_RSS",
};

export function summaryLabelToSourceKey(label: string): WebRefSourceKey {
  const norm = label.trim().toLowerCase();
  if (!norm) return "OTHER";
  for (const [k, v] of Object.entries(SUMMARY_LABEL_TO_KEY)) {
    if (norm === k.toLowerCase() || norm.includes(k.toLowerCase())) {
      return v;
    }
  }
  if (norm.includes("火山") || norm.includes("ark") || norm.includes("豆包")) {
    return "VOLCENGINE_ARK_BOT";
  }
  if (norm.includes("百度")) return "BAIDU_NEWS_HTML";
  if (norm.includes("duck")) return "DUCKDUCKGO_HTML";
  if (norm.includes("wiki") || norm.includes("维基")) return "WIKIPEDIA_REST";
  if (norm.includes("google")) return "GOOGLE_NEWS_RSS";
  return "OTHER";
}

export function parseSummarySections(summaryText: string): WebSummarySection[] {
  const raw = (summaryText ?? "").trim();
  if (!raw) return [];
  return raw
    .split(/\n\n---\n\n/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const m = part.match(/^\[([^\]]+)\]\n?([\s\S]*)$/);
      if (m) {
        const label = m[1].trim();
        return {
          label,
          body: (m[2] ?? "").trim(),
          sourceKey: summaryLabelToSourceKey(label),
        };
      }
      return { label: "", body: part, sourceKey: "OTHER" as WebRefSourceKey };
    });
}
