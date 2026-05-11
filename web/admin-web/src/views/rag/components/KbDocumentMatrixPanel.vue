<template>
  <div class="kb-dmx-root">
    <el-alert
      v-if="!vectorMilvusEnabled"
      type="warning"
      show-icon
      :closable="false"
      class="rag-cap-inline-alert"
      title="向量库未启用"
      description="上传、入库与触发索引已禁用；仍可浏览与下载已有文档。"
    />
    <div class="docs-matrix">
            <aside class="docs-nav" v-loading="loadingCategories">
              <el-button type="primary" class="new-cat-btn" @click="openCategoryCreate">新建分类</el-button>
              <nav class="cat-nav">
                <button
                  type="button"
                  class="cat-item"
                  :class="{ 'is-active': selectedCategoryId === null }"
                  @click="selectCategoryFilter(null)"
                >
                  全部分类
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
                        <el-dropdown-item command="edit">编辑</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </nav>
            </aside>

            <div ref="docsMainRef" class="docs-main">
              <div class="docs-toolbar">
                <div class="docs-toolbar-left">
                  <el-input
                    v-model="queryTitle"
                    placeholder="文档名称"
                    clearable
                    class="q-title"
                    @keyup.enter="runDocQuery"
                  />
                  <el-select v-model="queryDisplayStatus" placeholder="状态" clearable class="q-status">
                    <el-option label="已发布" value="PUBLISHED" />
                    <el-option label="解析中" value="PARSING" />
                    <el-option label="解析失败" value="PARSE_FAILED" />
                  </el-select>
                  <el-button @click="resetDocQuery">重置</el-button>
                  <el-button type="primary" @click="runDocQuery">查询</el-button>
                </div>
                <div class="docs-toolbar-right">
                  <el-button
                    type="warning"
                    plain
                    :loading="indexingLoading"
                    :disabled="!vectorMilvusEnabled"
                    @click="triggerIndex"
                  >
                    触发索引
                  </el-button>
                  <el-button plain @click="openJobsDialog">入库任务</el-button>
                  <el-button :loading="loadingDocPage || loadingCategories" @click="refreshDocs">刷新</el-button>
                  <el-button type="primary" :disabled="!vectorMilvusEnabled" @click="openIngest">上传 / 入库</el-button>
                </div>
              </div>

              <div class="docs-table-wrap">
                <el-table
                  ref="docTableRef"
                  v-loading="loadingDocPage"
                  :data="docRows"
                  class="docs-table"
                  border
                  stripe
                  :height="docTableBodyHeight"
                  empty-text="暂无文档"
                  @selection-change="onDocSelectionChange"
                >
                <el-table-column type="selection" width="48" align="center" />
                <el-table-column label="文档名称" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <router-link class="doc-title-link" :to="docChunksRoute(row)">
                      {{ row.title || "（无标题）" }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column label="命中" width="88" align="right">
                  <template #default="{ row }">{{ row.hitCount ?? 0 }}</template>
                </el-table-column>
                <el-table-column label="适用范围" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.applicableScope || "—" }}</template>
                </el-table-column>
                <el-table-column label="上传人" width="120" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.uploadedByLabel || "—" }}</template>
                </el-table-column>
                <el-table-column label="更新时间" width="172">
                  <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="112" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.displayStatus === 'PUBLISHED'" type="success" size="small">已发布</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSING'" type="warning" size="small">解析中</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSE_FAILED'" type="danger" size="small">解析失败</el-tag>
                    <el-tag v-else size="small">{{ ragDocumentDisplayStatusLabel(row.displayStatus) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="300" align="right" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.displayStatus === 'PUBLISHED' || !row.displayStatus">
                      <el-button link type="primary" size="small" @click="openDocSettings(row)">设置</el-button>
                      <el-button link type="primary" size="small" @click="openIngest">更新文档</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">下载</el-button>
                      <el-button link type="primary" size="small" @click="openChunksDrawer(row)">分片</el-button>
                    </template>
                    <template v-else-if="row.displayStatus === 'PARSE_FAILED'">
                      <el-button link type="primary" size="small" @click="openIngest">重新上传</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">下载</el-button>
                    </template>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                </el-table>
              </div>

              <div class="docs-footer">
                <div class="docs-footer-left">
                  <span class="doc-range-text">第 {{ docRangeText }} 条 / 共 {{ docPageTotal }} 条</span>
                  <el-button v-if="selectedDocs.length" link type="danger" size="small" @click="batchRemoveDocs">
                    批量删除
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
      title="入库异步任务"
      width="min(920px, 96vw)"
      class="jobs-dlg"
      destroy-on-close
      @open="onJobsDialogOpen"
    >
      <p class="jobs-dlg-hint">
        与本知识库相关的网页入库、文件入库与索引任务。展开行可查看阶段说明与入参摘要；原始数据在折叠区，供排障使用。
      </p>
      <div class="jobs-dlg-toolbar">
        <el-button type="primary" plain :loading="loadingJobs" @click="loadKbJobs">刷新任务列表</el-button>
      </div>
      <el-table
        v-loading="loadingJobs"
        :data="jobPipelineRows"
        size="small"
        stripe
        border
        class="jobs-dlg-table"
        max-height="420"
        empty-text="暂无入库任务"
      >
        <el-table-column label="任务类型" width="108" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small" effect="plain">{{ row.typeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题 / 地址" min-width="200">
          <template #default="{ row }">
            <div class="cell-title">{{ row.title }}</div>
            <div v-if="row.subtitle" class="cell-sub">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.statusType" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="expand-inner job-expand">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="任务内部编号（排障）">{{ row.job?.id }}</el-descriptions-item>
                <el-descriptions-item label="任务类型">{{ jobTaskTypeLabel(row.job?.taskType) }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag v-if="row.job" :type="jobStatusMeta(row.job.status).tag" size="small">
                    {{ jobStatusMeta(row.job.status).label }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="parseJobResultMetaRows(row.job?.resultJson).length" class="job-kv-block">
                <div class="job-section-title">结果摘要</div>
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
                <div class="job-section-title">执行阶段</div>
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
                <div class="job-section-title">任务入参</div>
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
                <el-collapse-item title="原始入参（排障）" :name="'p-' + row.key">
                  <el-scrollbar max-height="120px">
                    <pre class="json-pre">{{ prettyJson(row.job?.payloadJson) }}</pre>
                  </el-scrollbar>
                </el-collapse-item>
                <el-collapse-item title="原始返回（排障）" :name="'r-' + row.key">
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

    <el-drawer v-model="ingestOpen" title="上传与入库" size="480px" destroy-on-close @closed="resetIngestForm">
      <el-radio-group v-model="ingestType" class="ingest-type">
        <el-radio-button label="crawl">网页爬取</el-radio-button>
        <el-radio-button label="upload">本地上传</el-radio-button>
        <el-radio-button label="paste">Markdown 任务</el-radio-button>
      </el-radio-group>
      <p class="ingest-tip">{{ ingestTip }}</p>

      <el-form label-width="108px" class="ingest-form">
        <template v-if="ingestType === 'crawl'">
          <el-form-item label="网页 URL" required>
            <el-input v-model="crawlForm.url" placeholder="https://example.com/page" type="url" />
          </el-form-item>
        </template>
        <template v-else-if="ingestType === 'upload'">
          <el-form-item label="选择文件" required>
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="onPickUploadFile"
              :on-remove="() => (uploadFile = null)"
              accept=".txt,.md,.pdf,.doc,.docx,.html,.htm"
            >
              <el-button type="primary" plain>选择文件</el-button>
            </el-upload>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="文件名" required>
            <el-input v-model="pasteForm.originalFilename" placeholder="如 notes.md" />
          </el-form-item>
          <el-form-item label="内容类型">
            <el-input v-model="pasteForm.contentType" placeholder="可选，如 text/markdown" />
          </el-form-item>
          <el-form-item label="Markdown">
            <el-input v-model="pasteForm.markdownContent" type="textarea" :rows="8" placeholder="粘贴正文" />
          </el-form-item>
        </template>

        <el-form-item label="分片策略">
          <el-select v-model="ingestChunkStrategy" clearable placeholder="使用知识库默认" style="width: 100%">
            <el-option label="不分片" :value="0" />
            <el-option label="固定字数" :value="1" />
            <el-option label="语义段落" :value="2" />
            <el-option label="滑动窗口" :value="3" />
            <el-option label="自定义（预留）" :value="99" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="accent-btn" :loading="ingestSubmitting" @click="submitIngest">提交</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>

    <el-dialog v-model="chunkDlg" title="编辑分片" width="720px" destroy-on-close @closed="editingChunk = null">
      <el-input v-model="chunkEditText" type="textarea" :rows="14" />
      <template #footer>
        <el-button @click="chunkDlg = false">取消</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="saveChunk">保存</el-button>
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
          加载分片
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
        <el-table-column label="命中" width="72" align="right">
          <template #default="{ row: c }">{{ c.hitCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="220">
          <template #default="{ row: c }">{{ preview(c.content) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建" width="156" />
        <el-table-column label="操作" width="88" align="center">
          <template #default="{ row: c }">
            <el-button link type="primary" size="small" @click="openChunkEditFromDrawer(c)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else-if="chunksDrawerDoc"
        description="点击「加载分片」查看内容"
        :image-size="56"
      />
    </el-drawer>

    <el-dialog v-model="docSettingsDlg" title="文档设置" width="520px" destroy-on-close @closed="docSettingsRow = null">
      <el-form v-if="docSettingsRow" label-width="100px">
        <el-form-item label="分类">
          <el-select v-model="docSettingsCategoryId" clearable placeholder="未分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input v-model="docSettingsScope" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docSettingsDlg = false">取消</el-button>
        <el-button type="primary" :loading="docSettingsSaving" @click="saveDocSettings">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDlg"
      :title="categoryDlgMode === 'create' ? '新建分类' : '编辑分类'"
      width="420px"
      destroy-on-close
      @closed="onCategoryDlgClosed"
    >
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="categoryForm.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDlg = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategoryDlg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import type { UploadFile } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
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

/** 知识库文档导出 Markdown 的本地文件名：去掉常见源文件后缀，避免出现「报告.doc.md」。 */
function filenameForMarkdownExport(title: string | null | undefined): string {
  let base = (title || "document").trim().replace(/[/\\?%*:|"<>]/g, "_").slice(0, 120);
  if (!base) base = "document";
  const lower = base.toLowerCase();
  if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
    return base;
  }
  const stripped = base.replace(
    /\.(docx?|pdf|html?|txt|rtf|pptx?|xlsx?|csv|json|xml|epub|odt|pages)$/i,
    "",
  );
  const root = stripped.trim() || "document";
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
const docsMainRef = ref<HTMLElement | null>(null);
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
  chunksDrawerDoc.value ? `分片 · ${chunksDrawerDoc.value.title || "文档"}` : "分片",
);

const chunksDrawerRows = computed(() => {
  const d = chunksDrawerDoc.value;
  if (!d) return [];
  return chunksByDoc.value[d.id] ?? [];
});

const ingestTip = computed(() => {
  if (ingestType.value === "crawl") return "提交后创建异步任务，抓取网页并按所选分片策略入库。";
  if (ingestType.value === "upload") return "即时解析所选文件并入库；分片可选覆盖仅对本文件生效。";
  return "创建异步「文件入库」任务；适合大段 Markdown 或后续接对象存储的流程。";
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
  if (!v) return "—";
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
    ElMessage.warning("当前未启用 Milvus 向量库，无法触发索引。");
    return;
  }
  indexingLoading.value = true;
  try {
    const r = await ragApi.enqueueRagKbIndexJob(kid.value);
    ElMessage.success("索引任务已加入队列，可在本知识库标题栏「异步任务」中查看进度");
    await loadKbJobs();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "入队失败";
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
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载分类失败";
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
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载文档失败";
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
      await ElMessageBox.confirm(`确定删除分类「${c.name}」？（分类下不能有文档）`, "确认", { type: "warning" });
      await ragApi.deleteRagKbDocumentCategory(kid.value, c.id);
      ElMessage.success("已删除");
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
    ElMessage.warning("请填写分类名称");
    return;
  }
  categorySaving.value = true;
  try {
    if (categoryDlgMode.value === "create") {
      await ragApi.createRagKbDocumentCategory(kid.value, { name, sortOrder: categoryForm.sortOrder });
      ElMessage.success("已创建");
    } else if (categoryEditingId.value != null) {
      await ragApi.updateRagKbDocumentCategory(kid.value, categoryEditingId.value, {
        name,
        sortOrder: categoryForm.sortOrder,
      });
      ElMessage.success("已保存");
    }
    categoryDlg.value = false;
    await loadCategories();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
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
    ElMessage.success("已保存");
    docSettingsDlg.value = false;
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
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
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "加载失败";
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
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "下载失败";
    ElMessage.error(msg);
  }
}

async function batchRemoveDocs() {
  const rows = selectedDocs.value;
  if (!rows.length) return;
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 篇文档？`, "确认", { type: "warning" });
    for (const d of rows) {
      await ragApi.deleteRagKbDocument(kid.value, d.id);
    }
    ElMessage.success("已删除");
    docTableRef.value?.clearSelection();
    await refreshDocs();
  } catch {
    /* cancel */
  }
}

function openIngest() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning("当前未启用 Milvus 向量库，上传与入库暂不可用。");
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
    ElMessage.warning("当前未启用 Milvus 向量库，无法提交入库。");
    return;
  }
  const cs = ingestChunkStrategy.value;
  ingestSubmitting.value = true;
  try {
    if (ingestType.value === "crawl") {
      const u = crawlForm.url.trim();
      if (!u) {
        ElMessage.warning("请填写网页地址");
        return;
      }
      const r = await ragApi.enqueueUrlImportJob(kid.value, u, cs);
      ElMessage.success("已创建爬取任务，可在本知识库标题栏「异步任务」中查看进度");
    } else if (ingestType.value === "upload") {
      const f = uploadFile.value;
      if (!f) {
        ElMessage.warning("请选择文件");
        return;
      }
      await ragApi.uploadRagKbDocument(kid.value, f, cs);
      ElMessage.success("已上传并入库");
    } else {
      const name = pasteForm.originalFilename.trim();
      if (!name) {
        ElMessage.warning("请填写文件名");
        return;
      }
      const r = await ragApi.enqueueFileIngestJob(kid.value, {
        originalFilename: name,
        contentType: pasteForm.contentType.trim() || undefined,
        markdownContent: pasteForm.markdownContent.trim() || undefined,
        chunkStrategy: cs,
      });
      ElMessage.success("已创建文件任务，可在本知识库标题栏「异步任务」中查看进度");
    }
    ingestOpen.value = false;
    await refreshDocsAndKbJobs();
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "提交失败";
    ElMessage.error(msg);
  } finally {
    ingestSubmitting.value = false;
  }
}

async function removeDoc(row: RagDocumentAdminRow) {
  try {
    await ElMessageBox.confirm(`确定逻辑删除文档「${row.title}」及其分片？`, "确认", { type: "warning" });
    await ragApi.deleteRagKbDocument(kid.value, row.id);
    ElMessage.success("已删除");
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
    ElMessage.success("已更新分片");
    chunkDlg.value = false;
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
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
 * 表高取自 docs-main 减去工具栏、底栏与列 gap，须观察 docs-main 本身。
 * 若观察包裹 el-table 的 docs-table-wrap 并把其高度回写为 :height，表格渲染略大于槽位时会撑高该 wrap，
 * ResizeObserver 再读到更大高度，形成无限增高，分页器被顶出视口。
 */
function bindDocTableResize() {
  unbindDocTableResize();
  const main = docsMainRef.value;
  if (!main || typeof ResizeObserver === "undefined") {
    return;
  }
  // docs-main 为 column + gap:12px，toolbar / table-wrap / footer 三行之间共 2 段 gap
  const MAIN_COLUMN_GAP_PX = 24;
  const apply = () => {
    const tb = main.querySelector(".docs-toolbar") as HTMLElement | null;
    const ft = main.querySelector(".docs-footer") as HTMLElement | null;
    const mainH = main.getBoundingClientRect().height;
    const tbH = tb?.getBoundingClientRect().height ?? 0;
    const ftH = ft?.getBoundingClientRect().height ?? 0;
    const h = Math.floor(mainH - tbH - ftH - MAIN_COLUMN_GAP_PX);
    docTableBodyHeight.value = Math.max(200, h);
  };
  docTableResizeObserver = new ResizeObserver(() => apply());
  docTableResizeObserver.observe(main);
  apply();
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
  --ws-accent: #0d9488;
  --ws-accent-weak: #ccfbf1;
  --ws-card: #ffffff;
  --ws-border: #e2e8f0;
  --ws-muted: #64748b;
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
  background: linear-gradient(135deg, #0f766e, #0d9488) !important;
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
  background: #f8fafc !important;
  color: #475569;
  font-weight: 600;
}

.cell-title {
  font-weight: 500;
  color: #0f172a;
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
  background: #f8fafc;
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
  color: #475569;
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
  color: #475569;
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
  color: #475569;
  margin-top: 4px;
}

.muted {
  color: #cbd5e1;
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
  background: #fff;
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
  border-color: rgba(37, 99, 235, 0.35);
  background: #eff6ff;
}

.cat-item {
  flex: 0 0 auto;
  text-align: left;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 14px;
  color: #334155;
  cursor: pointer;
}

.cat-item:hover {
  background: #f1f5f9;
}

.cat-item.is-active {
  background: #eff6ff;
  color: #1d4ed8;
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
  background: #fff;
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
  background: #fafafa;
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
  color: #2563eb;
  font-weight: 500;
  text-decoration: none;
}

.doc-title-link:hover {
  text-decoration: underline;
}
</style>
