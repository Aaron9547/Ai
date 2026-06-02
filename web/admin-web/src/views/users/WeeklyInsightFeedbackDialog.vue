<template>
  <el-dialog
    v-model="open"
    :title="t('views.profiles.weeklyFeedbackDlgTitle')"
    width="640px"
    destroy-on-close
    @closed="planDlgOpen = false"
  >
    <el-table v-if="entries.length" :data="entries" border size="small" max-height="400">
      <el-table-column prop="weekStart" :label="t('views.profiles.weeklyFeedbackTableWeek')" width="120" />
      <el-table-column :label="t('views.profiles.weeklyFeedbackTableHelpful')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.helpful ? 'success' : 'info'" size="small">
            {{
              row.helpful
                ? t("views.profiles.weeklyFeedbackHelpfulYes")
                : t("views.profiles.weeklyFeedbackHelpfulNo")
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('views.profiles.weeklyFeedbackTableAt')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatAt(row.at) }}</template>
      </el-table-column>
      <el-table-column :label="t('views.profiles.colActions')" width="96" align="center">
        <template #default="{ row }">
          <el-button
            v-if="userId != null"
            type="primary"
            link
            @click="openPlan(row.weekStart)"
          >
            {{ t("views.profiles.weeklyFeedbackViewPlan") }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :description="t('common.dash')" :image-size="64" />

    <KnowledgePlanetWeeklyInsightDialog
      v-model:open="planDlgOpen"
      :user-id="userId"
      :week-start="planDlgWeekStart"
      :user-label="userLabel ?? ''"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  parseWeeklyInsightFeedbackJson,
  type WeeklyInsightFeedbackEntry,
} from "@/utils/weeklyInsightFeedback";
import { formatBeijingDateTime } from "@/utils/formatBeijingDateTime";
import KnowledgePlanetWeeklyInsightDialog from "./KnowledgePlanetWeeklyInsightDialog.vue";

const open = defineModel<boolean>("open", { default: false });

const props = defineProps<{
  rawJson: string | null;
  userId: number | null;
  userLabel?: string;
}>();

const { t } = useI18n();

const entries = computed((): WeeklyInsightFeedbackEntry[] => parseWeeklyInsightFeedbackJson(props.rawJson));

const planDlgOpen = ref(false);
const planDlgWeekStart = ref<string | null>(null);

function formatAt(at: string): string {
  return formatBeijingDateTime(at, t("common.dash"));
}

function openPlan(weekStart: string): void {
  planDlgWeekStart.value = weekStart;
  planDlgOpen.value = true;
}
</script>
