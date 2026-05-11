import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
});

function normalizeAssistantMarkdownSource(source: string): string {
  let s = source ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  s = s.replace(/\n{5,}/g, "\n\n\n");
  return s;
}

export function renderMarkdownToSafeHtml(source: string): string {
  const raw = md.render(normalizeAssistantMarkdownSource(source));
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ["target", "rel"],
  });
}
