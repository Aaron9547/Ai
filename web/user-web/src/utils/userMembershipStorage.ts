/**
 * C 端登录响应中的 {@code memberships} 缓存，用于侧栏「工作区」切换（须与 JWT {@code tms} 一致，仅可选已有机位的租户）。
 * 与 {@link clearUserSession} 同步清除。
 */
const KEY = "ai_user_memberships_json";

export type StoredUserMembership = {
  tenantId: number;
  tenantCode: string;
  tenantName?: string;
  role: string;
};

function isRow(x: unknown): x is StoredUserMembership {
  if (!x || typeof x !== "object") return false;
  const o = x as Record<string, unknown>;
  return (
    typeof o.tenantId === "number" &&
    typeof o.tenantCode === "string" &&
    typeof o.role === "string"
  );
}

export function saveUserMemberships(list: StoredUserMembership[] | undefined | null): void {
  if (!list?.length) {
    localStorage.removeItem(KEY);
    return;
  }
  localStorage.setItem(KEY, JSON.stringify(list));
}

export function loadUserMemberships(): StoredUserMembership[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isRow);
  } catch {
    return [];
  }
}

export function clearUserMemberships(): void {
  localStorage.removeItem(KEY);
}
