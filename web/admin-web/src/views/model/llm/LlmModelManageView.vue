<template>
  <div class="page">
    <el-card v-loading="metaLoading" shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div>
            <span class="title">{{ t("views.llmManage.title") }}</span>
            <p class="sub">
              {{ t("views.llmManage.sub") }}
            </p>
          </div>
        </div>
      </template>
      <el-tabs
        v-if="meta"
        :key="tabsRenderKey"
        v-model="activeKind"
        type="border-card"
        class="tabs"
      >
        <el-tab-pane v-for="t in meta.modelKindTabs" :key="t.kind" :name="t.kind" :label="t.label" lazy>
          <LlmModelKindTabPanel :tab="t" :option-lists="meta.optionLists" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as modelsApi from "../../../api/models";
import type { LlmModelAdminMetaResponse, LlmModelKindCode } from "../../../api/models";
import { apiRequestErrorMessage } from "../../../utils/apiRequestErrorMessage";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import LlmModelKindTabPanel from "./LlmModelKindTabPanel.vue";
import { ElMessage } from "element-plus";

const { t } = useI18n();
const { locale: appLocale } = storeToRefs(useUiPreferencesStore());
const metaLoading = ref(true);
const meta = ref<LlmModelAdminMetaResponse | null>(null);
const activeKind = ref<LlmModelKindCode | "">("");
/** 每次 meta 拉取成功后递增，与语言组合成 key，避免 en→zh 时 locale 先变而 meta 仍英文导致 key 不变、表格 Tab 不刷新 */
const metaRenderSeq = ref(0);
const tabsRenderKey = computed(() => `${appLocale.value}:${metaRenderSeq.value}`);

async function loadMeta() {
  metaLoading.value = true;
  try {
    const prevKind = activeKind.value;
    const data = await modelsApi.getLlmModelMeta(appLocale.value);
    if (!data?.modelKindTabs?.length) {
      ElMessage.error(t("views.llmManage.metaEmpty"));
      return;
    }
    meta.value = data;
    metaRenderSeq.value += 1;
    const kinds = meta.value.modelKindTabs.map((tab) => tab.kind);
    if (prevKind && kinds.includes(prevKind)) {
      activeKind.value = prevKind;
    } else {
      const first = meta.value.modelKindTabs[0];
      activeKind.value = first?.kind ?? "";
    }
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.llmManage.metaLoadFailed")));
  } finally {
    metaLoading.value = false;
  }
}

onMounted(() => void loadMeta());
/** flush: sync 与语言同一 tick 拉 meta，避免异步批处理后仍读到旧 locale */
watch(appLocale, () => void loadMeta(), { flush: "sync" });
</script>

<style scoped>
.page {
  padding: 0;
}

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
