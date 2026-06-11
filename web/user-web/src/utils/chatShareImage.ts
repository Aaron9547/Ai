import { toBlob, toPng } from "html-to-image";
import {
  chatExternalImageProxyUrl,
  isExternalHttpImageUrl,
} from "@/utils/chatExternalImageProxy";

/** 分享卡片宽约 480px；截图前将过宽 Markdown 图片缩至该宽度，避免 canvas 过大导致失败 */
const CAPTURE_MAX_IMAGE_WIDTH = 960;

const CAPTURE_OPTS = {
  pixelRatio: 2,
  cacheBust: false,
  includeQueryParams: true,
  skipFonts: false,
  backgroundColor: "#ffffff",
  fetchRequestInit: {
    credentials: "omit" as RequestCredentials,
    mode: "cors" as RequestMode,
  },
  onImageErrorHandler: () => undefined,
};

function isDataUrl(url: string): boolean {
  return /^data:/i.test(url);
}

function isSameOriginUrl(url: string): boolean {
  try {
    if (url.startsWith("/")) {
      return true;
    }
    const parsed = new URL(url, window.location.origin);
    if (parsed.origin === window.location.origin) {
      return true;
    }
    if (url.includes("/open/v1/chat/external-images/proxy")) {
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

function resolveFetchableImageUrl(url: string): string {
  if (isExternalHttpImageUrl(url)) {
    return chatExternalImageProxyUrl(url);
  }
  return url;
}

function isImageBlob(blob: Blob): boolean {
  if (blob.type.startsWith("image/")) {
    return true;
  }
  return blob.size > 64;
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error ?? new Error("read blob failed"));
    reader.readAsDataURL(blob);
  });
}

function downscaleDataUrlIfNeeded(dataUrl: string, maxWidth: number): Promise<string> {
  if (typeof document === "undefined") {
    return Promise.resolve(dataUrl);
  }
  return new Promise((resolve) => {
    const probe = new Image();
    probe.onload = () => {
      if (probe.naturalWidth <= maxWidth) {
        resolve(dataUrl);
        return;
      }
      const scale = maxWidth / probe.naturalWidth;
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(probe.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(probe.naturalHeight * scale));
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        resolve(dataUrl);
        return;
      }
      ctx.drawImage(probe, 0, 0, canvas.width, canvas.height);
      try {
        resolve(canvas.toDataURL("image/png"));
      } catch {
        resolve(dataUrl);
      }
    };
    probe.onerror = () => resolve(dataUrl);
    probe.src = dataUrl;
  });
}

/**
 * 仅对同源或已带 crossOrigin 成功加载的图片做 canvas 导出。
 * 跨域且未声明 crossOrigin 时，drawImage 可能导出全绿污染块，必须跳过。
 */
function canvasFromLoadedImage(img: HTMLImageElement): string | null {
  if (!img.complete || img.naturalWidth <= 0) {
    return null;
  }
  const src = (img.currentSrc || img.src || "").trim();
  if (src && !isDataUrl(src) && !isSameOriginUrl(src) && !img.crossOrigin) {
    return null;
  }
  try {
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, img.naturalWidth);
    canvas.height = Math.max(1, img.naturalHeight);
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      return null;
    }
    ctx.drawImage(img, 0, 0);
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

function loadImageWithCrossOrigin(url: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const probe = new Image();
    probe.crossOrigin = "anonymous";
    probe.onload = () => resolve(probe);
    probe.onerror = () => resolve(null);
    probe.src = url;
  });
}

function canvasFromCorsImage(img: HTMLImageElement): string | null {
  if (!img.complete || img.naturalWidth <= 0) {
    return null;
  }
  try {
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, img.naturalWidth);
    canvas.height = Math.max(1, img.naturalHeight);
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      return null;
    }
    ctx.drawImage(img, 0, 0);
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

async function fetchImageAsDataUrl(url: string): Promise<string | null> {
  const fetchUrl = resolveFetchableImageUrl(url);
  const sameOrigin = isSameOriginUrl(fetchUrl);
  const attempts: RequestInit[] = sameOrigin
    ? [{ credentials: "include", mode: "cors" }]
    : [
        { credentials: "omit", mode: "cors" },
        { credentials: "same-origin", mode: "cors" },
      ];

  for (const init of attempts) {
    try {
      const res = await fetch(fetchUrl, init);
      if (!res.ok) {
        continue;
      }
      const blob = await res.blob();
      if (!isImageBlob(blob)) {
        continue;
      }
      return await blobToDataUrl(blob);
    } catch {
      /* 尝试下一种 fetch 策略 */
    }
  }
  return null;
}

async function applyDataUrlToImage(img: HTMLImageElement, dataUrl: string): Promise<void> {
  img.src = await downscaleDataUrlIfNeeded(dataUrl, CAPTURE_MAX_IMAGE_WIDTH);
  img.removeAttribute("srcset");
  await img.decode?.().catch(() => undefined);
}

async function rewriteCrossOriginImagesToProxy(root: HTMLElement): Promise<void> {
  for (const img of root.querySelectorAll("img")) {
    const src = (img.currentSrc || img.getAttribute("src") || "").trim();
    if (!src || isDataUrl(src) || src.startsWith("blob:")) {
      continue;
    }
    if (isExternalHttpImageUrl(src)) {
      img.crossOrigin = "anonymous";
      img.referrerPolicy = "no-referrer";
      img.src = chatExternalImageProxyUrl(src);
      img.removeAttribute("srcset");
    }
  }
}

async function inlineOneImage(img: HTMLImageElement): Promise<void> {
  let src = (img.currentSrc || img.src || "").trim();
  if (!src || isDataUrl(src)) {
    return;
  }

  if (isExternalHttpImageUrl(src)) {
    src = chatExternalImageProxyUrl(src);
    img.crossOrigin = "anonymous";
    img.referrerPolicy = "no-referrer";
    img.src = src;
    img.removeAttribute("srcset");
    await img.decode?.().catch(() => undefined);
  }

  if (src.startsWith("blob:")) {
    let dataUrl = canvasFromLoadedImage(img);
    if (!dataUrl) {
      dataUrl = await fetchImageAsDataUrl(src);
    }
    if (dataUrl) {
      await applyDataUrlToImage(img, dataUrl);
    }
    return;
  }

  const sameOrigin = isSameOriginUrl(src);
  let dataUrl: string | null = null;

  if (sameOrigin) {
    dataUrl = canvasFromLoadedImage(img);
    if (!dataUrl) {
      dataUrl = await fetchImageAsDataUrl(src);
    }
  } else {
    dataUrl = await fetchImageAsDataUrl(src);
    if (!dataUrl) {
      const corsImg = await loadImageWithCrossOrigin(src);
      if (corsImg) {
        dataUrl = canvasFromCorsImage(corsImg);
      }
    }
  }

  if (!dataUrl) {
    return;
  }

  await applyDataUrlToImage(img, dataUrl);
}

async function inlineRemoteImages(root: HTMLElement): Promise<void> {
  const imgs = Array.from(root.querySelectorAll("img"));
  await Promise.all(imgs.map((img) => inlineOneImage(img)));
}

async function waitForImages(root: HTMLElement, timeoutMs = 15_000): Promise<void> {
  const imgs = Array.from(root.querySelectorAll("img"));
  if (!imgs.length) {
    return;
  }
  for (const img of imgs) {
    img.loading = "eager";
  }
  await Promise.race([
    Promise.all(
      imgs.map(
        (img) =>
          new Promise<void>((resolve) => {
            if (img.complete && img.naturalWidth > 0) {
              resolve();
              return;
            }
            img.addEventListener("load", () => resolve(), { once: true });
            img.addEventListener("error", () => resolve(), { once: true });
          }),
      ),
    ),
    new Promise<void>((resolve) => {
      window.setTimeout(resolve, timeoutMs);
    }),
  ]);
}

async function prepareCaptureElement(el: HTMLElement): Promise<void> {
  await rewriteCrossOriginImagesToProxy(el);
  await waitForImages(el);
  await inlineRemoteImages(el);
  await waitForImages(el);
}

export async function captureElementToPngDataUrl(el: HTMLElement): Promise<string> {
  await prepareCaptureElement(el);
  return toPng(el, CAPTURE_OPTS);
}

export async function captureElementToBlob(el: HTMLElement): Promise<Blob | null> {
  await prepareCaptureElement(el);
  return toBlob(el, CAPTURE_OPTS);
}

export async function copyImageBlobToClipboard(blob: Blob): Promise<boolean> {
  if (typeof navigator === "undefined" || !navigator.clipboard?.write) {
    return false;
  }
  try {
    await navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]);
    return true;
  } catch {
    return false;
  }
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
