<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">MCP 服务注册</span>
            <p class="sub">租户内 MCP Server 配置（持久化表 mcp_server_registry），与 /api/v1/admin/mcp-servers 对齐。</p>
          </div>
          <div class="actions">
            <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
            <el-button type="primary" @click="openCreate">新建</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe border empty-text="暂无数据">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status || "—" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dlg" :title="editId ? '编辑 MCP Server' : '新建 MCP Server'" width="520px" destroy-on-close @closed="resetForm">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="展示名" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="https://mcp.example.com" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import * as mcpApi from "../../api/mcpAdmin";
import type { McpServerRow } from "../../types/admin";

const loading = ref(false);
const saving = ref(false);
const rows = ref<McpServerRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive({ name: "", baseUrl: "", enabled: true });

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

async function load() {
  loading.value = true;
  try {
    const data = await mcpApi.fetchMcpServersPage(page.value, size.value);
    rows.value = data.records ?? [];
    total.value = data.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function onSizeChange() {
  page.value = 1;
  void load();
}

function openCreate() {
  editId.value = null;
  form.name = "";
  form.baseUrl = "";
  form.enabled = true;
  dlg.value = true;
}

function openEdit(row: McpServerRow) {
  editId.value = row.id;
  form.name = row.name;
  form.baseUrl = row.baseUrl;
  form.enabled = row.status === "ACTIVE";
  dlg.value = true;
}

function resetForm() {
  editId.value = null;
}

async function save() {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    ElMessage.warning("请填写名称与 Base URL");
    return;
  }
  saving.value = true;
  try {
    if (editId.value == null) {
      await mcpApi.createMcpServer({
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        enabled: form.enabled,
      });
      ElMessage.success("已创建");
    } else {
      await mcpApi.updateMcpServer(editId.value, {
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        enabled: form.enabled,
      });
      ElMessage.success("已保存");
    }
    dlg.value = false;
    await load();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

async function remove(row: McpServerRow) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, "删除确认", { type: "warning" });
    await mcpApi.deleteMcpServer(row.id);
    ElMessage.success("已删除");
    await load();
  } catch {
    /* cancel */
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.actions {
  display: flex;
  gap: 8px;
}

.title {
  font-weight: 600;
  font-size: 15px;
  color: #0f172a;
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
