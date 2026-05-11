<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div class="hdr-intro">
            <div class="hdr-title-row">
              <span class="title">知识库</span>
              <div class="kb-picker-wrap" v-loading="loadingKbs">
                <el-select
                  v-model="selectedKbId"
                  class="kb-picker"
                  filterable
                  placeholder="选择要管理的知识库"
                  :disabled="!rows.length"
                >
                  <el-option v-for="r in rows" :key="r.id" :label="kbOptionLabel(r)" :value="r.id">
                    <span class="kb-opt-name">{{ r.name }}</span>
                  </el-option>
                </el-select>
              </div>
            </div>
            <p class="sub">
              在标题旁切换知识库；下方管理文档、分类与入库。模型与默认分片策略、本库异步任务请在当前知识库标题栏中打开。
            </p>
          </div>
          <div class="hdr-actions">
            <el-button plain :loading="loadingKbs" @click="loadKbs">刷新列表</el-button>
            <el-button type="primary" @click="openCreate">新建知识库</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="capabilitiesLoaded && !vectorStoreMilvus"
        type="warning"
        show-icon
        :closable="false"
        class="rag-cap-alert"
        title="向量库未启用"
        description="当前为占位模式（未连接 Milvus）：文档上传、网页/文件入库与索引入库已暂停，对话侧也不会使用知识库检索。列表与已有文档仍可查看。请在部署中设置 ai.providers.vector-store=milvus 并保证 Milvus 可用后重试写入。"
      />

      <div class="kc-hub-body">
        <main class="kc-hub-main">
          <template v-if="selectedKb">
            <div class="main-hdr">
              <div class="main-hdr-left">
                <h2 class="main-title">{{ selectedKb.name }}</h2>
              </div>
              <div class="main-hdr-right">
                <el-switch
                  class="chat-rag-switch"
                  :model-value="selectedKb.chatRetrievalEnabled !== 'OFF'"
                  :loading="patchingChatRagId === selectedKb.id"
                  inline-prompt
                  active-text="对话检索开"
                  inactive-text="对话检索关"
                  @change="(on: boolean) => wrapChatRagToggle(selectedKb, on)"
                />
                <el-button size="small" plain @click="openEdit(selectedKb)">重命名</el-button>
                <el-button size="small" plain @click="openTasksDlg(selectedKb)">异步任务</el-button>
                <el-button size="small" type="primary" plain @click="openAdvancedDlg(selectedKb)">高级设置</el-button>
                <el-button size="small" type="danger" plain @click="remove(selectedKb)">删除</el-button>
              </div>
            </div>
            <KbDocumentMatrixPanel
              :key="selectedKb.id"
              :kb-id="selectedKb.id"
              :vector-milvus-enabled="vectorStoreMilvus"
            />
          </template>
          <el-empty
            v-else-if="!loadingKbs && !rows.length"
            description="暂无知识库，请先新建"
            :image-size="80"
          />
          <el-empty v-else description="请选择知识库" :image-size="72" />
        </main>
      </div>
    </el-card>

    <KbAdvancedSettingsDialog
      v-model="advancedDlg"
      :kb-id="dlgKbId"
      @saved="onAdvancedSaved"
    />
    <KbAsyncTasksDialog v-model="tasksDlg" :kb-id="dlgKbId" :kb-name="dlgKbName" />

    <el-dialog v-model="dlg" :title="editId ? '重命名知识库' : '新建知识库'" width="440px" destroy-on-close @closed="resetForm">
      <el-form :model="form" label-width="88px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="知识库名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import * as ragApi from "../../api/ragAdmin";
import type { RagKnowledgeBaseRow } from "../../types/admin";
import KbAdvancedSettingsDialog from "./components/KbAdvancedSettingsDialog.vue";
import KbAsyncTasksDialog from "./components/KbAsyncTasksDialog.vue";
import KbDocumentMatrixPanel from "./components/KbDocumentMatrixPanel.vue";

const route = useRoute();
const router = useRouter();

const loadingKbs = ref(false);
const capabilitiesLoaded = ref(false);
const vectorStoreMilvus = ref(true);
const patchingChatRagId = ref<number | null>(null);
const saving = ref(false);
const rows = ref<RagKnowledgeBaseRow[]>([]);
const selectedKb = ref<RagKnowledgeBaseRow | null>(null);

const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive({ name: "" });

const advancedDlg = ref(false);
const tasksDlg = ref(false);
const dlgKbId = ref(0);
const dlgKbName = ref("");

const kbIdFromQuery = computed(() => {
  const raw = route.query.kbId;
  if (raw == null || Array.isArray(raw)) return null;
  const n = Number.parseInt(String(raw), 10);
  return Number.isFinite(n) ? n : null;
});

const selectedKbId = computed({
  get: (): number | undefined => selectedKb.value?.id,
  set: (id: number | undefined) => {
    if (id == null) return;
    const row = rows.value.find((x) => x.id === id);
    if (row) selectKb(row);
  },
});

function kbOptionLabel(r: RagKnowledgeBaseRow): string {
  return r.name;
}

function openAdvancedDlg(row: RagKnowledgeBaseRow) {
  dlgKbId.value = row.id;
  dlgKbName.value = row.name;
  advancedDlg.value = true;
}

function openTasksDlg(row: RagKnowledgeBaseRow) {
  dlgKbId.value = row.id;
  dlgKbName.value = row.name;
  tasksDlg.value = true;
}

async function onAdvancedSaved() {
  await loadKbs();
}

function selectKb(row: RagKnowledgeBaseRow) {
  selectedKb.value = row;
  void router.replace({ query: { ...route.query, kbId: String(row.id) } });
}

function wrapChatRagToggle(row: RagKnowledgeBaseRow | null, on: boolean) {
  if (!row) return;
  void toggleKbChatRag(row, on);
}

async function toggleKbChatRag(row: RagKnowledgeBaseRow, on: boolean) {
  if (on && !vectorStoreMilvus.value) {
    ElMessage.warning("未启用 Milvus 时无法打开对话检索，请先配置向量库。");
    return;
  }
  if (on && row.assignedEmbeddingModelId == null) {
    ElMessage.warning("请先在「高级设置」中为该知识库绑定「向量模型」，再开启对话检索。");
    return;
  }
  patchingChatRagId.value = row.id;
  try {
    await ragApi.patchRagKbSettings(row.id, { chatRetrievalEnabled: on });
    ElMessage.success(on ? "已纳入对话检索" : "已暂停参与对话检索");
    await loadKbs();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "更新失败";
    ElMessage.error(msg);
  } finally {
    patchingChatRagId.value = null;
  }
}

async function loadKbs() {
  loadingKbs.value = true;
  try {
    const cap = await ragApi.fetchRagCapabilities();
    vectorStoreMilvus.value = cap.vectorStoreMilvus;
    capabilitiesLoaded.value = true;
    rows.value = await ragApi.fetchRagKbs();
    const qid = kbIdFromQuery.value;
    if (qid != null) {
      const hit = rows.value.find((x) => x.id === qid);
      if (hit) {
        selectedKb.value = hit;
        return;
      }
    }
    if (selectedKb.value && rows.value.some((x) => x.id === selectedKb.value!.id)) {
      selectedKb.value = rows.value.find((x) => x.id === selectedKb.value!.id) ?? null;
      return;
    }
    selectedKb.value = rows.value[0] ?? null;
    if (selectedKb.value) {
      void router.replace({ query: { ...route.query, kbId: String(selectedKb.value.id) } });
    }
  } finally {
    loadingKbs.value = false;
  }
}

watch(kbIdFromQuery, (id) => {
  if (id == null || !rows.value.length) return;
  const hit = rows.value.find((x) => x.id === id);
  if (hit) selectedKb.value = hit;
});

function openCreate() {
  editId.value = null;
  form.name = "";
  dlg.value = true;
}

function openEdit(row: RagKnowledgeBaseRow) {
  editId.value = row.id;
  form.name = row.name;
  dlg.value = true;
}

function resetForm() {
  editId.value = null;
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  saving.value = true;
  try {
    if (editId.value == null) {
      const created = await ragApi.createRagKb(form.name.trim());
      ElMessage.success("已创建");
      dlg.value = false;
      await loadKbs();
      selectKb(created);
    } else {
      const updated = await ragApi.updateRagKb(editId.value, form.name.trim());
      ElMessage.success("已保存");
      dlg.value = false;
      await loadKbs();
      if (selectedKb.value?.id === updated.id) {
        selectedKb.value = rows.value.find((x) => x.id === updated.id) ?? selectedKb.value;
      }
    }
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

async function remove(row: RagKnowledgeBaseRow) {
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${row.name}」？若已关联文档将无法删除。`,
      "删除确认",
      { type: "warning" },
    );
    await ragApi.deleteRagKb(row.id);
    ElMessage.success("已删除");
    if (selectedKb.value?.id === row.id) {
      selectedKb.value = null;
    }
    await loadKbs();
    if (!selectedKb.value && rows.value.length) {
      selectKb(rows.value[0]);
    } else if (!rows.value.length) {
      void router.replace({ query: {} });
    }
  } catch {
    /* cancel or 409 */
  }
}

onMounted(() => {
  void loadKbs();
});
</script>

<style scoped>
.rag-cap-alert {
  margin: 0 0 12px;
}

.panel {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.hdr {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hdr-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.title {
  font-weight: 600;
  font-size: 15px;
  color: #0f172a;
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.hdr-intro {
  min-width: 0;
  flex: 1 1 280px;
}

.hdr-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}

.kb-picker-wrap {
  min-width: 0;
}

.kb-picker {
  width: min(100%, 320px);
  min-width: 200px;
}

.kb-picker :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background: linear-gradient(180deg, #fafbfc 0%, #f4f6f8 100%);
  transition: box-shadow 0.15s ease, background 0.15s ease;
}

.kb-picker :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.kb-picker :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #94a3b8 inset, 0 0 0 3px rgba(148, 163, 184, 0.25);
  background: #fff;
}

.kb-opt-name {
  display: block;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kc-hub-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.kc-hub-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  padding: 16px 18px 20px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.04);
}

.main-hdr {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
}

.main-hdr-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.main-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.main-hdr-right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chat-rag-switch {
  margin-right: 4px;
}

.kc-hub-main :deep(.kb-dmx-root) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.kc-hub-main :deep(.docs-matrix) {
  flex: 1;
  min-height: 0;
  align-items: stretch;
  overflow: hidden;
}

.kc-hub-main :deep(.docs-main) {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

@media (max-width: 720px) {
  .kb-picker {
    width: 100%;
    min-width: 0;
  }
}
</style>
