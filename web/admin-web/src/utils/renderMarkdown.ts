import MarkdownIt from "markdown-it";
import multimdTable from "markdown-it-multimd-table";
import taskLists from "markdown-it-task-lists";
import DOMPurify from "dompurify";

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
});

md.use(multimdTable, {
  multiline: true,
  rowspan: true,
  headerless: true,
});

md.use(taskLists, {
  enabled: true,
  label: true,
  labelAfter: false,
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
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

/** 为 GFM 表格、任务列表等块级语法补空行（markdown-it 需要）。 */
function normalizeGfmBlockMarkdown(source: string): string {
  const lines = source.split("\n");
  const out: string[] = [];
  let inFence = false;
  let fenceChar = "";
  let fenceLen = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? "";
    const trimmed = line.trim();
    const open = trimmed.match(/^(`{3,}|~{3,})([^`~]*)$/);
    if (!inFence && open) {
      inFence = true;
      fenceChar = open[1]![0]!;
      fenceLen = open[1]!.length;
    } else if (inFence) {
      let count = 0;
      while (count < trimmed.length && trimmed[count] === fenceChar) {
        count++;
      }
      if (count >= fenceLen && trimmed.slice(count).trim() === "") {
        inFence = false;
        fenceChar = "";
        fenceLen = 0;
      }
    }

    const needsBlankBefore =
        !inFence
        && (trimmed.startsWith("|") || /^[-*+]\s+\[[ xX]\]\s/.test(trimmed));
    if (needsBlankBefore && out.length > 0 && out[out.length - 1]!.trim() !== "") {
      out.push("");
    }
    out.push(line);
  }
  return out.join("\n");
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

const PURIFY_OPTS: Parameters<typeof DOMPurify.sanitize>[1] = {
  ADD_ATTR: ["target", "rel", "type", "title", "aria-label", "disabled", "checked"],
  ADD_TAGS: ["button", "input"],
};

export function renderMarkdownToSafeHtml(source: string): string {
  ensureMarkdownCodeCopyListener();
  const normalized = normalizeGfmBlockMarkdown(normalizeAssistantMarkdownSource(source));
  const raw = md.render(normalized);
  return DOMPurify.sanitize(raw, PURIFY_OPTS);
}
