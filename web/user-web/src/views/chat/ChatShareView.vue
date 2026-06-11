<template>
  <div class="share-page">
    <div class="share-page-sheet">
      <header class="share-page-head">
        <div class="share-page-brand">
          <BrandMark :logo-url="logoUrl" :label="displayBrandTitle" size="share" />
          <span class="share-page-tag">{{ t("chat.shareCaptureTag") }}</span>
        </div>
        <h1 class="share-page-title">{{ title || t("chat.sharePageTitle") }}</h1>
        <p v-if="sharedAt" class="share-page-meta">{{ t("chat.sharePageMeta", { time: sharedAt }) }}</p>
      </header>

      <main v-if="loading" class="share-page-status">{{ t("chat.loading") }}</main>
      <main v-else-if="error" class="share-page-status share-page-status--error">{{ error }}</main>
      <main v-else class="share-page-body">
        <article
          v-for="(block, bi) in messageBlocks"
          :key="bi"
          class="share-page-block"
        >
          <div v-if="messageBlocks.length > 1" class="share-page-block__label">
            {{ t("chat.shareTurnLabel", { n: bi + 1 }) }}
          </div>
          <div
            v-for="(m, mi) in block"
            :key="mi"
            :class="['share-page-msg', m.role === 'user' ? 'share-page-msg--user' : 'share-page-msg--assistant']"
          >
            <div class="share-page-avatar" aria-hidden="true">
              <el-icon :size="15">
                <User v-if="m.role === 'user'" />
                <ChatLineRound v-else />
              </el-icon>
            </div>
            <div
              :class="[
                'share-page-bubble',
                m.role === 'user' ? 'share-page-bubble--user' : 'share-page-bubble--assistant bubble-md',
              ]"
            >
              <ShareUserMessageBody
                v-if="m.role === 'user'"
                :text="m.content"
                :attachments="userAttachmentsForMessage(m)"
              />
              <MarkdownRichContent
                v-else
                class="bubble-md"
                :source="assistantMarkdownSource(m)"
              />
            </div>
          </div>
        </article>
      </main>

      <footer class="share-page-foot">{{ t("chat.shareSnippetFooter") }}</footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChatLineRound, User } from "@element-plus/icons-vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import * as chatApi from "../../api/chat";
import BrandMark from "../../components/BrandMark.vue";
import MarkdownRichContent from "../../components/chat/MarkdownRichContent.vue";
import ShareUserMessageBody from "../../components/chat/ShareUserMessageBody.vue";
import { useTenantBranding } from "../../composables/useTenantBranding";
import {
  prefetchShareImageAttachments,
  revokeShareAttachmentBlobUrls,
  type ShareCaptureUserAttachment,
} from "../../utils/chatShareAttachments";

const { t } = useI18n();
const { logoUrl, displayBrandTitle, loadTenantBranding } = useTenantBranding();
const route = useRoute();

const title = ref("");
const sharedAt = ref("");
const conversationId = ref("");
const messages = ref<chatApi.ChatHistoryMessage[]>([]);
const userAttachmentPreviews = ref<Map<number, ShareCaptureUserAttachment[]>>(new Map());
const prefetchedBlobUrls = ref<string[]>([]);
const loading = ref(true);
const error = ref("");

/** 按 user → assistant 配对为轮次块 */
const messageBlocks = computed(() => {
  const blocks: chatApi.ChatHistoryMessage[][] = [];
  let current: chatApi.ChatHistoryMessage[] = [];
  for (const m of messages.value) {
    if (m.role === "user") {
      if (current.length) {
        blocks.push(current);
      }
      current = [m];
    } else if (m.role === "assistant" && current.length) {
      current.push(m);
      blocks.push(current);
      current = [];
    }
  }
  if (current.length) {
    blocks.push(current);
  }
  return blocks;
});

function userAttachmentsForMessage(m: chatApi.ChatHistoryMessage): ShareCaptureUserAttachment[] {
  return userAttachmentPreviews.value.get(m.id) ?? m.attachments?.map((a) => ({ ...a })) ?? [];
}

async function loadUserAttachmentPreviews() {
  revokeShareAttachmentBlobUrls(prefetchedBlobUrls.value);
  prefetchedBlobUrls.value = [];
  userAttachmentPreviews.value = new Map();
  if (!conversationId.value) {
    return;
  }
  for (const m of messages.value) {
    if (m.role !== "user" || !m.attachments?.length) {
      continue;
    }
    const { items, blobUrls } = await prefetchShareImageAttachments(conversationId.value, m.attachments);
    prefetchedBlobUrls.value.push(...blobUrls);
    userAttachmentPreviews.value.set(m.id, items);
  }
}

onBeforeUnmount(() => {
  revokeShareAttachmentBlobUrls(prefetchedBlobUrls.value);
});

function assistantMarkdownSource(m: chatApi.ChatHistoryMessage): string {
  if (m.workflowSegments?.length) {
    return m.workflowSegments
      .filter((s) => s.status !== "loading")
      .map((s) => s.text || "")
      .join("\n\n");
  }
  return m.content ?? "";
}

onMounted(async () => {
  void loadTenantBranding();
  const code = String(route.params.shareCode ?? "");
  if (!code) {
    error.value = t("chat.shareNotFound");
    loading.value = false;
    return;
  }
  try {
    const data = await chatApi.getPublicShare(code);
    title.value = data.title;
    messages.value = data.messages ?? [];
    conversationId.value = data.conversationId ?? "";
    sharedAt.value = data.sharedAt ? new Date(data.sharedAt).toLocaleString() : "";
    await loadUserAttachmentPreviews();
  } catch {
    error.value = t("chat.shareNotFound");
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.share-page {
  min-height: 100dvh;
  padding: 24px 16px 40px;
  box-sizing: border-box;
  background: linear-gradient(165deg, #f3f4f6 0%, #e8eaef 100%);
  color: #18181b;
}

.share-page-sheet {
  max-width: 520px;
  margin: 0 auto;
  background: #fff;
  border-radius: 16px;
  padding: 22px 20px 18px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 12px 40px rgba(15, 23, 42, 0.08);
}

.share-page-head {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f2;
}

.share-page-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.share-page-tag {
  font-size: 11px;
  font-weight: 500;
  color: #71717a;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.share-page-title {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
}

.share-page-meta {
  margin: 0;
  font-size: 12px;
  color: #a1a1aa;
}

.share-page-body {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.share-page-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.share-page-block__label {
  margin-bottom: 2px;
  font-size: 11px;
  font-weight: 600;
  color: #a1a1aa;
  letter-spacing: 0.04em;
}

.share-page-msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
}

.share-page-msg--user {
  flex-direction: row-reverse;
}

.share-page-avatar {
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

.share-page-msg--user .share-page-avatar {
  background: #eff6ff;
  color: #2563eb;
}

.share-page-bubble {
  min-width: 0;
  max-width: calc(100% - 42px);
  box-sizing: border-box;
  overflow-x: auto;
  overflow-wrap: anywhere;
}

.share-page-msg--assistant .share-page-bubble {
  flex: 1;
}

.share-page-msg--user .share-page-bubble {
  flex: 0 1 auto;
  width: fit-content;
}

.share-page-bubble--user {
  padding: 11px 14px;
  border-radius: 16px;
  border-bottom-right-radius: 5px;
  background: #f4f4f5;
  font-size: 14px;
  line-height: 1.6;
}

.share-page-user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.share-page-bubble--assistant {
  padding: 11px 14px;
  border-radius: 16px;
  border-bottom-left-radius: 5px;
  background: #fafafa;
  border: 1px solid #f0f0f2;
  font-size: 14px;
  line-height: 1.6;
}

.share-page-bubble--assistant :deep(.markdown-rich) {
  min-width: 0;
  max-width: 100%;
}

.share-page-foot {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f2;
  font-size: 11px;
  color: #a1a1aa;
  text-align: center;
}

.share-page-status {
  text-align: center;
  padding: 40px 16px;
  color: #71717a;
  font-size: 14px;
}

.share-page-status--error {
  color: #b91c1c;
}
</style>
