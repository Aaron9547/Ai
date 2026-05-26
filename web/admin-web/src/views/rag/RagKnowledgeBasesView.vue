<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div class="hdr-intro">
            <div class="hdr-title-row">
              <span class="title">{{ t("views.ragBases.title") }}</span>
              <div class="kb-picker-wrap" v-loading="loadingKbs">
                <el-select
                  v-model="selectedKbId"
                  class="kb-picker"
                  filterable
                  :placeholder="t('views.ragBases.kbPh')"
                  :disabled="!rows.length"
                >
                  <el-option v-for="r in rows" :key="r.id" :label="kbOptionLabel(r)" :value="r.id">
                    <span class="kb-opt-name">{{ r.name }}</span>
                  </el-option>
                </el-select>
              </div>
            </div>
            <p class="sub">
              {{ t("views.ragBases.sub") }}
            </p>
          </div>
          <div class="hdr-actions">
            <el-button plain :loading="loadingKbs" @click="loadKbs">{{ t("views.ragBases.refreshList") }}</el-button>
            <el-button type="primary" @click="openCreate">{{ t("views.ragBases.newKb") }}</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="capabilitiesLoaded && !vectorStoreMilvus"
        type="warning"
        show-icon
        :closable="false"
        class="rag-cap-alert"
        :title="t('views.ragBases.vecWarnTitle')"
        :description="t('views.ragBases.vecWarnDesc')"
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
                  :active-text="t('views.ragBases.chatRagOn')"
                  :inactive-text="t('views.ragBases.chatRagOff')"
                  @change="(on: boolean) => wrapChatRagToggle(selectedKb, on)"
                />
                <el-button size="small" plain @click="openEdit(selectedKb)">{{ t("views.ragBases.rename") }}</el-button>
                <el-button size="small" plain @click="openTasksDlg(selectedKb)">{{ t("views.ragBases.asyncTasks") }}</el-button>
                <el-button size="small" type="primary" plain @click="openAdvancedDlg(selectedKb)">{{ t("views.ragBases.advanced") }}</el-button>
                <el-button size="small" type="danger" plain @click="remove(selectedKb)">{{ t("views.ragBases.remove") }}</el-button>
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
            :description="t('views.ragBases.emptyNoKb')"
            :image-size="80"
          />
          <el-empty v-else :description="t('views.ragBases.emptyPick')" :image-size="72" />
        </main>
      </div>
    </el-card>

    <KbAdvancedSettingsDialog
      v-model="advancedDlg"
      :kb-id="dlgKbId"
      @saved="onAdvancedSaved"
    />
    <KbAsyncTasksDialog v-model="tasksDlg" :kb-id="dlgKbId" :kb-name="dlgKbName" />

    <el-dialog
      v-model="dlg"
      :title="editId ? t('views.ragBases.dlgRenameTitle') : t('views.ragBases.dlgNewTitle')"
      width="440px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form :model="form" label-width="88px">
        <el-form-item :label="t('views.ragBases.nameLabel')" required>
          <el-input v-model="form.name" :placeholder="t('views.ragBases.namePh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ t("views.ragBases.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("views.ragBases.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import * as ragApi from "../../api/ragAdmin";
import type { RagKnowledgeBaseRow } from "../../types/admin";
import KbAdvancedSettingsDialog from "./components/KbAdvancedSettingsDialog.vue";
import KbAsyncTasksDialog from "./components/KbAsyncTasksDialog.vue";
import KbDocumentMatrixPanel from "./components/KbDocumentMatrixPanel.vue";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

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
    ElMessage.warning(t("views.ragBases.milvusWarn"));
    return;
  }
  if (on && row.assignedEmbeddingModelId == null) {
    ElMessage.warning(t("views.ragBases.embedWarn"));
    return;
  }
  patchingChatRagId.value = row.id;
  try {
    await ragApi.patchRagKbSettings(row.id, { chatRetrievalEnabled: on });
    ElMessage.success(on ? t("views.ragBases.chatRagOnOk") : t("views.ragBases.chatRagOffOk"));
    await loadKbs();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.ragBases.updateFailed");
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
    ElMessage.warning(t("views.ragBases.nameRequired"));
    return;
  }
  saving.value = true;
  try {
    if (editId.value == null) {
      const created = await ragApi.createRagKb(form.name.trim());
      ElMessage.success(t("views.ragBases.created"));
      dlg.value = false;
      await loadKbs();
      selectKb(created);
    } else {
      const updated = await ragApi.updateRagKb(editId.value, form.name.trim());
      ElMessage.success(t("views.ragBases.saved"));
      dlg.value = false;
      await loadKbs();
      if (selectedKb.value?.id === updated.id) {
        selectedKb.value = rows.value.find((x) => x.id === updated.id) ?? selectedKb.value;
      }
    }
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.ragBases.saveFailed");
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

async function remove(row: RagKnowledgeBaseRow) {
  try {
    await ElMessageBox.confirm(t("views.ragBases.deleteConfirm", { name: row.name }), t("common.confirmTitle"), {
      type: "warning",
    });
    await ragApi.deleteRagKb(row.id);
    ElMessage.success(t("views.ragBases.deleted"));
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
  border: 1px solid var(--el-border-color);
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
  color: var(--el-text-color-primary);
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
  background: linear-gradient(180deg, var(--el-fill-color-light) 0%, var(--el-fill-color-darker) 100%);
  transition: box-shadow 0.15s ease, background 0.15s ease;
}

.kb-picker :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.kb-picker :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset, 0 0 0 3px var(--el-color-primary-light-8);
  background: var(--el-bg-color);
}

.kb-opt-name {
  display: block;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
}

.kc-hub-body {
  display: block;
  min-width: 0;
}

.kc-hub-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
  padding: 16px 18px 20px;
  box-shadow: var(--el-box-shadow-lighter);
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
  border-bottom: 1px dashed var(--el-border-color-lighter);
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
  color: var(--el-text-color-primary);
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

@media (max-width: 720px) {
  .kb-picker {
    width: 100%;
    min-width: 0;
  }
}
</style>
