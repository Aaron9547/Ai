<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.metering.title") }}</span>
          <div class="hdr-actions">
            <el-select
              v-model="listFilterUsageScene"
              class="scene-filter"
              clearable
              :placeholder="t('views.metering.placeholderAllScenes')"
              @change="onSceneFilterChange"
            >
              <el-option
                v-for="code in knownUsageSceneCodes"
                :key="code"
                :label="labelUsageScene(code)"
                :value="code"
              />
            </el-select>
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              :placeholder="t('views.metering.placeholderAllTenants')"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="tenantOpt in tenantOptions"
                :key="tenantOpt.id"
                :label="`${tenantOpt.name} (${tenantOpt.code})`"
                :value="tenantOpt.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading || sceneLoading" @click="reloadAll">{{ t("views.metering.refresh") }}</el-button>
          </div>
        </div>
      </template>

      <p class="panel-tip" v-html="t('views.metering.tip')" />

      <el-card shadow="never" class="scene-panel">
        <template #header>
          <div class="scene-hdr">
            <span class="scene-title">{{ t("views.metering.sceneSummaryTitle", { days: sceneDays }) }}</span>
            <el-button type="primary" link :loading="sceneLoading" @click="loadSceneSummary">
              {{ t("views.metering.sceneSummaryRefresh") }}
            </el-button>
          </div>
        </template>
        <el-table
          v-loading="sceneLoading"
          :data="sceneRows"
          stripe
          border
          size="small"
          class="scene-table"
          :empty-text="t('views.metering.sceneSummaryEmpty')"
        >
          <el-table-column :label="t('views.metering.colScene')" min-width="140">
            <template #default="{ row }">
              {{ labelUsageScene(row.usageScene) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('views.metering.colSceneTokens')" width="140" align="right">
            <template #default="{ row }">
              {{ formatQty(row.totalTokens) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('views.metering.colSceneEvents')" width="120" align="right">
            <template #default="{ row }">
              {{ formatQty(row.eventCount) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('views.metering.colActions')" width="100" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.usageScene && row.usageScene !== '-'"
                link
                type="primary"
                size="small"
                @click="filterByScene(row.usageScene)"
              >
                {{ t("views.metering.filterScene") }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        class="data-table"
        :empty-text="t('views.metering.empty')"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" :label="t('views.metering.colTime')" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.metering.colMeter')" width="168" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelMeterType(row.meterType) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" :label="t('views.metering.colQty')" width="120" align="right">
          <template #default="{ row }">
            {{ formatQty(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" :label="t('views.metering.colUnit')" width="88" align="center">
          <template #default="{ row }">
            {{ labelUnit(row.unit) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.metering.colTenant')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.metering.colUser')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatMeteringUserOrDevice(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" :label="t('views.metering.colDevice')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || emDash }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.metering.colExtra')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ meteringSummary(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.metering.colActions')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">{{ t("views.metering.detail") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailOpen" :title="t('views.metering.dlgTitle')" width="640px" destroy-on-close class="detail-dlg">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('views.metering.descRecordId')">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descTime')">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descTenantNc')">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descTenantId')">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descUserDevice')">{{ formatMeteringUserOrDevice(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descUserId')">{{ detail.userId ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descDevice')">{{ detail.deviceId || emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descMeter')">{{ labelMeterType(detail.meterType) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descMeterRaw')">{{ detail.meterType || emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descQty')">{{ formatQty(detail.quantity) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.metering.descUnit')">{{ labelUnit(detail.unit) }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>{{ t("views.metering.jsonHdr") }}</span>
            <el-button size="small" type="primary" plain @click="copyJson">{{ t("views.metering.copy") }}</el-button>
          </div>
          <el-scrollbar max-height="220px">
            <pre class="json-pre">{{ prettyRef }}</pre>
          </el-scrollbar>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as admin from "../../api/admin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import type { MeteringEventRow, MeteringUsageBySceneRow } from "../../types/admin";
import { formatMeteringUserOrDevice, formatTenantNameCode } from "../../utils/adminListDisplay";

const { t, tm } = useI18n();
const emDash = "\u2014";

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const meterTypeLabels = computed(() => (tm("views.metering.meterTypes") as Record<string, string>) ?? {});
const unitLabels = computed(() => (tm("views.metering.units") as Record<string, string>) ?? {});
const usageSceneLabels = computed(() => (tm("views.metering.usageScenes") as Record<string, string>) ?? {});

const knownUsageSceneCodes = [
  "DAILY_RECOMMEND",
  "HOT_TOPIC_DAILY",
  "KNOWLEDGE_PLANET",
  "MEMORY_ABSTRACT",
  "CHAT",
  "CONVERSATION_DIGEST",
  "LLM_FOLLOW_UP",
] as const;

const sceneDays = 7;
const sceneLoading = ref(false);
const sceneSummary = ref<MeteringUsageBySceneRow[]>([]);
const listFilterUsageScene = ref<string>("");

const sceneRows = computed(() => {
  const byCode = new Map(sceneSummary.value.map((r) => [r.usageScene, r]));
  const rows: MeteringUsageBySceneRow[] = knownUsageSceneCodes.map((code) => {
    const hit = byCode.get(code);
    return hit ?? { usageScene: code, totalTokens: 0, eventCount: 0 };
  });
  const other = byCode.get("-");
  if (other && (other.totalTokens > 0 || other.eventCount > 0)) {
    rows.push(other);
  }
  return rows.sort((a, b) => b.totalTokens - a.totalTokens);
});

function labelUsageScene(code: string | null | undefined): string {
  if (!code || code === "-") return t("views.metering.usageScenes._unknown");
  return usageSceneLabels.value[code] ?? code;
}

function labelMeterType(code: string | null | undefined): string {
  if (!code) return emDash;
  return meterTypeLabels.value[code] ?? code;
}

function labelUnit(u: string | null | undefined): string {
  if (!u) return emDash;
  const mapped = unitLabels.value[u];
  if (mapped) return mapped;
  return u;
}

const loading = ref(false);
const rows = ref<MeteringEventRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);

const detailOpen = ref(false);
const detail = ref<MeteringEventRow | null>(null);

const detailJson = computed(() => (detail.value ? JSON.stringify(detail.value, null, 2) : ""));

const prettyRef = computed(() => {
  if (!detail.value?.refJson) return emDash;
  try {
    return JSON.stringify(JSON.parse(detail.value.refJson), null, 2);
  } catch {
    return detail.value.refJson;
  }
});

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function formatQty(q: number | string | null | undefined): string {
  if (q === null || q === undefined || q === "") return emDash;
  return String(q);
}

function previewJson(raw: string | null | undefined): string {
  if (!raw) return emDash;
  const s = raw.trim();
  if (s.length <= 80) return s;
  return `${s.slice(0, 80)}…`;
}

function meteringSummary(row: MeteringEventRow): string {
  const raw = row.refJson;
  if (!raw?.trim()) return emDash;
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    const parts: string[] = [];
    if (typeof o.modelAlias === "string" && o.modelAlias) {
      parts.push(t("views.metering.summaryModel", { v: o.modelAlias }));
    }
    if (typeof o.durationMs === "number" && o.durationMs > 0) {
      parts.push(t("views.metering.summaryDuration", { v: o.durationMs }));
    }
    if (typeof o.totalTokens === "number") {
      parts.push(t("views.metering.summaryTokens", { v: o.totalTokens }));
    }
    if (typeof o.usageScene === "string" && o.usageScene) {
      parts.push(t("views.metering.summaryScene", { v: labelUsageScene(o.usageScene) }));
    }
    if (parts.length) {
      return parts.join(t("views.metering.summaryJoiner"));
    }
  } catch {
    /* fall through */
  }
  return previewJson(raw);
}

async function loadSceneSummary() {
  sceneLoading.value = true;
  try {
    sceneSummary.value = await admin.fetchMeteringUsageByScene(sceneDays, listFilterQuery());
  } finally {
    sceneLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  try {
    const data = await admin.fetchMetering(page.value, size.value, {
      ...listFilterQuery(),
      usageScene: listFilterUsageScene.value || undefined,
    });
    rows.value = data.records ?? [];
    total.value = data.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function reloadAll() {
  await Promise.all([loadSceneSummary(), load()]);
}

function onTenantFilterChange() {
  page.value = 1;
  void reloadAll();
}

function onSceneFilterChange() {
  page.value = 1;
  void load();
}

function filterByScene(code: string) {
  listFilterUsageScene.value = code;
  page.value = 1;
  void load();
}

function onSizeChange() {
  page.value = 1;
  void load();
}

function openDetail(row: MeteringEventRow) {
  detail.value = row;
  detailOpen.value = true;
}

function onRowClick(row: MeteringEventRow) {
  openDetail(row);
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(detailJson.value);
    ElMessage.success(t("views.metering.copied"));
  } catch {
    ElMessage.error(t("views.metering.copyFailed"));
  }
}

onMounted(() => {
  void reloadAll();
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.panel-tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.scene-panel {
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.scene-panel :deep(.el-card__header) {
  padding: 10px 14px;
}

.scene-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.scene-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.scene-table {
  width: 100%;
}

.scene-filter {
  width: 168px;
}

.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.hdr-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tenant-filter {
  width: 220px;
}

.title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.data-table {
  width: 100%;
}

.data-table :deep(.el-table__row) {
  cursor: pointer;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.json-block {
  margin-top: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.json-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.json-pre {
  margin: 0;
  padding: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
