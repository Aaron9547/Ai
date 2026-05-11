<template>
  <div class="page cors-page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">跨域来源（CORS）</span>
          <el-button type="primary" @click="openCreate">新增来源</el-button>
          <el-button text type="primary" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert
        class="mb"
        type="info"
        :closable="false"
        show-icon
        title="与浏览器地址栏完全一致"
        description="须包含协议、主机与端口（如 http://192.168.1.5:5176），最长 191 字符。localhost 与 127.0.0.1 视为不同来源。保存后立即生效，无需重启后端。"
      />
      <el-table v-loading="loading" :data="rows" stripe border empty-text="暂无记录">
        <el-table-column prop="origin" label="Origin" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="88" align="center" />
        <el-table-column label="启用" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{
              row.enabled === "ON" ? "是" : "否"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="editId ? '编辑来源' : '新增来源'" width="520px" destroy-on-close @closed="resetDlg">
      <el-form label-width="88px">
        <el-form-item label="Origin" required>
          <el-input
            v-model="form.origin"
            placeholder="如 https://app.example.com 或 http://10.0.0.1:5176"
            maxlength="191"
            show-word-limit
            clearable
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledOn" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import * as api from "@/api/corsAllowedOrigins";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const rows = ref<api.CorsOriginRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const dlg = ref(false);
const editId = ref<number | null>(null);

const form = reactive({
  origin: "",
  sortOrder: 0,
  enabledOn: true,
  remark: "",
});

async function load() {
  loading.value = true;
  try {
    rows.value = await api.listCorsOrigins();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "加载失败"));
  } finally {
    loading.value = false;
  }
}

function resetDlg() {
  editId.value = null;
  form.origin = "";
  form.sortOrder = 0;
  form.enabledOn = true;
  form.remark = "";
}

function openCreate() {
  resetDlg();
  dlg.value = true;
}

function openEdit(row: api.CorsOriginRow) {
  editId.value = row.id;
  form.origin = row.origin;
  form.sortOrder = row.sortOrder;
  form.enabledOn = row.enabled === "ON";
  form.remark = row.remark ?? "";
  dlg.value = true;
}

async function submit() {
  const origin = form.origin.trim();
  if (!origin) {
    ElMessage.warning("请填写 Origin");
    return;
  }
  saving.value = true;
  try {
    const enabled = form.enabledOn ? "ON" : "OFF";
    const remark = form.remark.trim();
    if (editId.value == null) {
      await api.createCorsOrigin({
        origin,
        enabled,
        sortOrder: form.sortOrder,
        remark: remark || undefined,
      });
      ElMessage.success("已创建");
    } else {
      await api.updateCorsOrigin(editId.value, {
        origin,
        enabled,
        sortOrder: form.sortOrder,
        remark: remark || null,
      });
      ElMessage.success("已保存");
    }
    dlg.value = false;
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: api.CorsOriginRow) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.origin}」？`, "删除", { type: "warning" });
  } catch {
    return;
  }
  try {
    await api.deleteCorsOrigin(row.id);
    ElMessage.success("已删除");
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
  }
}

onMounted(() => void load());
</script>

<style scoped>
.hdr {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  font-weight: 600;
  margin-right: auto;
}
.mb {
  margin-bottom: 12px;
}
.panel {
  border-radius: 12px;
}
</style>
