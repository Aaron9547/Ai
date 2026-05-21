<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    align-center
    destroy-on-close
    :close-on-click-modal="false"
    class="kb-data-panel-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="onOpened"
    @closed="emit('closed')"
  >
    <template #header>
      <div class="kb-data-panel-dialog__header">
        <h3 class="kb-data-panel-dialog__title">{{ title }}</h3>
        <p v-if="subtitle" class="kb-data-panel-dialog__subtitle">{{ subtitle }}</p>
      </div>
    </template>

    <div ref="shellRef" class="kb-data-panel-dialog__shell">
      <div v-if="$slots.toolbar" class="kb-data-panel-dialog__toolbar">
        <slot name="toolbar" />
      </div>
      <div class="kb-data-panel-dialog__body">
        <div class="kb-data-panel-dialog__table-slot">
          <slot :table-height="tableHeight" />
        </div>
      </div>
      <div v-if="$slots.footer" class="kb-data-panel-dialog__footer">
        <slot name="footer" />
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, watch } from "vue";
import { useKbDataPanelTableHeight } from "@/composables/useKbDataPanelTableHeight";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    subtitle?: string;
    width?: string;
    /** 表体最大高度（px），随视口在 composable 内钳制 */
    tableMaxHeight?: number;
  }>(),
  {
    width: "min(1040px, 96vw)",
    tableMaxHeight: 480,
  },
);

const emit = defineEmits<{
  "update:modelValue": [boolean];
  closed: [];
}>();

const open = computed(() => props.modelValue);
const { shellRef, tableHeight, remeasure } = useKbDataPanelTableHeight(open, {
  max: props.tableMaxHeight,
  min: 200,
  reservedPx: 4,
});

function onOpened() {
  requestAnimationFrame(() => remeasure());
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      requestAnimationFrame(() => remeasure());
    }
  },
);

defineExpose({ remeasure, tableHeight });
</script>

<style scoped>
.kb-data-panel-dialog :deep(.el-dialog) {
  margin: 3vh auto !important;
  max-height: 94vh;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--el-box-shadow);
}

.kb-data-panel-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.kb-data-panel-dialog :deep(.el-dialog__headerbtn) {
  top: 14px;
  right: 14px;
}

.kb-data-panel-dialog :deep(.el-dialog__body) {
  padding: 0 20px 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.kb-data-panel-dialog__header {
  padding-right: 28px;
}

.kb-data-panel-dialog__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.kb-data-panel-dialog__subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.kb-data-panel-dialog__shell {
  display: flex;
  flex-direction: column;
  height: min(70vh, calc(100vh - 168px));
  min-height: 320px;
}

.kb-data-panel-dialog__toolbar {
  flex-shrink: 0;
  padding: 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.kb-data-panel-dialog__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.kb-data-panel-dialog__body :deep(.kb-data-panel-dialog__table-slot) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.kb-data-panel-dialog__footer {
  flex-shrink: 0;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-extra-light);
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
</style>
