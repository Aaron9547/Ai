import { createRouter, createWebHistory } from "vue-router";
import {
  AI_USER_EFFECTIVE_TENANT_KEY,
  TENANT_CODE_PATH_RE,
  setRouteBoundTenantCode,
} from "../utils/outboundTenant";

const DEFAULT_CODE = String(import.meta.env.VITE_TENANT_CODE || "default").trim() || "default";

function readStoredTenantCode(): string {
  if (typeof localStorage === "undefined") {
    return DEFAULT_CODE;
  }
  const v = localStorage.getItem(AI_USER_EFFECTIVE_TENANT_KEY)?.trim();
  if (v && TENANT_CODE_PATH_RE.test(v)) {
    return v;
  }
  return DEFAULT_CODE;
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: () => `/${DEFAULT_CODE}/chat` },
    { path: "/chat", redirect: () => `/${readStoredTenantCode()}/chat` },
    { path: "/system/me", redirect: () => `/${readStoredTenantCode()}/system/me` },
    {
      path: "/:tenantCode([a-zA-Z0-9._-]{1,64})",
      component: () => import("../layouts/TenantLayout.vue"),
      children: [
        { path: "", redirect: (to) => `/${String(to.params.tenantCode)}/chat` },
        {
          path: "chat",
          name: "tenant-chat",
          component: () => import("../views/chat/ChatView.vue"),
        },
        {
          path: "system/me",
          name: "tenant-me",
          component: () => import("../views/system/MeView.vue"),
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const raw = to.params.tenantCode;
  if (typeof raw === "string" && TENANT_CODE_PATH_RE.test(raw)) {
    setRouteBoundTenantCode(raw);
    try {
      localStorage.setItem(AI_USER_EFFECTIVE_TENANT_KEY, raw);
    } catch {
      /* ignore */
    }
  } else {
    setRouteBoundTenantCode(null);
  }
});

export default router;
