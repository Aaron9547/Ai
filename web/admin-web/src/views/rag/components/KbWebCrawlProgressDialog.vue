<template>
  <el-dialog
    v-model="visible"
    :title="t('views.kbMatrix.webCrawlProgressTitle')"
    width="960px"
    destroy-on-close
    @open="onOpen"
    @closed="onClosed"
  >
    <p class="hint">{{ t("views.kbMatrix.webCrawlProgressHint") }}</p>
    <div class="toolbar">
      <el-button :loading="loading" @click="loadAll">{{ t("views.kbMatrix.refresh") }}</el-button>
    </div>

    <div class="section-title">{{ t("views.kbMatrix.webCrawlProgressSites") }}</div>
    <el-table v-loading="loading" :data="sites" border stripe size="small" :empty-text="t('views.kbMatrix.webCrawlSitesEmpty')">
      <el-table-column prop="name" :label="t('views.kbMatrix.webCrawlSitesColName')" min-width="96" />
      <el-table-column prop="baseUrl" :label="t('views.kbMatrix.webCrawlSitesColUrl')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('views.kbMatrix.webCrawlSitesColPreset')" width="100">
        <template #default="{ row }">{{ scheduleDisplay(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.webCrawlSitesColLast')" width="150">
        <template #default="{ row }">{{ formatTime(row.lastCrawlAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="siteJobMeta(row.id)" :type="siteJobMeta(row.id)!.tag" size="small">
            {{ siteJobMeta(row.id)!.label }}
          </el-tag>
          <span v-else class="muted">{{ emDash }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.colActions')" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :loading="runningSiteId === row.id" @click="runSite(row)">
            {{ t("views.kbMatrix.webCrawlSitesRun") }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-title jobs-title">{{ t("views.kbMatrix.webCrawlProgressJobs") }}</div>
    <el-table
      v-loading="loadingJobs"
      :data="crawlJobs"
      border
      stripe
      size="small"
      max-height="360"
      :empty-text="t('views.kbMatrix.webCrawlProgressJobsEmpty')"
    >
      <el-table-column :label="t('views.kbMatrix.colJobType')" width="108" align="center">
        <template #default="{ row }">
          <el-tag type="warning" size="small" effect="plain">{{ jobTaskTypeShort(row.taskType, t) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.colTitleAddr')" min-width="180">
        <template #default="{ row }">
          <div class="cell-title">{{ jobRowTitle(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="96" align="center">
        <template #default="{ row }">
          <el-tag :type="jobStatusMeta(row.status, t).tag" size="small">{{ jobStatusMeta(row.status, t).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.colTime')" width="168">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('views.kbMatrix.resultSummary')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ summarizeJobResult(row.resultJson, t) }}</template>
      </el-table-column>
      <el-table-column type="expand" width="44">
        <template #default="{ row }">
          <div class="expand-inner job-expand">
            <div v-if="parseJobResultMetaRows(row.resultJson, t).length" class="job-kv-block">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item
                  v-for="(r, ri) in parseJobResultMetaRows(row.resultJson, t)"
                  :key="'rm-' + ri"
                  :label="r.label"
                >
                  {{ r.value }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <p v-else class="muted">{{ t("views.kbMatrix.webCrawlProgressNoDetail") }}</p>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as jobApi from "@/api/jobAdmin";
import * as ragApi from "@/api/ragAdmin";
import type { JobTaskAdminRow } from "@/types/admin";
import type { RagWebCrawlSiteRow } from "@/api/ragAdmin";
import {
  jobStatusMeta,
  jobTaskTypeShort,
  parseJobResultMetaRows,
  summarizeJobResult,
} from "@/utils/ragJobDisplay";

const props = defineProps<{ modelValue: boolean; kbId: number }>();
const emit = defineEmits<{ "update:modelValue": [boolean] }>();

const { t } = useI18n();
const emDash = "\u2014";

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const loading = ref(false);
const loadingJobs = ref(false);
const sites = ref<RagWebCrawlSiteRow[]>([]);
const crawlJobs = ref<JobTaskAdminRow[]>([]);
const runningSiteId = ref<number | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

function formatTime(raw?: string | null) {
  if (!raw) return emDash;
  return raw.replace("T", " ").slice(0, 19);
}

function scheduleDisplay(row: RagWebCrawlSiteRow): string {
  if (!row.enabled) {
    return t("views.kbMatrix.webCrawlScheduleNone");
  }
  const label = row.schedulePresetLabel?.trim();
  if (!label) {
    return emDash;
  }
  if (row.runAtTime?.trim()) {
    return `${label} ${row.runAtTime.trim()}`;
  }
  return label;
}

function parsePayload(job: JobTaskAdminRow): { baseUrl?: string; siteId?: number; scheduleId?: number } {
  try {
    return JSON.parse(job.payloadJson || "{}") as { baseUrl?: string; siteId?: number; scheduleId?: number };
  } catch {
    return {};
  }
}

function jobRowTitle(job: JobTaskAdminRow): string {
  const p = parsePayload(job);
  return p.baseUrl?.trim() || `#${job.id}`;
}

function siteJobMeta(siteId: number): { label: string; tag: "success" | "warning" | "info" | "danger" } | null {
  const job = crawlJobs.value.find((j) => {
    const p = parsePayload(j);
    const sid = p.siteId ?? p.scheduleId;
    return sid === siteId;
  });
  if (!job) return null;
  return jobStatusMeta(job.status, t);
}

async function loadSites() {
  sites.value = await ragApi.fetchWebCrawlSites(props.kbId);
}

async function loadJobs() {
  loadingJobs.value = true;
  try {
    const p = await jobApi.fetchJobTasks({ page: 1, size: 50, ragKbId: props.kbId });
    crawlJobs.value = p.records.filter((j) => j.taskType === "RAG_SITE_CRAWL");
  } finally {
    loadingJobs.value = false;
  }
}

async function loadAll() {
  loading.value = true;
  try {
    await Promise.all([loadSites(), loadJobs()]);
  } catch {
    ElMessage.error(t("views.kbMatrix.webCrawlSitesLoadFailed"));
  } finally {
    loading.value = false;
  }
}

function hasActiveJobs() {
  return crawlJobs.value.some((j) => {
    const s = (j.status || "").toUpperCase();
    return s === "PENDING" || s === "RUNNING";
  });
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => {
    if (!visible.value) return;
    void loadJobs().then(() => {
      if (!hasActiveJobs()) stopPolling();
    });
  }, 5000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function onOpen() {
  void loadAll().then(() => {
    if (hasActiveJobs()) startPolling();
  });
}

function onClosed() {
  stopPolling();
  sites.value = [];
  crawlJobs.value = [];
}

async function runSite(row: RagWebCrawlSiteRow) {
  runningSiteId.value = row.id;
  try {
    await ragApi.runWebCrawlSiteNow(props.kbId, row.id);
    ElMessage.success(t("views.kbMatrix.webCrawlSitesRunStarted"));
    await loadAll();
    startPolling();
  } catch {
    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));
  } finally {
    runningSiteId.value = null;
  }
}

onBeforeUnmount(() => stopPolling());
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
.section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.jobs-title {
  margin-top: 20px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.cell-title {
  font-size: 13px;
  word-break: break-all;
}
.job-kv-block {
  padding: 8px 4px;
}
.expand-inner {
  padding: 8px 12px;
}
</style>
