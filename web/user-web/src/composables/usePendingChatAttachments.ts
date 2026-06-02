import { computed, onBeforeUnmount, ref, type Ref } from "vue";
import * as chatApi from "@/api/chat";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";

export type PendingAttachmentKind = "image" | "document";

export type PendingAttachmentStatus = "queued" | "uploading" | "done" | "error";

export interface PendingAttachment {
  localKey: string;
  file: File;
  kind: PendingAttachmentKind;
  previewUrl?: string;
  status: PendingAttachmentStatus;
  /** 0–100；上传中随网络进度增加，完成后为 100 */
  uploadPercent?: number;
  serverId?: number;
  fileName: string;
  charLength?: number;
  textExtracted?: boolean;
  errorMessage?: string;
}

const IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp)$/i;

function resolveKindFromName(fileName: string): PendingAttachmentKind {
  return IMAGE_EXT.test(fileName) ? "image" : "document";
}

export function resolvePendingAttachmentKind(file: File): PendingAttachmentKind {
  if (file.type.startsWith("image/")) {
    return "image";
  }
  return resolveKindFromName(file.name);
}

/** 剪贴板图片常无扩展名，规范为可上传文件名。 */
export function normalizeClipboardFile(file: File): File {
  const name = file.name?.trim() ?? "";
  const hasExt = /\.[a-z0-9]{2,8}$/i.test(name);
  if (name && name !== "blob" && hasExt) {
    return file;
  }
  const mime = file.type || "application/octet-stream";
  const ext =
    mime === "image/png"
      ? "png"
      : mime === "image/jpeg" || mime === "image/jpg"
        ? "jpg"
        : mime === "image/gif"
          ? "gif"
          : mime === "image/webp"
            ? "webp"
            : mime === "image/bmp"
              ? "bmp"
              : mime === "application/pdf"
                ? "pdf"
                : mime.startsWith("image/")
                  ? "png"
                  : "bin";
  const base = mime.startsWith("image/") ? "pasted-image" : "pasted-file";
  return new File([file], `${base}.${ext}`, { type: mime });
}

/** 粘贴/拖入批次内去重键（同名不同路径的同一文件可能 lastModified 略异，以 size+type 为主）。 */
function fileBatchDedupKey(f: File): string {
  return `${f.size}:${f.type}:${f.lastModified}:${f.name}`;
}

function fileBatchDedupKeyLoose(f: File): string {
  return `${f.size}:${f.type}`;
}

export function filesFromClipboardEvent(e: ClipboardEvent): File[] {
  const dt = e.clipboardData;
  if (!dt) {
    return [];
  }
  const out: File[] = [];
  const seenStrict = new Set<string>();
  const seenLoose = new Set<string>();
  const push = (raw: File | null) => {
    if (!raw || raw.size === 0) {
      return;
    }
    const f = normalizeClipboardFile(raw);
    const strict = fileBatchDedupKey(f);
    const loose = fileBatchDedupKeyLoose(f);
    if (seenStrict.has(strict) || seenLoose.has(loose)) {
      return;
    }
    seenStrict.add(strict);
    seenLoose.add(loose);
    out.push(f);
  };
  // 有 files 时仅用 FileList：再扫 items 会在 Chrome/Edge 上对同一粘贴重复入队（且 items 侧常无扩展名）。
  if (dt.files?.length) {
    for (let i = 0; i < dt.files.length; i++) {
      push(dt.files[i] ?? null);
    }
    return out;
  }
  for (let i = 0; i < dt.items.length; i++) {
    const item = dt.items[i];
    if (!item) {
      continue;
    }
    if (item.kind === "file") {
      push(item.getAsFile());
    }
  }
  return out;
}

function newLocalKey(): string {
  return `att-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/** 网络上传最多显示 95%，留待服务端解析完成后到 100%。 */
function networkUploadPercent(loaded: number, total: number): number {
  if (total <= 0) {
    return 0;
  }
  return Math.min(95, Math.round((loaded / total) * 100));
}

export function usePendingChatAttachments(options: {
  convId: Ref<string | null>;
  maxAttachmentsLimit: Ref<number>;
  createConversation: (title: string) => Promise<{ id: string }>;
  newConvTitle: () => string;
}) {
  const { t } = useI18n();
  const pendingAttachments = ref<PendingAttachment[]>([]);
  let ensureConvInFlight: Promise<string | null> | null = null;

  const uploadingCount = computed(
    () =>
      pendingAttachments.value.filter((p) => p.status === "queued" || p.status === "uploading")
        .length,
  );

  const hasUploading = computed(() => uploadingCount.value > 0);

  const doneAttachments = computed(() =>
    pendingAttachments.value.filter((p) => p.status === "done" && p.serverId != null),
  );

  function revokePreview(item: PendingAttachment) {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl);
    }
  }

  function removeAllPreviews() {
    for (const p of pendingAttachments.value) {
      revokePreview(p);
    }
  }

  onBeforeUnmount(() => {
    removeAllPreviews();
  });

  /** 替换数组项以触发 Vue 对 chip 状态的即时刷新（避免仅改属性不 re-render）。 */
  function patchAttachment(localKey: string, patch: Partial<PendingAttachment>) {
    const idx = pendingAttachments.value.findIndex((p) => p.localKey === localKey);
    if (idx < 0) {
      return;
    }
    const cur = pendingAttachments.value[idx]!;
    const next: PendingAttachment = { ...cur, ...patch };
    const copy = pendingAttachments.value.slice();
    copy[idx] = next;
    pendingAttachments.value = copy;
  }

  async function ensureConversationForAttach(): Promise<string | null> {
    if (options.convId.value) {
      return options.convId.value;
    }
    if (ensureConvInFlight) {
      return ensureConvInFlight;
    }
    ensureConvInFlight = (async () => {
      try {
        const c = await options.createConversation(options.newConvTitle());
        options.convId.value = c.id;
        return c.id;
      } catch (e: unknown) {
        ElMessage.error(apiRequestErrorMessage(e, t("chat.uploadFail")));
        return null;
      } finally {
        ensureConvInFlight = null;
      }
    })();
    return ensureConvInFlight;
  }

  function findItem(localKey: string): PendingAttachment | undefined {
    return pendingAttachments.value.find((p) => p.localKey === localKey);
  }

  async function uploadOne(localKey: string, conv: string) {
    const item = findItem(localKey);
    if (!item) {
      return;
    }
    patchAttachment(localKey, { status: "uploading", uploadPercent: 0, errorMessage: undefined });
    try {
      const res = await chatApi.uploadChatAttachment(conv, item.file, (loaded, total) => {
        patchAttachment(localKey, { uploadPercent: networkUploadPercent(loaded, total) });
      });
      patchAttachment(localKey, {
        status: "done",
        uploadPercent: 100,
        serverId: res.id,
        fileName: res.fileName,
        charLength: res.charLength,
        textExtracted: res.textExtracted,
        kind: res.kind === "image" ? "image" : "document",
      });
    } catch (e: unknown) {
      patchAttachment(localKey, {
        status: "error",
        uploadPercent: undefined,
        errorMessage: apiRequestErrorMessage(e, t("chat.attachUploadFailed")),
      });
    }
  }

  async function startUpload(localKey: string) {
    const item = findItem(localKey);
    if (!item) {
      return;
    }
    const conv = await ensureConversationForAttach();
    if (!conv) {
      patchAttachment(localKey, {
        status: "error",
        errorMessage: t("chat.attachUploadFailed"),
      });
      return;
    }
    await uploadOne(localKey, conv);
  }

  function isDuplicatePending(file: File): boolean {
    const loose = fileBatchDedupKeyLoose(file);
    return pendingAttachments.value.some((p) => fileBatchDedupKeyLoose(p.file) === loose);
  }

  function addFiles(files: File[]) {
    if (!files.length) {
      return;
    }
    const cap = options.maxAttachmentsLimit.value;
    const room = Math.max(0, cap - pendingAttachments.value.length);
    const batchSeenStrict = new Set<string>();
    const batchSeenLoose = new Set<string>();
    const uniqueIncoming: File[] = [];
    for (const raw of files) {
      const file = normalizeClipboardFile(raw);
      const strict = fileBatchDedupKey(file);
      const loose = fileBatchDedupKeyLoose(file);
      if (batchSeenStrict.has(strict) || batchSeenLoose.has(loose)) {
        continue;
      }
      if (isDuplicatePending(file)) {
        continue;
      }
      batchSeenStrict.add(strict);
      batchSeenLoose.add(loose);
      uniqueIncoming.push(file);
    }
    if (!uniqueIncoming.length) {
      return;
    }
    const slice = uniqueIncoming.slice(0, room);
    if (slice.length < uniqueIncoming.length) {
      ElMessage.warning(t("chat.attachLimitReached", { n: cap }));
    }
    for (const file of slice) {
      const kind = resolvePendingAttachmentKind(file);
      const localKey = newLocalKey();
      const item: PendingAttachment = {
        localKey,
        file,
        kind,
        fileName: file.name,
        status: "queued",
        uploadPercent: 0,
        previewUrl: kind === "image" ? URL.createObjectURL(file) : undefined,
      };
      pendingAttachments.value = [...pendingAttachments.value, item];
      void startUpload(localKey);
    }
  }

  function removeAt(index: number) {
    const [removed] = pendingAttachments.value.splice(index, 1);
    if (removed) {
      revokePreview(removed);
    }
    pendingAttachments.value = [...pendingAttachments.value];
  }

  function retry(localKey: string) {
    const item = findItem(localKey);
    if (!item || item.status !== "error") {
      return;
    }
    patchAttachment(localKey, { status: "queued", uploadPercent: 0, errorMessage: undefined });
    void startUpload(localKey);
  }

  function clearDone() {
    const keep = pendingAttachments.value.filter((p) => p.status !== "done");
    for (const p of pendingAttachments.value) {
      if (p.status === "done") {
        revokePreview(p);
      }
    }
    pendingAttachments.value = keep;
  }

  function clearAll() {
    removeAllPreviews();
    pendingAttachments.value = [];
  }

  function attachmentIdsForSend(): number[] {
    return doneAttachments.value.map((p) => p.serverId!);
  }

  function attachmentViewsForSend(): chatApi.ChatAttachmentMessage[] {
    return doneAttachments.value.map((p) => ({
      id: p.serverId!,
      fileName: p.fileName,
      charLength: p.charLength ?? null,
      textExtracted: p.textExtracted ?? false,
      kind: p.kind,
    }));
  }

  return {
    pendingAttachments,
    uploadingCount,
    hasUploading,
    doneAttachments,
    addFiles,
    removeAt,
    retry,
    clearDone,
    clearAll,
    attachmentIdsForSend,
    attachmentViewsForSend,
  };
}
