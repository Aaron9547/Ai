import { toBlob, toPng } from "html-to-image";

const CAPTURE_OPTS = {
  pixelRatio: 2,
  cacheBust: true,
  skipFonts: false,
};

export async function captureElementToPngDataUrl(el: HTMLElement): Promise<string> {
  return toPng(el, CAPTURE_OPTS);
}

export async function captureElementToBlob(el: HTMLElement): Promise<Blob | null> {
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
