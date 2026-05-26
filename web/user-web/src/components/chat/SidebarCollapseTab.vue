<template>
  <button
    type="button"
    class="sidebar-collapse-tab"
    :class="`sidebar-collapse-tab--${side}`"
    :aria-label="label"
    @click="emit('click')"
  >
    <el-icon class="sidebar-collapse-tab-icon" :size="12"><component :is="arrow" /></el-icon>
  </button>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight } from "@element-plus/icons-vue";
import { computed } from "vue";

const props = defineProps<{
  /** 面板在左侧（会话栏）或右侧（推荐栏） */
  side: "left" | "right";
  label: string;
}>();

const emit = defineEmits<{ click: [] }>();

/** 收起方向：左栏向左收、右栏向右收 */
const arrow = computed(() => (props.side === "left" ? ArrowLeft : ArrowRight));
</script>

<style scoped>
.sidebar-collapse-tab {
  position: absolute;
  top: 50%;
  z-index: 30;
  width: 14px;
  height: 110px;
  padding: 0;
  margin: 0;
  border: 1px solid var(--chat-border, #e8edf2);
  background: var(--chat-bg-elevated, #fff);
  color: #6b7c8a;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 10px rgba(30, 60, 90, 0.1);
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform var(--chat-shell-duration, 0.34s) var(--chat-shell-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    opacity 0.22s ease;
}

.sidebar-collapse-tab:hover {
  background: #eef4fa;
  border-color: #c5d9eb;
  color: #3d6f94;
  box-shadow: 0 3px 14px rgba(91, 159, 212, 0.16);
}

.sidebar-collapse-tab:focus-visible {
  outline: 2px solid var(--chat-accent, #5b9fd4);
  outline-offset: 2px;
}

.sidebar-collapse-tab-icon {
  flex-shrink: 0;
}

/* 左栏：整块在栏外，标签左缘对齐侧栏右缘（边框） */
.sidebar-collapse-tab--left {
  right: 0;
  transform: translate(100%, -50%);
  border-radius: 0 8px 8px 0;
}

/* 右栏：整块在栏外，标签右缘对齐侧栏左缘（边框） */
.sidebar-collapse-tab--right {
  left: 0;
  transform: translate(-100%, -50%);
  border-radius: 8px 0 0 8px;
}
</style>
