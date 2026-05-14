<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="hdr">
        <span>{{ title }}</span>
        <el-button size="small" type="primary" plain :disabled="!text || loading" @click="copy">
          复制 JSON
        </el-button>
      </div>
    </template>
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-else-if="err" type="error" :closable="false" show-icon :title="err" />
    <el-scrollbar v-else max-height="calc(100vh - 220px)">
      <pre class="json">{{ text }}</pre>
    </el-scrollbar>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const props = defineProps<{
  title: string;
  load: () => Promise<unknown>;
}>();

const text = ref("");
const loading = ref(true);
const err = ref("");

onMounted(async () => {
  try {
    const data = await props.load();
    text.value = JSON.stringify(data, null, 2);
  } catch (e: unknown) {
    err.value = apiRequestErrorMessage(e, "加载失败");
  } finally {
    loading.value = false;
  }
});

async function copy() {
  try {
    await navigator.clipboard.writeText(text.value);
    ElMessage.success("已复制到剪贴板");
  } catch {
    ElMessage.error("复制失败");
  }
}
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

.json {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New",
    monospace;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
  padding: 4px 8px 12px 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
