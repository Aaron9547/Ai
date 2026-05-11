<template>
  <div class="login-page">
    <div class="glow" />
    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="title">
          <el-icon :size="22" color="#2dd4bf"><Lock /></el-icon>
          <span>管理端登录</span>
        </div>
      </template>
      <el-alert
        class="mb"
        type="info"
        :closable="false"
        show-icon
        title="登录后进入控制台"
        description="用于管理用户、访问日志与运维数据。若您没有账号或无法登录，请联系企业管理员。"
      />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="登录名">
          <el-input v-model="loginName" autocomplete="username" size="large" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
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
          登录
        </el-button>
      </el-form>
      <el-alert v-if="error" class="mt" type="error" :closable="false" show-icon :title="error" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Key, Lock, User } from "@element-plus/icons-vue";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { openAuthLoginErrorMessage } from "@/utils/openAuthHttpErrors";
import {
  AI_ADMIN_ACCESS_TOKEN_KEY,
  AI_ADMIN_EFFECTIVE_TENANT_KEY,
  AI_ADMIN_MEMBERSHIPS_KEY,
  http,
} from "../../plugins/http";

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
    ElMessage.success("登录成功");
    const redir = typeof route.query.redirect === "string" ? route.query.redirect : "";
    if (redir && redir.startsWith("/") && !redir.startsWith("//")) {
      await router.replace(redir);
    } else {
      await router.replace("/gateway/access-logs");
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
  color: #0f172a;
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
