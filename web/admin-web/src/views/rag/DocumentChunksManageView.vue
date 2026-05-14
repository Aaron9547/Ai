<template>
  <div class="page" v-loading="pageLoading">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">分片管理</span>
            <p class="sub">「{{ docTitle }}」— 按段落维护知识块；禁用后该分片不参与词法检索召回。</p>
          </div>
        </div>
      </template>

      <div class="dc-split">
      <section class="dc-main">
        <div class="dc-toolbar">
          <el-input
            v-model="chunkSearch"
            class="dc-search"
            clearable
            placeholder="在分片内容中搜索…"
            prefix-icon="Search"
          />
          <el-button type="primary" @click="openNewChunk">新增分段</el-button>
        </div>

        <el-empty
          v-if="!pageLoading && !filteredChunks.length"
          description="暂无分片，或没有匹配当前关键词的分片"
          :image-size="72"
        />

        <div v-else class="dc-grid">
          <article v-for="row in pagedChunks" :key="row.id" class="chunk-card">
            <header class="chunk-card-hdr">
              <span class="chunk-seq">片段 {{ row.seq + 1 }} / {{ chunks.length }}</span>
              <span class="chunk-hit" title="对话 RAG 召回命中该分片的累计次数">命中 {{ row.hitCount ?? 0 }}</span>
              <el-switch
                :model-value="isChunkEnabled(row)"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
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
              <p class="chunk-text">
                <template v-for="(seg, i) in highlightSegments(row.content)" :key="i">
                  <mark v-if="seg.hl" class="chunk-hl">{{ seg.t }}</mark>
                  <template v-else>{{ seg.t }}</template>
                </template>
              </p>
            </button>
            <footer class="chunk-ft">
              <span>{{ charCount(row) }} 字</span>
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

    <el-dialog v-model="editDlg" title="编辑分片" width="720px" destroy-on-close @closed="editingRow = null">
      <el-input v-model="editText" type="textarea" :rows="16" />
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
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
import { useRoute, useRouter } from "vue-router";
import * as ragApi from "../../api/ragAdmin";
import type {
  RagChunkAdminRow,
  RagDocumentAdminRow,
  RagDocumentCategoryAdminRow,
  RagKnowledgeBaseRow,
} from "../../types/admin";

const route = useRoute();
const router = useRouter();

const kbId = computed(() => Number.parseInt(String(route.params.kbId), 10));
const docId = computed(() => Number.parseInt(String(route.params.docId), 10));

const pageLoading = ref(true);
const kbRow = ref<RagKnowledgeBaseRow | null>(null);
const doc = ref<RagDocumentAdminRow | null>(null);
const chunks = ref<RagChunkAdminRow[]>([]);
const categories = ref<RagDocumentCategoryAdminRow[]>([]);

const chunkSearch = ref("");
const chunkPage = ref(1);
const chunkPageSize = 12;
const PAGER_MIN_TOTAL = 20;

const docTitle = computed(() => {
  const q = route.query.docTitle;
  if (typeof q === "string" && q.trim()) return q.trim();
  return doc.value?.title || "文档";
});

const filteredChunks = computed(() => {
  const kw = chunkSearch.value.trim().toLowerCase();
  const list = [...chunks.value].sort((a, b) => a.seq - b.seq);
  if (!kw) return list;
  return list.filter((c) => (c.content || "").toLowerCase().includes(kw));
});

const showPager = computed(() => filteredChunks.value.length >= PAGER_MIN_TOTAL);

const pagedChunks = computed(() => {
  if (!showPager.value) return filteredChunks.value;
  const start = (chunkPage.value - 1) * chunkPageSize;
  return filteredChunks.value.slice(start, start + chunkPageSize);
});

watch([chunkSearch, filteredChunks], () => {
  chunkPage.value = 1;
});

const editDlg = ref(false);
const editText = ref("");
const editSaving = ref(false);
const editingRow = ref<RagChunkAdminRow | null>(null);

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
    const [kbs, d, ch] = await Promise.all([
      ragApi.fetchRagKbs(),
      ragApi.fetchRagKbDocument(kbId.value, docId.value),
      ragApi.fetchRagKbChunks(kbId.value, docId.value),
    ]);
    kbRow.value = kbs.find((x) => x.id === kbId.value) ?? null;
    doc.value = d;
    chunks.value = ch;
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
    chunks.value = await ragApi.fetchRagKbChunks(kbId.value, docId.value);
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
  const t = newText.value.trim();
  if (!t) {
    ElMessage.warning("请输入分段内容");
    return;
  }
  newSaving.value = true;
  try {
    const u = await ragApi.createRagKbChunk(kbId.value, docId.value, t);
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
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
  background: #fff;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  min-height: 200px;
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

.chunk-hl {
  padding: 0 2px;
  background: #fef08a;
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
  background: #fff;
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
