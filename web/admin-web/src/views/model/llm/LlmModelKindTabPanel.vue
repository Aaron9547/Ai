<template>
  <div class="tab-panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建{{ tab.label }}</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border empty-text="暂无模型">
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
            <el-switch :model-value="row.enabled" disabled />
          </template>
          <template v-else>
            {{ row[col.prop as keyof typeof row] }}
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dlg"
      class="llm-model-dialog"
      :title="editId ? `编辑 · ${tab.label}` : `新建 · ${tab.label}`"
      width="580px"
      destroy-on-close
      align-center
      @closed="onDlgClosed"
    >
      <div class="llm-model-dialog-scroll">
        <el-form :model="form" label-position="left" label-width="112px" size="small" class="form">
          <LlmModelFormFields
            :fields="tab.formFields"
            :form="form"
            :option-lists="optionLists"
            :is-edit="!!editId"
            :model-kind="tab.kind"
          />
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { reactive, ref, watch } from "vue";
import * as modelsApi from "../../../api/models";
import type {
  CreateLlmModelBody,
  LlmModelAdminMetaResponse,
  LlmModelAdminView,
  LlmVectorBackendCode,
  ModelKindTabMeta,
} from "../../../api/models";
import { apiRequestErrorMessage } from "../../../utils/apiRequestErrorMessage";
import LlmModelFormFields from "./LlmModelFormFields.vue";

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
  if (col.prop === "vectorBackend") return 220;
  if (col.prop === "localDeploy") return 108;
  return undefined;
}

function enumCellLabel(optionsKey: string, code: unknown): string {
  if (code == null || code === "") return "—";
  const opts = props.optionLists[optionsKey] || [];
  const o = opts.find((x) => x.code === code);
  return o ? o.label : String(code);
}

function boolTagText(prop: string, val: boolean): string {
  if (prop === "apiKeyConfigured") return val ? "已配" : "未配";
  if (prop === "allowAnonymous") return val ? "允许" : "禁止";
  if (prop === "supportsThinking") return val ? "支持" : "—";
  if (prop === "localDeploy") return val ? "Feign" : "直连";
  return val ? "是" : "否";
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
    else if (f.key === "vectorBackend") form[f.key] = "OPENAI_COMPATIBLE";
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
  if (props.tab.kind === "VECTOR") {
    form.vectorBackend = row.vectorBackend ?? "OPENAI_COMPATIBLE";
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
    if (f.key === "apiKey" && props.tab.kind === "VECTOR" && !editId.value) continue;
    if (f.key === "clearApiKey") continue;
    const v = form[f.key];
    if (v === undefined || v === null || (typeof v === "string" && !v.trim())) {
      ElMessage.warning(`请填写：${f.label}`);
      return false;
    }
  }
  if (!editId.value && !form.tokenQuotaUnlimited && (!form.tokenQuotaTotal || Number(form.tokenQuotaTotal) < 1)) {
    ElMessage.warning("请填写有效的 Token 上限，或打开「共用 Token 不限制」");
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
        ...(props.tab.kind === "VECTOR" ? { vectorBackend: form.vectorBackend as LlmVectorBackendCode } : {}),
        apiKey: String(form.apiKey ?? "").trim() || undefined,
        ...(props.tab.kind === "VECTOR" && form.clearApiKey ? { clearApiKey: true as const } : {}),
        allowAnonymous: !!form.allowAnonymous,
        maxAttachments:
          props.tab.kind === "VECTOR" ? 10 : Number(form.maxAttachments ?? 0),
        supportsThinking: props.tab.kind === "VECTOR" ? false : !!form.supportsThinking,
        enabled: !!form.enabled,
        sortOrder: Number(form.sortOrder ?? 0),
        ...(form.tokenQuotaUnlimited
          ? { tokenQuotaUnlimited: true as const }
          : { tokenQuotaTotal: Number(form.tokenQuotaTotal) }),
        localDeploy: !!form.localDeploy,
      });
      ElMessage.success("已保存");
    } else {
      const body: CreateLlmModelBody = {
        alias: String(form.alias ?? "").trim(),
        displayName: String(form.displayName ?? "").trim(),
        openaiBaseUrl: String(form.openaiBaseUrl ?? "").trim(),
        openaiModelId: String(form.openaiModelId ?? "").trim(),
        modelKind: props.tab.kind,
        ...(props.tab.kind === "VECTOR" ? { vectorBackend: form.vectorBackend as LlmVectorBackendCode } : {}),
        apiKey: String(form.apiKey ?? "").trim(),
        allowAnonymous: !!form.allowAnonymous,
        maxAttachments:
          props.tab.kind === "VECTOR" ? 10 : Number(form.maxAttachments ?? 0),
        supportsThinking: props.tab.kind === "VECTOR" ? false : !!form.supportsThinking,
        enabled: !!form.enabled,
        sortOrder: Number(form.sortOrder ?? 0),
        tokenQuotaTotal: form.tokenQuotaUnlimited ? null : Number(form.tokenQuotaTotal),
        localDeploy: !!form.localDeploy,
      };
      if (props.tab.kind !== "VECTOR" && !body.apiKey?.trim()) {
        ElMessage.warning("请填写 API Key");
        saving.value = false;
        return;
      }
      await modelsApi.createLlmModel(body);
      ElMessage.success("已创建");
    }
    dlg.value = false;
    await loadRows();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function remove(row: LlmModelAdminView) {
  try {
    await ElMessageBox.confirm(`确定删除模型「${row.displayName}」？`, "确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await modelsApi.deleteLlmModel(row.id);
    ElMessage.success("已删除");
    await loadRows();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
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
  color: #94a3b8;
}

.form {
  padding-top: 0;
}

/* 控制弹窗总高度：正文区滚动，避免字段多时整窗过长 */
.llm-model-dialog :deep(.el-dialog__body) {
  padding: 6px 16px 8px;
}

.llm-model-dialog-scroll {
  max-height: min(380px, 56vh);
  overflow-y: auto;
  padding-right: 2px;
}
</style>
