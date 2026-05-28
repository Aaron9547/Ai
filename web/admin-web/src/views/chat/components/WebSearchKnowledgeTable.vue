<template>
  <div class="pool">
    <el-alert type="info" :closable="false" show-icon class="knowledge-hint">
      {{ t("views.chatStarter.webKnowledgeHint") }}
    </el-alert>
    <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.chatStarter.webKnowledgeEmpty')">
      <el-table-column :label="t('views.chatStarter.colSource')" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ sourceLabel(row.source) }}
        </template>
      </el-table-column>
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
    <div v-if="total > 0" class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPage"
        @size-change="onPageSize"
      />
    </div>

    <el-dialog
      v-model="refsDlgOpen"
      :title="refsDlgTitle"
      width="min(880px, 96vw)"
      destroy-on-close
      append-to-body
      class="web-grounding-refs-dlg"
    >
      <div v-loading="refsDlgLoading" class="refs-dlg-body">
        <div v-if="refsDetail?.queryNormalized" class="refs-query-bar">
          <span class="refs-query-label">{{ t("views.chatStarter.webRefsNormLabel") }}</span>
          <span class="refs-query-text">{{ refsDetail.queryNormalized }}</span>
        </div>

        <section v-if="summarySections.length" class="refs-summary-block">
          <div class="refs-block-head">
            <h4 class="refs-block-title">{{ t("views.chatStarter.webRefsSummaryHdr") }}</h4>
            <p v-if="summaryDeduped" class="refs-dedup-hint">
              {{ t("views.chatStarter.webRefsSummaryDeduped", { raw: summaryRawCount, kept: summarySections.length }) }}
            </p>
          </div>
          <p class="refs-summary-note">{{ t("views.chatStarter.webRefsSummaryNote") }}</p>
          <el-collapse
            v-model="summaryCollapseActive"
            class="refs-summary-collapse"
          >
            <el-collapse-item
              v-for="(sec, sIdx) in summarySections"
              :key="'sum-' + sIdx"
              :name="sIdx"
            >
              <template #title>
                <span class="refs-summary-tab-title">
                  {{ sec.label || t("views.chatStarter.webRefsSummaryUntitled", { n: sIdx + 1 }) }}
                </span>
              </template>
              <pre class="refs-summary-body">{{ sec.body }}</pre>
            </el-collapse-item>
          </el-collapse>
        </section>

        <section class="refs-tabs-block">
          <div class="refs-block-head">
            <h4 class="refs-block-title">
              {{ t("views.chatStarter.webRefsListHdr", { n: dedupedRefCount }) }}
            </h4>
            <p v-if="refsDeduped" class="refs-dedup-hint">
              {{ t("views.chatStarter.webRefsListDeduped", { raw: rawRefCount, kept: dedupedRefCount }) }}
            </p>
          </div>
          <el-empty
            v-if="!refsDlgLoading && refGroups.length === 0"
            :description="t('views.chatStarter.webRefsEmpty')"
          />
          <el-tabs
            v-else
            v-model="refsActiveTab"
            type="card"
            class="refs-source-tabs"
          >
            <el-tab-pane
              v-for="group in refGroups"
              :key="group.key"
              :name="group.key"
            >
              <template #label>
                <span class="refs-tab-label">
                  {{ t(`views.chatStarter.webRefsTab.${group.key}`) }}
                  <el-badge :value="group.items.length" type="primary" class="refs-tab-badge" />
                </span>
              </template>
              <div class="refs-card-list">
                <article
                  v-for="(ref, idx) in group.items"
                  :key="group.key + '-' + idx"
                  class="refs-card"
                >
                  <header class="refs-card-head">
                    <span class="refs-card-index">{{ idx + 1 }}</span>
                    <div class="refs-card-title-wrap">
                      <a
                        v-if="(ref.url ?? '').trim()"
                        class="refs-card-title"
                        :href="ref.url"
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {{ refLabel(ref) }}
                        <el-icon class="refs-card-ext"><TopRight /></el-icon>
                      </a>
                      <span v-else class="refs-card-title refs-card-title--plain">{{ refLabel(ref) }}</span>
                      <div v-if="ref.siteName || ref.publishTime" class="refs-card-meta">
                        <el-tag v-if="ref.siteName" size="small" type="info" effect="plain">
                          {{ ref.siteName }}
                        </el-tag>
                        <span v-if="ref.publishTime" class="refs-card-time">{{ ref.publishTime }}</span>
                      </div>
                    </div>
                  </header>
                  <p v-if="(ref.snippet ?? '').trim()" class="refs-card-snippet">{{ ref.snippet }}</p>
                  <p v-if="(ref.url ?? '').trim()" class="refs-card-url" :title="ref.url">{{ ref.url }}</p>
                </article>
              </div>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { TopRight } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  deleteStarterPrompt,
  getWebGroundingDetail,
  listStarterPrompts,
  updateStarterPrompt,
  type StarterPromptRow,
  type WebGroundingDetailView,
  type WebGroundingReferenceItem,
} from "@/api/chatStarterPrompt";
import {
  buildDisplaySummarySections,
  dedupeWebRefs,
  groupWebRefsBySource,
  type WebRefSourceKey,
} from "@/utils/webRefSource";

const props = defineProps<{
  refreshToken?: number;
}>();

const emit = defineEmits<{ changed: [] }>();

const { t } = useI18n();
const loading = ref(false);
const rows = ref<StarterPromptRow[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const toggling = ref(false);

const refsDlgOpen = ref(false);
const refsDlgLoading = ref(false);
const refsDlgTitle = ref("");
const refsDetail = ref<WebGroundingDetailView | null>(null);
const refsActiveTab = ref<WebRefSourceKey>("OTHER");

const summaryPack = computed(() =>
  buildDisplaySummarySections(refsDetail.value?.summaryText ?? ""),
);
const summarySections = computed(() => summaryPack.value.sections);
const summaryRawCount = computed(() => summaryPack.value.rawCount);
const summaryDeduped = computed(
  () => summaryRawCount.value > summarySections.value.length && summarySections.value.length > 0,
);
const summaryCollapseActive = ref<number[]>([0]);

const rawRefCount = computed(() => refsDetail.value?.references?.length ?? 0);
const dedupedRefs = computed(() => dedupeWebRefs(refsDetail.value?.references));
const dedupedRefCount = computed(() => dedupedRefs.value.length);
const refsDeduped = computed(() => rawRefCount.value > dedupedRefCount.value);
const refGroups = computed(() => groupWebRefsBySource(dedupedRefs.value));

watch(
  refGroups,
  (groups) => {
    if (!groups.length) return;
    if (!groups.some((g) => g.key === refsActiveTab.value)) {
      refsActiveTab.value = groups[0].key;
    }
  },
  { immediate: true },
);

function sourceLabel(src: string | undefined) {
  const map: Record<string, string> = {
    WEB_SEARCH_GROUNDING: t("views.chatStarter.sourceWebKnowledge"),
    DAILY_RECOMMEND: t("views.chatStarter.sourceDailyRecommend"),
    HOT_TOPIC_DAILY: t("views.chatStarter.sourceHot"),
  };
  return map[src ?? ""] ?? src ?? "—";
}

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

async function load() {
  loading.value = true;
  try {
    const res = await listStarterPrompts({
      scene: "WEB_KNOWLEDGE",
      page: page.value,
      size: pageSize.value,
    });
    rows.value = res.records;
    total.value = res.total;
    page.value = res.page;
    pageSize.value = res.size;
  } catch {
    ElMessage.error(t("views.chatStarter.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function onPage(p: number) {
  page.value = p;
  void load();
}

function onPageSize(s: number) {
  pageSize.value = s;
  page.value = 1;
  void load();
}

async function openRefs(row: StarterPromptRow) {
  refsDlgTitle.value = t("views.chatStarter.webRefsDlgTitle", {
    text: row.promptText || "—",
  });
  refsDlgOpen.value = true;
  refsDlgLoading.value = true;
  refsDetail.value = null;
  summaryCollapseActive.value = [0];
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
    await load();
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
    if (rows.value.length <= 1 && page.value > 1) {
      page.value -= 1;
    }
    await load();
  } catch {
    ElMessage.error(t("views.chatStarter.saveFailed"));
  }
}

watch(
  () => props.refreshToken,
  () => {
    void load();
  },
);

onMounted(() => {
  void load();
});
</script>

<style scoped>
.knowledge-hint {
  margin-bottom: 12px;
}

.ref-zero {
  color: var(--el-text-color-placeholder);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.refs-dlg-body {
  max-height: min(72vh, 620px);
  overflow-y: auto;
  padding: 0 4px;
}

.refs-query-bar {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
}

.refs-query-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.refs-query-text {
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.refs-block-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.refs-block-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.refs-dedup-hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.refs-summary-note {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.refs-summary-block {
  margin-bottom: 18px;
}

.refs-summary-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  font-weight: 500;
}

.refs-summary-tab-title {
  color: var(--el-text-color-primary);
}

.refs-summary-body {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

.refs-tabs-block {
  margin-top: 4px;
}

.refs-source-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.refs-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.refs-tab-badge :deep(.el-badge__content) {
  position: static;
  transform: none;
  font-size: 11px;
  height: 16px;
  line-height: 16px;
  padding: 0 5px;
}

.refs-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.refs-card {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.refs-card-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.refs-card-index {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
}

.refs-card-title-wrap {
  flex: 1;
  min-width: 0;
}

.refs-card-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
  text-decoration: none;
  word-break: break-word;
}

.refs-card-title:hover {
  text-decoration: underline;
}

.refs-card-title--plain {
  color: var(--el-text-color-primary);
}

.refs-card-ext {
  font-size: 14px;
  flex-shrink: 0;
}

.refs-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.refs-card-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.refs-card-snippet {
  margin: 10px 0 0 32px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.refs-card-url {
  margin: 6px 0 0 32px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
