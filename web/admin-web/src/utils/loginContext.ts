import type { AdminMeView } from "@/api/adminMe";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { readJwtSubject, readJwtTid, readJwtTmr, readJwtUid } from "@/utils/jwtSubject";

/** 管理端当前登录用户（/admin/me 缓存）；登出或切换工作区后须 {@link setLoginUser} 更新。 */
let loginUserCache: AdminMeView | null = null;

export function setLoginUser(me: AdminMeView | null): void {
  loginUserCache = me;
}

export function getLoginUser(): AdminMeView | null {
  return loginUserCache;
}

export function requireLoginUser(): AdminMeView {
  if (!loginUserCache) {
    throw new Error("login user not loaded");
  }
  return loginUserCache;
}

export function getLoginUserId(): number | null {
  return readJwtUid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
}

export function getLoginTenantId(): number | null {
  if (loginUserCache?.tenantId != null && Number.isFinite(loginUserCache.tenantId)) {
    return loginUserCache.tenantId;
  }
  const tid = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  if (tid && /^\d+$/.test(tid)) {
    return Number.parseInt(tid, 10);
  }
  return null;
}

export function getLoginMemberRole(): string | null {
  if (loginUserCache?.memberRole) {
    return loginUserCache.memberRole;
  }
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
}

export function getLoginDisplayName(): string {
  const me = loginUserCache;
  const dn = me?.displayName?.trim();
  if (dn) return dn;
  const login = me?.loginName?.trim();
  if (login) return login;
  return readJwtSubject(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) ?? "";
}

export function clearLoginUser(): void {
  loginUserCache = null;
}
