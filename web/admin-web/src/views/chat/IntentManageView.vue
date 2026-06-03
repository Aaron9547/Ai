<template>
  <div class="intent-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      :title="t('views.intent.alertTitle')"
      :description="t('views.intent.alertDesc')"
    />
    <el-card shadow="never" class="card">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.intent.listTitle") }}</span>
          <div class="actions">
            <el-button type="primary" plain :loading="loading" class="users-refresh-btn" @click="loadAll">
              {{ t("views.intent.refresh") }}
            </el-button>
            <el-button type="primary" @click="openCreate">{{ t("views.intent.newIntent") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="intents" stripe border :empty-text="t('views.intent.empty')">
        <el-table-column prop="id" :label="t('views.intent.colId')" width="72" />
        <el-table-column prop="code" :label="t('views.intent.colCode')" min-width="120" />
        <el-table-column prop="displayName" :label="t('views.intent.colName')" min-width="120" />
        <el-table-column prop="handlerKind" :label="t('views.intent.colHandler')" min-width="200">
          <template #default="{ row }">
            <span>{{ handlerKindLabelForRow(row.handlerKind) }}</span>
            <span class="handler-kind-code">{{ row.handlerKind }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.intent.colEnabled')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
              {{ row.enabled === "ON" ? t("views.intent.yes") : t("views.intent.no") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="t('views.intent.colPriority')" width="88" align="center" />
        <el-table-column :label="t('views.intent.colActions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openKeywords(row)">{{ t("views.intent.keywords") }}</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.intent.edit") }}</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">{{ t("views.intent.delete") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dlgVisible"
      :title="dlgMode === 'create' ? t('views.intent.dlgNew') : t('views.intent.dlgEdit')"
      width="min(92vw, 860px)"
      destroy-on-close
      class="intent-edit-dlg"
    >
      <el-scrollbar class="intent-dlg-scroll" max-height="min(70vh, 560px)">
        <el-form label-position="top" class="intent-dlg-form" label-width="0">
          <el-form-item v-if="dlgMode === 'create'" :label="t('views.intent.labelCode')" required>
            <el-input v-model="form.code" :placeholder="t('views.intent.codePh')" />
          </el-form-item>
          <el-form-item :label="t('views.intent.labelName')" required>
            <el-input v-model="form.displayName" />
          </el-form-item>
          <el-form-item :label="t('views.intent.labelDesc')">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item :label="t('views.intent.labelHandler')" required>
            <el-select
              v-model="form.handlerKind"
              class="intent-handler-select"
              popper-class="intent-handler-select-dropdown"
              style="width: 100%"
              :disabled="!handlerKindOptions.length"
            >
              <el-option v-for="h in handlerKindOptions" :key="h.kind" :label="h.labelZh" :value="h.kind">
                <div class="handler-opt">
                  <div class="handler-opt-title">{{ h.labelZh }}</div>
                  <div class="handler-opt-desc">{{ h.description }}</div>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
          <div class="intent-dlg-meta-section" role="group" :aria-label="t('views.intent.metaSectionTitle')">
            <div class="intent-dlg-meta-section-title">{{ t("views.intent.metaSectionTitle") }}</div>
            <div class="intent-dlg-meta-grid">
              <div class="intent-dlg-meta-card">
                <div class="intent-dlg-meta-card-head">
                  <span class="intent-dlg-meta-card-title">{{ t("views.intent.labelEnabledInline") }}</span>
                  <el-tag
                    :type="form.enabledOn ? 'success' : 'info'"
                    effect="light"
                    round
                    size="small"
                    class="intent-status-tag"
                  >
                    {{ form.enabledOn ? t("views.intent.statusOn") : t("views.intent.statusOff") }}
                  </el-tag>
                </div>
                <p class="intent-dlg-meta-card-hint">{{ t("views.intent.enabledHint") }}</p>
                <div class="intent-dlg-meta-card-ctrl intent-dlg-meta-card-ctrl--switch">
                  <el-switch v-model="form.enabledOn" size="large" />
                </div>
              </div>
              <div class="intent-dlg-meta-card">
                <div class="intent-dlg-meta-card-head">
                  <span class="intent-dlg-meta-card-title">{{ t("views.intent.labelPriority") }}</span>
                </div>
                <p class="intent-dlg-meta-card-hint">{{ t("views.intent.priorityHint") }}</p>
                <div class="intent-dlg-meta-card-ctrl">
                  <el-input-number
                    v-model="form.sortOrder"
                    :min="0"
                    :max="999999"
                    controls-position="right"
                    class="intent-sort-input-full"
                  />
                </div>
              </div>
            </div>
          </div>
          <template v-if="sortedHandlerSchema.length > 0">
            <el-divider content-position="left" class="intent-params-divider">{{ t("views.intent.labelParamsDivider") }}</el-divider>
            <el-form-item
              v-for="f in sortedHandlerSchema"
              :key="f.name"
              :label="paramLabel(f)"
              :required="f.required"
              class="intent-param-item"
            >
              <el-select
                v-if="f.valueKind === 'SELECT'"
                v-model="handlerParamsForm[f.name]"
                style="width: 100%"
              >
                <el-option
                  v-for="o in f.options ?? []"
                  :key="o.value"
                  :label="o.labelZh"
                  :value="o.value"
                />
              </el-select>
              <el-switch
                v-else-if="f.valueKind === 'BOOLEAN'"
                v-model="handlerParamsForm[f.name]"
                active-value="true"
                inactive-value="false"
              />
              <el-input-number
                v-else-if="f.valueKind === 'INT'"
                :model-value="intFormModel(f.name)"
                :min="f.intMin ?? undefined"
                :max="f.intMax ?? undefined"
                controls-position="right"
                style="width: 100%"
                @update:model-value="(v: number | undefined) => setIntFormModel(f.name, v)"
              />
              <el-input
                v-else-if="f.valueKind === 'SECRET_STRING'"
                v-model="handlerParamsForm[f.name]"
                type="password"
                show-password
                clearable
                :placeholder="f.placeholder || undefined"
              />
              <el-input
                v-else
                v-model="handlerParamsForm[f.name]"
                clearable
                :placeholder="f.placeholder || undefined"
              />
            </el-form-item>
          </template>
        </el-form>
      </el-scrollbar>
      <template #footer>
        <el-button @click="dlgVisible = false">{{ t("views.intent.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitDlg">{{ t("views.intent.save") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="kwDrawer"
      :title="t('views.intent.kwDrawerTitle', { name: kwIntent?.displayName ?? '' })"
      class="kw-drawer"
      size="760px"
      destroy-on-close
    >
      <div class="kw-head">
        <el-button type="primary" size="small" @click="openKwCreate">{{ t("views.intent.kwNew") }}</el-button>
        <el-button text type="primary" :loading="kwLoading" @click="loadKeywords">{{ t("views.intent.kwRefresh") }}</el-button>
      </div>
      <div class="kw-table-scroll">
        <el-table v-loading="kwLoading" :data="keywords" stripe border class="kw-table" :empty-text="t('views.intent.kwEmpty')">
          <el-table-column prop="phrase" :label="t('views.intent.colPhrase')" min-width="120" />
          <el-table-column prop="keywordKind" :label="t('views.intent.colKind')" min-width="132">
            <template #default="{ row }">
              {{ kwKindLabel(row.keywordKind) }}
            </template>
          </el-table-column>
          <el-table-column prop="targetRound" :label="t('views.intent.colTargetRound')" min-width="100" />
          <el-table-column :label="t('views.intent.colEnabled')" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
                {{ row.enabled === "ON" ? t("views.intent.yes") : t("views.intent.no") }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" :label="t('views.intent.colSort')" width="72" align="center" />
          <el-table-column prop="hitCount" :label="t('views.intent.colHits')" width="88" align="right" />
          <el-table-column :label="t('views.intent.colActions')" width="128" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openKwEdit(row)">{{ t("views.intent.edit") }}</el-button>
              <el-button link type="danger" size="small" @click="onKwDelete(row)">{{ t("views.intent.kwDel") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="kwDlg"
      :title="kwDlgMode === 'create' ? t('views.intent.kwDlgNew') : t('views.intent.kwDlgEdit')"
      width="440px"
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-form-item :label="t('views.intent.phraseLabel')" required>
          <el-input v-model="kwForm.phrase" :placeholder="t('views.intent.phrasePh')" />
        </el-form-item>
        <el-form-item :label="t('views.intent.kindLabel')" required>
          <el-select v-model="kwForm.keywordKind" style="width: 100%">
            <el-option :label="kwKindLabel('TRIGGER')" value="TRIGGER" />
            <el-option :label="kwKindLabel('PLAN_CONTINUE')" value="PLAN_CONTINUE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('views.intent.targetRoundLabel')">
          <el-input v-model="kwForm.targetRound" :placeholder="t('views.intent.targetRoundPh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.intent.labelEnabledInline')">
          <div class="intent-kw-enable-row">
            <el-tag
              :type="kwForm.enabledOn ? 'success' : 'info'"
              effect="light"
              round
              size="small"
              class="intent-status-tag"
            >
              {{ kwForm.enabledOn ? t("views.intent.statusOn") : t("views.intent.statusOff") }}
            </el-tag>
            <el-switch v-model="kwForm.enabledOn" />
          </div>
        </el-form-item>
        <el-form-item :label="t('views.intent.colSort')">
          <el-input-number v-model="kwForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kwDlg = false">{{ t("views.intent.cancel") }}</el-button>
        <el-button type="primary" :loading="kwSaving" @click="submitKw">{{ t("views.intent.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as chatIntentApi from "@/api/chatIntent";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import { mergeIntentExtraFromSchema, readIntentHandlerFormFromExtra } from "./intentAdminMeta";
import type { IntentHandlerConfigFieldMeta } from "@/api/chatIntent";

const { t, te } = useI18n();

const loading = ref(false);
const intents = ref<chatIntentApi.IntentRow[]>([]);

const dlgVisible = ref(false);
const dlgMode = ref<"create" | "edit">("create");
const saving = ref(false);
const editingId = ref<number | null>(null);
/** 编辑时的原始扩展 JSON（仅内存，用于合并保留非 schema 键；不落表单展示） */
const extraConfigBaseline = ref<string>("");
const handlerKindOptions = ref<chatIntentApi.IntentHandlerKindOption[]>([]);

const form = reactive({
  code: "",
  displayName: "",
  description: "",
  handlerKind: "" as chatIntentApi.ChatIntentHandlerKind,
  enabledOn: false,
  sortOrder: 0,
});

const kwDrawer = ref(false);
const kwLoading = ref(false);
const keywords = ref<chatIntentApi.KeywordRow[]>([]);
const kwIntent = ref<chatIntentApi.IntentRow | null>(null);
const kwDlg = ref(false);
const kwDlgMode = ref<"create" | "edit">("create");
const kwSaving = ref(false);
const kwEditingId = ref<number | null>(null);
const kwForm = reactive({
  phrase: "",
  keywordKind: "TRIGGER" as chatIntentApi.ChatIntentKeywordKind,
  targetRound: "",
  enabledOn: true,
  sortOrder: 0,
});

const handlerSchema = ref<chatIntentApi.IntentHandlerConfigFieldMeta[]>([]);
/** 与 schema 对齐的 handlerParams 表单值（字符串；布尔为 true/false） */
const handlerParamsForm = reactive<Record<string, string>>({});

const sortedHandlerSchema = computed(() =>
  [...handlerSchema.value].sort((a, b) => a.sortOrder - b.sortOrder),
);

function kwKindLabel(kind: string): string {
  const key = `views.intent.keywordKinds.${kind}`;
  return te(key) ? String(t(key)) : kind;
}

function handlerKindLabelForRow(kind: string) {
  return handlerKindOptions.value.find((x) => x.kind === kind)?.labelZh ?? kind;
}

function paramLabel(f: IntentHandlerConfigFieldMeta): string {
  return f.labelZh;
}

function intFormModel(name: string): number | undefined {
  const s = handlerParamsForm[name];
  if (s === undefined || s === "") return undefined;
  const n = parseInt(s, 10);
  return Number.isNaN(n) ? undefined : n;
}

function setIntFormModel(name: string, v: number | undefined) {
  if (v === undefined || Number.isNaN(v)) {
    handlerParamsForm[name] = "";
  } else {
    handlerParamsForm[name] = String(Math.trunc(v));
  }
}

async function loadHandlerKindOptions() {
  try {
    handlerKindOptions.value = await chatIntentApi.listIntentHandlerKinds();
    if (
      form.handlerKind &&
      !handlerKindOptions.value.some((x) => x.kind === form.handlerKind) &&
      handlerKindOptions.value.length
    ) {
      form.handlerKind = handlerKindOptions.value[0]!.kind;
    }
    if (!form.handlerKind && handlerKindOptions.value.length) {
      form.handlerKind = handlerKindOptions.value[0]!.kind;
    }
  } catch {
    handlerKindOptions.value = [];
  }
}

async function loadHandlerSchema(kind: chatIntentApi.ChatIntentHandlerKind) {
  try {
    handlerSchema.value = kind ? await chatIntentApi.getIntentHandlerConfigSchema(kind) : [];
  } catch {
    handlerSchema.value = [];
  }
  for (const k of Object.keys(handlerParamsForm)) {
    delete handlerParamsForm[k];
  }
  for (const f of handlerSchema.value) {
    if (f.valueKind === "BOOLEAN") {
      handlerParamsForm[f.name] = "true";
    } else if (f.valueKind === "SELECT") {
      handlerParamsForm[f.name] = f.options?.[0]?.value ?? "";
    } else if (f.valueKind === "INT") {
      handlerParamsForm[f.name] = "";
    } else {
      handlerParamsForm[f.name] = "";
    }
  }
}

function hydrateHandlerParamsFromBaseline() {
  const fromDb = readIntentHandlerFormFromExtra(extraConfigBaseline.value, handlerSchema.value);
  for (const f of handlerSchema.value) {
    const existing = fromDb[f.name];
    if (existing !== undefined && existing !== "") {
      handlerParamsForm[f.name] = existing;
    } else if (f.valueKind === "SELECT") {
      handlerParamsForm[f.name] = existing ?? f.options?.[0]?.value ?? "";
    } else if (f.valueKind === "BOOLEAN") {
      handlerParamsForm[f.name] = "true";
    } else if (f.valueKind === "INT") {
      handlerParamsForm[f.name] = "";
    } else {
      handlerParamsForm[f.name] = "";
    }
  }
}

watch(
  () => [dlgVisible.value, form.handlerKind] as const,
  async ([vis]) => {
    if (!vis) return;
    await loadHandlerSchema(form.handlerKind);
    if (dlgMode.value === "edit" && editingId.value != null) {
      hydrateHandlerParamsFromBaseline();
    }
  },
);

async function loadAll() {
  loading.value = true;
  try {
    intents.value = await chatIntentApi.listIntents();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.loadFailed")));
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.code = "";
  form.displayName = "";
  form.description = "";
  form.handlerKind = handlerKindOptions.value[0]?.kind ?? "";
  form.enabledOn = false;
  form.sortOrder = 0;
  extraConfigBaseline.value = "";
}

async function openCreate() {
  if (!handlerKindOptions.value.length) {
    await loadHandlerKindOptions();
  }
  dlgMode.value = "create";
  editingId.value = null;
  resetForm();
  dlgVisible.value = true;
}

function openEdit(row: chatIntentApi.IntentRow) {
  dlgMode.value = "edit";
  editingId.value = row.id;
  form.code = row.code;
  form.displayName = row.displayName;
  form.description = row.description ?? "";
  form.handlerKind = row.handlerKind;
  form.enabledOn = row.enabled === "ON";
  form.sortOrder = row.sortOrder;
  extraConfigBaseline.value = row.extraConfigJson ?? "";
  dlgVisible.value = true;
}

function extraConfigPayloadForSubmit(): string | null {
  return mergeIntentExtraFromSchema(extraConfigBaseline.value || null, sortedHandlerSchema.value, {
    ...handlerParamsForm,
  });
}

async function submitDlg() {
  saving.value = true;
  try {
    const en: chatIntentApi.ToggleState = form.enabledOn ? "ON" : "OFF";
    if (dlgMode.value === "create") {
      await chatIntentApi.createIntent({
        code: form.code.trim(),
        displayName: form.displayName.trim(),
        description: form.description || null,
        handlerKind: form.handlerKind,
        enabled: en,
        sortOrder: form.sortOrder,
        extraConfigJson: extraConfigPayloadForSubmit(),
      });
      ElMessage.success(t("views.intent.created"));
    } else if (editingId.value != null) {
      await chatIntentApi.updateIntent(editingId.value, {
        displayName: form.displayName.trim(),
        description: form.description || null,
        handlerKind: form.handlerKind,
        enabled: en,
        sortOrder: form.sortOrder,
        extraConfigJson: extraConfigPayloadForSubmit(),
      });
      ElMessage.success(t("views.intent.saved"));
    }
    dlgVisible.value = false;
    await loadAll();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.saveFailed")));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: chatIntentApi.IntentRow) {
  try {
    await ElMessageBox.confirm(t("views.intent.deleteIntentConfirm", { name: row.displayName }), t("views.intent.confirm"), {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await chatIntentApi.deleteIntent(row.id);
    ElMessage.success(t("views.intent.deleted"));
    await loadAll();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.deleteFailed")));
  }
}

async function openKeywords(row: chatIntentApi.IntentRow) {
  kwIntent.value = row;
  kwDrawer.value = true;
  await loadKeywords();
}

async function loadKeywords() {
  if (!kwIntent.value) return;
  kwLoading.value = true;
  try {
    keywords.value = await chatIntentApi.listKeywords(kwIntent.value.id);
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.loadKwFailed")));
  } finally {
    kwLoading.value = false;
  }
}

function openKwCreate() {
  kwDlgMode.value = "create";
  kwEditingId.value = null;
  kwForm.phrase = "";
  kwForm.keywordKind = "TRIGGER";
  kwForm.targetRound = "";
  kwForm.enabledOn = true;
  kwForm.sortOrder = 0;
  kwDlg.value = true;
}

function openKwEdit(row: chatIntentApi.KeywordRow) {
  kwDlgMode.value = "edit";
  kwEditingId.value = row.id;
  kwForm.phrase = row.phrase;
  kwForm.keywordKind = row.keywordKind;
  kwForm.targetRound = row.targetRound ?? "";
  kwForm.enabledOn = row.enabled === "ON";
  kwForm.sortOrder = row.sortOrder;
  kwDlg.value = true;
}

async function submitKw() {
  if (!kwIntent.value) return;
  kwSaving.value = true;
  try {
    const en: chatIntentApi.ToggleState = kwForm.enabledOn ? "ON" : "OFF";
    const tr = kwForm.targetRound.trim();
    if (kwDlgMode.value === "create") {
      await chatIntentApi.addKeyword(kwIntent.value.id, {
        phrase: kwForm.phrase.trim(),
        keywordKind: kwForm.keywordKind,
        targetRound: tr || null,
        enabled: en,
        sortOrder: kwForm.sortOrder,
      });
      ElMessage.success(t("views.intent.added"));
    } else if (kwEditingId.value != null) {
      await chatIntentApi.updateKeyword(kwIntent.value.id, kwEditingId.value, {
        phrase: kwForm.phrase.trim(),
        keywordKind: kwForm.keywordKind,
        targetRound: tr || null,
        enabled: en,
        sortOrder: kwForm.sortOrder,
      });
      ElMessage.success(t("views.intent.saved"));
    }
    kwDlg.value = false;
    await loadKeywords();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.saveFailed")));
  } finally {
    kwSaving.value = false;
  }
}

async function onKwDelete(row: chatIntentApi.KeywordRow) {
  if (!kwIntent.value) return;
  try {
    await ElMessageBox.confirm(t("views.intent.deleteKwConfirm", { phrase: row.phrase }), t("views.intent.confirm"), {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await chatIntentApi.deleteKeyword(kwIntent.value.id, row.id);
    ElMessage.success(t("views.intent.deleted"));
    await loadKeywords();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.intent.deleteFailed")));
  }
}

onMounted(async () => {
  await loadHandlerKindOptions();
  void loadAll();
});
</script>

<style scoped>
.intent-page {
  padding: 12px 16px 24px;
}
.hint {
  margin-bottom: 12px;
}
.card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  font-weight: 600;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.users-refresh-btn {
  flex-shrink: 0;
  min-width: 5.5rem;
}
.kw-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.kw-table {
  margin-top: 4px;
  min-width: 640px;
}

/* 抽屉内表格：加宽抽屉仍可能遇窄屏，横向滚动避免列被裁切 */
.kw-drawer :deep(.el-drawer__body) {
  padding: 12px 16px 20px;
  overflow-x: hidden;
}

.kw-table-scroll {
  width: 100%;
  margin-top: 4px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.handler-kind-code {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}
.handler-opt-title {
  font-weight: 500;
}
.handler-opt-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.35;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.field-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  max-width: 52rem;
}

/* 意图新增/编辑：留白充足、层次清晰 */
.intent-edit-dlg :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    0 24px 48px -12px rgba(15, 23, 42, 0.16),
    0 0 0 1px rgba(15, 23, 42, 0.04);
}

.intent-edit-dlg :deep(.el-dialog__header) {
  padding: 22px 40px 16px;
  margin: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(180deg, var(--el-bg-color) 0%, var(--el-fill-color-light) 100%);
}

.intent-edit-dlg :deep(.el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.01em;
}

.intent-edit-dlg :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 22px;
}

.intent-edit-dlg :deep(.el-dialog__body) {
  padding: 28px 40px 22px;
  background: #fbfcfe;
}

.intent-edit-dlg :deep(.el-dialog__footer) {
  padding: 18px 40px 28px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: #fafbfd;
}

.intent-dlg-scroll {
  border-radius: 14px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(165deg, var(--el-bg-color) 0%, var(--el-fill-color-light) 45%, var(--el-bg-color) 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    0 1px 2px rgba(15, 23, 42, 0.04);
}

.intent-dlg-scroll :deep(.el-scrollbar__view) {
  padding: 36px 32px 40px 36px;
}

.intent-dlg-form {
  padding: 4px 6px 2px;
}

.intent-dlg-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.intent-dlg-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--el-text-color-regular);
  line-height: 1.4;
  margin-bottom: 6px !important;
  padding: 0;
  white-space: normal;
  word-break: break-word;
}

.intent-dlg-form :deep(.el-input__wrapper),
.intent-dlg-form :deep(.el-textarea__inner) {
  border-radius: 12px;
}

/* 处理器下拉：触发器更圆润（与输入框一致） */
.intent-dlg-form .intent-handler-select :deep(.el-select__wrapper) {
  border-radius: 12px;
  min-height: 40px;
  padding-left: 14px;
  padding-right: 10px;
}

.intent-dlg-form :deep(.el-input-number .el-input__wrapper) {
  border-radius: 12px;
}

.intent-params-divider {
  margin: 22px 0 18px;
}

.intent-params-divider :deep(.el-divider__text) {
  font-weight: 600;
  color: var(--el-text-color-regular);
  background-color: transparent;
  padding: 0 12px 0 0;
}

/* 启用 + 优先级：双卡片栅格，置于处理器参数之上 */
.intent-dlg-meta-section {
  margin: 10px 0 0;
  padding: 0;
}

.intent-dlg-meta-section-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}

.intent-dlg-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 560px) {
  .intent-dlg-meta-grid {
    grid-template-columns: 1fr;
  }
}

.intent-dlg-meta-card {
  padding: 16px 18px 14px;
  border-radius: 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.intent-dlg-meta-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.intent-dlg-meta-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.intent-status-tag {
  flex-shrink: 0;
  font-weight: 600;
}

.intent-dlg-meta-card-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.intent-dlg-meta-card-ctrl {
  width: 100%;
}

.intent-dlg-meta-card-ctrl--switch {
  display: flex;
  justify-content: flex-end;
  padding-top: 2px;
}

.intent-dlg-meta-card-ctrl .intent-sort-input-full {
  width: 100% !important;
}

.intent-kw-enable-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

.intent-param-item :deep(.el-form-item__content) {
  min-width: 0;
}

</style>

<style>
/* 处理器下拉 Teleport 到 body，须单独块；圆角面板 + 圆角选项行 */
.intent-handler-select-dropdown.el-popper {
  border-radius: 14px !important;
  padding: 8px 6px;
  overflow: hidden;
  box-shadow:
    0 16px 40px -8px rgba(15, 23, 42, 0.18),
    0 0 0 1px rgba(15, 23, 42, 0.06);
}

.intent-handler-select-dropdown .el-select-dropdown__list {
  padding: 4px 0;
}

.intent-handler-select-dropdown .el-select-dropdown__item {
  border-radius: 11px;
  margin: 3px 8px;
  padding: 12px 14px;
  min-height: auto;
  line-height: 1.45;
}

.intent-handler-select-dropdown .el-select-dropdown__item.is-hovering,
.intent-handler-select-dropdown .el-select-dropdown__item.is-selected {
  border-radius: 11px;
}

.intent-handler-select-dropdown .el-select-dropdown__item.is-selected {
  font-weight: 600;
}
</style>
