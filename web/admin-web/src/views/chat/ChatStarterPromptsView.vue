<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>{{ t("views.chatStarter.title") }}</h1>
        <p class="hint">{{ t("views.chatStarter.hint") }}</p>
      </div>
      <div class="head-actions">
        <el-button @click="router.push('/system/scheduled-tasks')">{{ t("views.chatStarter.goScheduled") }}</el-button>
        <el-button type="primary" plain :loading="refreshing" @click="onGlobalRefresh">{{ t("common.refresh") }}</el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
      <el-tab-pane lazy :label="t('views.chatStarter.tabEmpty')" name="empty">
        <starter-prompt-pool-table scene="EMPTY" :refresh-token="refreshToken" />
      </el-tab-pane>
      <el-tab-pane lazy :label="t('views.chatStarter.tabFollowUp')" name="followUp">
        <el-alert type="info" :closable="false" show-icon class="follow-hint">
          {{ t("views.chatStarter.followUpHint") }}
        </el-alert>
        <starter-prompt-pool-table scene="FOLLOW_UP" :refresh-token="refreshToken" />
      </el-tab-pane>
      <el-tab-pane lazy :label="t('views.chatStarter.tabWebKnowledge')" name="webKnowledge">
        <web-search-knowledge-table :refresh-token="refreshToken" />
      </el-tab-pane>
      <el-tab-pane lazy :label="t('views.chatStarter.tabDailyHot')" name="dailyHot">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-hdr">
              <span>{{ t("views.chatStarter.dailyBatchTitle") }}</span>
              <el-button type="primary" :loading="refreshing" @click="onRefreshDailyHot">
                {{ t("views.chatStarter.refreshDailyHot") }}
              </el-button>
            </div>
          </template>
          <p class="daily-note">{{ t("views.chatStarter.dailyScheduleNote") }}</p>
          <el-table v-loading="batchLoading" :data="batches" stripe border :empty-text="t('views.chatStarter.batchEmpty')">
            <el-table-column prop="topicDate" :label="t('views.chatStarter.colDate')" width="120" />
            <el-table-column :label="t('views.chatStarter.colStatus')" width="100">
              <template #default="{ row }">
                <el-tag :type="batchStatusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.chatStarter.colQuestions')" min-width="280">
              <template #default="{ row }">
                <span v-if="row.questions?.length">{{ row.questions.join("；") }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" :label="t('views.chatStarter.colError')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="fetchedAt" :label="t('views.chatStarter.colFetched')" width="168" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  listDailyBatches,
  refreshDailyHot,
  type DailyBatchRow,
} from "@/api/chatStarterPrompt";
import StarterPromptPoolTable from "./components/StarterPromptPoolTable.vue";
import WebSearchKnowledgeTable from "./components/WebSearchKnowledgeTable.vue";

const { t } = useI18n();
const router = useRouter();

const activeTab = ref("empty");
const refreshToken = ref(0);
const batchLoading = ref(false);
const refreshing = ref(false);
const batches = ref<DailyBatchRow[]>([]);

function batchStatusType(status: string) {
  if (status === "OK") return "success";
  if (status === "FAILED") return "danger";
  if (status === "PENDING") return "warning";
  return "info";
}

function bumpRefreshToken() {
  refreshToken.value += 1;
}

function onGlobalRefresh() {
  bumpRefreshToken();
  if (activeTab.value === "dailyHot") {
    void loadBatches();
  }
}

async function loadBatches() {
  batchLoading.value = true;
  try {
    batches.value = await listDailyBatches(14);
  } catch {
    ElMessage.error(t("views.chatStarter.loadFailed"));
  } finally {
    batchLoading.value = false;
  }
}

function onTabChange(name: string | number) {
  if (name === "dailyHot" && batches.value.length === 0) {
    void loadBatches();
  }
}

async function onRefreshDailyHot() {
  refreshing.value = true;
  try {
    const res = await refreshDailyHot(true);
    if (res.ok) {
      ElMessage.success(t("views.chatStarter.refreshOk", { n: res.questionCount }));
    } else {
      ElMessage.warning(res.message || t("views.chatStarter.refreshFailed"));
    }
    await loadBatches();
    bumpRefreshToken();
  } catch {
    ElMessage.error(t("views.chatStarter.refreshFailed"));
  } finally {
    refreshing.value = false;
  }
}
</script>

<style scoped>
.page {
  padding: 0;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.head h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-width: 640px;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
.follow-hint {
  margin-bottom: 12px;
}
.panel {
  border-radius: 12px;
}
.panel-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.daily-note {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.muted {
  color: var(--el-text-color-placeholder);
}
</style>
