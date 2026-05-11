<template>
  <el-container class="admin-shell">
    <el-aside width="240px" class="admin-aside">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true" />
        <div class="brand-text">
          <span class="brand-title">Ai 控制台</span>
          <span class="brand-sub">运营与中台</span>
        </div>
      </div>

      <div class="admin-aside-scroll">
        <el-menu
          router
          :default-active="sideMenuActivePath"
          unique-opened
          class="side-menu"
          background-color="transparent"
          text-color="#94a3b8"
          active-text-color="#e0e7ff"
        >
        <el-sub-menu
          v-if="menuAllowed('LLM_MODELS') || menuAllowed('MCP_SERVERS') || menuAllowed('RAG_KBS')"
          index="grp-ai"
        >
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>AI 中台</span>
          </template>
          <el-menu-item v-if="menuAllowed('LLM_MODELS')" index="/model/llm-models">
            <el-icon><Setting /></el-icon>
            <span>模型管理</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('MCP_SERVERS')" index="/mcp/servers">
            <el-icon><Connection /></el-icon>
            <span>MCP 管理</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('RAG_KBS')" index="/knowledge-center/knowledge-bases">
            <el-icon><Reading /></el-icon>
            <span>知识中心</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="menuAllowed('USERS')" index="grp-org">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户与组织</span>
          </template>
          <el-menu-item v-if="menuAllowed('USERS')" index="/users">
            <el-icon><Avatar /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="menuAllowed('SYSTEM_SETTINGS')" index="grp-system">
          <template #title>
            <el-icon><Operation /></el-icon>
            <span>系统</span>
          </template>
          <el-menu-item index="/system/runtime-settings">
            <el-icon><Setting /></el-icon>
            <span>系统参数</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="isFounder && (menuAllowed('TENANTS') || menuAllowed('MENU_CATALOG'))" index="grp-platform">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>平台</span>
          </template>
          <el-menu-item v-if="menuAllowed('TENANTS')" index="/tenant/tenants">
            <el-icon><House /></el-icon>
            <span>租户管理</span>
          </el-menu-item>
          <el-menu-item v-if="isFounder && menuAllowed('MENU_CATALOG')" index="/system/menu-items">
            <el-icon><MenuIcon /></el-icon>
            <span>菜单管理</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="
            menuAllowed('ACCESS_LOGS') ||
            menuAllowed('AUDIT_EVENTS') ||
            menuAllowed('METERING') ||
            menuAllowed('CHAT') ||
            menuAllowed('CHAT_INTENTS') ||
            menuAllowed('GATEWAY_API')
          "
          index="grp-observe"
        >
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>观测与审计</span>
          </template>
          <el-menu-item v-if="menuAllowed('GATEWAY_API')" index="/gateway/api-rate-limits">
            <el-icon><Setting /></el-icon>
            <span>接口与限流</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('GATEWAY_API')" index="/gateway/cors-origins">
            <el-icon><Link /></el-icon>
            <span>跨域来源</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('ACCESS_LOGS')" index="/gateway/access-logs">
            <el-icon><TrendCharts /></el-icon>
            <span>访问日志</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('AUDIT_EVENTS')" index="/audit/events">
            <el-icon><Document /></el-icon>
            <span>审计事件</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('METERING')" index="/billing/metering">
            <el-icon><Histogram /></el-icon>
            <span>计量</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('CHAT')" index="/chat/conversations">
            <el-icon><ChatDotRound /></el-icon>
            <span>对话日志</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('CHAT')" index="/chat/sensitive-terms">
            <el-icon><Warning /></el-icon>
            <span>敏感词</span>
          </el-menu-item>
          <el-menu-item v-if="menuAllowed('CHAT_INTENTS')" index="/chat/intents">
            <el-icon><Promotion /></el-icon>
            <span>意图识别</span>
          </el-menu-item>
        </el-sub-menu>
        </el-menu>
      </div>
    </el-aside>

    <el-container direction="vertical">
      <el-header height="56px" class="top-bar">
        <div class="crumb">{{ pageTitle }}</div>
        <div class="header-right">
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
                    <span class="hint-workspace-label">当前工作区</span>
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
                  <el-tag v-if="row.isCurrent" type="info" size="small" class="workspace-current-tag">当前</el-tag>
                </el-dropdown-item>
                <el-dropdown-item command="relogin" :divided="showWorkspaceSwitch">重新登录</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <RouterView />
      </el-main>
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
  DataLine,
  Document,
  Histogram,
  House,
  Link,
  Menu as MenuIcon,
  OfficeBuilding,
  Operation,
  Promotion,
  Reading,
  Setting,
  TrendCharts,
  User,
  Warning,
} from "@element-plus/icons-vue";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import * as tenantsApi from "@/api/tenants";
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

/** 知识库工作台等子路径在侧栏高亮「知识中心」入口 */
const sideMenuActivePath = computed(() => {
  if (route.path.startsWith("/knowledge-center")) {
    return "/knowledge-center/knowledge-bases";
  }
  return route.path;
});

const titles: Record<string, string> = {
  "/users": "用户管理",
  "/tenant/tenants": "租户管理",
  "/system/menu-items": "菜单管理",
  "/gateway/api-rate-limits": "接口与限流",
  "/gateway/access-logs": "HTTP 访问日志",
  "/audit/events": "审计事件",
  "/billing/metering": "计量事件",
  "/chat/conversations": "对话日志",
  "/chat/sensitive-terms": "敏感词管理",
  "/model/llm-models": "模型管理",
  "/mcp/servers": "MCP 服务注册",
  "/knowledge-center/knowledge-bases": "知识中心",
  "/system/runtime-settings": "系统参数（本租户）",
};

const pageTitle = computed(() => {
  if (/\/knowledge-center\/workspace\/\d+\/documents\/\d+\/chunks$/.test(route.path)) {
    return "知识中心 · 分片管理";
  }
  return titles[route.path] ?? "管理端";
});

const displayUserLabel = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return "免登录预览";
  }
  const me = meSnapshot.value;
  const name = me?.displayName?.trim();
  if (name) {
    return name;
  }
  const sub = readJwtSubject(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
  return sub ?? "未登录";
});

const isFounder = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return false;
  }
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const tenantOptions = ref<tenantsApi.TenantRow[]>([]);

/** 最近一次 /admin/me（用于下拉展示租户名等） */
const meSnapshot = ref<AdminMeView | null>(null);

const allowedMenuCodes = ref<string[] | null>(null);

type WorkspacePickRow = {
  tenantId: number;
  role: string;
  label: string;
  isCurrent: boolean;
};

function formatRoleLabel(code: string): string {
  const map: Record<string, string> = {
    FOUNDER: "创始人",
    OWNER: "租户负责人",
    ADMIN: "管理员",
    MEMBER: "成员",
  };
  return map[code] ?? code;
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
  return "当前租户";
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
  return "租户（信息未加载）";
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
        pushUnique(m.tenantId, m.role, `${membershipTenantDisplay(m)}（${formatRoleLabel(m.role)}）`);
      }
      return markWorkspaceCurrent(acc);
    }
    const tenants = tenantOptions.value;
    if (tenants.length > 1) {
      for (const t of tenants) {
        pushUnique(
          t.id,
          "FOUNDER",
          `${formatTenantNameCode({ tenantName: t.name, tenantCode: t.code })}（${formatRoleLabel("FOUNDER")}）`,
        );
      }
      return markWorkspaceCurrent(acc);
    }
    return [];
  }

  if (elevated.length <= 1) return [];
  for (const m of elevated) {
    pushUnique(m.tenantId, m.role, `${membershipTenantDisplay(m)}（${formatRoleLabel(m.role)}）`);
  }
  return markWorkspaceCurrent(acc);
});

const showWorkspaceSwitch = computed(() => workspacePickRows.value.length > 1);

const workspaceSummary = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return "";
  const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
  const tid = readJwtTid(token);
  const tmr = readJwtTmr(token);
  if (!tid || !tmr) return "—";
  const hit = workspacePickRows.value.find((r) => r.isCurrent);
  if (hit) return hit.label;
  return `${resolveTenantDisplayName(tid)}（${formatRoleLabel(tmr)}）`;
});

function workspaceCommand(row: WorkspacePickRow): string {
  return `ws:${row.tenantId}:${row.role}`;
}

async function applyWorkspaceSwitch(tenantId: number, role: string) {
  try {
    await ElMessageBox.confirm(
      "将签发新的登录凭证，菜单与数据范围会按所选租户与角色立即变更。",
      "确认切换工作区",
      { type: "warning", confirmButtonText: "确认切换", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
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
    ElMessage.success("已切换工作区");
  } catch (e: unknown) {
    console.warn("[workspace switch]", e);
    ElMessage.error("切换失败，请稍后重试");
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

onMounted(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP !== "true") {
    void fetchAdminMe()
      .then((me) => {
        meSnapshot.value = me;
        allowedMenuCodes.value = me.allowedMenuCodes ?? [];
      })
      .catch(() => {
        allowedMenuCodes.value = [];
      });
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

const avatarLetter = computed(() => {
  const name = displayUserLabel.value;
  if (!name || name === "未登录" || name === "免登录预览") return "?";
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
