<template>
  <el-card class="block" shadow="never">
    <template #header>
      <div class="outbound-card-header">
        <span class="outbound-card-title">{{ t("admin.shell.authRegister.blockTitle") }}</span>
        <span class="outbound-card-sub">{{ t("admin.shell.authRegister.blockSub") }}</span>
      </div>
    </template>
    <p class="model-calling-tip">{{ t("admin.shell.authRegister.tip") }}</p>
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

      <div class="outbound-section-head">{{ t("admin.shell.authRegister.emailSection") }}</div>
      <p class="qq-mail-hint">{{ t("admin.shell.authRegister.qqMailHint") }}</p>
      <div class="outbound-fields-grid">
        <el-form-item :label="t('admin.shell.authRegister.emailSmtpHost')" required>
          <el-input v-model="form.emailSmtpHost" clearable class="outbound-line-input" placeholder="smtp.qq.com" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailSmtpPort')">
          <el-input-number v-model="form.emailSmtpPort" :min="1" :max="65535" controls-position="right" class="num-wide" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailUsername')" required>
          <el-input v-model="form.emailUsername" clearable class="outbound-line-input" placeholder="your@qq.com" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailPassword')" required>
          <div class="password-field-wrap">
            <el-input
              v-model="form.emailPassword"
              type="password"
              show-password
              clearable
              class="outbound-line-input"
              :placeholder="passwordPlaceholder"
              @focus="onPasswordFocus"
              @blur="onPasswordBlur"
              @clear="onPasswordClear"
            />
            <el-tag v-if="emailPasswordConfigured" type="success" size="small" class="password-set-tag">
              {{ t("admin.shell.authRegister.emailPasswordSet") }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailFrom')" required>
          <el-input v-model="form.emailFrom" clearable class="outbound-line-input" placeholder="your@qq.com" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailSsl')">
          <el-switch v-model="form.emailSsl" />
        </el-form-item>
      </div>
      <el-form-item class="outbound-field-span">
        <template #label>{{ t("admin.shell.authRegister.emailSubjectTemplate") }}</template>
        <el-input v-model="form.emailSubjectTemplate" clearable class="outbound-line-input" />
      </el-form-item>
      <el-form-item class="outbound-field-span">
        <template #label>{{ t("admin.shell.authRegister.emailBodyTemplate") }}</template>
        <el-input v-model="form.emailBodyTemplate" type="textarea" :rows="5" class="outbound-line-input" />
      </el-form-item>
      <p class="template-hint">{{ t("admin.shell.authRegister.templatePlaceholders") }}</p>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{ t("admin.shell.authRegister.save") }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  getTenantShellConfig,
  putTenantShellAuthRegister,
  type TenantShellAuthRegisterPutBody,
  type TenantShellAuthRegisterRuntime,
} from "@/api/tenantShellConfig";

/** 已保存密码在输入框中的占位展示（不会提交到服务端） */
const PASSWORD_MASK = "********";

const { t } = useI18n();
const saving = ref(false);
const emailPasswordConfigured = ref(false);

const form = reactive<TenantShellAuthRegisterPutBody & { emailPassword: string }>(defaultForm());

const passwordPlaceholder = computed(() =>
  emailPasswordConfigured.value
    ? t("admin.shell.authRegister.emailPasswordPhEdit")
    : t("admin.shell.authRegister.emailPasswordPh"),
);

function passwordReadyForDelivery(): boolean {
  const raw = form.emailPassword?.trim() ?? "";
  if (raw && raw !== PASSWORD_MASK) {
    return true;
  }
  return emailPasswordConfigured.value;
}

function isEmailFieldsReady(): boolean {
  return Boolean(
    form.emailSmtpHost?.trim()
      && form.emailFrom?.trim()
      && form.emailUsername?.trim()
      && passwordReadyForDelivery(),
  );
}

const emailDeliveryReady = computed(() => isEmailFieldsReady());

function defaultForm(): TenantShellAuthRegisterPutBody & { emailPassword: string } {
  return {
    openRegistration: false,
    codeLength: 6,
    codeTtlSeconds: 600,
    sendCooldownSeconds: 60,
    emailSmtpHost: "",
    emailSmtpPort: 465,
    emailUsername: "",
    emailPassword: "",
    emailFrom: "",
    emailSsl: true,
    emailSubjectTemplate: "【{tenantName}】注册验证码",
    emailBodyTemplate:
      "您好，\n\n您正在注册账号，验证码为：{code}\n\n验证码 {ttlMinutes} 分钟内有效，请勿泄露给他人。",
  };
}

function applyPasswordDisplayFromConfigured() {
  form.emailPassword = emailPasswordConfigured.value ? PASSWORD_MASK : "";
}

function applyFromApi(ar: TenantShellAuthRegisterRuntime | undefined) {
  if (!ar) return;
  emailPasswordConfigured.value = Boolean(ar.emailPasswordConfigured);
  form.codeLength = ar.codeLength || 6;
  form.codeTtlSeconds = ar.codeTtlSeconds || 600;
  form.sendCooldownSeconds = ar.sendCooldownSeconds || 60;
  form.emailSmtpHost = ar.emailSmtpHost ?? "";
  form.emailSmtpPort = ar.emailSmtpPort || 465;
  form.emailUsername = ar.emailUsername ?? "";
  form.emailFrom = ar.emailFrom ?? "";
  form.emailSsl = ar.emailSsl;
  form.emailSubjectTemplate = ar.emailSubjectTemplate ?? form.emailSubjectTemplate;
  form.emailBodyTemplate = ar.emailBodyTemplate ?? form.emailBodyTemplate;
  applyPasswordDisplayFromConfigured();
  form.openRegistration = ar.openRegistration;
  if (!isEmailFieldsReady()) {
    form.openRegistration = false;
  }
}

function onPasswordFocus() {
  if (form.emailPassword === PASSWORD_MASK) {
    form.emailPassword = "";
  }
}

function onPasswordBlur() {
  if (!form.emailPassword?.trim() && emailPasswordConfigured.value) {
    form.emailPassword = PASSWORD_MASK;
  }
}

function onPasswordClear() {
  form.emailPassword = "";
}

function buildSavePayload(): TenantShellAuthRegisterPutBody {
  const raw = form.emailPassword?.trim() ?? "";
  const passwordToSend =
    raw && raw !== PASSWORD_MASK ? raw : undefined;
  const payload: TenantShellAuthRegisterPutBody = {
    openRegistration: form.openRegistration,
    codeLength: form.codeLength,
    codeTtlSeconds: form.codeTtlSeconds,
    sendCooldownSeconds: form.sendCooldownSeconds,
    emailSmtpHost: form.emailSmtpHost,
    emailSmtpPort: form.emailSmtpPort,
    emailUsername: form.emailUsername,
    emailFrom: form.emailFrom,
    emailSsl: form.emailSsl,
    emailSubjectTemplate: form.emailSubjectTemplate,
    emailBodyTemplate: form.emailBodyTemplate,
  };
  if (passwordToSend !== undefined) {
    payload.emailPassword = passwordToSend;
  }
  return payload;
}

async function reload() {
  const data = await getTenantShellConfig();
  applyFromApi(data.authRegister);
}

async function save() {
  if (form.openRegistration && !isEmailFieldsReady()) {
    ElMessage.warning(t("admin.shell.authRegister.openRegistrationNeedsEmail"));
    form.openRegistration = false;
    return;
  }
  saving.value = true;
  try {
    const data = await putTenantShellAuthRegister(buildSavePayload());
    applyFromApi(data.authRegister);
    ElMessage.success(t("admin.shell.authRegister.saveOk"));
  } catch (e: unknown) {
    const msg =
      typeof e === "object"
      && e !== null
      && "response" in e
      && typeof (e as { response?: { data?: { message?: string } } }).response?.data?.message === "string"
        ? (e as { response: { data: { message: string } } }).response.data.message
        : t("common.saveFailed");
    ElMessage.error(msg);
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
.auth-register-form .template-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.auth-register-warn,
.qq-mail-hint {
  margin: -4px 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.auth-register-warn {
  color: var(--el-color-warning);
}

.password-field-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.password-field-wrap .outbound-line-input {
  flex: 1;
  min-width: 0;
}

.password-set-tag {
  flex-shrink: 0;
}

.auth-register-form .outbound-fields-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px 16px;
}
</style>
