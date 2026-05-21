<template>
  <KbDataPanelDialog
    :model-value="modelValue"
    :title="t('views.kbAsync.title', { name: kbName })"
    :subtitle="t('views.kbAsync.sub')"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <template #toolbar>
      <el-select
        v-model="taskType"
        clearable
        :placeholder="t('views.kbAsync.typePh')"
        class="toolbar-select"
        @change="onFilterChange"
      >
        <el-option :label="t('views.kbAsync.typeRagIndex')" value="RAG_INDEX" />
        <el-option :label="t('views.kbAsync.typeUrlImport')" value="RAG_URL_IMPORT" />
        <el-option :label="t('views.kbAsync.typeFileImport')" value="RAG_FILE_IMPORT" />
        <el-option :label="t('views.kbAsync.typeSiteCrawl')" value="RAG_SITE_CRAWL" />
      </el-select>
      <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.kbMatrix.refresh") }}</el-button>
    </template>

    <template #default="{ tableHeight }">
      <el-table
        v-loading="loading"
        :data="rows"
        :height="tableHeight"
        class="kb-panel-table"
        size="small"
        highlight-current-row
        :empty-text="t('views.kbAsync.empty')"
        @row-click="onRowClick"
      >
        <el-table-column :label="t('views.kbMatrix.colJobType')" width="112" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="type-pill">{{ jobTaskTypeShort(row.taskType, t) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="jobStatusMeta(row.status, t).tag" size="small" effect="light" round>
              {{ jobStatusMeta(row.status, t).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colUpdated')" width="152">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.resultSummary')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="summary-cell">{{ summarizeJobResult(row.resultJson, t) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colActions')" width="72" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">
              {{ t("views.kbAsync.detail") }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[15, 30, 50]"
        small
        background
        @current-change="load"
        @size-change="onSizeChange"
      />
    </template>
  </KbDataPanelDialog>

  <KbJobTaskDetailDrawer v-model="detailOpen" :job="detail" @closed="detail = null" />
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as jobApi from "@/api/jobAdmin";
import type { JobTaskAdminRow } from "@/types/admin";
import {
  jobStatusMeta,
  jobTaskTypeShort,
  summarizeJobResult,
} from "@/utils/ragJobDisplay";
import KbDataPanelDialog from "./KbDataPanelDialog.vue";
import KbJobTaskDetailDrawer from "./KbJobTaskDetailDrawer.vue";

const { t } = useI18n();

const props = defineProps<{
  modelValue: boolean;
  kbId: number;
  kbName: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();

const loading = ref(false);
const rows = ref<JobTaskAdminRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(15);
const taskType = ref<string | undefined>(undefined);

const detailOpen = ref(false);
const detail = ref<JobTaskAdminRow | null>(null);

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.kbId > 0) {
      page.value = 1;
      void load();
    }
  },
);

function onClosed() {
  detailOpen.value = false;
  detail.value = null;
  rows.value = [];
  total.value = 0;
}

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

async function load() {
  if (!props.kbId) return;
  loading.value = true;
  try {
    const p = await jobApi.fetchJobTasks({
      page: page.value,
      size: size.value,
      taskType: taskType.value,
      ragKbId: props.kbId,
    });
    rows.value = p.records;
    total.value = p.total;
  } finally {
    loading.value = false;
  }
}

function onSizeChange() {
  page.value = 1;
  void load();
}

function onFilterChange() {
  page.value = 1;
  void load();
}

function onRowClick(row: JobTaskAdminRow) {
  openDetail(row);
}

function openDetail(row: JobTaskAdminRow) {
  detail.value = row;
  detailOpen.value = true;
}
</script>

<style scoped>
.toolbar-select {
  width: 200px;
}

.type-pill {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.summary-cell {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

:deep(.kb-panel-table) {
  width: 100%;
}

:deep(.kb-panel-table .el-table__inner-wrapper::before) {
  display: none;
}

:deep(.kb-panel-table th.el-table__cell) {
  background: var(--el-fill-color-light);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

:deep(.kb-panel-table .el-table__row) {
  cursor: pointer;
}

:deep(.kb-panel-table .el-table__row:hover > td.el-table__cell) {
  background-color: var(--el-fill-color-light) !important;
}
</style>
