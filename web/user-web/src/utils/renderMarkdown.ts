import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";

/**
 * 与常见 Markdown 预览器对齐：不做「单换行变 &lt;br&gt;」扩展（breaks:false），
 * 避免列表项、加粗标题与正文之间被拉出不自然的大段空白。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
});

const fenceDefault = md.renderer.rules.fence;
if (!fenceDefault) {
  throw new Error("markdown-it: default fence renderer missing");
}
md.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const inner = fenceDefault(tokens, idx, options, env, self);
  const token = tokens[idx];
  const info = token.info ? md.utils.unescapeAll(String(token.info)).trim() : "";
  const lang = info ? info.split(/\s+/)[0] : "";
  const langHtml = lang ? `<span class="md-code-lang">${md.utils.escapeHtml(lang)}</span>` : "";
  return (
      `<div class="md-code-block">` +
      `<div class="md-code-toolbar">${langHtml}` +
      `<button type="button" class="md-code-copy-btn" aria-label="复制代码" title="复制">复制</button>` +
      `</div>` +
      inner +
      `</div>`
  );
};

function normalizeAssistantMarkdownSource(source: string): string {
  let s = source ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  // 模型偶发输出连续大量空行，压成最多双换行，减轻块级元素堆叠
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

let mdCopyListenerAttached = false;

function copyTextToClipboard(text: string): Promise<void> {
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text);
  }
  return new Promise((resolve, reject) => {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.left = "-9999px";
    ta.style.top = "0";
    document.body.appendChild(ta);
    ta.select();
    try {
      if (document.execCommand("copy")) {
        resolve();
      } else {
        reject(new Error("execCommand copy failed"));
      }
    } catch (e) {
      reject(e);
    } finally {
      ta.remove();
    }
  });
}

function onMarkdownCodeCopyClick(ev: MouseEvent): void {
  const t = ev.target as HTMLElement | null;
  if (!t) {
    return;
  }
  const btn = t.closest("button.md-code-copy-btn");
  if (!btn) {
    return;
  }
  ev.preventDefault();
  ev.stopPropagation();
  const block = btn.closest(".md-code-block");
  const codeEl = block?.querySelector("pre code") as HTMLElement | null;
  const text = (codeEl?.innerText ?? codeEl?.textContent ?? "").replace(/\u00a0/g, " ");
  if (!text) {
    return;
  }
  const labelDefault = "复制";
  const labelDone = "已复制";
  void copyTextToClipboard(text).then(
      () => {
        btn.textContent = labelDone;
        window.setTimeout(() => {
          btn.textContent = labelDefault;
        }, 1600);
      },
      () => {
        btn.textContent = "失败";
        window.setTimeout(() => {
          btn.textContent = labelDefault;
        }, 1600);
      },
  );
}

function ensureMarkdownCodeCopyListener(): void {
  if (mdCopyListenerAttached || typeof document === "undefined") {
    return;
  }
  mdCopyListenerAttached = true;
  document.addEventListener("click", onMarkdownCodeCopyClick);
}

/**
 * 将助手 Markdown 转为可安全 v-html 的 HTML（禁止原始 HTML 标签，仅解析 MD 语法）。
 * 围栏代码块会带「复制」按钮（委托到 document，一次注册）。
 */
export function renderMarkdownToSafeHtml(source: string): string {
  ensureMarkdownCodeCopyListener();
  const raw = md.render(normalizeAssistantMarkdownSource(source));
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ["target", "rel", "type", "title", "aria-label"],
    ADD_TAGS: ["button"],
  });
}
