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

export function normalizeAssistantMarkdownSource(source: string): string {
  let s = source ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  // 模型偶发输出连续大量空行，压成最多双换行，减轻块级元素堆叠
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

/**
 * 将助手 Markdown 转为可安全 v-html 的 HTML（禁止原始 HTML 标签，仅解析 MD 语法）。
 * 对话主气泡请用 {@link buildMarkdownRichBlocks} + CodeBlock 组件渲染围栏代码。
 */
const SANITIZE_OPTS: DOMPurify.Config = {
  ADD_ATTR: ["target", "rel"],
};

function sanitizeMarkdownHtml(raw: string): string {
  return DOMPurify.sanitize(raw, SANITIZE_OPTS);
}

export function countFenceLines(s: string): number {
  let n = 0;
  for (const line of s.split("\n")) {
    if (/^\s*```/.test(line)) {
      n++;
    }
  }
  return n;
}

export function isInOpenFence(s: string): boolean {
  return countFenceLines(s) % 2 === 1;
}

/** 流式未写完的围栏代码块：临时补闭合围栏以便高亮，不改动原始存盘内容。 */
export function closeStreamingMarkdownFences(s: string): string {
  if (!isInOpenFence(s)) {
    return s;
  }
  return `${s}\n\`\`\``;
}

function hasDanglingInlineMarksOnLastLine(s: string): boolean {
  const last = s.split("\n").pop() ?? "";
  if ((last.match(/\*\*/g) || []).length % 2 === 1) {
    return true;
  }
  const withoutBold = last.replace(/\*\*/g, "");
  if ((withoutBold.match(/\*/g) || []).length % 2 === 1) {
    return true;
  }
  const inlineTicks = last.replace(/```/g, "").match(/`/g);
  return !!(inlineTicks && inlineTicks.length % 2 === 1);
}

/** 末行未闭合的行内标记临时补全，减少露出 **、` 等符号。 */
export function closeDanglingInlineMarks(s: string): string {
  const lines = s.split("\n");
  const lastIdx = lines.length - 1;
  let last = lines[lastIdx] ?? "";
  if ((last.match(/\*\*/g) || []).length % 2 === 1) {
    last += "**";
  }
  const withoutBold = last.replace(/\*\*/g, "");
  if ((withoutBold.match(/\*/g) || []).length % 2 === 1) {
    last += "*";
  }
  const inlineTicks = last.replace(/```/g, "").match(/`/g);
  if (inlineTicks && inlineTicks.length % 2 === 1) {
    last += "`";
  }
  lines[lastIdx] = last;
  return lines.join("\n");
}

const CURSOR_HOST_SELECTOR =
  "p, li, h1, h2, h3, h4, h5, h6, td, th, blockquote, dt, dd, code, strong, em, a, span";

function isVisuallyEmptyBlock(el: Element): boolean {
  const text = (el.textContent ?? "").replace(/\u00a0/g, " ").trim();
  if (text.length > 0) {
    return false;
  }
  return !el.querySelector("img, pre, table, ul, ol, .md-code-block, .stream-md-cursor");
}

/** 去掉末尾空段落/空列表项，避免光标落进空行。 */
function stripTrailingEmptyBlocks(root: HTMLElement): void {
  for (;;) {
    const last = root.lastElementChild;
    if (!last) {
      break;
    }
    if (last.tagName === "P" || last.tagName === "LI") {
      if (isVisuallyEmptyBlock(last)) {
        last.remove();
        continue;
      }
    }
    if ((last.tagName === "UL" || last.tagName === "OL") && last.querySelector("li") == null) {
      last.remove();
      continue;
    }
    break;
  }
}

function findLastTextNode(root: ParentNode): Text | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let last: Text | null = null;
  let node: Node | null;
  while ((node = walker.nextNode())) {
    const t = (node.textContent ?? "").replace(/\u00a0/g, " ");
    if (t.trim().length > 0) {
      last = node as Text;
    }
  }
  return last;
}

function findCursorHost(root: HTMLElement): HTMLElement | null {
  const lastText = findLastTextNode(root);
  if (lastText?.parentElement) {
    return lastText.parentElement;
  }
  const hosts = root.querySelectorAll(CURSOR_HOST_SELECTOR);
  if (hosts.length > 0) {
    return hosts[hosts.length - 1] as HTMLElement;
  }
  const lastChild = root.lastElementChild;
  return (lastChild as HTMLElement | null) ?? root;
}

function appendStreamingCursorWithDom(html: string, cursorHtml: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const root = document.createElement("div");
  root.innerHTML = html;
  stripTrailingEmptyBlocks(root);

  const tpl = document.createElement("template");
  tpl.innerHTML = cursorHtml;
  const cursorEl = tpl.content.firstChild;
  if (!cursorEl) {
    return html + cursorHtml;
  }

  const host = findCursorHost(root);
  if (!host) {
    return html + cursorHtml;
  }
  host.appendChild(cursorEl);
  return root.innerHTML;
}

/** 将打字光标插入最后一个块级元素内，避免单独占一行。 */
function appendStreamingCursorRegex(html: string, cursorHtml: string): string {
  let h = html.replace(/(?:<p>(?:\s|&nbsp;|<br\s*\/?>)*<\/p>\s*)+$/gi, "");
  if (/<\/p>\s*$/i.test(h)) {
    return h.replace(/<\/p>\s*$/i, `${cursorHtml}</p>`);
  }
  if (/<\/li>\s*$/i.test(h)) {
    return h.replace(/<\/li>\s*$/i, `${cursorHtml}</li>`);
  }
  if (/<\/(td|th)>\s*$/i.test(h)) {
    return h.replace(/<\/(td|th)>\s*$/i, `${cursorHtml}</$1>`);
  }
  if (/<\/code>\s*<\/pre>/i.test(h)) {
    return h.replace(/<\/code>(\s*<\/pre>)/i, `${cursorHtml}</code>$1`);
  }
  if (/<\/pre>\s*$/i.test(h)) {
    return h.replace(/<\/pre>\s*$/i, `${cursorHtml}</pre>`);
  }
  if (/<\/h[1-6]>\s*$/i.test(h)) {
    return h.replace(/<\/h([1-6])>\s*$/i, `${cursorHtml}</h$1>`);
  }
  if (/<\/blockquote>\s*$/i.test(h)) {
    return h.replace(/<\/blockquote>\s*$/i, `${cursorHtml}</blockquote>`);
  }
  return h + cursorHtml;
}

export function appendStreamingCursor(html: string, cursorHtml: string): string {
  if (!cursorHtml) {
    return html;
  }
  if (!html.trim()) {
    return cursorHtml;
  }
  const domResult = appendStreamingCursorWithDom(html, cursorHtml);
  if (domResult != null) {
    return domResult;
  }
  return appendStreamingCursorRegex(html, cursorHtml);
}

/** 流式打字光标 HTML（与 ChatView 中 class 一致） */
export const STREAM_CURSOR_HTML = '<span class="stream-md-cursor" aria-hidden="true"></span>';

export function renderMarkdownToSafeHtml(source: string): string {
  const raw = md.render(normalizeAssistantMarkdownSource(source));
  return sanitizeMarkdownHtml(raw);
}

/**
 * 流式阶段：按当前已输出内容渲染 Markdown（补全未闭合围栏/行内标记），末尾接打字光标。
 */
export function renderStreamingMarkdownToSafeHtml(source: string, cursorHtml = ""): string {
  const normalized = normalizeAssistantMarkdownSource(source ?? "");
  if (!normalized.trim()) {
    return cursorHtml;
  }
  const needsStreamPrep =
    isInOpenFence(normalized) ||
    (!isInOpenFence(normalized) && hasDanglingInlineMarksOnLastLine(normalized));
  const html = needsStreamPrep
    ? (() => {
        let s = normalized;
        if (!isInOpenFence(s)) {
          s = closeDanglingInlineMarks(s);
        }
        s = closeStreamingMarkdownFences(s);
        return sanitizeMarkdownHtml(md.render(s));
      })()
    : renderMarkdownToSafeHtml(source);
  return appendStreamingCursor(html, cursorHtml);
}
