<template>
  <el-card class="block" shadow="never">
    <template #header>
      <div class="outbound-card-header">
        <span class="outbound-card-title">{{ t("admin.shell.authRegister.blockTitle") }}</span>
        <span class="outbound-card-sub">{{ t("admin.shell.authRegister.blockSub") }}</span>
      </div>
    </template>
    <p class="model-calling-tip">{{ t("admin.shell.authRegister.tip") }}</p>
    <el-alert type="info" :closable="false" show-icon class="message-link-alert">
      <template #title>{{ t("admin.message.shellLinkTitle") }}</template>
      <el-button link type="primary" @click="router.push('/system/message-channels')">
        {{ t("admin.message.goChannels") }}
      </el-button>
      ·
      <el-button link type="primary" @click="router.push('/system/message-templates')">
        {{ t("admin.message.goTemplates") }}
      </el-button>
    </el-alert>
    <el-form label-width="auto" class="shell-form auth-register-form">
      <el-form-item :label="t('admin.shell.authRegister.openRegistration')">
        <el-switch v-model="form.openRegistration" :disabled="!emailDeliveryReady" />
      </el-form-item>
      <p v-if="!emailDeliveryReady" class="auth-register-warn">
        {{ t("admin.shell.authRegister.openRegistrationNeedsEmail") }}
      </p>
      <div class="outbound-fields-grid">
        <el-form-item :label="t('admin.shell.authRegister.codeLength')">
          <el-input-number v-model="form.codeLength" :min="4" :max="8" controls-position="right" class="num-wide" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.codeTtlSeconds')">
          <el-input-number v-model="form.codeTtlSeconds" :min="60" :max="3600" controls-position="right" class="num-wide" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.sendCooldownSeconds')">
          <el-input-number v-model="form.sendCooldownSeconds" :min="30" :max="600" controls-position="right" class="num-wide" />
        </el-form-item>
      </div>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ t("admin.shell.authRegister.save") }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  getTenantShellConfig,
  putTenantShellAuthRegister,
  type TenantShellAuthRegisterPutBody,
  type TenantShellAuthRegisterRuntime,
} from "@/api/tenantShellConfig";

const { t } = useI18n();
const router = useRouter();
const saving = ref(false);
const emailDeliveryReady = ref(false);

const form = reactive<TenantShellAuthRegisterPutBody>(defaultForm());

function defaultForm(): TenantShellAuthRegisterPutBody {
  return {
    openRegistration: false,
    codeLength: 6,
    codeTtlSeconds: 600,
    sendCooldownSeconds: 60,
  };
}

function applyFromApi(ar: TenantShellAuthRegisterRuntime | undefined) {
  if (!ar) return;
  emailDeliveryReady.value = Boolean(ar.emailDeliveryReady);
  form.codeLength = ar.codeLength || 6;
  form.codeTtlSeconds = ar.codeTtlSeconds || 600;
  form.sendCooldownSeconds = ar.sendCooldownSeconds || 60;
  form.openRegistration = ar.openRegistration;
  if (!emailDeliveryReady.value) {
    form.openRegistration = false;
  }
}

async function reload() {
  const data = await getTenantShellConfig();
  applyFromApi(data.authRegister);
}

async function save() {
  if (form.openRegistration && !emailDeliveryReady.value) {
    ElMessage.warning(t("admin.shell.authRegister.openRegistrationNeedsEmail"));
    form.openRegistration = false;
    return;
  }
  saving.value = true;
  try {
    const data = await putTenantShellAuthRegister(form);
    applyFromApi(data.authRegister);
    ElMessage.success(t("admin.shell.authRegister.saveOk"));
  } catch {
    ElMessage.error(t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  reload().catch(() => ElMessage.error(t("common.loadFailed")));
});

defineExpose({ reload });
</script>

<style scoped>
.message-link-alert {
  margin-bottom: 16px;
}

.auth-register-warn {
  margin: -4px 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning);
}

.auth-register-form .outbound-fields-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px 16px;
}
</style>
