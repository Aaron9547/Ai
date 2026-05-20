<script setup lang="ts">
import { InfoFilled } from "@element-plus/icons-vue";

defineProps<{
  label: string;
  tip?: string;
}>();
</script>

<template>
  <span class="kb-form-label-tip">
    <span class="kb-form-label-tip-text">{{ label }}</span>
    <el-tooltip
      v-if="tip"
      :content="tip"
      placement="top"
      :show-after="200"
      popper-class="kb-form-label-tooltip"
    >
      <span class="kb-form-label-tip-hit" tabindex="0" role="button" :aria-label="label">
        <el-icon class="kb-form-label-tip-icon"><InfoFilled /></el-icon>
      </span>
    </el-tooltip>
  </span>
</template>

<style scoped>
.kb-form-label-tip {
  display: inline-flex;
  width: 100%;
  max-width: 100%;
  min-height: var(--el-component-size);
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 4px 6px;
  box-sizing: border-box;
  line-height: 1.35;
}

.kb-form-label-tip-text {
  flex: 0 1 auto;
  min-width: 0;
  text-align: right;
}

.kb-form-label-tip-hit {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  outline: none;
}

.kb-form-label-tip-icon {
  cursor: help;
  color: var(--el-text-color-secondary);
  font-size: 15px;
}

.kb-form-label-tip-hit:hover .kb-form-label-tip-icon,
.kb-form-label-tip-hit:focus-visible .kb-form-label-tip-icon {
  color: var(--el-color-primary);
}
</style>

<style>
.kb-form-label-tooltip {
  max-width: min(360px, calc(100vw - 32px));
  line-height: 1.55;
}
</style>
