<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { InfoFilled } from "@element-plus/icons-vue";

/** 完整 i18n 路径，如 admin.shell.modelCalling.tooltips.ragSnippetMaxChars */
const props = defineProps<{
  label: string;
  tooltipI18nKey?: string;
  required?: boolean;
}>();

const { t } = useI18n();

function tooltipText(): string {
  const key = props.tooltipI18nKey?.trim();
  return key ? t(key) : "";
}

const hasTooltip = computed(() => Boolean(props.tooltipI18nKey?.trim()));
</script>

<template>
  <span class="shell-field-label">
    <span v-if="required" class="shell-field-required" :aria-label="t('admin.shell.fieldRequired')">*</span>
    <span class="shell-field-label-text">{{ label }}</span>
    <el-tooltip
      v-if="hasTooltip"
      :content="tooltipText()"
      placement="top"
      :show-after="200"
      popper-class="shell-param-tooltip"
    >
      <span class="shell-field-tip-hit" tabindex="0" role="button">
        <el-icon class="shell-field-tip-icon" :aria-label="t('admin.shell.outbound.tooltipsIconAria')">
          <InfoFilled />
        </el-icon>
      </span>
    </el-tooltip>
  </span>
</template>

<style scoped>
.shell-field-label {
  display: inline-flex;
  max-width: 100%;
  min-height: var(--el-component-size);
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 4px 6px;
  box-sizing: border-box;
}

.shell-field-required {
  color: var(--el-color-danger);
  line-height: 1;
  flex-shrink: 0;
}

.shell-field-label-text {
  flex: 0 1 auto;
  min-width: 0;
  line-height: 1.35;
  text-align: right;
}

.shell-field-tip-hit {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  outline: none;
}

.shell-field-tip-icon {
  cursor: help;
  color: var(--el-text-color-secondary);
  font-size: 15px;
}

.shell-field-tip-hit:hover .shell-field-tip-icon,
.shell-field-tip-hit:focus-visible .shell-field-tip-icon {
  color: var(--el-color-primary);
}
</style>

<style>
.shell-param-tooltip {
  max-width: min(380px, calc(100vw - 32px));
  line-height: 1.55;
  white-space: pre-wrap;
}
</style>
