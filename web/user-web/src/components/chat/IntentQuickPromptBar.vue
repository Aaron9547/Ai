<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  INTENT_QUICK_ROTATE_MS,
  intentQuickPhraseEntries,
  type IntentQuickPhraseEntry,
} from "@/constants/intentQuickPrompts";

const props = defineProps<{
  /** 输入框无内容时展示轮播占位 */
  visible?: boolean;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  send: [text: string, laneId: IntentQuickPhraseEntry["laneId"]];
}>();

const { t } = useI18n();

const entries = intentQuickPhraseEntries();
const index = ref(0);

const current = computed(() => {
  if (!entries.length) return null;
  return entries[index.value % entries.length]!;
});

let timer: ReturnType<typeof setInterval> | null = null;

function rotate() {
  if (entries.length <= 1) return;
  index.value = (index.value + 1) % entries.length;
}

function startTimer() {
  stopTimer();
  if (!props.visible || entries.length <= 1) return;
  timer = setInterval(rotate, INTENT_QUICK_ROTATE_MS);
}

function stopTimer() {
  if (timer != null) {
    clearInterval(timer);
    timer = null;
  }
}

function onGhostClick() {
  if (props.disabled || !props.visible) return;
  const e = current.value;
  if (!e) return;
  emit("send", e.text, e.laneId);
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      startTimer();
    } else {
      stopTimer();
    }
  },
  { immediate: true },
);

onUnmounted(stopTimer);
</script>

<template>
  <div v-show="visible && current" class="composer-intent-ghost">
    <Transition name="composer-intent-fade" mode="out-in">
      <button
        :key="current?.text"
        type="button"
        class="composer-intent-ghost-text"
        :disabled="disabled"
        :aria-label="t('chat.intentQuick.sendAria', { phrase: current?.text ?? '' })"
        @click="onGhostClick"
      >
        {{ current?.text }}
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.composer-intent-ghost {
  position: absolute;
  inset: 4px 0;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  margin: 0;
  padding: 6px 0;
  border: none;
  background: transparent;
  text-align: left;
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  pointer-events: none;
}

.composer-intent-ghost:disabled .composer-intent-ghost-text {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-intent-ghost-text {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.55;
  color: #cbd5e1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  pointer-events: auto;
  cursor: pointer;
  border: none;
  background: transparent;
  padding: 0;
}

.composer-intent-fade-enter-active,
.composer-intent-fade-leave-active {
  transition: opacity 0.22s ease;
}

.composer-intent-fade-enter-from,
.composer-intent-fade-leave-to {
  opacity: 0;
}
</style>
