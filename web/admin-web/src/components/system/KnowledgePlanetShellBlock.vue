<template>
  <el-card class="block tab-pane-card" shadow="never">
    <template #header>
      <div class="outbound-card-header">
        <span class="outbound-card-title">{{ t("admin.shell.knowledgePlanet.blockTitle") }}</span>
        <span class="outbound-card-sub">{{ t("admin.shell.knowledgePlanet.blockSub") }}</span>
      </div>
    </template>
    <p v-if="!emailDeliveryReady && form.enabled" class="auth-register-warn">
      {{ t("admin.shell.knowledgePlanet.emailNotReady") }}
    </p>
    <el-form label-width="auto" class="shell-form auth-register-form">
      <el-form-item :label="t('admin.shell.knowledgePlanet.enabled')">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.computeCron')">
        <el-input v-model="form.weeklyComputeCron" placeholder="0 0 3 * * MON" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.emailCron')">
        <el-input v-model="form.weeklyEmailCron" placeholder="0 0 9 * * MON" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.digestModel')">
        <el-input v-model="form.digestModelId" clearable :placeholder="t('admin.shell.knowledgePlanet.digestModelPh')" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.emailEnabled')">
        <el-switch v-model="form.emailEnabled" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.reuseSmtp')">
        <el-switch v-model="form.reuseRegisterSmtp" />
      </el-form-item>
      <template v-if="!form.reuseRegisterSmtp">
        <el-form-item :label="t('admin.shell.authRegister.emailSmtpHost')">
          <el-input v-model="form.emailSmtpHost" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailSmtpPort')">
          <el-input-number v-model="form.emailSmtpPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailUsername')">
          <el-input v-model="form.emailUsername" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailPassword')">
          <el-input v-model="form.emailPassword" type="password" show-password />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailFrom')">
          <el-input v-model="form.emailFrom" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.authRegister.emailSsl')">
          <el-switch v-model="form.emailSsl" />
        </el-form-item>
      </template>
      <el-form-item :label="t('admin.shell.authRegister.emailSubjectTemplate')">
        <el-input v-model="form.emailSubjectTemplate" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.authRegister.emailBodyTemplate')">
        <el-input v-model="form.emailBodyTemplate" type="textarea" :rows="8" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">{{
          t("admin.shell.knowledgePlanet.save")
        }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  getTenantShellConfig,
  putTenantShellKnowledgePlanet,
  type TenantShellKnowledgePlanetPutBody,
} from "@/api/tenantShellConfig";

const { t } = useI18n();
const saving = ref(false);
const emailDeliveryReady = ref(false);
const form = reactive<TenantShellKnowledgePlanetPutBody>({
  enabled: false,
  weeklyComputeCron: "0 0 3 * * MON",
  weeklyEmailCron: "0 0 9 * * MON",
  digestModelId: "",
  emailEnabled: true,
  reuseRegisterSmtp: true,
  emailSmtpHost: "",
  emailSmtpPort: 465,
  emailUsername: "",
  emailPassword: "",
  emailFrom: "",
  emailSsl: true,
  emailSubjectTemplate: "",
  emailBodyTemplate: "",
});

function applyFromConfig(kp: NonNullable<Awaited<ReturnType<typeof getTenantShellConfig>>["knowledgePlanet"]>): void {
  form.enabled = kp.enabled;
  form.weeklyComputeCron = kp.weeklyComputeCron;
  form.weeklyEmailCron = kp.weeklyEmailCron;
  form.digestModelId = kp.digestModelId ?? "";
  form.emailEnabled = kp.emailEnabled;
  form.reuseRegisterSmtp = kp.reuseRegisterSmtp;
  emailDeliveryReady.value = kp.emailDeliveryReady;
  form.emailSmtpHost = kp.emailSmtpHost;
  form.emailSmtpPort = kp.emailSmtpPort;
  form.emailUsername = kp.emailUsername;
  form.emailFrom = kp.emailFrom;
  form.emailSsl = kp.emailSsl;
  form.emailSubjectTemplate = kp.emailSubjectTemplate;
  form.emailBodyTemplate = kp.emailBodyTemplate;
  form.emailPassword = "";
}

async function load(): Promise<void> {
  const c = await getTenantShellConfig();
  if (c.knowledgePlanet) applyFromConfig(c.knowledgePlanet);
}

void load();

async function save(): Promise<void> {
  saving.value = true;
  try {
    const next = await putTenantShellKnowledgePlanet({ ...form });
    if (next.knowledgePlanet) applyFromConfig(next.knowledgePlanet);
    ElMessage.success(t("admin.shell.knowledgePlanet.saveOk"));
  } finally {
    saving.value = false;
  }
}
</script>
