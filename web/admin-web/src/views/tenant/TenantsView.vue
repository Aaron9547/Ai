<template>
  <div class="page tenants">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.tenants.title") }}</span>
          <div class="hdr-actions">
            <el-button type="primary" @click="openCreate">{{ t("views.tenants.newTenant") }}</el-button>
            <el-button text type="primary" :loading="loading" @click="load">{{ t("views.tenants.refresh") }}</el-button>
          </div>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        style="width: 100%"
        :empty-text="t('views.tenants.empty')"
      >
        <el-table-column prop="code" :label="t('views.tenants.colCode')" min-width="120" />
        <el-table-column prop="name" :label="t('views.tenants.colName')" min-width="160" />
        <el-table-column :label="t('views.tenants.colStatus')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.tenants.colActions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("views.tenants.edit") }}</el-button>
            <el-button link type="primary" size="small" @click="openMenus(row)">{{ t("views.tenants.menus") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlgCreate" :title="t('views.tenants.dlgCreateTitle')" width="480px" destroy-on-close @closed="resetCreate">
      <el-form label-width="88px">
        <el-form-item :label="t('views.tenants.codeLabel')" required>
          <el-input v-model="createForm.code" :placeholder="t('views.tenants.codePh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.tenants.nameLabel')" required>
          <el-input v-model="createForm.name" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgCreate = false">{{ t("views.tenants.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">{{ t("views.tenants.create") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerMenus" :title="t('views.tenants.drawerTitle')" size="min(560px, 92vw)" destroy-on-close @closed="onMenusDrawerClosed">
      <template v-if="menuTenant">
        <div v-loading="menuLoading" class="menu-drawer-body">
          <el-checkbox-group v-model="menuSelected" class="menu-stack">
            <template v-for="block in tenantMenuGrouped" :key="block.titleKey">
              <section class="menu-group-card">
                <header class="menu-group-head">{{ t(block.titleKey) }}</header>
                <div class="menu-group-items">
                  <el-checkbox v-for="c in block.codes" :key="c" :label="c" class="menu-item-cb">
                    {{ adminMenuLabel(c) }}
                  </el-checkbox>
                </div>
              </section>
            </template>
          </el-checkbox-group>
        </div>
        <div class="menu-actions">
          <el-button type="primary" :loading="menuSaving" @click="saveMenus">{{ t("views.tenants.saveMenus") }}</el-button>
          <el-button :loading="menuLoading" @click="reloadMenus">{{ t("views.tenants.reloadMenus") }}</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="dlgEdit" :title="t('views.tenants.dlgEditTitle')" width="480px" destroy-on-close>
      <el-form v-if="editRow" label-width="88px">
        <el-form-item :label="t('views.tenants.codeLabel')">
          <span>{{ editRow.code }}</span>
        </el-form-item>
        <el-form-item :label="t('views.tenants.nameLabel')">
          <el-input v-model="editName" clearable />
        </el-form-item>
        <el-form-item :label="t('views.tenants.statusLabel')">
          <el-select v-model="editStatus" style="width: 100%">
            <el-option :label="t('views.tenants.active')" value="ACTIVE" />
            <el-option :label="t('views.tenants.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgEdit = false">{{ t("views.tenants.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">{{ t("views.tenants.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import * as tenantsApi from "../../api/tenants";
import { ALL_ADMIN_MENU_CODES, ADMIN_MENU_LABELS, ADMIN_MENU_TENANT_GROUPS } from "@/constants/adminMenuCodes";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t, te } = useI18n();

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

const CODES_IN_TENANT_GROUPS = new Set(
  ADMIN_MENU_TENANT_GROUPS.flatMap((g) => [...(g.codes as readonly string[])]),
);

const menuDisplayCodes = computed(() => {
  const set = new Set<string>([...(ALL_ADMIN_MENU_CODES as readonly string[])]);
  for (const c of menuSelected.value) {
    if (c) set.add(c);
  }
  return Array.from(set).sort();
});

const menuDisplaySet = computed(() => new Set(menuDisplayCodes.value));

/** 与侧栏分组一致；未知码落入「其他」。 */
const tenantMenuGrouped = computed(() => {
  const set = menuDisplaySet.value;
  const blocks: { titleKey: string; codes: string[] }[] = ADMIN_MENU_TENANT_GROUPS.map((g) => ({
    titleKey: g.titleI18nKey,
    codes: g.codes.filter((c) => set.has(c)),
  })).filter((b) => b.codes.length > 0);
  const orphans = menuDisplayCodes.value.filter((c) => !CODES_IN_TENANT_GROUPS.has(c));
  if (orphans.length) {
    blocks.push({ titleKey: "views.tenants.menuGroups.other", codes: orphans });
  }
  return blocks;
});

function adminMenuLabel(code: string): string {
  const key = `menuCodes.${code}`;
  if (te(key)) return String(t(key));
  const fallback = ADMIN_MENU_LABELS[code];
  if (fallback) return fallback;
  return String(t("menuCodes._unknown", { code }));
}

function statusLabel(s: tenantsApi.TenantRow["status"]): string {
  if (s === "ACTIVE" || s === 1) return t("views.tenants.active");
  if (s === "DISABLED" || s === 0) return t("views.tenants.disabled");
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
    ElMessage.error(apiRequestErrorMessage(e, t("views.tenants.loadFailed")));
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
    ElMessage.success(t("views.tenants.created"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.tenants.createFailed")));
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
    ElMessage.success(t("views.tenants.saved"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.tenants.saveFailed")));
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
    const menuRows = await tenantsApi.getTenantAdminMenus(menuTenant.value.id);
    menuSelected.value = menuRows.length ? [...menuRows] : [...(ALL_ADMIN_MENU_CODES as readonly string[])];
  } finally {
    menuLoading.value = false;
  }
}

async function saveMenus() {
  if (!menuTenant.value) return;
  menuSaving.value = true;
  try {
    await tenantsApi.putTenantAdminMenus(menuTenant.value.id, menuSelected.value);
    ElMessage.success(t("views.tenants.saved"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.tenants.saveFailed")));
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

.menu-drawer-body {
  min-height: 100px;
}

.menu-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-group-card {
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
}

.menu-group-head {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.02em;
  margin: 0 0 10px;
  padding: 0;
}

.menu-group-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item-cb {
  margin: 0;
  width: 100%;
  height: auto;
  padding: 8px 10px;
  margin-inline: -6px;
  border-radius: 8px;
  transition: background-color 0.15s ease;
}

.menu-item-cb:hover {
  background: var(--el-fill-color-light);
}

.menu-item-cb.is-checked {
  background: var(--el-color-primary-light-9);
}

.menu-item-cb.is-checked:hover {
  background: var(--el-color-primary-light-8);
}

.menu-item-cb :deep(.el-checkbox__label) {
  font-size: 14px;
  color: var(--el-text-color-primary);
  padding-left: 10px;
  line-height: 1.4;
}

.menu-item-cb :deep(.el-checkbox__input) {
  align-self: center;
}

.menu-actions {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
