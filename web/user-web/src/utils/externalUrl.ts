/** 外链规范化：补全 scheme、去掉尾部标点，供推荐卡片等使用。 */
export function normalizeExternalUrl(raw: string | null | undefined): string {
  let u = (raw ?? "").trim();
  if (!u) return "";
  u = u.replace(/[\s.,;:!?）)】」』>]+$/u, "");
  if (/^www\./i.test(u)) {
    u = `https://${u}`;
  } else if (u.startsWith("//")) {
    u = `https:${u}`;
  }
  return u;
}

export function isHttpExternalUrl(url: string): boolean {
  const u = normalizeExternalUrl(url);
  if (!u) return false;
  try {
    const parsed = new URL(u);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}
