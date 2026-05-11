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

function normalizeAssistantMarkdownSource(source: string): string {
  let s = source ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  // 模型偶发输出连续大量空行，压成最多双换行，减轻块级元素堆叠
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

/**
 * 将助手 Markdown 转为可安全 v-html 的 HTML（禁止原始 HTML 标签，仅解析 MD 语法）。
 */
export function renderMarkdownToSafeHtml(source: string): string {
  const raw = md.render(normalizeAssistantMarkdownSource(source));
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ["target", "rel"],
  });
}
