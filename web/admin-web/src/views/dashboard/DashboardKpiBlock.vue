<template>
  <div class="dash-kpi-block">
    <section class="dash-sec">
      <h2 class="dash-sec-title">{{ t("views.dashboard.sectionCore") }}</h2>
      <el-row :gutter="18" class="dash-row">
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="indigo">
            <div class="kpi-icon">
              <el-icon :size="24"><User /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiActiveMembers") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vActiveMembers)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="sky">
            <div class="kpi-icon">
              <el-icon :size="24"><ChatDotRound /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiConversations") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vConversations)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="violet">
            <div class="kpi-icon">
              <el-icon :size="24"><Histogram /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiMessages") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vMessages)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="amber">
            <div class="kpi-icon">
              <el-icon :size="24"><Cpu /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiModels") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vModelActive)) }}
                <span class="kpi-suffix">/ {{ formatInt(Math.round(vModelTotal)) }}</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="emerald">
            <div class="kpi-icon">
              <el-icon :size="24"><Clock /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiJobsQueue") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vJobsQueue)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="rose">
            <div class="kpi-icon">
              <el-icon :size="24"><TrendCharts /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiJobs7d") }}</div>
              <div class="kpi-value">
                {{ formatInt(Math.round(vJobs7d)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="slate">
            <div class="kpi-icon">
              <el-icon :size="24"><TrendCharts /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiTenantTokenTotal") }}</div>
              <div class="kpi-value">
                {{ formatQty(vTenantTokenTotal) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="blue">
            <div class="kpi-icon">
              <el-icon :size="24"><Histogram /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiTenantPromptTokens") }}</div>
              <div class="kpi-value">
                {{ formatQty(vTenantPromptTokens) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi" data-tone="cyan">
            <div class="kpi-icon">
              <el-icon :size="24"><Histogram /></el-icon>
            </div>
            <div class="kpi-body">
              <div class="kpi-label">{{ t("views.dashboard.kpiTenantCompletionTokens") }}</div>
              <div class="kpi-value">
                {{ formatQty(vTenantCompletionTokens) }}
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </section>

    <section class="dash-sec">
      <h2 class="dash-sec-title">{{ t("views.dashboard.section24h") }}</h2>
      <el-row :gutter="18" class="dash-row">
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi kpi--compact" data-tone="indigo">
            <div class="kpi-body kpi-body--solo">
              <div class="kpi-label">{{ t("views.dashboard.kpiHttp24h") }}</div>
              <div class="kpi-value kpi-value--sm">
                {{ formatInt(Math.round(vHttp24)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi kpi--compact" data-tone="violet">
            <div class="kpi-body kpi-body--solo">
              <div class="kpi-label">{{ t("views.dashboard.kpiMetering24h") }}</div>
              <div class="kpi-value kpi-value--sm">
                {{ formatInt(Math.round(vMeterEvents)) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi kpi--compact" data-tone="sky">
            <div class="kpi-body kpi-body--solo">
              <div class="kpi-label">{{ t("views.dashboard.kpiPromptTokens24h") }}</div>
              <div class="kpi-value kpi-value--sm">
                {{ formatQty(vPrompt24) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi kpi--compact" data-tone="cyan">
            <div class="kpi-body kpi-body--solo">
              <div class="kpi-label">{{ t("views.dashboard.kpiCompletionTokens24h") }}</div>
              <div class="kpi-value kpi-value--sm">
                {{ formatQty(vCompletion24) }}
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="8" :lg="8">
          <div class="kpi kpi--compact" data-tone="amber">
            <div class="kpi-body kpi-body--solo">
              <div class="kpi-label">{{ t("views.dashboard.kpiAudit24h") }}</div>
              <div class="kpi-value kpi-value--sm">
                {{ formatInt(Math.round(vAudit24)) }}
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ChatDotRound, Clock, Cpu, Histogram, TrendCharts, User } from "@element-plus/icons-vue";
import { useTransition } from "@vueuse/core";
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { AdminDashboardSummary } from "@/api/adminDashboard";

const props = defineProps<{
  kpi: AdminDashboardSummary["kpi"];
  recent24h: AdminDashboardSummary["recent24h"];
}>();

const { t } = useI18n();

const TRANSITION_MS = 1150;

function useMetricTransition(getter: () => number) {
  const source = ref(0);
  const output = useTransition(source, { duration: TRANSITION_MS });
  watch(getter, (v) => {
    source.value = Number.isFinite(v) ? v : 0;
  }, { immediate: true });
  return output;
}

const vActiveMembers = useMetricTransition(() => props.kpi.activeMemberCount);
const vConversations = useMetricTransition(() => props.kpi.conversationCount);
const vMessages = useMetricTransition(() => props.kpi.chatMessageCount);
const vModelActive = useMetricTransition(() => props.kpi.llmModelActive);
const vModelTotal = useMetricTransition(() => props.kpi.llmModelTotal);
const vJobsQueue = useMetricTransition(() => props.kpi.jobTaskPendingOrRunning);
const vJobs7d = useMetricTransition(() => props.kpi.jobTasksCreatedLast7d);
const vTenantTokenTotal = useMetricTransition(() => props.kpi.tenantTokenTotal);
const vTenantPromptTokens = useMetricTransition(() => props.kpi.tenantPromptTokens);
const vTenantCompletionTokens = useMetricTransition(() => props.kpi.tenantCompletionTokens);
const vHttp24 = useMetricTransition(() => props.recent24h.httpAccessCount);
const vMeterEvents = useMetricTransition(() => props.recent24h.meteringEventCount);
const vPrompt24 = useMetricTransition(() => props.recent24h.promptTokens24h);
const vCompletion24 = useMetricTransition(() => props.recent24h.completionTokens24h);
const vAudit24 = useMetricTransition(() => props.recent24h.auditEventCount);

function formatInt(n: number): string {
  if (!Number.isFinite(n)) return "—";
  return String(Math.trunc(n));
}

function formatQty(n: number): string {
  if (!Number.isFinite(n)) return "—";
  return n >= 1000 ? n.toFixed(0) : n.toFixed(2);
}
</script>

<style scoped>
.dash-sec {
  margin-bottom: 22px;
}

.dash-sec-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  letter-spacing: 0.04em;
}

.dash-row {
  width: 100%;
}

.kpi {
  --kpi-accent: var(--el-color-primary);
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
  min-height: 120px;
  padding: 20px 20px 22px;
  border-radius: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.kpi:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--kpi-accent) 28%, var(--el-border-color-lighter));
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06), 0 20px 48px rgba(15, 23, 42, 0.1);
}

.kpi[data-tone="indigo"] {
  --kpi-accent: #4f46e5;
}
.kpi[data-tone="sky"] {
  --kpi-accent: #0284c7;
}
.kpi[data-tone="violet"] {
  --kpi-accent: #6d28d9;
}
.kpi[data-tone="amber"] {
  --kpi-accent: #d97706;
}
.kpi[data-tone="emerald"] {
  --kpi-accent: #059669;
}
.kpi[data-tone="slate"] {
  --kpi-accent: #475569;
}
.kpi[data-tone="cyan"] {
  --kpi-accent: #0891b2;
}
.kpi[data-tone="rose"] {
  --kpi-accent: #e11d48;
}
.kpi[data-tone="blue"] {
  --kpi-accent: #2563eb;
}

.kpi-icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--kpi-accent);
  background: color-mix(in srgb, var(--kpi-accent) 12%, var(--el-bg-color));
}

.kpi-body {
  min-width: 0;
  flex: 1;
}

.kpi-body--solo {
  width: 100%;
}

.kpi-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-bottom: 10px;
  min-height: 2.8em;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  overflow: hidden;
  word-break: break-word;
}

.kpi-value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
  letter-spacing: -0.03em;
  line-height: 1.15;
}

.kpi-value--sm {
  font-size: 22px;
}

.kpi-suffix {
  margin-left: 8px;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-placeholder);
}

.kpi--compact {
  padding: 18px 20px 20px;
  min-height: 104px;
  align-items: center;
}

.kpi--compact .kpi-label {
  margin-bottom: 6px;
}
</style>

<style>
html.dark .dash-page .dash-kpi-block .kpi {
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.35), 0 8px 24px rgba(0, 0, 0, 0.25);
}

html.dark .dash-page .dash-kpi-block .kpi:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(255, 255, 255, 0.08);
}
</style>
