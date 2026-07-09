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
    case "RAG_SITE_CRAWL":
      return String(t("views.kbAsync.jobTypes.RAG_SITE_CRAWL"));
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
    case "RAG_SITE_CRAWL":
      return String(t("views.kbAsync.jobTypesShort.RAG_SITE_CRAWL"));
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
    4: "views.kbAsync.chunkStrategy.4",
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
  if (typeof o.baseUrl === "string" && o.baseUrl.trim())
    rows.push({ label: String(t("views.kbAsync.payloadLabels.baseUrl")), value: o.baseUrl.trim() });
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
    if (typeof o.documentTotal === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.documentTotal")),
        value: String(o.documentTotal),
      });
    }
    if (typeof o.indexedDocuments === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.indexedDocuments")),
        value: String(o.indexedDocuments),
      });
    }
    if (typeof o.skippedDocuments === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.skippedDocuments")),
        value: String(o.skippedDocuments),
      });
    }
    if (typeof o.failedDocuments === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.failedDocuments")),
        value: String(o.failedDocuments),
      });
    }
    if (typeof o.discoveredUrls === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.discoveredUrls")),
        value: String(o.discoveredUrls),
      });
    }
    if (typeof o.toCrawlUrls === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.toCrawlUrls")),
        value: String(o.toCrawlUrls),
      });
    }
    if (typeof o.preset === "string" && o.preset.trim()) {
      rows.push({ label: String(t("views.kbAsync.resultMeta.crawlPreset")), value: o.preset.trim() });
    }
    if (typeof o.policySummary === "string" && o.policySummary.trim()) {
      rows.push({ label: String(t("views.kbAsync.resultMeta.crawlPolicy")), value: o.policySummary.trim() });
    }
    if (typeof o.successCount === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.successCount")),
        value: String(o.successCount),
      });
    }
    if (typeof o.skippedCount === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.skippedCount")),
        value: String(o.skippedCount),
      });
    }
    if (typeof o.failCount === "number") {
      rows.push({
        label: String(t("views.kbAsync.resultMeta.failCount")),
        value: String(o.failCount),
      });
    }
    if (typeof o.summary === "string" && o.summary.trim()) {
      rows.push({ label: String(t("views.kbAsync.resultMeta.summary")), value: o.summary.trim() });
    }
    if (typeof o.runId === "number") {
      rows.push({ label: String(t("views.kbAsync.resultMeta.crawlRunId")), value: String(o.runId) });
    }
    const fbc = o.failedByCode as Record<string, unknown> | undefined;
    if (fbc && typeof fbc === "object") {
      const parts = Object.entries(fbc)
        .filter(([, v]) => typeof v === "number")
        .map(([k, v]) => `${k}: ${v}`)
        .join(", ");
      if (parts) {
        rows.push({
          label: String(t("views.kbAsync.resultMeta.failedByCode")),
          value: parts,
        });
      }
    }
    const dbs = o.discoveryByStrategy as Record<string, unknown> | undefined;
    if (dbs && typeof dbs === "object") {
      const parts = Object.entries(dbs)
        .filter(([, v]) => typeof v === "number")
        .map(([k, v]) => `${k}: ${v}`)
        .join(", ");
      if (parts) {
        rows.push({
          label: String(t("views.kbAsync.resultMeta.discoveryByStrategy")),
          value: parts,
        });
      }
    }
    if (typeof o.stage === "string" && typeof o.message === "string" && o.message.trim()) {
      rows.push({ label: String(t("views.kbAsync.resultMeta.progress")), value: o.message.trim() });
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
    if (typeof o.summary === "string" && o.summary.trim()) {
      return o.summary.trim();
    }
    if (typeof o.indexedDocuments === "number") {
      return String(
        t("views.kbAsync.summarize.kbReindex", {
          ok: o.indexedDocuments,
          skip: typeof o.skippedDocuments === "number" ? o.skippedDocuments : 0,
          fail: typeof o.failedDocuments === "number" ? o.failedDocuments : 0,
          chunks: typeof o.chunkCount === "number" ? o.chunkCount : 0,
        }),
      );
    }
    if (typeof o.documentId === "number" && typeof o.chunkCount === "number") {
      return String(
        t("views.kbAsync.summarize.ingested", { docId: o.documentId, chunks: o.chunkCount }),
      );
    }
    if (o.indexed === true) return String(t("views.kbAsync.summarize.indexDone"));
    if (typeof o.successCount === "number" && typeof o.toCrawlUrls === "number") {
      return String(
        t("views.kbAsync.summarize.siteCrawl", {
          ok: o.successCount,
          total: o.toCrawlUrls,
          fail: typeof o.failCount === "number" ? o.failCount : 0,
        }),
      );
    }
    if (typeof o.message === "string" && o.message.trim() && typeof o.stage === "string") {
      return o.message.trim();
    }
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
  if (s === "EMBEDDING") return String(t("views.kbAsync.ragDocStatus.EMBEDDING"));
  if (s === "PARSE_FAILED") return String(t("views.kbAsync.ragDocStatus.PARSE_FAILED"));
  if (s === "INDEX_FAILED") return String(t("views.kbAsync.ragDocStatus.INDEX_FAILED"));
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

/** 爬取进度弹窗：管理员可读的单次爬取结果（隐藏 runId、策略 JSON 等技术字段）。 */
export type SiteCrawlOutcomeTone = "success" | "warning" | "danger" | "info";

export type SiteCrawlFailureLine = { label: string; count: number };

export type SiteCrawlRunDetailSlice = {
  ok?: number;
  skipped?: number;
  fail?: number;
  failedByCode?: Record<string, number>;
  queueByStatus?: Record<string, number>;
};

export type SiteCrawlOutcomeView = {
  headline: string;
  tone: SiteCrawlOutcomeTone;
  ok: number;
  skipped: number;
  fail: number;
  discoveredNote?: string;
  skippedNote?: string;
  failures: SiteCrawlFailureLine[];
  pendingNote?: string;
  showResumePending: boolean;
  showRetryFailed: boolean;
  runId: number | null;
};

function parseIntRecord(raw: unknown): Record<string, number> {
  if (!raw || typeof raw !== "object") return {};
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof v === "number" && v > 0) {
      out[k] = v;
    }
  }
  return out;
}

function mergeFailMaps(...maps: Record<string, number>[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const m of maps) {
    for (const [k, v] of Object.entries(m)) {
      out[k] = (out[k] ?? 0) + v;
    }
  }
  return out;
}

function humanizeCrawlFailCode(code: string, t: ComposerTranslation): string {
  const key = `views.kbMatrix.crawlFailReason.${code}` as const;
  const tr = t(key);
  if (tr !== key) return String(tr);
  if (code.startsWith("HTTP_")) {
    return String(t("views.kbMatrix.crawlFailReason.HTTP", { code: code.slice(5) }));
  }
  return code;
}

function failureLinesFromMap(map: Record<string, number>, t: ComposerTranslation): SiteCrawlFailureLine[] {
  return Object.entries(map)
    .sort((a, b) => b[1] - a[1])
    .map(([code, count]) => ({ label: humanizeCrawlFailCode(code, t), count }));
}

type CrawlStatTriplet = { ok: number; skipped: number; fail: number };

function crawlStatSum(t: CrawlStatTriplet): number {
  return t.ok + t.skipped + t.fail;
}

/**
 * 合并 job.result_json 计数与 GET crawl-runs 详情。
 * 运行中缓存的 run 详情常为 0，不得覆盖已完成的 successCount。
 */
export function mergeSiteCrawlStatCounts(
  fromResult: CrawlStatTriplet,
  fromRunDetail: CrawlStatTriplet | null | undefined,
): CrawlStatTriplet {
  if (!fromRunDetail) {
    return fromResult;
  }
  const resultSum = crawlStatSum(fromResult);
  const detailSum = crawlStatSum(fromRunDetail);
  if (detailSum > 0 && detailSum >= resultSum) {
    return fromRunDetail;
  }
  if (resultSum > 0) {
    return fromResult;
  }
  return fromRunDetail;
}

function buildHeadline(
  ok: number,
  skipped: number,
  fail: number,
  summary: string | undefined,
  error: string | undefined,
  progressMsg: string | undefined,
  t: ComposerTranslation,
): { headline: string; tone: SiteCrawlOutcomeTone } {
  if (error?.trim()) {
    return {
      headline: String(t("views.kbMatrix.crawlOutcome.failWithError", { msg: error.trim() })),
      tone: "danger",
    };
  }
  if (progressMsg?.trim()) {
    return { headline: progressMsg.trim(), tone: "info" };
  }
  if (summary?.trim()) {
    const tone: SiteCrawlOutcomeTone =
      fail > 0 && ok > 0 ? "warning" : fail > 0 && ok === 0 ? "danger" : "success";
    return { headline: summary.trim(), tone };
  }
  if (ok === 0 && fail === 0 && skipped > 0) {
    return {
      headline: String(t("views.kbMatrix.crawlOutcome.allSkipped", { n: skipped })),
      tone: "info",
    };
  }
  if (fail > 0 && ok === 0) {
    return {
      headline: String(t("views.kbMatrix.crawlOutcome.allFailed", { fail })),
      tone: "danger",
    };
  }
  if (fail > 0) {
    return {
      headline: String(t("views.kbMatrix.crawlOutcome.partial", { ok, fail })),
      tone: "warning",
    };
  }
  if (ok > 0) {
    return {
      headline: String(t("views.kbMatrix.crawlOutcome.success", { ok, skipped })),
      tone: "success",
    };
  }
  return {
    headline: String(t("views.kbMatrix.crawlOutcome.noArticles")),
    tone: "info",
  };
}

/** 将任务结果 JSON + 可选 Run 详情合并为面向业务的爬取结果视图。 */
export function buildSiteCrawlOutcomeView(
  resultJson: string | null | undefined,
  runDetail: SiteCrawlRunDetailSlice | null | undefined,
  t: ComposerTranslation,
): SiteCrawlOutcomeView | null {
  if (resultJson == null || !String(resultJson).trim()) {
    return null;
  }
  let o: Record<string, unknown>;
  try {
    o = JSON.parse(resultJson) as Record<string, unknown>;
  } catch {
    return null;
  }

  const fromResult: CrawlStatTriplet = {
    ok: typeof o.successCount === "number" ? o.successCount : 0,
    skipped: typeof o.skippedCount === "number" ? o.skippedCount : 0,
    fail: typeof o.failCount === "number" ? o.failCount : 0,
  };
  const fromRunDetail: CrawlStatTriplet | null = runDetail
    ? {
        ok: typeof runDetail.ok === "number" ? runDetail.ok : 0,
        skipped: typeof runDetail.skipped === "number" ? runDetail.skipped : 0,
        fail: typeof runDetail.fail === "number" ? runDetail.fail : 0,
      }
    : null;
  const merged = mergeSiteCrawlStatCounts(fromResult, fromRunDetail);
  const ok = merged.ok;
  const skipped = merged.skipped;
  const fail = merged.fail;

  const summary = typeof o.summary === "string" ? o.summary : undefined;
  const error = typeof o.error === "string" ? o.error : undefined;
  const progressMsg =
    typeof o.message === "string" && o.message.trim() ? o.message.trim() : undefined;
  const runId = typeof o.runId === "number" ? o.runId : null;

  const failedByCode = mergeFailMaps(
    parseIntRecord(o.failedByCode),
    runDetail?.failedByCode ?? {},
  );
  const failures = failureLinesFromMap(failedByCode, t);

  const queue = runDetail?.queueByStatus ?? {};
  const pending =
    (queue.PENDING ?? 0) + (queue.IN_PROGRESS ?? 0) + (queue.EXPLORE ?? 0);

  const { headline, tone } = buildHeadline(ok, skipped, fail, summary, error, progressMsg, t);

  let discoveredNote: string | undefined;
  const discovered = typeof o.discoveredUrls === "number" ? o.discoveredUrls : undefined;
  const toCrawl = typeof o.toCrawlUrls === "number" ? o.toCrawlUrls : undefined;
  if (discovered != null && toCrawl != null) {
    discoveredNote = String(
      t("views.kbMatrix.crawlOutcome.discovered", { discovered, toCrawl }),
    );
  } else if (discovered != null) {
    discoveredNote = String(t("views.kbMatrix.crawlOutcome.discoveredOnly", { discovered }));
  }

  const skippedNote = skipped > 0 ? String(t("views.kbMatrix.crawlOutcome.skippedHint")) : undefined;

  const pendingNote =
    pending > 0
      ? String(t("views.kbMatrix.crawlOutcome.pending", { n: pending }))
      : undefined;

  return {
    headline,
    tone,
    ok,
    skipped,
    fail,
    discoveredNote,
    skippedNote,
    failures,
    pendingNote,
    showResumePending: pending > 0 && runId != null,
    showRetryFailed: (fail > 0 || (queue.FAILED ?? 0) > 0) && runId != null,
    runId,
  };
}
