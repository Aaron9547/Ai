/**
 * 复制到系统剪贴板。
 * - 安全上下文（HTTPS / localhost 等）优先 {@link navigator.clipboard.writeText}。
 * - 非安全上下文（如 http://192.168.x.x）Clipboard API 不可用，直接用 textarea + {@link Document#execCommand}，
 *   须在用户手势（点击）栈内同步调用，故非安全时跳过 await，避免手势丢失导致降级也失败。
 */
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
      /* 权限等失败时再尝试同步降级 */
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
