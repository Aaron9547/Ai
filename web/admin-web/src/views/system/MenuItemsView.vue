<template>
  <div class="page menu-items">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.menuItems.title") }}</span>
          <div class="hdr-actions">
            <el-button v-if="isFounder" type="primary" @click="openCreate">{{ t("views.menuItems.add") }}</el-button>
            <el-button text type="primary" :loading="loading" @click="load">{{ t("views.menuItems.refresh") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.menuItems.empty')">
        <el-table-column prop="menuCode" :label="t('views.menuItems.colCode')" width="160" />
        <el-table-column prop="titleZh" :label="t('views.menuItems.colTitle')" min-width="140" />
        <el-table-column prop="routePath" :label="t('views.menuItems.colRoute')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('views.menuItems.colSort')" width="80" align="center" />
        <el-table-column :label="t('views.menuItems.colEnabled')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{
              row.enabled === "ON" ? t("views.menuItems.yes") : t("views.menuItems.no")
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isFounder" :label="t('views.menuItems.colActions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.menuItems.edit") }}</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">{{ t("views.menuItems.delete") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dlgEdit"
      :title="editId ? t('views.menuItems.dlgEdit') : t('views.menuItems.dlgNew')"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-width="96px">
        <el-form-item v-if="!editId" :label="t('views.menuItems.labelCode')" required>
          <el-select v-model="form.menuCode" filterable :placeholder="t('views.menuItems.codePh')" style="width: 100%">
            <el-option v-for="c in creatableCodes" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('views.menuItems.labelTitle')" required>
          <el-input v-model="form.titleZh" clearable />
        </el-form-item>
        <el-form-item :label="t('views.menuItems.labelRoute')">
          <el-input v-model="form.routePath" :placeholder="t('views.menuItems.routePh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.menuItems.labelSort')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item v-if="editId" :label="t('views.menuItems.labelEnabled')">
          <el-switch v-model="form.enabledOn" :active-text="t('views.menuItems.yes')" :inactive-text="t('views.menuItems.no')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgEdit = false">{{ t("views.menuItems.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">{{ t("views.menuItems.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as menuItemsApi from "@/api/menuItems";
import { ALL_ADMIN_MENU_CODES } from "@/constants/adminMenuCodes";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import { readJwtTmr } from "@/utils/jwtSubject";

const { t } = useI18n();

const rows = ref<menuItemsApi.MenuItemRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const dlgEdit = ref(false);
const editId = ref<number | null>(null);

const isFounder = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return false;
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const form = reactive({
  menuCode: "" as string,
  titleZh: "",
  routePath: "",
  sortOrder: 0,
  enabledOn: true,
});

const creatableCodes = computed(() => {
  const used = new Set(rows.value.map((r) => r.menuCode));
  return (ALL_ADMIN_MENU_CODES as readonly string[]).filter((c) => !used.has(c));
});

async function load() {
  loading.value = true;
  try {
    rows.value = await menuItemsApi.listMenuItems();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.menuItems.loadFailed")));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editId.value = null;
  form.menuCode = creatableCodes.value[0] ?? "";
  form.titleZh = "";
  form.routePath = "";
  form.sortOrder = 0;
  form.enabledOn = true;
  dlgEdit.value = true;
}

function openEdit(row: menuItemsApi.MenuItemRow) {
  editId.value = row.id;
  form.menuCode = row.menuCode;
  form.titleZh = row.titleZh;
  form.routePath = row.routePath ?? "";
  form.sortOrder = row.sortOrder;
  form.enabledOn = row.enabled === "ON";
  dlgEdit.value = true;
}

function resetForm() {
  editId.value = null;
}

async function submitForm() {
  saving.value = true;
  try {
    if (editId.value == null) {
      if (!form.menuCode) {
        ElMessage.warning(t("views.menuItems.pickCodeWarning"));
        return;
      }
      await menuItemsApi.createMenuItem({
        menuCode: form.menuCode,
        titleZh: form.titleZh || undefined,
        routePath: form.routePath || undefined,
        sortOrder: form.sortOrder,
      });
      ElMessage.success(t("views.menuItems.created"));
    } else {
      await menuItemsApi.updateMenuItem(editId.value, {
        titleZh: form.titleZh,
        routePath: form.routePath || undefined,
        sortOrder: form.sortOrder,
        enabled: form.enabledOn ? "ON" : "OFF",
      });
      ElMessage.success(t("views.menuItems.saved"));
    }
    dlgEdit.value = false;
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.menuItems.saveFailed")));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: menuItemsApi.MenuItemRow) {
  try {
    await ElMessageBox.confirm(
      t("views.menuItems.deleteConfirm", { title: row.titleZh }),
      t("views.menuItems.confirm"),
      { type: "warning" },
    );
    await menuItemsApi.deleteMenuItem(row.id);
    await load();
    ElMessage.success(t("views.menuItems.deleted"));
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, t("views.menuItems.deleteFailed")));
    }
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
}
.hdr-actions {
  display: flex;
  gap: 8px;
}
.title {
  font-weight: 600;
  font-size: 15px;
}
</style>
