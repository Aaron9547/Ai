<template>
  <div class="dyn-form">
    <template v-for="field in fields" :key="field.key">
      <el-form-item
        v-if="fieldVisible(field) && field.control !== 'checkbox'"
        :label="field.label"
        :required="field.required && isFieldRequired(field)"
      >
        <el-input
          v-if="field.control === 'text'"
          v-model="(form as Record<string, string>)[field.key]"
          :disabled="isEdit && field.disabledOnEdit"
          :placeholder="field.placeholder || undefined"
        />
        <el-input
          v-else-if="field.control === 'password'"
          v-model="(form as Record<string, string>)[field.key]"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="field.placeholder || undefined"
        />
        <el-switch v-else-if="field.control === 'switch'" v-model="(form as Record<string, boolean>)[field.key]" />
        <el-input-number
          v-else-if="field.control === 'number'"
          v-model="(form as Record<string, number>)[field.key]"
          :min="0"
          :max="field.key === 'maxAttachments' ? 10 : 999999999999"
          :step="field.key === 'tokenQuotaTotal' ? 1000 : 1"
          style="width: 100%"
        />
        <el-select
          v-else-if="field.control === 'select' && field.selectOptionsKey"
          v-model="(form as Record<string, string>)[field.key]"
          style="width: 100%"
          :placeholder="field.placeholder || '请选择'"
        >
          <el-option
            v-for="o in optionLists[field.selectOptionsKey] || []"
            :key="o.code"
            :label="o.label"
            :value="o.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-else-if="fieldVisible(field) && field.control === 'checkbox'"
        :label="field.label"
        class="chk-row"
      >
        <div class="chk-block">
          <el-checkbox v-model="(form as Record<string, boolean>)[field.key]" />
          <p v-if="field.placeholder" class="field-hint">{{ field.placeholder }}</p>
        </div>
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { FormFieldMeta, LlmModelAdminMetaResponse, LlmModelKindCode } from "../../../api/models";

const props = defineProps<{
  fields: FormFieldMeta[];
  form: Record<string, unknown>;
  optionLists: LlmModelAdminMetaResponse["optionLists"];
  isEdit: boolean;
  modelKind: LlmModelKindCode;
}>();

function fieldVisible(field: FormFieldMeta): boolean {
  if (field.key === "clearApiKey" && (!props.isEdit || props.modelKind !== "VECTOR")) {
    return false;
  }
  const su = field.showUnless;
  if (!su) return true;
  return props.form[su.field] !== su.equalsValue;
}

function isFieldRequired(field: FormFieldMeta): boolean {
  if (field.key === "apiKey" && props.modelKind === "VECTOR" && !props.isEdit) {
    return false;
  }
  return field.required;
}
</script>

<style scoped>
.dyn-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.dyn-form :deep(.el-form-item__label) {
  font-weight: 500;
  line-height: 32px;
  padding-right: 10px;
}

.chk-row {
  margin-bottom: 8px;
}

.field-hint {
  margin: 2px 0 0 0;
  font-size: 12px;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
}
</style>
