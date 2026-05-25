<template>
  <div class="users">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      :title="t('views.users.alertTitle')"
      :description="t('views.users.alertDesc')"
    />
    <el-card shadow="never" class="card card--members">
      <template #header>
        <div class="hdr-row">
          <span class="hdr">{{ t("views.users.hdrMembers") }}</span>
          <div class="filters">
            <el-select
              v-if="isFounder"
              v-model="tenantFilter"
              class="filter-tenant"
              filterable
              :placeholder="t('views.users.placeholderTenant')"
              clearable
              @change="onTenantFilterChange"
            >
              <el-option v-for="tenant in tenantOptions" :key="tenant.id" :label="tenantOptionLabel(tenant)" :value="tenant.id" />
            </el-select>
            <el-select
              v-model="roleFilter"
              class="filter-role"
              :placeholder="t('views.users.placeholderRole')"
              clearable
              @change="loadMembers"
            >
              <el-option :label="t('views.users.roleAll')" value="" />
              <el-option :label="t('views.users.roleFounder')" value="FOUNDER" />
              <el-option :label="t('views.users.roleOwner')" value="OWNER" />
              <el-option :label="t('views.users.roleAdmin')" value="ADMIN" />
              <el-option :label="t('views.users.roleMember')" value="MEMBER" />
            </el-select>
            <el-checkbox
              v-if="isOwnerOrFounder"
              v-model="includeInactive"
              class="filter-inactive"
              @change="loadMembers"
            >
              {{ t("views.users.includeInactive") }}
            </el-checkbox>
            <el-button v-if="canMutateMembership" type="primary" plain @click="openInvite">{{ t("views.users.invite") }}</el-button>
            <el-button class="users-refresh-btn" type="primary" plain :loading="loadingMembers" @click="loadMembers">
              {{ t("views.users.refresh") }}
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loadingMembers" :data="members" stripe border :empty-text="t('views.users.empty')">
        <el-table-column prop="accountNo" :label="t('views.users.colAccountNo')" min-width="148" show-overflow-tooltip />
        <el-table-column prop="loginName" :label="t('views.users.colLoginName')" min-width="120" />
        <el-table-column prop="email" :label="t('views.users.colEmail')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || t('common.dash') }}</template>
        </el-table-column>
        <el-table-column prop="phone" :label="t('views.users.colPhone')" width="120">
          <template #default="{ row }">{{ row.phone || t('common.dash') }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colRegChannel')" width="100">
          <template #default="{ row }">{{ registrationChannelLabel(row.registrationChannel) }}</template>
        </el-table-column>
        <el-table-column prop="displayName" :label="t('views.users.colNickname')" min-width="120" />
        <el-table-column :label="t('views.users.colAccountStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.userStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.userStatus === "ACTIVE" ? t("views.users.statusEnabled") : t("views.users.statusDisabled") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colMemberStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.memberStatus === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.memberStatus === "ACTIVE" ? t("views.users.statusActiveMember") : t("views.users.statusLeftMember") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colOnline')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sessionOnline ? 'success' : 'info'" size="small">
              {{ row.sessionOnline ? t("views.users.onlineYes") : t("views.users.onlineNo") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLogin')" min-width="168" show-overflow-tooltip>
          <template #default="{ row }">{{ formatLastLoginAt(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLoginIp')" min-width="132" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastLoginIp || t("common.dash") }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLoginRegion')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ formatLoginRegionDisplay(row.lastLoginRegion) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colRole')" min-width="220">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              class="role-select"
              :disabled="!canEditMemberRow(row)"
              @change="(v: string) => onMemberRoleChange(row, v as usersApi.TenantMemberRole)"
            >
              <el-option v-if="isFounder" :label="t('views.users.roleFounder')" value="FOUNDER" />
              <el-option :label="t('views.users.roleOwner')" value="OWNER" />
              <el-option :label="t('views.users.roleAdmin')" value="ADMIN" />
              <el-option :label="t('views.users.roleMember')" value="MEMBER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="canMutateMembership" :label="t('views.users.colActions')" width="112" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.memberStatus === 'ACTIVE' && canRemoveMemberRow(row)"
              link
              type="danger"
              size="small"
              @click="onRemoveMember(row)"
            >
              {{ t("views.users.removeFromTenant") }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="card card--accounts">
      <template #header>
        <div class="table-hdr">
          <span class="card-title">{{ t("views.users.hdrAccounts") }}</span>
          <div class="table-hdr-actions">
            <el-button type="primary" @click="openCreateDialog">{{ t("views.users.addUser") }}</el-button>
            <el-button class="users-refresh-btn" type="primary" plain :loading="loadingUsers" @click="loadUsers">
              {{ t("views.users.refresh") }}
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loadingUsers" :data="users" stripe border style="width: 100%" :empty-text="t('views.users.empty')">
        <el-table-column prop="accountNo" :label="t('views.users.colAccountNo')" min-width="148" show-overflow-tooltip />
        <el-table-column prop="loginName" :label="t('views.users.colLoginName')" min-width="120" />
        <el-table-column prop="email" :label="t('views.users.colEmail')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || t('common.dash') }}</template>
        </el-table-column>
        <el-table-column prop="phone" :label="t('views.users.colPhone')" width="120">
          <template #default="{ row }">{{ row.phone || t('common.dash') }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colRegChannel')" width="100">
          <template #default="{ row }">{{ registrationChannelLabel(row.registrationChannel) }}</template>
        </el-table-column>
        <el-table-column prop="displayName" :label="t('views.users.colNickname')" min-width="120" />
        <el-table-column :label="t('views.users.colTenantRole')" min-width="200">
          <template #default="{ row }">
            <el-select
              v-if="row.tenantRole != null"
              :model-value="row.tenantRole"
              size="small"
              class="role-select-inline"
              :disabled="!canEditUserRole(row)"
              @change="(v: string) => onUserRoleChange(row, v as usersApi.TenantMemberRole)"
            >
              <el-option v-if="isFounder" :label="t('views.users.roleFounder')" value="FOUNDER" />
              <el-option :label="t('views.users.roleOwner')" value="OWNER" />
              <el-option :label="t('views.users.roleAdmin')" value="ADMIN" />
              <el-option :label="t('views.users.roleMember')" value="MEMBER" />
            </el-select>
            <span v-else class="muted">{{ t("views.users.notInTenant") }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colOnline')" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sessionOnline ? 'success' : 'info'" size="small">
              {{ row.sessionOnline ? t("views.users.onlineYes") : t("views.users.onlineNo") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLogin')" min-width="168" show-overflow-tooltip>
          <template #default="{ row }">{{ formatLastLoginAt(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLoginIp')" min-width="132" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastLoginIp || t("common.dash") }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colLastLoginRegion')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ formatLoginRegionDisplay(row.lastLoginRegion) }}</template>
        </el-table-column>
        <el-table-column :label="t('views.users.colStatus')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.users.colActions')" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.sessionOnline && canManageAccountRow(row)"
              link
              type="warning"
              size="small"
              @click="kickUser(row)"
            >
              {{ t("views.users.kick") }}
            </el-button>
            <el-button
              v-if="canManageAccountRow(row)"
              link
              type="danger"
              size="small"
              @click="banUser(row)"
            >
              {{ t("views.users.ban") }}
            </el-button>
            <el-button
              v-if="canToggleAccountRow(row)"
              link
              type="primary"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ t("views.users.toggle") }}
            </el-button>
            <el-button
              v-if="canManageAccountRow(row)"
              link
              type="danger"
              size="small"
              @click="removeUser(row)"
            >
              {{ t("views.users.delete") }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="inviteVisible" :title="t('views.users.dlgInviteTitle')" width="440px" destroy-on-close @closed="resetInvite">
      <el-form label-width="96px">
        <el-form-item :label="t('views.users.dlgInviteLoginLabel')" required>
          <el-input
            v-model="inviteLoginName"
            autocomplete="off"
            clearable
            :placeholder="t('views.users.dlgInviteLoginPh')"
            class="w-full-role"
          />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgInviteRole')" required>
          <el-select v-model="inviteRole" class="w-full-role">
            <el-option v-if="isFounder" :label="t('views.users.roleFounder')" value="FOUNDER" />
            <el-option :label="t('views.users.roleOwner')" value="OWNER" />
            <el-option :label="t('views.users.roleAdmin')" value="ADMIN" />
            <el-option :label="t('views.users.roleMember')" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteVisible = false">{{ t("views.users.cancel") }}</el-button>
        <el-button type="primary" :loading="inviteSubmitting" @click="submitInvite">{{ t("views.users.ok") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" :title="t('views.users.dlgCreateTitle')" width="520px" destroy-on-close @closed="resetCreateForm">
      <el-form :model="form" label-width="88px" class="form-grid" @submit.prevent="onCreate">
        <el-form-item :label="t('views.users.dlgCreateLogin')" required>
          <el-input v-model="form.loginName" autocomplete="off" clearable />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgCreateEmail')">
          <el-input v-model="form.email" type="email" autocomplete="off" clearable :placeholder="t('views.users.dlgCreateOptionalPh')" />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgCreatePhone')">
          <el-input v-model="form.phone" autocomplete="off" clearable :placeholder="t('views.users.dlgCreateOptionalPh')" />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgCreatePassword')" required>
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgCreateNickname')">
          <el-input v-model="form.displayName" clearable :placeholder="t('views.users.dlgCreateNicknamePh')" />
        </el-form-item>
        <el-form-item :label="t('views.users.dlgCreateRole')">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-if="isFounder" :label="t('views.users.roleFounder')" value="FOUNDER" />
            <el-option :label="t('views.users.roleMember')" value="MEMBER" />
            <el-option :label="t('views.users.roleAdmin')" value="ADMIN" />
            <el-option :label="t('views.users.roleOwner')" value="OWNER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t("views.users.cancel") }}</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="onCreate">{{ t("views.users.create") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { AI_ADMIN_WORKSPACE_CHANGED_EVENT } from "@/constants/adminWorkspace";
import * as tenantsApi from "@/api/tenants";
import * as usersApi from "@/api/users";
import { sameLoginName } from "@/utils/accountPrincipal";
import { readJwtSubject, readJwtTid, readJwtTmr } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t, locale } = useI18n();

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

const selfLoginName = computed(() => readJwtSubject(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)));

const form = reactive({
  loginName: "",
  email: "",
  phone: "",
  password: "",
  displayName: "",
  role: "MEMBER" as usersApi.TenantMemberRole,
});

function tenantOptionLabel(t: tenantsApi.TenantRow): string {
  return `${t.name}（${t.code}）`;
}

function isSelfUser(loginName: string): boolean {
  return sameLoginName(loginName, selfLoginName.value);
}

function effectiveTenantId(): number | undefined {
  if (isFounder.value && tenantFilter.value != null) {
    return tenantFilter.value;
  }
  const jwtTid = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  const n = jwtTid ? Number.parseInt(jwtTid, 10) : NaN;
  return Number.isFinite(n) ? n : undefined;
}

function roleChangeTenantOpts(row?: usersApi.TenantMemberRow): { tenantId?: number } | undefined {
  if (row != null && isFounder.value) {
    return { tenantId: row.tenantId };
  }
  if (isFounder.value) {
    const tid = effectiveTenantId();
    return tid != null ? { tenantId: tid } : undefined;
  }
  return undefined;
}

function canManageAccountRow(row: usersApi.UserRow): boolean {
  return !isSelfUser(row.loginName);
}

function canToggleAccountRow(row: usersApi.UserRow): boolean {
  return canManageAccountRow(row);
}

function canEditMemberRow(row: usersApi.TenantMemberRow): boolean {
  if (!canMutateMembership.value) return false;
  if (row.memberStatus !== "ACTIVE") return false;
  if (isSelfUser(row.loginName)) return false;
  return true;
}

function canRemoveMemberRow(row: usersApi.TenantMemberRow): boolean {
  if (isSelfUser(row.loginName)) return false;
  return true;
}

function canEditUserRole(row: usersApi.UserRow): boolean {
  if (!canMutateMembership.value) return false;
  if (row.tenantRole == null) return false;
  if (isSelfUser(row.loginName)) return false;
  return true;
}

function founderInviteTenantId(): number | undefined {
  if (tenantFilter.value != null) {
    return tenantFilter.value;
  }
  const jwtTid = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  const n = jwtTid ? Number.parseInt(jwtTid, 10) : NaN;
  return Number.isFinite(n) ? n : undefined;
}

function syncTenantFilterFromWorkspace() {
  if (!isFounder.value) {
    return;
  }
  const jwtTid = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  const n = jwtTid ? Number.parseInt(jwtTid, 10) : NaN;
  if (Number.isFinite(n) && tenantOptions.value.some((t) => t.id === n)) {
    tenantFilter.value = n;
  }
}

function onWorkspaceChanged() {
  syncTenantFilterFromWorkspace();
  void loadMembers();
  void loadUsers();
}

function statusLabel(s: usersApi.UserRow["status"]): string {
  return s === "ACTIVE" ? t("views.users.statusEnabled") : t("views.users.statusDisabled");
}

function registrationChannelLabel(ch: usersApi.UserRegistrationChannel): string {
  const key = `views.users.regChannel.${ch}` as const;
  const msg = t(key);
  return msg === key ? ch : msg;
}

function formatLastLoginAt(raw: string | null | undefined): string {
  if (!raw) return t("common.dash");
  const s = raw.trim();
  // 后端已格式化为东八区墙钟 yyyy-MM-dd HH:mm:ss 时直接展示
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(s)) {
    return s;
  }
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const loc = String(locale.value || "zh-CN").replace("_", "-");
  return d.toLocaleString(loc, { timeZone: "Asia/Shanghai", hour12: false });
}

/** 展示「国家码」或「国家码|省|市」：CN 多段时前缀「中国」，段间用 · */
function formatLoginRegionDisplay(raw: string | null | undefined): string {
  if (!raw) return t("common.dash");
  const s = raw.trim();
  if (!s) return t("common.dash");
  const parts = s
    .split("|")
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
  if (parts.length <= 1) return s;
  const cc = parts[0].toUpperCase();
  const rest = parts.slice(1).join(" · ");
  if (cc === "CN") return t("views.users.loginRegionCn", { rest });
  return t("views.users.loginRegionIntl", { cc, rest });
}

function openCreateDialog() {
  resetCreateForm();
  createVisible.value = true;
}

function resetCreateForm() {
  form.loginName = "";
  form.email = "";
  form.phone = "";
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
    ElMessage.error(t("views.users.loadMembersFailed"));
    members.value = [];
  } finally {
    loadingMembers.value = false;
  }
}

async function loadUsers() {
  loadingUsers.value = true;
  try {
    const tid = effectiveTenantId();
    users.value = await usersApi.listUsers(
      1,
      50,
      isFounder.value && tid != null ? { tenantId: tid } : undefined,
    );
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.loadUsersFailed"), locale.value));
  } finally {
    loadingUsers.value = false;
  }
}

async function onMemberRoleChange(row: usersApi.TenantMemberRow, role: usersApi.TenantMemberRole) {
  if (row.role === role) return;
  try {
    await usersApi.updateTenantMemberRole(row, role, roleChangeTenantOpts(row));
    row.role = role;
    ElMessage.success(t("views.users.roleUpdated"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.updateFailed"), locale.value));
    await loadMembers();
  }
}

async function onUserRoleChange(row: usersApi.UserRow, role: usersApi.TenantMemberRole) {
  if (row.tenantRole === role) return;
  try {
    await usersApi.updateTenantMemberRole(row, role, roleChangeTenantOpts());
    row.tenantRole = role;
    ElMessage.success(t("views.users.roleUpdated"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.updateFailed"), locale.value));
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
    ElMessage.warning(t("views.users.fillLoginName"));
    return;
  }
  inviteSubmitting.value = true;
  try {
    const body: { loginName: string; role: usersApi.TenantMemberRole; tenantId?: number } = {
      loginName: name,
      role: inviteRole.value,
    };
    if (isFounder.value) {
      const tid = founderInviteTenantId();
      if (tid != null) {
        body.tenantId = tid;
      }
    }
    await usersApi.inviteTenantMember(body);
    inviteVisible.value = false;
    ElMessage.success(t("views.users.saved"));
    await loadMembers();
    await loadUsers();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
  } finally {
    inviteSubmitting.value = false;
  }
}

function messageBoxButtons() {
  return {
    confirmButtonText: t("common.confirm"),
    cancelButtonText: t("common.cancel"),
  };
}

function onTenantFilterChange() {
  void loadMembers();
  void loadUsers();
}

async function onRemoveMember(row: usersApi.TenantMemberRow) {
  try {
    await ElMessageBox.confirm(
      t("views.users.confirmRemoveMember", { name: row.loginName }),
      t("common.confirmTitle"),
      { type: "warning", ...messageBoxButtons() },
    );
  } catch {
    return;
  }
  try {
    await usersApi.removeTenantMember(row, roleChangeTenantOpts(row));
    ElMessage.success(t("views.users.removed"));
    await loadMembers();
    await loadUsers();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
  }
}

async function onCreate() {
  createSubmitting.value = true;
  try {
    await usersApi.createUser({
      loginName: form.loginName.trim(),
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      password: form.password,
      displayName: form.displayName.trim() || undefined,
      role: form.role,
    });
    createVisible.value = false;
    resetCreateForm();
    await loadUsers();
    await loadMembers();
    ElMessage.success(t("views.users.created"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.createFailed"), locale.value));
  } finally {
    createSubmitting.value = false;
  }
}

async function toggleStatus(u: usersApi.UserRow) {
  if (!canToggleAccountRow(u)) {
    ElMessage.warning(t("views.users.cannotOperateSelf"));
    return;
  }
  const next = u.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const action =
    next === "ACTIVE" ? t("views.users.toggleEnable") : t("views.users.toggleDisable");
  try {
    await ElMessageBox.confirm(
      t("views.users.toggleConfirm", { name: u.loginName, action }),
      t("common.confirmTitle"),
      { type: "warning", ...messageBoxButtons() },
    );
  } catch {
    return;
  }
  try {
    await usersApi.updateUser(u, { status: next });
    await loadUsers();
    await loadMembers();
    ElMessage.success(t("views.users.statusUpdated"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
  }
}

async function kickUser(u: usersApi.UserRow) {
  if (!u.sessionOnline || !canManageAccountRow(u)) {
    return;
  }
  try {
    await ElMessageBox.confirm(t("views.users.kickConfirm", { name: u.loginName }), t("common.confirmTitle"), {
      type: "warning",
      ...messageBoxButtons(),
    });
    await usersApi.kickUserSession(u);
    ElMessage.success(t("views.users.kicked"));
    await loadUsers();
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
    }
  }
}

async function banUser(u: usersApi.UserRow) {
  if (!canManageAccountRow(u)) {
    ElMessage.warning(t("views.users.cannotOperateSelf"));
    return;
  }
  try {
    await ElMessageBox.confirm(t("views.users.banConfirm", { name: u.loginName }), t("common.confirmTitle"), {
      type: "warning",
      confirmButtonText: t("views.users.banOk"),
      cancelButtonText: t("common.cancel"),
    });
    await usersApi.banUser(u);
    await loadUsers();
    await loadMembers();
    ElMessage.success(t("views.users.banned"));
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
    }
  }
}

async function removeUser(u: usersApi.UserRow) {
  if (!canManageAccountRow(u)) {
    ElMessage.warning(t("views.users.cannotOperateSelf"));
    return;
  }
  try {
    await ElMessageBox.confirm(t("views.users.deleteConfirm", { name: u.loginName }), t("common.confirmTitle"), {
      type: "warning",
      confirmButtonText: t("views.users.delete"),
      cancelButtonText: t("common.cancel"),
    });
  } catch {
    return;
  }
  try {
    await usersApi.deleteUser(u);
    await loadUsers();
    await loadMembers();
    ElMessage.success(t("views.users.deleted"));
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.users.opFailed"), locale.value));
  }
}

onMounted(async () => {
  if (isFounder.value) {
    try {
      tenantOptions.value = await tenantsApi.listTenants();
      syncTenantFilterFromWorkspace();
      if (tenantFilter.value == null && tenantOptions.value.length > 0) {
        tenantFilter.value = tenantOptions.value[0]!.id;
      }
    } catch {
      tenantOptions.value = [];
    }
  }
  window.addEventListener(AI_ADMIN_WORKSPACE_CHANGED_EVENT, onWorkspaceChanged);
  await Promise.all([loadMembers(), loadUsers()]);
});

onBeforeUnmount(() => {
  window.removeEventListener(AI_ADMIN_WORKSPACE_CHANGED_EVENT, onWorkspaceChanged);
});
</script>

<style scoped>
.card {
  margin-bottom: 0;
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
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
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

/* :loading 时会在文案前插入图标，不设宽度则按钮变宽，hdr-row 的 space-between 下整块筛选区左缘会左移 */
.users-refresh-btn {
  flex-shrink: 0;
  min-width: 5.5rem;
}
</style>
