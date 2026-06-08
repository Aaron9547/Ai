import type { ComposerTranslation } from "vue-i18n";

export type ReadableKvRow = { label: string; value: string };

export type McpResultReadable = {
  isError: boolean;
  textContent: string | null;
  metaRows: ReadableKvRow[];
};

export type QualityRecallHitRow = {
  rank: number;
  chunkId: number | null;
  inActualCitations: boolean;
  vectorSimilarity: string | null;
  keywordScore: string | null;
  hitSource: string | null;
};

export type QualityRecallReadable = {
  rate: string | null;
  citedInTopK: number | null;
  totalCited: number | null;
  topK: number | null;
  hintCode: string | null;
  hits: QualityRecallHitRow[];
  diagnostics: ReadableKvRow[];
};

export type QualityCitationItemRow = {
  chunkId: number | null;
  relevant: boolean;
  judgeReason: string;
};

export type QualityCitationReadable = {
  score: string | null;
  hintCode: string | null;
  items: QualityCitationItemRow[];
};

export type QualityFaithfulnessReadable = {
  score: string | null;
  judgeReason: string | null;
  unsupportedClaims: string[];
  hintCode: string | null;
};

export type QualityResultReadable = {
  recall: QualityRecallReadable | null;
  citation: QualityCitationReadable | null;
  faithfulness: QualityFaithfulnessReadable | null;
};

const EM_DASH = "\u2014";

function parseJson(raw: string | null | undefined): unknown {
  if (raw == null || !String(raw).trim()) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function asObject(v: unknown): Record<string, unknown> | null {
  if (v == null || typeof v !== "object" || Array.isArray(v)) return null;
  return v as Record<string, unknown>;
}

function asNumber(v: unknown): number | null {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function asBool(v: unknown): boolean {
  return v === true || v === "true";
}

function formatPctRate(v: unknown): string | null {
  const n = asNumber(v);
  if (n == null) return null;
  return `${(n * 100).toFixed(1)}%`;
}

function formatScore4(v: unknown): string | null {
  const n = asNumber(v);
  if (n == null) return null;
  return n.toFixed(4);
}

function formatScalar(v: unknown): string {
  if (v == null) return EM_DASH;
  if (typeof v === "boolean") return v ? "true" : "false";
  if (typeof v === "object") {
    try {
      return JSON.stringify(v);
    } catch {
      return String(v);
    }
  }
  const s = String(v).trim();
  return s || EM_DASH;
}

function humanizeKey(key: string): string {
  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/[._[\]]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function flattenJsonToRows(raw: string | null | undefined, maxRows = 24): ReadableKvRow[] {
  const parsed = parseJson(raw);
  if (parsed == null) {
    if (raw?.trim()) return [{ label: "text", value: raw.trim() }];
    return [];
  }
  if (typeof parsed === "string") return [{ label: "text", value: parsed }];
  const flat = flattenValue(parsed, "");
  return flat.slice(0, maxRows).map(({ key, value }) => ({
    label: humanizeKey(key || "value"),
    value,
  }));
}

function flattenValue(v: unknown, prefix: string): { key: string; value: string }[] {
  if (v == null) return prefix ? [{ key: prefix, value: EM_DASH }] : [];
  if (typeof v !== "object") {
    return [{ key: prefix || "value", value: formatScalar(v) }];
  }
  if (Array.isArray(v)) {
    if (v.length === 0) return prefix ? [{ key: prefix, value: "[]" }] : [];
    return v.flatMap((item, i) => flattenValue(item, prefix ? `${prefix}[${i + 1}]` : `[${i + 1}]`));
  }
  const o = v as Record<string, unknown>;
  const keys = Object.keys(o);
  if (keys.length === 0) return prefix ? [{ key: prefix, value: "{}" }] : [];
  return keys.flatMap((k) => {
    const child = o[k];
    const key = prefix ? `${prefix}.${k}` : k;
    if (child != null && typeof child === "object") return flattenValue(child, key);
    return [{ key, value: formatScalar(child) }];
  });
}

export function parseMcpResultReadable(raw: string | null | undefined): McpResultReadable | null {
  const parsed = parseJson(raw);
  if (parsed == null) {
    if (raw?.trim()) return { isError: false, textContent: raw.trim(), metaRows: [] };
    return null;
  }
  if (typeof parsed === "string") {
    return { isError: false, textContent: parsed, metaRows: [] };
  }
  const o = asObject(parsed);
  if (!o) return null;
  const isError = asBool(o.error);
  const textContent =
    typeof o.textContent === "string" && o.textContent.trim() ? o.textContent.trim() : null;
  const metaRows: ReadableKvRow[] = [];
  if (o.toolName != null) metaRows.push({ label: "toolName", value: formatScalar(o.toolName) });
  if (o.qualifiedName != null) metaRows.push({ label: "qualifiedName", value: formatScalar(o.qualifiedName) });
  if (o.serverId != null) metaRows.push({ label: "serverId", value: formatScalar(o.serverId) });
  if (o.elapsedMs != null) metaRows.push({ label: "elapsedMs", value: formatScalar(o.elapsedMs) });
  if (isError) metaRows.push({ label: "error", value: "true" });
  return { isError, textContent, metaRows };
}

export function parseQualityResultReadable(raw: string | null | undefined): QualityResultReadable | null {
  const parsed = parseJson(raw);
  const root = asObject(parsed);
  if (!root) return null;

  return {
    recall: parseRecall(asObject(root.recall)),
    citation: parseCitation(asObject(root.citationAccuracy)),
    faithfulness: parseFaithfulness(asObject(root.faithfulness)),
  };
}

function parseRecall(o: Record<string, unknown> | null): QualityRecallReadable | null {
  if (!o) return null;
  const hitsRaw = Array.isArray(o.retrievalHits) ? o.retrievalHits : [];
  const hits: QualityRecallHitRow[] = hitsRaw.map((item, idx) => {
    const row = asObject(item) ?? {};
    return {
      rank: asNumber(row.rank) ?? idx + 1,
      chunkId: asNumber(row.chunkId),
      inActualCitations: asBool(row.inActualCitations),
      vectorSimilarity: formatScore4(row.vectorSimilarity),
      keywordScore: formatScore4(row.keywordScore),
      hitSource: row.hitSource != null ? formatScalar(row.hitSource) : null,
    };
  });
  const diag = asObject(o.diagnostics);
  const diagnostics: ReadableKvRow[] = [];
  if (diag) {
    if (diag.milvusRecallCount != null) diagnostics.push({ label: "milvusRecallCount", value: formatScalar(diag.milvusRecallCount) });
    if (diag.afterCosineThresholdCount != null) {
      diagnostics.push({ label: "afterCosineThresholdCount", value: formatScalar(diag.afterCosineThresholdCount) });
    }
    if (diag.resolvableChunkCount != null) {
      diagnostics.push({ label: "resolvableChunkCount", value: formatScalar(diag.resolvableChunkCount) });
    }
    if (diag.minCosineThreshold != null) diagnostics.push({ label: "minCosineThreshold", value: formatScalar(diag.minCosineThreshold) });
    if (diag.maxMilvusSimilarity != null) diagnostics.push({ label: "maxMilvusSimilarity", value: formatScalar(diag.maxMilvusSimilarity) });
  }
  return {
    rate: formatPctRate(o.citationRecallRate),
    citedInTopK: asNumber(o.citedInTopK),
    totalCited: asNumber(o.totalCited),
    topK: asNumber(o.topK),
    hintCode: typeof o.hintCode === "string" ? o.hintCode : null,
    hits,
    diagnostics,
  };
}

function parseCitation(o: Record<string, unknown> | null): QualityCitationReadable | null {
  if (!o) return null;
  const itemsRaw = Array.isArray(o.items) ? o.items : [];
  const items: QualityCitationItemRow[] = itemsRaw.map((item) => {
    const row = asObject(item) ?? {};
    return {
      chunkId: asNumber(row.chunkId),
      relevant: asBool(row.relevant),
      judgeReason: typeof row.judgeReason === "string" ? row.judgeReason : "",
    };
  });
  return {
    score: formatPctRate(o.score),
    hintCode: typeof o.hintCode === "string" ? o.hintCode : null,
    items,
  };
}

function parseFaithfulness(o: Record<string, unknown> | null): QualityFaithfulnessReadable | null {
  if (!o) return null;
  const claimsRaw = o.unsupportedClaims;
  const unsupportedClaims: string[] = [];
  if (Array.isArray(claimsRaw)) {
    for (const c of claimsRaw) {
      if (typeof c === "string" && c.trim()) unsupportedClaims.push(c.trim());
    }
  }
  return {
    score: formatPctRate(o.score),
    judgeReason: typeof o.judgeReason === "string" ? o.judgeReason : null,
    unsupportedClaims,
    hintCode: typeof o.hintCode === "string" ? o.hintCode : null,
  };
}

export function qualityHintLabel(code: string | null | undefined, t: ComposerTranslation): string {
  if (!code) return EM_DASH;
  if (code === "RAG_QA_NO_KB") return String(t("views.ragQuality.hintNoKb"));
  if (code === "RAG_QA_NO_LLM") return String(t("views.ragQuality.hintNoLlm"));
  return code;
}

export function mcpMetaLabel(key: string, t: ComposerTranslation): string {
  const map: Record<string, string> = {
    toolName: String(t("views.observability.mcpMetaToolName")),
    qualifiedName: String(t("views.observability.colTool")),
    serverId: String(t("views.observability.mcpMetaServerId")),
    elapsedMs: String(t("views.observability.colLatency")),
    error: String(t("views.observability.colSuccess")),
  };
  return map[key] ?? humanizeKey(key);
}

export function qualityDiagLabel(key: string, t: ComposerTranslation): string {
  const map: Record<string, string> = {
    milvusRecallCount: String(t("views.ragQuality.diagMilvusRecall")),
    afterCosineThresholdCount: String(t("views.ragQuality.diagAfterThreshold")),
    resolvableChunkCount: String(t("views.ragQuality.diagResolvable")),
    minCosineThreshold: String(t("views.ragQuality.diagMinCosine")),
    maxMilvusSimilarity: String(t("views.ragQuality.diagMaxSimilarity")),
  };
  return map[key] ?? humanizeKey(key);
}

export function argLabel(key: string, t: ComposerTranslation): string {
  return humanizeKey(key);
}
