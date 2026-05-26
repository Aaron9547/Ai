<template>
  <div class="pool">
    <el-alert type="info" :closable="false" show-icon class="knowledge-hint">
      {{ t("views.chatStarter.webKnowledgeHint") }}
    </el-alert>
    <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.chatStarter.webKnowledgeEmpty')">
      <el-table-column prop="promptText" :label="t('views.chatStarter.colText')" min-width="200" show-overflow-tooltip />
      <el-table-column
        prop="groundingSummaryPreview"
        :label="t('views.chatStarter.colSummary')"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column prop="referenceCount" :label="t('views.chatStarter.colRefs')" width="88" align="center">
        <template #default="{ row }">
          <el-button
            v-if="(row.referenceCount ?? 0) > 0"
            link
            type="primary"
            size="small"
            @click="openRefs(row)"
          >
            {{ row.referenceCount }}
          </el-button>
          <span v-else class="ref-zero">0</span>
        </template>
      </el-table-column>
      <el-table-column prop="hitCount" :label="t('views.chatStarter.colHits')" width="88" align="center" />
      <el-table-column :label="t('views.chatStarter.colEnabled')" width="88">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            @change="(v: boolean) => toggleEnabled(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" :label="t('views.chatStarter.colUpdatedAt')" width="168" show-overflow-tooltip />
      <el-table-column :label="t('views.chatStarter.colActions')" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="remove(row)">{{ t("common.delete") }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="refsDlgOpen"
      :title="refsDlgTitle"
      width="min(720px, 96vw)"
      destroy-on-close
      append-to-body
      class="web-grounding-refs-dlg"
    >
      <div v-loading="refsDlgLoading" class="refs-dlg-body">
        <p v-if="refsDetail?.queryNormalized" class="refs-norm">
          <span class="refs-norm-label">{{ t("views.chatStarter.webRefsNormLabel") }}</span>
          {{ refsDetail.queryNormalized }}
        </p>
        <section v-if="(refsDetail?.summaryText ?? '').trim()" class="refs-summary">
          <h4 class="refs-section-hdr">{{ t("views.chatStarter.webRefsSummaryHdr") }}</h4>
          <p class="refs-summary-text">{{ refsDetail!.summaryText }}</p>
        </section>
        <section class="refs-list-section">
          <h4 class="refs-section-hdr">
            {{ t("views.chatStarter.webRefsListHdr", { n: refsDetail?.references?.length ?? 0 }) }}
          </h4>
          <el-empty
            v-if="!refsDlgLoading && !(refsDetail?.references?.length)"
            :description="t('views.chatStarter.webRefsEmpty')"
          />
          <ul v-else class="refs-list">
            <li v-for="(ref, idx) in refsDetail?.references ?? []" :key="idx" class="refs-item">
              <div class="refs-item-head">
                <a
                  v-if="(ref.url ?? '').trim()"
                  class="refs-item-title"
                  :href="ref.url"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ refLabel(ref) }}
                </a>
                <span v-else class="refs-item-title refs-item-title--plain">{{ refLabel(ref) }}</span>
                <span v-if="ref.siteName" class="refs-item-site">{{ ref.siteName }}</span>
                <span v-if="ref.publishTime" class="refs-item-time">{{ ref.publishTime }}</span>
              </div>
              <p v-if="(ref.snippet ?? '').trim()" class="refs-item-snippet">{{ ref.snippet }}</p>
              <p v-if="(ref.url ?? '').trim()" class="refs-item-url">{{ ref.url }}</p>
            </li>
          </ul>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  deleteStarterPrompt,
  getWebGroundingDetail,
  updateStarterPrompt,
  type StarterPromptRow,
  type WebGroundingDetailView,
  type WebGroundingReferenceItem,
} from "@/api/chatStarterPrompt";

defineProps<{
  rows: StarterPromptRow[];
  loading?: boolean;
}>();

const emit = defineEmits<{ changed: [] }>();

const { t } = useI18n();
const toggling = ref(false);

const refsDlgOpen = ref(false);
const refsDlgLoading = ref(false);
const refsDlgTitle = ref("");
const refsDetail = ref<WebGroundingDetailView | null>(null);

function refLabel(ref: WebGroundingReferenceItem): string {
  const title = (ref.title ?? "").trim();
  if (title) return title;
  const url = (ref.url ?? "").trim();
  if (url) {
    try {
      return new URL(url).hostname;
    } catch {
      return url;
    }
  }
  return t("views.chatStarter.webRefsUntitled");
}

async function openRefs(row: StarterPromptRow) {
  refsDlgTitle.value = t("views.chatStarter.webRefsDlgTitle", {
    text: row.promptText || "—",
  });
  refsDlgOpen.value = true;
  refsDlgLoading.value = true;
  refsDetail.value = null;
  try {
    refsDetail.value = await getWebGroundingDetail(row.id);
  } catch {
    ElMessage.error(t("views.chatStarter.webRefsLoadFailed"));
    refsDlgOpen.value = false;
  } finally {
    refsDlgLoading.value = false;
  }
}

async function toggleEnabled(row: StarterPromptRow, enabled: boolean) {
  if (toggling.value) return;
  toggling.value = true;
  try {
    await updateStarterPrompt(row.id, { enabled });
    emit("changed");
  } catch {
    ElMessage.error(t("views.chatStarter.saveFailed"));
  } finally {
    toggling.value = false;
  }
}

async function remove(row: StarterPromptRow) {
  try {
    await deleteStarterPrompt(row.id);
    ElMessage.success(t("views.chatStarter.deleted"));
    emit("changed");
  } catch {
    ElMessage.error(t("views.chatStarter.saveFailed"));
  }
}
</script>

<style scoped>
.knowledge-hint {
  margin-bottom: 12px;
}

.ref-zero {
  color: var(--el-text-color-placeholder);
}

.refs-dlg-body {
  max-height: min(70vh, 560px);
  overflow-y: auto;
  padding: 0 2px;
}

.refs-norm {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  word-break: break-word;
}

.refs-norm-label {
  font-weight: 600;
  margin-right: 6px;
}

.refs-section-hdr {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.refs-summary {
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
}

.refs-summary-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

.refs-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.refs-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.refs-item:last-child {
  border-bottom: none;
}

.refs-item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.refs-item-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
  text-decoration: none;
}

.refs-item-title:hover {
  text-decoration: underline;
}

.refs-item-title--plain {
  color: var(--el-text-color-primary);
}

.refs-item-site,
.refs-item-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.refs-item-snippet {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.refs-item-url {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  word-break: break-all;
}
</style>
