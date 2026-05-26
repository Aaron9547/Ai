<script setup lang="ts">
import { computed } from "vue";
import CodeBlock from "./CodeBlock.vue";
import { buildMarkdownRichBlocks } from "@/utils/parseMarkdownRichBlocks";

const props = defineProps<{
  source: string;
}>();

const blocks = computed(() => buildMarkdownRichBlocks(props.source ?? ""));
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
