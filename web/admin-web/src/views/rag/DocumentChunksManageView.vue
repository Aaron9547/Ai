<template>
  <div class="page document-chunks-page" v-loading="pageLoading">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.chunks.title") }}</span>
            <p class="sub">
              {{ t("views.chunks.sub", { doc: docTitle }) }}
              <template v-if="hasParentChildChunks"> {{ t("views.chunks.subParentChild") }}</template>
            </p>
          </div>
        </div>
      </template>

      <div class="dc-split">
      <section class="dc-main">
        <div class="dc-toolbar">
          <div class="dc-toolbar-left">
            <el-input
              v-model="chunkSearch"
              class="dc-search"
              clearable
              :placeholder="t('views.chunks.searchPh')"
              prefix-icon="Search"
            />
            <div v-if="hasParentChildChunks" class="dc-view-mode">
              <span class="dc-view-mode-label">{{ t("views.chunks.viewModeLabel") }}</span>
              <el-radio-group v-model="chunkViewMode" size="small">
                <el-radio-button value="retrieval">{{ t("views.chunks.viewRetrieval") }}</el-radio-button>
                <el-radio-button value="all">{{ t("views.chunks.viewAll") }}</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <el-button type="primary" class="dc-toolbar-add" @click="openNewChunk">{{ t("views.chunks.newChunk") }}</el-button>
        </div>

        <el-alert
          v-if="hasParentChildChunks && chunkViewMode === 'retrieval'"
          type="info"
          :closable="false"
          show-icon
          class="dc-hint-alert"
        >
          {{ t("views.chunks.hintParentHidden") }}
        </el-alert>
        <el-alert
          v-if="chunkCoverageLow"
          type="warning"
          :closable="false"
          show-icon
          class="dc-hint-alert"
        >
          {{
            t("views.chunks.hintCoverageLow", {
              chunkChars: totalChunkChars,
              docChars: docMdChars,
            })
          }}
        </el-alert>

        <el-empty
          v-if="!pageLoading && !filteredChunks.length"
          :description="t('views.chunks.empty')"
          :image-size="72"
        />

        <div v-else class="dc-grid">
          <article
            v-for="row in pagedChunks"
            :key="row.id"
            class="chunk-card"
            :class="{ 'chunk-card--parent': isParentChunk(row) }"
          >
            <header class="chunk-card-hdr">
              <span class="chunk-seq">
                <el-tag v-if="effectiveChunkRole(row) === 'PARENT'" type="info" size="small" class="chunk-role-tag">{{ t("views.chunks.roleParent") }}</el-tag>
                <el-tag v-else-if="effectiveChunkRole(row) === 'CHILD'" type="success" size="small" class="chunk-role-tag">{{ t("views.chunks.roleChild") }}</el-tag>
                {{ t("views.chunks.seq", { n: displaySeq(row), total: displayTotal(row) }) }}
              </span>
              <span class="chunk-hit" :title="t('views.chunks.hitTitle')">{{ t("views.chunks.hitLabel", { n: row.hitCount ?? 0 }) }}</span>
              <span v-if="isParentChunk(row)" class="chunk-parent-hint">{{ t("views.chunks.parentContextOnly") }}</span>
              <el-switch
                v-else
                :model-value="isChunkEnabled(row)"
                inline-prompt
                :active-text="t('views.chunks.enable')"
                :inactive-text="t('views.chunks.disable')"
                style="--el-switch-on-color: #16a34a; --el-switch-off-color: var(--el-text-color-placeholder)"
                :loading="toggleLoadingId === row.id"
                @change="(v: string | number | boolean) => onToggleRetrieval(row, Boolean(v))"
              />
              <el-dropdown trigger="click" @command="(cmd: string) => onChunkMenu(cmd, row)">
                <el-button text type="primary" class="chunk-more">···</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑内容</el-dropdown-item>
                    <el-dropdown-item command="copy">复制全文</el-dropdown-item>
                    <el-dropdown-item command="merge" :disabled="!hasNextChunk(row)">与下一块合并</el-dropdown-item>
                    <el-dropdown-item command="reindex">重置向量</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除分片</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </header>
            <button type="button" class="chunk-body" @click="openEditChunk(row)">
              <div
                v-if="shouldRenderChunkMarkdown(row)"
                class="chunk-text chunk-md"
                v-html="chunkMarkdownHtml(row.content)"
              />
              <p v-else class="chunk-text">
                <template v-for="(seg, i) in highlightSegments(row.content)" :key="i">
                  <mark v-if="seg.hl" class="chunk-hl">{{ seg.t }}</mark>
                  <template v-else>{{ seg.t }}</template>
                </template>
              </p>
            </button>
            <footer class="chunk-ft">
              <span>{{ t("views.chunks.chars", { n: charCount(row) }) }}</span>
            </footer>
          </article>
        </div>

        <div v-if="showPager" class="dc-pager-wrap">
          <el-pagination
            v-model:current-page="chunkPage"
            :page-size="chunkPageSize"
            :total="filteredChunks.length"
            layout="total, prev, pager, next"
            background
            small
          />
        </div>
      </section>

      <aside class="dc-aside">
        <div class="aside-card">
          <h3 class="aside-h">文档信息</h3>
          <dl class="aside-dl">
            <dt>名称</dt>
            <dd class="aside-muted">{{ doc?.title || "—" }}</dd>
            <dt>分类</dt>
            <dd>
              <el-button v-if="doc" link type="primary" class="aside-link" @click="openDocMeta">
                {{ doc.categoryName || "未分类" }}
              </el-button>
            </dd>
            <dt>适用范围</dt>
            <dd class="aside-muted">{{ doc?.applicableScope || "—" }}</dd>
            <dt>文档命中</dt>
            <dd class="aside-muted">{{ doc?.hitCount ?? 0 }}（按分片命中累计）</dd>
            <dt>更新时间</dt>
            <dd class="aside-muted">{{ formatTime(doc?.updatedAt) }}</dd>
          </dl>
          <el-button size="small" plain @click="openDocMeta">编辑文档属性</el-button>
        </div>

        <div class="aside-card">
          <h3 class="aside-h">关联附件</h3>
          <p class="aside-tip">原始 PDF、图片等可在此归档，便于运营对照（功能规划中）。</p>
          <el-upload class="dc-upload-ph" drag disabled :show-file-list="false">
            <el-icon class="dc-upload-ico"><UploadFilled /></el-icon>
            <div class="el-upload__text">点击上传文件</div>
          </el-upload>
          <el-empty description="暂无附件" :image-size="48" />
        </div>
      </aside>
    </div>
    </el-card>

    <el-dialog v-model="editDlg" :title="t('views.chunks.dlgEditChunk')" width="800px" destroy-on-close @closed="onEditDlgClosed">
      <el-tabs v-model="editTab" class="chunk-edit-tabs">
        <el-tab-pane :label="t('views.chunks.tabChunkPreview')" name="preview">
          <div class="chunk-edit-preview chunk-md" v-html="editMarkdownHtml" />
        </el-tab-pane>
        <el-tab-pane :label="t('views.chunks.tabSource')" name="source">
          <el-input v-model="editText" type="textarea" :rows="16" class="chunk-edit-source" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="editDlg = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="newDlg" title="新增分段" width="640px" destroy-on-close @closed="newText = ''">
      <el-input v-model="newText" type="textarea" :rows="12" placeholder="输入要追加到本文档的一段知识文本" />
      <template #footer>
        <el-button @click="newDlg = false">取消</el-button>
        <el-button type="primary" :loading="newSaving" @click="saveNewChunk">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="docMetaDlg" title="文档属性" width="520px" destroy-on-close>
      <el-form v-if="doc" label-width="100px">
        <el-form-item label="分类">
          <el-select v-model="docMetaCategoryId" clearable placeholder="未分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input v-model="docMetaScope" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docMetaDlg = false">取消</el-button>
        <el-button type="primary" :loading="docMetaSaving" @click="saveDocMeta">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import * as ragApi from "../../api/ragAdmin";
import { renderMarkdownToSafeHtml } from "../../utils/renderMarkdown";
import type {
  RagChunkAdminRow,
  RagDocumentAdminRow,
  RagDocumentCategoryAdminRow,
  RagKnowledgeBaseRow,
} from "../../types/admin";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

const kbId = computed(() => Number.parseInt(String(route.params.kbId), 10));
const docId = computed(() => Number.parseInt(String(route.params.docId), 10));

const pageLoading = ref(true);
const kbRow = ref<RagKnowledgeBaseRow | null>(null);
const doc = ref<RagDocumentAdminRow | null>(null);
const chunks = ref<RagChunkAdminRow[]>([]);
/** 后端根据 parentChunkId / chunkRole 判定的子母结构 */
const chunksParentChildFromApi = ref(false);
const categories = ref<RagDocumentCategoryAdminRow[]>([]);

const chunkSearch = ref("");
/** retrieval=默认隐藏母块；all=展示母块上下文 */
const chunkViewMode = ref<"retrieval" | "all">("retrieval");
const chunkPage = ref(1);
const chunkPageSize = 12;
const PAGER_MIN_TOTAL = 20;

type ChunkRoleKind = "PARENT" | "CHILD" | "FLAT";

function effectiveChunkRole(row: RagChunkAdminRow): ChunkRoleKind {
  const role = row.chunkRole;
  if (role === "PARENT" || role === "CHILD" || role === "FLAT") return role;
  if (row.parentChunkId != null) return "CHILD";
  if (chunks.value.some((c) => c.parentChunkId === row.id)) return "PARENT";
  return "FLAT";
}

const hasParentChildChunks = computed(() => {
  if (chunksParentChildFromApi.value) return true;
  if (chunks.value.some((c) => c.parentChunkId != null)) return true;
  return chunks.value.some((c) => {
    const r = effectiveChunkRole(c);
    return r === "PARENT" || r === "CHILD";
  });
});

const visibleChunks = computed(() => {
  const list = [...chunks.value].sort((a, b) => a.seq - b.seq);
  if (!hasParentChildChunks.value || chunkViewMode.value === "all") return list;
  return list.filter((c) => effectiveChunkRole(c) !== "PARENT");
});

function applyChunksListResponse(res: import("../../api/ragAdmin").RagDocumentChunksListResponse) {
  chunks.value = res.chunks;
  chunksParentChildFromApi.value = res.parentChild;
}

const docTitle = computed(() => {
  const q = route.query.docTitle;
  if (typeof q === "string" && q.trim()) return q.trim();
  return doc.value?.title || t("views.chunks.docFallback");
});

const filteredChunks = computed(() => {
  const kw = chunkSearch.value.trim().toLowerCase();
  const list = visibleChunks.value;
  if (!kw) return list;
  return list.filter((c) => (c.content || "").toLowerCase().includes(kw));
});

const showPager = computed(() => filteredChunks.value.length >= PAGER_MIN_TOTAL);

const pagedChunks = computed(() => {
  if (!showPager.value) return filteredChunks.value;
  const start = (chunkPage.value - 1) * chunkPageSize;
  return filteredChunks.value.slice(start, start + chunkPageSize);
});

watch([chunkSearch, filteredChunks, chunkViewMode], () => {
  chunkPage.value = 1;
});

const editDlg = ref(false);
const editTab = ref<"preview" | "source">("preview");
const editText = ref("");
const editSaving = ref(false);
const editingRow = ref<RagChunkAdminRow | null>(null);

const editMarkdownHtml = computed(() => renderMarkdownToSafeHtml(editText.value || ""));

const newDlg = ref(false);
const newText = ref("");
const newSaving = ref(false);

const docMetaDlg = ref(false);
const docMetaCategoryId = ref<number | undefined>(undefined);
const docMetaScope = ref("");
const docMetaSaving = ref(false);

const toggleLoadingId = ref<number | null>(null);

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

function charCount(row: RagChunkAdminRow): number {
  if (typeof row.contentLength === "number") return row.contentLength;
  return (row.content || "").length;
}

function hasNextChunk(row: RagChunkAdminRow): boolean {
  const list = [...chunks.value].sort((a, b) => a.seq - b.seq);
  const i = list.findIndex((x) => x.id === row.id);
  return i >= 0 && i < list.length - 1;
}

function isChunkEnabled(row: RagChunkAdminRow): boolean {
  return (row.retrievalEnabled || "ENABLED") !== "DISABLED";
}

function isParentChunk(row: RagChunkAdminRow): boolean {
  return effectiveChunkRole(row) === "PARENT";
}

function displaySeq(row: RagChunkAdminRow): number {
  const list = visibleChunks.value;
  const i = list.findIndex((x) => x.id === row.id);
  return i >= 0 ? i + 1 : row.seq + 1;
}

function displayTotal(row: RagChunkAdminRow): number {
  const role = effectiveChunkRole(row);
  if (role === "PARENT" || role === "CHILD") {
    return visibleChunks.value.length;
  }
  return chunks.value.length;
}

function looksLikeMarkdown(text: string): boolean {
  const s = (text || "").trim();
  if (!s) return false;
  return (
    /^#{1,6}\s/m.test(s) ||
    /\*\*[^*]+\*\*/.test(s) ||
    /```[\s\S]*?```/m.test(s) ||
    /^\s*[-*+]\s/m.test(s) ||
    /^\s*\d+\.\s/m.test(s) ||
    /\[.+\]\([^)]+\)/.test(s) ||
    /^>\s/m.test(s) ||
    /^\s*\|/.test(s) ||
    /^\s*[-*+]\s+\[[ xX]\]\s/m.test(s)
  );
}

function shouldRenderChunkMarkdown(row: RagChunkAdminRow): boolean {
  if (chunkSearch.value.trim()) return false;
  const s = (row.content || "").trim();
  if (!s) return false;
  if (doc.value) return true;
  return looksLikeMarkdown(s);
}

const totalChunkChars = computed(() =>
  chunks.value.reduce((sum, c) => sum + (c.content || "").trim().length, 0),
);

const docMdChars = computed(() => {
  const n = doc.value?.contentLength;
  if (n != null && n > 0) return n;
  return totalChunkChars.value;
});

const chunkCoverageLow = computed(() => {
  const docLen = docMdChars.value;
  if (docLen < 200) return false;
  let covered = totalChunkChars.value;
  if (hasParentChildChunks.value) {
    covered = chunks.value
      .filter((c) => {
        const r = effectiveChunkRole(c);
        return r === "CHILD" || r === "FLAT";
      })
      .reduce((sum, c) => sum + (c.content || "").trim().length, 0);
  }
  return covered < docLen * 0.85;
});

function chunkMarkdownHtml(content: string): string {
  return renderMarkdownToSafeHtml(content || "");
}

function onEditDlgClosed() {
  editingRow.value = null;
  editTab.value = "preview";
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function highlightSegments(text: string): { t: string; hl: boolean }[] {
  const raw = text || "";
  const kw = chunkSearch.value.trim();
  if (!kw) return [{ t: raw, hl: false }];
  try {
    const re = new RegExp(`(${escapeRegExp(kw)})`, "gi");
    const out: { t: string; hl: boolean }[] = [];
    let last = 0;
    let m: RegExpExecArray | null;
    const r = new RegExp(re.source, re.flags);
    while ((m = r.exec(raw)) != null) {
      if (m.index > last) out.push({ t: raw.slice(last, m.index), hl: false });
      out.push({ t: m[0], hl: true });
      last = m.index + m[0].length;
    }
    if (last < raw.length) out.push({ t: raw.slice(last), hl: false });
    return out.length ? out : [{ t: raw, hl: false }];
  } catch {
    return [{ t: raw, hl: false }];
  }
}

async function loadAll() {
  pageLoading.value = true;
  try {
    const [kbs, d, chRes] = await Promise.all([
      ragApi.fetchRagKbs(),
      ragApi.fetchRagKbDocument(kbId.value, docId.value),
      ragApi.fetchRagKbChunks(kbId.value, docId.value),
    ]);
    kbRow.value = kbs.find((x) => x.id === kbId.value) ?? null;
    doc.value = d;
    applyChunksListResponse(chRes);
    if (!kbRow.value) {
      ElMessage.error("未找到该知识库");
      void router.push("/knowledge-center/knowledge-bases");
      return;
    }
    try {
      categories.value = await ragApi.fetchRagKbDocumentCategories(kbId.value);
    } catch {
      categories.value = [];
    }
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载失败";
    ElMessage.error(msg);
    void router.push({ path: "/knowledge-center/knowledge-bases", query: { kbId: String(kbId.value) } });
  } finally {
    pageLoading.value = false;
  }
}

async function reloadChunks() {
  try {
    applyChunksListResponse(await ragApi.fetchRagKbChunks(kbId.value, docId.value));
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载分片失败";
    ElMessage.error(msg);
  }
}

async function onToggleRetrieval(row: RagChunkAdminRow, enabled: boolean) {
  toggleLoadingId.value = row.id;
  try {
    const updated = await ragApi.patchRagKbChunk(kbId.value, docId.value, row.id, {
      retrievalEnabled: enabled ? "ENABLED" : "DISABLED",
    });
    const i = chunks.value.findIndex((x) => x.id === updated.id);
    if (i >= 0) chunks.value[i] = updated;
    ElMessage.success(enabled ? "已启用检索" : "已禁用检索");
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "更新失败";
    ElMessage.error(msg);
  } finally {
    toggleLoadingId.value = null;
  }
}

function openEditChunk(row: RagChunkAdminRow) {
  editingRow.value = row;
  editText.value = row.content;
  editTab.value = looksLikeMarkdown(row.content || "") ? "preview" : "source";
  editDlg.value = true;
}

async function saveEdit() {
  if (!editingRow.value) return;
  editSaving.value = true;
  try {
    const u = await ragApi.patchRagKbChunk(kbId.value, docId.value, editingRow.value.id, {
      content: editText.value,
    });
    const i = chunks.value.findIndex((x) => x.id === u.id);
    if (i >= 0) chunks.value[i] = u;
    ElMessage.success("已保存");
    editDlg.value = false;
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    editSaving.value = false;
  }
}

function openNewChunk() {
  newText.value = "";
  newDlg.value = true;
}

async function saveNewChunk() {
  const body = newText.value.trim();
  if (!body) {
    ElMessage.warning("请输入分段内容");
    return;
  }
  newSaving.value = true;
  try {
    const u = await ragApi.createRagKbChunk(kbId.value, docId.value, body);
    chunks.value = [...chunks.value, u].sort((a, b) => a.seq - b.seq);
    ElMessage.success("已新增分片");
    newDlg.value = false;
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    newSaving.value = false;
  }
}

async function onChunkMenu(cmd: string, row: RagChunkAdminRow) {
  if (cmd === "edit") {
    openEditChunk(row);
    return;
  }
  if (cmd === "copy") {
    try {
      await navigator.clipboard.writeText(row.content || "");
      ElMessage.success("已复制到剪贴板");
    } catch {
      ElMessage.error("复制失败，请手动选择文本复制");
    }
    return;
  }
  if (cmd === "merge") {
    try {
      await ElMessageBox.confirm("将把「下一块」内容合并到当前块，并删除下一块。是否继续？", "合并分片", {
        type: "warning",
      });
      await ragApi.mergeRagKbChunkWithNext(kbId.value, docId.value, row.id);
      await reloadChunks();
      ElMessage.success("已合并");
    } catch (e: unknown) {
      if (e === "cancel" || e === "close") return;
      const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "合并失败";
      ElMessage.error(msg);
    }
    return;
  }
  if (cmd === "reindex") {
    try {
      await ragApi.patchRagKbChunk(kbId.value, docId.value, row.id, { content: row.content });
      await reloadChunks();
      ElMessage.success("已触发重新向量化");
    } catch (e: unknown) {
      const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "操作失败";
      ElMessage.error(msg);
    }
    return;
  }
  if (cmd === "delete") {
    try {
      await ElMessageBox.confirm("确定删除该分片？删除后不可恢复。", "删除分片", { type: "warning" });
      await ragApi.deleteRagKbChunk(kbId.value, docId.value, row.id);
      chunks.value = chunks.value.filter((x) => x.id !== row.id);
      ElMessage.success("已删除");
    } catch {
      /* cancel */
    }
  }
}

function openDocMeta() {
  if (!doc.value) return;
  docMetaCategoryId.value = doc.value.categoryId ?? undefined;
  docMetaScope.value = doc.value.applicableScope ?? "";
  docMetaDlg.value = true;
}

async function saveDocMeta() {
  if (!doc.value) return;
  docMetaSaving.value = true;
  try {
    const body =
      docMetaCategoryId.value == null
        ? { clearCategory: true as const, applicableScope: docMetaScope.value.trim() }
        : { categoryId: docMetaCategoryId.value, applicableScope: docMetaScope.value.trim() };
    const d = await ragApi.patchRagKbDocument(kbId.value, docId.value, body);
    doc.value = d;
    ElMessage.success("已保存");
    docMetaDlg.value = false;
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    docMetaSaving.value = false;
  }
}

watch(
  () => [route.params.kbId, route.params.docId],
  () => {
    void loadAll();
  },
);

onMounted(() => {
  void loadAll();
});
</script>

<style scoped>
.document-chunks-page {
  --dc-card-bg: var(--el-bg-color);
  --dc-card-parent-bg: var(--el-fill-color-lighter);
  --dc-card-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
  --dc-chunk-hl-bg: var(--el-color-warning-light-8);
  --dc-chunk-hl-fg: var(--el-text-color-primary);
}

.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.hdr {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dc-split {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  min-height: 0;
}

.dc-main {
  flex: 1 1 72%;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.dc-aside {
  flex: 0 0 280px;
  position: sticky;
  top: 12px;
}

.dc-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.dc-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  flex: 1 1 auto;
  min-width: 0;
}

.dc-toolbar-add {
  flex-shrink: 0;
}

.dc-view-mode {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.dc-view-mode-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.dc-search {
  flex: 1 1 280px;
  max-width: 420px;
}

.dc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.chunk-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--dc-card-bg);
  box-shadow: var(--dc-card-shadow);
  overflow: hidden;
  min-height: 200px;
}

.chunk-card--parent {
  background: var(--dc-card-parent-bg);
  border-style: dashed;
}

.chunk-role-tag {
  margin-right: 6px;
  vertical-align: middle;
}

.chunk-parent-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.chunk-card-hdr {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

.chunk-seq {
  flex: 1;
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.chunk-hit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.chunk-more {
  padding: 4px 8px !important;
}

.chunk-body {
  flex: 1;
  margin: 0;
  padding: 12px 14px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  min-height: 120px;
}

.chunk-body:hover {
  background: var(--el-fill-color-light);
}

.chunk-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-text.chunk-md {
  white-space: normal;
  max-height: min(42vh, 360px);
  overflow-x: auto;
  overflow-y: auto;
  text-align: left;
}

.dc-hint-alert {
  margin: 0 0 12px;
}

.chunk-text.chunk-md :deep(a) {
  color: var(--el-color-primary);
}

.chunk-text.chunk-md :deep(code) {
  padding: 0 4px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.chunk-text.chunk-md :deep(pre) {
  margin: 0 0 0.4em;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color);
  font-size: 12px;
  overflow-x: auto;
}

.chunk-text.chunk-md :deep(.md-code-block) {
  border-color: var(--el-border-color);
  background: var(--el-fill-color-lighter);
}

.chunk-text.chunk-md :deep(.md-code-toolbar) {
  background: var(--el-fill-color-light);
  border-bottom-color: var(--el-border-color-lighter);
}

.chunk-text.chunk-md :deep(blockquote) {
  margin: 0 0 0.4em;
  padding-left: 0.75em;
  border-left: 3px solid var(--el-border-color);
  color: var(--el-text-color-secondary);
}

.chunk-edit-tabs {
  margin-top: -4px;
}

.chunk-edit-preview {
  min-height: 280px;
  max-height: min(62vh, 520px);
  overflow: auto;
  padding: 4px 2px;
}

.chunk-edit-preview.chunk-md :deep(code),
.chunk-edit-preview.chunk-md :deep(pre),
.chunk-edit-preview.chunk-md :deep(.md-code-block) {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.chunk-edit-preview.chunk-md :deep(.md-code-block) {
  border-color: var(--el-border-color);
}

.chunk-edit-preview.chunk-md :deep(.md-code-toolbar) {
  background: var(--el-fill-color-light);
}

.chunk-hl {
  padding: 0 2px;
  background: var(--dc-chunk-hl-bg);
  color: var(--dc-chunk-hl-fg);
  border-radius: 2px;
}

.chunk-ft {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  border-top: 1px solid var(--el-border-color-lighter);
}

.dc-pager-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.aside-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: var(--dc-card-bg);
  box-shadow: var(--dc-card-shadow);
}

.aside-h {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.aside-dl {
  margin: 0 0 12px;
  font-size: 13px;
}

.aside-dl dt {
  margin-top: 8px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.aside-dl dd {
  margin: 2px 0 0;
}

.aside-muted {
  color: var(--el-text-color-secondary);
}

.aside-link {
  padding: 0 !important;
  height: auto !important;
}

.aside-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  line-height: 1.45;
}

.dc-upload-ph {
  margin-bottom: 8px;
  opacity: 0.55;
  pointer-events: none;
}

.dc-upload-ico {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 1024px) {
  .dc-split {
    flex-direction: column;
  }

  .dc-aside {
    flex: none;
    width: 100%;
    position: static;
  }
}
</style>

<style>
/* 深色：卡片勿用硬编码白底；投影与 Markdown 内嵌块跟随主题 */
html.dark .document-chunks-page {
  --dc-card-shadow: 0 1px 2px rgba(0, 0, 0, 0.35), 0 6px 18px rgba(0, 0, 0, 0.22);
  --dc-chunk-hl-bg: var(--el-color-warning-dark-2);
  --dc-chunk-hl-fg: var(--el-color-warning-light-9);
}

html.dark .document-chunks-page .chunk-card--parent {
  border-color: var(--el-border-color);
}

html.dark .document-chunks-page .chunk-text.chunk-md :deep(th),
html.dark .document-chunks-page .chunk-text.chunk-md :deep(td) {
  border-color: var(--el-border-color);
}

html.dark .document-chunks-page .chunk-edit-preview.chunk-md :deep(.md-code-block),
html.dark .document-chunks-page .chunk-text.chunk-md :deep(.md-code-block) {
  background: var(--el-fill-color-dark);
}
</style>
