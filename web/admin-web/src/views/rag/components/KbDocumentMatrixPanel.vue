<template>
  <div class="kb-dmx-root">
    <el-alert
      v-if="!vectorMilvusEnabled"
      type="warning"
      show-icon
      :closable="false"
      class="rag-cap-inline-alert"
      :title="t('views.kbMatrix.vecWarnTitle')"
      :description="t('views.kbMatrix.vecWarnDesc')"
    />
    <div class="docs-matrix">
            <aside class="docs-nav" v-loading="loadingCategories">
              <el-button type="primary" class="new-cat-btn" @click="openCategoryCreate">{{ t("views.kbMatrix.newCategory") }}</el-button>
              <nav class="cat-nav">
                <button
                  type="button"
                  class="cat-item"
                  :class="{ 'is-active': selectedCategoryId === null }"
                  @click="selectCategoryFilter(null)"
                >
                  {{ t("views.kbMatrix.allCategories") }}
                </button>
                <div
                  v-for="c in categories"
                  :key="c.id"
                  class="cat-row"
                  :class="{ 'is-active': selectedCategoryId === c.id }"
                >
                  <button type="button" class="cat-item cat-item-grow" @click="selectCategoryFilter(c.id)">
                    {{ c.name }}
                  </button>
                  <el-dropdown trigger="click" @command="(cmd: string) => onCategoryRowCommand(cmd, c)">
                    <el-button class="cat-more" text type="primary" size="small">···</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">{{ t("views.kbMatrix.edit") }}</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>{{ t("views.kbMatrix.delete") }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </nav>
            </aside>

            <div class="docs-main">
              <div class="docs-toolbar">
                <div class="docs-toolbar-left">
                  <el-input
                    v-model="queryTitle"
                    :placeholder="t('views.kbMatrix.docTitlePh')"
                    clearable
                    class="q-title"
                    @keyup.enter="runDocQuery"
                  />
                  <el-select v-model="queryDisplayStatus" :placeholder="t('views.kbMatrix.statusPh')" clearable class="q-status">
                    <el-option :label="t('views.kbMatrix.statusPublished')" value="PUBLISHED" />
                    <el-option :label="t('views.kbMatrix.statusParsing')" value="PARSING" />
                    <el-option :label="t('views.kbMatrix.statusFailed')" value="PARSE_FAILED" />
                  </el-select>
                  <el-button @click="resetDocQuery">{{ t("views.kbMatrix.reset") }}</el-button>
                  <el-button type="primary" @click="runDocQuery">{{ t("views.kbMatrix.query") }}</el-button>
                </div>
                <div class="docs-toolbar-right">
                  <el-button
                    type="warning"
                    plain
                    :loading="indexingLoading"
                    :disabled="!vectorMilvusEnabled"
                    @click="triggerIndex"
                  >
                    {{ t("views.kbMatrix.triggerIndex") }}
                  </el-button>
                  <el-button plain @click="openJobsDialog">{{ t("views.kbMatrix.jobsBtn") }}</el-button>
                  <el-button :loading="loadingDocPage || loadingCategories" @click="refreshDocs">{{ t("views.kbMatrix.refresh") }}</el-button>
                  <el-button type="primary" :disabled="!vectorMilvusEnabled" @click="openIngest">{{ t("views.kbMatrix.uploadIngest") }}</el-button>
                </div>
              </div>

              <div ref="docsTableWrapRef" class="docs-table-wrap">
                <el-table
                  ref="docTableRef"
                  v-loading="loadingDocPage"
                  :data="docRows"
                  class="docs-table"
                  border
                  stripe
                  :height="docTableBodyHeight"
                  :empty-text="t('views.kbMatrix.emptyDocs')"
                  @selection-change="onDocSelectionChange"
                >
                <el-table-column type="selection" width="48" align="center" />
                <el-table-column :label="t('views.kbMatrix.colDocTitle')" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <router-link class="doc-title-link" :to="docChunksRoute(row)">
                      {{ row.title || t("views.kbMatrix.noTitle") }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colHits')" width="88" align="right">
                  <template #default="{ row }">{{ row.hitCount ?? 0 }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colScope')" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.applicableScope || emDash }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colUploader')" width="120" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.uploadedByLabel || emDash }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colUpdated')" width="172">
                  <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colStatus')" width="112" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.displayStatus === 'PUBLISHED'" type="success" size="small">{{
                      t("views.kbMatrix.statusPublished")
                    }}</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSING'" type="warning" size="small">{{
                      t("views.kbMatrix.statusParsing")
                    }}</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSE_FAILED'" type="danger" size="small">{{
                      t("views.kbMatrix.statusFailed")
                    }}</el-tag>
                    <el-tag v-else size="small">{{ ragDocumentDisplayStatusLabel(row.displayStatus) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colActions')" width="300" align="right" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.displayStatus === 'PUBLISHED' || !row.displayStatus">
                      <el-button link type="primary" size="small" @click="openDocSettings(row)">{{ t("views.kbMatrix.settings") }}</el-button>
                      <el-button link type="primary" size="small" @click="openIngest">{{ t("views.kbMatrix.updateDoc") }}</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">{{ t("views.kbMatrix.removeDoc") }}</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">{{ t("views.kbMatrix.download") }}</el-button>
                      <el-button link type="primary" size="small" @click="openChunksDrawer(row)">{{ t("views.kbMatrix.chunks") }}</el-button>
                    </template>
                    <template v-else-if="row.displayStatus === 'PARSE_FAILED'">
                      <el-button link type="primary" size="small" @click="openIngest">{{ t("views.kbMatrix.reupload") }}</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">{{ t("views.kbMatrix.removeDoc") }}</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">{{ t("views.kbMatrix.download") }}</el-button>
                    </template>
                    <span v-else class="muted">{{ emDash }}</span>
                  </template>
                </el-table-column>
                </el-table>
              </div>

              <div class="docs-footer">
                <div class="docs-footer-left">
                  <span class="doc-range-text">{{ t("views.kbMatrix.rangeText", { range: docRangeText, total: docPageTotal }) }}</span>
                  <el-button v-if="selectedDocs.length" link type="danger" size="small" @click="batchRemoveDocs">
                    {{ t("views.kbMatrix.batchDelete") }}
                  </el-button>
                </div>
                <el-pagination
                  v-model:current-page="docPageCurrent"
                  v-model:page-size="docPageSize"
                  :total="docPageTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="sizes, prev, pager, next, jumper"
                  background
                  @current-change="loadDocPage"
                  @size-change="onDocPageSizeChange"
                />
              </div>
            </div>
    </div>

    <el-dialog
      v-model="jobsDlgOpen"
      :title="t('views.kbMatrix.jobsDlgTitle')"
      width="min(920px, 96vw)"
      class="jobs-dlg"
      destroy-on-close
      @open="onJobsDialogOpen"
    >
      <p class="jobs-dlg-hint">
        {{ t("views.kbMatrix.jobsDlgHint") }}
      </p>
      <div class="jobs-dlg-toolbar">
        <el-button type="primary" plain :loading="loadingJobs" @click="loadKbJobs">{{ t("views.kbMatrix.refreshJobs") }}</el-button>
      </div>
      <el-table
        v-loading="loadingJobs"
        :data="jobPipelineRows"
        size="small"
        stripe
        border
        class="jobs-dlg-table"
        max-height="420"
        :empty-text="t('views.kbMatrix.emptyJobs')"
      >
        <el-table-column :label="t('views.kbMatrix.colJobType')" width="108" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small" effect="plain">{{ row.typeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colTitleAddr')" min-width="200">
          <template #default="{ row }">
            <div class="cell-title">{{ row.title }}</div>
            <div v-if="row.subtitle" class="cell-sub">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colJobStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.statusType" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colTime')" width="168">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="expand-inner job-expand">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item :label="t('views.kbMatrix.expandJobId')">{{ row.job?.id }}</el-descriptions-item>
                <el-descriptions-item :label="t('views.kbMatrix.expandTaskType')">{{ jobTaskTypeLabel(row.job?.taskType) }}</el-descriptions-item>
                <el-descriptions-item :label="t('views.kbMatrix.expandStatus')">
                  <el-tag v-if="row.job" :type="jobStatusMeta(row.job.status).tag" size="small">
                    {{ jobStatusMeta(row.job.status).label }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="parseJobResultMetaRows(row.job?.resultJson).length" class="job-kv-block">
                <div class="job-section-title">{{ t("views.kbMatrix.resultSummary") }}</div>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item
                    v-for="(r, ri) in parseJobResultMetaRows(row.job?.resultJson)"
                    :key="'rm-' + ri"
                    :label="r.label"
                  >
                    {{ r.value }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
              <div v-if="parseResultSteps(row.job?.resultJson).length" class="timeline-wrap">
                <div class="job-section-title">{{ t("views.kbMatrix.jobTimelineTitle") }}</div>
                <el-timeline>
                  <el-timeline-item
                    v-for="(s, i) in parseResultSteps(row.job?.resultJson)"
                    :key="i"
                    :timestamp="formatTime(s.at)"
                    placement="top"
                  >
                    <strong>{{ jobStepPhaseLabel(s.phase) }}</strong>
                    <span class="st"> · {{ jobStepStatusLabel(s.status) }}</span>
                    <div v-if="humanizeJobStepDetail(s.detail)" class="td">{{ humanizeJobStepDetail(s.detail) }}</div>
                  </el-timeline-item>
                </el-timeline>
              </div>
              <div v-if="parseJobPayloadRows(row.job?.payloadJson).length" class="job-kv-block">
                <div class="job-section-title">{{ t("views.kbMatrix.payloadSection") }}</div>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item
                    v-for="(r, pi) in parseJobPayloadRows(row.job?.payloadJson)"
                    :key="'pl-' + pi"
                    :label="r.label"
                  >
                    {{ r.value }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
              <el-collapse class="job-raw-collapse">
                <el-collapse-item :title="t('views.kbMatrix.rawPayloadCollapse')" :name="'p-' + row.key">
                  <el-scrollbar max-height="120px">
                    <pre class="json-pre">{{ prettyJson(row.job?.payloadJson) }}</pre>
                  </el-scrollbar>
                </el-collapse-item>
                <el-collapse-item :title="t('views.kbMatrix.rawResultCollapse')" :name="'r-' + row.key">
                  <el-scrollbar max-height="140px">
                    <pre class="json-pre">{{ prettyJson(row.job?.resultJson) }}</pre>
                  </el-scrollbar>
                </el-collapse-item>
              </el-collapse>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-drawer v-model="ingestOpen" :title="t('views.kbMatrix.ingestDrawerTitle')" size="480px" destroy-on-close @closed="resetIngestForm">
      <el-radio-group v-model="ingestType" class="ingest-type">
        <el-radio-button label="crawl">{{ t("views.kbMatrix.ingestTabCrawl") }}</el-radio-button>
        <el-radio-button label="upload">{{ t("views.kbMatrix.ingestTabUpload") }}</el-radio-button>
        <el-radio-button label="paste">{{ t("views.kbMatrix.ingestTabPaste") }}</el-radio-button>
      </el-radio-group>
      <p class="ingest-tip">{{ ingestTip }}</p>

      <el-form label-width="108px" class="ingest-form">
        <template v-if="ingestType === 'crawl'">
          <el-form-item :label="t('views.kbMatrix.labelWebUrl')" required>
            <el-input v-model="crawlForm.url" :placeholder="t('views.ingest.urlPh')" type="url" />
          </el-form-item>
        </template>
        <template v-else-if="ingestType === 'upload'">
          <el-form-item :label="t('views.kbMatrix.labelPickFile')" required>
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="onPickUploadFile"
              :on-remove="() => (uploadFile = null)"
              accept=".txt,.md,.pdf,.doc,.docx,.html,.htm"
            >
              <el-button type="primary" plain>{{ t("views.kbMatrix.pickFileBtn") }}</el-button>
            </el-upload>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="t('views.ingest.labelFilename')" required>
            <el-input v-model="pasteForm.originalFilename" :placeholder="t('views.ingest.filenamePh')" />
          </el-form-item>
          <el-form-item :label="t('views.ingest.labelContentType')">
            <el-input v-model="pasteForm.contentType" :placeholder="t('views.ingest.ctPh')" />
          </el-form-item>
          <el-form-item :label="t('views.ingest.labelMd')">
            <el-input v-model="pasteForm.markdownContent" type="textarea" :rows="8" :placeholder="t('views.ingest.mdPh')" />
          </el-form-item>
        </template>

        <el-form-item :label="t('views.ingest.labelChunkOverride')">
          <el-select v-model="ingestChunkStrategy" clearable :placeholder="t('views.ingest.chunkDefaultPh')" style="width: 100%">
            <el-option :label="t('views.ingest.chunk0')" :value="0" />
            <el-option :label="t('views.ingest.chunk1')" :value="1" />
            <el-option :label="t('views.ingest.chunk2')" :value="2" />
            <el-option :label="t('views.ingest.chunk3')" :value="3" />
            <el-option :label="t('views.ingest.chunk99')" :value="99" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="accent-btn" :loading="ingestSubmitting" @click="submitIngest">{{
            t("views.kbMatrix.submitIngest")
          }}</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>

    <el-dialog v-model="chunkDlg" :title="t('views.chunks.dlgEditChunk')" width="720px" destroy-on-close @closed="editingChunk = null">
      <el-input v-model="chunkEditText" type="textarea" :rows="14" />
      <template #footer>
        <el-button @click="chunkDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="saveChunk">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="chunksDrawerOpen"
      :title="chunksDrawerTitle"
      size="680px"
      destroy-on-close
      @closed="onChunksDrawerClosed"
    >
      <div v-if="chunksDrawerDoc" class="chunks-drawer-bar">
        <el-button size="small" type="primary" plain :loading="chunksDrawerLoading" @click="loadChunksInDrawer">
          {{ t("views.kbMatrix.chunksLoadBtn") }}
        </el-button>
      </div>
      <el-table
        v-if="chunksDrawerDoc && chunksDrawerRows.length"
        :data="chunksDrawerRows"
        size="small"
        border
        class="chunks-drawer-table"
      >
        <el-table-column prop="seq" label="#" width="52" />
        <el-table-column :label="t('views.kbMatrix.colHits')" width="72" align="right">
          <template #default="{ row: c }">{{ c.hitCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colPreview')" min-width="220">
          <template #default="{ row: c }">{{ preview(c.content) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('views.kbMatrix.colCreatedShort')" width="156" />
        <el-table-column :label="t('views.kbMatrix.colActions')" width="88" align="center">
          <template #default="{ row: c }">
            <el-button link type="primary" size="small" @click="openChunkEditFromDrawer(c)">{{ t("views.kbMatrix.edit") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else-if="chunksDrawerDoc"
        :description="t('views.kbMatrix.chunksEmptyHint')"
        :image-size="56"
      />
    </el-drawer>

    <el-dialog v-model="docSettingsDlg" :title="t('views.kbMatrix.docSettingsTitle')" width="520px" destroy-on-close @closed="docSettingsRow = null">
      <el-form v-if="docSettingsRow" label-width="100px">
        <el-form-item :label="t('views.chunks.categoryLabel')">
          <el-select v-model="docSettingsCategoryId" clearable :placeholder="t('views.chunks.categoryPh')" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('views.chunks.scopeLabel')">
          <el-input v-model="docSettingsScope" type="textarea" :rows="3" :placeholder="t('views.kbMatrix.scopeOptionalPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docSettingsDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="docSettingsSaving" @click="saveDocSettings">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDlg"
      :title="categoryDlgMode === 'create' ? t('views.kbMatrix.categoryDlgNew') : t('views.kbMatrix.categoryDlgEdit')"
      width="420px"
      destroy-on-close
      @closed="onCategoryDlgClosed"
    >
      <el-form label-width="80px">
        <el-form-item :label="t('views.kbMatrix.labelCatName')" required>
          <el-input v-model="categoryForm.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('views.kbMatrix.labelSort')">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategoryDlg">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import type { UploadFile } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as ragApi from "../../../api/ragAdmin";
import * as jobApi from "../../../api/jobAdmin";
import type {
  JobTaskAdminRow,
  RagChunkAdminRow,
  RagDocumentAdminRow,
  RagDocumentCategoryAdminRow,
} from "../../../types/admin";
import {
  humanizeJobStepDetail,
  jobStatusMeta,
  jobStepPhaseLabel,
  jobStepStatusLabel,
  jobTaskTypeLabel,
  jobTaskTypeShort,
  parseJobPayloadRows,
  parseJobResultMetaRows,
  parseResultSteps,
  prettyJson,
  ragDocumentDisplayStatusLabel,
} from "../../../utils/ragJobDisplay";

const { t } = useI18n();
const emDash = "\u2014";

/** 知识库文档导出 Markdown 的本地文件名：去掉常见源文件后缀，避免出现「报告.doc.md」。 */
function filenameForMarkdownExport(title: string | null | undefined): string {
  let base = (title || t("views.kbMatrix.docFallback")).trim().replace(/[/\\?%*:|"<>]/g, "_").slice(0, 120);
  if (!base) base = t("views.kbMatrix.docFallback");
  const lower = base.toLowerCase();
  if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
    return base;
  }
  const stripped = base.replace(
    /\.(docx?|pdf|html?|txt|rtf|pptx?|xlsx?|csv|json|xml|epub|odt|pages)$/i,
    "",
  );
  const root = stripped.trim() || t("views.kbMatrix.docFallback");
  return `${root}.md`;
}

const props = defineProps<{ kbId: number; vectorMilvusEnabled?: boolean }>();
const kid = computed(() => props.kbId);
/** 未传时默认可用，避免其它入口误伤；知识库首页会显式传入。 */
const vectorMilvusEnabled = computed(() => props.vectorMilvusEnabled !== false);

const loadingDocPage = ref(false);
const loadingCategories = ref(false);
const loadingJobs = ref(false);
const indexingLoading = ref(false);

const docRows = ref<RagDocumentAdminRow[]>([]);
const docPageCurrent = ref(1);
const docPageSize = ref(10);
const docPageTotal = ref(0);
const categories = ref<RagDocumentCategoryAdminRow[]>([]);
const selectedCategoryId = ref<number | null>(null);
const queryTitle = ref("");
const queryDisplayStatus = ref<string | undefined>(undefined);
const selectedDocs = ref<RagDocumentAdminRow[]>([]);
const docTableRef = ref<{ clearSelection: () => void } | null>(null);
const docsTableWrapRef = ref<HTMLElement | null>(null);
/** 供 el-table 固定高度，使无数据时表体区域仍占满剩余空间 */
const docTableBodyHeight = ref(360);
let docTableResizeObserver: ResizeObserver | null = null;

const kbJobs = ref<JobTaskAdminRow[]>([]);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const chunksByDoc = ref<Record<number, RagChunkAdminRow[]>>({});
const chunksDrawerOpen = ref(false);
const chunksDrawerDoc = ref<RagDocumentAdminRow | null>(null);
const chunksDrawerLoading = ref(false);

const docSettingsDlg = ref(false);
const docSettingsRow = ref<RagDocumentAdminRow | null>(null);
const docSettingsCategoryId = ref<number | undefined>(undefined);
const docSettingsScope = ref("");
const docSettingsSaving = ref(false);

const categoryDlg = ref(false);
const categoryDlgMode = ref<"create" | "edit">("create");
const categoryEditingId = ref<number | null>(null);
const categoryForm = reactive({ name: "", sortOrder: 0 });
const categorySaving = ref(false);

const chunkDlg = ref(false);
const chunkEditText = ref("");
const chunkSaving = ref(false);
const editingChunk = ref<{ doc: RagDocumentAdminRow; chunk: RagChunkAdminRow } | null>(null);

const jobsDlgOpen = ref(false);

const ingestOpen = ref(false);
const ingestType = ref<"crawl" | "upload" | "paste">("crawl");
const ingestChunkStrategy = ref<number | undefined>(undefined);
const crawlForm = reactive({ url: "" });
const pasteForm = reactive({ originalFilename: "", contentType: "", markdownContent: "" });
const uploadFile = ref<File | null>(null);
const ingestSubmitting = ref(false);

type JobPipelineRow = {
  key: string;
  typeLabel: string;
  title: string;
  subtitle?: string;
  statusLabel: string;
  statusType: "success" | "warning" | "info" | "danger";
  createdAt?: string | null;
  job: JobTaskAdminRow;
};

const docRangeText = computed(() => {
  if (docPageTotal.value <= 0) return "0-0";
  const start = (docPageCurrent.value - 1) * docPageSize.value + 1;
  const end = Math.min(docPageCurrent.value * docPageSize.value, docPageTotal.value);
  return `${start}-${end}`;
});

const chunksDrawerTitle = computed(() =>
  chunksDrawerDoc.value
    ? t("views.kbMatrix.chunksTitle", {
        title: chunksDrawerDoc.value.title || t("views.kbMatrix.docFallback"),
      })
    : t("views.kbMatrix.chunksTitleFallback"),
);

const chunksDrawerRows = computed(() => {
  const d = chunksDrawerDoc.value;
  if (!d) return [];
  return chunksByDoc.value[d.id] ?? [];
});

const ingestTip = computed(() => {
  if (ingestType.value === "crawl") return t("views.kbMatrix.ingestTipCrawl");
  if (ingestType.value === "upload") return t("views.kbMatrix.ingestTipUpload");
  return t("views.kbMatrix.ingestTipPaste");
});

const jobPipelineRows = computed<JobPipelineRow[]>(() =>
  kbJobs.value.map((j) => {
    const st = jobStatusMeta(j.status);
    const { title, subtitle } = jobTitleSubtitle(j);
    return {
      key: `job-${j.id}`,
      typeLabel: jobTaskTypeShort(j.taskType),
      title,
      subtitle,
      statusLabel: st.label,
      statusType: st.tag,
      createdAt: j.createdAt,
      job: j,
    };
  }),
);

function docChunksRoute(row: RagDocumentAdminRow) {
  return {
    path: `/knowledge-center/workspace/${kid.value}/documents/${row.id}/chunks`,
    query: { docTitle: row.title || "" },
  };
}

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function preview(s: string): string {
  const t = (s || "").replace(/\s+/g, " ");
  return t.length > 160 ? `${t.slice(0, 160)}…` : t;
}

function jobTitleSubtitle(j: JobTaskAdminRow): { title: string; subtitle?: string } {
  try {
    const p = JSON.parse(j.payloadJson || "{}") as {
      url?: string;
      originalFilename?: string;
    };
    if (j.taskType === "RAG_URL_IMPORT" && p.url) return { title: p.url, subtitle: jobTaskTypeLabel(j.taskType) };
    if (p.originalFilename) return { title: p.originalFilename, subtitle: jobTaskTypeLabel(j.taskType) };
  } catch {
    /* ignore */
  }
  return { title: jobTaskTypeLabel(j.taskType), subtitle: formatTime(j.createdAt) };
}

async function triggerIndex() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusWarnIdx"));
    return;
  }
  indexingLoading.value = true;
  try {
    await ragApi.enqueueRagKbIndexJob(kid.value);
    ElMessage.success(t("views.kbMatrix.indexQueued"));
    await loadKbJobs();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.enqueueFailed");
    ElMessage.error(msg);
  } finally {
    indexingLoading.value = false;
  }
}

async function loadCategories() {
  loadingCategories.value = true;
  try {
    categories.value = await ragApi.fetchRagKbDocumentCategories(kid.value);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadCatFailed");
    ElMessage.error(msg);
  } finally {
    loadingCategories.value = false;
  }
}

async function loadDocPage() {
  loadingDocPage.value = true;
  try {
    const p = await ragApi.fetchRagKbDocumentsPage(kid.value, {
      page: docPageCurrent.value,
      size: docPageSize.value,
      categoryId: selectedCategoryId.value ?? undefined,
      displayStatus: queryDisplayStatus.value,
      titleKeyword: queryTitle.value.trim() || undefined,
    });
    docRows.value = p.records ?? [];
    docPageTotal.value = p.total ?? 0;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadDocFailed");
    ElMessage.error(msg);
  } finally {
    loadingDocPage.value = false;
    void nextTick().then(() => bindDocTableResize());
  }
}

async function loadKbJobs() {
  loadingJobs.value = true;
  try {
    const p = await jobApi.fetchJobTasks({ page: 1, size: 80, ragKbId: kid.value });
    kbJobs.value = p.records.filter((j) => {
      const t = j.taskType;
      return t === "RAG_URL_IMPORT" || t === "RAG_FILE_IMPORT" || t === "RAG_INDEX";
    });
  } finally {
    loadingJobs.value = false;
  }
}

function openJobsDialog() {
  jobsDlgOpen.value = true;
}

function onJobsDialogOpen() {
  void loadKbJobs();
}

/** 仅文档与分类（主区域「刷新」）。 */
async function refreshDocs() {
  await Promise.all([loadCategories(), loadDocPage()]);
}

/** 文档 + 任务缓存（切换知识库、入库提交后、轮询依赖）。 */
async function refreshDocsAndKbJobs() {
  await Promise.all([loadCategories(), loadDocPage(), loadKbJobs()]);
}

function selectCategoryFilter(id: number | null) {
  selectedCategoryId.value = id;
  docPageCurrent.value = 1;
  void loadDocPage();
}

function resetDocQuery() {
  queryTitle.value = "";
  queryDisplayStatus.value = undefined;
  docPageCurrent.value = 1;
  void loadDocPage();
}

function runDocQuery() {
  docPageCurrent.value = 1;
  void loadDocPage();
}

function onDocPageSizeChange() {
  docPageCurrent.value = 1;
  void loadDocPage();
}

function onDocSelectionChange(rows: RagDocumentAdminRow[]) {
  selectedDocs.value = rows;
}

function openCategoryCreate() {
  categoryDlgMode.value = "create";
  categoryEditingId.value = null;
  categoryForm.name = "";
  categoryForm.sortOrder = 0;
  categoryDlg.value = true;
}

function onCategoryDlgClosed() {
  categoryEditingId.value = null;
}

async function onCategoryRowCommand(cmd: string, c: RagDocumentCategoryAdminRow) {
  if (cmd === "edit") {
    categoryDlgMode.value = "edit";
    categoryEditingId.value = c.id;
    categoryForm.name = c.name;
    categoryForm.sortOrder = c.sortOrder ?? 0;
    categoryDlg.value = true;
    return;
  }
  if (cmd === "delete") {
    try {
      await ElMessageBox.confirm(t("views.kbMatrix.deleteCatConfirm", { name: c.name }), t("views.menuItems.confirm"), {
        type: "warning",
      });
      await ragApi.deleteRagKbDocumentCategory(kid.value, c.id);
      ElMessage.success(t("views.kbMatrix.deleted"));
      if (selectedCategoryId.value === c.id) {
        selectedCategoryId.value = null;
      }
      await loadCategories();
      await loadDocPage();
    } catch {
      /* cancel or conflict */
    }
  }
}

async function saveCategoryDlg() {
  const name = categoryForm.name.trim();
  if (!name) {
    ElMessage.warning(t("views.kbMatrix.fillCatName"));
    return;
  }
  categorySaving.value = true;
  try {
    if (categoryDlgMode.value === "create") {
      await ragApi.createRagKbDocumentCategory(kid.value, { name, sortOrder: categoryForm.sortOrder });
      ElMessage.success(t("views.kbMatrix.catCreated"));
    } else if (categoryEditingId.value != null) {
      await ragApi.updateRagKbDocumentCategory(kid.value, categoryEditingId.value, {
        name,
        sortOrder: categoryForm.sortOrder,
      });
      ElMessage.success(t("views.kbMatrix.catSaved"));
    }
    categoryDlg.value = false;
    await loadCategories();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    categorySaving.value = false;
  }
}

function openDocSettings(row: RagDocumentAdminRow) {
  docSettingsRow.value = row;
  docSettingsCategoryId.value = row.categoryId ?? undefined;
  docSettingsScope.value = row.applicableScope ?? "";
  docSettingsDlg.value = true;
}

async function saveDocSettings() {
  const row = docSettingsRow.value;
  if (!row) return;
  docSettingsSaving.value = true;
  try {
    const body =
      docSettingsCategoryId.value == null
        ? { clearCategory: true as const, applicableScope: docSettingsScope.value.trim() }
        : {
            categoryId: docSettingsCategoryId.value,
            applicableScope: docSettingsScope.value.trim(),
          };
    const updated = await ragApi.patchRagKbDocument(kid.value, row.id, body);
    const i = docRows.value.findIndex((x) => x.id === updated.id);
    if (i >= 0) docRows.value[i] = updated;
    ElMessage.success(t("views.kbMatrix.docSaved"));
    docSettingsDlg.value = false;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    docSettingsSaving.value = false;
  }
}

function openChunksDrawer(row: RagDocumentAdminRow) {
  chunksDrawerDoc.value = row;
  chunksDrawerOpen.value = true;
  void loadChunksInDrawer();
}

async function loadChunksInDrawer() {
  const d = chunksDrawerDoc.value;
  if (!d) return;
  chunksDrawerLoading.value = true;
  try {
    chunksByDoc.value[d.id] = await ragApi.fetchRagKbChunks(kid.value, d.id);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadFailed");
    ElMessage.error(msg);
  } finally {
    chunksDrawerLoading.value = false;
  }
}

function onChunksDrawerClosed() {
  chunksDrawerDoc.value = null;
}

function openChunkEditFromDrawer(chunk: RagChunkAdminRow) {
  const doc = chunksDrawerDoc.value;
  if (!doc) return;
  editingChunk.value = { doc, chunk };
  chunkEditText.value = chunk.content;
  chunkDlg.value = true;
}

async function downloadDocMarkdown(row: RagDocumentAdminRow) {
  try {
    const md = await ragApi.exportRagKbDocumentMarkdown(kid.value, row.id);
    const blob = new Blob([md], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filenameForMarkdownExport(row.title);
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.downloadFailed");
    ElMessage.error(msg);
  }
}

async function batchRemoveDocs() {
  const rows = selectedDocs.value;
  if (!rows.length) return;
  try {
    await ElMessageBox.confirm(t("views.kbMatrix.batchDeleteConfirm", { n: rows.length }), t("views.menuItems.confirm"), {
      type: "warning",
    });
    for (const d of rows) {
      await ragApi.deleteRagKbDocument(kid.value, d.id);
    }
    ElMessage.success(t("views.kbMatrix.deleted"));
    docTableRef.value?.clearSelection();
    await refreshDocs();
  } catch {
    /* cancel */
  }
}

function openIngest() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusUploadDisabled"));
    return;
  }
  resetIngestForm();
  ingestOpen.value = true;
}

function resetIngestForm() {
  ingestType.value = "crawl";
  ingestChunkStrategy.value = undefined;
  crawlForm.url = "";
  pasteForm.originalFilename = "";
  pasteForm.contentType = "";
  pasteForm.markdownContent = "";
  uploadFile.value = null;
}

function onPickUploadFile(file: UploadFile) {
  uploadFile.value = (file.raw as File) || null;
}

async function submitIngest() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusSubmitDisabled"));
    return;
  }
  const cs = ingestChunkStrategy.value;
  ingestSubmitting.value = true;
  try {
    if (ingestType.value === "crawl") {
      const u = crawlForm.url.trim();
      if (!u) {
        ElMessage.warning(t("views.ingest.fillUrl"));
        return;
      }
      await ragApi.enqueueUrlImportJob(kid.value, u, cs);
      ElMessage.success(t("views.kbMatrix.crawlJobCreated"));
    } else if (ingestType.value === "upload") {
      const f = uploadFile.value;
      if (!f) {
        ElMessage.warning(t("views.kbMatrix.pickFileWarning"));
        return;
      }
      await ragApi.uploadRagKbDocument(kid.value, f, cs);
      ElMessage.success(t("views.kbMatrix.uploadIngestDone"));
    } else {
      const name = pasteForm.originalFilename.trim();
      if (!name) {
        ElMessage.warning(t("views.ingest.fillName"));
        return;
      }
      await ragApi.enqueueFileIngestJob(kid.value, {
        originalFilename: name,
        contentType: pasteForm.contentType.trim() || undefined,
        markdownContent: pasteForm.markdownContent.trim() || undefined,
        chunkStrategy: cs,
      });
      ElMessage.success(t("views.kbMatrix.fileJobCreated"));
    }
    ingestOpen.value = false;
    await refreshDocsAndKbJobs();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.submitFailed");
    ElMessage.error(msg);
  } finally {
    ingestSubmitting.value = false;
  }
}

async function removeDoc(row: RagDocumentAdminRow) {
  try {
    await ElMessageBox.confirm(
      t("views.kbMatrix.deleteDocConfirm", { title: row.title || t("views.kbMatrix.noTitle") }),
      t("views.menuItems.confirm"),
      { type: "warning" },
    );
    await ragApi.deleteRagKbDocument(kid.value, row.id);
    ElMessage.success(t("views.kbMatrix.deleted"));
    delete chunksByDoc.value[row.id];
    await refreshDocs();
  } catch {
    /* cancel */
  }
}

async function saveChunk() {
  if (!editingChunk.value) return;
  chunkSaving.value = true;
  try {
    const u = await ragApi.patchRagKbChunk(kid.value, editingChunk.value.doc.id, editingChunk.value.chunk.id, {
      content: chunkEditText.value,
    });
    const arr = chunksByDoc.value[editingChunk.value.doc.id];
    if (arr) {
      const i = arr.findIndex((x) => x.id === u.id);
      if (i >= 0) arr[i] = u;
    }
    ElMessage.success(t("views.kbMatrix.chunkUpdated"));
    chunkDlg.value = false;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    chunkSaving.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => {
    const active = kbJobs.value.some((j) => {
      const s = (j.status || "").toUpperCase();
      return s === "PENDING" || s === "RUNNING";
    });
    if (active) void loadKbJobs();
  }, 6000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function unbindDocTableResize() {
  if (docTableResizeObserver) {
    docTableResizeObserver.disconnect();
    docTableResizeObserver = null;
  }
}

/**
 * 表高取自「文档表格外层槽」{@code .docs-table-wrap} 的 {@code clientHeight}（flex:1 + min-height:0 下的可用高度），
 * 勿用 {@code docs-main} 的 {@code getBoundingClientRect().height} 参与回算：表体略超出时会把 main 撑高，
 * ResizeObserver 反复读到更大高度 → 无限增高，分页器被顶出视口（直连带 ?kbId= 进入时更易触发）。
 */
function bindDocTableResize() {
  unbindDocTableResize();
  const wrap = docsTableWrapRef.value;
  if (!wrap || typeof ResizeObserver === "undefined") {
    return;
  }
  const capByViewport = () =>
    typeof window !== "undefined" ? Math.max(240, window.innerHeight - 200) : 720;

  const apply = () => {
    let h = Math.floor(wrap.clientHeight);
    if (h < 80) {
      return;
    }
    h = Math.min(h, capByViewport());
    docTableBodyHeight.value = Math.max(200, h);
  };
  docTableResizeObserver = new ResizeObserver(() => {
    window.requestAnimationFrame(apply);
  });
  docTableResizeObserver.observe(wrap);
  requestAnimationFrame(apply);
}

watch(
  () => props.kbId,
  () => {
    if (!Number.isFinite(props.kbId)) return;
    selectedCategoryId.value = null;
    docPageCurrent.value = 1;
    queryTitle.value = "";
    queryDisplayStatus.value = undefined;
    chunksByDoc.value = {};
    stopPolling();
    void refreshDocsAndKbJobs()
      .then(() => startPolling())
      .then(() => nextTick())
      .then(() => bindDocTableResize());
  },
  { immediate: true },
);

watch(docPageSize, () => {
  void nextTick().then(() => bindDocTableResize());
});

onMounted(() => {
  void nextTick().then(() => bindDocTableResize());
});

onBeforeUnmount(() => {
  unbindDocTableResize();
  stopPolling();
});
</script>

<style scoped>
.rag-cap-inline-alert {
  margin-bottom: 12px;
  flex-shrink: 0;
}

.kb-dmx-root {
  --ws-accent: var(--el-color-primary);
  --ws-accent-weak: var(--el-color-primary-light-9);
  --ws-card: var(--el-bg-color);
  --ws-border: var(--el-border-color-lighter);
  --ws-muted: var(--el-text-color-secondary);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.doc-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.doc-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.accent-btn {
  background: linear-gradient(135deg, var(--el-color-primary-dark-2), var(--el-color-primary)) !important;
  border: none !important;
}

.doc-toolbar-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.pipe-table {
  border-radius: 12px;
}

.pipe-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.cell-title {
  font-weight: 500;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.cell-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ws-muted);
  word-break: break-all;
}

.expand-inner {
  padding: 8px 12px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.job-expand .json-hdr {
  margin-top: 10px;
}

.job-kv-block {
  margin-top: 12px;
}

.job-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.job-raw-collapse {
  margin-top: 10px;
}

.expand-hdr {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
}

.json-hdr {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.json-block {
  margin-top: 10px;
}

.json-pre {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}

.timeline-wrap {
  margin-top: 10px;
}

.st {
  color: var(--ws-muted);
  font-size: 12px;
}

.td {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
}

.muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.ingest-type {
  width: 100%;
  display: flex;
  margin-bottom: 12px;
}

.ingest-type :deep(.el-radio-button) {
  flex: 1;
}

.ingest-type :deep(.el-radio-button__inner) {
  width: 100%;
}

.ingest-tip {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.ingest-form {
  padding-top: 4px;
}

/* —— 文档矩阵（左右分栏）—— */
.docs-matrix {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.docs-nav {
  flex: 0 0 220px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-self: stretch;
  padding: 12px;
  border: 1px solid var(--ws-border);
  border-radius: 12px;
  background: var(--ws-card);
}

.new-cat-btn {
  width: 100%;
  margin-bottom: 12px;
}

.cat-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.cat-row {
  display: flex;
  align-items: stretch;
  border-radius: 8px;
  border: 1px solid transparent;
}

.cat-row.is-active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.cat-item {
  flex: 0 0 auto;
  text-align: left;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 14px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.cat-item:hover {
  background: var(--el-fill-color);
}

.cat-item.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.cat-item-grow {
  flex: 1;
  min-width: 0;
}

.cat-more {
  flex-shrink: 0;
  padding: 0 6px !important;
}

.docs-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.docs-table-wrap {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid var(--ws-border);
  background: var(--ws-card);
}

.docs-table-wrap :deep(.el-table) {
  --el-table-border-color: var(--ws-border);
}

.docs-toolbar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--ws-border);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
}

.docs-toolbar-left,
.docs-toolbar-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.q-title {
  width: 200px;
  max-width: 100%;
}

.q-status {
  width: 140px;
}

.docs-table {
  border-radius: 12px;
}

.docs-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.doc-range-text {
  font-size: 13px;
  color: var(--ws-muted);
}

.jobs-dlg-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.jobs-dlg-toolbar {
  margin-bottom: 10px;
}

.jobs-dlg-table {
  border-radius: 10px;
}

.chunks-drawer-bar {
  margin-bottom: 12px;
}

.chunks-drawer-table {
  border-radius: 8px;
}

.doc-title-link {
  color: var(--el-color-primary);
  font-weight: 500;
  text-decoration: none;
}

.doc-title-link:hover {
  text-decoration: underline;
}
</style>
