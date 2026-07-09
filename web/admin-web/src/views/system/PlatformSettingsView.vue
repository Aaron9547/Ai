<template>
  <div class="platform-settings">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb"
      :title="t('views.platformSettings.alertTitle')"
      :description="t('views.platformSettings.alertDesc')"
    />
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="hdr">{{ t("views.platformSettings.hdr") }}</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              clearable
              :placeholder="t('views.platformSettings.keywordPh')"
              class="kw-input"
              @keyup.enter="applyFilter"
            />
            <el-select
              v-model="valueKindFilter"
              clearable
              class="kind-select"
              :placeholder="t('views.platformSettings.filterKindPh')"
              @change="applyFilter"
            >
              <el-option :label="t('views.platformSettings.filterKindAll')" value="" />
              <el-option
                v-for="k in platformValueKinds"
                :key="k"
                :label="formatSettingValueKindLabel(t, k)"
                :value="k"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="applyFilter">{{
              t("views.platformSettings.query")
            }}</el-button>
            <el-button :loading="loading" @click="reload">{{ t("views.platformSettings.reload") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredRows" border stripe style="width: 100%">
        <el-table-column
          prop="descriptionZh"
          :label="t('views.platformSettings.colDesc')"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column prop="key" :label="t('views.platformSettings.colKey')" width="280" show-overflow-tooltip />
        <el-table-column prop="valueKind" :label="t('views.platformSettings.colKind')" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="settingValueKindTagType(row.valueKind)">{{
              formatSettingValueKindLabel(t, row.valueKind)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.platformSettings.colValue')" min-width="220">
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
            <template v-else>
              <div
                class="string-value-editable"
                role="button"
                tabindex="0"
                :title="row.valueText || undefined"
                :aria-label="t('views.platformSettings.ariaEdit', { label: row.descriptionZh || row.key })"
                @click="openValueDialog(row)"
                @keydown.enter.prevent="openValueDialog(row)"
              >
                {{ previewValue(row.valueText) }}
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column
          prop="defaultValueText"
          :label="t('views.platformSettings.colDefault')"
          min-width="160"
          show-overflow-tooltip
        />
      </el-table>
    </el-card>

    <el-dialog
      v-model="valueDialogOpen"
      :title="valueDialogTitle"
      width="560px"
      destroy-on-close
      @closed="onValueDialogClosed"
    >
      <el-input
        v-model="valueDialogValue"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 12 }"
        :maxlength="65000"
        show-word-limit
        :placeholder="t('views.platformSettings.valuePh')"
      />
      <template #footer>
        <el-button @click="valueDialogOpen = false">{{ t("views.platformSettings.cancel") }}</el-button>
        <el-button type="primary" :loading="valueDialogSubmitting" @click="confirmValueDialog">{{
          t("views.platformSettings.dlgOk")
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as api from "@/api/platformSettings";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import {
  formatSettingValueKindLabel,
  PLATFORM_SETTING_VALUE_KINDS,
  settingValueKindTagType,
} from "@/utils/settingValueKindLabel";

const { t } = useI18n();

const platformValueKinds = PLATFORM_SETTING_VALUE_KINDS;

const allRows = ref<api.PlatformSettingRow[]>([]);
const loading = ref(false);
const keyword = ref("");
const valueKindFilter = ref("");
const appliedKeyword = ref("");
const appliedValueKind = ref("");
const persistingKey = ref<string | null>(null);

const valueDialogOpen = ref(false);
const valueDialogSubmitting = ref(false);
const valueDialogKey = ref<string | null>(null);
const valueDialogDescription = ref("");
const valueDialogValue = ref("");

const valueDialogTitle = computed(() => {
  const d = valueDialogDescription.value;
  return d ? t("views.platformSettings.dlgModifyPrefix", { desc: d }) : t("views.platformSettings.dlgModify");
});

const filteredRows = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase();
  const kind = appliedValueKind.value.trim();
  return allRows.value.filter((row) => {
    if (kind && row.valueKind !== kind) {
      return false;
    }
    if (!kw) {
      return true;
    }
    const hay = `${row.key} ${row.descriptionZh} ${row.valueText} ${row.defaultValueText}`.toLowerCase();
    return hay.includes(kw);
  });
});

function previewValue(text: string | undefined) {
  const s = text ?? "";
  if (!s) {
    return t("views.platformSettings.emptyValue");
  }
  if (s.length <= 96) {
    return s;
  }
  return `${s.slice(0, 96)}…`;
}

async function load() {
  loading.value = true;
  try {
    allRows.value = await api.fetchPlatformSettings();
  } catch (e: unknown) {
    allRows.value = [];
    ElMessage.error(apiRequestErrorMessage(e, t("views.platformSettings.loadFailed")));
  } finally {
    loading.value = false;
  }
}

function applyFilter() {
  appliedKeyword.value = keyword.value.trim();
  appliedValueKind.value = (valueKindFilter.value ?? "").trim();
}

function reload() {
  void load();
}

async function persistOne(key: string, valueText: string) {
  await api.replacePlatformSettings([{ key, valueText }]);
  ElMessage.success(t("views.platformSettings.saved"));
  await load();
}

async function persistBoolean(row: api.PlatformSettingRow) {
  if (persistingKey.value) {
    return;
  }
  persistingKey.value = row.key;
  try {
    await persistOne(row.key, row.valueText);
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.platformSettings.saveFailed")));
    await load();
  } finally {
    persistingKey.value = null;
  }
}

function openValueDialog(row: api.PlatformSettingRow) {
  if (valueDialogSubmitting.value) {
    return;
  }
  valueDialogKey.value = row.key;
  valueDialogDescription.value = row.descriptionZh ?? row.key;
  valueDialogValue.value = row.valueText ?? "";
  valueDialogOpen.value = true;
}

function onValueDialogClosed() {
  valueDialogKey.value = null;
  valueDialogDescription.value = "";
  valueDialogValue.value = "";
}

async function confirmValueDialog() {
  const key = valueDialogKey.value;
  if (!key) {
    valueDialogOpen.value = false;
    return;
  }
  valueDialogSubmitting.value = true;
  try {
    await persistOne(key, valueDialogValue.value.trim());
    valueDialogOpen.value = false;
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.platformSettings.saveFailed")));
  } finally {
    valueDialogSubmitting.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.platform-settings {
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
  text-underline-offset: 3px;
  word-break: break-all;
}
.string-value-editable:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
  border-radius: 4px;
}
</style>
