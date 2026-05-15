<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { InfoFilled } from "@element-plus/icons-vue";

const props = defineProps<{
  label: string;
  tipKey: string;
}>();

const { t } = useI18n();

function tooltipText(): string {
  return t(`admin.shell.outbound.tooltips.${props.tipKey}`);
}
</script>

<template>
  <span class="outbound-field-label">
    <span class="outbound-field-label-text">{{ label }}</span>
    <el-tooltip
      :content="tooltipText()"
      placement="top"
      :show-after="200"
      popper-class="outbound-param-tooltip"
    >
      <span class="outbound-field-tip-hit" tabindex="0" role="button">
        <el-icon class="outbound-field-tip-icon" :aria-label="t('admin.shell.outbound.tooltipsIconAria')">
          <InfoFilled />
        </el-icon>
      </span>
    </el-tooltip>
  </span>
</template>

<style scoped>
/* inline-flex + max-content：宽度随文案变长；不换行避免说明图标掉到下一行。
   标签列整体仍由 el-form-item__label（flex-end）靠右对齐。 */
.outbound-field-label {
  display: inline-flex;
  max-width: 100%;
  min-height: var(--el-component-size);
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 4px 6px;
  box-sizing: border-box;
}

.outbound-field-label-text {
  flex: 0 1 auto;
  min-width: 0;
  line-height: 1.35;
  text-align: right;
}

.outbound-field-tip-hit {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  outline: none;
}

.outbound-field-tip-icon {
  cursor: help;
  color: var(--el-text-color-secondary);
  font-size: 15px;
}

.outbound-field-tip-hit:hover .outbound-field-tip-icon,
.outbound-field-tip-hit:focus-visible .outbound-field-tip-icon {
  color: var(--el-color-primary);
}
</style>

<style>
/* 挂载在 body 上的 tooltip */
.outbound-param-tooltip {
  max-width: min(360px, calc(100vw - 32px));
  line-height: 1.55;
  white-space: pre-wrap;
}
</style>
