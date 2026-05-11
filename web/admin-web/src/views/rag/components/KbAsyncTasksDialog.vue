<template>
  <el-dialog
    :model-value="modelValue"
    :title="`本知识库异步任务 · ${kbName}`"
    width="920px"
    destroy-on-close
    class="kb-tasks-dlg"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <p class="sub">
      仅展示与当前知识库相关的网页抓取、文件入库、索引等后台任务。点「详情」可查看阶段时间线与入参摘要。
    </p>

    <div class="filters">
      <el-select
        v-model="taskType"
        clearable
        placeholder="全部类型"
        style="width: 200px"
        @change="onFilterChange"
      >
        <el-option label="知识库索引" value="RAG_INDEX" />
        <el-option label="网页抓取入库" value="RAG_URL_IMPORT" />
        <el-option label="文件 / Markdown 入库" value="RAG_FILE_IMPORT" />
      </el-select>
      <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border empty-text="暂无任务" highlight-current-row class="task-table">
      <el-table-column label="任务类型" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ jobTaskTypeLabel(row.taskType) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="jobStatusMeta(row.status).tag" size="small">{{ jobStatusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="168">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="168">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ summarizeJobResult(row.resultJson) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="88" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailOpen" title="任务详情" width="720px" append-to-body destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="任务内部编号（排障）">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ jobTaskTypeLabel(detail.taskType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="jobStatusMeta(detail.status).tag" size="small">{{ jobStatusMeta(detail.status).label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detailResultMeta.length" class="kv-block">
          <div class="section-title">执行结果摘要</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(r, i) in detailResultMeta" :key="`rm-${i}`" :label="r.label">
              {{ r.value }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="resultSteps.length" class="timeline-wrap">
          <div class="section-title">执行阶段</div>
          <el-timeline>
            <el-timeline-item
              v-for="(s, i) in resultSteps"
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

        <div v-if="detailPayloadRows.length" class="kv-block">
          <div class="section-title">任务入参</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(r, i) in detailPayloadRows" :key="`pl-${i}`" :label="r.label">
              {{ r.value }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="raw-tools">
          <el-button size="small" type="primary" plain @click="copyResult">复制原始返回 JSON</el-button>
        </div>
        <el-collapse class="raw-collapse">
          <el-collapse-item title="原始入参（技术支持 / 排障）" name="payload">
            <el-scrollbar max-height="180px">
              <pre class="json-pre">{{ prettyJson(detail.payloadJson) }}</pre>
            </el-scrollbar>
          </el-collapse-item>
          <el-collapse-item title="原始返回（技术支持 / 排障）" name="result">
            <el-scrollbar max-height="220px">
              <pre class="json-pre">{{ prettyJson(detail.resultJson) }}</pre>
            </el-scrollbar>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";
import * as jobApi from "../../../api/jobAdmin";
import type { JobTaskAdminRow } from "../../../types/admin";
import {
  humanizeJobStepDetail,
  jobStatusMeta,
  jobStepPhaseLabel,
  jobStepStatusLabel,
  jobTaskTypeLabel,
  parseJobPayloadRows,
  parseJobResultMetaRows,
  parseResultSteps,
  prettyJson,
  summarizeJobResult,
} from "../../../utils/ragJobDisplay";

const props = defineProps<{
  modelValue: boolean;
  kbId: number;
  kbName: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();

const loading = ref(false);
const rows = ref<JobTaskAdminRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const taskType = ref<string | undefined>(undefined);

const detailOpen = ref(false);
const detail = ref<JobTaskAdminRow | null>(null);
const resultSteps = ref(parseResultSteps(undefined));

const detailPayloadRows = computed(() => parseJobPayloadRows(detail.value?.payloadJson));
const detailResultMeta = computed(() => parseJobResultMetaRows(detail.value?.resultJson));

watch(
  () => detail.value?.resultJson,
  (rj) => {
    resultSteps.value = parseResultSteps(rj);
  },
  { immediate: true },
);

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.kbId > 0) {
      page.value = 1;
      void load();
    }
  },
);

function onClosed() {
  detailOpen.value = false;
  detail.value = null;
  rows.value = [];
  total.value = 0;
}

function formatTime(v: string | null | undefined): string {
  if (!v) return "—";
  return v.replace("T", " ").slice(0, 19);
}

async function load() {
  if (!props.kbId) return;
  loading.value = true;
  try {
    const p = await jobApi.fetchJobTasks({
      page: page.value,
      size: size.value,
      taskType: taskType.value,
      ragKbId: props.kbId,
    });
    rows.value = p.records;
    total.value = p.total;
  } finally {
    loading.value = false;
  }
}

function onSizeChange() {
  page.value = 1;
  void load();
}

function onFilterChange() {
  page.value = 1;
  void load();
}

function openDetail(row: JobTaskAdminRow) {
  detail.value = row;
  detailOpen.value = true;
}

async function copyResult() {
  const t = prettyJson(detail.value?.resultJson);
  if (t === "—") return;
  try {
    await navigator.clipboard.writeText(t);
    ElMessage.success("已复制");
  } catch {
    ElMessage.warning("复制失败，请展开后在文本框内手动复制");
  }
}
</script>

<style scoped>
.sub {
  margin: 0 0 12px;
  font-size: 12px;
  color: #64748b;
}

.filters {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.task-table {
  width: 100%;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.kv-block {
  margin-top: 14px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.raw-tools {
  margin-top: 14px;
  margin-bottom: 6px;
}

.raw-collapse {
  margin-top: 0;
}

.json-pre {
  margin: 0;
  padding: 10px 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}

.timeline-wrap {
  margin: 16px 0;
}

.st {
  color: #64748b;
  font-size: 13px;
}

.td {
  font-size: 12px;
  color: #475569;
  margin-top: 4px;
  word-break: break-all;
}
</style>
