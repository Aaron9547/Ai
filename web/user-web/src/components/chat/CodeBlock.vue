<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { copyTextToUserClipboard } from "@/utils/clipboard";
import {
  formatCodeLanguageLabel,
  highlightWithPrism,
  resolvePrismLanguage,
} from "@/utils/prismSetup";

const props = withDefaults(
  defineProps<{
    code: string;
    language?: string;
  }>(),
  {
    language: "",
  },
);

const { t } = useI18n();

const highlighted = ref("");
const copied = ref(false);
const copyFailed = ref(false);
let resetTimer: number | undefined;

const languageLabel = computed(() => formatCodeLanguageLabel(props.language));
const prismLanguage = computed(() => resolvePrismLanguage(props.language));

const copyLabel = computed(() => {
  if (copied.value) {
    return t("chat.codeBlock.copied");
  }
  if (copyFailed.value) {
    return t("chat.codeBlock.copyFailed");
  }
  return t("chat.codeBlock.copy");
});

function refreshHighlight() {
  highlighted.value = highlightWithPrism(props.code ?? "", props.language);
}

watch(() => [props.code, props.language] as const, refreshHighlight, { immediate: true });

function scheduleResetFeedback() {
  if (resetTimer) {
    window.clearTimeout(resetTimer);
  }
  resetTimer = window.setTimeout(() => {
    copied.value = false;
    copyFailed.value = false;
  }, 2000);
}

async function onCopy() {
  const ok = await copyTextToUserClipboard(props.code ?? "");
  if (ok) {
    copyFailed.value = false;
    copied.value = true;
  } else {
    copied.value = false;
    copyFailed.value = true;
  }
  scheduleResetFeedback();
}
</script>

<template>
  <div
    class="ai-code-block"
    role="group"
    :aria-label="t('chat.codeBlock.aria', { lang: languageLabel })"
  >
    <div class="ai-code-header">
      <div class="ai-code-dots" aria-hidden="true">
        <span class="ai-code-dot ai-code-dot--red" />
        <span class="ai-code-dot ai-code-dot--yellow" />
        <span class="ai-code-dot ai-code-dot--green" />
      </div>

      <span class="ai-code-lang">{{ languageLabel }}</span>

      <button
        type="button"
        class="ai-code-copy"
        :class="{
          'ai-code-copy--success': copied,
          'ai-code-copy--fail': copyFailed,
        }"
        :aria-label="copyLabel"
        :title="copyLabel"
        @click="onCopy"
      >
        <svg
          v-if="!copied"
          class="ai-code-copy-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <rect x="9" y="9" width="13" height="13" rx="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
        <svg
          v-else
          class="ai-code-copy-icon ai-code-copy-icon--ok"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          aria-hidden="true"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
        </svg>
        <span>{{ copyLabel }}</span>
      </button>
    </div>

    <div class="ai-code-body">
      <pre class="ai-code-pre-wrap"><code
        class="ai-code-pre"
        :class="prismLanguage !== 'plain' ? `language-${prismLanguage}` : undefined"
        v-html="highlighted"
      ></code></pre>
    </div>
  </div>
</template>

<style scoped>
.ai-code-block {
  position: relative;
  width: 100%;
  max-width: 100%;
  margin: 1rem 0;
  border-radius: 20px;
  overflow: hidden;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.04) inset,
    0 8px 30px -8px rgba(15, 23, 42, 0.22);
}

.ai-code-block:hover {
  transform: translateY(-2px);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.04) inset,
    0 20px 50px -12px rgba(15, 23, 42, 0.45);
}

html.dark .ai-code-block {
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.05) inset,
    0 12px 40px -12px rgba(0, 0, 0, 0.55);
}

html.dark .ai-code-block:hover {
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.05) inset,
    0 20px 50px -12px rgba(0, 0, 0, 0.65);
}

.ai-code-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border: 1px solid rgba(228, 228, 231, 0.7);
  border-bottom: none;
  border-radius: 20px 20px 0 0;
  background: rgba(250, 250, 250, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

html.dark .ai-code-header {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(24, 24, 27, 0.55);
}

.ai-code-dots {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}

.ai-code-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.06);
}

.ai-code-dot--red {
  background: #ff5f57;
}

.ai-code-dot--yellow {
  background: #febc2e;
}

.ai-code-dot--green {
  background: #28c840;
}

.ai-code-lang {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #71717a;
}

html.dark .ai-code-lang {
  color: #a1a1aa;
}

.ai-code-copy {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid rgba(212, 212, 216, 0.9);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  font-weight: 500;
  color: #3f3f46;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.ai-code-block:hover .ai-code-copy {
  border-color: #d4d4d8;
}

.ai-code-copy:hover {
  background: #fff;
  color: #18181b;
}

html.dark .ai-code-copy {
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.06);
  color: #e4e4e7;
}

html.dark .ai-code-copy:hover {
  border-color: rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.ai-code-copy--success {
  border-color: rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.1);
  color: #047857;
  animation: ai-code-copy-pop 0.35s ease;
}

html.dark .ai-code-copy--success {
  color: #6ee7b7;
}

.ai-code-copy-icon {
  width: 14px;
  height: 14px;
  opacity: 0.7;
}

.ai-code-block:hover .ai-code-copy-icon {
  opacity: 1;
}

.ai-code-copy-icon--ok {
  opacity: 1;
  color: #059669;
}

html.dark .ai-code-copy-icon--ok {
  color: #34d399;
}

@keyframes ai-code-copy-pop {
  0% {
    transform: scale(0.96);
  }
  50% {
    transform: scale(1.02);
  }
  100% {
    transform: scale(1);
  }
}

.ai-code-body {
  overflow-x: auto;
  border: 1px solid rgba(228, 228, 231, 0.7);
  border-top: none;
  border-radius: 0 0 20px 20px;
  background: #0f1419;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.35) transparent;
}

html.dark .ai-code-body {
  border-color: rgba(255, 255, 255, 0.08);
}

.ai-code-body::-webkit-scrollbar {
  height: 6px;
}

.ai-code-body::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.35);
}

.ai-code-pre-wrap {
  margin: 0;
  min-width: 100%;
  padding: 16px 20px;
}

@media (min-width: 640px) {
  .ai-code-pre-wrap {
    padding: 20px;
  }
}

.ai-code-pre {
  display: block;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  color: #f4f4f5;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.65;
  text-align: left;
  white-space: pre;
  word-break: normal;
  overflow-wrap: normal;
  tab-size: 2;
  font-variant-ligatures: contextual;
}

/* Prism token palette */
.ai-code-body :deep(.token.comment),
.ai-code-body :deep(.token.prolog),
.ai-code-body :deep(.token.doctype),
.ai-code-body :deep(.token.cdata) {
  color: #6b7280;
  font-style: italic;
}

.ai-code-body :deep(.token.punctuation) {
  color: #94a3b8;
}

.ai-code-body :deep(.token.property),
.ai-code-body :deep(.token.tag),
.ai-code-body :deep(.token.boolean),
.ai-code-body :deep(.token.number),
.ai-code-body :deep(.token.constant),
.ai-code-body :deep(.token.symbol) {
  color: #fbbf24;
}

.ai-code-body :deep(.token.selector),
.ai-code-body :deep(.token.attr-name),
.ai-code-body :deep(.token.string),
.ai-code-body :deep(.token.char),
.ai-code-body :deep(.token.builtin) {
  color: #86efac;
}

.ai-code-body :deep(.token.operator),
.ai-code-body :deep(.token.entity),
.ai-code-body :deep(.token.url) {
  color: #67e8f9;
}

.ai-code-body :deep(.token.atrule),
.ai-code-body :deep(.token.attr-value),
.ai-code-body :deep(.token.keyword) {
  color: #c4b5fd;
}

.ai-code-body :deep(.token.function),
.ai-code-body :deep(.token.class-name) {
  color: #93c5fd;
}

.ai-code-body :deep(.token.regex),
.ai-code-body :deep(.token.important),
.ai-code-body :deep(.token.variable) {
  color: #fda4af;
}

.ai-code-body :deep(.token.inserted) {
  color: #6ee7b7;
}

.ai-code-body :deep(.token.deleted) {
  color: #fca5a5;
}
</style>
