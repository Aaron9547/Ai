<template>
  <div class="page menu-items">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">菜单管理</span>
          <div class="hdr-actions">
            <el-button v-if="isFounder" type="primary" @click="openCreate">新增菜单项</el-button>
            <el-button text type="primary" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe border empty-text="暂无数据">
        <el-table-column prop="menuCode" label="菜单码" width="160" />
        <el-table-column prop="titleZh" label="显示名称" min-width="140" />
        <el-table-column prop="routePath" label="路由" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{ row.enabled === "ON" ? "是" : "否" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isFounder" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlgEdit" :title="editId ? '编辑菜单项' : '新增菜单项'" width="520px" destroy-on-close @closed="resetForm">
      <el-form label-width="96px">
        <el-form-item v-if="!editId" label="菜单码" required>
          <el-select v-model="form.menuCode" filterable placeholder="选择枚举码" style="width: 100%">
            <el-option v-for="c in creatableCodes" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="form.titleZh" clearable />
        </el-form-item>
        <el-form-item label="路由">
          <el-input v-model="form.routePath" placeholder="如 /users" clearable />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item v-if="editId" label="启用">
          <el-switch v-model="form.enabledOn" active-text="是" inactive-text="否" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import * as menuItemsApi from "@/api/menuItems";
import { ALL_ADMIN_MENU_CODES } from "@/constants/adminMenuCodes";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { readJwtTmr } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

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
    ElMessage.error(apiRequestErrorMessage(e, "加载失败"));
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
        ElMessage.warning("请选择菜单码");
        return;
      }
      await menuItemsApi.createMenuItem({
        menuCode: form.menuCode,
        titleZh: form.titleZh || undefined,
        routePath: form.routePath || undefined,
        sortOrder: form.sortOrder,
      });
      ElMessage.success("已创建");
    } else {
      await menuItemsApi.updateMenuItem(editId.value, {
        titleZh: form.titleZh,
        routePath: form.routePath || undefined,
        sortOrder: form.sortOrder,
        enabled: form.enabledOn ? "ON" : "OFF",
      });
      ElMessage.success("已保存");
    }
    dlgEdit.value = false;
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: menuItemsApi.MenuItemRow) {
  try {
    await ElMessageBox.confirm(`确定删除菜单项「${row.titleZh}」?`, "确认", { type: "warning" });
    await menuItemsApi.deleteMenuItem(row.id);
    await load();
    ElMessage.success("已删除");
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
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
</style>
