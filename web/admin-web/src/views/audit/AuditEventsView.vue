<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">审计事件</span>
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
        class="data-table"
        empty-text="暂无审计记录"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column prop="createdAt" label="发生时间" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelAction(row.action) }}
          </template>
        </el-table-column>
        <el-table-column label="关联对象类型" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelResourceType(row.resourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="关联对象" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAuditResource(row) }}
          </template>
        </el-table-column>
        <el-table-column label="操作者类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ labelActorType(row.actorType) }}
          </template>
        </el-table-column>
        <el-table-column label="操作者" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAuditActor(row) }}
          </template>
        </el-table-column>
        <el-table-column label="租户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column label="详情摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ previewJson(row.detailJson) }}
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

    <el-dialog v-model="detailOpen" title="审计事件详情" width="640px" destroy-on-close class="detail-dlg">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="记录编号（排障）">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="租户（名称 / 编码）">{{ formatTenantNameCode(detail) }}</el-descriptions-item>
          <el-descriptions-item label="租户 ID（排障）">{{ detail.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="操作者类型">{{ labelActorType(detail.actorType) }}</el-descriptions-item>
          <el-descriptions-item label="操作者">{{ formatAuditActor(detail) }}</el-descriptions-item>
          <el-descriptions-item label="操作者标识原文（排障）">{{ detail.actorId || "—" }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">{{ labelAction(detail.action) }}</el-descriptions-item>
          <el-descriptions-item label="原始操作码（排障）">{{ detail.action || "—" }}</el-descriptions-item>
          <el-descriptions-item label="关联对象（可读）">{{ formatAuditResource(detail) }}</el-descriptions-item>
          <el-descriptions-item label="关联对象类型">{{ labelResourceType(detail.resourceType) }}</el-descriptions-item>
          <el-descriptions-item label="关联对象标识（排障）">{{ detail.resourceId || "—" }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-hdr">
            <span>详情数据（JSON）</span>
            <el-button size="small" type="primary" plain @click="copyJson">复制</el-button>
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
import { computed, onMounted, ref } from "vue";
import * as admin from "../../api/admin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import type { AuditEventRow } from "../../types/admin";
import { formatAuditActor, formatAuditResource, formatTenantNameCode } from "../../utils/adminListDisplay";

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const ACTION_LABELS: Record<string, string> = {
  TENANT_MEMBER_INVITE: "邀请或恢复成员",
  TENANT_MEMBER_REMOVE: "移出租户",
  TENANT_MEMBER_ROLE_UPDATE: "变更成员角色",
  ADMIN_CONTEXT_SWITCH: "切换管理端工作区",
};

const ACTOR_TYPE_LABELS: Record<string, string> = {
  USER: "用户",
  SERVICE: "服务账号",
  SYSTEM: "系统",
};

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  sys_tenant_member: "租户成员",
  admin_context: "管理端上下文",
  sys_audit_event: "审计事件",
};

function labelAction(a: string | null | undefined): string {
  if (!a) return "—";
  return ACTION_LABELS[a] ?? a;
}

function labelActorType(t: string | null | undefined): string {
  if (!t) return "—";
  return ACTOR_TYPE_LABELS[t] ?? t;
}

function labelResourceType(t: string | null | undefined): string {
  if (!t) return "—";
  return RESOURCE_TYPE_LABELS[t] ?? t;
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
  if (!detail.value?.detailJson) return "—";
  try {
    return JSON.stringify(JSON.parse(detail.value.detailJson), null, 2);
  } catch {
    return detail.value.detailJson;
  }
});

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

function previewJson(raw: string | null | undefined): string {
  if (!raw) return "—";
  const t = raw.trim();
  if (t.length <= 80) return t;
  return `${t.slice(0, 80)}…`;
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
