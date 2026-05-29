<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.mcp.title") }}</span>
            <p class="sub">{{ t("views.mcp.sub") }}</p>
          </div>
          <div class="actions">
            <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.mcp.refresh") }}</el-button>
            <el-button type="primary" @click="openCreate">{{ t("views.mcp.new") }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.mcp.empty')">
        <el-table-column prop="name" :label="t('views.mcp.colName')" min-width="120" />
        <el-table-column prop="baseUrl" :label="t('views.mcp.colBaseUrl')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('views.mcp.colHasKey')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.hasApiKey ? 'success' : 'info'">
              {{ row.hasApiKey ? t("views.mcp.hasKey") : t("views.mcp.noKey") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('views.mcp.colStatus')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status || t("common.dash") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.mcp.colProbe')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.lastProbeOk === true" size="small" type="success">OK</el-tag>
            <el-tag v-else-if="row.lastProbeOk === false" size="small" type="danger">FAIL</el-tag>
            <span v-else>{{ t("common.dash") }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('views.mcp.colCreated')" width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.mcp.colActions')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="probingId === row.id" @click="probe(row)">
              {{ t("views.mcp.probe") }}
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.mcp.edit") }}</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">{{ t("views.mcp.delete") }}</el-button>
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

    <el-dialog
      v-model="dlg"
      :title="editId ? t('views.mcp.dlgEdit') : t('views.mcp.dlgNew')"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item :label="t('views.mcp.labelName')" required>
          <el-input v-model="form.name" :placeholder="t('views.mcp.namePh')" />
        </el-form-item>
        <el-form-item :label="t('views.mcp.colBaseUrl')" required>
          <el-input v-model="form.baseUrl" :placeholder="t('views.mcp.basePh')" />
        </el-form-item>
        <el-form-item :label="t('views.mcp.labelTransport')">
          <el-input :model-value="t('views.mcp.transportHttp')" disabled />
        </el-form-item>
        <el-form-item :label="t('views.mcp.labelDescription')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('views.mcp.labelApiKey')">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="t('views.mcp.apiKeyPh')" />
          <p v-if="editId" class="hint">{{ t("views.mcp.apiKeyEditHint") }}</p>
        </el-form-item>
        <el-form-item :label="t('views.mcp.labelEnabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ t("views.mcp.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("views.mcp.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as mcpApi from "../../api/mcpAdmin";
import type { McpServerRow } from "../../types/admin";

const { t } = useI18n();

const loading = ref(false);
const saving = ref(false);
const probingId = ref<number | null>(null);
const rows = ref<McpServerRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const dlg = ref(false);
const editId = ref<number | null>(null);
const form = reactive({
  name: "",
  baseUrl: "",
  description: "",
  apiKey: "",
  enabled: true,
});

function formatTime(v: string | null | undefined): string {
  if (!v) return t("common.dash");
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
  form.description = "";
  form.apiKey = "";
  form.enabled = true;
  dlg.value = true;
}

function openEdit(row: McpServerRow) {
  editId.value = row.id;
  form.name = row.name;
  form.baseUrl = row.baseUrl;
  form.description = row.description ?? "";
  form.apiKey = "";
  form.enabled = row.status === "ACTIVE";
  dlg.value = true;
}

function resetForm() {
  editId.value = null;
}

async function save() {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    ElMessage.warning(t("views.mcp.fillWarning"));
    return;
  }
  saving.value = true;
  try {
    const body = {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      transportKind: "STREAMABLE_HTTP",
      description: form.description.trim() || undefined,
      apiKey: form.apiKey.trim() || undefined,
      enabled: form.enabled,
    };
    if (editId.value == null) {
      await mcpApi.createMcpServer(body);
      ElMessage.success(t("views.mcp.created"));
    } else {
      await mcpApi.updateMcpServer(editId.value, body);
      ElMessage.success(t("views.mcp.saved"));
    }
    dlg.value = false;
    await load();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.mcp.saveErr");
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

async function probe(row: McpServerRow) {
  probingId.value = row.id;
  try {
    const r = await mcpApi.probeMcpServer(row.id);
    if (r.ok) {
      ElMessage.success(t("views.mcp.probeOk", { count: r.toolCount, ms: r.elapsedMs }));
    } else {
      ElMessage.error(t("views.mcp.probeFail", { msg: r.message }));
    }
    await load();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.mcp.probeFail", { msg: "error" });
    ElMessage.error(msg);
  } finally {
    probingId.value = null;
  }
}

async function remove(row: McpServerRow) {
  try {
    await ElMessageBox.confirm(t("views.mcp.deleteConfirm", { name: row.name }), t("views.mcp.deleteTitle"), {
      type: "warning",
    });
    await mcpApi.deleteMcpServer(row.id);
    ElMessage.success(t("views.mcp.deleted"));
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
  border: 1px solid var(--el-border-color);
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
  color: var(--el-text-color-primary);
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
