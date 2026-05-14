<template>
  <div class="page cors-page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.cors.title") }}</span>
          <el-button type="primary" @click="openCreate">{{ t("views.cors.newOrigin") }}</el-button>
          <el-button text type="primary" :loading="loading" @click="load">{{ t("views.cors.refresh") }}</el-button>
        </div>
      </template>
      <el-alert
        class="mb"
        type="info"
        :closable="false"
        show-icon
        :title="t('views.cors.alertTitle')"
        :description="t('views.cors.alertDesc')"
      />
      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.cors.empty')">
        <el-table-column prop="origin" :label="t('views.cors.colOrigin')" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('views.cors.colSort')" width="88" align="center" />
        <el-table-column :label="t('views.cors.colEnabled')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{
              row.enabled === "ON" ? t("views.cors.yes") : t("views.cors.no")
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('views.cors.colRemark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('common.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.cors.edit") }}</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">{{ t("views.cors.delete") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dlg"
      :title="editId ? t('views.cors.dlgEdit') : t('views.cors.dlgNew')"
      width="520px"
      destroy-on-close
      @closed="resetDlg"
    >
      <el-form label-width="88px">
        <el-form-item :label="t('views.cors.labelOrigin')" required>
          <el-input
            v-model="form.origin"
            :placeholder="t('views.cors.originPh')"
            maxlength="191"
            show-word-limit
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('views.cors.labelSort')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('views.cors.labelEnabled')">
          <el-switch v-model="form.enabledOn" :active-text="t('views.cors.yes')" :inactive-text="t('views.cors.no')" />
        </el-form-item>
        <el-form-item :label="t('views.cors.labelRemark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ t("views.cors.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ t("views.cors.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as api from "@/api/corsAllowedOrigins";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t } = useI18n();

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
    ElMessage.error(apiRequestErrorMessage(e, t("views.cors.loadFailed")));
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
    ElMessage.warning(t("views.cors.fillOrigin"));
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
      ElMessage.success(t("views.cors.created"));
    } else {
      await api.updateCorsOrigin(editId.value, {
        origin,
        enabled,
        sortOrder: form.sortOrder,
        remark: remark || null,
      });
      ElMessage.success(t("views.cors.saved"));
    }
    dlg.value = false;
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.cors.saveFailed")));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: api.CorsOriginRow) {
  try {
    await ElMessageBox.confirm(t("views.cors.deleteConfirm", { origin: row.origin }), t("views.cors.deleteTitle"), {
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await api.deleteCorsOrigin(row.id);
    ElMessage.success(t("views.cors.deleted"));
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.cors.deleteFailed")));
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
