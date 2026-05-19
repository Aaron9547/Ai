<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.scheduledTasks.title") }}</span>
            <p class="sub">{{ t("views.scheduledTasks.sub") }}</p>
          </div>
          <div class="actions">
            <el-select
              v-model="filterExecutor"
              clearable
              :placeholder="t('views.scheduledTasks.filterExecutor')"
              style="width: 220px"
              @change="load"
            >
              <el-option :label="t('views.scheduledTasks.filterAll')" value="" />
              <el-option v-for="o in meta.executors" :key="o.code" :label="o.label" :value="o.code" />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.scheduledTasks.refresh") }}</el-button>
            <el-button type="primary" @click="openCreate">{{ t("views.scheduledTasks.new") }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.scheduledTasks.empty')">
        <el-table-column :label="t('views.scheduledTasks.colExecutor')" width="180">
          <template #default="{ row }">{{ row.executorLabel }}</template>
        </el-table-column>
        <el-table-column prop="name" :label="t('views.scheduledTasks.colName')" min-width="120" />
        <el-table-column prop="cronExpression" :label="t('views.scheduledTasks.colCron')" min-width="140" />
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
        <el-table-column :label="t('views.scheduledTasks.colActions')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="runNow(row)">{{ t("views.scheduledTasks.runNow") }}</el-button>
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
        <el-form-item :label="t('views.scheduledTasks.labelExecutor')" required>
          <el-select v-model="form.executorCode" :disabled="editId != null" style="width: 100%">
            <el-option v-for="o in meta.executors" :key="o.code" :label="o.label" :value="o.code" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('views.scheduledTasks.labelName')" required>
          <el-input v-model="form.name" maxlength="128" />
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
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as stApi from "@/api/scheduledTasksAdmin";
import type { ScheduledTaskMeta, ScheduledTaskRow } from "@/api/scheduledTasksAdmin";

const { t } = useI18n();

const loading = ref(false);
const saving = ref(false);
const rows = ref<ScheduledTaskRow[]>([]);
const meta = ref<ScheduledTaskMeta>({ executors: [] });
const filterExecutor = ref("");

const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive({
  executorCode: "RAG_WEB_CRAWL_DISPATCH",
  name: "",
  cronExpression: "0 0 3 * * *",
  enabled: true,
});

function formatTime(raw?: string | null) {
  if (!raw) return "—";
  return raw.replace("T", " ").slice(0, 19);
}

async function loadMeta() {
  meta.value = await stApi.fetchScheduledTaskMeta();
  if (!form.executorCode && meta.value.executors[0]) {
    form.executorCode = meta.value.executors[0].code;
  }
}

async function load() {
  loading.value = true;
  try {
    rows.value = await stApi.fetchScheduledTasks(filterExecutor.value || undefined);
  } catch {
    ElMessage.error(t("views.scheduledTasks.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  editId.value = null;
  form.executorCode = meta.value.executors[0]?.code ?? "RAG_WEB_CRAWL_DISPATCH";
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
  if (!name || !form.executorCode || !cron) {
    ElMessage.warning(t("views.scheduledTasks.formRequired"));
    return;
  }
  saving.value = true;
  try {
    const body = {
      executorCode: form.executorCode,
      name,
      cronExpression: cron,
      enabled: form.enabled,
    };
    if (editId.value != null) {
      await stApi.updateScheduledTask(editId.value, body);
    } else {
      await stApi.createScheduledTask(body);
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
  try {
    await stApi.runScheduledTaskNow(row.id);
    ElMessage.success(t("views.scheduledTasks.runStarted"));
    await load();
  } catch {
    ElMessage.error(t("views.scheduledTasks.saveFailed"));
  }
}

async function remove(row: ScheduledTaskRow) {
  try {
    await ElMessageBox.confirm(t("views.scheduledTasks.deleteConfirm", { name: row.name }), { type: "warning" });
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
  max-width: 560px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.cron-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
