<template>
  <div class="tab-panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">{{ t("views.llmKindTab.new", { label: tab.label }) }}</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.llmKindTab.empty')">
      <el-table-column
        v-for="col in tab.listColumns"
        :key="col.prop"
        :label="col.label"
        :min-width="colMinWidth(col)"
        :width="colWidth(col)"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <template v-if="col.format === 'usage'">
            <span class="mono">{{ row.tokensUsed ?? 0 }}</span>
            <span class="quota-sep">/</span>
            <span class="mono">{{ row.tokenQuotaTotal == null ? "∞" : row.tokenQuotaTotal }}</span>
          </template>
          <template v-else-if="col.format === 'enum' && col.optionsKey">
            {{ enumCellLabel(col.optionsKey, row[col.prop as keyof typeof row]) }}
          </template>
          <template v-else-if="col.format === 'boolTag'">
            <el-tag :type="boolTagType(col.prop, row[col.prop as keyof typeof row] as boolean)" size="small">
              {{ boolTagText(col.prop, row[col.prop as keyof typeof row] as boolean) }}
            </el-tag>
          </template>
          <template v-else-if="col.format === 'boolSwitch'">
            <el-switch
              v-if="col.prop === 'enabled'"
              :model-value="row.enabled"
              :loading="togglingEnabledId === row.id"
              @update:model-value="(v: boolean) => toggleRowEnabled(row, v)"
            />
            <el-switch v-else :model-value="row[col.prop as keyof typeof row] as boolean" disabled />
          </template>
          <template v-else>
            {{ row[col.prop as keyof typeof row] }}
          </template>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.llmKindTab.colActions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.llmKindTab.edit") }}</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">{{ t("views.llmKindTab.delete") }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dlg"
      class="llm-model-dialog"
      width="600px"
      destroy-on-close
      align-center
      header-class="llm-model-dialog-header"
      body-class="llm-model-dialog-body"
      @closed="onDlgClosed"
    >
      <template #header>
        <div class="dlg-hdr">
          <h2 class="dlg-hdr-title">{{ editId ? t("views.llmKindTab.dlgHeaderEdit") : t("views.llmKindTab.dlgHeaderNew") }}</h2>
          <p class="dlg-hdr-kind">{{ tab.label }}</p>
        </div>
      </template>
      <el-scrollbar class="dlg-scroll" max-height="min(432px, 58vh)">
        <el-form :model="form" label-position="top" size="default" class="form">
          <LlmModelFormFields
            :fields="tab.formFields"
            :form="form"
            :option-lists="optionLists"
            :is-edit="!!editId"
            :model-kind="tab.kind"
          />
        </el-form>
      </el-scrollbar>
      <template #footer>
        <el-button @click="dlg = false">{{ t("views.llmKindTab.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("views.llmKindTab.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as modelsApi from "../../../api/models";
import type {
  CreateLlmModelBody,
  LlmModelAdminMetaResponse,
  LlmModelAdminView,
  ModelKindTabMeta,
} from "../../../api/models";
import { apiRequestErrorMessage } from "../../../utils/apiRequestErrorMessage";
import LlmModelFormFields from "./LlmModelFormFields.vue";

const { t } = useI18n();

const props = defineProps<{
  tab: ModelKindTabMeta;
  optionLists: LlmModelAdminMetaResponse["optionLists"];
}>();

const loading = ref(false);
const saving = ref(false);
const rows = ref<LlmModelAdminView[]>([]);
const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive<Record<string, unknown>>({});
/** 列表行「启用」开关提交中，避免连点 */
const togglingEnabledId = ref<number | null>(null);

async function loadRows() {
  loading.value = true;
  try {
    rows.value = await modelsApi.listLlmModels({ modelKind: props.tab.kind });
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.tab.kind,
  () => {
    void loadRows();
  },
  { immediate: true },
);

function colMinWidth(col: { prop: string; format: string }): number | undefined {
  if (col.format === "usage") return 160;
  if (col.prop === "openaiBaseUrl") return 200;
  return undefined;
}

function colWidth(col: { prop: string; format: string }): number | undefined {
  if (col.format === "boolTag" || col.format === "boolSwitch") return 100;
  if (col.prop === "alias") return 140;
  if (col.prop === "openaiModelId") return 160;
  if (col.prop === "integrationBackend") return 220;
  if (col.prop === "localDeploy") return 108;
  return undefined;
}

function enumCellLabel(optionsKey: string, code: unknown): string {
  if (code == null || code === "") return t("common.dash");
  const opts = props.optionLists[optionsKey] || [];
  const o = opts.find((x) => x.code === code);
  return o ? o.label : String(code);
}

function boolTagText(prop: string, val: boolean): string {
  if (prop === "apiKeyConfigured") return val ? t("views.llmKindTab.boolApiKeyOk") : t("views.llmKindTab.boolApiKeyNo");
  if (prop === "allowAnonymous") return val ? t("views.llmKindTab.boolAllowYes") : t("views.llmKindTab.boolAllowNo");
  if (prop === "supportsThinking") return val ? t("views.llmKindTab.boolThinkingYes") : t("views.llmKindTab.boolThinkingNo");
  if (prop === "localDeploy") return val ? t("views.llmKindTab.boolLocalFeign") : t("views.llmKindTab.boolLocalDirect");
  return val ? t("views.llmKindTab.boolYes") : t("views.llmKindTab.boolNo");
}

function boolTagType(prop: string, val: boolean): "success" | "warning" | "info" {
  if (prop === "apiKeyConfigured") return val ? "success" : "warning";
  if (prop === "allowAnonymous") return val ? "success" : "info";
  return val ? "success" : "info";
}

function initFormCreate() {
  for (const k of Object.keys(form)) {
    delete form[k];
  }
  for (const f of props.tab.formFields) {
    if (f.key === "tokenQuotaUnlimited") form[f.key] = true;
    else if (f.key === "tokenQuotaTotal") form[f.key] = 1_000_000;
    else if (f.key === "allowAnonymous") form[f.key] = false;
    else if (f.key === "enabled") form[f.key] = true;
    else if (f.key === "sortOrder") form[f.key] = 0;
    else if (f.key === "maxAttachments") form[f.key] = 10;
    else if (f.key === "supportsThinking") form[f.key] = false;
    else if (f.key === "integrationBackend")
      form[f.key] = props.tab.kind === "WEB_SEARCH" ? "VOLCENGINE_ARK_BOT" : "OPENAI_COMPATIBLE";
    else if (f.key === "clearApiKey") form[f.key] = false;
    else if (f.key === "localDeploy") form[f.key] = false;
    else form[f.key] = "";
  }
}

function initFormEdit(row: LlmModelAdminView) {
  for (const k of Object.keys(form)) {
    delete form[k];
  }
  form.alias = row.alias;
  form.displayName = row.displayName;
  form.openaiBaseUrl = row.openaiBaseUrl;
  form.openaiModelId = row.openaiModelId;
  if (props.tab.kind === "VECTOR" || props.tab.kind === "WEB_SEARCH") {
    form.integrationBackend =
      row.integrationBackend ??
      (props.tab.kind === "WEB_SEARCH" ? "VOLCENGINE_ARK_BOT" : "OPENAI_COMPATIBLE");
  }
  form.apiKey = "";
  form.allowAnonymous = row.allowAnonymous;
  form.maxAttachments = row.maxAttachments;
  form.supportsThinking = row.supportsThinking;
  form.enabled = row.enabled;
  form.sortOrder = row.sortOrder;
  form.tokenQuotaUnlimited = row.tokenQuotaTotal == null;
  form.tokenQuotaTotal = row.tokenQuotaTotal ?? 1_000_000;
  form.clearApiKey = false;
  form.localDeploy = !!row.localDeploy;
}

function openCreate() {
  editId.value = null;
  initFormCreate();
  dlg.value = true;
}

function openEdit(row: LlmModelAdminView) {
  editId.value = row.id;
  initFormEdit(row);
  dlg.value = true;
}

function onDlgClosed() {
  editId.value = null;
}

function validateRequired(): boolean {
  for (const f of props.tab.formFields) {
    if (!f.required) continue;
    if (f.key === "clearApiKey") continue;
    // 编辑：密钥留空表示不轮换，不要求重新填写
    if (f.key === "apiKey" && editId.value) continue;
    // 新建向量模型：允许免密
    if (f.key === "apiKey" && props.tab.kind === "VECTOR" && !editId.value) continue;
    const v = form[f.key];
    if (v === undefined || v === null || (typeof v === "string" && !v.trim())) {
      ElMessage.warning(t("views.llmKindTab.fillField", { label: f.label }));
      return false;
    }
  }
  if (!editId.value && !form.tokenQuotaUnlimited && (!form.tokenQuotaTotal || Number(form.tokenQuotaTotal) < 1)) {
    ElMessage.warning(t("views.llmKindTab.tokenQuotaWarning"));
    return false;
  }
  return true;
}

async function save() {
  if (!validateRequired()) return;
  saving.value = true;
  try {
    if (editId.value) {
      await modelsApi.updateLlmModel(editId.value, {
        displayName: String(form.displayName ?? "").trim(),
        openaiBaseUrl: String(form.openaiBaseUrl ?? "").trim(),
        openaiModelId: String(form.openaiModelId ?? "").trim(),
        modelKind: props.tab.kind,
        ...(props.tab.kind === "VECTOR" || props.tab.kind === "WEB_SEARCH"
          ? { integrationBackend: String(form.integrationBackend ?? "").trim() }
          : {}),
        apiKey: String(form.apiKey ?? "").trim() || undefined,
        ...(props.tab.kind === "VECTOR" && form.clearApiKey ? { clearApiKey: true as const } : {}),
        allowAnonymous: !!form.allowAnonymous,
        maxAttachments:
          props.tab.kind === "VECTOR" ? 10 : props.tab.kind === "WEB_SEARCH" ? 0 : Number(form.maxAttachments ?? 0),
        supportsThinking:
          props.tab.kind === "VECTOR" || props.tab.kind === "WEB_SEARCH" ? false : !!form.supportsThinking,
        enabled: !!form.enabled,
        sortOrder: Number(form.sortOrder ?? 0),
        ...(form.tokenQuotaUnlimited
          ? { tokenQuotaUnlimited: true as const }
          : { tokenQuotaTotal: Number(form.tokenQuotaTotal) }),
        localDeploy: !!form.localDeploy,
      });
      ElMessage.success(t("views.llmKindTab.saved"));
    } else {
      const body: CreateLlmModelBody = {
        alias: String(form.alias ?? "").trim(),
        displayName: String(form.displayName ?? "").trim(),
        openaiBaseUrl: String(form.openaiBaseUrl ?? "").trim(),
        openaiModelId: String(form.openaiModelId ?? "").trim(),
        modelKind: props.tab.kind,
        ...(props.tab.kind === "VECTOR" || props.tab.kind === "WEB_SEARCH"
          ? { integrationBackend: String(form.integrationBackend ?? "").trim() }
          : {}),
        apiKey: String(form.apiKey ?? "").trim(),
        allowAnonymous: !!form.allowAnonymous,
        maxAttachments:
          props.tab.kind === "VECTOR" ? 10 : props.tab.kind === "WEB_SEARCH" ? 0 : Number(form.maxAttachments ?? 0),
        supportsThinking:
          props.tab.kind === "VECTOR" || props.tab.kind === "WEB_SEARCH" ? false : !!form.supportsThinking,
        enabled: !!form.enabled,
        sortOrder: Number(form.sortOrder ?? 0),
        tokenQuotaTotal: form.tokenQuotaUnlimited ? null : Number(form.tokenQuotaTotal),
        localDeploy: !!form.localDeploy,
      };
      if (props.tab.kind !== "VECTOR" && props.tab.kind !== "WEB_SEARCH" && !body.apiKey?.trim()) {
        ElMessage.warning(t("views.llmKindTab.apiKeyWarning"));
        saving.value = false;
        return;
      }
      await modelsApi.createLlmModel(body);
      ElMessage.success(t("views.llmKindTab.created"));
    }
    dlg.value = false;
    await loadRows();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.llmKindTab.saveFailed")));
  } finally {
    saving.value = false;
  }
}

async function toggleRowEnabled(row: LlmModelAdminView, enabled: boolean) {
  if (row.enabled === enabled || togglingEnabledId.value !== null) return;
  togglingEnabledId.value = row.id;
  const prev = row.enabled;
  row.enabled = enabled;
  try {
    await modelsApi.updateLlmModel(row.id, { modelKind: props.tab.kind, enabled });
    ElMessage.success(t("views.llmKindTab.saved"));
  } catch (e: unknown) {
    row.enabled = prev;
    ElMessage.error(apiRequestErrorMessage(e, t("views.llmKindTab.saveFailed")));
  } finally {
    togglingEnabledId.value = null;
  }
}

async function remove(row: LlmModelAdminView) {
  try {
    await ElMessageBox.confirm(t("views.llmKindTab.deleteConfirm", { name: row.displayName }), t("views.llmKindTab.confirm"), {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await modelsApi.deleteLlmModel(row.id);
    ElMessage.success(t("views.llmKindTab.deleted"));
    await loadRows();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.llmKindTab.deleteFailed")));
  }
}
</script>

<style scoped>
.tab-panel {
  padding-top: 8px;
}

.toolbar {
  margin-bottom: 12px;
}

.mono {
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, monospace;
}

.quota-sep {
  margin: 0 4px;
  color: var(--el-text-color-placeholder);
}

.form {
  padding: 4px 8px 12px 4px;
}

/* 弹窗：标题区 + Element Plus 滚动条（勿用原生 overflow 以免样式割裂） */
.llm-model-dialog :deep(.llm-model-dialog-header) {
  padding: 14px 20px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.llm-model-dialog :deep(.el-dialog__headerbtn) {
  top: 12px;
}

.dlg-hdr {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding-right: 36px;
}

.dlg-hdr-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.35;
}

.dlg-hdr-kind {
  margin: 0;
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.llm-model-dialog :deep(.llm-model-dialog-body) {
  padding: 0 4px 4px;
}

.dlg-scroll {
  padding: 0 4px 0 0;
}

.dlg-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.form :deep(.el-form-item:last-child) {
  margin-bottom: 6px;
}
</style>
