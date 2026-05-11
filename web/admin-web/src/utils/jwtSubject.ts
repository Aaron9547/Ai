/** 解析 JWT payload 中的 subject（本地登录为登录名 login_name），失败返回 null。 */
/** JWT 中管理端成员角色（tmr），如 FOUNDER / OWNER / ADMIN / MEMBER */

export type JwtTmsEntry = { tid: number; tmr: string };

/** JWT {@code tms}：租户成员关系 JSON 数组字符串，解析失败返回空数组。 */
export function readJwtTms(accessToken: string | null | undefined): JwtTmsEntry[] {
  if (!accessToken || typeof accessToken !== "string") return [];
  const parts = accessToken.split(".");
  if (parts.length < 2) return [];
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    const padded = pad ? payload + "=".repeat(4 - pad) : payload;
    const json = atob(padded);
    const o = JSON.parse(json) as { tms?: unknown };
    if (typeof o.tms !== "string" || !o.tms.trim()) return [];
    const inner = JSON.parse(o.tms) as unknown;
    if (!Array.isArray(inner)) return [];
    const out: JwtTmsEntry[] = [];
    for (const row of inner) {
      if (!row || typeof row !== "object") continue;
      const r = row as { tid?: unknown; tmr?: unknown };
      const tid = typeof r.tid === "number" && Number.isFinite(r.tid) ? r.tid : Number(r.tid);
      const tmr = typeof r.tmr === "string" ? r.tmr : "";
      if (Number.isFinite(tid) && tmr) {
        out.push({ tid: tid as number, tmr });
      }
    }
    return out;
  } catch {
    return [];
  }
}

export function readJwtTid(accessToken: string | null | undefined): string | null {
  if (!accessToken || typeof accessToken !== "string") return null;
  const parts = accessToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    const padded = pad ? payload + "=".repeat(4 - pad) : payload;
    const json = atob(padded);
    const o = JSON.parse(json) as { tid?: unknown };
    if (typeof o.tid === "number" && Number.isFinite(o.tid)) return String(o.tid);
    if (typeof o.tid === "string" && /^\d+$/.test(o.tid)) return o.tid;
    return null;
  } catch {
    return null;
  }
}

export function readJwtTmr(accessToken: string | null | undefined): string | null {
  if (!accessToken || typeof accessToken !== "string") return null;
  const parts = accessToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    const padded = pad ? payload + "=".repeat(4 - pad) : payload;
    const json = atob(padded);
    const o = JSON.parse(json) as { tmr?: unknown };
    return typeof o.tmr === "string" && o.tmr.length > 0 ? o.tmr : null;
  } catch {
    return null;
  }
}

/** JWT {@code uid}：账号主键，失败返回 null。 */
export function readJwtUid(accessToken: string | null | undefined): number | null {
  if (!accessToken || typeof accessToken !== "string") return null;
  const parts = accessToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    const padded = pad ? payload + "=".repeat(4 - pad) : payload;
    const json = atob(padded);
    const o = JSON.parse(json) as { uid?: unknown };
    if (typeof o.uid === "number" && Number.isFinite(o.uid)) return o.uid as number;
    if (typeof o.uid === "string" && /^\d+$/.test(o.uid)) return Number.parseInt(o.uid, 10);
    return null;
  } catch {
    return null;
  }
}

export function readJwtSubject(accessToken: string | null | undefined): string | null {
  if (!accessToken || typeof accessToken !== "string") return null;
  const parts = accessToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = payload.length % 4;
    const padded = pad ? payload + "=".repeat(4 - pad) : payload;
    const json = atob(padded);
    const o = JSON.parse(json) as { sub?: unknown };
    return typeof o.sub === "string" && o.sub.length > 0 ? o.sub : null;
  } catch {
    return null;
  }
}
