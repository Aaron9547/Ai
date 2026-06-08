<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.chatConv.title") }}</span>
            <p class="sub" v-html="t('views.chatConv.sub')" />
          </div>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              :placeholder="t('views.chatConv.placeholderAllTenants')"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="tenantOpt in tenantOptions"
                :key="tenantOpt.id"
                :label="`${tenantOpt.name} (${tenantOpt.code})`"
                :value="tenantOpt.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">{{ t("views.chatConv.refresh") }}</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        class="data-table"
        :empty-text="t('views.chatConv.empty')"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column :label="t('views.chatConv.colTenant')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('views.chatConv.colTitle')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('views.chatConv.colTokens')" width="140" align="right">
          <template #default="{ row }">
            {{ formatConversationTokensApprox(row.totalTokensInConversation) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.chatConv.colUser')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatChatConversationUser(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" :label="t('views.chatConv.colDevice')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || emDash }}
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('views.chatConv.colUpdated')" width="168">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('views.chatConv.colActions')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openMessages(row)">{{
              t("views.chatConv.detail")
            }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailOpen"
      :title="detailTitle"
      class="chat-conv-detail-dlg"
      width="92%"
      top="3vh"
      destroy-on-close
      append-to-body
      align-center
    >
      <div class="detail-dlg-body">
        <p v-if="detailConv" class="detail-meta-id">{{ t("views.chatConv.drawerDebug", { id: detailConv.id }) }}</p>
        <div
          v-if="detailConv && detailConv.totalTokensInConversation != null"
          class="detail-token-banner"
        >
          {{ t("views.chatConv.drawerTokenBanner")
          }}<strong>{{ formatConversationTokensApprox(detailConv.totalTokensInConversation) }}</strong>
        </div>
        <div v-if="msgLoading" class="msg-loading">{{ t("views.chatConv.loading") }}</div>
        <div v-else class="msg-list">
          <template v-for="seg in detailMessageSegments" :key="seg.key">
            <div v-if="seg.kind === 'pair'" class="qa-pair-card">
              <header class="qa-pair-head">
                <span class="qa-pair-badge">{{ t("views.chatConv.roundN", { n: seg.pairIndex }) }}</span>
                <span class="qa-pair-hint">{{ t("views.chatConv.pairHint") }}</span>
              </header>
              <section class="qa-section qa-section--q">
                <div class="qa-section-head">
                  <span class="qa-role">{{ t("views.chatConv.roleUser") }}</span>
                  <span v-if="seg.user.createdAt" class="msg-time">{{ formatTime(seg.user.createdAt) }}</span>
                </div>
                <div v-if="seg.user.reasoning" class="msg-reasoning msg-reasoning--non-assistant">
                  <span class="msg-reasoning-hdr">{{ t("views.chatConv.reasoning") }}</span>
                  <MarkdownRichContent class="msg-md-inline" :source="seg.user.reasoning" />
                </div>
                <MarkdownRichContent class="msg-md-inline qa-user-md" :source="seg.user.content ?? ''" />
                <div v-if="seg.user.attachments?.length" class="qa-user-attachments">
                  <span class="qa-user-attach-label">{{ t("views.chatConv.userAttachments") }}</span>
                  <span v-for="a in seg.user.attachments" :key="a.id" class="qa-user-attach-chip" :title="a.fileName">
                    {{ a.fileName }}
                  </span>
                </div>
              </section>
              <section class="qa-section qa-section--a">
                <div class="qa-section-head">
                  <span class="qa-role">{{ t("views.chatConv.roleAssistant") }}</span>
                  <span v-if="seg.assistant.createdAt" class="msg-time">{{ formatTime(seg.assistant.createdAt) }}</span>
                </div>
                <ChatDrawerAssistantAuditBlock
                  :message="seg.assistant"
                  :conversation-id="detailConv?.id"
                  :user-message-id="seg.user.id"
                  :user-query-text="seg.user.content"
                  @open-rag-citation="openRagCitation"
                />
              </section>
            </div>
            <div v-else :class="['msg-block', seg.message.role]">
              <div class="msg-head">
                <span class="msg-role">{{ roleUiLabel(seg.message.role) }}</span>
                <span v-if="seg.message.createdAt" class="msg-time">{{ formatTime(seg.message.createdAt) }}</span>
              </div>
              <p v-if="seg.message.role === 'assistant'" class="qa-orphan-hint">
                {{ t("views.chatConv.orphanAssistant") }}
              </p>
              <p v-else-if="seg.message.role === 'user'" class="qa-orphan-hint">
                {{ t("views.chatConv.orphanUser") }}
              </p>
              <template v-if="seg.message.role === 'assistant'">
                <ChatDrawerAssistantAuditBlock :message="seg.message" @open-rag-citation="openRagCitation" />
              </template>
              <template v-else>
                <div v-if="seg.message.reasoning" class="msg-reasoning msg-reasoning--non-assistant">
                  <span class="msg-reasoning-hdr">{{ t("views.chatConv.reasoning") }}</span>
                  <MarkdownRichContent class="msg-md-inline" :source="seg.message.reasoning" />
                </div>
                <MarkdownRichContent class="msg-md-inline" :source="seg.message.content ?? ''" />
                <div v-if="seg.message.attachments?.length" class="qa-user-attachments">
                  <span class="qa-user-attach-label">{{ t("views.chatConv.userAttachments") }}</span>
                  <span
                    v-for="a in seg.message.attachments"
                    :key="a.id"
                    class="qa-user-attach-chip"
                    :title="a.fileName"
                  >
                    {{ a.fileName }}
                  </span>
                </div>
              </template>
            </div>
          </template>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="ragCitationDlgOpen"
      :title="ragCitationDlgTitle"
      width="min(720px, 96vw)"
      destroy-on-close
      class="rag-citation-dlg"
    >
      <div v-loading="ragCitationDlgLoading" class="rag-citation-body">
        <el-alert
          v-if="ragCitationDlgSnapshot"
          type="warning"
          :closable="false"
          show-icon
          class="rag-citation-snapshot-alert"
          :title="t('views.chatConv.ragChunkDeletedSnapshotTitle')"
          :description="t('views.chatConv.ragChunkDeletedSnapshotDesc')"
        />
        <pre
          v-if="!ragCitationDlgRenderMd"
          class="rag-citation-pre"
          :class="{ 'rag-citation-pre--snapshot': ragCitationDlgSnapshot }"
        >{{ ragCitationDlgBody }}</pre>
        <MarkdownRichContent
          v-else
          class="rag-citation-md"
          :class="{ 'rag-citation-md--snapshot': ragCitationDlgSnapshot }"
          :source="ragCitationDlgBody"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as chatAdmin from "../../api/chatAdmin";
import type { ChatConversationRow, ChatMessageAdminRow, RagCitationAdmin } from "../../api/chatAdmin";
import * as ragApi from "../../api/ragAdmin";
import { apiRequestErrorMessage } from "../../utils/apiRequestErrorMessage";
import ChatDrawerAssistantAuditBlock from "./components/ChatDrawerAssistantAuditBlock.vue";
import MarkdownRichContent from "../../components/markdown/MarkdownRichContent.vue";
import axios from "axios";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";
import {
  formatChatConversationUser,
  formatConversationTokensApprox,
  formatTenantNameCode,
} from "../../utils/adminListDisplay";

type DrawerMessageSegment =
  | {
      kind: "pair";
      key: string;
      pairIndex: number;
      user: ChatMessageAdminRow;
      assistant: ChatMessageAdminRow;
    }
  | { kind: "single"; key: string; message: ChatMessageAdminRow };

const { t } = useI18n();
const emDash = "\u2014";

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const loading = ref(false);
const rows = ref<ChatConversationRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);

const detailOpen = ref(false);
const detailConv = ref<ChatConversationRow | null>(null);
const messages = ref<ChatMessageAdminRow[]>([]);
const msgLoading = ref(false);

const ragCitationDlgOpen = ref(false);
const ragCitationDlgTitle = ref("");
const ragCitationDlgBody = ref("");
const ragCitationDlgSnapshot = ref(false);
const ragCitationDlgRenderMd = ref(false);
const ragCitationDlgLoading = ref(false);

const detailTitle = computed(() => {
  if (!detailConv.value) return t("views.chatConv.drawerTitle");
  const title = (detailConv.value.title ?? "").trim() || t("views.chatConv.unnamedConv");
  const tenant = formatTenantNameCode(detailConv.value);
  return tenant !== emDash ? `${tenant} · ${title}` : title;
});

/** 将连续「用户 + 助手」合并为一轮卡片，其余单条单独展示。 */
const detailMessageSegments = computed<DrawerMessageSegment[]>(() => {
  const msgs = messages.value;
  const out: DrawerMessageSegment[] = [];
  let i = 0;
  let pairIndex = 0;
  while (i < msgs.length) {
    const cur = msgs[i];
    const next = msgs[i + 1];
    if (cur.role === "user" && next?.role === "assistant") {
      pairIndex += 1;
      out.push({
        kind: "pair",
        key: `pair-${cur.id}-${next.id}`,
        pairIndex,
        user: cur,
        assistant: next,
      });
      i += 2;
    } else {
      out.push({ kind: "single", key: `single-${cur.id}`, message: cur });
      i += 1;
    }
  }
  return out;
});

function roleUiLabel(role: string): string {
  if (role === "user") return t("views.chatConv.roleUser");
  if (role === "assistant") return t("views.chatConv.roleAssistant");
  return role;
}

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function ragCitationSnapshotPreview(c: RagCitationAdmin): string | null {
  const preview = (c.contentPreview || "").trim();
  return preview || null;
}

function showRagCitationSnapshot(preview: string) {
  ragCitationDlgSnapshot.value = true;
  ragCitationDlgRenderMd.value = true;
  ragCitationDlgBody.value = preview;
}

function setRagCitationPlainMessage(msg: string) {
  ragCitationDlgRenderMd.value = false;
  ragCitationDlgBody.value = msg;
}

async function openRagCitation(c: RagCitationAdmin) {
  ragCitationDlgTitle.value = t("views.chatDrawerAudit.citationLabel", {
    title: (c.documentTitle || t("views.chatDrawerAudit.docFallback")).trim(),
    n: c.chunkSeq + 1,
  });
  ragCitationDlgOpen.value = true;
  ragCitationDlgLoading.value = true;
  ragCitationDlgBody.value = "";
  ragCitationDlgSnapshot.value = false;
  ragCitationDlgRenderMd.value = false;
  const snapshotPreview = ragCitationSnapshotPreview(c);
  try {
    const { chunks } = await ragApi.fetchRagKbChunks(c.kbId, c.documentId);
    const row = chunks.find((x) => x.id === c.chunkId);
    if (row?.content) {
      ragCitationDlgRenderMd.value = true;
      ragCitationDlgBody.value = row.content;
    } else if (snapshotPreview) {
      showRagCitationSnapshot(snapshotPreview);
    } else {
      setRagCitationPlainMessage(t("views.chatConv.ragChunkNotFound"));
    }
  } catch (e: unknown) {
    const notFound =
      axios.isAxiosError(e) && (e.response?.status === 404 || e.response?.status === 410);
    if (notFound && snapshotPreview) {
      showRagCitationSnapshot(snapshotPreview);
    } else {
      setRagCitationPlainMessage(apiRequestErrorMessage(e, t("views.chatConv.loadFailed")));
    }
  } finally {
    ragCitationDlgLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  try {
    const data = await chatAdmin.fetchChatConversations(page.value, size.value, listFilterQuery());
    rows.value = data.records ?? [];
    total.value = data.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function onTenantFilterChange() {
  page.value = 1;
  void load();
}

function onSizeChange() {
  page.value = 1;
  void load();
}

async function openMessages(row: ChatConversationRow) {
  detailConv.value = row;
  detailOpen.value = true;
  msgLoading.value = true;
  messages.value = [];
  try {
    messages.value = await chatAdmin.fetchChatMessages(row.id);
  } finally {
    msgLoading.value = false;
  }
}

function onRowClick(row: ChatConversationRow) {
  void openMessages(row);
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.page {
  padding: 0 0 24px;
  /* 对话详情弹框：随浅色/深色主题切换的表面色 */
  --cc-user-bg: var(--el-color-primary-light-9);
  --cc-user-border: var(--el-color-primary-light-5);
  --cc-banner-bg: var(--el-color-primary-light-9);
  --cc-banner-border: var(--el-color-primary-light-5);
}

.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.hdr {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.hdr-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.tenant-filter {
  width: 220px;
}

.title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.data-table {
  width: 100%;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-dlg-body {
  max-height: min(78vh, calc(92vh - 140px));
  overflow-x: hidden;
  overflow-y: auto;
  padding: 0 2px 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color-darker) transparent;
}

.detail-meta-id {
  margin: 0 4px 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-token-banner {
  margin: 0 4px 14px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--cc-banner-bg);
  border: 1px solid var(--cc-banner-border);
  border-radius: 8px;
}

.detail-token-banner strong {
  color: var(--el-color-primary);
  font-weight: 700;
}

.msg-loading {
  padding: 24px;
  color: var(--el-text-color-secondary);
}

.msg-list {
  padding: 0 6px 16px;
  min-width: 0;
}

.qa-pair-card {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
  overflow: hidden;
}

.qa-pair-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: linear-gradient(90deg, var(--el-color-success-light-9) 0%, var(--el-color-success-light-8) 40%, var(--el-fill-color-light) 100%);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.qa-pair-badge {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-5);
  padding: 3px 10px;
  border-radius: 999px;
}

.qa-pair-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.qa-section {
  padding: 12px 14px 14px;
}

.qa-section--q {
  background: var(--cc-user-bg);
  border-bottom: 1px dashed var(--cc-user-border);
}

.qa-section--a {
  background: var(--el-fill-color-light);
}

.qa-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.qa-role {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--el-text-color-regular);
}

.msg-md-inline {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.qa-user-md {
  margin-top: 2px;
}

.qa-user-attachments {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin-top: 8px;
  font-size: 12px;
}

.qa-user-attach-label {
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.qa-user-attach-chip {
  display: inline-block;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qa-orphan-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 8px;
  padding: 8px 10px;
}

.msg-block {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.msg-block.user {
  background: var(--cc-user-bg);
  border-color: var(--cc-user-border);
}

.msg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.msg-role {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
}

.msg-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.msg-plain {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-sans-serif, system-ui, sans-serif;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.rag-citation-body {
  min-height: 120px;
}

.rag-citation-snapshot-alert {
  margin-bottom: 14px;
}

.rag-citation-snapshot-alert :deep(.el-alert__title) {
  font-size: 14px;
  font-weight: 700;
}

.rag-citation-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
  max-height: min(60vh, 480px);
  overflow: auto;
}

.rag-citation-pre--snapshot {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
}

.rag-citation-md {
  max-height: min(60vh, 480px);
  overflow: auto;
  white-space: normal;
}

.rag-citation-md--snapshot {
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

/* 思考在正文之前，与「先推理后作答」的阅读顺序一致 */
.msg-reasoning {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--el-fill-color-light) 0%, var(--el-fill-color) 100%);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-border-color);
}

.msg-reasoning--non-assistant {
  margin-bottom: 8px;
}

.msg-reasoning-hdr {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--el-text-color-secondary);
}

.msg-reasoning pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
}

/* 深色模式下 success/warning 徽章对比度 */
:global(html.dark) .qa-pair-badge {
  color: var(--el-color-success-light-3);
}

:global(html.dark) .qa-orphan-hint {
  color: var(--el-color-warning-light-3);
}
</style>

<style>
/* 对话详情大弹框：宽屏更大、小屏自适应 */
.chat-conv-detail-dlg.el-dialog {
  display: flex;
  flex-direction: column;
  width: min(1200px, 96vw) !important;
  max-width: 96vw;
  max-height: 92vh;
  margin: 3vh auto;
}

.chat-conv-detail-dlg .el-dialog__header {
  flex-shrink: 0;
  padding: 16px 20px 12px;
  margin-right: 0;
}

.chat-conv-detail-dlg .el-dialog__body {
  flex: 1;
  min-height: 0;
  padding: 0 20px 20px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .chat-conv-detail-dlg.el-dialog {
    width: 100% !important;
    max-width: 100vw;
    max-height: 100vh;
    margin: 0;
    border-radius: 0;
  }

  .chat-conv-detail-dlg .el-dialog__body {
    padding: 0 12px 16px;
  }

  .detail-dlg-body {
    max-height: calc(100vh - 120px);
  }
}
</style>
