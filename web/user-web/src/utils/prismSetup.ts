import Prism from "prismjs";

/** 按 Prism 依赖顺序加载；其余语言走 clike 回退 */
import "prismjs/components/prism-clike";
import "prismjs/components/prism-markup";
import "prismjs/components/prism-javascript";
import "prismjs/components/prism-css";
import "prismjs/components/prism-json";
import "prismjs/components/prism-markdown";
import "prismjs/components/prism-typescript";
import "prismjs/components/prism-python";
import "prismjs/components/prism-bash";
import "prismjs/components/prism-sql";
import "prismjs/components/prism-yaml";
import "prismjs/components/prism-java";
import "prismjs/components/prism-csharp";
import "prismjs/components/prism-go";
import "prismjs/components/prism-rust";
import "prismjs/components/prism-php";
import "prismjs/components/prism-ruby";
import "prismjs/components/prism-c";
import "prismjs/components/prism-cpp";
import "prismjs/components/prism-docker";
import "prismjs/components/prism-kotlin";
import "prismjs/components/prism-swift";

/** markdown 围栏 info 与常见别名 → Prism 语法 id */
const LANG_ALIASES: Record<string, string> = {
  js: "javascript",
  node: "javascript",
  nodejs: "javascript",
  mjs: "javascript",
  cjs: "javascript",
  jsx: "javascript",
  ts: "typescript",
  tsx: "typescript",
  py: "python",
  python3: "python",
  sh: "bash",
  shell: "bash",
  zsh: "bash",
  console: "bash",
  terminal: "bash",
  yml: "yaml",
  md: "markdown",
  html: "markup",
  xml: "markup",
  svg: "markup",
  vue: "markup",
  svelte: "markup",
  csharp: "csharp",
  cs: "csharp",
  "c#": "csharp",
  golang: "go",
  rs: "rust",
  kt: "kotlin",
  kts: "kotlin",
  rb: "ruby",
  dockerfile: "docker",
  text: "plain",
  plaintext: "plain",
  txt: "plain",
};

const LANG_DISPLAY: Record<string, string> = {
  javascript: "JavaScript",
  typescript: "TypeScript",
  python: "Python",
  bash: "Bash",
  json: "JSON",
  yaml: "YAML",
  markdown: "Markdown",
  rust: "Rust",
  go: "Go",
  java: "Java",
  kotlin: "Kotlin",
  csharp: "C#",
  swift: "Swift",
  sql: "SQL",
  css: "CSS",
  markup: "HTML",
  html: "HTML",
  docker: "Docker",
  php: "PHP",
  ruby: "Ruby",
  c: "C",
  cpp: "C++",
  clike: "Code",
  plain: "Plain Text",
};

function normalizeLangId(lang: string | undefined): string {
  return (lang ?? "").trim().toLowerCase();
}

export function resolvePrismLanguage(lang: string | undefined): string {
  const raw = normalizeLangId(lang);
  if (!raw) {
    return "plain";
  }
  const mapped = LANG_ALIASES[raw] ?? raw;
  if (mapped === "plain") {
    return "plain";
  }
  if (Prism.languages[mapped]) {
    return mapped;
  }
  if (Prism.languages.clike) {
    return "clike";
  }
  return "plain";
}

export function highlightWithPrism(code: string, language: string | undefined): string {
  const lang = resolvePrismLanguage(language);
  const source = code ?? "";
  if (lang === "plain") {
    return Prism.util.encode(source) as string;
  }
  try {
    return Prism.highlight(source, Prism.languages[lang]!, lang);
  } catch {
    return Prism.util.encode(source) as string;
  }
}

export function formatCodeLanguageLabel(lang: string | undefined): string {
  const raw = normalizeLangId(lang);
  if (!raw) {
    return "Plain Text";
  }
  const mapped = LANG_ALIASES[raw] ?? raw;
  if (LANG_DISPLAY[mapped]) {
    return LANG_DISPLAY[mapped]!;
  }
  if (LANG_DISPLAY[raw]) {
    return LANG_DISPLAY[raw]!;
  }
  return raw.charAt(0).toUpperCase() + raw.slice(1);
}
