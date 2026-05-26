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

function normalizeAssistantMarkdownSource(source: string): string {
  let s = source ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

function isMarkdownTableRowLine(trimmed: string): boolean {
  return trimmed.startsWith("|") && trimmed.includes("|", 1);
}

/** 去掉表格行之间的空行（模型/粘贴常带空行，会导致无法解析为 table）。 */
function collapseMarkdownTableBlankLines(source: string): string {
  const lines = source.split("\n");
  const out: string[] = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? "";
    const trimmed = line.trim();
    if (trimmed === "" && out.length > 0) {
      const prevTrimmed = (out[out.length - 1] ?? "").trim();
      let j = i + 1;
      while (j < lines.length && (lines[j] ?? "").trim() === "") {
        j++;
      }
      const nextTrimmed = j < lines.length ? (lines[j] ?? "").trim() : "";
      if (isMarkdownTableRowLine(prevTrimmed) && isMarkdownTableRowLine(nextTrimmed)) {
        continue;
      }
    }
    out.push(line);
  }
  return out.join("\n");
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

    const prevTrimmed = out.length > 0 ? (out[out.length - 1] ?? "").trim() : "";
    const prevIsTableRow = isMarkdownTableRowLine(prevTrimmed);
    const isTableRow = isMarkdownTableRowLine(trimmed);
    const needsBlankBefore =
        !inFence
        && out.length > 0
        && prevTrimmed !== ""
        && ((isTableRow && !prevIsTableRow)
          || (!isTableRow
            && ( /^#{1,6}\s/.test(trimmed)
              || /^[-*+]\s+/.test(trimmed)
              || /^\d+\.\s+/.test(trimmed)
              || /^>/.test(trimmed))));
    if (needsBlankBefore) {
      out.push("");
    }
    out.push(line);
  }
  return out.join("\n");
}

const PURIFY_OPTS: Parameters<typeof DOMPurify.sanitize>[1] = {
  ADD_ATTR: ["target", "rel", "type", "title", "aria-label", "disabled", "checked", "class"],
  ADD_TAGS: ["button", "input"],
};

/** 与 {@link parseMarkdownRichBlocks} 共用同一 markdown-it 实例（含 GFM 表格/任务列表）。 */
export function getAdminMarkdownIt(): MarkdownIt {
  return md;
}

export function normalizeMarkdownSourceForParse(source: string): string {
  const base = normalizeAssistantMarkdownSource(source);
  return normalizeGfmBlockMarkdown(collapseMarkdownTableBlankLines(base));
}

/**
 * 将 Markdown 转为可安全 v-html 的 HTML。
 * 对话抽检等场景请优先 {@link buildMarkdownRichBlocks} + CodeBlock 组件渲染围栏代码。
 */
export function renderMarkdownToSafeHtml(source: string): string {
  const normalized = normalizeMarkdownSourceForParse(source);
  const raw = md.render(normalized);
  return DOMPurify.sanitize(raw, PURIFY_OPTS);
}
