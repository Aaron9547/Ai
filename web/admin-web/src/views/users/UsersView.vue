<template>
  <div class="users">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      title="用户管理"
      description="「租户成员」用于查看与邀请成员、调整角色、移出租户；「本租户账号」用于新增账号、启停、踢下线与封禁等。创始人可在上方选择租户后操作成员。"
    />
    <el-card shadow="never" class="card card--members">
      <template #header>
        <div class="hdr-row">
          <span class="hdr">租户成员</span>
          <div class="filters">
            <el-select
              v-if="isFounder"
              v-model="tenantFilter"
              class="filter-tenant"
              filterable
              placeholder="租户"
              clearable
              @change="loadMembers"
            >
              <el-option v-for="t in tenantOptions" :key="t.id" :label="tenantOptionLabel(t)" :value="t.id" />
            </el-select>
            <el-select v-model="roleFilter" class="filter-role" placeholder="角色" clearable @change="loadMembers">
              <el-option label="全部角色" value="" />
              <el-option label="创始人" value="FOUNDER" />
              <el-option label="所有者" value="OWNER" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="成员" value="MEMBER" />
            </el-select>
            <el-checkbox
              v-if="isOwnerOrFounder"
              v-model="includeInactive"
              class="filter-inactive"
              @change="loadMembers"
            >
              含已退出成员
            </el-checkbox>
            <el-button v-if="canMutateMembership" type="primary" plain @click="openInvite">邀请成员</el-button>
            <el-button class="users-refresh-btn" type="primary" plain :loading="loadingMembers" @click="loadMembers">
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loadingMembers" :data="members" stripe border empty-text="暂无数据">
        <el-table-column prop="loginName" label="登录名" min-width="120" />
        <el-table-column prop="displayName" label="昵称" min-width="120" />
        <el-table-column label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.userStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.userStatus === "ACTIVE" ? "启用" : "已禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成员状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.memberStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.memberStatus === "ACTIVE" ? "在册" : "已退出" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              class="role-select"
              :disabled="!canEditMemberRow(row)"
              @change="(v: string) => onMemberRoleChange(row, v as usersApi.TenantMemberRole)"
            >
              <el-option v-if="isFounder" label="创始人" value="FOUNDER" />
              <el-option label="所有者" value="OWNER" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="成员" value="MEMBER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="canMutateMembership" label="操作" width="112" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.memberStatus === 'ACTIVE' && canRemoveMemberRow(row)"
              link
              type="danger"
              size="small"
              @click="onRemoveMember(row)"
            >
              移出租户
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="card card--accounts">
      <template #header>
        <div class="table-hdr">
          <span class="card-title">本租户账号</span>
          <div class="table-hdr-actions">
            <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
            <el-button class="users-refresh-btn" type="primary" plain :loading="loadingUsers" @click="loadUsers">
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loadingUsers" :data="users" stripe border style="width: 100%" empty-text="暂无数据">
        <el-table-column prop="loginName" label="登录名" min-width="120" />
        <el-table-column prop="displayName" label="昵称" min-width="120" />
        <el-table-column label="本租户角色" min-width="200">
          <template #default="{ row }">
            <el-select
              v-if="row.tenantRole != null"
              :model-value="row.tenantRole"
              size="small"
              class="role-select-inline"
              :disabled="!canEditUserRole(row)"
              @change="(v: string) => onUserRoleChange(row, v as usersApi.TenantMemberRole)"
            >
              <el-option v-if="isFounder" label="创始人" value="FOUNDER" />
              <el-option label="所有者" value="OWNER" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="成员" value="MEMBER" />
            </el-select>
            <span v-else class="muted">未加入本租户</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="kickUser(row)">踢下线</el-button>
            <el-button link type="danger" size="small" @click="banUser(row)">封禁</el-button>
            <el-button link type="primary" size="small" @click="toggleStatus(row)">启停</el-button>
            <el-button link type="danger" size="small" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="inviteVisible" title="邀请成员" width="440px" destroy-on-close @closed="resetInvite">
      <el-form label-width="96px">
        <el-form-item label="登录名" required>
          <el-input
            v-model="inviteLoginName"
            autocomplete="off"
            clearable
            placeholder="对方账号的登录名（全局唯一）"
            class="w-full-role"
          />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="inviteRole" class="w-full-role">
            <el-option v-if="isFounder" label="创始人" value="FOUNDER" />
            <el-option label="所有者" value="OWNER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="成员" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" :loading="inviteSubmitting" @click="submitInvite">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新增用户" width="520px" destroy-on-close @closed="resetCreateForm">
      <el-form :model="form" label-width="88px" class="form-grid" @submit.prevent="onCreate">
        <el-form-item label="登录名" required>
          <el-input v-model="form.loginName" autocomplete="off" clearable />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.displayName" clearable placeholder="可选，不填则存空串" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-if="isFounder" label="创始人" value="FOUNDER" />
            <el-option label="成员" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="所有者" value="OWNER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import * as tenantsApi from "@/api/tenants";
import * as usersApi from "@/api/users";
import { readJwtTid, readJwtTmr, readJwtUid } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const members = ref<usersApi.TenantMemberRow[]>([]);
const users = ref<usersApi.UserRow[]>([]);
const loadingMembers = ref(false);
const loadingUsers = ref(false);
const tenantOptions = ref<tenantsApi.TenantRow[]>([]);
const tenantFilter = ref<number | undefined>(undefined);
const roleFilter = ref<"" | usersApi.TenantMemberRole>("");
const includeInactive = ref(false);
const inviteVisible = ref(false);
const inviteSubmitting = ref(false);
const inviteLoginName = ref("");
const inviteRole = ref<usersApi.TenantMemberRole>("MEMBER");
const createVisible = ref(false);
const createSubmitting = ref(false);

const isFounder = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return false;
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const jwtMemberRole = computed((): usersApi.TenantMemberRole | null => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return "OWNER";
  const r = readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  if (r === "FOUNDER" || r === "OWNER" || r === "ADMIN" || r === "MEMBER") return r;
  return null;
});

const isOwnerOrFounder = computed(() => {
  const r = jwtMemberRole.value;
  return r === "FOUNDER" || r === "OWNER";
});

const canMutateMembership = computed(() => isOwnerOrFounder.value);

const selfUserId = computed(() => readJwtUid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)));

const form = reactive({
  loginName: "",
  password: "",
  displayName: "",
  role: "MEMBER" as usersApi.TenantMemberRole,
});

function tenantOptionLabel(t: tenantsApi.TenantRow): string {
  return `${t.name}（${t.code}）`;
}

function canEditMemberRow(row: usersApi.TenantMemberRow): boolean {
  if (!canMutateMembership.value) return false;
  if (row.memberStatus !== "ACTIVE") return false;
  if (selfUserId.value != null && row.userId === selfUserId.value) return false;
  return true;
}

function canRemoveMemberRow(row: usersApi.TenantMemberRow): boolean {
  if (selfUserId.value != null && row.userId === selfUserId.value) return false;
  return true;
}

function canEditUserRole(row: usersApi.UserRow): boolean {
  if (!canMutateMembership.value) return false;
  if (row.tenantRole == null) return false;
  const self = readJwtUid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  if (self != null && row.id === self) return false;
  return true;
}

function statusLabel(s: usersApi.UserRow["status"]): string {
  return s === "ACTIVE" ? "启用" : "已禁用";
}

function openCreateDialog() {
  resetCreateForm();
  createVisible.value = true;
}

function resetCreateForm() {
  form.loginName = "";
  form.password = "";
  form.displayName = "";
  form.role = "MEMBER";
}

async function loadMembers() {
  loadingMembers.value = true;
  try {
    const params: {
      tenantId?: number;
      role?: usersApi.TenantMemberRole;
      includeInactive?: boolean;
    } = {};
    if (isFounder.value && tenantFilter.value != null) {
      params.tenantId = tenantFilter.value;
    }
    if (roleFilter.value) {
      params.role = roleFilter.value;
    }
    if (includeInactive.value && isOwnerOrFounder.value) {
      params.includeInactive = true;
    }
    members.value = await usersApi.listTenantMembers(params);
  } catch (e: unknown) {
    console.warn("[tenant-members]", e);
    ElMessage.error("成员列表加载失败，请稍后重试");
    members.value = [];
  } finally {
    loadingMembers.value = false;
  }
}

async function loadUsers() {
  loadingUsers.value = true;
  try {
    users.value = await usersApi.listUsers();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "账号列表加载失败"));
  } finally {
    loadingUsers.value = false;
  }
}

async function onMemberRoleChange(row: usersApi.TenantMemberRow, role: usersApi.TenantMemberRole) {
  if (row.role === role) return;
  try {
    const opts =
      isFounder.value && tenantFilter.value != null ? { tenantId: tenantFilter.value } : undefined;
    await usersApi.updateTenantMemberRole(row.userId, role, opts);
    row.role = role;
    ElMessage.success("已更新角色");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "更新失败"));
    await loadMembers();
  }
}

async function onUserRoleChange(row: usersApi.UserRow, role: usersApi.TenantMemberRole) {
  if (row.tenantRole === role) return;
  try {
    await usersApi.updateTenantMemberRole(row.id, role);
    row.tenantRole = role;
    ElMessage.success("已更新角色");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "更新失败"));
    await loadUsers();
  }
}

function openInvite() {
  resetInvite();
  inviteVisible.value = true;
}

function resetInvite() {
  inviteLoginName.value = "";
  inviteRole.value = "MEMBER";
}

async function submitInvite() {
  const name = inviteLoginName.value.trim();
  if (!name) {
    ElMessage.warning("请填写登录名");
    return;
  }
  inviteSubmitting.value = true;
  try {
    const body: { loginName: string; role: usersApi.TenantMemberRole; tenantId?: number } = {
      loginName: name,
      role: inviteRole.value,
    };
    if (isFounder.value && tenantFilter.value != null) {
      body.tenantId = tenantFilter.value;
    }
    await usersApi.inviteTenantMember(body);
    inviteVisible.value = false;
    ElMessage.success("已保存");
    await loadMembers();
    await loadUsers();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "操作失败"));
  } finally {
    inviteSubmitting.value = false;
  }
}

async function onRemoveMember(row: usersApi.TenantMemberRow) {
  try {
    await ElMessageBox.confirm(
      `将用户「${row.loginName}」从当前租户移出？其账号仍保留，仅本租户成员关系结束。`,
      "确认",
      { type: "warning" },
    );
  } catch {
    return;
  }
  try {
    const opts =
      isFounder.value && tenantFilter.value != null ? { tenantId: tenantFilter.value } : undefined;
    await usersApi.removeTenantMember(row.userId, opts);
    ElMessage.success("已移出");
    await loadMembers();
    await loadUsers();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "操作失败"));
  }
}

async function onCreate() {
  createSubmitting.value = true;
  try {
    await usersApi.createUser({
      loginName: form.loginName,
      password: form.password,
      displayName: form.displayName || undefined,
      role: form.role,
    });
    createVisible.value = false;
    resetCreateForm();
    await loadUsers();
    await loadMembers();
    ElMessage.success("已创建");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "创建失败"));
  } finally {
    createSubmitting.value = false;
  }
}

async function toggleStatus(u: usersApi.UserRow) {
  const next = u.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  await usersApi.updateUser(u.id, { status: next });
  await loadUsers();
  await loadMembers();
  ElMessage.success("已更新状态");
}

async function kickUser(u: usersApi.UserRow) {
  try {
    await ElMessageBox.confirm(`将用户「${u.loginName}」踢下线（需重新登录）?`, "确认", { type: "warning" });
    await usersApi.kickUserSession(u.id);
    ElMessage.success("已踢下线");
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, "操作失败"));
    }
  }
}

async function banUser(u: usersApi.UserRow) {
  try {
    await ElMessageBox.confirm(`封禁用户「${u.loginName}」? 将禁用账号并使当前登录失效。`, "确认", {
      type: "warning",
      confirmButtonText: "封禁",
    });
    await usersApi.banUser(u.id);
    await loadUsers();
    await loadMembers();
    ElMessage.success("已封禁");
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, "操作失败"));
    }
  }
}

async function removeUser(u: usersApi.UserRow) {
  try {
    await ElMessageBox.confirm(`确定删除（禁用）用户「${u.loginName}」?`, "确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  await usersApi.deleteUser(u.id);
  await loadUsers();
  await loadMembers();
  ElMessage.success("已删除");
}

onMounted(async () => {
  if (isFounder.value) {
    try {
      tenantOptions.value = await tenantsApi.listTenants();
      const jwtTid = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
      const n = jwtTid ? Number.parseInt(jwtTid, 10) : NaN;
      if (Number.isFinite(n) && tenantOptions.value.some((t) => t.id === n)) {
        tenantFilter.value = n;
      } else if (tenantOptions.value.length > 0) {
        tenantFilter.value = tenantOptions.value[0]!.id;
      }
    } catch {
      tenantOptions.value = [];
    }
  }
  await Promise.all([loadMembers(), loadUsers()]);
});
</script>

<style scoped>
.card {
  margin-bottom: 0;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.card--members {
  margin-bottom: 14px;
}

.hint {
  margin-bottom: 12px;
}

.hdr-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.hdr {
  font-weight: 600;
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-tenant {
  width: 220px;
}

.filter-role {
  width: 140px;
}

.filter-inactive {
  margin-right: 0;
  white-space: nowrap;
}

.role-select {
  width: 180px;
}

.role-select-inline {
  width: 168px;
}

.w-full-role {
  width: 100%;
}

.table-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-hdr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.form-grid {
  max-width: 100%;
}

.muted {
  color: #94a3b8;
  font-size: 13px;
}

/* :loading 时会在文案前插入图标，不设宽度则按钮变宽，hdr-row 的 space-between 下整块筛选区左缘会左移 */
.users-refresh-btn {
  flex-shrink: 0;
  min-width: 5.5rem;
}
</style>
