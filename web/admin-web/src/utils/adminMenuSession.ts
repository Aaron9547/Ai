/** 管理端当前 JWT 工作区对应的 {@code allowedMenuCodes}（由 {@link AdminLayout} / 登录后刷新）。 */
let allowedMenuCodes: string[] | null = null;

export function setAdminMenuSession(codes: string[] | null): void {
  allowedMenuCodes = codes;
}

export function getAdminMenuSession(): string[] | null {
  return allowedMenuCodes;
}
