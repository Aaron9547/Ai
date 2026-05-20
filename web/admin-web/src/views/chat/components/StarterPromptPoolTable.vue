<template>
  <div class="pool">
    <div class="pool-toolbar">
      <el-button type="primary" @click="openCreate">{{ t("views.chatStarter.addPrompt") }}</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.chatStarter.poolEmpty')">
      <el-table-column prop="promptText" :label="t('views.chatStarter.colText')" min-width="280" show-overflow-tooltip />
      <el-table-column :label="t('views.chatStarter.colSource')" width="120">
        <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column prop="weight" :label="t('views.chatStarter.colWeight')" width="80" />
      <el-table-column :label="t('views.chatStarter.colEnabled')" width="72">
        <template #default="{ row }">{{ row.enabled ? t("common.yes") : t("common.no") }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" :label="t('views.chatStarter.colSort')" width="72" />
      <el-table-column prop="createdAt" :label="t('views.chatStarter.colCreatedAt')" width="168" show-overflow-tooltip />
      <el-table-column :label="t('views.chatStarter.colActions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">{{ t("common.edit") }}</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">{{ t("common.delete") }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dlg"
      :title="editId != null ? t('views.chatStarter.editDlg') : t('views.chatStarter.addDlg')"
      width="520px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item :label="t('views.chatStarter.colText')" required>
          <el-input v-model="form.promptText" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('views.chatStarter.colWeight')">
          <el-input-number v-model="form.weight" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item :label="t('views.chatStarter.colSort')">
          <el-input-number v-model="form.sortOrder" />
        </el-form-item>
        <el-form-item :label="t('views.chatStarter.colEnabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  createStarterPrompt,
  deleteStarterPrompt,
  updateStarterPrompt,
  type StarterPromptRow,
} from "@/api/chatStarterPrompt";

const props = defineProps<{
  scene: "EMPTY" | "FOLLOW_UP";
  rows: StarterPromptRow[];
  loading?: boolean;
}>();

const emit = defineEmits<{ changed: [] }>();

const { t } = useI18n();
const dlg = ref(false);
const saving = ref(false);
const editId = ref<number | null>(null);
const form = reactive({
  promptText: "",
  weight: 100,
  enabled: true,
  sortOrder: 0,
});

function sourceLabel(src: string) {
  const map: Record<string, string> = {
    MANUAL: t("views.chatStarter.sourceManual"),
    HOT_TOPIC_DAILY: t("views.chatStarter.sourceHot"),
    LLM_FOLLOW_UP: t("views.chatStarter.sourceLlm"),
  };
  return map[src] ?? src;
}

function openCreate() {
  editId.value = null;
  form.promptText = "";
  form.weight = 100;
  form.enabled = true;
  form.sortOrder = 0;
  dlg.value = true;
}

function openEdit(row: StarterPromptRow) {
  if (row.source !== "MANUAL") {
    ElMessage.warning(t("views.chatStarter.editManualOnly"));
    return;
  }
  editId.value = row.id;
  form.promptText = row.promptText;
  form.weight = row.weight;
  form.enabled = row.enabled;
  form.sortOrder = row.sortOrder;
  dlg.value = true;
}

async function save() {
  const text = form.promptText.trim();
  if (!text) {
    ElMessage.warning(t("views.chatStarter.textRequired"));
    return;
  }
  saving.value = true;
  try {
    if (editId.value != null) {
      await updateStarterPrompt(editId.value, {
        promptText: text,
        weight: form.weight,
        enabled: form.enabled,
        sortOrder: form.sortOrder,
      });
    } else {
      await createStarterPrompt({
        scene: props.scene,
        promptText: text,
        weight: form.weight,
        enabled: form.enabled,
        sortOrder: form.sortOrder,
      });
    }
    dlg.value = false;
    ElMessage.success(t("views.chatStarter.saved"));
    emit("changed");
  } catch {
    ElMessage.error(t("views.chatStarter.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function remove(row: StarterPromptRow) {
  if (row.source !== "MANUAL") {
    ElMessage.warning(t("views.chatStarter.deleteManualOnly"));
    return;
  }
  try {
    await deleteStarterPrompt(row.id);
    ElMessage.success(t("views.chatStarter.deleted"));
    emit("changed");
  } catch {
    ElMessage.error(t("views.chatStarter.saveFailed"));
  }
}
</script>

<style scoped>
.pool-toolbar {
  margin-bottom: 12px;
}
</style>
