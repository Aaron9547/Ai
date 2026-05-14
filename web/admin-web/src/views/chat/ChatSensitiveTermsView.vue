<template>
  <div class="page">
    <header class="head">
      <h1>{{ t("views.sensitive.title") }}</h1>
      <p class="hint" v-html="t('views.sensitive.hint')" />
    </header>

    <div class="split">
      <el-card shadow="never" class="split-card">
        <template #header>
          <span>{{ t("views.sensitive.platformTitle") }}</span>
          <el-tag v-if="!isFounder" type="info" size="small" class="tag-ro">{{ t("views.sensitive.roTag") }}</el-tag>
        </template>
        <div class="pane-inner">
          <div v-if="isFounder" class="toolbar">
            <el-input v-model="platformWord" :placeholder="t('views.sensitive.addWordPh')" clearable class="inp-short" />
            <el-button type="primary" :loading="saving" @click="onAddPlatform">{{ t("views.sensitive.add") }}</el-button>
            <el-button @click="platformImportOpen = true">{{ t("views.sensitive.batchImport") }}</el-button>
          </div>
          <div class="toolbar toolbar--secondary">
            <el-input
              v-model="platformQInput"
              :placeholder="t('views.sensitive.filterPh')"
              clearable
              class="inp-short"
              @keyup.enter="applyPlatformSearch"
            />
            <el-button plain @click="applyPlatformSearch">{{ t("views.sensitive.filterBtn") }}</el-button>
          </div>
          <div class="table-scroll">
            <el-table
              v-loading="platformLoading"
              :data="platformTerms"
              stripe
              :empty-text="t('views.sensitive.emptyPlatform')"
            >
              <el-table-column prop="word" :label="t('views.sensitive.colWord')" min-width="120" show-overflow-tooltip />
              <el-table-column prop="createdAt" :label="t('views.sensitive.colCreated')" width="168" :formatter="fmtTimeCol" />
              <el-table-column v-if="isFounder" :label="t('views.sensitive.colActions')" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="onDelete(row)">{{ t("views.sensitive.delete") }}</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="pane-footer">
            <el-pagination
              v-model:current-page="platformPage"
              v-model:page-size="platformSize"
              layout="total, sizes, prev, pager, next"
              :total="platformTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              small
              @current-change="loadPlatform"
              @size-change="onPlatformSizeChange"
            />
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="split-card">
        <template #header>
          <span>{{ t("views.sensitive.tenantTitle") }}</span>
        </template>
        <div class="pane-inner">
          <div class="toolbar">
            <el-input v-model="tenantWord" :placeholder="t('views.sensitive.addWordPh')" clearable class="inp-short" />
            <el-button type="primary" :loading="saving" @click="onAddTenant">{{ t("views.sensitive.add") }}</el-button>
            <el-button @click="tenantImportOpen = true">{{ t("views.sensitive.batchImport") }}</el-button>
          </div>
          <div class="toolbar toolbar--secondary">
            <el-input
              v-model="tenantQInput"
              :placeholder="t('views.sensitive.filterPh')"
              clearable
              class="inp-short"
              @keyup.enter="applyTenantSearch"
            />
            <el-button plain @click="applyTenantSearch">{{ t("views.sensitive.filterBtn") }}</el-button>
          </div>
          <div class="table-scroll">
            <el-table v-loading="tenantLoading" :data="tenantTerms" stripe :empty-text="t('views.sensitive.emptyTenant')">
              <el-table-column prop="word" :label="t('views.sensitive.colWord')" min-width="120" show-overflow-tooltip />
              <el-table-column :label="t('views.sensitive.colTenantCode')" min-width="120" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.tenantCode?.trim() ? row.tenantCode : emDash }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" :label="t('views.sensitive.colCreated')" width="168" :formatter="fmtTimeCol" />
              <el-table-column :label="t('views.sensitive.colActions')" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="onDelete(row)">{{ t("views.sensitive.delete") }}</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-if="isFounder" class="toolbar toolbar--tenant-target">
            <span class="field-label">{{ t("views.sensitive.targetTenant") }}</span>
            <el-select
              v-model="extensionTenantId"
              class="tenant-select"
              clearable
              filterable
              :placeholder="t('views.sensitive.targetTenantPh')"
              @change="onExtensionTenantChange"
            >
              <el-option
                v-for="tenantOpt in tenantOptions"
                :key="tenantOpt.id"
                :label="`${tenantOpt.name} (${tenantOpt.code})`"
                :value="tenantOpt.id"
              />
            </el-select>
          </div>
          <div class="pane-footer">
            <el-pagination
              v-model:current-page="tenantPage"
              v-model:page-size="tenantSize"
              layout="total, sizes, prev, pager, next"
              :total="tenantTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              small
              @current-change="loadTenant"
              @size-change="onTenantSizeChange"
            />
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="platformImportOpen" :title="t('views.sensitive.dlgPlatformImport')" width="520px" destroy-on-close>
      <p class="dlg-hint">{{ t("views.sensitive.importHintPlatform") }}</p>
      <el-input v-model="platformImportText" type="textarea" :rows="10" :placeholder="t('views.sensitive.pastePh')" />
      <template #footer>
        <el-button @click="platformImportOpen = false">{{ t("views.sensitive.cancel") }}</el-button>
        <el-button type="primary" :loading="importing" @click="onImportPlatform">{{ t("views.sensitive.import") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tenantImportOpen" :title="t('views.sensitive.dlgTenantImport')" width="520px" destroy-on-close>
      <p class="dlg-hint">{{ t("views.sensitive.importHintTenant") }}</p>
      <el-input v-model="tenantImportText" type="textarea" :rows="10" :placeholder="t('views.sensitive.pastePh')" />
      <template #footer>
        <el-button @click="tenantImportOpen = false">{{ t("views.sensitive.cancel") }}</el-button>
        <el-button type="primary" :loading="importing" @click="onImportTenant">{{ t("views.sensitive.import") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as chatAdmin from "../../api/chatAdmin";
import type { SensitiveTermRow } from "../../api/chatAdmin";
import { useAdminFounderTenantOptions } from "../../composables/useAdminFounderTenantOptions";

const { t, locale } = useI18n();
const emDash = "\u2014";

const { isFounder, tenantOptions } = useAdminFounderTenantOptions();

const extensionTenantId = ref<number | undefined>(undefined);

const platformTerms = ref<SensitiveTermRow[]>([]);
const platformPage = ref(1);
const platformSize = ref(20);
const platformTotal = ref(0);
const platformQ = ref("");
const platformQInput = ref("");
const platformLoading = ref(false);

const tenantTerms = ref<SensitiveTermRow[]>([]);
const tenantPage = ref(1);
const tenantSize = ref(20);
const tenantTotal = ref(0);
const tenantQ = ref("");
const tenantQInput = ref("");
const tenantLoading = ref(false);

const platformWord = ref("");
const tenantWord = ref("");
const saving = ref(false);
const importing = ref(false);
const platformImportOpen = ref(false);
const tenantImportOpen = ref(false);
const platformImportText = ref("");
const tenantImportText = ref("");

function fmtTimeCol(_row: unknown, _col: unknown, cellValue: string) {
  if (!cellValue) return emDash;
  const d = new Date(cellValue);
  if (Number.isNaN(d.getTime())) return cellValue;
  const loc = locale.value === "en" ? "en-US" : "zh-CN";
  return d.toLocaleString(loc, { hour12: false });
}

function tenantPageOpts() {
  const o: { q?: string; filterTenantId?: number } = {};
  if (tenantQ.value.trim()) {
    o.q = tenantQ.value.trim();
  }
  if (extensionTenantId.value != null) {
    o.filterTenantId = extensionTenantId.value;
  }
  return o;
}

function tenantWriteTarget(): number | undefined {
  return isFounder.value && extensionTenantId.value != null ? extensionTenantId.value : undefined;
}

async function loadPlatform() {
  platformLoading.value = true;
  try {
    const q = platformQ.value.trim() || undefined;
    const data = await chatAdmin.fetchSensitiveTermsPlatformPage(platformPage.value, platformSize.value, q);
    platformTerms.value = data.records ?? [];
    platformTotal.value = data.total ?? 0;
  } catch {
    platformTerms.value = [];
    platformTotal.value = 0;
    ElMessage.error(t("views.sensitive.platformLoadErr"));
  } finally {
    platformLoading.value = false;
  }
}

async function loadTenant() {
  tenantLoading.value = true;
  try {
    const data = await chatAdmin.fetchSensitiveTermsTenantPage(
      tenantPage.value,
      tenantSize.value,
      tenantPageOpts(),
    );
    tenantTerms.value = data.records ?? [];
    tenantTotal.value = data.total ?? 0;
  } catch {
    tenantTerms.value = [];
    tenantTotal.value = 0;
    ElMessage.error(t("views.sensitive.tenantLoadErr"));
  } finally {
    tenantLoading.value = false;
  }
}

function applyPlatformSearch() {
  platformQ.value = platformQInput.value;
  platformPage.value = 1;
  void loadPlatform();
}

function applyTenantSearch() {
  tenantQ.value = tenantQInput.value;
  tenantPage.value = 1;
  void loadTenant();
}

function onPlatformSizeChange() {
  platformPage.value = 1;
  void loadPlatform();
}

function onTenantSizeChange() {
  tenantPage.value = 1;
  void loadTenant();
}

function onExtensionTenantChange() {
  tenantPage.value = 1;
  void loadTenant();
}

async function onAddPlatform() {
  const w = platformWord.value.trim();
  if (!w) return;
  saving.value = true;
  try {
    await chatAdmin.addSensitiveTerm("PLATFORM", w);
    platformWord.value = "";
    await loadPlatform();
    ElMessage.success(t("views.sensitive.added"));
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, t("views.sensitive.addFailed")));
  } finally {
    saving.value = false;
  }
}

async function onAddTenant() {
  const w = tenantWord.value.trim();
  if (!w) return;
  saving.value = true;
  try {
    await chatAdmin.addSensitiveTerm("TENANT", w, tenantWriteTarget());
    tenantWord.value = "";
    await loadTenant();
    ElMessage.success(t("views.sensitive.added"));
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, t("views.sensitive.addFailed")));
  } finally {
    saving.value = false;
  }
}

async function onImportPlatform() {
  if (!platformImportText.value.trim()) {
    ElMessage.warning(t("views.sensitive.importEmpty"));
    return;
  }
  importing.value = true;
  try {
    const r = await chatAdmin.importSensitiveTerms("PLATFORM", platformImportText.value);
    platformImportOpen.value = false;
    platformImportText.value = "";
    await loadPlatform();
    ElMessage.success(
      t("views.sensitive.importResult", {
        inserted: r.inserted,
        skippedDuplicates: r.skippedDuplicates,
        skippedInvalid: r.skippedInvalid,
      }),
    );
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, t("views.sensitive.importFailed")));
  } finally {
    importing.value = false;
  }
}

async function onImportTenant() {
  if (!tenantImportText.value.trim()) {
    ElMessage.warning(t("views.sensitive.importEmpty"));
    return;
  }
  importing.value = true;
  try {
    const r = await chatAdmin.importSensitiveTerms("TENANT", tenantImportText.value, tenantWriteTarget());
    tenantImportOpen.value = false;
    tenantImportText.value = "";
    await loadTenant();
    ElMessage.success(
      t("views.sensitive.importResult", {
        inserted: r.inserted,
        skippedDuplicates: r.skippedDuplicates,
        skippedInvalid: r.skippedInvalid,
      }),
    );
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, t("views.sensitive.importFailed")));
  } finally {
    importing.value = false;
  }
}

async function onDelete(row: SensitiveTermRow) {
  try {
    await ElMessageBox.confirm(t("views.sensitive.deleteConfirm", { word: row.word }), t("views.menuItems.confirm"), {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await chatAdmin.deleteSensitiveTerm(row.id);
    await Promise.all([loadPlatform(), loadTenant()]);
    ElMessage.success(t("views.sensitive.deleted"));
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, t("views.sensitive.deleteFailed")));
  }
}

function errMsg(e: unknown, fallback: string): string {
  if (e && typeof e === "object" && "response" in e) {
    const r = (e as { response?: { data?: { message?: string } } }).response?.data?.message;
    if (typeof r === "string" && r.length) return r;
  }
  return fallback;
}

onMounted(() => {
  void Promise.all([loadPlatform(), loadTenant()]).catch(() => ElMessage.error(t("views.sensitive.loadFailed")));
});
</script>

<style scoped>
.page {
  padding: 16px 20px 24px;
  width: 100%;
  max-width: none;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.head {
  flex-shrink: 0;
  margin-bottom: 14px;
}

.head h1 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.split {
  display: flex;
  flex-direction: row;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

@media (max-width: 960px) {
  .split {
    flex-direction: column;
  }
}

.split-card {
  flex: 1 1 0;
  min-width: 0;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.split-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pane-inner {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.tag-ro {
  margin-left: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  flex-shrink: 0;
}

.toolbar--secondary {
  margin-top: -2px;
}

.toolbar--tenant-target {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color);
}

.field-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.tenant-select {
  flex: 1 1 160px;
  min-width: 0;
  max-width: 100%;
}

.inp-short {
  flex: 1 1 140px;
  min-width: 0;
  max-width: 260px;
}

.table-scroll {
  flex: 1;
  min-height: 200px;
  overflow: auto;
}

.pane-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.dlg-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 10px;
}
</style>
