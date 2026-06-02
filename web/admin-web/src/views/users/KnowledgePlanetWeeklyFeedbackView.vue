<template>
  <div class="kp-weekly-feedback-admin">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      :title="t('views.kpWeeklyFeedback.alertTitle')"
      :description="t('views.kpWeeklyFeedback.alertDesc')"
    />

    <el-row :gutter="12" class="stats">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ t("views.kpWeeklyFeedback.statTotal") }}</div>
          <div class="stat-value">{{ total }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card stat-card--yes">
          <div class="stat-label">{{ t("views.kpWeeklyFeedback.statHelpful") }}</div>
          <div class="stat-value">{{ helpfulCount }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card stat-card--no">
          <div class="stat-label">{{ t("views.kpWeeklyFeedback.statNotHelpful") }}</div>
          <div class="stat-value">{{ notHelpfulCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="hdr-row">
          <span class="hdr">{{ t("views.kpWeeklyFeedback.listTitle") }}</span>
          <div class="filters">
            <el-input
              v-model="keyword"
              clearable
              :placeholder="t('views.kpWeeklyFeedback.keywordPh')"
              style="width: 220px"
              @keyup.enter="reload"
            />
            <el-button type="primary" plain :loading="loading" @click="reload">{{ t("views.kpWeeklyFeedback.query") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.kpWeeklyFeedback.empty')">
        <el-table-column prop="loginName" :label="t('views.kpWeeklyFeedback.colLoginName')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="displayName" :label="t('views.kpWeeklyFeedback.colNickname')" min-width="100" />
        <el-table-column prop="weekStart" :label="t('views.kpWeeklyFeedback.colWeek')" width="120" />
        <el-table-column :label="t('views.kpWeeklyFeedback.colHelpful')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.helpful ? 'success' : 'info'" size="small">
              {{ row.helpful ? t("views.kpWeeklyFeedback.yes") : t("views.kpWeeklyFeedback.no") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kpWeeklyFeedback.colAt')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatAt(row.at) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.kpWeeklyFeedback.colActions')" width="180" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openWeeklyPlan(row)">{{ t("views.kpWeeklyFeedback.viewPlan") }}</el-button>
            <el-button type="primary" link @click="goProfile(row.userId)">{{ t("views.kpWeeklyFeedback.viewProfile") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </el-card>

    <KnowledgePlanetWeeklyInsightDialog
      v-model:open="planDlgOpen"
      :user-id="planDlgUserId"
      :week-start="planDlgWeekStart"
      :user-label="planDlgUserLabel"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  listKnowledgePlanetWeeklyFeedback,
  type WeeklyFeedbackEntry,
} from "@/api/knowledgePlanetWeeklyFeedback";
import { formatBeijingDateTime } from "@/utils/formatBeijingDateTime";
import KnowledgePlanetWeeklyInsightDialog from "./KnowledgePlanetWeeklyInsightDialog.vue";

const { t } = useI18n();
const router = useRouter();

const loading = ref(false);
const keyword = ref("");
const page = ref(1);
const pageSize = 20;
const total = ref(0);
const helpfulCount = ref(0);
const notHelpfulCount = ref(0);
const rows = ref<WeeklyFeedbackEntry[]>([]);

const planDlgOpen = ref(false);
const planDlgUserId = ref<number | null>(null);
const planDlgWeekStart = ref<string | null>(null);
const planDlgUserLabel = ref("");

function openWeeklyPlan(row: WeeklyFeedbackEntry): void {
  planDlgUserId.value = row.userId;
  planDlgWeekStart.value = row.weekStart;
  const name = (row.displayName || row.loginName || "").trim();
  planDlgUserLabel.value = name || String(row.userId);
  planDlgOpen.value = true;
}

function formatAt(at: string | null): string {
  return formatBeijingDateTime(at, t("common.dash"));
}

async function reload(): Promise<void> {
  loading.value = true;
  try {
    const res = await listKnowledgePlanetWeeklyFeedback({
      page: page.value,
      size: pageSize,
      keyword: keyword.value.trim() || undefined,
    });
    rows.value = res.records ?? [];
    total.value = res.total ?? 0;
    helpfulCount.value = res.helpfulCount ?? 0;
    notHelpfulCount.value = res.notHelpfulCount ?? 0;
  } finally {
    loading.value = false;
  }
}

function onPage(p: number): void {
  page.value = p;
  void reload();
}

function goProfile(userId: number): void {
  void router.push({ path: "/users/profiles", query: { userId: String(userId) } });
}

onMounted(() => {
  void reload();
});
</script>

<style scoped>
.kp-weekly-feedback-admin .hint {
  margin-bottom: 16px;
}
.stats {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  margin-bottom: 8px;
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
}
.stat-card--yes .stat-value {
  color: var(--el-color-success);
}
.stat-card--no .stat-value {
  color: var(--el-text-color-secondary);
}
.card {
  margin-top: 0;
}
.hdr-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.filters {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
