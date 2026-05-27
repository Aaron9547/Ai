/** 登录/注册常用邮箱后缀 */
export const COMMON_EMAIL_SUFFIXES = [
  "@qq.com",
  "@163.com",
  "@126.com",
  "@gmail.com",
  "@outlook.com",
  "@icloud.com",
  "@foxmail.com",
  "@sina.com",
] as const;

export type EmailSuffixSuggestion = { value: string };

/**
 * 根据当前输入生成自动完成候选（输入时展示，非底部固定列表）。
 * - 无 `@`：补全为 local+各后缀
 * - 有 `@`：按已输入域名前缀过滤后缀
 */
export function buildEmailSuffixSuggestions(input: string, maxLength = 128): EmailSuffixSuggestion[] {
  const trimmed = input.trim();
  if (!trimmed) {
    return [];
  }
  const atIdx = trimmed.indexOf("@");
  const local = atIdx === -1 ? trimmed : trimmed.slice(0, atIdx);
  if (!local) {
    return [];
  }
  if (atIdx === -1) {
    return COMMON_EMAIL_SUFFIXES.map((suffix) => ({
      value: `${local}${suffix}`.slice(0, maxLength),
    }));
  }
  const domainPart = trimmed.slice(atIdx + 1).toLowerCase();
  return COMMON_EMAIL_SUFFIXES.filter((suffix) => {
    const domain = suffix.slice(1);
    return domainPart === "" || domain.startsWith(domainPart);
  }).map((suffix) => ({
    value: `${local}${suffix}`.slice(0, maxLength),
  }));
}

/** @deprecated 供测试或手动拼接；UI 优先用 buildEmailSuffixSuggestions */
export function applyEmailSuffix(current: string, suffix: string, maxLength = 128): string {
  const domain = suffix.startsWith("@") ? suffix : `@${suffix}`;
  const trimmed = current.trim();
  const atIdx = trimmed.indexOf("@");
  const local = atIdx === -1 ? trimmed : trimmed.slice(0, atIdx);
  return `${local}${domain}`.slice(0, maxLength);
}
