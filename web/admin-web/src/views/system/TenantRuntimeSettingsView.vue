<template>
  <div class="runtime-settings">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb"
      :title="t('views.runtime.alertTitle')"
      :description="t('views.runtime.alertDesc')"
    />
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="hdr">{{ t("views.runtime.hdr") }}</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              clearable
              :placeholder="t('views.runtime.keywordPh')"
              class="kw-input"
              @keyup.enter="runQuery"
            />
            <el-select
              v-model="valueKindFilter"
              clearable
              class="kind-select"
              :placeholder="t('views.runtime.filterKindPh')"
              @change="onKindChange"
            >
              <el-option :label="t('views.runtime.filterKindAll')" value="" />
              <el-option :label="t('views.runtime.filterKindString')" value="STRING" />
              <el-option :label="t('views.runtime.filterKindBoolean')" value="BOOLEAN" />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="runQuery">{{ t("views.runtime.query") }}</el-button>
            <el-button :loading="loading" @click="reload">{{ t("views.runtime.reload") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" border stripe style="width: 100%">
        <el-table-column prop="descriptionZh" :label="t('views.runtime.colDesc')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="key" :label="t('views.runtime.colKey')" width="220" show-overflow-tooltip />
        <el-table-column prop="valueKind" :label="t('views.runtime.colKind')" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.valueKind === 'BOOLEAN' ? 'success' : 'info'">{{ row.valueKind }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.runtime.colValue')" min-width="260">
          <template #default="{ row }">
            <template v-if="row.valueKind === 'BOOLEAN'">
              <el-switch
                v-model="row.valueText"
                active-value="true"
                inactive-value="false"
                :disabled="persistingKey === row.key"
                @change="() => persistBoolean(row)"
              />
            </template>
            <template v-else-if="isStringMasked(row)">
              <div class="string-secret-row">
                <span class="masked-bullets" aria-hidden="true">{{ maskDots(row.valueText) }}</span>
                <el-button type="primary" link @click.stop="setRevealed(row.key, true)">{{
                  t("views.runtime.show")
                }}</el-button>
                <el-button type="primary" link @click.stop="openStringDialog(row)">{{ t("views.runtime.edit") }}</el-button>
              </div>
            </template>
            <template v-else-if="isStringRevealedSecret(row)">
              <div class="string-secret-row">
                <div
                  class="string-value-editable"
                  role="button"
                  tabindex="0"
                  :title="row.valueText || undefined"
                  :aria-label="t('views.runtime.ariaEdit', { label: row.descriptionZh || row.key })"
                  @click="openStringDialog(row)"
                  @keydown.enter.prevent="openStringDialog(row)"
                >
                  {{ previewValue(row.valueText) }}
                </div>
                <el-button type="primary" link @click.stop="setRevealed(row.key, false)">{{
                  t("views.runtime.hide")
                }}</el-button>
              </div>
            </template>
            <template v-else>
              <div
                class="string-value-editable"
                role="button"
                tabindex="0"
                :title="row.valueText || undefined"
                :aria-label="t('views.runtime.ariaEdit', { label: row.descriptionZh || row.key })"
                @click="openStringDialog(row)"
                @keydown.enter.prevent="openStringDialog(row)"
              >
                {{ previewValue(row.valueText) }}
              </div>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="stringDialogOpen" :title="stringDialogTitle" width="560px" destroy-on-close @closed="onStringDialogClosed">
      <el-input
        v-model="stringDialogValue"
        type="textarea"
        :autosize="{ minRows: 8, maxRows: 16 }"
        :maxlength="65000"
        show-word-limit
        :placeholder="t('views.runtime.valuePh')"
      />
      <template #footer>
        <el-button @click="stringDialogOpen = false">{{ t("views.runtime.cancel") }}</el-button>
        <el-button type="primary" :loading="stringDialogSubmitting" @click="confirmStringDialog">{{
          t("views.runtime.dlgOk")
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as api from "@/api/tenantRuntimeSettings";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t } = useI18n();

const rows = ref<api.TenantRuntimeSettingRow[]>([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const keyword = ref("");
const valueKindFilter = ref<string>("");
/** 已提交给后端的查询词（与输入框同步于「查询」或回车） */
const appliedKeyword = ref("");
/** 已提交的值类型筛选（与下拉同步于「查询」或类型变更） */
const appliedValueKind = ref("");

/** 正在持久化的键（避免重复点击） */
const persistingKey = ref<string | null>(null);

/** 表格内已临时明文展示的敏感键（刷新列表后清空） */
const revealedByKey = ref<Record<string, boolean>>({});

const stringDialogOpen = ref(false);
const stringDialogSubmitting = ref(false);
const stringDialogKey = ref<string | null>(null);
const stringDialogDescription = ref("");
const stringDialogValue = ref("");

const stringDialogTitle = computed(() => {
  const d = stringDialogDescription.value;
  return d ? t("views.runtime.dlgModifyPrefix", { desc: d }) : t("views.runtime.dlgModify");
});

function valuePresent(text: string | undefined) {
  return text != null && text.length > 0;
}

function isStringMasked(row: api.TenantRuntimeSettingRow) {
  return row.valueKind !== "BOOLEAN" && row.sensitive === true && valuePresent(row.valueText) && !revealedByKey.value[row.key];
}

function isStringRevealedSecret(row: api.TenantRuntimeSettingRow) {
  return row.valueKind !== "BOOLEAN" && row.sensitive === true && valuePresent(row.valueText) && !!revealedByKey.value[row.key];
}

function setRevealed(key: string, show: boolean) {
  if (show) {
    revealedByKey.value = { ...revealedByKey.value, [key]: true };
  } else {
    const next = { ...revealedByKey.value };
    delete next[key];
    revealedByKey.value = next;
  }
}

/** 与长度弱相关的占位符，不还原真实长度以免被推断 */
function maskDots(_raw: string | undefined) {
  return "••••••••";
}

function previewValue(text: string | undefined) {
  const s = text ?? "";
  if (!s) {
    return t("views.runtime.emptyValue");
  }
  if (s.length <= 96) {
    return s;
  }
  return s.slice(0, 96) + "…";
}

async function load() {
  loading.value = true;
  try {
    const data = await api.fetchTenantRuntimeSettingsPage({
      current: page.value,
      size: size.value,
      keyword: appliedKeyword.value,
      valueKind: appliedValueKind.value || undefined,
    });
    rows.value = data.records ?? [];
    total.value = data.total ?? 0;
    revealedByKey.value = {};
  } finally {
    loading.value = false;
  }
}

function runQuery() {
  appliedKeyword.value = keyword.value.trim();
  appliedValueKind.value = (valueKindFilter.value ?? "").trim();
  page.value = 1;
  void load();
}

function onKindChange() {
  appliedValueKind.value = (valueKindFilter.value ?? "").trim();
  page.value = 1;
  void load();
}

function reload() {
  void load();
}

function onSizeChange() {
  page.value = 1;
  void load();
}

async function persistOne(key: string, valueText: string) {
  await api.replaceTenantRuntimeSettings([{ key, valueText }]);
  ElMessage.success(t("views.runtime.saved"));
  await load();
}

async function persistBoolean(row: api.TenantRuntimeSettingRow) {
  if (persistingKey.value) {
    return;
  }
  persistingKey.value = row.key;
  try {
    await persistOne(row.key, row.valueText);
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.runtime.saveFailed")));
    await load();
  } finally {
    persistingKey.value = null;
  }
}

function openStringDialog(row: api.TenantRuntimeSettingRow) {
  if (stringDialogSubmitting.value) {
    return;
  }
  stringDialogKey.value = row.key;
  stringDialogDescription.value = row.descriptionZh ?? row.key;
  stringDialogValue.value = row.valueText ?? "";
  stringDialogOpen.value = true;
}

function onStringDialogClosed() {
  stringDialogKey.value = null;
  stringDialogDescription.value = "";
  stringDialogValue.value = "";
}

async function confirmStringDialog() {
  const key = stringDialogKey.value;
  if (!key) {
    stringDialogOpen.value = false;
    return;
  }
  stringDialogSubmitting.value = true;
  try {
    await persistOne(key, stringDialogValue.value.trim());
    stringDialogOpen.value = false;
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.runtime.saveFailed")));
  } finally {
    stringDialogSubmitting.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.runtime-settings {
  padding: 8px 4px;
}
.mb {
  margin-bottom: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.kw-input {
  width: min(320px, 100%);
}
.kind-select {
  width: 140px;
}
.hdr {
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.string-secret-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.masked-bullets {
  font-size: 14px;
  letter-spacing: 1px;
  color: var(--el-text-color-secondary);
  user-select: none;
}
.string-value-editable {
  display: inline-block;
  max-width: 100%;
  cursor: pointer;
  padding: 2px 0;
  border-radius: 2px;
  outline: none;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 500;
  text-decoration: underline;
  text-decoration-style: solid;
  text-underline-offset: 3px;
  text-decoration-thickness: 1px;
  word-break: break-all;
  transition:
    color 0.15s ease,
    text-decoration-color 0.15s ease;
}
.string-value-editable:hover {
  color: var(--el-color-primary-light-3);
  text-decoration-color: var(--el-color-primary-light-3);
}
.string-value-editable:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
  border-radius: 4px;
}
</style>
