<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">对话日志</span>
            <p class="sub">
              按权限展示会话列表（含访客设备码）；创始人可不选租户查看全部。列表中的「会话总 Token」为助手消息（含历史稿）用量的<strong>近似合计</strong>，供抽检参考。点开详情后，消息按<strong>一问一答</strong>分组展示，便于对照抽检。
            </p>
          </div>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              placeholder="全部租户"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="t in tenantOptions"
                :key="t.id"
                :label="`${t.name} (${t.code})`"
                :value="t.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        border
        max-height="520"
        class="data-table"
        empty-text="暂无会话"
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column label="租户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTenantNameCode(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="会话总 Token（约）" width="140" align="right">
          <template #default="{ row }">
            {{ formatConversationTokensApprox(row.totalTokensInConversation) }}
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatChatConversationUser(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" label="设备码" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.deviceId || "—" }}
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新" width="168">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openMessages(row)">详情</el-button>
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

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="min(760px, 96vw)" destroy-on-close>
      <el-scrollbar max-height="calc(100vh - 120px)">
        <p v-if="drawerConv" class="drawer-debug-id">会话内部编号（排障）：{{ drawerConv.id }}</p>
        <div
          v-if="drawerConv && drawerConv.totalTokensInConversation != null"
          class="drawer-token-banner"
        >
          本会话助手侧 Token 合计（约）：<strong>{{ formatConversationTokensApprox(drawerConv.totalTokensInConversation) }}</strong>
        </div>
        <div v-if="msgLoading" class="msg-loading">加载中…</div>
        <div v-else class="msg-list">
          <template v-for="seg in drawerMessageSegments" :key="seg.key">
            <div v-if="seg.kind === 'pair'" class="qa-pair-card">
              <header class="qa-pair-head">
                <span class="qa-pair-badge">第 {{ seg.pairIndex }} 轮</span>
                <span class="qa-pair-hint">用户提问 · 助手回答</span>
              </header>
              <section class="qa-section qa-section--q">
                <div class="qa-section-head">
                  <span class="qa-role">用户</span>
                  <span v-if="seg.user.createdAt" class="msg-time">{{ formatTime(seg.user.createdAt) }}</span>
                </div>
                <div v-if="seg.user.reasoning" class="msg-reasoning msg-reasoning--non-assistant">
                  <span class="msg-reasoning-hdr">思考过程</span>
                  <pre>{{ seg.user.reasoning }}</pre>
                </div>
                <pre class="msg-plain qa-user-pre">{{ seg.user.content }}</pre>
              </section>
              <section class="qa-section qa-section--a">
                <div class="qa-section-head">
                  <span class="qa-role">助手</span>
                  <span v-if="seg.assistant.createdAt" class="msg-time">{{ formatTime(seg.assistant.createdAt) }}</span>
                </div>
                <ChatDrawerAssistantAuditBlock
                  :message="seg.assistant"
                  @open-rag-citation="openRagCitation"
                />
              </section>
            </div>
            <div v-else :class="['msg-block', seg.message.role]">
              <div class="msg-head">
                <span class="msg-role">{{ seg.message.role }}</span>
                <span v-if="seg.message.createdAt" class="msg-time">{{ formatTime(seg.message.createdAt) }}</span>
              </div>
              <p v-if="seg.message.role === 'assistant'" class="qa-orphan-hint">
                本条助手消息未与上一条用户消息成对（例如会话以助手开头或数据异常），单独展示。
              </p>
              <p v-else-if="seg.message.role === 'user'" class="qa-orphan-hint">
                本条用户消息后未紧跟助手回复（例如仍生成中或仅落库了用户侧），单独展示。
              </p>
              <template v-if="seg.message.role === 'assistant'">
                <ChatDrawerAssistantAuditBlock :message="seg.message" @open-rag-citation="openRagCitation" />
              </template>
              <template v-else>
                <div v-if="seg.message.reasoning" class="msg-reasoning msg-reasoning--non-assistant">
                  <span class="msg-reasoning-hdr">思考过程</span>
                  <pre>{{ seg.message.reasoning }}</pre>
                </div>
                <pre class="msg-plain">{{ seg.message.content }}</pre>
              </template>
            </div>
          </template>
        </div>
      </el-scrollbar>
    </el-drawer>

    <el-dialog
      v-model="ragCitationDlgOpen"
      :title="ragCitationDlgTitle"
      width="min(720px, 96vw)"
      destroy-on-close
      class="rag-citation-dlg"
    >
      <div v-loading="ragCitationDlgLoading" class="rag-citation-body">
        <pre class="rag-citation-pre">{{ ragCitationDlgBody }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import * as chatAdmin from "../../api/chatAdmin";
import type { ChatConversationRow, ChatMessageAdminRow, RagCitationAdmin } from "../../api/chatAdmin";
import * as ragApi from "../../api/ragAdmin";
import ChatDrawerAssistantAuditBlock from "./components/ChatDrawerAssistantAuditBlock.vue";
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

const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const loading = ref(false);
const rows = ref<ChatConversationRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);

const drawerOpen = ref(false);
const drawerConv = ref<ChatConversationRow | null>(null);
const messages = ref<ChatMessageAdminRow[]>([]);
const msgLoading = ref(false);

const ragCitationDlgOpen = ref(false);
const ragCitationDlgTitle = ref("");
const ragCitationDlgBody = ref("");
const ragCitationDlgLoading = ref(false);

const drawerTitle = computed(() => {
  if (!drawerConv.value) return "消息详情";
  const title = (drawerConv.value.title ?? "").trim() || "未命名会话";
  const tenant = formatTenantNameCode(drawerConv.value);
  return tenant !== "—" ? `${tenant} · ${title}` : title;
});

/** 将连续「用户 + 助手」合并为一轮卡片，其余单条单独展示。 */
const drawerMessageSegments = computed<DrawerMessageSegment[]>(() => {
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

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

async function openRagCitation(c: RagCitationAdmin) {
  ragCitationDlgTitle.value = `${(c.documentTitle || "文档").trim()} · 第 ${c.chunkSeq + 1} 片`;
  ragCitationDlgOpen.value = true;
  ragCitationDlgLoading.value = true;
  ragCitationDlgBody.value = "";
  try {
    const chunks = await ragApi.fetchRagKbChunks(c.kbId, c.documentId);
    const row = chunks.find((x) => x.id === c.chunkId);
    ragCitationDlgBody.value = row?.content ?? "（未找到该分片，可能已删除或无权访问）";
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载失败";
    ragCitationDlgBody.value = msg;
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
  drawerConv.value = row;
  drawerOpen.value = true;
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
}

.panel {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
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
  color: #0f172a;
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.data-table {
  width: 100%;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.drawer-debug-id {
  margin: 0 8px 10px;
  font-size: 12px;
  color: #64748b;
}

.drawer-token-banner {
  margin: 0 8px 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #334155;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
}

.msg-loading {
  padding: 24px;
  color: #64748b;
}

.msg-list {
  padding: 0 8px 24px;
}

.qa-pair-card {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid #cbd5e1;
  background: #fff;
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
  overflow: hidden;
}

.qa-pair-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: linear-gradient(90deg, #f0fdf4 0%, #ecfdf5 40%, #f8fafc 100%);
  border-bottom: 1px solid #e2e8f0;
}

.qa-pair-badge {
  font-size: 12px;
  font-weight: 700;
  color: #166534;
  background: #dcfce7;
  border: 1px solid #86efac;
  padding: 3px 10px;
  border-radius: 999px;
}

.qa-pair-hint {
  font-size: 12px;
  color: #64748b;
}

.qa-section {
  padding: 12px 14px 14px;
}

.qa-section--q {
  background: #eff6ff;
  border-bottom: 1px dashed #bfdbfe;
}

.qa-section--a {
  background: #f8fafc;
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
  color: #475569;
}

.qa-user-pre {
  margin-top: 2px;
}

.qa-orphan-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 8px 10px;
}

.msg-block {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.msg-block.user {
  background: #eff6ff;
  border-color: #bfdbfe;
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
  color: #64748b;
}

.msg-time {
  font-size: 11px;
  color: #94a3b8;
}

.msg-plain {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-sans-serif, system-ui, sans-serif;
  font-size: 13px;
  color: #0f172a;
}

.rag-citation-body {
  min-height: 120px;
}

.rag-citation-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.55;
  color: #0f172a;
  max-height: min(60vh, 480px);
  overflow: auto;
}

/* 思考在正文之前，与「先推理后作答」的阅读顺序一致 */
.msg-reasoning {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 3px solid #94a3b8;
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
  color: #64748b;
}

.msg-reasoning pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
}
</style>
