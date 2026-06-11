import { resolveApiBaseForBrowser } from "@/plugins/http";

function isDataUrl(url: string): boolean {
  return /^data:/i.test(url);
}

function pageOrigin(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return window.location.origin;
}

function apiOrigin(): string {
  const base = resolveApiBaseForBrowser().replace(/\/$/, "");
  if (!base) {
    return pageOrigin();
  }
  try {
    return new URL(base, pageOrigin()).origin;
  } catch {
    return pageOrigin();
  }
}

/** 是否为需走服务端代理的外链 http(s) 图片（排除 data/blob/同源/API 同源）。 */
export function isExternalHttpImageUrl(url: string): boolean {
  const trimmed = (url ?? "").trim();
  if (!trimmed || isDataUrl(trimmed) || trimmed.startsWith("blob:")) {
    return false;
  }
  try {
    const parsed = new URL(trimmed, pageOrigin());
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      return false;
    }
    const origin = parsed.origin;
    return origin !== pageOrigin() && origin !== apiOrigin();
  } catch {
    return false;
  }
}

/** 将外链图片 URL 转为 C 端 open 代理地址（与 API 同源，可 fetch / 截图内联）。 */
export function chatExternalImageProxyUrl(originalUrl: string): string {
  const base = resolveApiBaseForBrowser().replace(/\/$/, "");
  const prefix = base || "";
  return `${prefix}/open/v1/chat/external-images/proxy?url=${encodeURIComponent(originalUrl.trim())}`;
}

export function rewriteExternalImagesInHtml(html: string): string {
  if (!html || typeof DOMParser === "undefined") {
    return html;
  }
  const doc = new DOMParser().parseFromString(`<div id="root">${html}</div>`, "text/html");
  const root = doc.getElementById("root");
  if (!root) {
    return html;
  }
  for (const img of root.querySelectorAll("img")) {
    const src = (img.getAttribute("src") ?? "").trim();
    if (!isExternalHttpImageUrl(src)) {
      continue;
    }
    img.setAttribute("src", chatExternalImageProxyUrl(src));
    img.removeAttribute("srcset");
    img.setAttribute("crossorigin", "anonymous");
    img.setAttribute("referrerpolicy", "no-referrer");
  }
  return root.innerHTML;
}
