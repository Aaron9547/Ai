<template>
  <el-dialog
    v-model="innerVisible"
    :title="t('auth.title')"
    width="420px"
    destroy-on-close
    class="auth-dlg"
    @closed="reset"
  >
    <el-tabs v-model="tab">
      <el-tab-pane :label="t('auth.tabLogin')" name="login">
        <el-form class="auth-form" label-position="top" @submit.prevent="onLogin">
          <el-form-item :label="t('auth.loginName')" required>
            <el-input v-model="loginUser" autocomplete="username" maxlength="64" />
          </el-form-item>
          <el-form-item :label="t('auth.password')" required>
            <el-input v-model="loginPass" type="password" autocomplete="current-password" show-password />
          </el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" native-type="submit" @click="onLogin">
            {{ t("auth.login") }}
          </el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane :label="t('auth.tabReg')" name="reg">
        <el-form class="auth-form" label-position="top" @submit.prevent="onRegister">
          <el-form-item :label="t('auth.loginName')" required>
            <el-input
              v-model="regUser"
              autocomplete="username"
              maxlength="64"
              :placeholder="t('auth.regLoginPh')"
            />
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
          <el-form-item :label="t('auth.displayName')">
            <el-input v-model="regDisplay" maxlength="64" :placeholder="t('auth.displayPh')" />
          </el-form-item>
          <p class="auth-hint">{{ t("auth.regHint") }}</p>
          <el-button type="primary" class="auth-submit" :loading="loading" native-type="submit" @click="onRegister">
            {{ t("auth.regSubmit") }}
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import * as authApi from "../api/auth";
import {
  AI_USER_ACCESS_TOKEN_KEY,
  AI_USER_EFFECTIVE_TENANT_KEY,
} from "../plugins/http";
import { openAuthLoginErrorMessage, openAuthRegisterErrorMessage } from "../utils/openAuthHttpErrors";
import { saveUserMemberships } from "../utils/userMembershipStorage";

const { t } = useI18n();

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [boolean]; done: [] }>();

const router = useRouter();
const innerVisible = ref(props.modelValue);
const tab = ref<"login" | "reg">("login");
const loading = ref(false);

const loginUser = ref("");
const loginPass = ref("");
const regUser = ref("");
const regPass = ref("");
const regDisplay = ref("");

watch(
  () => props.modelValue,
  (v) => {
    innerVisible.value = v;
  },
);

watch(innerVisible, (v) => {
  emit("update:modelValue", v);
});

function reset() {
  tab.value = "login";
  loginUser.value = "";
  loginPass.value = "";
  regUser.value = "";
  regPass.value = "";
  regDisplay.value = "";
  loading.value = false;
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

/** 登录后进入 JWT 主租户路径（URL 使用 {@code sys_tenant.code}）。 */
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
  loading.value = true;
  try {
    const data = await authApi.loginOpen(loginUser.value.trim(), loginPass.value);
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

async function onRegister() {
  loading.value = true;
  try {
    const data = await authApi.registerOpen(regUser.value.trim(), regPass.value, regDisplay.value);
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
.auth-form {
  padding-top: 8px;
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
}

.auth-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
}
</style>
