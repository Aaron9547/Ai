<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.audit.title") }}</span>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              :placeholder="t('views.audit.placeholderAllTenants')"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="tenantOpt in tenantOptions"
                :key="tenantOpt.id"
                :label="`${tenantOpt.name} (${tenantOpt.code})`"
                :value="tenantOpt.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.audit.refresh") }}</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        max-height="520"
        class="data-table"
        :empty-text="t('views.audit.empty')"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" :label="t('views.audit.colTime')" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colAction')" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelAction(row.action) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colResType')" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelResourceType(row.resourceType) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colResource')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAuditResource(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colActorType')" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelActorType(row.actorType) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colActor')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAuditActor(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colTenant')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colSummary')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ previewJson(row.detailJson) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.audit.colActions')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">{{ t("views.audit.detail") }}</el-button>
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

    <el-dialog v-model="detailOpen" :title="t('views.audit.dlgTitle')" width="640px" destroy-on-close class="detail-dlg">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('views.audit.descRecordId')">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descTime')">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descTenantNc')">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descTenantId')">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descActorType')">{{ labelActorType(detail.actorType) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descActor')">{{ formatAuditActor(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descActorIdRaw')">{{ detail.actorId || emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descAction')">{{ labelAction(detail.action) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descActionRaw')">{{ detail.action || emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descResourceReadable')">{{ formatAuditResource(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descResourceType')">{{ labelResourceType(detail.resourceType) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.audit.descResourceId')">{{ detail.resourceId || emDash }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>{{ t("views.audit.jsonHdr") }}</span>
            <el-button size="small" type="primary" plain @click="copyJson">{{ t("views.audit.copy") }}</el-button>
          </div>
          <el-scrollbar max-height="220px">
            <pre class="json-pre">{{ prettyDetail }}</pre>
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
import type { AuditEventRow } from "../../types/admin";
import { formatAuditActor, formatAuditResource, formatTenantNameCode } from "../../utils/adminListDisplay";

const { t, tm } = useI18n();
const emDash = "\u2014";

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const actionLabels = computed(() => (tm("views.audit.actions") as Record<string, string>) ?? {});
const actorTypeLabels = computed(() => (tm("views.audit.actorTypes") as Record<string, string>) ?? {});
const resourceTypeLabels = computed(() => (tm("views.audit.resourceTypes") as Record<string, string>) ?? {});

function labelAction(a: string | null | undefined): string {
  if (!a) return emDash;
  return actionLabels.value[a] ?? a;
}

function labelActorType(actor: string | null | undefined): string {
  if (!actor) return emDash;
  return actorTypeLabels.value[actor] ?? actor;
}

function labelResourceType(rt: string | null | undefined): string {
  if (!rt) return emDash;
  return resourceTypeLabels.value[rt] ?? rt;
}

const loading = ref(false);
const rows = ref<AuditEventRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);

const detailOpen = ref(false);
const detail = ref<AuditEventRow | null>(null);

const detailJson = computed(() => (detail.value ? JSON.stringify(detail.value, null, 2) : ""));

const prettyDetail = computed(() => {
  if (!detail.value?.detailJson) return emDash;
  try {
    return JSON.stringify(JSON.parse(detail.value.detailJson), null, 2);
  } catch {
    return detail.value.detailJson;
  }
});

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function previewJson(raw: string | null | undefined): string {
  if (!raw) return emDash;
  const s = raw.trim();
  if (s.length <= 80) return s;
  return `${s.slice(0, 80)}…`;
}

async function load() {
  loading.value = true;
  try {
    const data = await admin.fetchAudit(page.value, size.value, listFilterQuery());
    rows.value = data.records ?? [];
    total.value = data.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function onTenantFilterChange() {
  page.value = 1;
  void load();
}

function onSizeChange() {
  page.value = 1;
  void load();
}

function openDetail(row: AuditEventRow) {
  detail.value = row;
  detailOpen.value = true;
}

function onRowClick(row: AuditEventRow) {
  openDetail(row);
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(detailJson.value);
    ElMessage.success(t("views.audit.copied"));
  } catch {
    ElMessage.error(t("views.audit.copyFailed"));
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
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
