/** 与后端 {@link AccountPrincipalKind} / 路径前缀对齐。 */
export type AccountPrincipalKind = "ACCOUNT_NO" | "LOGIN_NAME" | "EMAIL" | "PHONE";

export type AccountPrincipal = {
  kind: AccountPrincipalKind;
  value: string;
};

export function loginNamePrincipal(loginName: string): AccountPrincipal {
  return { kind: "LOGIN_NAME", value: loginName.trim() };
}

export function accountNoPrincipal(accountNo: string): AccountPrincipal {
  return { kind: "ACCOUNT_NO", value: accountNo.trim() };
}

const PATH_PREFIX: Record<AccountPrincipalKind, string> = {
  ACCOUNT_NO: "ac",
  LOGIN_NAME: "ln",
  EMAIL: "em",
  PHONE: "ph",
};

/** 生成 REST 路径段（须再 {@link encodeURIComponent} 嵌入 URL）。 */
export function accountPathSegment(principal: AccountPrincipal): string {
  return `${PATH_PREFIX[principal.kind]}:${principal.value}`;
}

export function accountPathFromLoginName(loginName: string): string {
  return encodeURIComponent(accountPathSegment(loginNamePrincipal(loginName)));
}

export function accountPathFromAccountNo(accountNo: string): string {
  return encodeURIComponent(accountPathSegment(accountNoPrincipal(accountNo)));
}

/** 管理端写操作推荐用正式账号编号，无则回退登录名。 */
export function accountPathForUser(row: { accountNo?: string | null; loginName: string }): string {
  const no = row.accountNo?.trim();
  if (no) {
    return accountPathFromAccountNo(no);
  }
  return accountPathFromLoginName(row.loginName);
}

export function sameLoginName(a: string, b: string | null | undefined): boolean {
  if (!b) return false;
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}
