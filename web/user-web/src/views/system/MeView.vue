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
          <p class="card-hint">访客仍可对话；在本机登录/注册时会自动归并<strong>当前设备码</strong>下的匿名数据；其他设备见下方「合并其他设备」。</p>
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

        <section v-if="snapshot.userId != null" class="card">
          <h2>合并其他设备的访客数据</h2>
          <p class="card-hint">
            在另一浏览器或电脑<strong>未登录</strong>产生的对话挂在对方的设备码下。于对方页面「复制设备码」后粘贴到此处，可将<strong>当前租户</strong>内该访客会话与画像/记忆并入你的账号；每台设备码只需合并一次，可多次合并<strong>不同</strong>设备码。
          </p>
          <div class="me-merge-row">
            <el-input
              v-model="otherDeviceIdInput"
              class="me-merge-input"
              placeholder="粘贴另一设备的设备码（UUID）"
              clearable
            />
            <el-button
              type="primary"
              plain
              :loading="mergingOtherDevice"
              :disabled="!otherDeviceIdInput.trim()"
              @click="mergeOtherGuestDevice"
            >
              合并
            </el-button>
          </div>
        </section>

        <section v-if="snapshot.userId != null" class="card">
          <h2>画像与记忆数据</h2>
          <p class="card-hint">
            导出为 JSON（含跨会话画像标签与分层记忆片段）；删除后对话记录仍在，但模型侧个性化记忆与计数摘要将清空，且须重新绑定设备与账号关系。
          </p>
          <div class="me-actions">
            <el-button size="small" type="primary" plain :loading="exporting" @click="downloadProfileExport">
              导出 JSON
            </el-button>
            <el-button size="small" type="danger" plain :loading="purging" @click="confirmPurgeProfile">
              删除画像与记忆
            </el-button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { exportProfileDataJson, mergeGuestDevice, purgeProfileData } from "../../api/profile";
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
const exporting = ref(false);
const purging = ref(false);
const otherDeviceIdInput = ref("");
const mergingOtherDevice = ref(false);

async function mergeOtherGuestDevice() {
  const id = otherDeviceIdInput.value.trim();
  if (!id) return;
  mergingOtherDevice.value = true;
  try {
    const r = await mergeGuestDevice(id);
    if (!r.ran) {
      ElMessage.info("未提供有效设备码");
      return;
    }
    if (r.conversationsReassigned === 0 && r.memoryChunksReassigned === 0) {
      ElMessage.success("已处理：未找到该设备在本租户下的访客数据（或此前已合并）");
    } else {
      ElMessage.success(
        `已归并：会话 ${r.conversationsReassigned} 条，记忆片段 ${r.memoryChunksReassigned} 条`,
      );
    }
    otherDeviceIdInput.value = "";
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "合并失败"));
  } finally {
    mergingOtherDevice.value = false;
  }
}

async function downloadProfileExport() {
  exporting.value = true;
  try {
    const data = await exportProfileDataJson();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `ai-profile-export-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success("已开始下载导出文件");
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "导出失败"));
  } finally {
    exporting.value = false;
  }
}

async function confirmPurgeProfile() {
  try {
    await ElMessageBox.confirm(
      "将删除本租户下与画像、分层记忆相关的数据（对话列表不会删除）。此操作不可恢复，是否继续？",
      "删除画像与记忆",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  purging.value = true;
  try {
    await purgeProfileData();
    ElMessage.success("已删除画像与记忆数据");
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
  } finally {
    purging.value = false;
  }
}

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
  const ok = await copyTextToUserClipboard(id);
  if (ok) {
    ElMessage.success("已复制到剪贴板");
  } else {
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

.me-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.me-merge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 4px;
}

.me-merge-input {
  flex: 1 1 200px;
  min-width: 0;
}
</style>
