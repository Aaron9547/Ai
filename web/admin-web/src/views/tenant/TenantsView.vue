<template>
  <div class="page tenants">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">租户管理</span>
          <div class="hdr-actions">
            <el-button type="primary" @click="openCreate">新建租户</el-button>
            <el-button text type="primary" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe border style="width: 100%" empty-text="暂无数据">
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openMenus(row)">后台菜单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlgCreate" title="新建租户" width="480px" destroy-on-close @closed="resetCreate">
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input v-model="createForm.code" placeholder="唯一英文标识" clearable />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerMenus" title="租户后台开放菜单" size="420px" destroy-on-close @closed="onMenusDrawerClosed">
      <template v-if="menuTenant">
        <p class="menu-hint">勾选该租户下 OWNER/ADMIN 在管理端可见的能力模块（存英文稳定码）。</p>
        <el-checkbox-group v-model="menuSelected" class="chk-grid">
          <el-checkbox v-for="c in menuDisplayCodes" :key="c" :label="c">{{ adminMenuLabelZh(c) }}</el-checkbox>
        </el-checkbox-group>
        <div class="menu-actions">
          <el-button type="primary" :loading="menuSaving" @click="saveMenus">保存</el-button>
          <el-button :loading="menuLoading" @click="reloadMenus">重新加载</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="dlgEdit" title="编辑租户" width="480px" destroy-on-close>
      <el-form v-if="editRow" label-width="88px">
        <el-form-item label="编码">
          <span>{{ editRow.code }}</span>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editName" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editStatus" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import * as tenantsApi from "../../api/tenants";
import { ALL_ADMIN_MENU_CODES, adminMenuLabelZh } from "@/constants/adminMenuCodes";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const rows = ref<tenantsApi.TenantRow[]>([]);
const loading = ref(false);
const saving = ref(false);
const dlgCreate = ref(false);
const dlgEdit = ref(false);
const editRow = ref<tenantsApi.TenantRow | null>(null);
const editName = ref("");
const editStatus = ref<tenantsApi.TenantStatus>("ACTIVE");

const createForm = reactive({ code: "", name: "" });

const drawerMenus = ref(false);
const menuTenant = ref<tenantsApi.TenantRow | null>(null);
const menuSelected = ref<string[]>([]);
const menuLoading = ref(false);
const menuSaving = ref(false);

const menuDisplayCodes = computed(() => {
  const set = new Set<string>([...(ALL_ADMIN_MENU_CODES as readonly string[])]);
  for (const c of menuSelected.value) {
    if (c) set.add(c);
  }
  return Array.from(set).sort();
});

function statusLabel(s: tenantsApi.TenantRow["status"]): string {
  if (s === "ACTIVE" || s === 1) return "启用";
  if (s === "DISABLED" || s === 0) return "停用";
  return String(s);
}

function statusTag(s: tenantsApi.TenantRow["status"]): "success" | "info" {
  return s === "ACTIVE" || s === 1 ? "success" : "info";
}

async function load() {
  loading.value = true;
  try {
    rows.value = await tenantsApi.listTenants();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "加载失败"));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  createForm.code = "";
  createForm.name = "";
  dlgCreate.value = true;
}

function resetCreate() {
  createForm.code = "";
  createForm.name = "";
}

async function submitCreate() {
  saving.value = true;
  try {
    await tenantsApi.createTenant({ code: createForm.code.trim(), name: createForm.name.trim() });
    dlgCreate.value = false;
    await load();
    ElMessage.success("已创建");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "创建失败"));
  } finally {
    saving.value = false;
  }
}

function openEdit(row: tenantsApi.TenantRow) {
  editRow.value = row;
  editName.value = row.name;
  const s = row.status;
  editStatus.value = s === "DISABLED" || s === 0 ? "DISABLED" : "ACTIVE";
  dlgEdit.value = true;
}

async function submitEdit() {
  if (!editRow.value) return;
  saving.value = true;
  try {
    await tenantsApi.updateTenant(editRow.value.id, {
      name: editName.value.trim(),
      status: editStatus.value,
    });
    dlgEdit.value = false;
    await load();
    ElMessage.success("已保存");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

function onMenusDrawerClosed() {
  menuTenant.value = null;
  menuSelected.value = [];
}

async function openMenus(row: tenantsApi.TenantRow) {
  menuTenant.value = row;
  drawerMenus.value = true;
  await reloadMenus();
}

async function reloadMenus() {
  if (!menuTenant.value) return;
  menuLoading.value = true;
  try {
    const rows = await tenantsApi.getTenantAdminMenus(menuTenant.value.id);
    menuSelected.value = rows.length ? [...rows] : [...(ALL_ADMIN_MENU_CODES as readonly string[])];
  } finally {
    menuLoading.value = false;
  }
}

async function saveMenus() {
  if (!menuTenant.value) return;
  menuSaving.value = true;
  try {
    await tenantsApi.putTenantAdminMenus(menuTenant.value.id, menuSelected.value);
    ElMessage.success("已保存");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    menuSaving.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.tenants.panel {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
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

.menu-hint {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 12px;
}

.chk-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px 0;
}

.menu-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
