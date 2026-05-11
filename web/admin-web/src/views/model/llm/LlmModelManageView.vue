<template>
  <div class="page">
    <el-card v-loading="metaLoading" shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">可配置模型</span>
            <p class="sub">
              按模型类型分 Tab；列表列与表单字段由服务端元数据下发。后端扩展模型类型、向量路径策略或对接协议枚举并更新元数据服务后，刷新本页即可同步展示，无需再改前端页面。
            </p>
          </div>
        </div>
      </template>
      <el-tabs v-if="meta" v-model="activeKind" type="border-card" class="tabs">
        <el-tab-pane v-for="t in meta.modelKindTabs" :key="t.kind" :name="t.kind" :label="t.label" lazy>
          <LlmModelKindTabPanel :tab="t" :option-lists="meta.optionLists" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import * as modelsApi from "../../../api/models";
import type { LlmModelAdminMetaResponse } from "../../../api/models";
import LlmModelKindTabPanel from "./LlmModelKindTabPanel.vue";

const metaLoading = ref(true);
const meta = ref<LlmModelAdminMetaResponse | null>(null);
const activeKind = ref("");

onMounted(async () => {
  metaLoading.value = true;
  try {
    meta.value = await modelsApi.getLlmModelMeta();
    const first = meta.value.modelKindTabs[0];
    if (first) {
      activeKind.value = first.kind;
    }
  } finally {
    metaLoading.value = false;
  }
});
</script>

<style scoped>
.page {
  padding: 0;
}

.panel {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
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
  color: #0f172a;
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}

.tabs {
  border: none;
  box-shadow: none;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
