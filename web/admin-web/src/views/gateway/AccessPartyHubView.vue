<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import {
  auditSummary,
  createAccessParty,
  createApiModule,
  deleteAccessParty,
  deleteApiModule,
  downloadMarkdownFile,
  fetchIntegrationDoc,
  listAccessParties,
  listApiModules,
  listAuditLogs,
  listModuleEndpoints,
  replaceModuleEndpoints,
  rotateAccessPartySecret,
  updateAccessParty,
  updateApiModule,
  type AccessPartyRow,
  type ApiModuleRow,
  type AuditLogRow,
} from "@/api/gatewayAccessParty";
import { listApiEndpointPicker, type ApiEndpointRow } from "@/api/gatewayApiEndpoints";
import { confirmMessageBox } from "@/utils/messageBoxI18n";

const { t } = useI18n();
const router = useRouter();
const activeTab = ref("parties");

const partyLoading = ref(false);
const partyRows = ref<AccessPartyRow[]>([]);
const partyPage = ref(1);
const partyTotal = ref(0);

const moduleRows = ref<ApiModuleRow[]>([]);
const moduleLoading = ref(false);
const moduleEndpointCounts = ref<Record<number, number>>({});

const endpointPicker = ref<ApiEndpointRow[]>([]);

const auditRows = ref<AuditLogRow[]>([]);
const auditSummaryData = ref<Record<string, unknown>>({});
const auditPartyFilter = ref<number | undefined>(undefined);
const auditPage = ref(1);
const auditTotal = ref(0);

const partyDialog = ref(false);
const partyForm = ref({
  displayName: "",
  appId: "",
  totalRpmCap: 100,
  remark: "",
  statusOn: true,
});
const editingPartyId = ref<number | null>(null);

const secretDialog = ref(false);
const secretPayload = ref({ appId: "", secret: "" });

const moduleDialog = ref(false);
const editingModuleId = ref<number | null>(null);
const moduleForm = ref({
  code: "",
  displayName: "",
  sortOrder: 0,
  enabledOn: true,
  remark: "",
});

const bindDialog = ref(false);
const bindModuleId = ref<number | null>(null);
const bindModuleName = ref("");
const bindEndpointIds = ref<number[]>([]);
const bindLoading = ref(false);

const exportDialog = ref(false);
const exportPartyId = ref<number | null>(null);
const exportPartyName = ref("");
const exportBaseUrl = ref("");
const exportLoading = ref(false);

function defaultGatewayBaseUrl(): string {
  const fromEnv = (import.meta.env.VITE_API_BASE as string | undefined)?.trim();
  if (fromEnv) return fromEnv.replace(/\/+$/, "");
  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin;
  }
  return "";
}

function goGrantWizard(row: AccessPartyRow) {
  router.push(`/gateway/access-parties/${row.id}/grant`);
}

async function loadParties() {
  partyLoading.value = true;
  try {
    const p = await listAccessParties(partyPage.value, 20);
    partyRows.value = p.records;
    partyTotal.value = p.total;
  } finally {
    partyLoading.value = false;
  }
}

async function loadModuleEndpointCounts(modules: ApiModuleRow[]) {
  const counts: Record<number, number> = {};
  await Promise.all(
    modules.map(async (m) => {
      const links = await listModuleEndpoints(m.id);
      counts[m.id] = links.length;
    }),
  );
  moduleEndpointCounts.value = counts;
}

async function loadModules() {
  moduleLoading.value = true;
  try {
    moduleRows.value = await listApiModules();
    await loadModuleEndpointCounts(moduleRows.value);
  } finally {
    moduleLoading.value = false;
  }
}

async function loadAudit() {
  auditSummaryData.value = await auditSummary(7);
  const p = await listAuditLogs(auditPage.value, 20, auditPartyFilter.value);
  auditRows.value = p.records;
  auditTotal.value = p.total;
}

async function loadPicker() {
  endpointPicker.value = await listApiEndpointPicker();
}

function openSecretDialog(appId: string, secret: string) {
  secretPayload.value = { appId, secret };
  secretDialog.value = true;
}

async function copySecretPayload() {
  const text = `AppId: ${secretPayload.value.appId}\nSecret: ${secretPayload.value.secret}`;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(t("views.gatewayAccessParty.copiedSecret"));
  } catch {
    ElMessage.warning(t("views.gatewayAccessParty.secretHint"));
  }
}

function openPartyCreate() {
  editingPartyId.value = null;
  partyForm.value = { displayName: "", appId: "", totalRpmCap: 100, remark: "", statusOn: true };
  partyDialog.value = true;
}

function openPartyEdit(row: AccessPartyRow) {
  editingPartyId.value = row.id;
  partyForm.value = {
    displayName: row.displayName,
    appId: row.appId,
    totalRpmCap: row.totalRpmCap,
    remark: row.remark ?? "",
    statusOn: row.status === "ON",
  };
  partyDialog.value = true;
}

async function saveParty() {
  if (!partyForm.value.displayName.trim()) {
    ElMessage.warning(t("views.gatewayAccessParty.fillNameWarning"));
    return;
  }
  try {
    if (editingPartyId.value) {
      await updateAccessParty(editingPartyId.value, {
        displayName: partyForm.value.displayName,
        totalRpmCap: partyForm.value.totalRpmCap,
        remark: partyForm.value.remark,
        status: partyForm.value.statusOn ? "ON" : "OFF",
      });
      partyDialog.value = false;
      ElMessage.success(t("views.gatewayAccessParty.saved"));
    } else {
      const r = await createAccessParty({
        displayName: partyForm.value.displayName,
        appId: partyForm.value.appId || undefined,
        totalRpmCap: partyForm.value.totalRpmCap,
        remark: partyForm.value.remark,
        status: partyForm.value.statusOn ? "ON" : "OFF",
      });
      partyDialog.value = false;
      await loadParties();
      openSecretDialog(r.party.appId, r.plainSecret);
      return;
    }
    await loadParties();
  } catch {
    ElMessage.error(t("views.gatewayAccessParty.saveFailed"));
  }
}

async function onRotateSecret(row: AccessPartyRow) {
  try {
    const secret = await rotateAccessPartySecret(row.id);
    openSecretDialog(row.appId, secret);
  } catch {
    ElMessage.error(t("views.gatewayAccessParty.saveFailed"));
  }
}

async function onDeleteParty(row: AccessPartyRow) {
  await confirmMessageBox(t, t("views.gatewayAccessParty.deleteConfirm"), { type: "warning" });
  await deleteAccessParty(row.id);
  await loadParties();
}

function openModuleCreate() {
  editingModuleId.value = null;
  moduleForm.value = { code: "", displayName: "", sortOrder: 0, enabledOn: true, remark: "" };
  moduleDialog.value = true;
}

function openModuleEdit(row: ApiModuleRow) {
  editingModuleId.value = row.id;
  moduleForm.value = {
    code: row.code,
    displayName: row.displayName,
    sortOrder: row.sortOrder,
    enabledOn: row.enabled === "ON",
    remark: row.remark ?? "",
  };
  moduleDialog.value = true;
}

async function saveModule() {
  if (!moduleForm.value.displayName.trim()) {
    ElMessage.warning(t("views.gatewayAccessParty.fillNameWarning"));
    return;
  }
  try {
    if (editingModuleId.value) {
      await updateApiModule(editingModuleId.value, {
        displayName: moduleForm.value.displayName,
        sortOrder: moduleForm.value.sortOrder,
        enabled: moduleForm.value.enabledOn ? "ON" : "OFF",
        remark: moduleForm.value.remark,
      });
    } else {
      if (!moduleForm.value.code.trim()) {
        ElMessage.warning(t("views.gatewayAccessParty.fillCodeWarning"));
        return;
      }
      await createApiModule({
        code: moduleForm.value.code,
        displayName: moduleForm.value.displayName,
        sortOrder: moduleForm.value.sortOrder,
        enabled: moduleForm.value.enabledOn ? "ON" : "OFF",
        remark: moduleForm.value.remark,
      });
    }
    moduleDialog.value = false;
    ElMessage.success(t("views.gatewayAccessParty.saved"));
    await loadModules();
  } catch {
    ElMessage.error(t("views.gatewayAccessParty.saveFailed"));
  }
}

async function onDeleteModule(row: ApiModuleRow) {
  await confirmMessageBox(t, t("views.gatewayAccessParty.deleteModuleConfirm"), { type: "warning" });
  await deleteApiModule(row.id);
  await loadModules();
}

async function openBindEndpoints(row: ApiModuleRow) {
  bindModuleId.value = row.id;
  bindModuleName.value = row.displayName;
  bindLoading.value = true;
  bindDialog.value = true;
  try {
    const links = await listModuleEndpoints(row.id);
    bindEndpointIds.value = links.map((l) => l.endpointId);
  } finally {
    bindLoading.value = false;
  }
}

async function saveBindEndpoints() {
  if (!bindModuleId.value) return;
  await replaceModuleEndpoints(bindModuleId.value, bindEndpointIds.value);
  bindDialog.value = false;
  ElMessage.success(t("views.gatewayAccessParty.saved"));
  await loadModules();
}

function endpointLabel(ep: ApiEndpointRow) {
  return `${ep.displayName} · ${ep.httpMethod} ${ep.pathPattern}`;
}

function auditStatusType(code: number): "success" | "warning" | "danger" | "info" {
  if (code >= 200 && code < 300) return "success";
  if (code === 401 || code === 403 || code === 429) return "warning";
  if (code >= 500) return "danger";
  return "info";
}

function openExportDoc(row: AccessPartyRow) {
  exportPartyId.value = row.id;
  exportPartyName.value = row.displayName;
  exportBaseUrl.value = defaultGatewayBaseUrl();
  exportDialog.value = true;
}

async function confirmExportDoc() {
  if (!exportPartyId.value) return;
  exportLoading.value = true;
  try {
    const doc = await fetchIntegrationDoc(exportPartyId.value, exportBaseUrl.value || undefined);
    downloadMarkdownFile(doc.filename, doc.markdown);
    exportDialog.value = false;
    ElMessage.success(t("views.gatewayAccessParty.exportDocSuccess"));
  } catch {
    ElMessage.error(t("views.gatewayAccessParty.exportDocFailed"));
  } finally {
    exportLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadParties(), loadModules(), loadPicker()]);
});
</script>

<template>
  <div class="page gw-hub">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.gatewayAccessParty.title") }}</span>
          <p class="hdr-sub">{{ t("views.gatewayAccessParty.sub") }}</p>
        </div>
      </template>

      <el-tabs
        v-model="activeTab"
        @tab-change="
          (n: string | number) => {
            if (n === 'audit') loadAudit();
            if (n === 'modules') loadModules();
          }
        "
      >
        <el-tab-pane :label="t('views.gatewayAccessParty.tabParties')" name="parties">
          <div class="tab-toolbar">
            <el-button type="primary" @click="openPartyCreate">{{ t("views.gatewayAccessParty.newParty") }}</el-button>
            <el-button text type="primary" :loading="partyLoading" @click="loadParties">{{ t("common.refresh") }}</el-button>
          </div>
          <el-table v-loading="partyLoading" :data="partyRows" stripe border>
            <el-table-column prop="displayName" :label="t('views.gatewayAccessParty.colName')" min-width="140" />
            <el-table-column prop="appId" :label="t('views.gatewayAccessParty.colAppId')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="totalRpmCap" :label="t('views.gatewayAccessParty.colTotalRpm')" width="110" align="right" />
            <el-table-column :label="t('views.gatewayAccessParty.colStatus')" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ON' ? 'success' : 'info'" size="small">
                  {{ row.status === "ON" ? t("views.gateway.yes") : t("views.gateway.no") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.gateway.colActions')" width="360" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goGrantWizard(row)">{{ t("views.gatewayAccessParty.bindGrant") }}</el-button>
                <el-button link type="primary" size="small" @click="openPartyEdit(row)">{{ t("views.gateway.edit") }}</el-button>
                <el-button link type="primary" size="small" @click="onRotateSecret(row)">{{ t("views.gatewayAccessParty.rotate") }}</el-button>
                <el-button link type="primary" size="small" @click="openExportDoc(row)">{{ t("views.gatewayAccessParty.exportDoc") }}</el-button>
                <el-button link type="danger" size="small" @click="onDeleteParty(row)">{{ t("views.gateway.delete") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="partyTotal > 20" class="pager">
            <el-pagination
              v-model:current-page="partyPage"
              :page-size="20"
              :total="partyTotal"
              layout="total, prev, pager, next"
              @current-change="loadParties"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane :label="t('views.gatewayAccessParty.tabModules')" name="modules">
          <div class="tab-toolbar">
            <el-button type="primary" @click="openModuleCreate">{{ t("views.gatewayAccessParty.newModule") }}</el-button>
            <el-button text type="primary" :loading="moduleLoading" @click="loadModules">{{ t("common.refresh") }}</el-button>
          </div>
          <el-table v-loading="moduleLoading" :data="moduleRows" stripe border>
            <el-table-column prop="code" :label="t('views.gatewayAccessParty.labelCode')" width="120" />
            <el-table-column prop="displayName" :label="t('views.gateway.colName')" min-width="140" />
            <el-table-column :label="t('views.gatewayAccessParty.endpointCount')" width="88" align="center">
              <template #default="{ row }">{{ moduleEndpointCounts[row.id] ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="sortOrder" :label="t('views.gateway.colSort')" width="80" align="right" />
            <el-table-column :label="t('views.gateway.colEnabled')" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
                  {{ row.enabled === "ON" ? t("views.gateway.yes") : t("views.gateway.no") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.gateway.colActions')" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openModuleEdit(row)">{{ t("views.gateway.edit") }}</el-button>
                <el-button link type="primary" size="small" @click="openBindEndpoints(row)">{{ t("views.gatewayAccessParty.bindEndpoints") }}</el-button>
                <el-button link type="danger" size="small" @click="onDeleteModule(row)">{{ t("views.gateway.delete") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('views.gatewayAccessParty.tabAudit')" name="audit">
          <div class="tab-toolbar wrap">
            <el-select
              v-model="auditPartyFilter"
              clearable
              :placeholder="t('views.gatewayAccessParty.auditFilterParty')"
              style="width: 240px"
              @change="
                () => {
                  auditPage = 1;
                  loadAudit();
                }
              "
            >
              <el-option v-for="p in partyRows" :key="p.id" :label="p.displayName" :value="p.id" />
            </el-select>
            <el-button text type="primary" @click="loadAudit">{{ t("common.refresh") }}</el-button>
          </div>
          <el-row :gutter="12" class="summary-row">
            <el-col :span="6">
              <el-statistic :title="t('views.gatewayAccessParty.statTotal')" :value="Number(auditSummaryData.totalCalls ?? 0)" />
            </el-col>
            <el-col :span="6">
              <el-statistic
                :title="t('views.gatewayAccessParty.statSuccessRate')"
                :value="Math.round(Number(auditSummaryData.successRate ?? 0) * 1000) / 10"
                suffix="%"
              />
            </el-col>
          </el-row>
          <el-table :data="auditRows" stripe border>
            <el-table-column prop="pathPattern" :label="t('views.gateway.colPathAnt')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="httpStatus" label="HTTP" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="auditStatusType(row.httpStatus)" size="small">{{ row.httpStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" :label="t('views.gatewayAccessParty.colDuration')" width="100" align="right" />
            <el-table-column prop="clientIp" label="IP" width="120" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('views.gatewayAccessParty.colTime')" min-width="160" />
          </el-table>
          <div v-if="auditTotal > 20" class="pager">
            <el-pagination
              v-model:current-page="auditPage"
              :page-size="20"
              :total="auditTotal"
              layout="total, prev, pager, next"
              @current-change="loadAudit"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 接入方表单 -->
    <el-dialog v-model="partyDialog" :title="t('views.gatewayAccessParty.partyDialog')" width="480px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item :label="t('views.gatewayAccessParty.colName')" required>
          <el-input v-model="partyForm.displayName" />
        </el-form-item>
        <el-form-item v-if="!editingPartyId" :label="t('views.gatewayAccessParty.colAppId')">
          <el-input v-model="partyForm.appId" :placeholder="t('views.gatewayAccessParty.appIdOptional')" />
        </el-form-item>
        <el-form-item :label="t('views.gatewayAccessParty.colTotalRpm')">
          <el-input-number v-model="partyForm.totalRpmCap" :min="0" />
        </el-form-item>
        <el-form-item :label="t('views.gatewayAccessParty.colStatus')">
          <el-switch v-model="partyForm.statusOn" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.colRemark')">
          <el-input v-model="partyForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partyDialog = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" @click="saveParty">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <!-- Secret 仅一次展示 -->
    <el-dialog
      v-model="secretDialog"
      :title="t('views.gatewayAccessParty.secretTitle')"
      width="520px"
      :close-on-click-modal="false"
      :show-close="false"
      destroy-on-close
    >
      <el-alert type="warning" :title="t('views.gatewayAccessParty.secretOnce')" :description="t('views.gatewayAccessParty.secretHint')" show-icon :closable="false" class="secret-alert" />
      <el-descriptions :column="1" border class="secret-desc">
        <el-descriptions-item :label="t('views.gatewayAccessParty.colAppId')">
          <code>{{ secretPayload.appId }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="Secret">
          <code class="secret-code">{{ secretPayload.secret }}</code>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="copySecretPayload">{{ t("views.gatewayAccessParty.copySecret") }}</el-button>
        <el-button type="primary" @click="secretDialog = false">{{ t("views.gatewayAccessParty.secretSavedConfirm") }}</el-button>
      </template>
    </el-dialog>

    <!-- 模块表单 -->
    <el-dialog v-model="moduleDialog" :title="t('views.gatewayAccessParty.moduleDialog')" width="480px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item v-if="!editingModuleId" :label="t('views.gatewayAccessParty.labelCode')" required>
          <el-input v-model="moduleForm.code" :placeholder="t('views.gatewayAccessParty.codePh')" />
        </el-form-item>
        <el-form-item v-else :label="t('views.gatewayAccessParty.labelCode')">
          <el-input :model-value="moduleForm.code" disabled />
        </el-form-item>
        <el-form-item :label="t('views.gateway.colName')" required>
          <el-input v-model="moduleForm.displayName" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.colSort')">
          <el-input-number v-model="moduleForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.colEnabled')">
          <el-switch v-model="moduleForm.enabledOn" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.colRemark')">
          <el-input v-model="moduleForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moduleDialog = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" @click="saveModule">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <!-- 模块绑定接口 -->
    <el-dialog v-model="bindDialog" :title="t('views.gatewayAccessParty.bindEndpointsTitle')" width="640px" destroy-on-close>
      <p class="bind-hint">{{ t("views.gatewayAccessParty.bindEndpointsHint") }}</p>
      <p v-if="bindModuleName" class="bind-module-name">{{ bindModuleName }}</p>
      <el-select
        v-model="bindEndpointIds"
        v-loading="bindLoading"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        :placeholder="t('views.gatewayAccessParty.pickEndpointsPh')"
        style="width: 100%"
      >
        <el-option v-for="ep in endpointPicker" :key="ep.id" :label="endpointLabel(ep)" :value="ep.id" />
      </el-select>
      <template #footer>
        <el-button @click="bindDialog = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" @click="saveBindEndpoints">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exportDialog" :title="t('views.gatewayAccessParty.exportDocTitle')" width="520px" destroy-on-close>
      <p v-if="exportPartyName" class="export-party-name">{{ exportPartyName }}</p>
      <el-alert type="info" :title="t('views.gatewayAccessParty.exportDocHint')" show-icon :closable="false" class="export-hint" />
      <el-form label-width="120px" class="export-form">
        <el-form-item :label="t('views.gatewayAccessParty.exportDocBaseUrl')">
          <el-input v-model="exportBaseUrl" :placeholder="t('views.gatewayAccessParty.exportDocBaseUrlPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialog = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="exportLoading" @click="confirmExportDoc">{{ t("views.gatewayAccessParty.exportDoc") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.hdr-sub {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.tab-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: center;
}
.tab-toolbar.wrap {
  flex-wrap: wrap;
}
.summary-row {
  margin-bottom: 16px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.secret-alert {
  margin-bottom: 12px;
}
.secret-desc code {
  word-break: break-all;
}
.secret-code {
  font-size: 13px;
}
.bind-hint {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.bind-module-name {
  margin: 0 0 12px;
  font-weight: 600;
}
.export-party-name {
  margin: 0 0 12px;
  font-weight: 600;
}
.export-hint {
  margin-bottom: 16px;
}
.export-form {
  margin-top: 8px;
}
</style>
