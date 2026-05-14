<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">文档入库</span>
            <p class="sub">
              选择知识库后提交网页或文本任务；网页流水线会抓取页面、转为 Markdown、按策略分片并写入向量占位。也可粘贴 Markdown 走文件入库任务。
            </p>
          </div>
          <el-button type="primary" plain :loading="loadingKbs" @click="loadKbs">刷新知识库</el-button>
        </div>
      </template>

      <el-form label-width="100px" class="kb-row">
        <el-form-item label="知识库" required>
          <el-select v-model="kbId" placeholder="请选择" filterable style="width: 320px" :loading="loadingKbs">
            <el-option v-for="r in kbs" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert type="info" show-icon :closable="false" class="hint">
        分片策略默认沿用知识库「高级设置」中的默认策略；网页任务可在此单次覆盖。任务完成后在知识库页的「异步任务」弹窗中可查看阶段时间线。
      </el-alert>

      <el-divider content-position="left">网页地址</el-divider>
      <el-form :model="urlForm" label-width="120px" @submit.prevent>
        <el-form-item label="网页地址" required>
          <el-input v-model="urlForm.url" placeholder="https://example.com/docs/intro" style="max-width: 560px" />
        </el-form-item>
        <el-form-item label="本次分片策略">
          <el-select v-model="urlForm.chunkStrategy" clearable placeholder="使用知识库默认" style="width: 280px">
            <el-option label="不分片" :value="0" />
            <el-option label="固定字数" :value="1" />
            <el-option label="语义段落" :value="2" />
            <el-option label="滑动窗口" :value="3" />
            <el-option label="自定义（预留）" :value="99" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="urlSubmitting" :disabled="!kbId" @click="submitUrl">提交网页入库任务</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">文件 / 粘贴正文</el-divider>
      <el-form :model="fileForm" label-width="120px" @submit.prevent>
        <el-form-item label="文件名" required>
          <el-input v-model="fileForm.originalFilename" placeholder="例如 notes.md" style="max-width: 400px" />
        </el-form-item>
        <el-form-item label="内容类型">
          <el-input v-model="fileForm.contentType" placeholder="可选，如 text/markdown" style="max-width: 400px" />
        </el-form-item>
        <el-form-item label="Markdown 正文">
          <el-input
            v-model="fileForm.markdownContent"
            type="textarea"
            :rows="8"
            placeholder="可选；填写后将按知识库默认策略分片入库。不填则仅创建占位任务。"
            style="max-width: 720px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="fileSubmitting" :disabled="!kbId" @click="submitFile">
            提交文件入库任务
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import * as ragApi from "../../api/ragAdmin";
import type { RagKnowledgeBaseRow } from "../../types/admin";

const route = useRoute();
const loadingKbs = ref(false);
const kbs = ref<RagKnowledgeBaseRow[]>([]);
const kbId = ref<number | undefined>(undefined);

const urlForm = reactive({ url: "", chunkStrategy: undefined as number | undefined });
const fileForm = reactive({ originalFilename: "", contentType: "", markdownContent: "" });
const urlSubmitting = ref(false);
const fileSubmitting = ref(false);

async function loadKbs() {
  loadingKbs.value = true;
  try {
    kbs.value = await ragApi.fetchRagKbs();
    const qkb = route.query.kb;
    if (typeof qkb === "string" && qkb) {
      const n = Number.parseInt(qkb, 10);
      if (Number.isFinite(n)) kbId.value = n;
    }
    if (kbId.value == null && kbs.value.length > 0) {
      kbId.value = kbs.value[0].id;
    }
  } finally {
    loadingKbs.value = false;
  }
}

async function submitUrl() {
  if (!kbId.value) {
    ElMessage.warning("请先选择知识库");
    return;
  }
  const u = urlForm.url.trim();
  if (!u) {
    ElMessage.warning("请填写网页地址");
    return;
  }
  urlSubmitting.value = true;
  try {
    const r = await ragApi.enqueueUrlImportJob(kbId.value, u, urlForm.chunkStrategy);
    ElMessage.success(`已创建任务（编号 ${r.jobTaskId}），可在知识库页标题栏「异步任务」中查看进度`);
    urlForm.url = "";
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "提交失败";
    ElMessage.error(msg);
  } finally {
    urlSubmitting.value = false;
  }
}

async function submitFile() {
  if (!kbId.value) {
    ElMessage.warning("请先选择知识库");
    return;
  }
  const name = fileForm.originalFilename.trim();
  if (!name) {
    ElMessage.warning("请填写文件名");
    return;
  }
  fileSubmitting.value = true;
  try {
    const r = await ragApi.enqueueFileIngestJob(kbId.value, {
      originalFilename: name,
      contentType: fileForm.contentType.trim() || undefined,
      markdownContent: fileForm.markdownContent.trim() || undefined,
    });
    ElMessage.success(`已创建任务（编号 ${r.jobTaskId}），可在知识库页标题栏「异步任务」中查看进度`);
    fileForm.originalFilename = "";
    fileForm.contentType = "";
    fileForm.markdownContent = "";
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "提交失败";
    ElMessage.error(msg);
  } finally {
    fileSubmitting.value = false;
  }
}

onMounted(() => {
  void loadKbs();
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.hdr {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.kb-row {
  margin-bottom: 8px;
}

.hint {
  margin-bottom: 16px;
}
</style>
