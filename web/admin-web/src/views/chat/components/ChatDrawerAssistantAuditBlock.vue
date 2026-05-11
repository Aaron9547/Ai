<template>
  <div class="adb-root">
    <template v-if="message.priorVersions?.length">
      <div class="msg-assistant-current">
        <div class="msg-version-strip msg-version-strip--current">
          <span class="msg-version-label">当前版本</span>
          <span v-if="message.modelAlias" class="msg-version-meta">模型 {{ message.modelAlias }}</span>
        </div>
        <div v-if="message.reasoning" class="msg-reasoning msg-reasoning--in-current">
          <span class="msg-reasoning-hdr">思考过程</span>
          <pre>{{ message.reasoning }}</pre>
        </div>
        <div class="msg-md bubble-md bubble-md--current" v-html="assistantHtml(message.content)" />
        <div v-if="message.totalTokens != null && message.totalTokens > 0" class="msg-usage msg-usage--current">
          本版约 {{ message.totalTokens }} tokens（提示 {{ message.promptTokens ?? "—" }} / 生成
          {{ message.completionTokens ?? "—" }}）
        </div>
        <div v-if="message.ragCitations?.length" class="msg-rag-wrap">
          <div class="msg-rag-hdr">知识引用</div>
          <div class="msg-rag-tags">
            <el-tag
              v-for="(c, ci) in message.ragCitations"
              :key="ci"
              type="info"
              effect="plain"
              class="msg-rag-tag"
              @click.stop="emit('open-rag-citation', c)"
            >
              {{ ragCitationLabel(c) }}
            </el-tag>
          </div>
        </div>
      </div>
      <div class="msg-assistant-archive" aria-label="历史稿归档">
        <el-collapse class="msg-prior-collapse msg-prior-collapse--archive">
          <el-collapse-item name="prior">
            <template #title>
              <div class="msg-archive-collapse-head">
                <div class="msg-archive-title-row">
                  <span class="msg-archive-title">历史稿归档</span>
                  <span class="msg-archive-count">共 {{ message.priorVersions.length }} 版</span>
                </div>
                <span class="msg-archive-sub">重新生成前内容，仅供审计；点击展开查看各版。用户端当前为上方「当前版本」</span>
              </div>
            </template>
            <div v-for="(pv, idx) in message.priorVersions" :key="idx" class="msg-prior-block">
              <div class="msg-prior-hdr">
                <span class="msg-prior-badge">第 {{ idx + 1 }} 版（已替换）</span>
                <span v-if="pv.modelAlias" class="msg-prior-model">模型 {{ pv.modelAlias }}</span>
              </div>
              <div v-if="pv.reasoning" class="msg-reasoning msg-reasoning--archive">
                <span class="msg-reasoning-hdr">思考过程</span>
                <pre>{{ pv.reasoning }}</pre>
              </div>
              <div class="msg-md bubble-md bubble-md--archive" v-html="assistantHtml(pv.content ?? '')" />
              <div v-if="pv.totalTokens != null && pv.totalTokens > 0" class="msg-usage msg-usage--archive">
                该版约 {{ pv.totalTokens }} tokens（提示 {{ pv.promptTokens ?? "—" }} / 生成 {{ pv.completionTokens ?? "—" }}）
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </template>
    <template v-else>
      <div v-if="message.modelAlias" class="msg-model">模型 {{ message.modelAlias }}</div>
      <div v-if="message.reasoning" class="msg-reasoning">
        <span class="msg-reasoning-hdr">思考过程</span>
        <pre>{{ message.reasoning }}</pre>
      </div>
      <div class="msg-md bubble-md" v-html="assistantHtml(message.content)" />
      <div v-if="message.totalTokens != null && message.totalTokens > 0" class="msg-usage">
        本条约 {{ message.totalTokens }} tokens（提示 {{ message.promptTokens ?? "—" }} / 生成 {{ message.completionTokens ?? "—" }}）
      </div>
      <div v-if="message.ragCitations?.length" class="msg-rag-wrap">
        <div class="msg-rag-hdr">知识引用</div>
        <div class="msg-rag-tags">
          <el-tag
            v-for="(c, ci) in message.ragCitations"
            :key="ci"
            type="info"
            effect="plain"
            class="msg-rag-tag"
            @click.stop="emit('open-rag-citation', c)"
          >
            {{ ragCitationLabel(c) }}
          </el-tag>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessageAdminRow, RagCitationAdmin } from "../../../api/chatAdmin";
import { renderMarkdownToSafeHtml } from "../../../utils/renderMarkdown";

defineProps<{ message: ChatMessageAdminRow }>();
const emit = defineEmits<{ (e: "open-rag-citation", c: RagCitationAdmin): void }>();

function assistantHtml(text: string): string {
  return renderMarkdownToSafeHtml(text ?? "");
}

function ragCitationLabel(c: RagCitationAdmin): string {
  const t = (c.documentTitle || "文档").trim();
  const short = t.length > 18 ? `${t.slice(0, 18)}…` : t;
  return `${short} · 第 ${c.chunkSeq + 1} 片`;
}
</script>

<style scoped>
.adb-root {
  min-width: 0;
}

.msg-model {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.msg-md {
  font-size: 14px;
  line-height: 1.6;
  color: #0f172a;
}

.bubble-md :deep(p) {
  margin: 0 0 0.5em;
}

.bubble-md :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-md :deep(pre) {
  overflow-x: auto;
  padding: 10px;
  border-radius: 8px;
  background: #f1f5f9;
  font-size: 13px;
}

.msg-usage {
  margin-top: 8px;
  font-size: 12px;
  color: #0369a1;
}

.msg-rag-wrap {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}

.msg-rag-hdr {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
}

.msg-rag-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.msg-rag-tag {
  cursor: pointer;
}

.msg-reasoning {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 3px solid #94a3b8;
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

.msg-assistant-current {
  border-radius: 10px;
  border: 1px solid #bfdbfe;
  border-left: 5px solid #2563eb;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 48%);
  padding: 12px 14px 14px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}

.msg-version-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 14px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
}

.msg-version-strip--current {
  border-bottom-color: #bfdbfe;
}

.msg-version-label {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #1e40af;
  background: #dbeafe;
  border: 1px solid #93c5fd;
}

.msg-version-meta {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

.msg-reasoning--in-current {
  border-left-color: #3b82f6;
  background: linear-gradient(135deg, #eff6ff 0%, #f8fafc 100%);
}

.bubble-md--current {
  font-size: 14px;
}

.msg-usage--current {
  color: #1d4ed8;
  font-weight: 500;
}

.msg-assistant-archive {
  margin-top: 16px;
  padding: 8px;
  border-radius: 10px;
  border: 1px dashed #94a3b8;
  background: #f1f5f9;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 70%);
}

.msg-archive-collapse-head {
  flex: 1;
  min-width: 0;
  padding: 4px 0;
  text-align: left;
}

.msg-archive-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-archive-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.04em;
}

.msg-archive-count {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.msg-archive-sub {
  display: block;
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
}

.msg-prior-collapse {
  margin: 0;
  border: none;
  background: transparent;
}

.msg-prior-collapse--archive :deep(.el-collapse-item__header) {
  align-items: flex-start;
  height: auto;
  min-height: 48px;
  line-height: 1.35;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: #e2e8f0;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
}

.msg-prior-collapse--archive :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.msg-prior-collapse--archive :deep(.el-collapse-item__content) {
  padding: 12px 4px 4px;
}

.msg-prior-block {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #cbd5e1;
  box-shadow: 0 1px 1px rgb(15 23 42 / 4%);
}

.msg-prior-block:last-child {
  margin-bottom: 0;
}

.msg-prior-hdr {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #cbd5e1;
}

.msg-prior-badge {
  font-size: 11px;
  font-weight: 700;
  color: #9a3412;
  background: #ffedd5;
  border: 1px solid #fdba74;
  padding: 2px 8px;
  border-radius: 6px;
}

.msg-prior-model {
  font-size: 11px;
  color: #64748b;
}

.msg-reasoning--archive {
  margin-bottom: 8px;
  background: #f8fafc;
  border-left-color: #cbd5e1;
}

.bubble-md--archive {
  font-size: 13px;
  color: #475569;
  line-height: 1.55;
}

.bubble-md--archive :deep(pre) {
  background: #f1f5f9;
  font-size: 12px;
}

.msg-usage--archive {
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
}
</style>
