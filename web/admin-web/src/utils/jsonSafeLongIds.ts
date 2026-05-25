/**
 * 后端 {@code sec_user_account.id} 等为 MyBatis 雪花 Long，超过 {@code Number.MAX_SAFE_INTEGER}。
 * 原生 {@code JSON.parse} 会四舍五入，导致 PUT /admin/users/{id} 等路径 id 与库不一致。
 */

/** 将 JSON 中疑似雪花主键的数值字面量改为字符串（须在 {@code JSON.parse} 之前处理）。 */
export function quoteUnsafeJsonIntegers(raw: string): string {
  return raw.replace(
    /"([a-zA-Z][a-zA-Z0-9]*Id|id|uid)":(\s*)(-?\d{16,})(?=[,\}\]\s])/g,
    (_match, key: string, sp: string, num: string) => `"${key}":${sp}"${num}"`,
  );
}

export function parseApiJson(raw: string): unknown {
  return JSON.parse(quoteUnsafeJsonIntegers(raw));
}

/** URL 路径中的 id：统一为字符串，避免 number 精度丢失。 */
export function pathId(id: string | number): string {
  return typeof id === "string" ? id : String(id);
}
