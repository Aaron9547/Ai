<template>
  <div class="adb-root">
    <template v-if="message.priorVersions?.length">
      <div class="msg-assistant-current">
        <div class="msg-version-strip msg-version-strip--current">
          <span class="msg-version-label">当前版本</span>
          <span v-if="message.modelAlias" class="msg-version-meta">模型 {{ message.modelAlias }}</span>
        </div>
        <div v-if="(message.contentSummary ?? '').trim()" class="msg-content-summary msg-content-summary--current">
          <span class="msg-summary-hdr">{{ t("views.chatDrawerAudit.summaryHdr") }}</span>
          <span class="msg-summary-sub">{{ t("views.chatDrawerAudit.summarySub") }}</span>
          <pre class="msg-summary-pre">{{ message.contentSummary }}</pre>
        </div>
        <div v-if="message.reasoning" class="msg-reasoning msg-reasoning--in-current">
          <span class="msg-reasoning-hdr">{{ t("views.chatDrawerAudit.reasoning") }}</span>
          <MarkdownRichContent class="msg-reasoning-md" :source="message.reasoning" />
        </div>
        <MarkdownRichContent class="msg-md bubble-md bubble-md--current" :source="message.content ?? ''" />
        <div v-if="message.totalTokens != null && message.totalTokens > 0" class="msg-usage msg-usage--current">
          本版约 {{ message.totalTokens }} tokens（提示 {{ message.promptTokens ?? "—" }} / 生成
          {{ message.completionTokens ?? "—" }}）
        </div>
        <div v-if="message.ragCitations?.length" class="msg-rag-wrap">
          <div class="msg-rag-hdr">{{ t("views.chatDrawerAudit.ragHdr") }}</div>
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
        <div v-if="message.webSearchReferences?.length" class="msg-web-wrap">
          <div class="msg-web-hdr">{{ t("views.chatDrawerAudit.webHdr") }}</div>
          <div class="msg-web-chips">
            <template v-for="(w, wi) in message.webSearchReferences" :key="wi">
              <a
                v-if="(w.url ?? '').trim()"
                class="msg-web-chip"
                :href="w.url"
                target="_blank"
                rel="noopener noreferrer"
                :title="(w.summary || '').trim() || undefined"
                @click.stop
              >
                {{ webRefLabel(w) }}
              </a>
              <span
                v-else
                class="msg-web-chip msg-web-chip--nolink"
                :title="(w.summary || '').trim() || undefined"
              >
                {{ webRefLabel(w) }}
              </span>
            </template>
          </div>
        </div>
        <div v-if="canAssessQuality" class="msg-qa-wrap">
          <el-button
            size="small"
            type="primary"
            plain
            :loading="qaLoading"
            @click.stop="runQualityAssessment"
          >
            {{ qaLoading ? t("views.ragQuality.assessRunning") : t("views.ragQuality.assessBtn") }}
          </el-button>
          <div v-if="qaResult && showQaScores" class="msg-qa-scores">
            <span class="msg-qa-score">
              {{ t("views.ragQuality.scoreRecall") }} {{ formatPct(qaResult.recallHitRate) }}
            </span>
            <span class="msg-qa-score">
              {{ t("views.ragQuality.scoreCitation") }} {{ formatPct(qaResult.citationAccuracy) }}
            </span>
            <span class="msg-qa-score">
              {{ t("views.ragQuality.scoreFaithfulness") }} {{ formatPct(qaResult.faithfulnessScore) }}
            </span>
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
                <span class="msg-reasoning-hdr">{{ t("views.chatDrawerAudit.reasoning") }}</span>
                <MarkdownRichContent class="msg-reasoning-md" :source="pv.reasoning" />
              </div>
              <MarkdownRichContent class="msg-md bubble-md bubble-md--archive" :source="pv.content ?? ''" />
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
      <div v-if="(message.contentSummary ?? '').trim()" class="msg-content-summary">
        <span class="msg-summary-hdr">{{ t("views.chatDrawerAudit.summaryHdr") }}</span>
        <span class="msg-summary-sub">{{ t("views.chatDrawerAudit.summarySub") }}</span>
        <pre class="msg-summary-pre">{{ message.contentSummary }}</pre>
      </div>
      <div v-if="message.reasoning" class="msg-reasoning">
        <span class="msg-reasoning-hdr">{{ t("views.chatDrawerAudit.reasoning") }}</span>
        <MarkdownRichContent class="msg-reasoning-md" :source="message.reasoning" />
      </div>
      <MarkdownRichContent class="msg-md bubble-md" :source="message.content ?? ''" />
      <div v-if="message.totalTokens != null && message.totalTokens > 0" class="msg-usage">
        本条约 {{ message.totalTokens }} tokens（提示 {{ message.promptTokens ?? "—" }} / 生成 {{ message.completionTokens ?? "—" }}）
      </div>
      <div v-if="message.ragCitations?.length" class="msg-rag-wrap">
        <div class="msg-rag-hdr">{{ t("views.chatDrawerAudit.ragHdr") }}</div>
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
      <div v-if="message.webSearchReferences?.length" class="msg-web-wrap">
        <div class="msg-web-hdr">{{ t("views.chatDrawerAudit.webHdr") }}</div>
        <div class="msg-web-chips">
          <template v-for="(w, wi) in message.webSearchReferences" :key="wi">
            <a
              v-if="(w.url ?? '').trim()"
              class="msg-web-chip"
              :href="w.url"
              target="_blank"
              rel="noopener noreferrer"
              :title="(w.summary || '').trim() || undefined"
              @click.stop
            >
              {{ webRefLabel(w) }}
            </a>
            <span
              v-else
              class="msg-web-chip msg-web-chip--nolink"
              :title="(w.summary || '').trim() || undefined"
            >
              {{ webRefLabel(w) }}
            </span>
          </template>
        </div>
      </div>
      <div v-if="canAssessQuality" class="msg-qa-wrap">
        <el-button
          size="small"
          type="primary"
          plain
          :loading="qaLoading"
          @click.stop="runQualityAssessment"
        >
          {{ qaLoading ? t("views.ragQuality.assessRunning") : t("views.ragQuality.assessBtn") }}
        </el-button>
        <div v-if="qaResult && showQaScores" class="msg-qa-scores">
          <span class="msg-qa-score">
            {{ t("views.ragQuality.scoreRecall") }} {{ formatPct(qaResult.recallHitRate) }}
          </span>
          <span class="msg-qa-score">
            {{ t("views.ragQuality.scoreCitation") }} {{ formatPct(qaResult.citationAccuracy) }}
          </span>
          <span class="msg-qa-score">
            {{ t("views.ragQuality.scoreFaithfulness") }} {{ formatPct(qaResult.faithfulnessScore) }}
          </span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import * as obsApi from "../../../api/observabilityAdmin";
import type { RagQualityAssessmentView } from "../../../api/observabilityAdmin";
import type { ChatMessageAdminRow, RagCitationAdmin, WebSearchRefAdmin } from "../../../api/chatAdmin";
import MarkdownRichContent from "../../../components/markdown/MarkdownRichContent.vue";

const props = defineProps<{
  message: ChatMessageAdminRow;
  conversationId?: number | null;
  userMessageId?: number | null;
  userQueryText?: string | null;
}>();
const emit = defineEmits<{ (e: "open-rag-citation", c: RagCitationAdmin): void }>();
const { t } = useI18n();

const qaLoading = ref(false);
const qaResult = ref<RagQualityAssessmentView | null>(null);
let qaPollTimer: ReturnType<typeof setInterval> | null = null;

const canAssessQuality = computed(
  () =>
    props.conversationId != null &&
    props.userMessageId != null &&
    (props.message.ragCitations?.length ?? 0) > 0,
);

const showQaScores = computed(
  () => qaResult.value?.status === "SUCCEEDED" || qaResult.value?.status === "FAILED",
);

function formatPct(v: number | null | undefined): string {
  if (v == null) return "—";
  return `${(Number(v) * 100).toFixed(1)}%`;
}

function stopQaPoll() {
  if (qaPollTimer != null) {
    clearInterval(qaPollTimer);
    qaPollTimer = null;
  }
}

onUnmounted(() => {
  stopQaPoll();
});

function pollQualityAssessment(runId: string) {
  stopQaPoll();
  qaPollTimer = setInterval(() => {
    void (async () => {
      try {
        const view = await obsApi.fetchRagQualityAssessment(runId);
        qaResult.value = view;
        if (view.status === "SUCCEEDED" || view.status === "FAILED") {
          stopQaPoll();
          qaLoading.value = false;
          if (view.status === "FAILED") {
            ElMessage.error(t("views.ragQuality.assessFailed"));
          } else {
            ElMessage.success(t("views.ragQuality.assessDone"));
          }
        }
      } catch {
        /* ignore poll errors */
      }
    })();
  }, 2000);
}

async function runQualityAssessment() {
  if (!canAssessQuality.value || props.conversationId == null || props.userMessageId == null) {
    return;
  }
  stopQaPoll();
  qaLoading.value = true;
  qaResult.value = null;
  try {
    const kbId = props.message.ragCitations?.[0]?.kbId;
    const initial = await obsApi.submitRagQualityAssessment({
      scope: "MESSAGE_TURN",
      conversationId: props.conversationId,
      userMessageId: props.userMessageId,
      assistantMessageId: props.message.id,
      kbId,
      queryText: (props.userQueryText ?? "").trim() || undefined,
      assistantAnswer: props.message.content ?? "",
    });
    qaResult.value = initial;
    if (initial.status === "SUCCEEDED" || initial.status === "FAILED") {
      qaLoading.value = false;
      if (initial.status === "FAILED") {
        ElMessage.error(t("views.ragQuality.assessFailed"));
      } else {
        ElMessage.success(t("views.ragQuality.assessDone"));
      }
      return;
    }
    pollQualityAssessment(initial.runId);
  } catch {
    qaLoading.value = false;
    ElMessage.error(t("views.ragQuality.assessFailed"));
  }
}

function ragCitationLabel(c: RagCitationAdmin): string {
  const title = (c.documentTitle || t("views.chatDrawerAudit.docFallback")).trim();
  const short = title.length > 18 ? `${title.slice(0, 18)}…` : title;
  return t("views.chatDrawerAudit.citationLabel", { title: short, n: c.chunkSeq + 1 });
}

function webRefLabel(w: WebSearchRefAdmin): string {
  const title = (w.title || "").trim();
  const site = (w.siteName || "").trim();
  if (title && site) return `${title} · ${site}`;
  if (title) return title;
  if (site) return site;
  const u = (w.url || "").trim();
  if (u) {
    try {
      return new URL(u).hostname || u;
    } catch {
      return u.length > 40 ? `${u.slice(0, 40)}…` : u;
    }
  }
  return t("views.chatDrawerAudit.linkFallback");
}
</script>

<style scoped>
.adb-root {
  min-width: 0;
}

.msg-model {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.msg-content-summary {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.msg-content-summary--current {
  margin-top: 4px;
}

.msg-summary-hdr {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.msg-summary-sub {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  line-height: 1.4;
}

.msg-summary-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.msg-md {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.bubble-md :deep(.markdown-rich__html pre) {
  overflow-x: auto;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color);
  font-size: 13px;
}

.msg-reasoning-md {
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.msg-reasoning-md :deep(.ai-code-block) {
  margin: 0.6em 0;
}

.msg-usage {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.msg-rag-wrap {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.msg-rag-hdr {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
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

.msg-web-wrap {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.msg-web-hdr {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.msg-web-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.msg-web-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  text-decoration: none;
  word-break: break-word;
}

.msg-web-chip:hover {
  border-color: var(--el-color-primary);
}

.msg-web-chip--nolink {
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-lighter);
}

.msg-reasoning {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--el-fill-color-light) 0%, var(--el-fill-color) 100%);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-border-color);
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
  color: var(--el-text-color-regular);
}

.msg-assistant-current {
  border-radius: 10px;
  border: 1px solid var(--el-color-primary-light-5);
  border-left: 5px solid var(--el-color-primary);
  background: linear-gradient(180deg, var(--el-fill-color-light) 0%, var(--el-bg-color) 48%);
  padding: 12px 14px 14px;
  box-shadow: var(--el-box-shadow-lighter);
}

.msg-version-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 14px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.msg-version-strip--current {
  border-bottom-color: var(--el-color-primary-light-5);
}

.msg-version-label {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
}

.msg-version-meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.msg-reasoning--in-current {
  border-left-color: var(--el-color-primary);
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-light) 100%);
}

.bubble-md--current {
  font-size: 14px;
}

.msg-usage--current {
  color: var(--el-color-primary);
  font-weight: 500;
}

.msg-assistant-archive {
  margin-top: 16px;
  padding: 8px;
  border-radius: 10px;
  border: 1px dashed var(--el-border-color);
  background: var(--el-fill-color);
  box-shadow: inset 0 1px 0 var(--el-border-color-lighter);
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
  color: var(--el-text-color-regular);
  letter-spacing: 0.04em;
}

.msg-archive-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.msg-archive-sub {
  display: block;
  font-size: 11px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
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
  color: var(--el-text-color-secondary);
  background: var(--el-border-color-lighter);
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
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
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  box-shadow: var(--el-box-shadow-lighter);
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
  border-bottom: 1px dashed var(--el-border-color);
}

.msg-prior-badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  padding: 2px 8px;
  border-radius: 6px;
}

.msg-prior-model {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.msg-reasoning--archive {
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-left-color: var(--el-border-color);
}

.bubble-md--archive {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.55;
}

.bubble-md--archive :deep(.markdown-rich__html pre) {
  background: var(--el-fill-color);
  font-size: 12px;
}

.msg-usage--archive {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.msg-qa-wrap {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-qa-scores {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.msg-qa-score {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
}

:global(html.dark) .msg-qa-score {
  color: var(--el-color-primary-light-3);
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
}

:global(html.dark) .msg-version-label {
  color: var(--el-color-primary-light-3);
}

:global(html.dark) .msg-prior-badge {
  color: var(--el-color-warning-light-3);
}
</style>
