import DOMPurify from "dompurify";
import type MarkdownItToken from "markdown-it/lib/token.mjs";
import { getAdminMarkdownIt, normalizeMarkdownSourceForParse } from "./renderMarkdown";

export type MarkdownRichBlock =
  | { kind: "html"; html: string }
  | { kind: "code"; language: string; code: string };

const md = getAdminMarkdownIt();

const PURIFY_OPTS: Parameters<typeof DOMPurify.sanitize>[1] = {
  ADD_ATTR: ["target", "rel", "type", "title", "aria-label", "disabled", "checked", "class"],
  ADD_TAGS: ["button", "input"],
};

function sanitizeHtml(raw: string): string {
  return DOMPurify.sanitize(raw, PURIFY_OPTS);
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

    const html = block.html;
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
      out.push({
        kind: "code",
        language: extractLanguageFromPreCodeHtml(match[0]),
        code: normalizeCodeContent(decodeHtmlEntities(match[1] ?? "")),
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

/** 将 Markdown 拆成「正文 HTML + CodeBlock」供管理端对话抽检等场景渲染。 */
export function buildMarkdownRichBlocks(source: string): MarkdownRichBlock[] {
  const work = normalizeMarkdownSourceForParse(source ?? "");
  if (!work.trim()) {
    return [];
  }
  return hoistPreCodeFromHtmlBlocks(buildBlocksFromTokens(work));
}
