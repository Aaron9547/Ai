<template>
  <Teleport to="body">
    <Transition name="auth-overlay" @after-leave="reset">
      <div
        v-if="innerVisible"
        class="auth-overlay"
        role="dialog"
        aria-modal="true"
        :aria-label="t('auth.title')"
        @click.self="close"
        @keydown.esc="close"
      >
        <Transition name="auth-pop" appear>
          <div v-if="innerVisible" class="auth-shell">
            <button type="button" class="auth-close" :aria-label="t('auth.close')" @click="close">
              <el-icon :size="18"><Close /></el-icon>
            </button>

            <div class="auth-brand">
              <span class="auth-brand-glow" aria-hidden="true" />
              <BrandMark :logo-url="brandLogoUrl" :label="brandTitle" size="auth" />
              <h2 class="auth-title">{{ brandTitle }}</h2>
              <p class="auth-subtitle">{{ tab === "login" ? t("auth.loginSubtitle") : t("auth.regSubtitle") }}</p>
            </div>

            <div class="auth-tabs" role="tablist">
              <button
                type="button"
                role="tab"
                class="auth-tab"
                :class="{ 'auth-tab--active': tab === 'login' }"
                :aria-selected="tab === 'login'"
                @click="switchTab('login')"
              >
                {{ t("auth.tabLogin") }}
              </button>
              <button
                type="button"
                role="tab"
                class="auth-tab"
                :class="{ 'auth-tab--active': tab === 'reg' }"
                :aria-selected="tab === 'reg'"
                @click="switchTab('reg')"
              >
                {{ t("auth.tabReg") }}
              </button>
              <span class="auth-tab-indicator" :class="{ 'auth-tab-indicator--reg': tab === 'reg' }" aria-hidden="true" />
            </div>

            <el-scrollbar class="auth-body-scroll" tag="div">
              <div class="auth-flip-wrap">
              <div class="auth-flip-inner">
                <div v-show="tab === 'login'" class="auth-panel auth-panel--login">
                  <el-form class="auth-form" label-position="top" @submit.prevent="onLogin">
                    <el-form-item :label="t('auth.email')" required>
                      <AuthEmailInput
                        v-model="loginUser"
                        autocomplete="username"
                        :placeholder="t('auth.emailPh')"
                      />
                    </el-form-item>
                    <el-form-item :label="t('auth.password')" required>
                      <el-input
                        v-model="loginPass"
                        type="password"
                        autocomplete="current-password"
                        show-password
                      />
                    </el-form-item>
                    <el-button
                      type="primary"
                      class="auth-submit"
                      :loading="loading"
                      native-type="submit"
                    >
                      {{ t("auth.login") }}
                    </el-button>
                  </el-form>
                </div>

                <div v-show="tab === 'reg'" class="auth-panel auth-panel--reg">
                  <el-form class="auth-form" label-position="top" @submit.prevent="onRegister">
                    <el-form-item :label="t('auth.email')" required>
                      <AuthEmailInput
                        v-model="regEmail"
                        autocomplete="email"
                        :placeholder="t('auth.emailPh')"
                      />
                    </el-form-item>
                    <el-form-item :label="t('auth.verificationCode')" required>
                      <div class="auth-code-row">
                        <el-input
                          v-model="regCode"
                          maxlength="6"
                          inputmode="numeric"
                          autocomplete="one-time-code"
                          :placeholder="t('auth.codePh')"
                        />
                        <el-button
                          class="auth-code-btn"
                          :disabled="codeCooldown > 0 || sendingCode"
                          :loading="sendingCode"
                          @click="onSendCode"
                        >
                          {{ codeCooldown > 0 ? t("auth.resendIn", { s: codeCooldown }) : t("auth.sendCode") }}
                        </el-button>
                      </div>
                    </el-form-item>
                    <el-form-item :label="t('auth.password')" required>
                      <el-input
                        v-model="regPass"
                        type="password"
                        autocomplete="new-password"
                        show-password
                        :placeholder="t('auth.regPassPh')"
                      />
                    </el-form-item>
                    <el-form-item :label="t('auth.confirmPassword')" required>
                      <el-input
                        v-model="regPassConfirm"
                        type="password"
                        autocomplete="new-password"
                        show-password
                        :placeholder="t('auth.confirmPassPh')"
                      />
                    </el-form-item>
                    <el-form-item :label="t('auth.displayName')">
                      <el-input v-model="regDisplay" maxlength="64" :placeholder="t('auth.displayPh')" />
                    </el-form-item>
                    <p class="auth-hint">{{ t("auth.regHint") }}</p>
                    <el-button
                      type="primary"
                      class="auth-submit"
                      :loading="loading"
                      native-type="submit"
                    >
                      {{ t("auth.regSubmit") }}
                    </el-button>
                  </el-form>
                </div>
              </div>
            </div>
            </el-scrollbar>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { Close } from "@element-plus/icons-vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import * as authApi from "../api/auth";
import {
  AI_USER_ACCESS_TOKEN_KEY,
  AI_USER_EFFECTIVE_TENANT_KEY,
} from "../plugins/http";
import {
  openAuthLoginErrorMessage,
  openAuthRegisterErrorMessage,
  openAuthSendCodeErrorMessage,
} from "../utils/openAuthHttpErrors";
import { saveUserMemberships } from "../utils/userMembershipStorage";
import AuthEmailInput from "./auth/AuthEmailInput.vue";
import BrandMark from "./BrandMark.vue";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const { t } = useI18n();

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    brandLogoUrl?: string | null;
    brandTitle?: string;
  }>(),
  { brandLogoUrl: "", brandTitle: "" },
);

const brandTitle = computed(() => {
  const custom = props.brandTitle?.trim();
  if (custom) return custom;
  return t("auth.title");
});
const emit = defineEmits<{ "update:modelValue": [boolean]; done: [] }>();

const router = useRouter();
const innerVisible = ref(props.modelValue);
const tab = ref<"login" | "reg">("login");
const loading = ref(false);
const sendingCode = ref(false);
const codeCooldown = ref(0);

const loginUser = ref("");
const loginPass = ref("");
const regEmail = ref("");
const regCode = ref("");
const regPass = ref("");
const regPassConfirm = ref("");
const regDisplay = ref("");

let cooldownTimer: ReturnType<typeof setInterval> | null = null;

watch(
  () => props.modelValue,
  (v) => {
    innerVisible.value = v;
  },
);

watch(innerVisible, (v) => {
  emit("update:modelValue", v);
});

onBeforeUnmount(() => {
  stopCooldownTimer();
});

function stopCooldownTimer() {
  if (cooldownTimer != null) {
    clearInterval(cooldownTimer);
    cooldownTimer = null;
  }
}

function startCooldown(seconds: number) {
  stopCooldownTimer();
  codeCooldown.value = Math.max(0, Math.floor(seconds));
  if (codeCooldown.value <= 0) {
    return;
  }
  cooldownTimer = setInterval(() => {
    if (codeCooldown.value <= 1) {
      codeCooldown.value = 0;
      stopCooldownTimer();
    } else {
      codeCooldown.value -= 1;
    }
  }, 1000);
}

function switchTab(next: "login" | "reg") {
  if (tab.value === next || loading.value) {
    return;
  }
  tab.value = next;
}

function close() {
  if (loading.value) {
    return;
  }
  innerVisible.value = false;
}

function reset() {
  tab.value = "login";
  loginUser.value = "";
  loginPass.value = "";
  regEmail.value = "";
  regCode.value = "";
  regPass.value = "";
  regPassConfirm.value = "";
  regDisplay.value = "";
  loading.value = false;
  sendingCode.value = false;
  stopCooldownTimer();
  codeCooldown.value = 0;
}

function isStrongPassword(password: string): boolean {
  if (password.length < 8 || password.length > 128) {
    return false;
  }
  return /[A-Za-z]/.test(password) && /\d/.test(password);
}

function persistSession(data: authApi.LoginResponse) {
  localStorage.setItem(AI_USER_ACCESS_TOKEN_KEY, data.accessToken);
  const list = data.memberships ?? [];
  saveUserMemberships(list);
  const envC = String(import.meta.env.VITE_TENANT_CODE || "default").trim();
  const match =
    list.find((m) => m.tenantCode === envC) ??
    list.find((m) => String(m.tenantId) === String(import.meta.env.VITE_TENANT_ID || "1"));
  const pick = match ?? list[0];
  if (pick) {
    localStorage.setItem(AI_USER_EFFECTIVE_TENANT_KEY, pick.tenantCode);
  }
}

function homeChatPathAfterLogin(data: authApi.LoginResponse): string {
  const list = data.memberships ?? [];
  const envC = String(import.meta.env.VITE_TENANT_CODE || "default").trim();
  const match =
    list.find((m) => m.tenantCode === envC) ??
    list.find((m) => String(m.tenantId) === String(import.meta.env.VITE_TENANT_ID || "1"));
  const pick = match ?? list[0];
  const code = pick ? pick.tenantCode : envC;
  return `/${code}/chat`;
}

async function onLogin() {
  const email = loginUser.value.trim();
  if (!EMAIL_RE.test(email)) {
    ElMessage.warning(t("auth.errInvalidEmail"));
    return;
  }
  if (!loginPass.value) {
    ElMessage.warning(t("auth.errPasswordRequired"));
    return;
  }
  loading.value = true;
  try {
    const data = await authApi.loginOpen(email, loginPass.value);
    persistSession(data);
    await router.push(homeChatPathAfterLogin(data));
    ElMessage.success(t("auth.loggedIn"));
    innerVisible.value = false;
    emit("done");
  } catch (e: unknown) {
    ElMessage.error(openAuthLoginErrorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function onSendCode() {
  const email = regEmail.value.trim().toLowerCase();
  if (!EMAIL_RE.test(email)) {
    ElMessage.warning(t("auth.errInvalidEmail"));
    return;
  }
  sendingCode.value = true;
  try {
    const res = await authApi.sendRegisterCode(email);
    if (res.sent) {
      ElMessage.success(t("auth.codeSent"));
      startCooldown(res.cooldownSeconds || 60);
    } else {
      startCooldown(res.cooldownSeconds || 60);
      ElMessage.info(t("auth.resendWait", { s: res.cooldownSeconds }));
    }
  } catch (e: unknown) {
    ElMessage.error(openAuthSendCodeErrorMessage(e));
  } finally {
    sendingCode.value = false;
  }
}

async function onRegister() {
  const email = regEmail.value.trim().toLowerCase();
  if (!EMAIL_RE.test(email)) {
    ElMessage.warning(t("auth.errInvalidEmail"));
    return;
  }
  if (!/^\d{6}$/.test(regCode.value.trim())) {
    ElMessage.warning(t("auth.errInvalidCode"));
    return;
  }
  if (!isStrongPassword(regPass.value)) {
    ElMessage.warning(t("auth.errWeakPassword"));
    return;
  }
  if (regPass.value !== regPassConfirm.value) {
    ElMessage.warning(t("auth.errPasswordMismatch"));
    return;
  }
  loading.value = true;
  try {
    const data = await authApi.registerOpen({
      email,
      password: regPass.value,
      verificationCode: regCode.value.trim(),
      displayName: regDisplay.value.trim() || undefined,
    });
    persistSession(data);
    await router.push(homeChatPathAfterLogin(data));
    ElMessage.success(t("auth.regOk"));
    innerVisible.value = false;
    emit("done");
  } catch (e: unknown) {
    ElMessage.error(openAuthRegisterErrorMessage(e));
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-overlay {
  position: fixed;
  inset: 0;
  /* 低于 Element Plus Message（约 3000+），避免「验证码已发送」等 Toast 被毛玻璃盖住 */
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: rgba(15, 23, 42, 0.52);
  backdrop-filter: blur(14px) saturate(1.2);
  -webkit-backdrop-filter: blur(14px) saturate(1.2);
}

.auth-shell {
  position: relative;
  width: min(100%, 440px);
  max-height: min(92vh, 760px);
  display: flex;
  flex-direction: column;
  padding: 28px 28px 24px;
  border-radius: 22px;
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.94) 100%);
  box-shadow:
    0 28px 90px rgba(15, 23, 42, 0.28),
    0 0 0 1px rgba(255, 255, 255, 0.65) inset,
    0 0 60px rgba(59, 130, 246, 0.12);
  transform-style: preserve-3d;
  will-change: transform, opacity;
  overflow: hidden;
}

.auth-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  color: #64748b;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.auth-close:hover {
  background: rgba(148, 163, 184, 0.24);
  transform: rotate(90deg);
}

.auth-brand {
  position: relative;
  margin-bottom: 18px;
  text-align: center;
}

.auth-brand-glow {
  position: absolute;
  inset: -20px 20% auto;
  height: 80px;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.35), transparent 70%);
  filter: blur(8px);
  pointer-events: none;
}

.auth-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.auth-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.45;
}

.auth-tabs {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin-bottom: 16px;
  padding: 4px;
  border-radius: 14px;
  background: rgba(226, 232, 240, 0.55);
}

.auth-tab {
  position: relative;
  z-index: 1;
  border: none;
  background: transparent;
  padding: 10px 12px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: color 0.25s ease;
}

.auth-tab--active {
  color: #0f172a;
}

.auth-tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.08);
  transition: transform 0.55s cubic-bezier(0.34, 1.56, 0.64, 1);
  pointer-events: none;
}

.auth-tab-indicator--reg {
  transform: translateX(100%);
}

.auth-body-scroll {
  flex: 1;
  min-height: 0;
}

.auth-body-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.auth-body-scroll :deep(.el-scrollbar__view) {
  padding-right: 4px;
}

.auth-body-scroll :deep(.el-scrollbar__bar.is-vertical) {
  width: 6px;
  right: 2px;
}

.auth-body-scroll :deep(.el-scrollbar__thumb) {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 4px;
  opacity: 1;
}

.auth-body-scroll :deep(.el-scrollbar__thumb:hover) {
  background: rgba(100, 116, 139, 0.65);
}

.auth-flip-wrap {
  display: contents;
}

.auth-flip-inner {
  display: contents;
}

.auth-flip-inner--reg {
  transform: none;
}

.auth-panel {
  position: relative;
  width: 100%;
}

.auth-panel--reg {
  transform: none;
}

.auth-form {
  padding-top: 4px;
}

.auth-code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.auth-code-row :deep(.el-input) {
  flex: 1;
}

.auth-code-btn {
  flex-shrink: 0;
  min-width: 108px;
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
  height: 42px;
  border-radius: 12px;
  font-weight: 600;
}

.auth-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}

/* 遮罩淡入 */
.auth-overlay-enter-active,
.auth-overlay-leave-active {
  transition: opacity 0.38s ease;
}

.auth-overlay-enter-from,
.auth-overlay-leave-to {
  opacity: 0;
}

/* 弹窗 3D 入场 */
.auth-pop-enter-active {
  transition:
    opacity 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.72s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.auth-pop-leave-active {
  transition:
    opacity 0.28s ease,
    transform 0.32s ease;
}

.auth-pop-enter-from {
  opacity: 0;
  transform: perspective(1400px) rotateX(16deg) rotateY(-24deg) scale(0.78) translateY(36px);
}

.auth-pop-leave-to {
  opacity: 0;
  transform: perspective(1400px) rotateX(-8deg) scale(0.92) translateY(-12px);
}

@media (prefers-reduced-motion: reduce) {
  .auth-tab-indicator,
  .auth-pop-enter-active,
  .auth-pop-leave-active,
  .auth-overlay-enter-active,
  .auth-overlay-leave-active,
  .auth-panel-enter-active,
  .auth-panel-leave-active,
  .auth-close {
    transition: none !important;
  }

  .auth-pop-enter-from,
  .auth-pop-leave-to {
    transform: none;
  }
}

.auth-panel-enter-active,
.auth-panel-leave-active {
  transition:
    opacity 0.32s ease,
    transform 0.42s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.auth-panel-enter-from {
  opacity: 0;
  transform: perspective(800px) rotateY(-14deg) translateX(10px);
}

.auth-panel-leave-to {
  opacity: 0;
  transform: perspective(800px) rotateY(14deg) translateX(-10px);
}
</style>
