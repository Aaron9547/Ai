<template>
  <el-dialog
    v-model="visible"
    class="kb-chunk-preview-dlg"
    :title="t('views.kbMatrix.chunkPreviewTitle')"
    width="760px"
    destroy-on-close
    @closed="onClosed"
  >
    <div v-if="loading" class="preview-loading">
      <el-skeleton :rows="6" animated />
    </div>
    <el-scrollbar
      v-else-if="result"
      class="kb-chunk-preview-scroll admin-el-scrollbar"
      max-height="min(72vh, 640px)"
    >
      <div class="kb-chunk-preview-scroll-inner">
      <p class="preview-meta">
        {{
          t("views.kbMatrix.chunkPreviewMeta", {
            strategy: strategyLabel(result.strategyCode),
            fixed: result.fixedChars,
            overlap: result.slideOverlap,
            pages: result.pageCount,
            chunks: result.totalChunkCount,
            discovered: result.discoveredUrlCount ?? result.pageCount,
          })
        }}
      </p>
      <div v-for="(page, pi) in result.pages" :key="pi" class="preview-page">
        <h4 class="preview-page-title">{{ page.title || page.url }}</h4>
        <p class="preview-page-sub">
          {{ page.url }} · {{ page.markdownChars }} {{ t("views.kbMatrix.chunkPreviewChars") }} ·
          {{ page.chunkCount }} {{ t("views.kbMatrix.chunkPreviewBlocks") }}
        </p>
        <el-alert v-if="page.error" type="warning" :closable="false" show-icon class="preview-trunc">
          {{ page.error }}
        </el-alert>
        <el-alert v-else-if="page.chunksTruncated" type="info" :closable="false" show-icon class="preview-trunc">
          {{ t("views.kbMatrix.chunkPreviewTruncated") }}
        </el-alert>
        <el-table v-if="!page.error && page.chunks.length" :data="page.chunks" size="small" border stripe>
          <el-table-column :label="t('views.kbMatrix.chunkPreviewColSeq')" width="56" align="center">
            <template #default="{ row }">#{{ row.seq + 1 }}</template>
          </el-table-column>
          <el-table-column :label="t('views.kbMatrix.chunkPreviewColLen')" width="72" align="right" prop="chars" />
          <el-table-column :label="t('views.kbMatrix.colPreview')" min-width="320">
            <template #default="{ row }">
              <div class="preview-md md-surface-scroll" v-html="previewHtml(row.preview)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      </div>
    </el-scrollbar>
    <el-empty v-else :description="t('views.kbMatrix.chunkPreviewEmpty')" />
    <template #footer>
      <el-button @click="visible = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import * as ragApi from "@/api/ragAdmin";
import type { ChunkPreviewResult } from "@/api/ragAdmin";
import { renderMarkdownToSafeHtml } from "@/utils/renderMarkdown";

const props = defineProps<{ modelValue: boolean; kbId: number }>();
const emit = defineEmits<{ "update:modelValue": [boolean] }>();

const { t } = useI18n();
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const loading = ref(false);
const result = ref<ChunkPreviewResult | null>(null);

function strategyLabel(code: number): string {
  const key = `views.kbAsync.chunkStrategy.${code}`;
  const msg = t(key);
  return msg === key ? String(code) : msg;
}

function onClosed() {
  result.value = null;
  loading.value = false;
}

function previewHtml(text: string): string {
  return renderMarkdownToSafeHtml(text || "");
}

async function run(body: ragApi.ChunkPreviewRequestBody) {
  loading.value = true;
  result.value = null;
  visible.value = true;
  try {
    result.value = await ragApi.previewIngestChunks(props.kbId, body);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e
        ? String((e as { message?: string }).message)
        : t("views.kbMatrix.chunkPreviewFailed");
    ElMessage.error(msg);
    visible.value = false;
  } finally {
    loading.value = false;
  }
}

defineExpose({ run });
</script>

<style scoped>
.preview-meta {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.preview-page {
  margin-bottom: 20px;
}
.preview-page-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
}
.preview-page-sub {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  word-break: break-all;
}
.preview-trunc {
  margin-bottom: 8px;
}
.preview-loading {
  padding: 8px 0;
}

.kb-chunk-preview-scroll-inner {
  padding: 2px 4px 8px;
}

.kb-chunk-preview-scroll :deep(.el-scrollbar__view) {
  padding-right: 6px;
}

.preview-page :deep(.el-table__body-wrapper) {
  scrollbar-width: thin;
  scrollbar-color: var(--admin-scroll-thumb) transparent;
}

.preview-page :deep(.el-table__body-wrapper)::-webkit-scrollbar {
  width: var(--admin-scroll-size);
  height: var(--admin-scroll-size);
}

.preview-page :deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background-color: var(--admin-scroll-thumb);
}

.preview-md {
  font-size: 12px;
  line-height: 1.5;
  max-height: 140px;
  overflow-x: hidden;
  overflow-y: auto;
  text-align: left;
  padding: 4px 6px 4px 4px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.preview-md :deep(p) {
  margin: 0 0 0.35em;
}

.preview-md :deep(p:last-child) {
  margin-bottom: 0;
}

.preview-md :deep(h1),
.preview-md :deep(h2),
.preview-md :deep(h3),
.preview-md :deep(h4) {
  margin: 0 0 0.25em;
  font-size: 12px;
  font-weight: 600;
}

.preview-md :deep(ul),
.preview-md :deep(ol) {
  margin: 0;
  padding-left: 1.1em;
}
</style>
