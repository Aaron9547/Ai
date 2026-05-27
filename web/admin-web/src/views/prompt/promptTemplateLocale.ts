/** 提示词模板 locale 码（与库表一致） */
export const PROMPT_TEMPLATE_LOCALES = ["zh-CN", "en-US", "*"] as const;

export type PromptTemplateLocaleCode = (typeof PROMPT_TEMPLATE_LOCALES)[number];

/** 列表/筛选展示的语种文案（随 vue-i18n 切换） */
export function promptTemplateLocaleLabel(
  locale: string,
  t: (key: string) => string,
): string {
  const code = locale?.trim() || "";
  const key = `views.promptTemplate.localeValue.${code}`;
  const label = t(key);
  return label === key ? code : label;
}
