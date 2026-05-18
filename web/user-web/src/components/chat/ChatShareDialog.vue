<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('chat.shareDialogTitle')"
    width="min(520px, 94vw)"
    destroy-on-close
    class="share-dlg"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <section class="share-section">
      <header class="share-section__head">
        <h3 class="share-section__title">{{ t("chat.sharePickSection") }}</h3>
        <p class="share-section__hint">{{ t("chat.shareSelectHint") }}</p>
      </header>
      <el-scrollbar class="share-turn-scroll" max-height="200px">
        <ul class="share-turn-list" role="group" :aria-label="t('chat.sharePickSection')">
          <li v-for="turn in turns" :key="turn.index" class="share-turn-list__item">
            <button
              type="button"
              class="share-turn-item"
              :class="{
                'share-turn-item--active': isTurnSelected(turn.index),
                'share-turn-item--disabled': !isTurnReady(turn),
              }"
              :disabled="!isTurnReady(turn)"
              :aria-pressed="isTurnSelected(turn.index)"
              @click="toggleTurn(turn.index)"
            >
              <span class="share-turn-item__check" aria-hidden="true">
                <el-icon v-if="isTurnSelected(turn.index)" :size="13"><Check /></el-icon>
              </span>
              <span class="share-turn-item__body">
                <span class="share-turn-item__label">{{ turn.label }}</span>
                <span v-if="turn.snippet" class="share-turn-item__snippet">{{ turn.snippet }}</span>
                <span v-else-if="!isTurnReady(turn)" class="share-turn-item__pending">
                  {{ t("chat.shareTurnPending") }}
                </span>
              </span>
            </button>
          </li>
        </ul>
      </el-scrollbar>
    </section>

    <section v-if="previewUrl || imageGenerating" class="share-section share-section--preview">
      <h3 class="share-section__title">{{ t("chat.sharePreviewSection") }}</h3>
      <div v-if="imageGenerating && !previewUrl" class="share-preview-loading">
        {{ t("chat.shareGenerating") }}
      </div>
      <el-scrollbar v-else-if="previewUrl" class="share-preview-scroll" max-height="300px">
        <div class="share-preview-stage">
          <img :src="previewUrl" class="share-preview-img" :alt="t('chat.shareImageAlt')" />
        </div>
      </el-scrollbar>
    </section>

    <div ref="captureHostRef" class="share-capture-host" aria-hidden="true">
      <ChatShareCaptureCard
        v-if="captureTurns.length"
        ref="captureCardRef"
        :title="conversationTitle"
        :turns="captureTurns"
        :footer="t('chat.shareSnippetFooter')"
      />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t("chat.close") }}</el-button>
      <el-button
        type="primary"
        plain
        :loading="imageGenerating"
        :disabled="!previewBlob && !imageGenerating"
        @click="copyImage"
      >
        {{ t("chat.copyShareImage") }}
      </el-button>
      <el-button
        type="primary"
        :loading="linkCreating"
        :disabled="!canCreateLink"
        @click="copyShareClipboard"
      >
        {{ t("chat.copyShareLink") }}
      </el-button>
      <el-button v-if="canWebShare && previewBlob" type="primary" plain @click="systemShare">
        {{ t("chat.systemShare") }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Check } from "@element-plus/icons-vue";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import ChatShareCaptureCard, { type ShareCaptureTurn } from "./ChatShareCaptureCard.vue";
import * as chatApi from "../../api/chat";
import { renderMarkdownToSafeHtml } from "../../utils/renderMarkdown";
import {
  buildChatShareTurns,
  findTurnByAssistantIndex,
  messageIdsForTurns,
  type ChatShareTurn,
} from "../../utils/chatShareTurns";
import {
  captureElementToBlob,
  copyImageBlobToClipboard,
  downloadBlob,
} from "../../utils/chatShareImage";
import { copyTextToUserClipboard } from "../../utils/clipboard";
import { buildShareClipboardText } from "../../utils/chatShareClipboard";

export type ShareDialogMsg = {
  id?: number;
  role: "user" | "assistant" | "system";
  content?: string;
  workflowSegments?: { text?: string | null; status: string }[];
};

const props = defineProps<{
  modelValue: boolean;
  conversationId: number | null;
  conversationTitle: string;
  messages: ShareDialogMsg[];
  /** 点击分享时对应的助手消息下标 */
  anchorAssistantIdx: number;
  tenantCode: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [v: boolean];
}>();

const { t, locale } = useI18n();

const turns = computed(() =>
  buildChatShareTurns(props.messages, (n) => t("chat.shareTurnLabel", { n })),
);

const selectedTurnIndexes = ref<number[]>([]);

function isTurnReady(turn: ChatShareTurn): boolean {
  return turn.userId != null && turn.assistantId != null;
}

function isTurnSelected(index: number): boolean {
  return selectedTurnIndexes.value.includes(index);
}

function toggleTurn(index: number) {
  const turn = turns.value.find((x) => x.index === index);
  if (!turn || !isTurnReady(turn)) {
    return;
  }
  const cur = [...selectedTurnIndexes.value];
  const i = cur.indexOf(index);
  if (i >= 0) {
    cur.splice(i, 1);
  } else {
    cur.push(index);
  }
  selectedTurnIndexes.value = cur.sort((a, b) => a - b);
}
const previewUrl = ref("");
const previewBlob = ref<Blob | null>(null);
const imageGenerating = ref(false);
const linkCreating = ref(false);
/** 与当前选中轮次绑定的分享链接缓存 */
const shareLinkCache = ref<{
  key: string;
  url: string;
  expiresAt: string | null;
} | null>(null);

const shareSelectionKey = computed(() =>
  [...selectedTurnIndexes.value].sort((a, b) => a - b).join(","),
);
const captureHostRef = ref<HTMLElement | null>(null);
const captureCardRef = ref<InstanceType<typeof ChatShareCaptureCard> | null>(null);

const canWebShare = computed(
  () => typeof navigator !== "undefined" && typeof navigator.share === "function",
);

const canCreateLink = computed(() => {
  if (!props.conversationId) {
    return false;
  }
  const ids = messageIdsForTurns(turns.value, selectedTurnIndexes.value);
  return ids.length > 0;
});

const captureTurns = computed((): ShareCaptureTurn[] => {
  const sel = new Set(selectedTurnIndexes.value);
  const result: ShareCaptureTurn[] = [];
  for (const turn of turns.value) {
    if (!sel.has(turn.index)) {
      continue;
    }
    const user = props.messages[turn.userIdx];
    const assistant = props.messages[turn.assistantIdx];
    if (!user || !assistant) {
      continue;
    }
    result.push({
      turnIndex: turn.index,
      userPlain: user.content ?? "",
      assistantHtml: assistantHtml(assistant),
    });
  }
  return result;
});

function assistantHtml(m: ShareDialogMsg): string {
  if ((m.workflowSegments?.length ?? 0) > 0) {
    const parts = m.workflowSegments!
      .filter((s) => s.text && s.status !== "loading")
      .map((s) => renderMarkdownToSafeHtml(s.text || ""));
    return parts.join("");
  }
  return renderMarkdownToSafeHtml(m.content ?? "");
}

watch(selectedTurnIndexes, () => {
  shareLinkCache.value = null;
  if (props.modelValue) {
    void generatePreview();
  }
});

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      revokePreview();
      shareLinkCache.value = null;
      return;
    }
    const idx = findTurnByAssistantIndex(turns.value, props.anchorAssistantIdx);
    selectedTurnIndexes.value = turns.value.some((x) => x.index === idx) ? [idx] : [];
    void generatePreview();
  },
);

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
  }
  previewUrl.value = "";
  previewBlob.value = null;
}

async function generatePreview() {
  if (!captureTurns.value.length) {
    revokePreview();
    return;
  }
  imageGenerating.value = true;
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    const el = captureCardRef.value?.getElement();
    if (!el) {
      return;
    }
    const blob = await captureElementToBlob(el);
    if (!blob) {
      ElMessage.warning(t("chat.shareImageFail"));
      return;
    }
    revokePreview();
    previewBlob.value = blob;
    previewUrl.value = URL.createObjectURL(blob);
  } catch {
    ElMessage.warning(t("chat.shareImageFail"));
  } finally {
    imageGenerating.value = false;
  }
}

async function copyImage() {
  if (!previewBlob.value) {
    await generatePreview();
  }
  const blob = previewBlob.value;
  if (!blob) {
    return;
  }
  const ok = await copyImageBlobToClipboard(blob);
  if (ok) {
    ElMessage.success(t("chat.shareImageCopied"));
  } else {
    downloadBlob(blob, `chat-share-${Date.now()}.png`);
    ElMessage.info(t("chat.shareImageDownloaded"));
  }
}

async function ensureShareLink(): Promise<{ url: string; expiresAt: string | null } | null> {
  if (!props.conversationId) {
    return null;
  }
  const ids = messageIdsForTurns(turns.value, selectedTurnIndexes.value);
  if (!ids.length) {
    ElMessage.warning(t("chat.shareLinkNeedPersisted"));
    return null;
  }
  const key = shareSelectionKey.value;
  if (shareLinkCache.value?.key === key) {
    return {
      url: shareLinkCache.value.url,
      expiresAt: shareLinkCache.value.expiresAt,
    };
  }
  linkCreating.value = true;
  try {
    const res = await chatApi.createConversationShare(props.conversationId, { messageIds: ids });
    const url = `${window.location.origin}${res.sharePath}`;
    shareLinkCache.value = { key, url, expiresAt: res.expiresAt };
    return { url, expiresAt: res.expiresAt };
  } catch {
    ElMessage.error(t("chat.shareLinkFail"));
    return null;
  } finally {
    linkCreating.value = false;
  }
}

async function copyShareClipboard() {
  const link = await ensureShareLink();
  if (!link) {
    return;
  }
  const text = buildShareClipboardText({
    title: props.conversationTitle,
    url: link.url,
    expiresAt: link.expiresAt,
    turns: turns.value,
    selectedIndexes: selectedTurnIndexes.value,
    locale: locale.value,
    t,
  });
  const ok = await copyTextToUserClipboard(text);
  if (ok) {
    ElMessage.success(t("chat.shareClipboardCopied"));
  } else {
    ElMessage.warning(t("chat.copyFail"));
  }
}

async function systemShare() {
  if (!previewBlob.value) {
    return;
  }
  try {
    const file = new File([previewBlob.value], "chat-share.png", { type: "image/png" });
    if (navigator.canShare?.({ files: [file] })) {
      await navigator.share!({
        title: props.conversationTitle,
        files: [file],
      });
      return;
    }
    const link = await ensureShareLink();
    if (link) {
      const text = buildShareClipboardText({
        title: props.conversationTitle,
        url: link.url,
        expiresAt: link.expiresAt,
        turns: turns.value,
        selectedIndexes: selectedTurnIndexes.value,
        locale: locale.value,
        t,
      });
      await navigator.share!({ title: props.conversationTitle, text });
    }
  } catch {
    /* 用户取消 */
  }
}
</script>

<style scoped>
.share-section {
  margin-bottom: 18px;
}

.share-section--preview {
  margin-bottom: 0;
}

.share-section__head {
  margin-bottom: 10px;
}

.share-section__title {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  letter-spacing: 0.01em;
}

.share-section__hint {
  margin: 0;
  font-size: 12px;
  color: #a1a1aa;
  line-height: 1.45;
}

.share-turn-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.share-turn-list {
  list-style: none;
  margin: 0;
  padding: 4px 10px 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.share-turn-list__item {
  margin: 0;
  padding: 0;
}

.share-turn-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #ececec;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.share-turn-item:hover:not(:disabled) {
  border-color: #d8d8dc;
  background: #fcfcfc;
}

.share-turn-item--active {
  border-color: #d0d0d4;
  background: #fafafa;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.share-turn-item--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.share-turn-item__check {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 6px;
  border: 1.5px solid #d4d4d8;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #fff;
  transition:
    border-color 0.16s ease,
    background 0.16s ease;
}

.share-turn-item--active .share-turn-item__check {
  border-color: #202020;
  background: #202020;
}

.share-turn-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.share-turn-item__label {
  font-size: 12px;
  font-weight: 600;
  color: #52525b;
  line-height: 1.3;
}

.share-turn-item--active .share-turn-item__label {
  color: #18181b;
}

.share-turn-item__snippet {
  font-size: 13px;
  line-height: 1.5;
  color: #3f3f46;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.share-turn-item__pending {
  font-size: 12px;
  color: #a1a1aa;
}

.share-preview-loading {
  padding: 28px 16px;
  text-align: center;
  font-size: 13px;
  color: #71717a;
  border-radius: 14px;
  background: linear-gradient(180deg, #f5f5f7 0%, #eeeff2 100%);
}

.share-preview-scroll {
  border-radius: 14px;
  background: linear-gradient(180deg, #f0f1f4 0%, #e8eaef 100%);
  overflow: hidden;
}

.share-preview-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.share-preview-stage {
  padding: 20px 24px 24px;
  display: flex;
  justify-content: center;
}

.share-preview-img {
  display: block;
  width: 100%;
  max-width: 280px;
  height: auto;
  border-radius: 10px;
  box-shadow:
    0 2px 8px rgba(15, 23, 42, 0.08),
    0 16px 40px rgba(15, 23, 42, 0.14);
}

.share-capture-host {
  position: fixed;
  left: -9999px;
  top: 0;
  pointer-events: none;
  z-index: -1;
}

.share-dlg :deep(.el-dialog__body) {
  padding-top: 8px;
}
</style>
