/** 管理端知识中心：异步任务类型、状态、阶段等对用户可读的中文映射 */

export function jobTaskTypeLabel(taskType: string | null | undefined): string {
  switch ((taskType || "").toUpperCase()) {
    case "RAG_INDEX":
      return "知识库索引重建";
    case "RAG_URL_IMPORT":
      return "网页抓取入库";
    case "RAG_FILE_IMPORT":
      return "文件 / Markdown 入库";
    default:
      return taskType?.trim() || "—";
  }
}

/** 表格标签等紧凑用语 */
export function jobTaskTypeShort(taskType: string | null | undefined): string {
  switch ((taskType || "").toUpperCase()) {
    case "RAG_URL_IMPORT":
      return "网页入库";
    case "RAG_FILE_IMPORT":
      return "文件入库";
    case "RAG_INDEX":
      return "索引";
    default:
      return jobTaskTypeLabel(taskType);
  }
}

export type JobStatusTag = "success" | "warning" | "info" | "danger";

export function jobStatusMeta(status: string | null | undefined): { label: string; tag: JobStatusTag } {
  const s = (status || "").toUpperCase();
  if (s === "SUCCEEDED") return { label: "已完成", tag: "success" };
  if (s === "FAILED") return { label: "失败", tag: "danger" };
  if (s === "RUNNING") return { label: "执行中", tag: "warning" };
  if (s === "PENDING") return { label: "等待中", tag: "info" };
  return { label: status?.trim() || "—", tag: "info" };
}

export function jobStepPhaseLabel(phase: string | null | undefined): string {
  const p = (phase || "").trim().toLowerCase();
  const map: Record<string, string> = {
    fetch_url: "下载网页",
    html_to_md: "转为 Markdown",
    persist: "保存文档与分片",
    vector: "写入向量索引",
    markdown_source: "读取正文",
  };
  return map[p] ?? (phase?.trim() || "—");
}

export function jobStepStatusLabel(status: string | null | undefined): string {
  const s = (status || "").trim().toLowerCase();
  if (s === "running") return "进行中";
  if (s === "ok") return "已完成";
  if (s === "failed" || s === "error") return "失败";
  return status?.trim() || "—";
}

export function humanizeJobStepDetail(detail: string | null | undefined): string {
  if (detail == null || !String(detail).trim()) return "";
  let d = String(detail).trim();
  const mDoc = /^documentId=(\d+),chunks=(\d+)$/.exec(d);
  if (mDoc) return `文档编号 ${mDoc[1]}，共 ${mDoc[2]} 个分片`;
  if (/^bytes=\d+$/i.test(d)) return `已下载约 ${d.slice(6)} 字节`;
  if (/^chars=\d+$/i.test(d)) return `正文约 ${d.slice(6)} 字`;
  if (/^collection=/.test(d)) return `向量集合：${d.replace(/^collection=/, "")}`;
  return d;
}

export function ragChunkStrategyLabel(code: number | string | null | undefined): string {
  const n = typeof code === "string" ? Number.parseInt(code, 10) : code;
  if (n == null || Number.isNaN(n)) return "—";
  switch (n) {
    case 0:
      return "整篇不分片";
    case 1:
      return "固定字数切分";
    case 2:
      return "语义段落";
    case 3:
      return "滑动窗口";
    case 99:
      return "自定义（预留）";
    default:
      return `策略代码 ${n}`;
  }
}

export type JobKvRow = { label: string; value: string };

export function parseJobPayloadRows(payloadJson: string | null | undefined): JobKvRow[] {
  if (payloadJson == null || !String(payloadJson).trim()) return [];
  let o: Record<string, unknown>;
  try {
    o = JSON.parse(payloadJson) as Record<string, unknown>;
  } catch {
    return [{ label: "原始入参", value: String(payloadJson) }];
  }
  const rows: JobKvRow[] = [];
  if (o.kbId != null && o.kbId !== "") rows.push({ label: "目标知识库", value: `#${String(o.kbId)}` });
  if (typeof o.url === "string" && o.url.trim()) rows.push({ label: "网页地址", value: o.url.trim() });
  if (typeof o.originalFilename === "string" && o.originalFilename.trim())
    rows.push({ label: "文件名", value: o.originalFilename.trim() });
  if (o.chunkStrategy != null && o.chunkStrategy !== "")
    rows.push({ label: "分片策略", value: ragChunkStrategyLabel(o.chunkStrategy as number) });
  if (typeof o.contentType === "string" && o.contentType.trim())
    rows.push({ label: "内容类型", value: o.contentType.trim() });
  if (typeof o.markdownContent === "string" && o.markdownContent.length > 0) {
    rows.push({
      label: "Markdown 正文",
      value: `已填写（约 ${o.markdownContent.length} 字，完整内容见下方「原始数据」）`,
    });
  }
  return rows;
}

export function parseJobResultMetaRows(resultJson: string | null | undefined): JobKvRow[] {
  if (resultJson == null || !String(resultJson).trim()) return [];
  try {
    const o = JSON.parse(resultJson) as Record<string, unknown>;
    const rows: JobKvRow[] = [];
    if (typeof o.documentId === "number") rows.push({ label: "文档编号", value: String(o.documentId) });
    if (typeof o.chunkCount === "number") rows.push({ label: "分片数量", value: `${o.chunkCount} 块` });
    if (typeof o.kbId === "number") rows.push({ label: "知识库编号", value: String(o.kbId) });
    if (typeof o.error === "string" && o.error.trim()) rows.push({ label: "错误说明", value: o.error.trim() });
    if (o.indexed === true) rows.push({ label: "索引结果", value: "占位任务已结束（尚未执行全库向量重建）" });
    return rows;
  } catch {
    return [];
  }
}

export function summarizeJobResult(resultJson: string | null | undefined): string {
  if (resultJson == null || !String(resultJson).trim()) return "—";
  try {
    const o = JSON.parse(resultJson) as Record<string, unknown>;
    if (typeof o.error === "string" && o.error.trim()) {
      const e = o.error.trim();
      return e.length > 80 ? `失败：${e.slice(0, 80)}…` : `失败：${e}`;
    }
    if (typeof o.documentId === "number" && typeof o.chunkCount === "number") {
      return `已入库 · 文档 ${o.documentId} · ${o.chunkCount} 个分片`;
    }
    if (o.indexed === true) return "索引占位已完成";
    const steps = o.steps as unknown[] | undefined;
    if (Array.isArray(steps) && steps.length > 0) return `共 ${steps.length} 个执行阶段`;
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

/** 文档列表「展示状态」列（与后端 RagDocumentDisplayStatus 一致） */
export function ragDocumentDisplayStatusLabel(code: string | null | undefined): string {
  const s = (code || "").toUpperCase();
  if (s === "PUBLISHED") return "已发布";
  if (s === "PARSING") return "解析中";
  if (s === "PARSE_FAILED") return "解析失败";
  return code?.trim() || "—";
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
