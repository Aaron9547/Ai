import type { IntentHandlerConfigFieldMeta } from "@/api/chatIntent";

export function keywordKindLabel(kind: string): string {
  if (kind === "TRIGGER") return "首轮 / 单轮进入";
  if (kind === "PLAN_CONTINUE") return "流程续接（多轮）";
  return String(kind);
}

/** 从落库 {@code extra_config_json} 按 schema 的 {@code paramStorage} 与 {@code name} 还原表单字符串（布尔为 true/false）。 */
export function readIntentHandlerFormFromExtra(
  extraJson: string | null | undefined,
  schema: IntentHandlerConfigFieldMeta[],
): Record<string, string> {
  const out: Record<string, string> = {};
  let root: Record<string, unknown> = {};
  try {
    if (extraJson?.trim()) root = JSON.parse(extraJson) as Record<string, unknown>;
  } catch {
    return out;
  }
  for (const f of schema) {
    const bucket =
      f.paramStorage === "TRAVEL_ROUTING" ? root.travelRouting : root.handlerParams;
    if (!bucket || typeof bucket !== "object") continue;
    const v = (bucket as Record<string, unknown>)[f.name];
    if (v === undefined || v === null) continue;
    if (typeof v === "boolean") out[f.name] = v ? "true" : "false";
    else if (typeof v === "number") out[f.name] = String(v);
    else out[f.name] = String(v);
  }
  return out;
}

/**
 * 将处理器参数表单写回 {@code extra_config_json}：在 baseline 上合并 {@code handlerParams} / {@code travelRouting} 等桶，
 * 仅覆盖 schema 中出现的键；其它键保留。
 */
export function mergeIntentExtraFromSchema(
  baselineJson: string | null | undefined,
  schema: IntentHandlerConfigFieldMeta[],
  form: Record<string, string>,
): string | null {
  let root: Record<string, unknown> = {};
  try {
    if (baselineJson?.trim()) root = JSON.parse(baselineJson) as Record<string, unknown>;
  } catch {
    root = {};
  }
  const hp: Record<string, unknown> =
    root.handlerParams && typeof root.handlerParams === "object"
      ? { ...(root.handlerParams as Record<string, unknown>) }
      : {};
  const tr: Record<string, unknown> =
    root.travelRouting && typeof root.travelRouting === "object"
      ? { ...(root.travelRouting as Record<string, unknown>) }
      : {};

  for (const f of schema) {
    const bucket = f.paramStorage === "TRAVEL_ROUTING" ? tr : hp;
    const raw = form[f.name];
    if (raw === undefined || raw === "") {
      delete bucket[f.name];
      continue;
    }
    if (f.valueKind === "BOOLEAN") {
      bucket[f.name] = raw === "true";
    } else if (f.valueKind === "INT") {
      const n = parseInt(String(raw), 10);
      if (Number.isNaN(n)) {
        delete bucket[f.name];
      } else {
        bucket[f.name] = n;
      }
    } else {
      bucket[f.name] = raw;
    }
  }

  if (Object.keys(hp).length > 0) root.handlerParams = hp;
  else delete root.handlerParams;
  if (Object.keys(tr).length > 0) root.travelRouting = tr;
  else delete root.travelRouting;

  const s = JSON.stringify(root);
  return s === "{}" ? null : s;
}
