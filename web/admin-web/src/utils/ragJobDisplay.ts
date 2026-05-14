import type { ComposerTranslation } from "vue-i18n";

/** Async job labels for knowledge center (requires vue-i18n `t`) */

export function jobTaskTypeLabel(taskType: string | null | undefined, t: ComposerTranslation): string {
  switch ((taskType || "").toUpperCase()) {
    case "RAG_INDEX":
      return String(t("views.kbAsync.jobTypes.RAG_INDEX"));
    case "RAG_URL_IMPORT":
      return String(t("views.kbAsync.jobTypes.RAG_URL_IMPORT"));
    case "RAG_FILE_IMPORT":
      return String(t("views.kbAsync.jobTypes.RAG_FILE_IMPORT"));
    default:
      return taskType?.trim() || String(t("common.dash"));
  }
}

export function jobTaskTypeShort(taskType: string | null | undefined, t: ComposerTranslation): string {
  switch ((taskType || "").toUpperCase()) {
    case "RAG_URL_IMPORT":
      return String(t("views.kbAsync.jobTypesShort.RAG_URL_IMPORT"));
    case "RAG_FILE_IMPORT":
      return String(t("views.kbAsync.jobTypesShort.RAG_FILE_IMPORT"));
    case "RAG_INDEX":
      return String(t("views.kbAsync.jobTypesShort.RAG_INDEX"));
    default:
      return jobTaskTypeLabel(taskType, t);
  }
}

export type JobStatusTag = "success" | "warning" | "info" | "danger";

export function jobStatusMeta(
  status: string | null | undefined,
  t: ComposerTranslation,
): { label: string; tag: JobStatusTag } {
  const s = (status || "").toUpperCase();
  if (s === "SUCCEEDED") return { label: String(t("views.kbAsync.jobStatus.SUCCEEDED")), tag: "success" };
  if (s === "FAILED") return { label: String(t("views.kbAsync.jobStatus.FAILED")), tag: "danger" };
  if (s === "RUNNING") return { label: String(t("views.kbAsync.jobStatus.RUNNING")), tag: "warning" };
  if (s === "PENDING") return { label: String(t("views.kbAsync.jobStatus.PENDING")), tag: "info" };
  return { label: status?.trim() || String(t("common.dash")), tag: "info" };
}

export function jobStepPhaseLabel(phase: string | null | undefined, t: ComposerTranslation): string {
  const p = (phase || "").trim().toLowerCase();
  const key = `views.kbAsync.phases.${p}` as const;
  const tr = t(key);
  if (tr !== key) return String(tr);
  return phase?.trim() || String(t("common.dash"));
}

export function jobStepStatusLabel(status: string | null | undefined, t: ComposerTranslation): string {
  const s = (status || "").trim().toLowerCase();
  if (s === "running") return String(t("views.kbAsync.stepStatus.running"));
  if (s === "ok") return String(t("views.kbAsync.stepStatus.ok"));
  if (s === "failed" || s === "error") return String(t("views.kbAsync.stepStatus.failed"));
  return status?.trim() || String(t("common.dash"));
}

export function humanizeJobStepDetail(detail: string | null | undefined, t: ComposerTranslation): string {
  if (detail == null || !String(detail).trim()) return "";
  let d = String(detail).trim();
  const mDoc = /^documentId=(\d+),chunks=(\d+)$/.exec(d);
  if (mDoc) {
    return String(
      t("views.kbAsync.stepDetail.docChunks", { docId: mDoc[1], chunks: mDoc[2] }),
    );
  }
  if (/^bytes=\d+$/i.test(d)) {
    return String(t("views.kbAsync.stepDetail.bytes", { n: d.slice(6) }));
  }
  if (/^chars=\d+$/i.test(d)) {
    return String(t("views.kbAsync.stepDetail.chars", { n: d.slice(6) }));
  }
  if (/^collection=/.test(d)) {
    return String(t("views.kbAsync.stepDetail.collection", { name: d.replace(/^collection=/, "") }));
  }
  return d;
}

export function ragChunkStrategyLabel(code: number | string | null | undefined, t: ComposerTranslation): string {
  const n = typeof code === "string" ? Number.parseInt(code, 10) : code;
  if (n == null || Number.isNaN(n)) return String(t("common.dash"));
  const map: Record<number, string> = {
    0: "views.kbAsync.chunkStrategy.0",
    1: "views.kbAsync.chunkStrategy.1",
    2: "views.kbAsync.chunkStrategy.2",
    3: "views.kbAsync.chunkStrategy.3",
    99: "views.kbAsync.chunkStrategy.99",
  };
  const path = map[n];
  if (path) return String(t(path));
  return String(t("views.kbAsync.chunkStrategy.other", { n }));
}

export type JobKvRow = { label: string; value: string };

export function parseJobPayloadRows(
  payloadJson: string | null | undefined,
  t: ComposerTranslation,
): JobKvRow[] {
  if (payloadJson == null || !String(payloadJson).trim()) return [];
  let o: Record<string, unknown>;
  try {
    o = JSON.parse(payloadJson) as Record<string, unknown>;
  } catch {
    return [{ label: String(t("views.kbAsync.payloadLabels.raw")), value: String(payloadJson) }];
  }
  const rows: JobKvRow[] = [];
  if (o.kbId != null && o.kbId !== "") rows.push({ label: String(t("views.kbAsync.payloadLabels.kb")), value: `#${String(o.kbId)}` });
  if (typeof o.url === "string" && o.url.trim()) rows.push({ label: String(t("views.kbAsync.payloadLabels.url")), value: o.url.trim() });
  if (typeof o.originalFilename === "string" && o.originalFilename.trim())
    rows.push({ label: String(t("views.kbAsync.payloadLabels.filename")), value: o.originalFilename.trim() });
  if (o.chunkStrategy != null && o.chunkStrategy !== "")
    rows.push({
      label: String(t("views.kbAsync.payloadLabels.chunkStrategy")),
      value: ragChunkStrategyLabel(o.chunkStrategy as number, t),
    });
  if (typeof o.contentType === "string" && o.contentType.trim())
    rows.push({ label: String(t("views.kbAsync.payloadLabels.contentType")), value: o.contentType.trim() });
  if (typeof o.markdownContent === "string" && o.markdownContent.length > 0) {
    rows.push({
      label: String(t("views.kbAsync.payloadLabels.markdownBody")),
      value: String(t("views.kbAsync.payloadLabels.markdownFilled", { n: o.markdownContent.length })),
    });
  }
  return rows;
}

export function parseJobResultMetaRows(resultJson: string | null | undefined, t: ComposerTranslation): JobKvRow[] {
  if (resultJson == null || !String(resultJson).trim()) return [];
  try {
    const o = JSON.parse(resultJson) as Record<string, unknown>;
    const rows: JobKvRow[] = [];
    if (typeof o.documentId === "number") rows.push({ label: String(t("views.kbAsync.resultMeta.documentId")), value: String(o.documentId) });
    if (typeof o.chunkCount === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.chunkCount")),
        value: String(t("views.kbAsync.resultMeta.chunkCountUnit", { n: o.chunkCount })),
      });
    }
    if (typeof o.kbId === "number") rows.push({ label: String(t("views.kbAsync.resultMeta.kbId")), value: String(o.kbId) });
    if (typeof o.error === "string" && o.error.trim()) rows.push({ label: String(t("views.kbAsync.resultMeta.error")), value: o.error.trim() });
    if (o.indexed === true) {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.indexedLabel")),
        value: String(t("views.kbAsync.resultMeta.indexedValue")),
      });
    }
    return rows;
  } catch {
    return [];
  }
}

export function summarizeJobResult(resultJson: string | null | undefined, t: ComposerTranslation): string {
  if (resultJson == null || !String(resultJson).trim()) return String(t("common.dash"));
  try {
    const o = JSON.parse(resultJson) as Record<string, unknown>;
    if (typeof o.error === "string" && o.error.trim()) {
      const e = o.error.trim();
      return e.length > 80
        ? `${String(t("views.kbAsync.summarize.failPrefix"))}${e.slice(0, 80)}…`
        : `${String(t("views.kbAsync.summarize.failPrefix"))}${e}`;
    }
    if (typeof o.documentId === "number" && typeof o.chunkCount === "number") {
      return String(
        t("views.kbAsync.summarize.ingested", { docId: o.documentId, chunks: o.chunkCount }),
      );
    }
    if (o.indexed === true) return String(t("views.kbAsync.summarize.indexDone"));
    const steps = o.steps as unknown[] | undefined;
    if (Array.isArray(steps) && steps.length > 0) return String(t("views.kbAsync.summarize.steps", { n: steps.length }));
  } catch {
    /* fall through */
  }
  const s = String(resultJson).replace(/\s+/g, " ");
  return s.length > 100 ? `${s.slice(0, 100)}…` : s;
}

export function prettyJson(v: string | null | undefined): string {
  if (v == null || !String(v).trim()) return "—";
  try {
    return JSON.stringify(JSON.parse(v), null, 2);
  } catch {
    return v;
  }
}

export type ResultStep = { phase?: string; status?: string; detail?: string; at?: string };

export function ragDocumentDisplayStatusLabel(code: string | null | undefined, t: ComposerTranslation): string {
  const s = (code || "").toUpperCase();
  if (s === "PUBLISHED") return String(t("views.kbAsync.ragDocStatus.PUBLISHED"));
  if (s === "PARSING") return String(t("views.kbAsync.ragDocStatus.PARSING"));
  if (s === "PARSE_FAILED") return String(t("views.kbAsync.ragDocStatus.PARSE_FAILED"));
  return code?.trim() || String(t("common.dash"));
}

export function parseResultSteps(resultJson: string | null | undefined): ResultStep[] {
  if (resultJson == null || !String(resultJson).trim()) return [];
  try {
    const o = JSON.parse(resultJson) as { steps?: ResultStep[] };
    if (o.steps && Array.isArray(o.steps)) return o.steps.filter((x) => x && typeof x === "object");
  } catch {
    /* ignore */
  }
  return [];
}
