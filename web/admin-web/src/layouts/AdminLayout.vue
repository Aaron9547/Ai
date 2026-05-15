<template>
  <el-container class="admin-shell">
    <el-aside width="240px" class="admin-aside">
      <div class="brand">
        <img
          v-if="brandLogoSrc"
          class="brand-logo"
          :src="brandLogoSrc"
          alt=""
        />
        <span v-else class="brand-mark" aria-hidden="true" />
        <div class="brand-text">
          <span class="brand-title">{{ displayBrandTitle }}</span>
          <span class="brand-sub">{{ t("admin.brandSub") }}</span>
        </div>
      </div>

      <el-scrollbar class="admin-aside-scroll" height="100%">
        <el-menu
          router
          :default-active="sideMenuActivePath"
          unique-opened
          class="side-menu"
          background-color="transparent"
          text-color="#94a3b8"
          active-text-color="#e0e7ff"
        >
        <el-menu-item v-if="menuAllowed('DASHBOARD')" index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>{{ t("admin.menu.dashboard") }}</span>
        </el-menu-item>
        <!-- 1. 模型、工具与知识数据（中台能力） -->
        <el-sub-menu
          v-if="menuAllowed('LLM_MODELS') || menuAllowed('MCP_SERVERS') || menuAllowed('RAG_KBS')"
          index="grp-model-knowledge"
        >
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>{{ t("admin.menu.modelKnowledge") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('LLM_MODELS')" index="/model/llm-models">
            <el-icon><Setting /></el-icon>
            <span>{{ t("admin.menu.llmModels") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('MCP_SERVERS')" index="/mcp/servers">
            <el-icon><Connection /></el-icon>
            <span>{{ t("admin.menu.mcpServers") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('RAG_KBS')" index="/knowledge-center/knowledge-bases">
            <el-icon><Reading /></el-icon>
            <span>{{ t("admin.menu.knowledgeCenter") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 2. 当前租户内成员账号与画像记忆（非「组织架构」） -->
        <el-sub-menu v-if="menuAllowed('USERS') || menuAllowed('USER_PROFILES')" index="grp-members">
          <template #title>
            <el-icon><User /></el-icon>
            <span>{{ t("admin.menu.membersProfiles") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('USERS')" index="/users">
            <el-icon><Avatar /></el-icon>
            <span>{{ t("admin.menu.users") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('USER_PROFILES')" index="/users/profiles">
            <el-icon><UserFilled /></el-icon>
            <span>{{ t("admin.menu.userProfiles") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 3. 对话侧运营：内容日志与策略（与网关 HTTP 日志区分） -->
        <el-sub-menu v-if="menuAllowed('CHAT') || menuAllowed('CHAT_INTENTS')" index="grp-chat-risk">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ t("admin.menu.chatRisk") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('CHAT')" index="/chat/conversations">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ t("admin.menu.chatConversations") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('CHAT')" index="/chat/sensitive-terms">
            <el-icon><Warning /></el-icon>
            <span>{{ t("admin.menu.sensitiveTerms") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('CHAT_INTENTS')" index="/chat/intents">
            <el-icon><Promotion /></el-icon>
            <span>{{ t("admin.menu.intents") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 4. 接入层：网关策略与原始 HTTP 访问轨迹 -->
        <el-sub-menu
          v-if="menuAllowed('GATEWAY_API') || menuAllowed('ACCESS_LOGS')"
          index="grp-gateway"
        >
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>{{ t("admin.menu.gateway") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('GATEWAY_API')" index="/gateway/api-rate-limits">
            <el-icon><Setting /></el-icon>
            <span>{{ t("admin.menu.apiRateLimits") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('GATEWAY_API')" index="/gateway/cors-origins">
            <el-icon><Link /></el-icon>
            <span>{{ t("admin.menu.corsOrigins") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('ACCESS_LOGS')" index="/gateway/access-logs">
            <el-icon><TrendCharts /></el-icon>
            <span>{{ t("admin.menu.accessLogs") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 5. 合规留痕与计费类用量 -->
        <el-sub-menu
          v-if="menuAllowed('AUDIT_EVENTS') || menuAllowed('METERING')"
          index="grp-audit-metering"
        >
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>{{ t("admin.menu.auditMetering") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('AUDIT_EVENTS')" index="/audit/events">
            <el-icon><Document /></el-icon>
            <span>{{ t("admin.menu.auditEvents") }}</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('METERING')" index="/billing/metering">
            <el-icon><Histogram /></el-icon>
            <span>{{ t("admin.menu.metering") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 6. 本租户业务开关类参数（与平台级菜单/租户 CRUD 区分） -->
        <el-sub-menu v-if="menuAllowed('SYSTEM_SETTINGS')" index="grp-tenant-settings">
          <template #title>
            <el-icon><Tools /></el-icon>
            <span>{{ t("admin.menu.tenantSettings") }}</span>
          </template>
          <el-menu-item index="/system/runtime-settings">
            <el-icon><Setting /></el-icon>
            <span>{{ t("admin.menu.runtimeSettings") }}</span>
          </el-menu-item>
          <el-menu-item index="/system/tenant-shell-config">
            <el-icon><Picture /></el-icon>
            <span>{{ t("admin.menu.tenantShell") }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 7. 跨租户平台治理（创始人） -->
        <el-sub-menu v-if="isFounder && (menuAllowed('TENANTS') || menuAllowed('MENU_CATALOG'))" index="grp-platform">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>{{ t("admin.menu.platform") }}</span>
          </template>
          <el-menu-item v-if="menuAllowed('TENANTS')" index="/tenant/tenants">
            <el-icon><House /></el-icon>
            <span>{{ t("admin.menu.tenantManage") }}</span>
          </el-menu-item>
          <el-menu-item v-if="isFounder && menuAllowed('MENU_CATALOG')" index="/system/menu-items">
            <el-icon><MenuIcon /></el-icon>
            <span>{{ t("admin.menu.menuCatalog") }}</span>
          </el-menu-item>
        </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container direction="vertical">
      <el-header height="56px" class="top-bar">
        <div class="crumb">{{ pageTitle }}</div>
        <div class="header-right">
          <LocaleThemeToolbar />
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            popper-class="admin-user-dropdown"
            @command="onUserMenuCommand"
          >
            <span class="user-trigger" role="button" tabindex="0" aria-haspopup="menu">
              <span class="user-avatar" aria-hidden="true">{{ avatarLetter }}</span>
              <span class="user-name">{{ displayUserLabel }}</span>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="showWorkspaceSwitch" disabled class="user-menu-hint">
                  <div class="hint-workspace">
                    <span class="hint-workspace-label">{{ t("common.currentWorkspace") }}</span>
                    <span class="hint-workspace-value">{{ workspaceSummary }}</span>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item
                  v-for="row in workspacePickRows"
                  :key="`${row.tenantId}:${row.role}`"
                  :command="workspaceCommand(row)"
                  :disabled="row.isCurrent"
                  class="workspace-pick-item"
                >
                  <span class="workspace-pick-label">{{ row.label }}</span>
                  <el-tag v-if="row.isCurrent" type="info" size="small" class="workspace-current-tag">{{
                    t("common.current")
                  }}</el-tag>
                </el-dropdown-item>
                <el-dropdown-item command="relogin" :divided="showWorkspaceSwitch">{{ t("common.relogin") }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>{{ t("common.logout") }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <el-scrollbar class="admin-main-scrollbar" height="100%">
          <div class="admin-main-scrollbar-inner">
            <RouterView />
          </div>
        </el-scrollbar>
      </el-main>
      <el-footer class="admin-shell-footer" height="auto">{{ footerLine }}</el-footer>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {
  ArrowDown,
  Avatar,
  ChatDotRound,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  Histogram,
  House,
  Link,
  Menu as MenuIcon,
  Monitor,
  Odometer,
  OfficeBuilding,
  Picture,
  Promotion,
  Reading,
  Setting,
  Tools,
  TrendCharts,
  User,
  UserFilled,
  Warning,
} from "@element-plus/icons-vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { ElLoading, ElMessage, ElMessageBox } from "element-plus";
import LocaleThemeToolbar from "@/components/LocaleThemeToolbar.vue";
import * as tenantShellConfigApi from "@/api/tenantShellConfig";
import * as tenantsApi from "@/api/tenants";
import { AI_ADMIN_TENANT_SHELL_CHANGED_EVENT, AI_ADMIN_WORKSPACE_CHANGED_EVENT } from "@/constants/adminWorkspace";
import { postAdminContextSwitch } from "@/api/authAdminContext";
import { fetchAdminMe, type AdminMeMembership, type AdminMeView } from "@/api/adminMe";
import {
  AI_ADMIN_ACCESS_TOKEN_KEY,
  AI_ADMIN_EFFECTIVE_TENANT_KEY,
  AI_ADMIN_MEMBERSHIPS_KEY,
} from "@/plugins/http";
import { readJwtSubject, readJwtTid, readJwtTmr, readJwtTms } from "@/utils/jwtSubject";
import { formatTenantNameCode } from "@/utils/adminListDisplay";

const router = useRouter();
const route = useRoute();
const { t, te } = useI18n();

/** 知识库工作台等子路径在侧栏高亮「知识中心」入口 */
const sideMenuActivePath = computed(() => {
  if (route.path.startsWith("/knowledge-center")) {
    return "/knowledge-center/knowledge-bases";
  }
  return route.path;
});

const pageTitle = computed(() => {
  if (/\/knowledge-center\/workspace\/\d+\/documents\/\d+\/chunks$/.test(route.path)) {
    return t("admin.titles.chunks");
  }
  const pathKey = `admin.titles.${route.path}`;
  if (te(pathKey)) return t(pathKey);
  return t("admin.defaultPageTitle");
});

const displayBrandTitle = computed(() => {
  const r = shellBranding.value?.portalTitleResolved?.trim();
  if (r) return r;
  return t("admin.brandTitle");
});

const footerLine = computed(() => {
  const ft = shellBranding.value?.footerText?.trim();
  if (ft) return ft;
  return t("admin.shell.defaultFooter");
});

const displayUserLabel = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return t("common.authSkipPreview");
  }
  const me = meSnapshot.value;
  const name = me?.displayName?.trim();
  if (name) {
    return name;
  }
  const sub = readJwtSubject(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  return sub ?? t("common.notLoggedIn");
});

const isFounder = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return false;
  }
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const tenantOptions = ref<tenantsApi.TenantRow[]>([]);

/** 侧栏 / 页脚：租户壳配置（独立接口） */
const shellBranding = ref<tenantShellConfigApi.TenantShellBranding | null>(null);

function resolvePublicAssetUrl(raw: string | undefined | null): string {
  const u = (raw ?? "").trim();
  if (!u) return "";
  if (/^https?:\/\//i.test(u)) return u;
  const base = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");
  const path = u.startsWith("/") ? u : `/${u}`;
  return base ? `${base}${path}` : path;
}

const brandLogoSrc = computed(() => resolvePublicAssetUrl(shellBranding.value?.logoUrl));

/** 最近一次 /admin/me（用于下拉展示租户名等） */
const meSnapshot = ref<AdminMeView | null>(null);

const allowedMenuCodes = ref<string[] | null>(null);

type WorkspacePickRow = {
  tenantId: number;
  role: string;
  label: string;
  isCurrent: boolean;
};

function roleLabel(code: string): string {
  const key = `roles.${code}`;
  return te(key) ? String(t(key)) : code;
}

function resolveTenantDisplayName(tid: string): string {
  const n = Number.parseInt(tid, 10);
  if (!Number.isFinite(n)) return tid;
  const fromMembership = meSnapshot.value?.memberships.find((x) => x.tenantId === n);
  if (fromMembership?.tenantName?.trim()) {
    return fromMembership.tenantName.trim();
  }
  const optRow = tenantOptions.value.find((t) => t.id === n);
  if (optRow) {
    return formatTenantNameCode({ tenantName: optRow.name, tenantCode: optRow.code });
  }
  const code = fromMembership?.tenantCode?.trim();
  if (code) return code;
  return t("common.currentTenant");
}

function membershipTenantDisplay(m: AdminMeMembership): string {
  const n = m.tenantName?.trim();
  if (n) return n;
  const c = m.tenantCode?.trim();
  if (c) return c;
  const optRow = tenantOptions.value.find((t) => t.id === m.tenantId);
  if (optRow) {
    return formatTenantNameCode({ tenantName: optRow.name, tenantCode: optRow.code });
  }
  return t("common.tenantInfoPending");
}

function markWorkspaceCurrent(rows: WorkspacePickRow[]): WorkspacePickRow[] {
  const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
  const tid = readJwtTid(token);
  const tmr = readJwtTmr(token);
  return rows.map((r) => ({
    ...r,
    isCurrent: Boolean(tid && tmr && String(r.tenantId) === tid && r.role === tmr),
  }));
}

/** 可切换的「租户（角色）」列表；仅当条数 > 1 时展示切换区。 */
const workspacePickRows = computed((): WorkspacePickRow[] => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return [];
  const me = meSnapshot.value;
  if (!me?.memberships?.length) return [];

  const elevated = me.memberships.filter((m) => m.role !== "MEMBER");
  const seen = new Set<string>();
  const acc: WorkspacePickRow[] = [];
  const pushUnique = (tenantId: number, role: string, label: string) => {
    const key = `${tenantId}:${role}`;
    if (seen.has(key)) return;
    seen.add(key);
    acc.push({ tenantId, role, label, isCurrent: false });
  };

  if (isFounder.value) {
    if (elevated.length > 1) {
      for (const m of elevated) {
        pushUnique(m.tenantId, m.role, `${membershipTenantDisplay(m)}（${roleLabel(m.role)}）`);
      }
      return markWorkspaceCurrent(acc);
    }
    const tenants = tenantOptions.value;
    if (tenants.length > 1) {
      for (const t of tenants) {
        pushUnique(
          t.id,
          "FOUNDER",
          `${formatTenantNameCode({ tenantName: t.name, tenantCode: t.code })}（${roleLabel("FOUNDER")}）`,
        );
      }
      return markWorkspaceCurrent(acc);
    }
    return [];
  }

  if (elevated.length <= 1) return [];
  for (const m of elevated) {
    pushUnique(m.tenantId, m.role, `${membershipTenantDisplay(m)}（${roleLabel(m.role)}）`);
  }
  return markWorkspaceCurrent(acc);
});

const showWorkspaceSwitch = computed(() => workspacePickRows.value.length > 1);

const workspaceSummary = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return "";
  const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
  const tid = readJwtTid(token);
  const tmr = readJwtTmr(token);
  if (!tid || !tmr) return t("common.dash");
  const hit = workspacePickRows.value.find((r) => r.isCurrent);
  if (hit) return hit.label;
  return `${resolveTenantDisplayName(tid)}（${roleLabel(tmr)}）`;
});

function workspaceCommand(row: WorkspacePickRow): string {
  return `ws:${row.tenantId}:${row.role}`;
}

async function applyWorkspaceSwitch(tenantId: number, role: string) {
  try {
    await ElMessageBox.confirm(
      t("admin.workspace.switchConfirmMsg"),
      t("admin.workspace.switchConfirmTitle"),
      {
        type: "warning",
        confirmButtonText: t("admin.workspace.switchConfirmOk"),
        cancelButtonText: t("common.cancel"),
      },
    );
  } catch {
    return;
  }
  const loading = ElLoading.service({
    lock: true,
    fullscreen: true,
    text: t("common.workspaceSwitching"),
    background: "rgba(0, 0, 0, 0.25)",
  });
  try {
    const data = await postAdminContextSwitch({ tenantId, role });
    localStorage.setItem(AI_ADMIN_ACCESS_TOKEN_KEY, data.accessToken);
    localStorage.removeItem(AI_ADMIN_EFFECTIVE_TENANT_KEY);
    if (data.memberships?.length) {
      localStorage.setItem(AI_ADMIN_MEMBERSHIPS_KEY, JSON.stringify(data.memberships));
    } else {
      localStorage.removeItem(AI_ADMIN_MEMBERSHIPS_KEY);
    }
    const fresh = await fetchAdminMe().catch(() => null);
    meSnapshot.value = fresh;
    allowedMenuCodes.value = fresh?.allowedMenuCodes ?? [];
    if (readJwtTmr(data.accessToken) === "FOUNDER") {
      void tenantsApi.listTenants().then((r) => {
        tenantOptions.value = r;
      });
    }
    ElMessage.success(t("common.workspaceSwitched"));
    window.dispatchEvent(new Event(AI_ADMIN_WORKSPACE_CHANGED_EVENT));
    void reloadShellBranding();
  } catch (e: unknown) {
    console.warn("[workspace switch]", e);
    ElMessage.error(t("common.workspaceSwitchFailed"));
  } finally {
    loading.close();
  }
}

function menuAllowed(code: string): boolean {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return true;
  }
  const list = allowedMenuCodes.value;
  if (list === null) {
    return true;
  }
  const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
  if (readJwtTmr(token) === "FOUNDER") {
    return true;
  }
  return list.includes(code);
}

async function reloadShellBranding() {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return;
  }
  if (!localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) {
    shellBranding.value = null;
    return;
  }
  try {
    const cfg = await tenantShellConfigApi.getTenantShellConfig();
    shellBranding.value = cfg.branding;
  } catch {
    shellBranding.value = null;
  }
}

function onTenantShellChanged() {
  void reloadShellBranding();
}

onMounted(() => {
  window.addEventListener(AI_ADMIN_TENANT_SHELL_CHANGED_EVENT, onTenantShellChanged);
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP !== "true") {
    void fetchAdminMe()
      .then((me) => {
        meSnapshot.value = me;
        allowedMenuCodes.value = me.allowedMenuCodes ?? [];
      })
      .catch(() => {
        allowedMenuCodes.value = [];
      });
    void reloadShellBranding();
  }
  if (isFounder.value) {
    void tenantsApi
      .listTenants()
      .then((r) => {
        tenantOptions.value = r;
      })
      .catch(() => {
        tenantOptions.value = [];
      });
  }
});

onUnmounted(() => {
  window.removeEventListener(AI_ADMIN_TENANT_SHELL_CHANGED_EVENT, onTenantShellChanged);
});

const avatarLetter = computed(() => {
  const name = displayUserLabel.value;
  if (!name || name === t("common.notLoggedIn") || name === t("common.authSkipPreview")) return "?";
  return name.slice(0, 1).toUpperCase();
});

function onUserMenuCommand(cmd: string) {
  if (cmd.startsWith("ws:")) {
    const m = /^ws:(\d+):([A-Z_]+)$/.exec(cmd);
    if (!m) return;
    const tenantId = Number.parseInt(m[1]!, 10);
    const role = m[2]!;
    if (!Number.isFinite(tenantId) || !role) return;
    void applyWorkspaceSwitch(tenantId, role);
  } else if (cmd === "relogin") {
    localStorage.removeItem(AI_ADMIN_ACCESS_TOKEN_KEY);
    localStorage.removeItem(AI_ADMIN_EFFECTIVE_TENANT_KEY);
    localStorage.removeItem(AI_ADMIN_MEMBERSHIPS_KEY);
    void router.replace({ path: "/login", query: { redirect: route.fullPath } });
  } else if (cmd === "logout") {
    localStorage.removeItem(AI_ADMIN_ACCESS_TOKEN_KEY);
    localStorage.removeItem(AI_ADMIN_EFFECTIVE_TENANT_KEY);
    localStorage.removeItem(AI_ADMIN_MEMBERSHIPS_KEY);
    void router.replace("/login");
  }
}
</script>

<style scoped>
.side-menu {
  padding: 4px 0 16px;
  border-right: none;
  min-height: min-content;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px 2px 2px;
  margin: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: #334155;
  outline: none;
  transition: background 0.12s ease, color 0.12s ease;
}

.user-trigger:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(145deg, #6366f1, #4338ca);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}

.user-caret {
  font-size: 11px;
  color: #94a3b8;
  flex-shrink: 0;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.admin-main {
  padding: 0 !important;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-main-scrollbar {
  flex: 1;
  min-height: 0;
}

.admin-main-scrollbar :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.admin-main-scrollbar-inner {
  min-height: 100%;
  box-sizing: border-box;
}

</style>

<!-- 下拉层 teleport 到 body，须用非 scoped 配合 popper-class -->
<style>
.admin-user-dropdown.el-popper {
  min-width: 320px;
  max-width: min(480px, calc(100vw - 24px));
}

.admin-user-dropdown .el-dropdown-menu {
  padding: 6px 0;
}

.admin-user-dropdown .el-dropdown-menu__item.user-menu-hint {
  min-height: auto;
  padding: 10px 16px;
  line-height: 1.45;
  max-width: none;
  height: auto;
}

.admin-user-dropdown .el-dropdown-menu__item.user-menu-hint.is-disabled {
  cursor: default;
  opacity: 1;
  color: inherit;
}

.admin-user-dropdown .hint-workspace {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 14px;
  row-gap: 2px;
  align-items: start;
  width: 100%;
}

.admin-user-dropdown .hint-workspace-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
  padding-top: 1px;
  white-space: nowrap;
}

.admin-user-dropdown .hint-workspace-value {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  word-break: break-word;
  white-space: normal;
}

.admin-user-dropdown .el-dropdown-menu__item.workspace-pick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 40px;
}

.admin-user-dropdown .workspace-pick-label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #334155;
}

.admin-user-dropdown .workspace-current-tag {
  flex-shrink: 0;
}
</style>
