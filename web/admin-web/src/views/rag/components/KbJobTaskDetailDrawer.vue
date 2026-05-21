<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="min(480px, 92vw)"
    destroy-on-close
    class="kb-job-detail-drawer"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <el-scrollbar v-if="job" class="kb-job-detail-drawer__scroll">
      <div class="kb-job-detail-drawer__inner">
        <div class="status-row">
          <el-tag :type="jobStatusMeta(job.status, t).tag" size="small">{{ jobStatusMeta(job.status, t).label }}</el-tag>
          <span class="status-row__time">{{ formatTime(job.updatedAt || job.createdAt) }}</span>
        </div>

        <section v-if="crawlOutcome" class="outcome-card">
          <p :class="['outcome-card__headline', `outcome-card__headline--${crawlOutcome.tone}`]">
            {{ crawlOutcome.headline }}
          </p>
          <div class="stat-chips">
            <span class="stat-chip stat-chip--ok">
              {{ t("views.kbMatrix.crawlOutcome.statOk") }}<strong>{{ crawlOutcome.ok }}</strong>
            </span>
            <span class="stat-chip stat-chip--skip">
              {{ t("views.kbMatrix.crawlOutcome.statSkipped") }}<strong>{{ crawlOutcome.skipped }}</strong>
            </span>
            <span class="stat-chip stat-chip--fail">
              {{ t("views.kbMatrix.crawlOutcome.statFail") }}<strong>{{ crawlOutcome.fail }}</strong>
            </span>
          </div>
          <p v-if="crawlOutcome.discoveredNote" class="outcome-note">{{ crawlOutcome.discoveredNote }}</p>
          <p v-if="crawlOutcome.skippedNote" class="outcome-note muted">{{ crawlOutcome.skippedNote }}</p>
          <ul v-if="crawlOutcome.failures.length" class="failure-list">
            <li v-for="(f, fi) in crawlOutcome.failures" :key="fi">
              {{ f.label }}<span class="failure-count">{{ f.count }}</span>
            </li>
          </ul>
          <p v-if="crawlOutcome.pendingNote" class="outcome-note warn">{{ crawlOutcome.pendingNote }}</p>
          <div v-if="crawlOutcome.showResumePending || crawlOutcome.showRetryFailed" class="outcome-actions">
            <el-button
              v-if="crawlOutcome.showResumePending"
              size="small"
              type="primary"
              :loading="resuming"
              @click="emit('resume-pending', crawlOutcome.runId!)"
            >
              {{ t("views.kbMatrix.webCrawlRunResumePending") }}
            </el-button>
            <el-button
              v-if="crawlOutcome.showRetryFailed"
              size="small"
              type="warning"
              plain
              :loading="retrying"
              @click="emit('retry-failed', crawlOutcome.runId!)"
            >
              {{ t("views.kbMatrix.webCrawlRetryFailed") }}
            </el-button>
          </div>
        </section>

        <section v-else-if="resultMeta.length" class="meta-card">
          <h4 class="block-title">{{ t("views.kbAsync.secResult") }}</h4>
          <dl class="meta-dl">
            <div v-for="(r, i) in resultMeta" :key="i" class="meta-row">
              <dt>{{ r.label }}</dt>
              <dd>{{ r.value }}</dd>
            </div>
          </dl>
        </section>

        <section v-if="resultSteps.length" class="timeline-card">
          <h4 class="block-title">{{ t("views.kbAsync.secSteps") }}</h4>
          <el-timeline>
            <el-timeline-item
              v-for="(s, i) in resultSteps"
              :key="i"
              :timestamp="formatTime(s.at)"
              placement="top"
            >
              <strong>{{ jobStepPhaseLabel(s.phase, t) }}</strong>
              <span class="step-st"> · {{ jobStepStatusLabel(s.status, t) }}</span>
              <div v-if="humanizeJobStepDetail(s.detail, t)" class="step-detail">
                {{ humanizeJobStepDetail(s.detail, t) }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </section>

        <section v-if="payloadRows.length" class="meta-card">
          <h4 class="block-title">{{ t("views.kbAsync.secPayload") }}</h4>
          <dl class="meta-dl">
            <div v-for="(r, i) in payloadRows" :key="'p-' + i" class="meta-row">
              <dt>{{ r.label }}</dt>
              <dd>{{ r.value }}</dd>
            </div>
          </dl>
        </section>

        <el-collapse class="raw-collapse">
          <el-collapse-item :title="t('views.kbAsync.rawPayload')" name="payload">
            <pre class="json-pre">{{ prettyJson(job.payloadJson) }}</pre>
          </el-collapse-item>
          <el-collapse-item :title="t('views.kbAsync.rawResult')" name="result">
            <pre class="json-pre">{{ prettyJson(job.resultJson) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-scrollbar>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { JobTaskAdminRow } from "@/types/admin";
import type { SiteCrawlRunDetailSlice } from "@/utils/ragJobDisplay";
import {
  buildSiteCrawlOutcomeView,
  humanizeJobStepDetail,
  jobStatusMeta,
  jobStepPhaseLabel,
  jobStepStatusLabel,
  jobTaskTypeLabel,
  parseJobPayloadRows,
  parseJobResultMetaRows,
  parseResultSteps,
  prettyJson,
} from "@/utils/ragJobDisplay";

const props = defineProps<{
  modelValue: boolean;
  job: JobTaskAdminRow | null;
  runDetailSlice?: SiteCrawlRunDetailSlice | null;
  resuming?: boolean;
  retrying?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [boolean];
  closed: [];
  "resume-pending": [runId: number];
  "retry-failed": [runId: number];
}>();

const { t } = useI18n();

const resultSteps = computed(() => parseResultSteps(props.job?.resultJson));
const resultMeta = computed(() => parseJobResultMetaRows(props.job?.resultJson, t));
const payloadRows = computed(() => parseJobPayloadRows(props.job?.payloadJson, t));
const crawlOutcome = computed(() => {
  if (!props.job || (props.job.taskType || "").toUpperCase() !== "RAG_SITE_CRAWL") {
    return null;
  }
  return buildSiteCrawlOutcomeView(props.job.resultJson, props.runDetailSlice ?? null, t);
});

const drawerTitle = computed(() => {
  if (!props.job) return "";
  return jobTaskTypeLabel(props.job.taskType, t);
});

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}
</script>

<style scoped>
.kb-job-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.kb-job-detail-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.kb-job-detail-drawer__scroll {
  height: 100%;
}

.kb-job-detail-drawer__inner {
  padding: 16px 20px 24px;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.status-row__time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.block-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.outcome-card,
.meta-card,
.timeline-card {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
}

.outcome-card__headline {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
}

.outcome-card__headline--success {
  color: var(--el-color-success);
}
.outcome-card__headline--warning {
  color: var(--el-color-warning);
}
.outcome-card__headline--danger {
  color: var(--el-color-danger);
}
.outcome-card__headline--info {
  color: var(--el-text-color-primary);
}

.stat-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stat-chip {
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 12px;
}

.stat-chip strong {
  margin-left: 4px;
  font-weight: 700;
}

.stat-chip--ok {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.stat-chip--skip {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
.stat-chip--fail {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.outcome-note {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.5;
}

.outcome-note.warn {
  color: var(--el-color-warning);
}

.outcome-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.failure-list {
  margin: 10px 0 0;
  padding-left: 18px;
  font-size: 13px;
}

.failure-count {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
}

.failure-count::before {
  content: "×";
}

.meta-dl {
  margin: 0;
}

.meta-row {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 6px 12px;
  padding: 7px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  font-size: 13px;
}

.meta-row:last-child {
  border-bottom: none;
}

.meta-row dt {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.meta-row dd {
  margin: 0;
  word-break: break-word;
}

.step-st {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.step-detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.raw-collapse {
  margin-top: 8px;
}

.json-pre {
  margin: 0;
  padding: 10px;
  font-size: 11px;
  line-height: 1.4;
  background: var(--el-fill-color-darker);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 160px;
  overflow: auto;
}

.muted {
  color: var(--el-text-color-secondary);
}
</style>
