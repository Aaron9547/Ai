<template>
  <div ref="rootRef" class="share-capture">
    <div class="share-capture-sheet">
      <header class="share-capture-head">
        <div class="share-capture-head__brand">
          <span class="share-capture-head__logo">Ai</span>
          <span class="share-capture-head__tag">{{ t("chat.shareCaptureTag") }}</span>
        </div>
        <h2 class="share-capture-head__title">{{ title }}</h2>
      </header>

      <div class="share-capture-turns">
        <article
          v-for="(turn, ti) in turns"
          :key="turn.turnIndex"
          class="share-capture-block"
        >
          <div v-if="turns.length > 1" class="share-capture-block__label">
            {{ turnLabel(ti + 1) }}
          </div>
          <div class="share-capture-msg share-capture-msg--user">
            <div class="share-capture-msg__avatar" aria-hidden="true">
              <el-icon :size="15"><User /></el-icon>
            </div>
            <div class="share-capture-msg__bubble share-capture-msg__bubble--user">
              {{ turn.userPlain }}
            </div>
          </div>
          <div class="share-capture-msg share-capture-msg--assistant">
            <div class="share-capture-msg__avatar" aria-hidden="true">
              <el-icon :size="15"><ChatLineRound /></el-icon>
            </div>
            <div class="share-capture-msg__bubble share-capture-msg__bubble--assistant bubble-md">
              <MarkdownRichContent :source="turn.assistantSource" />
            </div>
          </div>
        </article>
      </div>

      <footer class="share-capture-foot">{{ footer }}</footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChatLineRound, User } from "@element-plus/icons-vue";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import MarkdownRichContent from "./MarkdownRichContent.vue";

export type ShareCaptureTurn = {
  turnIndex: number;
  userPlain: string;
  assistantSource: string;
};

defineProps<{
  title: string;
  turns: ShareCaptureTurn[];
  footer: string;
}>();

const { t } = useI18n();
const rootRef = ref<HTMLElement | null>(null);

function turnLabel(n: number) {
  return t("chat.shareTurnLabel", { n });
}

defineExpose({
  getElement: () => rootRef.value,
});
</script>

<style scoped>
.share-capture {
  width: 480px;
  box-sizing: border-box;
  padding: 20px;
  background: linear-gradient(165deg, #f3f4f6 0%, #e8eaef 100%);
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  color: #18181b;
}

.share-capture-sheet {
  background: #ffffff;
  border-radius: 16px;
  padding: 22px 20px 18px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 12px 40px rgba(15, 23, 42, 0.08);
}

.share-capture-head {
  margin-bottom: 22px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f2;
}

.share-capture-head__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.share-capture-head__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border-radius: 9px;
  background: #202020;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.share-capture-head__tag {
  font-size: 11px;
  font-weight: 500;
  color: #71717a;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.share-capture-head__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
  color: #18181b;
  word-break: break-word;
}

.share-capture-turns {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.share-capture-block__label {
  margin-bottom: 12px;
  font-size: 11px;
  font-weight: 600;
  color: #a1a1aa;
  letter-spacing: 0.04em;
}

.share-capture-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.share-capture-msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
}

.share-capture-msg--user {
  flex-direction: row-reverse;
}

.share-capture-msg__avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4f4f5;
  color: #52525b;
}

.share-capture-msg--user .share-capture-msg__avatar {
  background: #eff6ff;
  color: #2563eb;
}

.share-capture-msg__bubble {
  flex: 1;
  min-width: 0;
  max-width: calc(100% - 42px);
  box-sizing: border-box;
}

.share-capture-msg__bubble--user {
  padding: 11px 14px;
  border-radius: 16px;
  border-bottom-right-radius: 5px;
  background: #f4f4f5;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #18181b;
}

.share-capture-msg__bubble--assistant {
  padding: 11px 14px;
  border-radius: 16px;
  border-bottom-left-radius: 5px;
  background: #fafafa;
  border: 1px solid #f0f0f2;
  font-size: 14px;
  line-height: 1.6;
  color: #18181b;
}

.share-capture-msg__bubble--assistant :deep(p) {
  margin: 0 0 0.55em;
}

.share-capture-msg__bubble--assistant :deep(p:last-child) {
  margin-bottom: 0;
}

.share-capture-msg__bubble--assistant :deep(ul),
.share-capture-msg__bubble--assistant :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.25em;
}

.share-capture-msg__bubble--assistant :deep(li) {
  margin: 0.2em 0;
}

.share-capture-msg__bubble--assistant :deep(h1),
.share-capture-msg__bubble--assistant :deep(h2),
.share-capture-msg__bubble--assistant :deep(h3) {
  margin: 0.6em 0 0.35em;
  font-size: 1em;
  font-weight: 600;
  line-height: 1.35;
}

.share-capture-msg__bubble--assistant :deep(pre) {
  margin: 0.55em 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #ececef;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.45;
}

.share-capture-msg__bubble--assistant :deep(code) {
  font-size: 0.9em;
}

.share-capture-msg__bubble--assistant :deep(p code),
.share-capture-msg__bubble--assistant :deep(li code) {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: #f4f4f5;
}

.share-capture-msg__bubble--assistant :deep(blockquote) {
  margin: 0.5em 0;
  padding-left: 12px;
  border-left: 3px solid #e4e4e7;
  color: #52525b;
}

.share-capture-foot {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f2;
  font-size: 11px;
  line-height: 1.5;
  color: #a1a1aa;
  text-align: center;
}
</style>
