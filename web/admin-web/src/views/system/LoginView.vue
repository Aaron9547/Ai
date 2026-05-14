<template>
  <div class="login-page">
    <div class="glow" />
    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="title">
          <el-icon :size="22" color="#2dd4bf"><Lock /></el-icon>
          <span>{{ t("login.title") }}</span>
        </div>
      </template>
      <el-alert
        class="mb"
        type="info"
        :closable="false"
        show-icon
        :title="t('login.alertTitle')"
        :description="t('login.alertDesc')"
      />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('login.loginName')">
          <el-input v-model="loginName" autocomplete="username" size="large" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('login.password')">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            size="large"
            @keyup.enter="submit"
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-button type="primary" native-type="submit" size="large" class="w100" :loading="loading">
          {{ t("login.submit") }}
        </el-button>
      </el-form>
      <el-alert v-if="error" class="mt" type="error" :closable="false" show-icon :title="error" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Key, Lock, User } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { openAuthLoginErrorMessage } from "@/utils/openAuthHttpErrors";
import {
  AI_ADMIN_ACCESS_TOKEN_KEY,
  AI_ADMIN_EFFECTIVE_TENANT_KEY,
  AI_ADMIN_MEMBERSHIPS_KEY,
  http,
} from "../../plugins/http";

const { t } = useI18n();
const router = useRouter();
const route = useRoute();
const loginName = ref("admin");
const password = ref("admin");
const loading = ref(false);
const error = ref("");
async function submit() {
  loading.value = true;
  error.value = "";
  try {
    const { data } = await http.post<{
      accessToken: string;
      tokenType: string;
      expiresInSeconds: number;
      memberships?: { tenantId: number; tenantCode: string; tenantName?: string; role: string }[];
    }>("/open/v1/auth/login", { loginName: loginName.value, password: password.value });
    localStorage.removeItem(AI_ADMIN_EFFECTIVE_TENANT_KEY);
    localStorage.removeItem(AI_ADMIN_MEMBERSHIPS_KEY);
    localStorage.setItem(AI_ADMIN_ACCESS_TOKEN_KEY, data.accessToken);
    if (data.memberships?.length) {
      localStorage.setItem(AI_ADMIN_MEMBERSHIPS_KEY, JSON.stringify(data.memberships));
    }
    ElMessage.success(t("login.success"));
    const redir = typeof route.query.redirect === "string" ? route.query.redirect : "";
    if (redir && redir.startsWith("/") && !redir.startsWith("//")) {
      await router.replace(redir);
    } else {
      await router.replace("/dashboard");
    }
  } catch (e: unknown) {
    console.warn("[admin-web login]", e);
    error.value = openAuthLoginErrorMessage(e);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mb {
  margin-bottom: 8px;
}

.mt {
  margin-top: 14px;
}

.w100 {
  width: 100%;
  margin-top: 4px;
}
</style>
