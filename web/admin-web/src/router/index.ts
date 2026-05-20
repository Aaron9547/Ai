import { createRouter, createWebHistory } from "vue-router";
import AdminLayout from "@/layouts/AdminLayout.vue";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { readJwtTmr } from "@/utils/jwtSubject";

function authSkip(): boolean {
  return import.meta.env.VITE_ADMIN_AUTH_SKIP === "true";
}

function hasToken(): boolean {
  return !!localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      component: () => import("../views/system/LoginView.vue"),
    },
    {
      path: "/",
      component: AdminLayout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () => import("../views/dashboard/DashboardView.vue"),
        },
        {
          path: "users",
          component: () => import("../views/users/UsersView.vue"),
        },
        {
          path: "users/profiles",
          component: () => import("../views/users/UserProfilesView.vue"),
        },
        { path: "users/roles", redirect: "/users" },
        {
          path: "tenant/tenants",
          meta: { founderOnly: true },
          component: () => import("../views/tenant/TenantsView.vue"),
        },
        {
          path: "system/menu-items",
          meta: { founderOnly: true },
          component: () => import("../views/system/MenuItemsView.vue"),
        },
        {
          path: "gateway/api-rate-limits",
          component: () => import("../views/gateway/GatewayRateLimitsView.vue"),
        },
        {
          path: "gateway/cors-origins",
          component: () => import("../views/gateway/CorsOriginsView.vue"),
        },
        {
          path: "gateway/access-logs",
          component: () => import("../views/gateway/AccessLogsView.vue"),
        },
        {
          path: "audit/events",
          component: () => import("../views/audit/AuditEventsView.vue"),
        },
        {
          path: "billing/metering",
          component: () => import("../views/billing/MeteringView.vue"),
        },
        {
          path: "chat/conversations",
          component: () => import("../views/chat/ChatConversationsView.vue"),
        },
        {
          path: "chat/sensitive-terms",
          component: () => import("../views/chat/ChatSensitiveTermsView.vue"),
        },
        {
          path: "chat/intents",
          component: () => import("../views/chat/IntentManageView.vue"),
        },
        {
          path: "chat/starter-prompts",
          component: () => import("../views/chat/ChatStarterPromptsView.vue"),
        },
        {
          path: "model/llm-models",
          component: () => import("../views/model/llm/LlmModelManageView.vue"),
        },
        {
          path: "mcp/servers",
          component: () => import("../views/mcp/McpServersView.vue"),
        },
        {
          path: "rag/knowledge-bases",
          redirect: "/knowledge-center/knowledge-bases",
        },
        {
          path: "knowledge-center",
          component: () => import("../views/rag/KnowledgeCenterLayout.vue"),
          redirect: "/knowledge-center/knowledge-bases",
          children: [
            {
              path: "knowledge-bases",
              component: () => import("../views/rag/RagKnowledgeBasesView.vue"),
            },
            { path: "ingest", redirect: "/knowledge-center/knowledge-bases" },
            { path: "tasks", redirect: "/knowledge-center/knowledge-bases" },
            {
              path: "workspace/:kbId/documents/:docId/chunks",
              component: () => import("../views/rag/DocumentChunksManageView.vue"),
            },
          ],
        },
        {
          path: "system/runtime-settings",
          component: () => import("../views/system/TenantRuntimeSettingsView.vue"),
        },
        {
          path: "system/scheduled-tasks",
          component: () => import("../views/system/ScheduledTasksView.vue"),
        },
        {
          path: "system/tenant-shell-config",
          component: () => import("../views/system/TenantShellConfigView.vue"),
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  if (authSkip()) {
    return;
  }
  if (to.path === "/login") {
    if (hasToken()) {
      const redir = typeof to.query.redirect === "string" ? to.query.redirect : "";
      return redir && redir.startsWith("/") && !redir.startsWith("//") ? redir : "/dashboard";
    }
    return;
  }
  if (!hasToken()) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.meta.founderOnly === true) {
    const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
    if (readJwtTmr(token) !== "FOUNDER") {
      return { path: "/dashboard" };
    }
  }
});

export default router;
