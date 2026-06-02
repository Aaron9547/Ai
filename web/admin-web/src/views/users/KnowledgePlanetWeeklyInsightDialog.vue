<template>
  <el-dialog
    v-model="open"
    :title="dlgTitle"
    width="720px"
    destroy-on-close
    class="kp-weekly-insight-dlg"
    @closed="onClosed"
  >
    <div v-loading="loading">
      <KnowledgeWeeklyPlanPreview v-if="detail?.plan" :plan="detail.plan" :meta="previewMeta" />
      <el-empty v-else-if="!loading && loadFailed" :description="loadError" :image-size="80" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  fetchKnowledgePlanetWeeklyInsight,
  type WeeklyInsightDetail,
} from "@/api/knowledgePlanetWeeklyFeedback";
import KnowledgeWeeklyPlanPreview from "@/components/knowledgePlanet/KnowledgeWeeklyPlanPreview.vue";

const open = defineModel<boolean>("open", { default: false });

const props = defineProps<{
  userId: number | null;
  weekStart: string | null;
  userLabel?: string;
}>();

const { t } = useI18n();

const loading = ref(false);
const loadFailed = ref(false);
const loadError = ref("");
const detail = ref<WeeklyInsightDetail | null>(null);

const dlgTitle = computed(() => {
  const name = (props.userLabel ?? "").trim();
  const week = props.weekStart ?? "";
  if (name && week) {
    return t("views.kpWeeklyFeedback.planDlgTitle", { name, week });
  }
  return t("views.kpWeeklyFeedback.planDlgTitleShort");
});

const previewMeta = computed(() => {
  const d = detail.value;
  if (!d) return undefined;
  return {
    weekStart: d.weekStart,
    status: d.status,
    computedAt: d.computedAt,
  };
});

async function load(): Promise<void> {
  const uid = props.userId;
  const week = props.weekStart?.trim();
  if (uid == null || !week) {
    detail.value = null;
    return;
  }
  loading.value = true;
  loadFailed.value = false;
  loadError.value = "";
  detail.value = null;
  try {
    detail.value = await fetchKnowledgePlanetWeeklyInsight(uid, week);
  } catch {
    loadFailed.value = true;
    loadError.value = t("views.kpWeeklyFeedback.planLoadFailed");
  } finally {
    loading.value = false;
  }
}

function onClosed(): void {
  detail.value = null;
  loadFailed.value = false;
  loadError.value = "";
}

watch(
  () => [open.value, props.userId, props.weekStart] as const,
  ([isOpen]) => {
    if (isOpen) void load();
  },
);
</script>

<style scoped>
.kp-weekly-insight-dlg :deep(.el-dialog__body) {
  padding-top: 8px;
  padding-bottom: 16px;
}
</style>
