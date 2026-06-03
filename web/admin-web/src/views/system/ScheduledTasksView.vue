<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.scheduledTasks.title") }}</span>
            <p class="sub">{{ tabSub }}</p>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeCategory" class="task-tabs" @tab-change="onTabChange">
        <el-tab-pane
          :label="t('views.scheduledTasks.tabTenant')"
          name="TENANT_CRON"
        />
        <el-tab-pane
          :label="t('views.scheduledTasks.tabChatReminder')"
          name="CHAT_USER_REMINDER"
        />
      </el-tabs>

      <div class="toolbar">
        <el-select
          v-if="isTenantTab"
          v-model="filterExecutor"
          clearable
          :placeholder="t('views.scheduledTasks.filterExecutor')"
          style="width: 220px"
          @change="load"
        >
          <el-option :label="t('views.scheduledTasks.filterAll')" value="" />
          <el-option
            v-for="o in tenantExecutors"
            :key="o.code"
            :label="o.label"
            :value="o.code"
          />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.scheduledTasks.refresh") }}</el-button>
        <el-button v-if="isTenantTab" type="primary" @click="openCreate">{{ t("views.scheduledTasks.new") }}</el-button>
      </div>

      <el-alert
        v-if="!isTenantTab"
        type="info"
        :closable="false"
        show-icon
        class="reminder-hint"
      >
        {{ t("views.scheduledTasks.chatReminderHint") }}
      </el-alert>

      <el-alert
        v-if="runningRows.length > 0"
        type="info"
        :closable="false"
        show-icon
        class="running-banner"
      >
        <span>{{ t("views.scheduledTasks.runningBanner", { n: runningRows.length }) }}</span>
        <el-button link type="primary" size="small" @click="openProgress(runningRows[0]!)">
          {{ t("views.scheduledTasks.viewProgress") }}
        </el-button>
      </el-alert>

      <el-table v-loading="loading" :data="rows" stripe border :empty-text="emptyText">
        <el-table-column v-if="isTenantTab" :label="t('views.scheduledTasks.colExecutor')" width="200">
          <template #default="{ row }">{{ executorLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="name" :label="t('views.scheduledTasks.colName')" min-width="140" />
        <el-table-column prop="cronExpression" :label="t('views.scheduledTasks.colCron')" min-width="140" />
        <el-table-column :label="t('views.scheduledTasks.colRunStatus')" min-width="200">
          <template #default="{ row }">
            <template v-if="isRunning(row)">
              <el-tag type="warning" size="small">{{ t("views.scheduledTasks.statusRunning") }}</el-tag>
              <span v-if="rowProgressHint(row)" class="run-hint">{{ rowProgressHint(row) }}</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.scheduledTasks.colEnabled')" width="72" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? t("common.yes") : t("common.no") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.scheduledTasks.colLastExec')" width="168">
          <template #default="{ row }">{{ formatTime(row.lastExecAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.scheduledTasks.colNextExec')" width="168">
          <template #default="{ row }">{{ formatTime(row.nextExecAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.scheduledTasks.colActions')" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :disabled="isRunning(row)"
              :loading="runSubmittingId === row.id"
              @click="runNow(row)"
            >
              {{ t("views.scheduledTasks.runNow") }}
            </el-button>
            <el-button v-if="isRunning(row)" link type="primary" size="small" @click="openProgress(row)">
              {{ t("views.scheduledTasks.viewProgress") }}
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.scheduledTasks.edit") }}</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">{{ t("views.scheduledTasks.delete") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dlg"
      :title="editId ? t('views.scheduledTasks.dlgEdit') : t('views.scheduledTasks.dlgNew')"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="120px">
        <el-form-item v-if="isTenantTab && !editId" :label="t('views.scheduledTasks.labelExecutor')" required>
          <el-select v-model="form.executorCode" style="width: 100%">
            <el-option v-for="o in tenantExecutors" :key="o.code" :label="o.label" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="editId" :label="t('views.scheduledTasks.labelExecutor')">
          <span class="executor-readonly">{{ executorLabelForCode(form.executorCode) }}</span>
        </el-form-item>
        <el-form-item :label="t('views.scheduledTasks.labelName')" required>
          <el-input v-model="form.name" maxlength="128" :disabled="!isTenantTab && !editId" />
        </el-form-item>
        <el-form-item :label="t('views.scheduledTasks.labelCron')" required>
          <el-input v-model="form.cronExpression" placeholder="0 0 3 * * *" />
          <p class="cron-hint">{{ t("views.scheduledTasks.cronHint") }}</p>
        </el-form-item>
        <el-form-item :label="t('views.scheduledTasks.labelEnabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ t("views.scheduledTasks.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("views.scheduledTasks.save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="progressDlg"
      :title="t('views.scheduledTasks.progressTitle', { name: progressTaskName })"
      width="520px"
      @closed="onProgressDlgClosed"
    >
      <div v-if="progressRun">
        <p class="progress-status">
          <el-tag :type="progressStatusTag(progressRun.status)" size="small">{{ progressRun.status }}</el-tag>
        </p>
        <p v-if="progressParsed?.message" class="progress-msg">{{ progressParsed.message }}</p>
        <p v-if="progressParsed?.stage" class="progress-stage">{{ progressParsed.stage }}</p>
        <el-progress
          v-if="progressParsed?.percent != null"
          :percentage="progressParsed.percent"
          :stroke-width="10"
          style="margin-top: 12px"
        />
        <p v-if="progressParsed?.current != null && progressParsed?.total != null" class="progress-count">
          {{ progressParsed.current }} / {{ progressParsed.total }}
        </p>
        <p v-if="progressRun.errorMessage" class="progress-err">{{ progressRun.errorMessage }}</p>
      </div>
      <template #footer>
        <el-button @click="progressDlg = false">{{ t("views.scheduledTasks.close") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as stApi from "@/api/scheduledTasksAdmin";
import { confirmMessageBox } from "@/utils/messageBoxI18n";
import type {
  ScheduledRunDetail,
  ScheduledTaskCategoryCode,
  ScheduledTaskMeta,
  ScheduledTaskRow,
  TaskProgress,
} from "@/api/scheduledTasksAdmin";

const { t } = useI18n();

const activeCategory = ref<ScheduledTaskCategoryCode>("TENANT_CRON");
const loading = ref(false);
const saving = ref(false);
const rows = ref<ScheduledTaskRow[]>([]);
const meta = ref<ScheduledTaskMeta>({ categories: [], executors: [] });
const filterExecutor = ref("");
const runSubmittingId = ref<number | null>(null);

const isTenantTab = computed(() => activeCategory.value === "TENANT_CRON");

const tenantExecutors = computed(() =>
  meta.value.executors.filter((e) => e.taskCategory === "TENANT_CRON" || !e.taskCategory),
);

const tabSub = computed(() =>
  isTenantTab.value ? t("views.scheduledTasks.subTenant") : t("views.scheduledTasks.subChatReminder"),
);

const emptyText = computed(() =>
  isTenantTab.value ? t("views.scheduledTasks.emptyTenant") : t("views.scheduledTasks.emptyChatReminder"),
);

const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive({
  executorCode: "RAG_WEB_CRAWL_DISPATCH",
  name: "",
  cronExpression: "0 0 3 * * *",
  enabled: true,
});

const progressDlg = ref(false);
const progressTaskId = ref<number | null>(null);
const progressTaskName = ref("");
const progressRun = ref<ScheduledRunDetail | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;
let listPollTimer: ReturnType<typeof setInterval> | null = null;

const PROGRESS_LS_KEY = "AI_ADMIN_SCHEDULED_TASK_PROGRESS";

const runningRows = computed(() => rows.value.filter(isRunning));

const progressParsed = computed<TaskProgress | null>(() =>
  stApi.parseTaskProgress(progressRun.value?.progressJson),
);

function rowProgressHint(row: ScheduledTaskRow): string | null {
  const p = stApi.parseTaskProgress(row.activeRun?.progressJson);
  if (!p) return null;
  if (p.message?.trim()) return p.message.trim();
  if (p.percent != null) return `${p.percent}%`;
  if (p.current != null && p.total != null) return `${p.current}/${p.total}`;
  if (p.stage?.trim()) return p.stage.trim();
  return null;
}

function runSummaryToDetail(row: ScheduledTaskRow): ScheduledRunDetail | null {
  const ar = row.activeRun;
  if (!ar) return null;
  return {
    id: ar.runId,
    registrationId: row.id,
    executorCode: row.executorCode,
    executorLabel: row.executorLabel,
    status: ar.status,
    triggerType: "",
    progressJson: ar.progressJson ?? null,
    childJobTaskIdsJson: null,
    errorMessage: null,
    startedAt: null,
    finishedAt: null,
  };
}

function persistProgressWatch() {
  if (progressTaskId.value == null) return;
  try {
    sessionStorage.setItem(
      PROGRESS_LS_KEY,
      JSON.stringify({
        taskId: progressTaskId.value,
        taskName: progressTaskName.value,
        category: activeCategory.value,
        runId: progressRun.value?.id ?? null,
      }),
    );
  } catch {
    /* ignore quota */
  }
}

function clearProgressWatch() {
  try {
    sessionStorage.removeItem(PROGRESS_LS_KEY);
  } catch {
    /* ignore */
  }
}

async function tryRestoreProgressWatch() {
  let saved: { taskId?: number; taskName?: string; category?: ScheduledTaskCategoryCode } | null = null;
  try {
    const raw = sessionStorage.getItem(PROGRESS_LS_KEY);
    if (raw) saved = JSON.parse(raw) as { taskId?: number; taskName?: string; category?: ScheduledTaskCategoryCode };
  } catch {
    clearProgressWatch();
    return;
  }
  if (saved?.taskId == null) return;
  if (saved.category && saved.category !== activeCategory.value) {
    activeCategory.value = saved.category;
    await load();
  }
  const row = rows.value.find((r) => r.id === saved!.taskId);
  if (row && isRunning(row)) {
    openProgress(row, true);
    return;
  }
  clearProgressWatch();
}

function formatTime(raw?: string | null) {
  if (!raw) return "—";
  return raw.replace("T", " ").slice(0, 19);
}

function executorLabel(row: ScheduledTaskRow) {
  if (row.executorLabel?.trim()) return row.executorLabel;
  return executorLabelForCode(row.executorCode);
}

function executorLabelForCode(code: string) {
  const hit = meta.value.executors.find((e) => e.code === code);
  return hit?.label || code || "—";
}

function isRunning(row: ScheduledTaskRow) {
  const st = row.activeRun?.status;
  return st === "PENDING" || st === "RUNNING";
}

function progressStatusTag(status: string) {
  if (status === "SUCCEEDED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "RUNNING") return "warning";
  return "info";
}

async function loadMeta() {
  meta.value = await stApi.fetchScheduledTaskMeta();
  if (!form.executorCode && tenantExecutors.value[0]) {
    form.executorCode = tenantExecutors.value[0].code;
  }
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    rows.value = await stApi.fetchScheduledTasks({
      taskCategory: activeCategory.value,
      executorCode: isTenantTab.value && filterExecutor.value ? filterExecutor.value : undefined,
    });
    syncListPoll();
    patchProgressFromListRow();
  } catch {
    if (!silent) ElMessage.error(t("views.scheduledTasks.loadFailed"));
  } finally {
    if (!silent) loading.value = false;
  }
}

function onTabChange() {
  filterExecutor.value = "";
  void load();
}

function patchProgressFromListRow() {
  if (!progressDlg.value || progressTaskId.value == null || !progressRun.value) return;
  const row = rows.value.find((r) => r.id === progressTaskId.value);
  if (!row?.activeRun) return;
  progressRun.value = {
    ...progressRun.value,
    status: row.activeRun.status,
    progressJson: row.activeRun.progressJson ?? progressRun.value.progressJson,
  };
}

function syncListPoll() {
  stopListPoll();
  if (rows.value.some(isRunning)) {
    listPollTimer = setInterval(() => {
      void load(true);
    }, 3000);
  }
}

function stopListPoll() {
  if (listPollTimer != null) {
    clearInterval(listPollTimer);
    listPollTimer = null;
  }
}

function resetForm() {
  editId.value = null;
  form.executorCode = tenantExecutors.value[0]?.code ?? "RAG_WEB_CRAWL_DISPATCH";
  form.name = "";
  form.cronExpression = "0 0 3 * * *";
  form.enabled = true;
}

function openCreate() {
  resetForm();
  dlg.value = true;
}

function openEdit(row: ScheduledTaskRow) {
  editId.value = row.id;
  form.executorCode = row.executorCode;
  form.name = row.name;
  form.cronExpression = row.cronExpression;
  form.enabled = row.enabled;
  dlg.value = true;
}

async function save() {
  const name = form.name.trim();
  const cron = form.cronExpression.trim();
  if (!name || !cron) {
    ElMessage.warning(t("views.scheduledTasks.formRequired"));
    return;
  }
  if (!editId.value && isTenantTab.value && !form.executorCode) {
    ElMessage.warning(t("views.scheduledTasks.formRequired"));
    return;
  }
  saving.value = true;
  try {
    if (editId.value != null) {
      await stApi.updateScheduledTask(editId.value, {
        name,
        cronExpression: cron,
        enabled: form.enabled,
      });
    } else {
      await stApi.createScheduledTask({
        executorCode: form.executorCode,
        name,
        cronExpression: cron,
        enabled: form.enabled,
      });
    }
    dlg.value = false;
    ElMessage.success(t("views.scheduledTasks.saved"));
    await load();
  } catch {
    ElMessage.error(t("views.scheduledTasks.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function runNow(row: ScheduledTaskRow) {
  if (isRunning(row)) {
    openProgress(row);
    return;
  }
  runSubmittingId.value = row.id;
  try {
    const res = await stApi.runScheduledTaskNow(row.id);
    if (res.duplicate) {
      ElMessage.info(t("views.scheduledTasks.runDuplicate"));
    } else {
      ElMessage.success(t("views.scheduledTasks.runQueued"));
    }
    await load();
    progressTaskId.value = row.id;
    progressTaskName.value = row.name;
    progressRun.value = res.run;
    progressDlg.value = true;
    persistProgressWatch();
    startPoll();
  } catch {
    ElMessage.error(t("views.scheduledTasks.saveFailed"));
  } finally {
    runSubmittingId.value = null;
  }
}

function openProgress(row: ScheduledTaskRow, _fromRestore = false) {
  progressTaskId.value = row.id;
  progressTaskName.value = row.name;
  progressRun.value = runSummaryToDetail(row) ?? progressRun.value;
  progressDlg.value = true;
  persistProgressWatch();
  void refreshProgress();
  startPoll();
}

async function refreshProgress() {
  if (progressTaskId.value == null) return;
  try {
    const run = await stApi.fetchActiveScheduledRun(progressTaskId.value);
    if (run) {
      progressRun.value = run;
    } else if (progressRun.value?.id) {
      progressRun.value = await stApi.fetchScheduledRun(progressRun.value.id);
    }
    const st = progressRun.value?.status;
    if (st === "SUCCEEDED" || st === "FAILED") {
      stopPoll();
      clearProgressWatch();
      await load();
    } else {
      persistProgressWatch();
    }
  } catch {
    /* ignore poll errors */
  }
}

function onProgressDlgClosed() {
  stopPoll();
  const id = progressTaskId.value;
  const row = id != null ? rows.value.find((r) => r.id === id) : undefined;
  if (!row || !isRunning(row)) {
    clearProgressWatch();
  }
}

function startPoll() {
  stopPoll();
  pollTimer = setInterval(() => {
    void refreshProgress();
  }, 2000);
}

function stopPoll() {
  if (pollTimer != null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function remove(row: ScheduledTaskRow) {
  try {
    await confirmMessageBox(t, t("views.scheduledTasks.deleteConfirm", { name: row.name }), { type: "warning" });
    await stApi.deleteScheduledTask(row.id);
    ElMessage.success(t("views.scheduledTasks.deleted"));
    await load();
  } catch {
    /* cancel */
  }
}

onMounted(async () => {
  await loadMeta();
  await load();
  await tryRestoreProgressWatch();
});

onUnmounted(() => {
  stopPoll();
  stopListPoll();
});
</script>

<style scoped>
.page {
  padding: 0;
}
.panel {
  border-radius: 12px;
}
.hdr {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-width: 640px;
}
.task-tabs {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.reminder-hint {
  margin-bottom: 12px;
}
.executor-readonly {
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.cron-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.muted {
  color: var(--el-text-color-placeholder);
}
.running-banner {
  margin-bottom: 12px;
}
.run-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.progress-status {
  margin: 0 0 8px;
}
.progress-msg {
  margin: 0 0 4px;
  font-size: 15px;
}
.progress-stage {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.progress-count {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.progress-err {
  margin: 12px 0 0;
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
