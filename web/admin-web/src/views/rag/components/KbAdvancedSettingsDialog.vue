<template>
  <el-dialog
    :model-value="modelValue"
    :title="titleText"
    width="720px"
    destroy-on-close
    class="kb-adv-dlg"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert type="info" show-icon :closable="false" class="hint-alert">
      配置对话侧语言模型、向量嵌入模型（Milvus 入库/检索必填）与默认分片策略；文档与入库仍在当前知识库主区操作。
    </el-alert>

    <el-alert
      v-if="capabilitiesLoaded && !vectorStoreMilvus"
      type="warning"
      show-icon
      :closable="false"
      class="hint-alert"
      title="向量库未启用"
      description="未连接 Milvus 时无法打开「参与对话检索」与依赖向量的入库；请在 application.yml 中配置 ai.providers.vector-store=milvus。"
    />

    <div v-loading="loadingKb">
      <el-form v-if="kbRow" :model="settings" label-width="140px" class="form-block">
        <el-form-item label="名称">
          <el-input v-model="settings.name" style="max-width: 420px" />
        </el-form-item>
        <el-form-item label="默认分片策略">
          <el-select v-model="settings.defaultChunkStrategyCode" style="width: 280px">
            <el-option label="不分片（整篇一块）" :value="0" />
            <el-option label="固定字数" :value="1" />
            <el-option label="语义段落" :value="2" />
            <el-option label="滑动窗口" :value="3" />
            <el-option label="自定义（预留）" :value="99" />
          </el-select>
        </el-form-item>
        <el-form-item label="固定分片目标字数">
          <el-input-number v-model="settings.chunkFixedChars" :min="200" :max="8000" />
        </el-form-item>
        <el-form-item label="滑动重叠字数">
          <el-input-number v-model="settings.chunkSlideOverlap" :min="0" :max="2000" />
        </el-form-item>
        <el-form-item label="参与对话检索">
          <el-switch
            v-model="settings.chatRetrievalEnabled"
            inline-prompt
            active-text="开启"
            inactive-text="关闭"
          />
          <p class="field-hint">关闭后，该知识库不再参与 C 端对话的 RAG 合并检索（文档分片上的「可检索」开关仍独立生效）。</p>
        </el-form-item>
        <el-form-item label="对话向量相似度下限">
          <el-input-number
            v-model="settings.chatVectorMinCosineScore"
            :min="0"
            :max="1"
            :step="0.01"
            :precision="4"
            style="width: 200px"
          />
          <p class="field-hint">
            仅作用于本库 Milvus COSINE 召回：低于该分数的命中不写参考片段与引用。默认 0.65；填 0
            表示本库不做向量分数过滤。多库同时参与对话时，每个库各自使用该阈值。
          </p>
        </el-form-item>
        <el-form-item label="对话绑定（语言模型）">
          <el-select
            v-model="settings.assignedLlmModelId"
            clearable
            filterable
            placeholder="不绑定"
            style="width: 360px"
            :loading="loadingModels"
          >
            <el-option
              v-for="m in languageModels"
              :key="m.id"
              :label="`${m.displayName} (${m.alias})`"
              :value="m.id"
            />
          </el-select>
          <el-checkbox v-model="settings.clearAssignedLlmModel" class="chk">清空</el-checkbox>
          <p class="field-hint">可选：与对话编排相关的语言模型（类型须为「语言」）。</p>
        </el-form-item>
        <el-form-item label="向量模型（嵌入）">
          <el-select
            v-model="settings.assignedEmbeddingModelId"
            clearable
            filterable
            placeholder="请选择向量模型"
            style="width: 360px"
            :loading="loadingModels"
          >
            <el-option
              v-for="m in vectorModels"
              :key="m.id"
              :label="`${m.displayName} (${m.alias}) · ${m.openaiModelId}`"
              :value="m.id"
            />
          </el-select>
          <el-checkbox v-model="settings.clearAssignedEmbeddingModel" class="chk">清空</el-checkbox>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :loading="savingSettings" :disabled="!kbRow" @click="saveSettings">保存设置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, reactive, ref, watch } from "vue";
import * as ragApi from "../../../api/ragAdmin";
import * as modelsApi from "../../../api/models";
import type { LlmModelAdminView } from "../../../api/models";
import type { RagKnowledgeBaseRow } from "../../../types/admin";

const props = defineProps<{
  modelValue: boolean;
  kbId: number;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "saved"): void;
}>();

const loadingKb = ref(false);
const capabilitiesLoaded = ref(false);
const vectorStoreMilvus = ref(true);
const loadingModels = ref(false);
const savingSettings = ref(false);
const allModels = ref<LlmModelAdminView[]>([]);
const languageModels = computed(() => allModels.value.filter((m) => (m.modelKind ?? "LANGUAGE") === "LANGUAGE"));
const vectorModels = computed(() => allModels.value.filter((m) => m.modelKind === "VECTOR"));
const kbRow = ref<RagKnowledgeBaseRow | null>(null);

const titleText = computed(() =>
  kbRow.value ? `高级设置 · ${kbRow.value.name}` : "高级设置",
);

const settings = reactive({
  name: "",
  defaultChunkStrategyCode: 1,
  chunkFixedChars: 800,
  chunkSlideOverlap: 120,
  chatRetrievalEnabled: true,
  assignedLlmModelId: undefined as number | undefined,
  clearAssignedLlmModel: false,
  assignedEmbeddingModelId: undefined as number | undefined,
  clearAssignedEmbeddingModel: false,
  chatVectorMinCosineScore: 0.65,
});

function strategyNameToCode(name?: string): number {
  const map: Record<string, number> = {
    NONE: 0,
    FIXED_CHAR: 1,
    SEMANTIC: 2,
    SLIDING_WINDOW: 3,
    CUSTOM: 99,
  };
  return name && map[name] != null ? map[name] : 1;
}

async function loadKb() {
  if (!props.modelValue || !Number.isFinite(props.kbId) || props.kbId <= 0) return;
  loadingKb.value = true;
  try {
    const cap = await ragApi.fetchRagCapabilities();
    vectorStoreMilvus.value = cap.vectorStoreMilvus;
    capabilitiesLoaded.value = true;
    const list = await ragApi.fetchRagKbs();
    kbRow.value = list.find((x) => x.id === props.kbId) ?? null;
    if (!kbRow.value) {
      ElMessage.error("未找到该知识库");
      emit("update:modelValue", false);
      return;
    }
    settings.name = kbRow.value.name;
    settings.defaultChunkStrategyCode = strategyNameToCode(kbRow.value.defaultChunkStrategy);
    settings.chunkFixedChars = kbRow.value.chunkFixedChars ?? 800;
    settings.chunkSlideOverlap = kbRow.value.chunkSlideOverlap ?? 120;
    settings.chatRetrievalEnabled = kbRow.value.chatRetrievalEnabled !== "OFF";
    settings.assignedLlmModelId = kbRow.value.assignedLlmModelId ?? undefined;
    settings.clearAssignedLlmModel = false;
    settings.assignedEmbeddingModelId = kbRow.value.assignedEmbeddingModelId ?? undefined;
    settings.clearAssignedEmbeddingModel = false;
    settings.chatVectorMinCosineScore =
      kbRow.value.chatVectorMinCosineScore != null ? kbRow.value.chatVectorMinCosineScore : 0.65;
  } finally {
    loadingKb.value = false;
  }
}

async function loadModels() {
  loadingModels.value = true;
  try {
    const [lang, vec] = await Promise.all([
      modelsApi.listLlmModels({ modelKind: "LANGUAGE" }),
      modelsApi.listLlmModels({ modelKind: "VECTOR" }),
    ]);
    allModels.value = [...lang, ...vec];
  } finally {
    loadingModels.value = false;
  }
}

async function saveSettings() {
  if (!kbRow.value) return;
  if (settings.chatRetrievalEnabled && !vectorStoreMilvus.value) {
    ElMessage.warning("未启用 Milvus 时无法打开参与对话检索。");
    return;
  }
  savingSettings.value = true;
  try {
    await ragApi.patchRagKbSettings(props.kbId, {
      name: settings.name.trim(),
      defaultChunkStrategyCode: settings.defaultChunkStrategyCode,
      chunkFixedChars: settings.chunkFixedChars,
      chunkSlideOverlap: settings.chunkSlideOverlap,
      chatRetrievalEnabled: settings.chatRetrievalEnabled,
      assignedLlmModelId: settings.assignedLlmModelId ?? null,
      clearAssignedLlmModel: settings.clearAssignedLlmModel,
      assignedEmbeddingModelId: settings.assignedEmbeddingModelId ?? null,
      clearAssignedEmbeddingModel: settings.clearAssignedEmbeddingModel,
      chatVectorMinCosineScore: settings.chatVectorMinCosineScore,
    });
    ElMessage.success("已保存");
    await loadKb();
    emit("saved");
  } catch (e: unknown) {
    const msg = e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : "保存失败";
    ElMessage.error(msg);
  } finally {
    savingSettings.value = false;
  }
}

watch(
  () => [props.modelValue, props.kbId] as const,
  ([open]) => {
    if (open) {
      void loadKb();
      void loadModels();
    }
  },
);
</script>

<style scoped>
.hint-alert {
  margin-bottom: 12px;
}

.form-block {
  margin-top: 8px;
  max-height: min(62vh, 520px);
  overflow-y: auto;
  padding-right: 4px;
}

.chk {
  margin-left: 12px;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.45;
}
</style>
