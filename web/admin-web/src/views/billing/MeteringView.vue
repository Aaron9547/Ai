<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">计量事件</span>
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

      <p class="panel-tip">
        语言对话的 <strong>Token</strong> 与<strong>本次调用耗时</strong>合并为同一条记录（见扩展信息中的「耗时」）；历史上曾单独写入的「调用耗时」行仍可能出现在列表底部。
      </p>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        max-height="520"
        class="data-table"
        empty-text="暂无计量记录"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" label="发生时间" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="计量项" width="168" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelMeterType(row.meterType) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="120" align="right">
          <template #default="{ row }">
            {{ formatQty(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="88" align="center">
          <template #default="{ row }">
            {{ labelUnit(row.unit) }}
          </template>
        </el-table-column>
        <el-table-column label="租户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatMeteringUserOrDevice(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" label="设备码" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || "—" }}
          </template>
        </el-table-column>
        <el-table-column label="扩展信息" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ meteringSummary(row) }}
          </template>
        </el-table-column>
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

    <el-dialog v-model="detailOpen" title="计量事件详情" width="640px" destroy-on-close class="detail-dlg">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="记录编号（排障）">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="租户（名称 / 编码）">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item label="租户 ID（排障）">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="用户 / 设备">{{ formatMeteringUserOrDevice(detail) }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID（排障）">{{ detail.userId ?? "—" }}</el-descriptions-item>
          <el-descriptions-item label="设备码">{{ detail.deviceId || "—" }}</el-descriptions-item>
          <el-descriptions-item label="计量项">{{ labelMeterType(detail.meterType) }}</el-descriptions-item>
          <el-descriptions-item label="原始计量码（排障）">{{ detail.meterType || "—" }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ formatQty(detail.quantity) }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ labelUnit(detail.unit) }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>扩展数据（JSON）</span>
            <el-button size="small" type="primary" plain @click="copyJson">复制</el-button>
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
import { computed, onMounted, ref } from "vue";
import * as admin from "../../api/admin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import type { MeteringEventRow } from "../../types/admin";
import { formatMeteringUserOrDevice, formatTenantNameCode } from "../../utils/adminListDisplay";

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const METER_TYPE_LABELS: Record<string, string> = {
  "llm.chat.completion": "对话补全（Token）",
  "llm.model.usage": "模型调用（Token）",
  MODEL_COMPLETION: "调用耗时（毫秒·历史）",
};

function labelMeterType(code: string | null | undefined): string {
  if (!code) return "—";
  return METER_TYPE_LABELS[code] ?? code;
}

function labelUnit(u: string | null | undefined): string {
  if (!u) return "—";
  if (u === "token") return "Token";
  if (u === "ms") return "毫秒";
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
  if (!detail.value?.refJson) return "—";
  try {
    return JSON.stringify(JSON.parse(detail.value.refJson), null, 2);
  } catch {
    return detail.value.refJson;
  }
});

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

function formatQty(q: number | string | null | undefined): string {
  if (q === null || q === undefined || q === "") return "—";
  return String(q);
}

function previewJson(raw: string | null | undefined): string {
  if (!raw) return "—";
  const t = raw.trim();
  if (t.length <= 80) return t;
  return `${t.slice(0, 80)}…`;
}

function meteringSummary(row: MeteringEventRow): string {
  const raw = row.refJson;
  if (!raw?.trim()) return "—";
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    const parts: string[] = [];
    if (typeof o.modelAlias === "string" && o.modelAlias) {
      parts.push(`模型：${o.modelAlias}`);
    }
    if (typeof o.durationMs === "number" && o.durationMs > 0) {
      parts.push(`耗时：${o.durationMs} ms`);
    }
    if (typeof o.totalTokens === "number") {
      parts.push(`Token：${o.totalTokens}`);
    }
    if (parts.length) {
      return parts.join("；");
    }
  } catch {
    /* fall through */
  }
  return previewJson(raw);
}

async function load() {
  loading.value = true;
  try {
    const data = await admin.fetchMetering(page.value, size.value, listFilterQuery());
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

.panel-tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: #64748b;
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
