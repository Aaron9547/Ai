/** 复制到系统剪贴板（管理端 CodeBlock 等）。 */
export async function copyTextToUserClipboard(text: string): Promise<boolean> {
  const s = text ?? "";
  if (typeof document === "undefined") {
    return false;
  }
  const secure = typeof window !== "undefined" && window.isSecureContext;
  const hasAsync =
    typeof navigator !== "undefined" &&
    navigator.clipboard != null &&
    typeof navigator.clipboard.writeText === "function";

  if (secure && hasAsync) {
    try {
      await navigator.clipboard.writeText(s);
      return true;
    } catch {
      /* 降级 */
    }
  }
  return legacyExecCommandCopy(s);
}

function legacyExecCommandCopy(s: string): boolean {
  const ta = document.createElement("textarea");
  ta.value = s;
  ta.setAttribute("readonly", "readonly");
  ta.style.cssText =
    "position:fixed;left:0;top:0;width:2px;height:2px;padding:0;margin:0;border:none;outline:none;" +
    "box-shadow:none;background:transparent;opacity:0;pointer-events:none;z-index:-1;";
  document.body.appendChild(ta);
  ta.focus();
  ta.select();
  ta.setSelectionRange(0, s.length);
  try {
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    document.body.removeChild(ta);
  }
}
