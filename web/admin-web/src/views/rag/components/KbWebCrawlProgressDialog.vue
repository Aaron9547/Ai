<template>
  <KbDataPanelDialog
    v-model="visible"
    :title="t('views.kbMatrix.webCrawlProgressTitle')"
    :subtitle="t('views.kbMatrix.webCrawlProgressHint')"
    @closed="onClosed"
  >
    <template #toolbar>
      <el-button type="primary" plain :loading="loading" @click="loadAll">{{ t("views.kbMatrix.refresh") }}</el-button>
      <span v-if="jobsTotal > 0" class="toolbar-stat">
        {{ t("views.kbMatrix.webCrawlProgressJobs") }}
        <strong>{{ jobsTotal }}</strong>
      </span>
    </template>

    <template #default="{ tableHeight }">
      <div class="panel-stack">
        <el-tabs v-model="activeTab" class="kb-panel-tabs" @tab-change="onTabChange">
        <el-tab-pane :label="t('views.kbMatrix.webCrawlProgressSites')" name="sites">
          <el-table
            v-loading="loading"
            :data="sites"
            :height="tableHeight"
            class="kb-panel-table"
            size="small"
            :empty-text="t('views.kbMatrix.webCrawlSitesEmpty')"
          >
            <el-table-column prop="name" :label="t('views.kbMatrix.webCrawlSitesColName')" width="100" show-overflow-tooltip />
            <el-table-column prop="baseUrl" :label="t('views.kbMatrix.webCrawlSitesColUrl')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="t('views.kbMatrix.webCrawlSitesColPreset')" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ scheduleDisplay(row) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.webCrawlSitesColLast')" width="140">
              <template #default="{ row }">{{ formatTime(row.lastCrawlAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="88" align="center">
              <template #default="{ row }">
                <el-tag v-if="siteJobMeta(row.id)" :type="siteJobMeta(row.id)!.tag" size="small" effect="light" round>
                  {{ siteJobMeta(row.id)!.label }}
                </el-tag>
                <span v-else class="muted">{{ emDash }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colActions')" width="148" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :loading="runningSiteId === row.id" @click="runSite(row)">
                  {{ t("views.kbMatrix.webCrawlSitesRun") }}
                </el-button>
                <el-button link type="danger" size="small" @click="confirmDeleteSite(row)">
                  {{ t("views.kbMatrix.webCrawlSitesDelete") }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="jobsTabLabel" name="jobs">
          <el-table
            v-loading="loadingJobs"
            :data="jobsPageRows"
            :height="tableHeight"
            class="kb-panel-table"
            size="small"
            highlight-current-row
            :empty-text="t('views.kbMatrix.webCrawlProgressJobsEmpty')"
            @row-click="onJobRowClick"
          >
            <el-table-column :label="t('views.kbMatrix.colJobType')" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ jobTaskTypeShort(row.taskType, t) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colTitleAddr')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="cell-title">{{ jobRowTitle(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="84" align="center">
              <template #default="{ row }">
                <el-tag :type="jobStatusMeta(row.status, t).tag" size="small" effect="light" round>
                  {{ jobStatusMeta(row.status, t).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colUpdated')" width="140">
              <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.resultSummary')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="summary-cell">{{ summarizeJobResult(row.resultJson, t) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colActions')" width="64" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openJobDetail(row)">
                  {{ t("views.kbAsync.detail") }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        </el-tabs>
        <div v-if="activeTab === 'jobs' && jobsTotal > jobsPageSize" class="panel-stack__pager">
          <el-pagination
            v-model:current-page="jobsPage"
            layout="total, prev, pager, next"
            :total="jobsTotal"
            :page-size="jobsPageSize"
            small
            background
          />
        </div>
      </div>
    </template>
  </KbDataPanelDialog>

  <KbJobTaskDetailDrawer
    v-model="jobDetailOpen"
    :job="jobDetailRow"
    :run-detail-slice="jobDetailRunSlice"
    :resuming="resumingRunId != null"
    :retrying="retryingRunId != null"
    @resume-pending="resumePendingRun"
    @retry-failed="retryFailedRun"
    @closed="onJobDetailClosed"
  />
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as jobApi from "@/api/jobAdmin";
import * as ragApi from "@/api/ragAdmin";
import type { JobTaskAdminRow } from "@/types/admin";
import type { CrawlRunDetailView, RagWebCrawlSiteRow } from "@/api/ragAdmin";
import {
  jobStatusMeta,
  jobTaskTypeShort,
  summarizeJobResult,
  type SiteCrawlRunDetailSlice,
} from "@/utils/ragJobDisplay";
import KbDataPanelDialog from "./KbDataPanelDialog.vue";
import KbJobTaskDetailDrawer from "./KbJobTaskDetailDrawer.vue";

const props = withDefaults(
  defineProps<{ modelValue: boolean; kbId: number; defaultTab?: "sites" | "jobs" }>(),
  { defaultTab: "jobs" },
);
const emit = defineEmits<{ "update:modelValue": [boolean] }>();

const { t } = useI18n();
const emDash = "\u2014";

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const activeTab = ref<"sites" | "jobs">("jobs");
const loading = ref(false);
const loadingJobs = ref(false);
const sites = ref<RagWebCrawlSiteRow[]>([]);
const allCrawlJobs = ref<JobTaskAdminRow[]>([]);
const jobsPage = ref(1);
const jobsPageSize = 15;
const runDetailsByRunId = ref<Map<number, CrawlRunDetailView>>(new Map());
const loadingRunDetailId = ref<number | null>(null);
const resumingRunId = ref<number | null>(null);
const runningSiteId = ref<number | null>(null);
const retryingRunId = ref<number | null>(null);
const jobDetailOpen = ref(false);
const jobDetailRow = ref<JobTaskAdminRow | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const jobsTotal = computed(() => allCrawlJobs.value.length);

const jobsPageRows = computed(() => {
  const start = (jobsPage.value - 1) * jobsPageSize;
  return allCrawlJobs.value.slice(start, start + jobsPageSize);
});

const jobsTabLabel = computed(() => {
  const base = t("views.kbMatrix.webCrawlProgressJobs");
  const n = jobsTotal.value;
  return n > 0 ? `${base} (${n})` : String(base);
});

const jobDetailRunSlice = computed((): SiteCrawlRunDetailSlice | null => {
  const job = jobDetailRow.value;
  if (!job) return null;
  const runId = parseRunId(job.resultJson);
  if (runId == null) return null;
  const d = runDetailsByRunId.value.get(runId);
  if (!d) return null;
  return {
    ok: d.ok,
    skipped: d.skipped,
    fail: d.fail,
    failedByCode: d.failedByCode,
    queueByStatus: d.queueByStatus,
  };
});

function parseRunId(resultJson: string | null | undefined): number | null {
  if (!resultJson?.trim()) return null;
  try {
    const o = JSON.parse(resultJson) as { runId?: number };
    return typeof o.runId === "number" ? o.runId : null;
  } catch {
    return null;
  }
}

async function ensureRunDetailLoaded(runId: number, force = false) {
  if (!force && runDetailsByRunId.value.has(runId)) return;
  loadingRunDetailId.value = runId;
  try {
    const detail = await ragApi.fetchCrawlRun(props.kbId, runId);
    const next = new Map(runDetailsByRunId.value);
    next.set(runId, detail);
    runDetailsByRunId.value = next;
  } catch {
    /* drawer still shows summary from result_json */
  } finally {
    if (loadingRunDetailId.value === runId) {
      loadingRunDetailId.value = null;
    }
  }
}

function isTerminalJobStatus(status: string | null | undefined): boolean {
  const s = (status || "").toUpperCase();
  return s === "SUCCEEDED" || s === "FAILED";
}

async function syncOpenJobDetailAfterRefresh() {
  if (!jobDetailOpen.value || jobDetailRow.value == null) return;
  const jobId = jobDetailRow.value.id;
  const updated = allCrawlJobs.value.find((j) => j.id === jobId);
  if (!updated) return;
  jobDetailRow.value = updated;
  const runId = parseRunId(updated.resultJson);
  if (runId == null) return;
  const force = isTerminalJobStatus(updated.status);
  await ensureRunDetailLoaded(runId, force);
}

function onJobRowClick(row: JobTaskAdminRow, _col: unknown, event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  if (target?.closest("button, a, .el-button, .el-link")) return;
  openJobDetail(row);
}

function openJobDetail(row: JobTaskAdminRow) {
  jobDetailRow.value = row;
  jobDetailOpen.value = true;
  const runId = parseRunId(row.resultJson);
  if (runId != null) {
    void ensureRunDetailLoaded(runId, isTerminalJobStatus(row.status));
  }
}

function onJobDetailClosed() {
  jobDetailRow.value = null;
}

function onTabChange() {
  /* tab 切换后表体高度由 KbDataPanelDialog ResizeObserver 自动重算 */
}

watch(visible, (isOpen) => {
  if (isOpen) {
    activeTab.value = props.defaultTab;
    jobsPage.value = 1;
    void loadAll().then(() => {
      if (hasActiveJobs()) startPolling();
    });
  } else {
    stopPolling();
  }
});

async function resumePendingRun(runId: number) {
  resumingRunId.value = runId;
  try {
    const res = await ragApi.resumeCrawlRunPending(props.kbId, runId);
    ElMessage.success(res.summary || t("views.kbMatrix.webCrawlRetryFailedOk"));
    runDetailsByRunId.value = new Map();
    await ensureRunDetailLoaded(runId);
    await loadJobs();
  } catch {
    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));
  } finally {
    resumingRunId.value = null;
  }
}

async function retryFailedRun(runId: number) {
  retryingRunId.value = runId;
  try {
    const res = await ragApi.retryCrawlRunFailed(props.kbId, runId);
    ElMessage.success(res.summary || t("views.kbMatrix.webCrawlRetryFailedOk"));
    runDetailsByRunId.value = new Map();
    await ensureRunDetailLoaded(runId);
    await loadJobs();
  } catch {
    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));
  } finally {
    retryingRunId.value = null;
  }
}

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
  if ((job.taskType || "").toUpperCase() === "RAG_INDEX") {
    return String(t("views.kbAsync.jobTypes.RAG_INDEX"));
  }
  const p = parsePayload(job);
  return p.baseUrl?.trim() || `#${job.id}`;
}

function siteJobMeta(siteId: number): { label: string; tag: "success" | "warning" | "info" | "danger" } | null {
  const job = allCrawlJobs.value.find((j) => {
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

async function loadJobs(silent = false) {
  if (!silent) {
    loadingJobs.value = true;
  }
  try {
    const p = await jobApi.fetchJobTasks({ page: 1, size: 100, ragKbId: props.kbId });
    const ragTypes = new Set(["RAG_SITE_CRAWL", "RAG_INDEX", "RAG_URL_IMPORT", "RAG_FILE_IMPORT"]);
    allCrawlJobs.value = p.records.filter((j) => ragTypes.has((j.taskType || "").toUpperCase()));
    const maxPage = Math.max(1, Math.ceil(allCrawlJobs.value.length / jobsPageSize));
    if (jobsPage.value > maxPage) {
      jobsPage.value = maxPage;
    }
  } finally {
    if (!silent) {
      loadingJobs.value = false;
    }
  }
}

async function loadAll(silent = false) {
  if (!silent) {
    loading.value = true;
  }
  try {
    await Promise.all([loadSites(), loadJobs(silent)]);
  } catch {
    if (!silent) {
      ElMessage.error(t("views.kbMatrix.webCrawlSitesLoadFailed"));
    }
  } finally {
    if (!silent) {
      loading.value = false;
    }
  }
}

function hasActiveJobs() {
  return allCrawlJobs.value.some((j) => {
    const s = (j.status || "").toUpperCase();
    return s === "PENDING" || s === "RUNNING";
  });
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => {
    if (!visible.value) return;
    void loadJobs(true)
      .then(() => syncOpenJobDetailAfterRefresh())
      .then(() => {
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

function onClosed() {
  stopPolling();
  sites.value = [];
  allCrawlJobs.value = [];
  runDetailsByRunId.value = new Map();
  loadingRunDetailId.value = null;
  jobsPage.value = 1;
  activeTab.value = props.defaultTab;
}

async function confirmDeleteSite(row: RagWebCrawlSiteRow) {
  try {
    const choice = await ElMessageBox.confirm(
      t("views.kbMatrix.webCrawlDeletePrompt", { name: row.name }),
      t("views.kbMatrix.webCrawlSitesDelete"),
      {
        distinguishCancelAndClose: true,
        confirmButtonText: t("views.kbMatrix.webCrawlDeleteWithDocs"),
        cancelButtonText: t("views.kbMatrix.webCrawlDeleteConfigOnly"),
        type: "warning",
      },
    );
    const purgeDocuments = choice === "confirm";
    await deleteSite(row, purgeDocuments);
  } catch (action) {
    if (action === "cancel") {
      await deleteSite(row, false);
    }
  }
}

async function deleteSite(row: RagWebCrawlSiteRow, purgeDocuments: boolean) {
  try {
    const res = await ragApi.deleteWebCrawlSite(props.kbId, row.id, purgeDocuments);
    const msg = purgeDocuments
      ? t("views.kbMatrix.webCrawlDeleteDoneWithDocs", {
          docs: res.documentsPurged,
          runs: res.crawlRunsRemoved,
        })
      : t("views.kbMatrix.webCrawlDeleteDoneConfig");
    ElMessage.success(msg);
    runDetailsByRunId.value = new Map();
    await loadAll();
  } catch {
    ElMessage.error(t("views.kbMatrix.webCrawlSitesSaveFailed"));
  }
}

async function runSite(row: RagWebCrawlSiteRow) {
  runningSiteId.value = row.id;
  try {
    await ragApi.runWebCrawlSiteNow(props.kbId, row.id);
    ElMessage.success(t("views.kbMatrix.webCrawlSitesRunStarted"));
    activeTab.value = "jobs";
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
.panel-stack {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-stack__pager {
  flex-shrink: 0;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-extra-light);
  display: flex;
  justify-content: flex-end;
}

.kb-panel-tabs {
  flex: 1;
  min-height: 0;
}

.toolbar-stat {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.toolbar-stat strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.kb-panel-tabs {
  display: flex;
  flex-direction: column;
}

.kb-panel-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  flex-shrink: 0;
}

.kb-panel-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
}

.kb-panel-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cell-title {
  font-size: 12px;
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