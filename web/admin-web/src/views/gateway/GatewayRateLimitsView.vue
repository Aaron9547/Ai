<template>
  <div class="page gw-hub">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.gateway.title") }}</span>
          <p class="hdr-sub">{{ t("views.gateway.sub") }}</p>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="hub-tabs">
        <el-tab-pane :label="t('views.gateway.tabEndpoints')" name="endpoints">
          <div class="tab-toolbar">
            <el-button type="primary" @click="openEndpointCreate">{{ t("views.gateway.newEndpoint") }}</el-button>
            <el-button text type="primary" :loading="epLoading" @click="loadEndpoints">{{ t("views.gateway.refresh") }}</el-button>
          </div>
          <el-table v-loading="epLoading" :data="epRows" stripe border :empty-text="t('views.gateway.emptyEndpoints')">
            <el-table-column prop="displayName" :label="t('views.gateway.colName')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="pathPattern" :label="t('views.gateway.colPathAnt')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="httpMethod" :label="t('views.gateway.colMethod')" width="88" />
            <el-table-column prop="sortOrder" :label="t('views.gateway.colSort')" width="72" align="right" />
            <el-table-column :label="t('views.gateway.colEnabled')" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
                  {{ row.enabled === "ON" ? t("views.gateway.yes") : t("views.gateway.no") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('views.gateway.colRemark')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('views.gateway.colActions')" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEndpointEdit(row)">{{ t("views.gateway.edit") }}</el-button>
                <el-button link type="danger" size="small" @click="onEndpointDelete(row)">{{ t("views.gateway.delete") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="epPage"
              v-model:page-size="epSize"
              layout="total, sizes, prev, pager, next"
              :total="epTotal"
              :page-sizes="[10, 20, 50]"
              background
              @current-change="loadEndpoints"
              @size-change="onEpSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane :label="t('views.gateway.tabLimits')" name="limits">
          <div class="tab-toolbar limits-toolbar">
            <template v-if="showFounderScope">
              <span class="lbl">{{ t("views.gateway.scopeLabel") }}</span>
              <el-select v-model="rateScopeUi" style="width: 200px" @change="onRateScopeChange">
                <el-option :label="t('views.gateway.scopeGlobal')" value="GLOBAL" />
                <el-option :label="t('views.gateway.scopeTenant')" value="TENANT" />
              </el-select>
              <el-select
                v-if="rateScopeUi === 'TENANT'"
                v-model="rateTenantId"
                filterable
                :placeholder="t('views.gateway.selectTenant')"
                style="width: 260px"
                :loading="tenantLoading"
                @change="onRateTenantChange"
              >
                <el-option v-for="row in tenantOptions" :key="row.id" :label="tenantOptionLabel(row)" :value="row.id" />
              </el-select>
            </template>
            <span v-else class="muted">{{ t("views.gateway.tenantOnlyHint") }}</span>
            <el-button type="primary" :disabled="limitsLoadBlocked" @click="openLimitCreate">{{ t("views.gateway.newRule") }}</el-button>
            <el-button text type="primary" :loading="rlLoading" @click="loadRateLimits">{{ t("views.gateway.refresh") }}</el-button>
          </div>
          <el-alert v-if="limitsLoadBlocked" type="warning" show-icon :closable="false" class="scope-alert">
            {{ t("views.gateway.scopeAlert") }}
          </el-alert>
          <el-table v-loading="rlLoading" :data="rlRows" stripe border :empty-text="t('views.gateway.emptyRules')">
            <el-table-column :label="t('views.gateway.colTenant')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ formatRateLimitTenantCell(row.tenantId) }}
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" :label="t('views.gateway.colPathAnt')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="httpMethod" :label="t('views.gateway.colMethod')" width="90" />
            <el-table-column prop="requestsPerMinute" :label="t('views.gateway.limitsRpm')" width="120" align="right" />
            <el-table-column :label="t('views.gateway.colEnabled')" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{
                  row.enabled === "ON" ? t("views.gateway.yes") : t("views.gateway.no")
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('views.gateway.colRemark')" min-width="140" show-overflow-tooltip />
            <el-table-column :label="t('views.gateway.colActions')" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openLimitEdit(row)">{{ t("views.gateway.edit") }}</el-button>
                <el-button link type="danger" size="small" @click="onLimitDelete(row)">{{ t("views.gateway.delete") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="rlPage"
              v-model:page-size="rlSize"
              layout="total, sizes, prev, pager, next"
              :total="rlTotal"
              :page-sizes="[10, 20, 50]"
              background
              @current-change="loadRateLimits"
              @size-change="onRlSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 接口目录弹窗 -->
    <el-dialog
      v-model="epDlg"
      :title="epEditId ? t('views.gateway.dlgEndpointTitleEdit') : t('views.gateway.dlgEndpointTitleNew')"
      width="560px"
      destroy-on-close
      @closed="resetEpDlg"
    >
      <el-form label-width="108px">
        <el-form-item :label="t('views.gateway.labelDisplayName')" required>
          <el-input v-model="epForm.displayName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelPathPattern')" required>
          <el-input v-model="epForm.pathPattern" :placeholder="t('views.gateway.pathPh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelHttpMethod')">
          <el-input v-model="epForm.httpMethod" :placeholder="t('views.gateway.methodPh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelSort')">
          <el-input-number v-model="epForm.sortOrder" :min="0" :max="999999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelEnabled')">
          <el-switch v-model="epForm.enabledOn" :active-text="t('views.gateway.yes')" :inactive-text="t('views.gateway.no')" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelRemark')">
          <el-input v-model="epForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="epDlg = false">{{ t("views.gateway.cancel") }}</el-button>
        <el-button type="primary" :loading="epSaving" @click="submitEndpoint">{{ t("views.gateway.save") }}</el-button>
      </template>
    </el-dialog>

    <!-- 限流规则弹窗 -->
    <el-dialog
      v-model="rlDlg"
      :title="rlEditId ? t('views.gateway.dlgLimitTitleEdit') : t('views.gateway.dlgLimitTitleNew')"
      width="600px"
      destroy-on-close
      @closed="resetRlDlg"
    >
      <el-form label-width="112px">
        <el-form-item :label="t('views.gateway.quickPick')">
          <el-select
            v-model="pickedEndpointId"
            filterable
            clearable
            :placeholder="t('views.gateway.quickPickPh')"
            style="width: 100%"
            @change="applyPickedEndpoint"
          >
            <el-option
              v-for="e in pickerEndpoints"
              :key="e.id"
              :label="`${e.displayName} · ${e.pathPattern} [${e.httpMethod}]`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="showFounderScope" :label="t('views.gateway.tenantScope')">
          <el-radio-group v-model="rlFormScope">
            <el-radio-button label="GLOBAL">{{ t("views.gateway.scopeRadioGlobal") }}</el-radio-button>
            <el-radio-button label="TENANT">{{ t("views.gateway.scopeRadioTenant") }}</el-radio-button>
          </el-radio-group>
          <el-select
            v-if="rlFormScope === 'TENANT'"
            v-model="rlFormTenantId"
            filterable
            :placeholder="t('views.gateway.selectTenant')"
            style="width: 100%; margin-top: 8px"
          >
            <el-option v-for="row in tenantOptions" :key="row.id" :label="tenantOptionLabel(row)" :value="row.id" />
          </el-select>
          <div class="hint">{{ t("views.gateway.tenantScopeHint") }}</div>
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelPathPattern')" required>
          <el-input v-model="rlForm.pathPattern" :placeholder="t('views.gateway.pathPh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelHttpMethod')">
          <el-input v-model="rlForm.httpMethod" :placeholder="t('views.gateway.methodPh')" clearable />
        </el-form-item>
        <el-form-item :label="t('views.gateway.rpm')">
          <el-input-number v-model="rlForm.requestsPerMinute" :min="1" :max="1000000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelEnabled')">
          <el-switch v-model="rlForm.enabledOn" :active-text="t('views.gateway.yes')" :inactive-text="t('views.gateway.no')" />
        </el-form-item>
        <el-form-item :label="t('views.gateway.labelRemark')">
          <el-input v-model="rlForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rlDlg = false">{{ t("views.gateway.cancel") }}</el-button>
        <el-button type="primary" :loading="rlSaving" @click="submitLimit">{{ t("views.gateway.save") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as epApi from "@/api/gatewayApiEndpoints";
import * as gwApi from "@/api/gatewayRateLimits";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { useAdminFounderTenantOptions } from "@/composables/useAdminFounderTenantOptions";
import { readJwtTid, readJwtTmr } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import type { TenantRow } from "@/api/tenants";
import { formatTenantNameCode, formatTenantRowOptionLabel } from "@/utils/adminListDisplay";

const { t } = useI18n();

const activeTab = ref<"endpoints" | "limits">("endpoints");

const { tenantOptions, loadTenants } = useAdminFounderTenantOptions();

/** 与限流页历史逻辑一致：跳过登录联调时不视为创始人。 */
const showFounderScope = computed(() => {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") return false;
  return readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER";
});

const epLoading = ref(false);
const epRows = ref<epApi.ApiEndpointRow[]>([]);
const epTotal = ref(0);
const epPage = ref(1);
const epSize = ref(20);
const epDlg = ref(false);
const epEditId = ref<number | null>(null);
const epSaving = ref(false);
const epForm = reactive({
  displayName: "",
  pathPattern: "",
  httpMethod: "*",
  sortOrder: 0,
  enabledOn: true,
  remark: "",
});

const rlLoading = ref(false);
const rlRows = ref<gwApi.RateLimitRow[]>([]);
const rlTotal = ref(0);
const rlPage = ref(1);
const rlSize = ref(20);
const rlDlg = ref(false);
const rlEditId = ref<number | null>(null);
const rlSaving = ref(false);
const rateScopeUi = ref<"GLOBAL" | "TENANT">("TENANT");
const rateTenantId = ref<number | undefined>(undefined);
const pickerEndpoints = ref<epApi.ApiEndpointRow[]>([]);
const pickedEndpointId = ref<number | undefined>(undefined);
const rlFormScope = ref<"GLOBAL" | "TENANT">("TENANT");
const rlFormTenantId = ref<number | undefined>(undefined);
const rlForm = reactive({
  pathPattern: "",
  httpMethod: "*",
  requestsPerMinute: 120,
  enabledOn: false,
  remark: "",
});

const tenantLoading = computed(() => showFounderScope.value && tenantOptions.value.length === 0);

const limitsLoadBlocked = computed(
  () => showFounderScope.value && rateScopeUi.value === "TENANT" && rateTenantId.value == null,
);

function tenantOptionLabel(t: TenantRow): string {
  return formatTenantRowOptionLabel(t);
}

function formatRateLimitTenantCell(tenantId: number | null | undefined): string {
  if (tenantId == null) return t("views.gateway.tenantGlobal");
  const row = tenantOptions.value.find((o) => o.id === tenantId);
  if (row) return formatTenantNameCode({ tenantName: row.name, tenantCode: row.code });
  return t("common.dash");
}

function rateListParams(): gwApi.RateLimitListParams | undefined {
  if (!showFounderScope.value) return undefined;
  if (rateScopeUi.value === "GLOBAL") return { rateScope: "GLOBAL" };
  if (rateTenantId.value != null) return { rateScope: "TENANT", tenantId: rateTenantId.value };
  return undefined;
}

async function loadEndpoints() {
  epLoading.value = true;
  try {
    const data = await epApi.listApiEndpoints(epPage.value, epSize.value);
    epRows.value = data.records ?? [];
    epTotal.value = data.total ?? 0;
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.loadEndpointsFailed")));
  } finally {
    epLoading.value = false;
  }
}

function onEpSizeChange() {
  epPage.value = 1;
  void loadEndpoints();
}

async function loadPicker() {
  try {
    pickerEndpoints.value = await epApi.listApiEndpointPicker();
  } catch {
    pickerEndpoints.value = [];
  }
}

async function loadRateLimits() {
  if (limitsLoadBlocked.value) {
    rlRows.value = [];
    rlTotal.value = 0;
    return;
  }
  rlLoading.value = true;
  try {
    const data = await gwApi.listRateLimits(rlPage.value, rlSize.value, rateListParams());
    rlRows.value = data.records ?? [];
    rlTotal.value = data.total ?? 0;
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.loadLimitsFailed")));
  } finally {
    rlLoading.value = false;
  }
}

function onRlSizeChange() {
  rlPage.value = 1;
  void loadRateLimits();
}

function onRateScopeChange() {
  rlPage.value = 1;
  if (rateScopeUi.value === "GLOBAL") {
    rateTenantId.value = undefined;
  } else if (rateTenantId.value == null && tenantOptions.value.length > 0) {
    const jwt = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
    const n = jwt ? Number.parseInt(jwt, 10) : NaN;
    const hit = tenantOptions.value.find((o) => o.id === n);
    rateTenantId.value = hit ? n : tenantOptions.value[0].id;
  }
  void loadRateLimits();
}

function onRateTenantChange() {
  rlPage.value = 1;
  void loadRateLimits();
}

function openEndpointCreate() {
  epEditId.value = null;
  epForm.displayName = "";
  epForm.pathPattern = "";
  epForm.httpMethod = "*";
  epForm.sortOrder = 0;
  epForm.enabledOn = true;
  epForm.remark = "";
  epDlg.value = true;
}

function openEndpointEdit(row: epApi.ApiEndpointRow) {
  epEditId.value = row.id;
  epForm.displayName = row.displayName;
  epForm.pathPattern = row.pathPattern;
  epForm.httpMethod = row.httpMethod || "*";
  epForm.sortOrder = row.sortOrder ?? 0;
  epForm.enabledOn = row.enabled === "ON";
  epForm.remark = row.remark ?? "";
  epDlg.value = true;
}

function resetEpDlg() {
  epEditId.value = null;
}

async function submitEndpoint() {
  epSaving.value = true;
  try {
    if (epEditId.value == null) {
      await epApi.createApiEndpoint({
        displayName: epForm.displayName.trim(),
        pathPattern: epForm.pathPattern.trim(),
        httpMethod: epForm.httpMethod,
        sortOrder: epForm.sortOrder,
        enabled: epForm.enabledOn ? "ON" : "OFF",
        remark: epForm.remark || undefined,
      });
      ElMessage.success(t("views.gateway.created"));
    } else {
      await epApi.updateApiEndpoint(epEditId.value, {
        displayName: epForm.displayName.trim(),
        pathPattern: epForm.pathPattern.trim(),
        httpMethod: epForm.httpMethod,
        sortOrder: epForm.sortOrder,
        enabled: epForm.enabledOn ? "ON" : "OFF",
        remark: epForm.remark,
      });
      ElMessage.success(t("views.gateway.saved"));
    }
    epDlg.value = false;
    await loadEndpoints();
    await loadPicker();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.saveFailed")));
  } finally {
    epSaving.value = false;
  }
}

async function onEndpointDelete(row: epApi.ApiEndpointRow) {
  try {
    await ElMessageBox.confirm(t("views.gateway.deleteEndpointConfirm", { name: row.displayName }), t("views.gateway.confirm"), {
      type: "warning",
    });
    await epApi.deleteApiEndpoint(row.id);
    await loadEndpoints();
    await loadPicker();
    ElMessage.success(t("views.gateway.deleted"));
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.deleteFailed")));
    }
  }
}

function resolveLimitFormTenantIdForSubmit(): number | null | undefined {
  if (!showFounderScope.value) {
    return undefined;
  }
  if (rlFormScope.value === "GLOBAL") {
    return null;
  }
  return rlFormTenantId.value ?? null;
}

async function openLimitCreate() {
  if (limitsLoadBlocked.value) return;
  rlEditId.value = null;
  pickedEndpointId.value = undefined;
  await loadPicker();
  if (showFounderScope.value) {
    if (rateScopeUi.value === "GLOBAL") {
      rlFormScope.value = "GLOBAL";
      rlFormTenantId.value = undefined;
    } else {
      rlFormScope.value = "TENANT";
      rlFormTenantId.value = rateTenantId.value;
    }
  } else {
    rlFormScope.value = "TENANT";
    rlFormTenantId.value = undefined;
  }
  rlForm.pathPattern = "";
  rlForm.httpMethod = "*";
  rlForm.requestsPerMinute = 120;
  rlForm.enabledOn = false;
  rlForm.remark = "";
  rlDlg.value = true;
}

async function openLimitEdit(row: gwApi.RateLimitRow) {
  rlEditId.value = row.id;
  await loadPicker();
  pickedEndpointId.value = undefined;
  rlForm.pathPattern = row.pathPattern;
  rlForm.httpMethod = row.httpMethod || "*";
  rlForm.requestsPerMinute = row.requestsPerMinute;
  rlForm.enabledOn = row.enabled === "ON";
  rlForm.remark = row.remark ?? "";
  if (showFounderScope.value) {
    if (row.tenantId == null) {
      rlFormScope.value = "GLOBAL";
      rlFormTenantId.value = undefined;
    } else {
      rlFormScope.value = "TENANT";
      rlFormTenantId.value = row.tenantId;
    }
  }
  rlDlg.value = true;
}

function resetRlDlg() {
  rlEditId.value = null;
  pickedEndpointId.value = undefined;
}

function applyPickedEndpoint(id: number | undefined) {
  if (id == null) return;
  const e = pickerEndpoints.value.find((x) => x.id === id);
  if (!e) return;
  rlForm.pathPattern = e.pathPattern;
  rlForm.httpMethod = e.httpMethod || "*";
}

async function submitLimit() {
  rlSaving.value = true;
  try {
    if (showFounderScope.value && rlFormScope.value === "TENANT" && rlFormTenantId.value == null) {
      ElMessage.warning(t("views.gateway.pickTenantWarning"));
      return;
    }
    if (!rlForm.pathPattern.trim()) {
      ElMessage.warning(t("views.gateway.fillPathWarning"));
      return;
    }
    const tid = resolveLimitFormTenantIdForSubmit();
    if (rlEditId.value == null) {
      await gwApi.createRateLimit({
        tenantId: tid,
        pathPattern: rlForm.pathPattern.trim(),
        httpMethod: rlForm.httpMethod,
        requestsPerMinute: rlForm.requestsPerMinute,
        enabled: rlForm.enabledOn ? "ON" : "OFF",
        remark: rlForm.remark || undefined,
      });
      ElMessage.success(t("views.gateway.created"));
    } else {
      await gwApi.updateRateLimit(rlEditId.value, {
        tenantId: tid,
        pathPattern: rlForm.pathPattern.trim(),
        httpMethod: rlForm.httpMethod,
        requestsPerMinute: rlForm.requestsPerMinute,
        enabled: rlForm.enabledOn ? "ON" : "OFF",
        remark: rlForm.remark,
      });
      ElMessage.success(t("views.gateway.saved"));
    }
    rlDlg.value = false;
    await loadRateLimits();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.saveFailed")));
  } finally {
    rlSaving.value = false;
  }
}

async function onLimitDelete(row: gwApi.RateLimitRow) {
  try {
    await ElMessageBox.confirm(
      t("views.gateway.deleteLimitConfirm", { method: row.httpMethod, path: row.pathPattern }),
      t("views.gateway.confirm"),
      { type: "warning" },
    );
    await gwApi.deleteRateLimit(row.id);
    await loadRateLimits();
    ElMessage.success(t("views.gateway.deleted"));
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, t("views.gateway.deleteFailed")));
    }
  }
}

watch(activeTab, (tab) => {
  if (tab === "limits") void loadRateLimits();
});

onMounted(() => {
  void loadEndpoints();
  void loadPicker();
  void loadTenants();
  if (showFounderScope.value) {
    const jwt = readJwtTid(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY));
    const n = jwt ? Number.parseInt(jwt, 10) : NaN;
    watch(
      tenantOptions,
      (opts) => {
        if (!showFounderScope.value || opts.length === 0) return;
        if (rateTenantId.value != null) return;
        const hit = Number.isFinite(n) ? opts.find((o) => o.id === n) : undefined;
        rateTenantId.value = hit ? (n as number) : opts[0].id;
        void loadRateLimits();
      },
      { immediate: true },
    );
  } else {
    void loadRateLimits();
  }
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}
.hdr {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}
.hdr-sub {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.hub-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.limits-toolbar .lbl {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.muted {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  margin-right: auto;
}
.scope-alert {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
