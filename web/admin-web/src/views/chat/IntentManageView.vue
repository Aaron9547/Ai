<template>
  <div class="intent-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      title="意图识别"
      description="配置对话侧关键词命中后的处理器。当前内置「出差报销」多阶段流（与 C 端 SSE workflowStage 帧配合展示）。新建意图请选择已实现的处理器类型；扩展需后端注册。"
    />
    <el-card shadow="never" class="card">
      <template #header>
        <div class="hdr">
          <span class="title">意图列表</span>
          <div class="actions">
            <el-input-number
              v-if="isFounder"
              v-model="filterTenantId"
              :min="1"
              :step="1"
              controls-position="right"
              placeholder="按租户筛选"
              class="tid-filter"
              @change="loadAll"
            />
            <el-button type="primary" plain :loading="loading" class="users-refresh-btn" @click="loadAll">
              刷新
            </el-button>
            <el-button type="primary" @click="openCreate">新增意图</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="intents" stripe border empty-text="暂无数据">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column v-if="isFounder" prop="tenantId" label="租户" width="88" />
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column prop="displayName" label="名称" min-width="120" />
        <el-table-column prop="handlerKind" label="处理器" width="160" />
        <el-table-column label="启用" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
              {{ row.enabled === "ON" ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="优先级" width="88" align="center" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openKeywords(row)">关键词</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlgVisible" :title="dlgMode === 'create' ? '新增意图' : '编辑意图'" width="520px" destroy-on-close>
      <el-form label-width="108px">
        <el-form-item v-if="dlgMode === 'create'" label="编码" required>
          <el-input v-model="form.code" placeholder="英文 snake，租户内唯一" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="处理器" required>
          <el-select v-model="form.handlerKind" style="width: 100%">
            <el-option label="出差报销" value="TRAVEL_REIMBURSEMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledOn" active-text="开" inactive-text="关" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="isFounder && dlgMode === 'create'" label="目标租户">
          <el-input-number v-model="form.targetTenantId" :min="1" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="isFounder && dlgMode === 'edit'" label="目标租户">
          <el-input-number v-model="form.targetTenantId" :min="1" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="扩展 JSON">
          <el-input v-model="form.extraConfigJson" type="textarea" :rows="4" placeholder="可选；Coze 等密钥请走环境变量或密钥服务，勿填生产明文" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDlg">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="kwDrawer" :title="`关键词 · ${kwIntent?.displayName ?? ''}`" size="520px" destroy-on-close>
      <div class="kw-head">
        <el-button type="primary" size="small" @click="openKwCreate">新增关键词</el-button>
        <el-button text type="primary" :loading="kwLoading" @click="loadKeywords">刷新</el-button>
      </div>
      <el-table v-loading="kwLoading" :data="keywords" stripe border class="kw-table" empty-text="暂无关键词">
        <el-table-column prop="phrase" label="短语" min-width="140" />
        <el-table-column prop="keywordKind" label="类型" width="140" />
        <el-table-column label="启用" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
              {{ row.enabled === "ON" ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="72" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openKwEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onKwDelete(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="kwDlg" :title="kwDlgMode === 'create' ? '新增关键词' : '编辑关键词'" width="440px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="短语" required>
          <el-input v-model="kwForm.phrase" placeholder="子串包含匹配" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="kwForm.keywordKind" style="width: 100%">
            <el-option label="首轮触发" value="TRIGGER" />
            <el-option label="行程续办" value="PLAN_CONTINUE" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="kwForm.enabledOn" active-text="开" inactive-text="关" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="kwForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="isFounder" label="目标租户">
          <el-input-number v-model="kwForm.targetTenantId" :min="1" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kwDlg = false">取消</el-button>
        <el-button type="primary" :loading="kwSaving" @click="submitKw">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import * as chatIntentApi from "@/api/chatIntent";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { readJwtTmr } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const loading = ref(false);
const intents = ref<chatIntentApi.IntentRow[]>([]);
const filterTenantId = ref<number | undefined>(undefined);

const isFounder = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return true;
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const dlgVisible = ref(false);
const dlgMode = ref<"create" | "edit">("create");
const saving = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  code: "",
  displayName: "",
  description: "",
  handlerKind: "TRAVEL_REIMBURSEMENT" as chatIntentApi.ChatIntentHandlerKind,
  enabledOn: false,
  sortOrder: 0,
  extraConfigJson: "",
  targetTenantId: 1 as number | undefined,
});

const kwDrawer = ref(false);
const kwLoading = ref(false);
const keywords = ref<chatIntentApi.KeywordRow[]>([]);
const kwIntent = ref<chatIntentApi.IntentRow | null>(null);
const kwDlg = ref(false);
const kwDlgMode = ref<"create" | "edit">("create");
const kwSaving = ref(false);
const kwEditingId = ref<number | null>(null);
const kwForm = reactive({
  phrase: "",
  keywordKind: "TRIGGER" as chatIntentApi.ChatIntentKeywordKind,
  enabledOn: true,
  sortOrder: 0,
  targetTenantId: 1 as number | undefined,
});

async function loadAll() {
  loading.value = true;
  try {
    intents.value = await chatIntentApi.listIntents(filterTenantId.value);
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "加载失败"));
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.code = "";
  form.displayName = "";
  form.description = "";
  form.handlerKind = "TRAVEL_REIMBURSEMENT";
  form.enabledOn = false;
  form.sortOrder = 0;
  form.extraConfigJson = "";
  form.targetTenantId = 1;
}

function openCreate() {
  dlgMode.value = "create";
  editingId.value = null;
  resetForm();
  dlgVisible.value = true;
}

function openEdit(row: chatIntentApi.IntentRow) {
  dlgMode.value = "edit";
  editingId.value = row.id;
  form.code = row.code;
  form.displayName = row.displayName;
  form.description = row.description ?? "";
  form.handlerKind = row.handlerKind;
  form.enabledOn = row.enabled === "ON";
  form.sortOrder = row.sortOrder;
  form.extraConfigJson = row.extraConfigJson ?? "";
  form.targetTenantId = row.tenantId;
  dlgVisible.value = true;
}

async function submitDlg() {
  saving.value = true;
  try {
    const en: chatIntentApi.ToggleState = form.enabledOn ? "ON" : "OFF";
    if (dlgMode.value === "create") {
      await chatIntentApi.createIntent({
        code: form.code.trim(),
        displayName: form.displayName.trim(),
        description: form.description || null,
        handlerKind: form.handlerKind,
        enabled: en,
        sortOrder: form.sortOrder,
        extraConfigJson: form.extraConfigJson?.trim() || null,
        targetTenantId: isFounder.value ? form.targetTenantId : null,
      });
      ElMessage.success("已创建");
    } else if (editingId.value != null) {
      await chatIntentApi.updateIntent(editingId.value, {
        displayName: form.displayName.trim(),
        description: form.description || null,
        handlerKind: form.handlerKind,
        enabled: en,
        sortOrder: form.sortOrder,
        extraConfigJson: form.extraConfigJson?.trim() || null,
        targetTenantId: isFounder.value ? form.targetTenantId : null,
      });
      ElMessage.success("已保存");
    }
    dlgVisible.value = false;
    await loadAll();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

async function onDelete(row: chatIntentApi.IntentRow) {
  try {
    await ElMessageBox.confirm(`确定删除意图「${row.displayName}」及其全部关键词？`, "确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await chatIntentApi.deleteIntent(row.id, isFounder.value ? row.tenantId : undefined);
    ElMessage.success("已删除");
    await loadAll();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
  }
}

async function openKeywords(row: chatIntentApi.IntentRow) {
  kwIntent.value = row;
  kwDrawer.value = true;
  await loadKeywords();
}

async function loadKeywords() {
  if (!kwIntent.value) return;
  kwLoading.value = true;
  try {
    keywords.value = await chatIntentApi.listKeywords(
      kwIntent.value.id,
      isFounder.value ? kwIntent.value.tenantId : undefined,
    );
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "加载关键词失败"));
  } finally {
    kwLoading.value = false;
  }
}

function openKwCreate() {
  kwDlgMode.value = "create";
  kwEditingId.value = null;
  kwForm.phrase = "";
  kwForm.keywordKind = "TRIGGER";
  kwForm.enabledOn = true;
  kwForm.sortOrder = 0;
  kwForm.targetTenantId = kwIntent.value?.tenantId ?? 1;
  kwDlg.value = true;
}

function openKwEdit(row: chatIntentApi.KeywordRow) {
  kwDlgMode.value = "edit";
  kwEditingId.value = row.id;
  kwForm.phrase = row.phrase;
  kwForm.keywordKind = row.keywordKind;
  kwForm.enabledOn = row.enabled === "ON";
  kwForm.sortOrder = row.sortOrder;
  kwForm.targetTenantId = kwIntent.value?.tenantId ?? 1;
  kwDlg.value = true;
}

async function submitKw() {
  if (!kwIntent.value) return;
  kwSaving.value = true;
  try {
    const en: chatIntentApi.ToggleState = kwForm.enabledOn ? "ON" : "OFF";
    const tid = isFounder.value ? kwForm.targetTenantId : undefined;
    if (kwDlgMode.value === "create") {
      await chatIntentApi.addKeyword(
        kwIntent.value.id,
        {
          phrase: kwForm.phrase.trim(),
          keywordKind: kwForm.keywordKind,
          enabled: en,
          sortOrder: kwForm.sortOrder,
        },
        tid,
      );
      ElMessage.success("已添加");
    } else if (kwEditingId.value != null) {
      await chatIntentApi.updateKeyword(
        kwIntent.value.id,
        kwEditingId.value,
        {
          phrase: kwForm.phrase.trim(),
          keywordKind: kwForm.keywordKind,
          enabled: en,
          sortOrder: kwForm.sortOrder,
        },
        tid,
      );
      ElMessage.success("已保存");
    }
    kwDlg.value = false;
    await loadKeywords();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    kwSaving.value = false;
  }
}

async function onKwDelete(row: chatIntentApi.KeywordRow) {
  if (!kwIntent.value) return;
  try {
    await ElMessageBox.confirm(`删除关键词「${row.phrase}」？`, "确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await chatIntentApi.deleteKeyword(
      kwIntent.value.id,
      row.id,
      isFounder.value ? kwIntent.value.tenantId : undefined,
    );
    ElMessage.success("已删除");
    await loadKeywords();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
  }
}

onMounted(() => {
  void loadAll();
});
</script>

<style scoped>
.intent-page {
  padding: 12px 16px 24px;
}
.hint {
  margin-bottom: 12px;
}
.card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  font-weight: 600;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.tid-filter {
  width: 140px;
}
.users-refresh-btn {
  flex-shrink: 0;
  min-width: 5.5rem;
}
.kw-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.kw-table {
  margin-top: 4px;
}
</style>
