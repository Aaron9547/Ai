<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">HTTP 访问日志</span>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              placeholder="全部租户"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="t in tenantOptions"
                :key="t.id"
                :label="`${t.name} (${t.code})`"
                :value="t.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
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
        empty-text="暂无访问记录"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" label="时间" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="86" />
        <el-table-column prop="pathPattern" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column prop="httpStatus" label="状态" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.httpStatus)" size="small">{{ row.httpStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="102" align="right" />
        <el-table-column label="租户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAccessLogUser(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" label="设备" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || "—" }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailOpen" title="访问日志详情" width="640px" destroy-on-close class="detail-dlg">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="记录编号（排障）">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 方法">{{ detail.method }}</el-descriptions-item>
          <el-descriptions-item label="路径模板">{{ detail.pathPattern }}</el-descriptions-item>
          <el-descriptions-item label="状态码">{{ detail.httpStatus }}</el-descriptions-item>
          <el-descriptions-item label="耗时 (ms)">{{ detail.durationMs }}</el-descriptions-item>
          <el-descriptions-item label="租户名称 / 编码">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item label="租户 ID（排障）">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="用户展示名">{{ formatAccessLogUser(detail) }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID（排障）">{{ detail.userId ?? "—" }}</el-descriptions-item>
          <el-descriptions-item label="设备码">{{ detail.deviceId || "—" }}</el-descriptions-item>
          <el-descriptions-item label="Trace / Request ID">{{ detail.traceId || "—" }}</el-descriptions-item>
          <el-descriptions-item label="客户端 IP">{{ detail.clientIp || "—" }}</el-descriptions-item>
          <el-descriptions-item label="User-Agent">
            <el-scrollbar max-height="120px">
              <div class="ua">{{ detail.userAgent || "—" }}</div>
            </el-scrollbar>
          </el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>完整 JSON（便于联调复制）</span>
            <el-button size="small" type="primary" plain @click="copyJson">复制</el-button>
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
import * as admin from "../../api/admin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import type { AccessLogRow } from "../../types/admin";
import { formatAccessLogUser, formatTenantNameCode } from "../../utils/adminListDisplay";

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
  if (!v) return "—";
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
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败");
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
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
  color: #0f172a;
}

.log-table {
  width: 100%;
}

.log-table :deep(.el-table__row) {
  cursor: pointer;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.ua {
  font-size: 12px;
  line-height: 1.45;
  color: #475569;
  word-break: break-word;
  padding-right: 8px;
}

.json-block {
  margin-top: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.json-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
}

.json-pre {
  margin: 0;
  padding: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
