import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";
import type MarkdownItToken from "markdown-it/lib/token.mjs";
import {
  appendStreamingCursor,
  closeDanglingInlineMarks,
  closeStreamingMarkdownFences,
  isInOpenFence,
  normalizeAssistantMarkdownSource,
} from "./renderMarkdown";

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
});

export type MarkdownRichBlock =
  | { kind: "html"; html: string }
  | { kind: "code"; language: string; code: string };

function sanitizeHtml(raw: string): string {
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ["target", "rel", "class"],
  });
}

function normalizeCodeContent(code: string): string {
  return (code ?? "").replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\n$/, "");
}

function fenceLanguage(info: string | null | undefined): string {
  const raw = (info ?? "").trim();
  if (!raw) {
    return "";
  }
  return raw.split(/\s+/)[0] ?? "";
}

/**
 * 用 markdown-it token 流拆分围栏/缩进代码块，避免手写 split('```') 漏拆或误拆。
 */
function buildBlocksFromTokens(source: string): MarkdownRichBlock[] {
  const tokens = md.parse(source, {});
  const blocks: MarkdownRichBlock[] = [];
  let i = 0;

  while (i < tokens.length) {
    const token = tokens[i]!;
    if (token.type === "fence" || token.type === "code_block") {
      blocks.push({
        kind: "code",
        language: token.type === "fence" ? fenceLanguage(token.info) : "",
        code: normalizeCodeContent(token.content),
      });
      i++;
      continue;
    }

    const start = i;
    while (
      i < tokens.length &&
      tokens[i]!.type !== "fence" &&
      tokens[i]!.type !== "code_block"
    ) {
      i++;
    }

    if (start >= i) {
      continue;
    }

    const slice: MarkdownItToken[] = tokens.slice(start, i);
    const html = sanitizeHtml(md.renderer.render(slice, md.options, {}));
    if (html.trim()) {
      blocks.push({ kind: "html", html });
    }
  }

  return blocks;
}

/** markdown-it 输出的 <pre><code>（class 可在 pre 或 code 上） */
const PRE_CODE_HTML_RE =
  /<pre(?:\s[^>]*)?>\s*<code(?:\s[^>]*)?>([\s\S]*?)<\/code>\s*<\/pre>/gi;

function extractLanguageFromPreCodeHtml(fragment: string): string {
  const m = fragment.match(/class="[^"]*\blanguage-([\w#+.:-]+)/i);
  return (m?.[1] ?? "").trim();
}

function decodeHtmlEntities(text: string): string {
  if (typeof document === "undefined") {
    return text
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&amp;/g, "&")
      .replace(/&quot;/g, '"');
  }
  const ta = document.createElement("textarea");
  ta.innerHTML = text;
  return ta.value;
}

/**
 * 将正文 HTML 里 markdown-it 渲染出的 <pre><code> 提升为 CodeBlock，避免仍走旧灰色 pre 样式。
 */
function hoistPreCodeFromHtmlBlocks(blocks: MarkdownRichBlock[]): MarkdownRichBlock[] {
  if (typeof document === "undefined") {
    return blocks;
  }

  const out: MarkdownRichBlock[] = [];

  for (const block of blocks) {
    if (block.kind !== "html" || !/<pre\b/i.test(block.html)) {
      out.push(block);
      continue;
    }

    let html = block.html;
    let match: RegExpExecArray | null;
    let lastIndex = 0;
    const re = new RegExp(PRE_CODE_HTML_RE.source, "gi");
    let found = false;

    while ((match = re.exec(html)) !== null) {
      found = true;
      const before = html.slice(lastIndex, match.index);
      if (before.trim()) {
        out.push({ kind: "html", html: before });
      }
      const language = extractLanguageFromPreCodeHtml(match[0]);
      const code = decodeHtmlEntities(match[1] ?? "");
      out.push({
        kind: "code",
        language,
        code: normalizeCodeContent(code),
      });
      lastIndex = match.index + match[0].length;
    }

    if (!found) {
      out.push(block);
      continue;
    }

    const tail = html.slice(lastIndex);
    if (tail.trim()) {
      out.push({ kind: "html", html: tail });
    }
  }

  return out;
}

export type BuildMarkdownRichBlocksOptions = {
  streaming?: boolean;
  cursorHtml?: string;
};

/**
 * 将助手 Markdown 拆成「正文 HTML 片段 + 代码块」供 Vue 组件渲染。
 */
export function buildMarkdownRichBlocks(
  source: string,
  opts?: BuildMarkdownRichBlocksOptions,
): MarkdownRichBlock[] {
  const normalized = normalizeAssistantMarkdownSource(source ?? "");
  if (!normalized.trim()) {
    return opts?.cursorHtml ? [{ kind: "html", html: opts.cursorHtml }] : [];
  }

  let work = normalized;
  if (opts?.streaming) {
    if (!isInOpenFence(work)) {
      work = closeDanglingInlineMarks(work);
    }
    work = closeStreamingMarkdownFences(work);
  }

  let blocks = buildBlocksFromTokens(work);
  blocks = hoistPreCodeFromHtmlBlocks(blocks);

  if (opts?.streaming && opts.cursorHtml) {
    const last = blocks[blocks.length - 1];
    if (!last) {
      blocks.push({ kind: "html", html: opts.cursorHtml });
    } else if (last.kind === "html") {
      last.html = appendStreamingCursor(last.html, opts.cursorHtml);
    } else {
      blocks.push({ kind: "html", html: opts.cursorHtml });
    }
  }

  return blocks;
}
