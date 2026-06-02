/** 解析画像标签 {@code INTEREST_NEWS_JSON}（今日智能洞察点击记录）。 */
export type InterestNewsEntry = {
  itemId: string;
  tag: string;
  title: string;
  summary: string;
  url: string;
};

export function parseInterestNewsJson(raw: string | null | undefined): InterestNewsEntry[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const arr = JSON.parse(raw) as unknown;
    if (!Array.isArray(arr)) {
      return [];
    }
    const out: InterestNewsEntry[] = [];
    for (const item of arr) {
      if (item === null || typeof item !== "object") continue;
      const o = item as Record<string, unknown>;
      out.push({
        itemId: String(o.itemId ?? "").trim(),
        tag: String(o.tag ?? "").trim(),
        title: String(o.title ?? "").trim(),
        summary: String(o.summary ?? "").trim(),
        url: String(o.url ?? "").trim(),
      });
    }
    return out;
  } catch {
    return [];
  }
}

export function isInterestNewsJsonTag(code: string): boolean {
  return (code ?? "").trim() === "INTEREST_NEWS_JSON";
}
