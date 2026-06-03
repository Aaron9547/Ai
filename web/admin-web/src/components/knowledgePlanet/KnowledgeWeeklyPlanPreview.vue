<template>
  <el-scrollbar v-if="plan" class="kp-plan-scroll admin-el-scrollbar" max-height="min(68vh, 520px)">
    <div class="kp-plan-preview">
    <el-descriptions v-if="meta" :column="2" border size="small" class="kp-plan-meta">
      <el-descriptions-item v-if="meta.weekStart" :label="t('views.kpWeeklyFeedback.planWeek')">
        {{ meta.weekStart }}
      </el-descriptions-item>
      <el-descriptions-item v-if="meta.status" :label="t('views.kpWeeklyFeedback.planStatus')">
        {{ meta.status }}
      </el-descriptions-item>
      <el-descriptions-item v-if="meta.computedAt" :label="t('views.kpWeeklyFeedback.planComputedAt')" :span="2">
        {{ formatBeijingDateTime(meta.computedAt) }}
      </el-descriptions-item>
    </el-descriptions>

    <section v-if="plan.summary" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planSummary") }}</h4>
      <p>{{ plan.summary }}</p>
    </section>
    <section v-if="plan.inferredPersona" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planPersona") }}</h4>
      <p>{{ plan.inferredPersona }}</p>
    </section>
    <section v-if="plan.progressNotes" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planProgress") }}</h4>
      <p>{{ plan.progressNotes }}</p>
    </section>
    <section v-if="plan.thinkDirections?.length" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planThink") }}</h4>
      <ul>
        <li v-for="(item, i) in plan.thinkDirections" :key="'t' + i">{{ item }}</li>
      </ul>
    </section>
    <section v-if="plan.gapAreas?.length" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planGaps") }}</h4>
      <ul>
        <li v-for="(item, i) in plan.gapAreas" :key="'g' + i">{{ item }}</li>
      </ul>
    </section>
    <section v-if="plan.bookRecommendations?.length" class="kp-plan-sec">
      <h4>{{ t("views.kpWeeklyFeedback.planBooks") }}</h4>
      <ul class="kp-plan-books">
        <li v-for="(book, i) in plan.bookRecommendations" :key="'b' + i">
          <el-link
            v-if="book.url"
            :href="book.url"
            type="primary"
            target="_blank"
            rel="noopener noreferrer"
            >{{ book.title }}</el-link
          >
          <span v-else>{{ book.title }}</span>
          <span v-if="book.reason" class="kp-plan-book-reason"> — {{ book.reason }}</span>
        </li>
      </ul>
    </section>
    <el-empty
      v-if="!hasContent"
      :description="t('views.kpWeeklyFeedback.planEmpty')"
      :image-size="64"
    />
    </div>
  </el-scrollbar>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { KnowledgeWeeklyPlan } from "@/api/knowledgePlanetWeeklyFeedback";
import { formatBeijingDateTime } from "@/utils/formatBeijingDateTime";

const props = defineProps<{
  plan: KnowledgeWeeklyPlan | null;
  meta?: { weekStart?: string; status?: string; computedAt?: string | null };
}>();

const { t } = useI18n();

const hasContent = computed(() => {
  const p = props.plan;
  if (!p) return false;
  return !!(
    p.summary?.trim() ||
    p.inferredPersona?.trim() ||
    p.progressNotes?.trim() ||
    (p.thinkDirections?.length ?? 0) > 0 ||
    (p.gapAreas?.length ?? 0) > 0 ||
    (p.bookRecommendations?.length ?? 0) > 0
  );
});
</script>

<style scoped>
.kp-plan-scroll {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.kp-plan-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.kp-plan-scroll :deep(.el-scrollbar__view) {
  padding: 16px 18px 18px;
}

.kp-plan-preview {
  min-height: 0;
}

.kp-plan-meta {
  margin-bottom: 12px;
}
.kp-plan-sec {
  margin-bottom: 14px;
}
.kp-plan-sec h4 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.kp-plan-sec p,
.kp-plan-sec li {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}
.kp-plan-sec ul {
  margin: 0;
  padding-left: 1.2em;
}
.kp-plan-sec li + li {
  margin-top: 4px;
}
.kp-plan-books {
  list-style: none;
  padding: 0;
}
.kp-plan-books li {
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.kp-plan-books li:last-child {
  border-bottom: none;
}
.kp-plan-book-reason {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
