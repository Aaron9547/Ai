<template>
  <button
    type="button"
    :class="[
      'quick-chip',
      {
        'quick-chip--flash': flashing,
        'quick-chip--compact': compact,
        'quick-chip--refresh': dashed,
      },
    ]"
    :disabled="disabled"
    @click="onClick"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
    compact?: boolean;
    dashed?: boolean;
  }>(),
  { disabled: false, compact: false, dashed: false },
);

const emit = defineEmits<{ click: [] }>();

const flashing = ref(false);
let flashTimer: ReturnType<typeof setTimeout> | null = null;

function onClick(): void {
  if (props.disabled) return;
  flashing.value = true;
  if (flashTimer) clearTimeout(flashTimer);
  flashTimer = setTimeout(() => {
    flashing.value = false;
    flashTimer = null;
  }, 200);
  emit("click");
}
</script>

<style scoped>
.quick-chip {
  padding: 10px 16px;
  border-radius: 20px;
  border: 1px solid #e8ecef;
  background: transparent;
  color: #9aa8b5;
  font-size: 13px;
  line-height: 1.35;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.quick-chip:hover:not(:disabled) {
  border-color: var(--chat-accent, #5b9fd4);
  color: var(--chat-accent, #5b9fd4);
  background: rgba(91, 159, 212, 0.1);
}

.quick-chip--flash {
  border-color: var(--chat-accent, #5b9fd4);
  color: var(--chat-accent, #5b9fd4);
  background: rgba(91, 159, 212, 0.14);
}

.quick-chip--compact {
  padding: 6px 12px;
  font-size: 12px;
}

.quick-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.quick-chip--refresh {
  border-style: dashed;
}

:global(html.dark) .quick-chip {
  border-color: var(--chat-border-subtle, #3f3f46);
  color: var(--chat-text-muted, #a1a1aa);
  background: transparent;
}

:global(html.dark) .quick-chip:hover:not(:disabled) {
  border-color: var(--chat-accent, #34d399);
  color: var(--chat-accent, #34d399);
  background: rgba(52, 211, 153, 0.1);
}
</style>
