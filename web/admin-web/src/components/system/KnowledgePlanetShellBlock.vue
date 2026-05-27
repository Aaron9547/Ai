<template>
  <el-card class="block tab-pane-card" shadow="never">
    <template #header>
      <div class="outbound-card-header">
        <span class="outbound-card-title">{{ t("admin.shell.knowledgePlanet.blockTitle") }}</span>
        <span class="outbound-card-sub">{{ t("admin.shell.knowledgePlanet.blockSub") }}</span>
      </div>
    </template>
    <el-alert type="info" :closable="false" show-icon class="message-link-alert">
      <template #title>{{ t("admin.message.shellWeeklyHint") }}</template>
      <el-button link type="primary" @click="router.push('/system/message-templates')">
        {{ t("admin.message.goTemplates") }}
      </el-button>
    </el-alert>
    <p v-if="!emailDeliveryReady && form.enabled && form.emailEnabled" class="auth-register-warn">
      {{ t("admin.shell.knowledgePlanet.emailNotReady") }}
    </p>
    <el-form label-width="auto" class="shell-form auth-register-form">
      <el-form-item :label="t('admin.shell.knowledgePlanet.enabled')">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.digestModel')">
        <el-select
          v-model="digestModelId"
          class="kp-digest-model-select"
          clearable
          filterable
          :loading="loadingLanguageModels"
          :placeholder="t('admin.shell.knowledgePlanet.digestModelPh')"
        >
          <el-option
            v-for="opt in digestModelSelectOptions"
            :key="opt.id"
            :label="opt.label"
            :value="opt.id"
            :disabled="opt.disabled"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('admin.shell.knowledgePlanet.emailEnabled')">
        <el-switch v-model="form.emailEnabled" />
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
import { computed, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { listLlmModels, type LlmModelAdminView } from "@/api/models";
import {
  getTenantShellConfig,
  putTenantShellKnowledgePlanet,
  type TenantShellKnowledgePlanetPutBody,
} from "@/api/tenantShellConfig";

const { t, locale } = useI18n();
const router = useRouter();
const saving = ref(false);
const loadingLanguageModels = ref(false);
const emailDeliveryReady = ref(false);
const languageModels = ref<LlmModelAdminView[]>([]);
const digestModelId = ref<number | undefined>(undefined);

const form = reactive<TenantShellKnowledgePlanetPutBody>({
  enabled: false,
  digestModelId: "",
  emailEnabled: true,
});

const digestModelSelectOptions = computed(() => {
  void locale.value;
  const selected = digestModelId.value;
  const rows = languageModels.value
    .slice()
    .sort((a, b) => {
      if (a.enabled !== b.enabled) return a.enabled ? -1 : 1;
      const ao = a.sortOrder ?? 0;
      const bo = b.sortOrder ?? 0;
      if (ao !== bo) return ao - bo;
      return a.id - b.id;
    })
    .map((m) => ({
      id: m.id,
      label: `${m.displayName} (${m.alias}) · ${m.openaiModelId}`,
      disabled: !m.enabled && m.id !== selected,
    }));
  if (typeof selected === "number" && !rows.some((r) => r.id === selected)) {
    return [
      {
        id: selected,
        label: t("admin.shell.knowledgePlanet.digestModelOrphan", { id: selected }),
        disabled: false,
      },
      ...rows,
    ];
  }
  return rows;
});

function parseDigestModelId(raw: string | undefined | null): number | undefined {
  if (raw == null || raw.trim() === "") return undefined;
  const n = Number(raw.trim());
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

function applyFromConfig(kp: NonNullable<Awaited<ReturnType<typeof getTenantShellConfig>>["knowledgePlanet"]>): void {
  form.enabled = kp.enabled;
  digestModelId.value = parseDigestModelId(kp.digestModelId);
  form.digestModelId = digestModelId.value != null ? String(digestModelId.value) : "";
  form.emailEnabled = kp.emailEnabled;
  emailDeliveryReady.value = kp.emailDeliveryReady;
}

async function loadLanguageModels(): Promise<void> {
  loadingLanguageModels.value = true;
  try {
    languageModels.value = await listLlmModels({ modelKind: "LANGUAGE" });
  } finally {
    loadingLanguageModels.value = false;
  }
}

async function load(): Promise<void> {
  const [c] = await Promise.all([getTenantShellConfig(), loadLanguageModels()]);
  if (c.knowledgePlanet) applyFromConfig(c.knowledgePlanet);
}

void load();

async function save(): Promise<void> {
  saving.value = true;
  try {
    form.digestModelId = digestModelId.value != null ? String(digestModelId.value) : "";
    const next = await putTenantShellKnowledgePlanet({ ...form });
    if (next.knowledgePlanet) applyFromConfig(next.knowledgePlanet);
    ElMessage.success(t("admin.shell.knowledgePlanet.saveOk"));
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.message-link-alert {
  margin-bottom: 16px;
}

.auth-register-warn {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.kp-digest-model-select {
  width: 100%;
  max-width: 480px;
}
</style>
