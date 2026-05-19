<template>

  <el-dialog v-model="visible" :title="t('views.kbMatrix.webCrawlSitesTitle')" width="920px" destroy-on-close @open="onOpen">

    <p class="hint">{{ t("views.kbMatrix.webCrawlSitesHint") }}</p>

    <div class="toolbar">

      <el-button type="primary" @click="openCreate">{{ t("views.kbMatrix.webCrawlSitesNew") }}</el-button>

      <el-button :loading="loading" @click="load">{{ t("views.kbMatrix.refresh") }}</el-button>

    </div>

    <el-table v-loading="loading" :data="rows" border stripe :empty-text="t('views.kbMatrix.webCrawlSitesEmpty')">

      <el-table-column prop="name" :label="t('views.kbMatrix.webCrawlSitesColName')" min-width="100" />

      <el-table-column prop="baseUrl" :label="t('views.kbMatrix.webCrawlSitesColUrl')" min-width="180" show-overflow-tooltip />

      <el-table-column :label="t('views.kbMatrix.webCrawlSitesColPreset')" width="100">

        <template #default="{ row }">{{ row.schedulePresetLabel }}</template>

      </el-table-column>

      <el-table-column prop="runAtTime" :label="t('views.kbMatrix.webCrawlSitesColRunAt')" width="88" />

      <el-table-column :label="t('views.kbMatrix.webCrawlSitesColEnabled')" width="72" align="center">

        <template #default="{ row }">

          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? t("common.yes") : t("common.no") }}</el-tag>

        </template>

      </el-table-column>

      <el-table-column :label="t('views.kbMatrix.webCrawlSitesColLast')" width="160">

        <template #default="{ row }">{{ formatTime(row.lastCrawlAt) }}</template>

      </el-table-column>

      <el-table-column :label="t('views.kbMatrix.colActions')" width="220" fixed="right" align="center">

        <template #default="{ row }">

          <el-button link type="primary" size="small" @click="previewSite(row)">{{ t("views.kbMatrix.chunkPreviewBtn") }}</el-button>

          <el-button link type="primary" size="small" @click="runSite(row)">{{ t("views.kbMatrix.webCrawlSitesRun") }}</el-button>

          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.kbMatrix.edit") }}</el-button>

          <el-button link type="danger" size="small" @click="remove(row)">{{ t("views.kbMatrix.delete") }}</el-button>

        </template>

      </el-table-column>

    </el-table>



    <el-dialog v-model="formDlg" :title="editId ? t('views.kbMatrix.webCrawlSitesEdit') : t('views.kbMatrix.webCrawlSitesNew')" width="560px" append-to-body destroy-on-close>

      <el-form label-width="120px">

        <el-form-item :label="t('views.kbMatrix.webCrawlSitesColName')" required>

          <el-input v-model="form.name" maxlength="128" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.webCrawlSitesColUrl')" required>

          <el-input v-model="form.baseUrl" type="url" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.labelSyncMode')" required>

          <el-select v-model="form.syncMode" style="width: 100%">

            <el-option v-for="o in meta.syncModes" :key="o.code" :label="o.label" :value="o.code" />

          </el-select>

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.webCrawlSitesColPreset')" required>

          <el-select v-model="form.schedulePreset" style="width: 100%">

            <el-option v-for="o in meta.schedulePresets" :key="o.code" :label="o.label" :value="o.code" />

          </el-select>

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.webCrawlSitesColRunAt')">

          <el-time-picker v-model="form.runAtTime" format="HH:mm" value-format="HH:mm" style="width: 100%" clearable />

        </el-form-item>

        <el-form-item :label="t('views.ingest.labelChunkOverride')">

          <el-select v-model="form.chunkStrategy" clearable style="width: 100%">

            <el-option :label="t('views.ingest.chunk2')" :value="2" />

            <el-option :label="t('views.ingest.chunk1')" :value="1" />

            <el-option :label="t('views.ingest.chunk3')" :value="3" />

          </el-select>

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.extractEngine')">

          <el-select v-model="form.extractor" style="width: 100%">

            <el-option v-for="o in meta.contentExtractors ?? []" :key="o.code" :label="o.label" :value="o.code" />

          </el-select>

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.extractContentSelector')">

          <el-input v-model="form.contentSelector" :placeholder="t('views.kbMatrix.extractContentSelectorPh')" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.extractTitleSelector')">

          <el-input v-model="form.titleSelector" :placeholder="t('views.kbMatrix.extractTitleSelectorPh')" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.extractExcludeSelectors')">

          <el-input v-model="form.excludeText" type="textarea" :rows="3" :placeholder="t('views.kbMatrix.extractExcludeSelectorsPh')" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.labelMaxDepth')">

          <el-input-number v-model="form.maxDepth" :min="1" :max="8" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.labelFilterCrawled')">

          <el-switch v-model="form.filterCrawled" />

        </el-form-item>

        <el-form-item :label="t('views.kbMatrix.webCrawlSitesColEnabled')">

          <el-switch v-model="form.enabled" />

        </el-form-item>

      </el-form>

      <template #footer>

        <el-button @click="formDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>

        <el-button type="primary" :loading="saving" @click="save">{{ t("views.kbMatrix.formSave") }}</el-button>

      </template>

    </el-dialog>



    <KbChunkPreviewDialog ref="chunkPreviewRef" v-model="chunkPreviewDlgOpen" :kb-id="kbId" />

  </el-dialog>

</template>



<script setup lang="ts">

import { ElMessage, ElMessageBox } from "element-plus";

import { computed, reactive, ref } from "vue";

import { useI18n } from "vue-i18n";

import * as ragApi from "@/api/ragAdmin";

import type { RagWebCrawlExtractConfig, RagWebCrawlSiteMeta, RagWebCrawlSiteRow } from "@/api/ragAdmin";

import KbChunkPreviewDialog from "./KbChunkPreviewDialog.vue";



const props = defineProps<{ modelValue: boolean; kbId: number }>();

const emit = defineEmits<{ "update:modelValue": [boolean] }>();



const { t } = useI18n();

const visible = computed({

  get: () => props.modelValue,

  set: (v) => emit("update:modelValue", v),

});



const loading = ref(false);

const saving = ref(false);

const rows = ref<RagWebCrawlSiteRow[]>([]);

const meta = ref<RagWebCrawlSiteMeta>({ syncModes: [], schedulePresets: [], contentExtractors: [] });

const formDlg = ref(false);

const editId = ref<number | null>(null);

const chunkPreviewDlgOpen = ref(false);

const chunkPreviewRef = ref<InstanceType<typeof KbChunkPreviewDialog> | null>(null);



const form = reactive({

  name: "",

  baseUrl: "",

  syncMode: "FULL",

  schedulePreset: "DAILY",

  runAtTime: undefined as string | undefined,

  maxDepth: 3,

  filterCrawled: true,

  enabled: true,

  chunkStrategy: 2 as number | undefined,

  extractor: "jsoup",

  contentSelector: "",

  titleSelector: "",

  excludeText: "",

});



function formatTime(raw?: string | null) {

  if (!raw) return "—";

  return raw.replace("T", " ").slice(0, 19);

}



function buildExtractConfig(): RagWebCrawlExtractConfig {

  const excludeSelectors = form.excludeText

    .split("\n")

    .map((s) => s.trim())

    .filter(Boolean);

  return {

    extractor: form.extractor || "jsoup",

    contentSelector: form.contentSelector.trim() || undefined,

    titleSelector: form.titleSelector.trim() || undefined,

    excludeSelectors: excludeSelectors.length ? excludeSelectors : undefined,

  };

}



function applyExtractToForm(cfg?: RagWebCrawlExtractConfig | null) {

  form.extractor = cfg?.extractor || "jsoup";

  form.contentSelector = cfg?.contentSelector || "";

  form.titleSelector = cfg?.titleSelector || "";

  form.excludeText = (cfg?.excludeSelectors || []).join("\n");

}



async function loadMeta() {

  meta.value = await ragApi.fetchWebCrawlSiteMeta();

  if (!form.syncMode && meta.value.syncModes[0]) form.syncMode = meta.value.syncModes[0].code;

}



async function load() {

  loading.value = true;

  try {

    rows.value = await ragApi.fetchWebCrawlSites(props.kbId);

  } catch {

    ElMessage.error(t("views.kbMatrix.webCrawlSitesLoadFailed"));

  } finally {

    loading.value = false;

  }

}



function onOpen() {

  void loadMeta().then(() => load());

}



function resetForm() {

  editId.value = null;

  form.name = "";

  form.baseUrl = "";

  form.syncMode = meta.value.syncModes[0]?.code ?? "FULL";

  form.schedulePreset = "DAILY";

  form.runAtTime = undefined;

  form.maxDepth = 3;

  form.filterCrawled = true;

  form.enabled = true;

  form.chunkStrategy = 2;

  applyExtractToForm(null);

}



function openCreate() {

  resetForm();

  formDlg.value = true;

}



function openEdit(row: RagWebCrawlSiteRow) {

  editId.value = row.id;

  form.name = row.name;

  form.baseUrl = row.baseUrl;

  form.syncMode = row.syncMode ?? "FULL";

  form.schedulePreset = row.schedulePreset;

  form.runAtTime = row.runAtTime ?? undefined;

  form.maxDepth = row.maxDepth ?? 3;

  form.filterCrawled = row.filterCrawled;

  form.enabled = row.enabled;

  form.chunkStrategy = row.chunkStrategy ?? 2;

  applyExtractToForm(row.extractConfig);

  formDlg.value = true;

}



async function save() {

  if (!form.name.trim() || !form.baseUrl.trim()) {

    ElMessage.warning(t("views.kbMatrix.webCrawlSitesFormRequired"));

    return;

  }

  saving.value = true;

  try {

    const body = {

      name: form.name.trim(),

      baseUrl: form.baseUrl.trim(),

      syncMode: form.syncMode,

      schedulePreset: form.schedulePreset,

      runAtTime: form.runAtTime,

      maxDepth: form.maxDepth,

      filterCrawled: form.filterCrawled,

      enabled: form.enabled,

      chunkStrategy: form.chunkStrategy,

      extractConfig: buildExtractConfig(),

    };

    if (editId.value != null) {

      await ragApi.updateWebCrawlSite(props.kbId, editId.value, body);

    } else {

      await ragApi.createWebCrawlSite(props.kbId, body);

    }

    formDlg.value = false;

    ElMessage.success(t("views.kbMatrix.webCrawlSitesSaved"));

    await load();

  } catch {

    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));

  } finally {

    saving.value = false;

  }

}



async function previewSite(row: RagWebCrawlSiteRow) {

  await chunkPreviewRef.value?.run({

    baseUrl: row.baseUrl,

    maxDepth: row.maxDepth ?? 3,

    chunkStrategy: row.chunkStrategy ?? 2,

    siteId: row.id,

  });

}



async function runSite(row: RagWebCrawlSiteRow) {

  try {

    await ragApi.runWebCrawlSiteNow(props.kbId, row.id);

    ElMessage.success(t("views.kbMatrix.webCrawlSitesRunStarted"));

    await load();

  } catch {

    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));

  }

}



async function remove(row: RagWebCrawlSiteRow) {

  try {

    await ElMessageBox.confirm(t("views.kbMatrix.webCrawlSitesDeleteConfirm", { name: row.name }), { type: "warning" });

    await ragApi.deleteWebCrawlSite(props.kbId, row.id);

    ElMessage.success(t("views.kbMatrix.webCrawlSitesDeleted"));

    await load();

  } catch {

    /* cancel */

  }

}

</script>



<style scoped>

.hint {

  margin: 0 0 12px;

  font-size: 13px;

  color: var(--el-text-color-secondary);

  line-height: 1.5;

}

.toolbar {

  display: flex;

  gap: 8px;

  margin-bottom: 12px;

}

</style>


