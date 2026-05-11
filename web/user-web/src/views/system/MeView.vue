<template>
  <div class="me-page">
    <header class="me-top">
      <RouterLink class="back" :to="chatPagePath">
        <el-icon><ArrowLeft /></el-icon>
        返回对话
      </RouterLink>
      <h1>设备与登录</h1>
      <el-button text type="primary" :loading="loading" @click="reload">刷新</el-button>
    </header>

    <div class="me-body">
      <el-skeleton v-if="loading && !snapshot" :rows="6" animated />
      <el-alert v-else-if="err" type="error" :closable="false" show-icon :title="err" />
      <template v-else-if="snapshot">
        <p class="me-lead">
          本页说明当前请求下服务端识别到的<strong>租户</strong>、<strong>是否已登录</strong>以及<strong>设备标识</strong>，便于你确认访客对话、多租户与后续「设备绑定账号」是否正常。
        </p>

        <section class="card">
          <h2>当前租户</h2>
          <p class="card-value">{{ tenantDisplay }}</p>
          <p class="card-hint">
            用户可见的工作区以<strong>租户编码</strong>为准（与库表 <code>sys_tenant.code</code>、地址栏路径第一段一致）；请求会携带
            <code>X-Tenant-Code</code>（或兼容场景下的 <code>X-Tenant-Id</code>），由网关解析为内部租户上下文。
          </p>
        </section>

        <section class="card">
          <h2>登录状态</h2>
          <p class="card-value">
            <template v-if="snapshot.userId != null">
              已登录
              <template v-if="loginLine">：{{ loginLine }}</template>
            </template>
            <template v-else>未登录（访客模式）</template>
          </p>
          <p class="card-hint">访客仍可对话；注册/登录后服务端会将同一设备上的匿名数据按策略归并到账号。</p>
        </section>

        <section class="card">
          <h2>设备标识</h2>
          <p class="card-value mono">{{ snapshot.deviceId || "未检测到" }}</p>
          <p class="card-hint">
            由前端生成并保存在本机（如 <code>localStorage</code>），通过 <code>X-Device-Id</code> 随请求发送。请勿清除该值，否则会被视为新设备。
          </p>
          <el-button
            v-if="snapshot.deviceId"
            size="small"
            type="primary"
            plain
            @click="copyDevice"
          >
            复制设备码
          </el-button>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from "@element-plus/icons-vue";
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { http } from "../../plugins/http";
import { apiRequestErrorMessage } from "../../utils/apiRequestErrorMessage";
import { TENANT_CODE_PATH_RE } from "../../utils/outboundTenant";

type MePayload = {
  tenantId: number | null;
  tenantName: string | null;
  tenantCode: string | null;
  userId: number | null;
  loginName?: string | null;
  displayName?: string | null;
  deviceId: string | null;
};

const route = useRoute();
const snapshot = ref<MePayload | null>(null);

const tenantCodeFromRoute = computed(() => {
  const raw = route.params.tenantCode;
  return typeof raw === "string" && TENANT_CODE_PATH_RE.test(raw)
    ? raw
    : String(import.meta.env.VITE_TENANT_CODE || "default");
});

const chatPagePath = computed(() => `/${tenantCodeFromRoute.value}/chat`);

const loginLine = computed(() => {
  const s = snapshot.value;
  if (!s || s.userId == null) {
    return "";
  }
  const login = s.loginName?.trim();
  const nick = s.displayName?.trim();
  if (login && nick && nick !== login) {
    return `登录名 ${login}，昵称 ${nick}`;
  }
  if (login) {
    return `登录名 ${login}`;
  }
  if (nick) {
    return `昵称 ${nick}`;
  }
  return "";
});

const tenantDisplay = computed(() => {
  const s = snapshot.value;
  if (!s || s.tenantId == null) {
    return "—";
  }
  const name = s.tenantName?.trim();
  const code = s.tenantCode?.trim();
  const routeCode = tenantCodeFromRoute.value;
  if (name && code) {
    return `${name}（${code}）`;
  }
  if (name) {
    return name;
  }
  if (code) {
    return code;
  }
  // 接口未返回 code 时以路径编码为对人标识，不展示自增 tenantId（见 .cursorrules §7.1）
  return routeCode ? `工作区：${routeCode}` : "已识别工作区";
});

const loading = ref(true);
const err = ref("");

async function load() {
  loading.value = true;
  err.value = "";
  try {
    const { data } = await http.get<MePayload>("/open/v1/system/me");
    snapshot.value = data ?? null;
  } catch (e: unknown) {
    err.value = apiRequestErrorMessage(e, "加载失败");
    snapshot.value = null;
  } finally {
    loading.value = false;
  }
}

function reload() {
  void load();
}

async function copyDevice() {
  const id = snapshot.value?.deviceId;
  if (!id) return;
  try {
    await navigator.clipboard.writeText(id);
    ElMessage.success("已复制到剪贴板");
  } catch {
    ElMessage.warning("复制失败，请手动选择文本复制");
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.me-page {
  min-height: 100dvh;
  background: #fafafa;
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.me-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  padding: 14px max(20px, env(safe-area-inset-right)) 14px max(20px, env(safe-area-inset-left));
  padding-top: max(14px, env(safe-area-inset-top));
  border-bottom: 1px solid #e5e5e5;
  background: #fff;
}

.me-top h1 {
  flex: 1;
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #18181b;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #52525b;
  text-decoration: none;
}

.back:hover {
  color: #202020;
}

.me-body {
  max-width: 560px;
  margin: 0 auto;
  padding: 20px max(20px, env(safe-area-inset-right)) 40px max(20px, env(safe-area-inset-left));
}

@media (max-width: 719px) {
  .me-top h1 {
    width: 100%;
    order: 2;
  }

  .me-body {
    padding-top: 16px;
  }
}

.me-lead {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.65;
  color: #3f3f46;
}

.card {
  background: #fff;
  border: 1px solid #ececec;
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 14px;
}

.card h2 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #52525b;
}

.card-value {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 500;
  color: #18181b;
  word-break: break-word;
}

.card-meta {
  margin: 0 0 8px;
  font-size: 13px;
  color: #71717a;
}

.card-value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  font-weight: 400;
  color: #3f3f46;
}

.card-hint {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.55;
  color: #71717a;
}

.card-hint code {
  font-size: 12px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f4f4f5;
  color: #52525b;
}
</style>
