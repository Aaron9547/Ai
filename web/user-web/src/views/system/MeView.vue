<template>
  <div class="me-page">
    <header class="me-top">
      <RouterLink class="back" :to="chatPagePath">
        <el-icon><ArrowLeft /></el-icon>
        {{ t("me.backChat") }}
      </RouterLink>
      <h1>{{ t("me.title") }}</h1>
      <el-button text type="primary" :loading="loading" @click="reload">{{ t("me.refresh") }}</el-button>
    </header>

    <div class="me-body">
      <el-skeleton v-if="loading && !snapshot" :rows="6" animated />
      <el-alert v-else-if="err" type="error" :closable="false" show-icon :title="err" />
      <template v-else-if="snapshot">
        <p class="me-lead">{{ t("me.lead") }}</p>

        <section class="card">
          <h2>{{ t("me.tenant") }}</h2>
          <p class="card-value">{{ tenantDisplay }}</p>
          <p class="card-hint">{{ t("me.tenantHint") }}</p>
        </section>

        <section class="card">
          <h2>{{ t("me.loginStatus") }}</h2>
          <p class="card-value">
            <template v-if="snapshot.userId != null">
              {{ t("me.loggedIn") }}
              <template v-if="loginLine">：{{ loginLine }}</template>
            </template>
            <template v-else>{{ t("me.guest") }}</template>
          </p>
          <p class="card-hint">{{ t("me.loginHint") }}</p>
        </section>

        <section class="card">
          <h2>{{ t("me.deviceId") }}</h2>
          <p class="card-value mono">{{ snapshot.deviceId || t("me.notDetected") }}</p>
          <p class="card-hint">{{ t("me.deviceHint") }}</p>
          <el-button
            v-if="snapshot.deviceId"
            size="small"
            type="primary"
            plain
            @click="copyDevice"
          >
            {{ t("me.copyDevice") }}
          </el-button>
        </section>

        <section v-if="snapshot.userId != null" class="card">
          <h2>{{ t("me.mergeSection") }}</h2>
          <p class="card-hint">{{ t("me.mergeHint") }}</p>
          <div class="me-merge-row">
            <el-input
              v-model="otherDeviceIdInput"
              class="me-merge-input"
              :placeholder="t('me.mergePlaceholder')"
              clearable
            />
            <el-button
              type="primary"
              plain
              :loading="mergingOtherDevice"
              :disabled="!otherDeviceIdInput.trim()"
              @click="mergeOtherGuestDevice"
            >
              {{ t("me.merge") }}
            </el-button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import { mergeGuestDevice } from "../../api/profile";
import { http } from "../../plugins/http";
import { copyTextToUserClipboard } from "../../utils/clipboard";
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

const { t } = useI18n();
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
    return t("me.loginLineNick", { login, nick });
  }
  if (login) {
    return t("me.loginLineLogin", { login });
  }
  if (nick) {
    return t("me.loginLineNickOnly", { nick });
  }
  return "";
});

const tenantDisplay = computed(() => {
  const s = snapshot.value;
  if (!s || s.tenantId == null) {
    return t("common.dash");
  }
  const name = s.tenantName?.trim();
  const code = s.tenantCode?.trim();
  const routeCode = tenantCodeFromRoute.value;
  if (name && code) {
    return t("me.tenantPair", { name, code });
  }
  if (name) {
    return name;
  }
  if (code) {
    return code;
  }
  return routeCode ? t("me.workspaceFallback", { code: routeCode }) : t("me.workspaceDetected");
});

const loading = ref(true);
const err = ref("");
const otherDeviceIdInput = ref("");
const mergingOtherDevice = ref(false);

async function mergeOtherGuestDevice() {
  const id = otherDeviceIdInput.value.trim();
  if (!id) return;
  mergingOtherDevice.value = true;
  try {
    const r = await mergeGuestDevice(id);
    if (!r.ran) {
      ElMessage.info(t("me.noValidDevice"));
      return;
    }
    if (r.conversationsReassigned === 0 && r.memoryChunksReassigned === 0 && r.knowledgeNodesReassigned === 0) {
      ElMessage.success(t("me.mergeNoData"));
    } else {
      ElMessage.success(
        t("me.mergeDone", {
          c: r.conversationsReassigned,
          m: r.memoryChunksReassigned,
          k: r.knowledgeNodesReassigned,
        }),
      );
    }
    otherDeviceIdInput.value = "";
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("me.mergeFail")));
  } finally {
    mergingOtherDevice.value = false;
  }
}

async function load() {
  loading.value = true;
  err.value = "";
  try {
    const { data } = await http.get<MePayload>("/open/v1/system/me");
    snapshot.value = data ?? null;
  } catch (e: unknown) {
    err.value = apiRequestErrorMessage(e, t("me.loadFail"));
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
  const ok = await copyTextToUserClipboard(id);
  if (ok) {
    ElMessage.success(t("me.copied"));
  } else {
    ElMessage.warning(t("me.copyFail"));
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.me-page {
  min-height: 100dvh;
  max-height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  flex: 1;
  min-height: 0;
  max-width: 720px;
  width: 100%;
  margin: 0 auto;
  padding: 20px max(20px, env(safe-area-inset-right)) max(32px, env(safe-area-inset-bottom))
    max(20px, env(safe-area-inset-left));
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}

.me-lead {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.65;
  color: #52525b;
}

.card {
  margin-bottom: 16px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
  background: #fff;
}

.card h2 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: #18181b;
}

.card-value {
  margin: 0 0 8px;
  font-size: 14px;
  color: #27272a;
  word-break: break-word;
}

.card-value.mono {
  font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
  font-size: 13px;
}

.card-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #71717a;
}

.me-merge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.me-merge-input {
  flex: 1;
  min-width: 200px;
}

html.dark .me-page {
  background: #0a0a0a;
}

html.dark .me-top,
html.dark .card {
  background: #141414;
  border-color: #333;
}

html.dark .me-top h1,
html.dark .card h2,
html.dark .card-value {
  color: #e5e5e5;
}

html.dark .back,
html.dark .me-lead,
html.dark .card-hint {
  color: #a1a1aa;
}
</style>
