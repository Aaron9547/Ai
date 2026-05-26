<script setup lang="ts">
import { computed } from "vue";
import CodeBlock from "./CodeBlock.vue";
import { buildMarkdownRichBlocks } from "@/utils/parseMarkdownRichBlocks";
import { STREAM_CURSOR_HTML } from "@/utils/renderMarkdown";

const props = withDefaults(
  defineProps<{
    source: string;
    streaming?: boolean;
    cursorHtml?: string;
  }>(),
  {
    streaming: false,
    cursorHtml: STREAM_CURSOR_HTML,
  },
);

const blocks = computed(() =>
  buildMarkdownRichBlocks(props.source ?? "", {
    streaming: props.streaming,
    cursorHtml: props.streaming ? props.cursorHtml : "",
  }),
);
</script>

<template>
  <div class="markdown-rich">
    <template
      v-for="(block, index) in blocks"
      :key="block.kind === 'code' ? `code-${index}-${block.language}-${block.code.length}` : `html-${index}`"
    >
      <CodeBlock
        v-if="block.kind === 'code'"
        :code="block.code"
        :language="block.language"
      />
      <div
        v-else-if="block.html.trim()"
        class="markdown-rich__html"
        v-html="block.html"
      />
    </template>
  </div>
</template>

<style scoped>
.markdown-rich__html :deep(p:first-child) {
  margin-top: 0;
}

.markdown-rich__html :deep(p:last-child) {
  margin-bottom: 0;
}

/* 未提升的 pre 兜底：避免 display:none 导致代码完全不可见 */
.markdown-rich__html :deep(pre) {
  margin: 0.75em 0;
  padding: 12px 16px;
  border-radius: 12px;
  background: #0f1419;
  color: #f4f4f5;
  overflow-x: auto;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre;
}

.markdown-rich__html :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}
</style>
