<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.accessLogs.title") }}</span>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              :placeholder="t('views.accessLogs.placeholderAllTenants')"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="tenant in tenantOptions"
                :key="tenant.id"
                :label="`${tenant.name} (${tenant.code})`"
                :value="tenant.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">{{
              t("views.accessLogs.refresh")
            }}</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        max-height="520"
        class="log-table"
        :empty-text="t('views.accessLogs.empty')"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" :label="t('views.accessLogs.colTime')" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="method" :label="t('views.accessLogs.colMethod')" width="86" />
        <el-table-column prop="pathPattern" :label="t('views.accessLogs.colPath')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="httpStatus" :label="t('views.accessLogs.colStatus')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.httpStatus)" size="small">{{ row.httpStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" :label="t('views.accessLogs.colDuration')" width="102" align="right" />
        <el-table-column :label="t('views.accessLogs.colTenant')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.accessLogs.colUser')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAccessLogUser(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" :label="t('views.accessLogs.colDevice')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || t("common.dash") }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" :label="t('views.accessLogs.colClientIp')" width="130" show-overflow-tooltip />
        <el-table-column :label="t('views.accessLogs.colActions')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">{{
              t("views.accessLogs.detail")
            }}</el-button>
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

    <el-dialog
      v-model="detailOpen"
      :title="t('views.accessLogs.dlgTitle')"
      width="640px"
      destroy-on-close
      class="detail-dlg"
    >
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('views.accessLogs.descRecordId')">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descTime')">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descHttpMethod')">{{ detail.method }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descPathPattern')">{{ detail.pathPattern }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descStatus')">{{ detail.httpStatus }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descDuration')">{{ detail.durationMs }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descTenantNameCode')">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descTenantId')">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descUserDisplay')">{{ formatAccessLogUser(detail) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descUserId')">{{ detail.userId ?? t("common.dash") }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descDevice')">{{ detail.deviceId || t("common.dash") }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descTrace')">{{ detail.traceId || t("common.dash") }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descClientIp')">{{ detail.clientIp || t("common.dash") }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.accessLogs.descUa')">
            <el-scrollbar max-height="120px">
              <div class="ua">{{ detail.userAgent || t("common.dash") }}</div>
            </el-scrollbar>
          </el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>{{ t("views.accessLogs.jsonHdr") }}</span>
            <el-button size="small" type="primary" plain @click="copyJson">{{ t("views.accessLogs.copy") }}</el-button>
          </div>
          <el-scrollbar max-height="220px">
            <pre class="json-pre">{{ detailJson }}</pre>
          </el-scrollbar>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import * as admin from "../../api/admin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import type { AccessLogRow } from "../../types/admin";
import { formatAccessLogUser, formatTenantNameCode } from "../../utils/adminListDisplay";

const { t } = useI18n();
const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const loading = ref(false);
const rows = ref<AccessLogRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);

const detailOpen = ref(false);
const detail = ref<AccessLogRow | null>(null);

const detailJson = computed(() => (detail.value ? JSON.stringify(detail.value, null, 2) : ""));

function formatTime(v: string | null | undefined): string {
  if (!v) return t("common.dash");
  return v.replace("T", " ").slice(0, 19);
}

function statusTagType(code: number): "success" | "warning" | "danger" | "info" {
  if (code >= 200 && code < 300) return "success";
  if (code >= 400 && code < 500) return "warning";
  if (code >= 500) return "danger";
  return "info";
}

async function load() {
  loading.value = true;
  try {
    const data = await admin.fetchAccessLogs(page.value, size.value, listFilterQuery());
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

function openDetail(row: AccessLogRow) {
  detail.value = row;
  detailOpen.value = true;
}

function onRowClick(row: AccessLogRow) {
  openDetail(row);
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(detailJson.value);
    ElMessage.success(t("views.accessLogs.copied"));
  } catch {
    ElMessage.error(t("views.accessLogs.copyFailed"));
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
  margin-bottom: 8px;
}

.hdr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title {
  font-weight: 600;
  font-size: 15px;
}

.tenant-filter {
  width: min(280px, 40vw);
}

.log-table {
  width: 100%;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.json-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 12px 0 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.json-pre {
  margin: 0;
  padding: 8px;
  font-size: 12px;
  line-height: 1.4;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  overflow: auto;
}

.ua {
  word-break: break-all;
  font-size: 12px;
  line-height: 1.45;
}
</style>
