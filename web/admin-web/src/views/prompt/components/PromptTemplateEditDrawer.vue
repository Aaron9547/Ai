<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? t('views.promptTemplate.editTitle') : t('views.promptTemplate.createTitle')"
    size="720px"
    destroy-on-close
    @closed="onClosed"
  >
    <el-alert type="info" :closable="false" show-icon class="top-alert">
      {{
        cloneFrom
          ? t("views.promptTemplate.cloneAlert", { code: cloneFrom.promptCode })
          : t("views.promptTemplate.editAlert")
      }}
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form">
      <el-form-item :label="t('views.promptTemplate.fieldCode')" prop="promptCode">
        <el-input
          v-model="form.promptCode"
          :disabled="isEdit"
          :placeholder="t('views.promptTemplate.fieldCodePh')"
        />
        <div class="extra">{{ t("views.promptTemplate.fieldCodeExtra") }}</div>
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item :label="t('views.promptTemplate.fieldKind')" prop="promptKind">
            <el-select v-model="form.promptKind" :disabled="isEdit" style="width: 100%">
              <el-option
                v-for="k in kindOptions"
                :key="k.value"
                :label="k.label"
                :value="k.value"
              >
                <div>{{ k.label }}</div>
                <div class="opt-sub">{{ k.hint }}</div>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('views.promptTemplate.fieldDomain')" prop="domain">
            <el-select v-model="form.domain" :disabled="isEdit" style="width: 100%">
              <el-option
                v-for="d in domainOptions"
                :key="d"
                :label="t(`views.promptTemplate.domain.${d}`)"
                :value="d"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item :label="t('views.promptTemplate.fieldLocale')" prop="locale">
            <el-select v-model="form.locale" :disabled="isEdit" style="width: 100%">
              <el-option
                v-for="loc in localeOptions"
                :key="loc"
                :label="localeLabel(loc)"
                :value="loc"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="!isEdit" :label="t('views.promptTemplate.fieldVersion')">
            <el-input-number v-model="form.version" :min="1" :max="999" style="width: 100%" />
            <div class="extra">{{ t("views.promptTemplate.fieldVersionExtra") }}</div>
          </el-form-item>
          <el-form-item v-else :label="t('views.promptTemplate.fieldVersion')">
            <span>{{ row?.version }}</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item :label="t('views.promptTemplate.fieldContent')" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="16" class="mono" />
        <div class="extra">{{ t("views.promptTemplate.fieldContentExtra") }}</div>
      </el-form-item>

      <el-form-item
        v-if="form.promptKind === 'USER'"
        :label="t('views.promptTemplate.fieldVars')"
      >
        <el-input
          v-model="form.variablesSchemaJson"
          type="textarea"
          :rows="4"
          class="mono"
          :placeholder="t('views.promptTemplate.fieldVarsPh')"
        />
      </el-form-item>

      <el-form-item :label="t('views.promptTemplate.fieldRemark')">
        <el-input v-model="form.remark" />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item :label="t('views.promptTemplate.fieldSort')">
            <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('views.promptTemplate.fieldEnabled')">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ t("common.cancel") }}</el-button>
      <el-button type="primary" :loading="saving" @click="submit(false)">
        {{ t("common.save") }}
      </el-button>
      <el-button type="primary" plain :loading="saving" @click="submit(true)">
        {{ t("views.promptTemplate.saveAndEvict") }}
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  createPromptTemplate,
  evictPromptTemplateCache,
  updatePromptTemplate,
  type PromptTemplateDomain,
  type PromptTemplateKind,
  type PromptTemplateRow,
} from "@/api/promptTemplate";
import { PROMPT_TEMPLATE_LOCALES, promptTemplateLocaleLabel } from "../promptTemplateLocale";

const props = defineProps<{
  modelValue: boolean;
  row: PromptTemplateRow | null;
  /** 从平台默认复制为本租户覆盖（新建模式） */
  cloneFrom?: PromptTemplateRow | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [boolean];
  saved: [];
}>();

const { t } = useI18n();
const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit("update:modelValue", v),
});
const isEdit = computed(() => props.row != null);
const cloneFrom = computed(() => props.cloneFrom ?? null);
const saving = ref(false);
const formRef = ref<FormInstance>();

const form = reactive({
  promptCode: "",
  promptKind: "SYSTEM" as PromptTemplateKind,
  domain: "CHAT" as PromptTemplateDomain,
  locale: "zh-CN",
  content: "",
  variablesSchemaJson: "",
  version: 1,
  enabled: true,
  remark: "",
  sortOrder: 0,
});

const rules: FormRules = {
  promptCode: [{ required: true, message: "required", trigger: "blur" }],
  promptKind: [{ required: true, trigger: "change" }],
  domain: [{ required: true, trigger: "change" }],
  content: [{ required: true, trigger: "blur" }],
};

const localeOptions = PROMPT_TEMPLATE_LOCALES;

function localeLabel(locale: string) {
  return promptTemplateLocaleLabel(locale, t);
}

const domainOptions: PromptTemplateDomain[] = [
  "CHAT",
  "MEMORY",
  "RAG",
  "WEB",
  "PLANET",
  "STARTER",
  "GUARD",
];

const kindOptions = computed(() => [
  {
    value: "SYSTEM" as const,
    label: t("views.promptTemplate.kind.SYSTEM"),
    hint: t("views.promptTemplate.kindHint.SYSTEM"),
  },
  {
    value: "USER" as const,
    label: t("views.promptTemplate.kind.USER"),
    hint: t("views.promptTemplate.kindHint.USER"),
  },
  {
    value: "FRAGMENT" as const,
    label: t("views.promptTemplate.kind.FRAGMENT"),
    hint: t("views.promptTemplate.kindHint.FRAGMENT"),
  },
  {
    value: "QUERY" as const,
    label: t("views.promptTemplate.kind.QUERY"),
    hint: t("views.promptTemplate.kindHint.QUERY"),
  },
]);

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    const seed = props.row ?? props.cloneFrom;
    if (props.row) {
      form.promptCode = props.row.promptCode;
      form.promptKind = props.row.promptKind;
      form.domain = props.row.domain;
      form.locale = props.row.locale;
      form.content = props.row.content;
      form.variablesSchemaJson = props.row.variablesSchemaJson ?? "";
      form.version = props.row.version;
      form.enabled = props.row.enabled;
      form.remark = props.row.remark ?? "";
      form.sortOrder = props.row.sortOrder;
    } else if (seed) {
      form.promptCode = seed.promptCode;
      form.promptKind = seed.promptKind;
      form.domain = seed.domain;
      form.locale = seed.locale;
      form.content = seed.content;
      form.variablesSchemaJson = seed.variablesSchemaJson ?? "";
      form.version = 1;
      form.enabled = seed.enabled;
      form.remark = seed.remark ?? "";
      form.sortOrder = seed.sortOrder;
    } else {
      form.promptCode = "";
      form.promptKind = "SYSTEM";
      form.domain = "CHAT";
      form.locale = "zh-CN";
      form.content = "";
      form.variablesSchemaJson = "";
      form.version = 1;
      form.enabled = true;
      form.remark = "";
      form.sortOrder = 0;
    }
  },
);

function onClosed() {
  formRef.value?.resetFields();
}

async function submit(evictAfter: boolean) {
  const ok = await formRef.value?.validate().catch(() => false);
  if (!ok) return;
  saving.value = true;
  try {
    if (isEdit.value && props.row) {
      await updatePromptTemplate(props.row.id, {
        content: form.content,
        variablesSchemaJson: form.variablesSchemaJson || null,
        enabled: form.enabled,
        remark: form.remark || null,
        sortOrder: form.sortOrder,
      });
    } else {
      await createPromptTemplate({
        promptCode: form.promptCode.trim(),
        promptKind: form.promptKind,
        domain: form.domain,
        locale: form.locale,
        content: form.content,
        variablesSchemaJson: form.variablesSchemaJson || null,
        version: form.version,
        enabled: form.enabled,
        remark: form.remark || null,
        sortOrder: form.sortOrder,
      });
    }
    if (evictAfter) {
      await evictPromptTemplateCache({
        promptCode: form.promptCode.trim(),
        locale: form.locale,
      });
    }
    ElMessage.success(t("common.saved"));
    visible.value = false;
    emit("saved");
  } catch {
    ElMessage.error(t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.top-alert {
  margin-bottom: 16px;
}
.form {
  padding-right: 8px;
}
.extra {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin-top: 4px;
}
.mono :deep(textarea) {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
}
.opt-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
