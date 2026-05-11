<template>
  <div class="page">
    <header class="head">
      <h1>敏感词管理</h1>
      <p class="hint">
        平台强制词库对<strong>所有租户</strong>的对话校验生效；仅<strong>创始人</strong>可维护平台词与导入平台词。租户扩展词按<strong>数据租户</strong>隔离；创始人请在<strong>租户扩展词库卡片内、词表下方</strong>选择「维护目标租户」（未选时使用当前工作区租户）。
      </p>
    </header>

    <div class="split">
      <el-card shadow="never" class="split-card">
        <template #header>
          <span>平台强制词库（全租户）</span>
          <el-tag v-if="!isFounder" type="info" size="small" class="tag-ro">只读</el-tag>
        </template>
        <div class="pane-inner">
          <div v-if="isFounder" class="toolbar">
            <el-input v-model="platformWord" placeholder="新增一个词" clearable class="inp-short" />
            <el-button type="primary" :loading="saving" @click="onAddPlatform">添加</el-button>
            <el-button @click="platformImportOpen = true">批量导入</el-button>
          </div>
          <div class="toolbar toolbar--secondary">
            <el-input
              v-model="platformQInput"
              placeholder="关键词筛选（包含匹配）"
              clearable
              class="inp-short"
              @keyup.enter="applyPlatformSearch"
            />
            <el-button plain @click="applyPlatformSearch">筛选</el-button>
          </div>
          <div class="table-scroll">
            <el-table
              v-loading="platformLoading"
              :data="platformTerms"
              stripe
              empty-text="暂无平台词（请创始人配置）"
            >
              <el-table-column prop="word" label="词" min-width="120" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="创建时间" width="168" :formatter="fmtTimeCol" />
              <el-table-column v-if="isFounder" label="操作" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="onDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="pane-footer">
            <el-pagination
              v-model:current-page="platformPage"
              v-model:page-size="platformSize"
              layout="total, sizes, prev, pager, next"
              :total="platformTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              small
              @current-change="loadPlatform"
              @size-change="onPlatformSizeChange"
            />
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="split-card">
        <template #header>
          <span>租户扩展词库</span>
        </template>
        <div class="pane-inner">
          <div class="toolbar">
            <el-input v-model="tenantWord" placeholder="新增一个词" clearable class="inp-short" />
            <el-button type="primary" :loading="saving" @click="onAddTenant">添加</el-button>
            <el-button @click="tenantImportOpen = true">批量导入</el-button>
          </div>
          <div class="toolbar toolbar--secondary">
            <el-input
              v-model="tenantQInput"
              placeholder="关键词筛选（包含匹配）"
              clearable
              class="inp-short"
              @keyup.enter="applyTenantSearch"
            />
            <el-button plain @click="applyTenantSearch">筛选</el-button>
          </div>
          <div class="table-scroll">
            <el-table v-loading="tenantLoading" :data="tenantTerms" stripe empty-text="暂无该租户扩展词">
              <el-table-column prop="word" label="词" min-width="120" show-overflow-tooltip />
              <el-table-column label="租户编码" min-width="120" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.tenantCode?.trim() ? row.tenantCode : "—" }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="168" :formatter="fmtTimeCol" />
              <el-table-column label="操作" width="88" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="onDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-if="isFounder" class="toolbar toolbar--tenant-target">
            <span class="field-label">维护目标租户</span>
            <el-select
              v-model="extensionTenantId"
              class="tenant-select"
              clearable
              filterable
              placeholder="未选则用当前工作区租户"
              @change="onExtensionTenantChange"
            >
              <el-option
                v-for="t in tenantOptions"
                :key="t.id"
                :label="`${t.name} (${t.code})`"
                :value="t.id"
              />
            </el-select>
          </div>
          <div class="pane-footer">
            <el-pagination
              v-model:current-page="tenantPage"
              v-model:page-size="tenantSize"
              layout="total, sizes, prev, pager, next"
              :total="tenantTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              small
              @current-change="loadTenant"
              @size-change="onTenantSizeChange"
            />
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="platformImportOpen" title="批量导入 — 平台强制词库" width="520px" destroy-on-close>
      <p class="dlg-hint">每行一条，或使用逗号、顿号、分号分隔。仅创始人可导入到平台池。</p>
      <el-input v-model="platformImportText" type="textarea" :rows="10" placeholder="粘贴词表…" />
      <template #footer>
        <el-button @click="platformImportOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="onImportPlatform">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tenantImportOpen" title="批量导入 — 租户扩展词库" width="520px" destroy-on-close>
      <p class="dlg-hint">每行一条，或使用逗号、顿号、分号分隔。导入目标与词表下方的「维护目标租户」选择一致。</p>
      <el-input v-model="tenantImportText" type="textarea" :rows="10" placeholder="粘贴词表…" />
      <template #footer>
        <el-button @click="tenantImportOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="onImportTenant">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";
import * as chatAdmin from "../../api/chatAdmin";
import type { SensitiveTermRow } from "../../api/chatAdmin";
import { useAdminFounderTenantOptions } from "../../composables/useAdminFounderTenantOptions";

const { isFounder, tenantOptions } = useAdminFounderTenantOptions();

const extensionTenantId = ref<number | undefined>(undefined);

const platformTerms = ref<SensitiveTermRow[]>([]);
const platformPage = ref(1);
const platformSize = ref(20);
const platformTotal = ref(0);
const platformQ = ref("");
const platformQInput = ref("");
const platformLoading = ref(false);

const tenantTerms = ref<SensitiveTermRow[]>([]);
const tenantPage = ref(1);
const tenantSize = ref(20);
const tenantTotal = ref(0);
const tenantQ = ref("");
const tenantQInput = ref("");
const tenantLoading = ref(false);

const platformWord = ref("");
const tenantWord = ref("");
const saving = ref(false);
const importing = ref(false);
const platformImportOpen = ref(false);
const tenantImportOpen = ref(false);
const platformImportText = ref("");
const tenantImportText = ref("");

function fmtTimeCol(_row: unknown, _col: unknown, cellValue: string) {
  if (!cellValue) return "—";
  const d = new Date(cellValue);
  if (Number.isNaN(d.getTime())) return cellValue;
  return d.toLocaleString("zh-CN", { hour12: false });
}

function tenantPageOpts() {
  const o: { q?: string; filterTenantId?: number } = {};
  if (tenantQ.value.trim()) {
    o.q = tenantQ.value.trim();
  }
  if (extensionTenantId.value != null) {
    o.filterTenantId = extensionTenantId.value;
  }
  return o;
}

function tenantWriteTarget(): number | undefined {
  return isFounder.value && extensionTenantId.value != null ? extensionTenantId.value : undefined;
}

async function loadPlatform() {
  platformLoading.value = true;
  try {
    const q = platformQ.value.trim() || undefined;
    const data = await chatAdmin.fetchSensitiveTermsPlatformPage(platformPage.value, platformSize.value, q);
    platformTerms.value = data.records ?? [];
    platformTotal.value = data.total ?? 0;
  } catch {
    platformTerms.value = [];
    platformTotal.value = 0;
    ElMessage.error("平台词列表加载失败");
  } finally {
    platformLoading.value = false;
  }
}

async function loadTenant() {
  tenantLoading.value = true;
  try {
    const data = await chatAdmin.fetchSensitiveTermsTenantPage(
      tenantPage.value,
      tenantSize.value,
      tenantPageOpts(),
    );
    tenantTerms.value = data.records ?? [];
    tenantTotal.value = data.total ?? 0;
  } catch {
    tenantTerms.value = [];
    tenantTotal.value = 0;
    ElMessage.error("租户词列表加载失败");
  } finally {
    tenantLoading.value = false;
  }
}

function applyPlatformSearch() {
  platformQ.value = platformQInput.value;
  platformPage.value = 1;
  void loadPlatform();
}

function applyTenantSearch() {
  tenantQ.value = tenantQInput.value;
  tenantPage.value = 1;
  void loadTenant();
}

function onPlatformSizeChange() {
  platformPage.value = 1;
  void loadPlatform();
}

function onTenantSizeChange() {
  tenantPage.value = 1;
  void loadTenant();
}

function onExtensionTenantChange() {
  tenantPage.value = 1;
  void loadTenant();
}

async function onAddPlatform() {
  const w = platformWord.value.trim();
  if (!w) return;
  saving.value = true;
  try {
    await chatAdmin.addSensitiveTerm("PLATFORM", w);
    platformWord.value = "";
    await loadPlatform();
    ElMessage.success("已添加");
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, "添加失败"));
  } finally {
    saving.value = false;
  }
}

async function onAddTenant() {
  const w = tenantWord.value.trim();
  if (!w) return;
  saving.value = true;
  try {
    await chatAdmin.addSensitiveTerm("TENANT", w, tenantWriteTarget());
    tenantWord.value = "";
    await loadTenant();
    ElMessage.success("已添加");
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, "添加失败"));
  } finally {
    saving.value = false;
  }
}

async function onImportPlatform() {
  if (!platformImportText.value.trim()) {
    ElMessage.warning("请输入要导入的内容");
    return;
  }
  importing.value = true;
  try {
    const r = await chatAdmin.importSensitiveTerms("PLATFORM", platformImportText.value);
    platformImportOpen.value = false;
    platformImportText.value = "";
    await loadPlatform();
    ElMessage.success(`导入完成：新增 ${r.inserted}，重复跳过 ${r.skippedDuplicates}，无效 ${r.skippedInvalid}`);
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, "导入失败"));
  } finally {
    importing.value = false;
  }
}

async function onImportTenant() {
  if (!tenantImportText.value.trim()) {
    ElMessage.warning("请输入要导入的内容");
    return;
  }
  importing.value = true;
  try {
    const r = await chatAdmin.importSensitiveTerms("TENANT", tenantImportText.value, tenantWriteTarget());
    tenantImportOpen.value = false;
    tenantImportText.value = "";
    await loadTenant();
    ElMessage.success(`导入完成：新增 ${r.inserted}，重复跳过 ${r.skippedDuplicates}，无效 ${r.skippedInvalid}`);
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, "导入失败"));
  } finally {
    importing.value = false;
  }
}

async function onDelete(row: SensitiveTermRow) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.word}」？`, "确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await chatAdmin.deleteSensitiveTerm(row.id);
    await Promise.all([loadPlatform(), loadTenant()]);
    ElMessage.success("已删除");
  } catch (e: unknown) {
    ElMessage.error(errMsg(e, "删除失败"));
  }
}

function errMsg(e: unknown, fallback: string): string {
  if (e && typeof e === "object" && "response" in e) {
    const r = (e as { response?: { data?: { message?: string } } }).response?.data?.message;
    if (typeof r === "string" && r.length) return r;
  }
  return fallback;
}

onMounted(() => {
  void Promise.all([loadPlatform(), loadTenant()]).catch(() => ElMessage.error("加载失败"));
});
</script>

<style scoped>
.page {
  padding: 16px 20px 24px;
  width: 100%;
  max-width: none;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.head {
  flex-shrink: 0;
  margin-bottom: 14px;
}

.head h1 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.split {
  display: flex;
  flex-direction: row;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

@media (max-width: 960px) {
  .split {
    flex-direction: column;
  }
}

.split-card {
  flex: 1 1 0;
  min-width: 0;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.split-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pane-inner {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.tag-ro {
  margin-left: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  flex-shrink: 0;
}

.toolbar--secondary {
  margin-top: -2px;
}

.toolbar--tenant-target {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color);
}

.field-label {
  font-size: 13px;
  color: #64748b;
  flex-shrink: 0;
}

.tenant-select {
  flex: 1 1 160px;
  min-width: 0;
  max-width: 100%;
}

.inp-short {
  flex: 1 1 140px;
  min-width: 0;
  max-width: 260px;
}

.table-scroll {
  flex: 1;
  min-height: 200px;
  overflow: auto;
}

.pane-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.dlg-hint {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 10px;
}
</style>
