<template>
  <div class="page gw-hub">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">接口与限流</span>
          <p class="hdr-sub">维护可调用的接口目录，并为路径配置每分钟请求上限；创始人可切换「全局限流」或指定租户。</p>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="hub-tabs">
        <el-tab-pane label="接口管理" name="endpoints">
          <div class="tab-toolbar">
            <el-button type="primary" @click="openEndpointCreate">新建接口</el-button>
            <el-button text type="primary" :loading="epLoading" @click="loadEndpoints">刷新</el-button>
          </div>
          <el-table v-loading="epLoading" :data="epRows" stripe border empty-text="暂无接口目录项">
            <el-table-column prop="displayName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="pathPattern" label="路径模式 (Ant)" min-width="220" show-overflow-tooltip />
            <el-table-column prop="httpMethod" label="方法" width="88" />
            <el-table-column prop="sortOrder" label="排序" width="72" align="right" />
            <el-table-column label="启用" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">
                  {{ row.enabled === "ON" ? "是" : "否" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEndpointEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onEndpointDelete(row)">删除</el-button>
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

        <el-tab-pane label="限流控制" name="limits">
          <div class="tab-toolbar limits-toolbar">
            <template v-if="showFounderScope">
              <span class="lbl">查看范围</span>
              <el-select v-model="rateScopeUi" style="width: 200px" @change="onRateScopeChange">
                <el-option label="全局限流（tenant 为空）" value="GLOBAL" />
                <el-option label="指定租户" value="TENANT" />
              </el-select>
              <el-select
                v-if="rateScopeUi === 'TENANT'"
                v-model="rateTenantId"
                filterable
                placeholder="选择租户"
                style="width: 260px"
                :loading="tenantLoading"
                @change="onRateTenantChange"
              >
                <el-option v-for="t in tenantOptions" :key="t.id" :label="tenantOptionLabel(t)" :value="t.id" />
              </el-select>
            </template>
            <span v-else class="muted">当前仅可管理本租户的限流规则。</span>
            <el-button type="primary" :disabled="limitsLoadBlocked" @click="openLimitCreate">新建规则</el-button>
            <el-button text type="primary" :loading="rlLoading" @click="loadRateLimits">刷新</el-button>
          </div>
          <el-alert v-if="limitsLoadBlocked" type="warning" show-icon :closable="false" class="scope-alert">
            创始人请先选择「指定租户」下的具体租户，再查看或配置该租户的限流。
          </el-alert>
          <el-table v-loading="rlLoading" :data="rlRows" stripe border empty-text="暂无规则">
            <el-table-column label="租户" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ formatRateLimitTenantCell(row.tenantId) }}
              </template>
            </el-table-column>
            <el-table-column prop="pathPattern" label="路径模式 (Ant)" min-width="220" show-overflow-tooltip />
            <el-table-column prop="httpMethod" label="方法" width="90" />
            <el-table-column prop="requestsPerMinute" label="每分钟上限" width="120" align="right" />
            <el-table-column label="启用" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 'ON' ? 'success' : 'info'" size="small">{{ row.enabled === "ON" ? "是" : "否" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openLimitEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onLimitDelete(row)">删除</el-button>
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
    <el-dialog v-model="epDlg" :title="epEditId ? '编辑接口' : '新建接口'" width="560px" destroy-on-close @closed="resetEpDlg">
      <el-form label-width="108px">
        <el-form-item label="展示名称" required>
          <el-input v-model="epForm.displayName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="路径模式" required>
          <el-input v-model="epForm.pathPattern" placeholder="如 /api/v1/admin/users/**" clearable />
        </el-form-item>
        <el-form-item label="HTTP 方法">
          <el-input v-model="epForm.httpMethod" placeholder="* 或 GET / POST" clearable />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="epForm.sortOrder" :min="0" :max="999999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="epForm.enabledOn" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="epForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="epDlg = false">取消</el-button>
        <el-button type="primary" :loading="epSaving" @click="submitEndpoint">保存</el-button>
      </template>
    </el-dialog>

    <!-- 限流规则弹窗 -->
    <el-dialog v-model="rlDlg" :title="rlEditId ? '编辑限流规则' : '新建限流规则'" width="600px" destroy-on-close @closed="resetRlDlg">
      <el-form label-width="112px">
        <el-form-item label="快捷选择">
          <el-select
            v-model="pickedEndpointId"
            filterable
            clearable
            placeholder="从接口目录带入路径与方法（可再改）"
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
        <el-form-item v-if="showFounderScope" label="租户范围">
          <el-radio-group v-model="rlFormScope">
            <el-radio-button label="GLOBAL">全局</el-radio-button>
            <el-radio-button label="TENANT">指定租户</el-radio-button>
          </el-radio-group>
          <el-select
            v-if="rlFormScope === 'TENANT'"
            v-model="rlFormTenantId"
            filterable
            placeholder="选择租户"
            style="width: 100%; margin-top: 8px"
          >
            <el-option v-for="t in tenantOptions" :key="t.id" :label="tenantOptionLabel(t)" :value="t.id" />
          </el-select>
          <div class="hint">全局规则对所有租户上下文生效；租户级仅匹配对应租户请求。</div>
        </el-form-item>
        <el-form-item label="路径模式" required>
          <el-input v-model="rlForm.pathPattern" placeholder="如 /api/v1/admin/users/**" clearable />
        </el-form-item>
        <el-form-item label="HTTP 方法">
          <el-input v-model="rlForm.httpMethod" placeholder="* 或 GET / POST" clearable />
        </el-form-item>
        <el-form-item label="每分钟请求数">
          <el-input-number v-model="rlForm.requestsPerMinute" :min="1" :max="1000000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="rlForm.enabledOn" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="rlForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rlDlg = false">取消</el-button>
        <el-button type="primary" :loading="rlSaving" @click="submitLimit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import * as epApi from "@/api/gatewayApiEndpoints";
import * as gwApi from "@/api/gatewayRateLimits";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "@/plugins/http";
import { useAdminFounderTenantOptions } from "@/composables/useAdminFounderTenantOptions";
import { readJwtTid, readJwtTmr } from "@/utils/jwtSubject";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import type { TenantRow } from "@/api/tenants";
import { formatTenantNameCode, formatTenantRowOptionLabel } from "@/utils/adminListDisplay";

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
  if (tenantId == null) return "全局";
  const t = tenantOptions.value.find((o) => o.id === tenantId);
  if (t) return formatTenantNameCode({ tenantName: t.name, tenantCode: t.code });
  return "—";
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
    ElMessage.error(apiRequestErrorMessage(e, "加载接口目录失败"));
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
    ElMessage.error(apiRequestErrorMessage(e, "加载限流规则失败"));
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
      ElMessage.success("已创建");
    } else {
      await epApi.updateApiEndpoint(epEditId.value, {
        displayName: epForm.displayName.trim(),
        pathPattern: epForm.pathPattern.trim(),
        httpMethod: epForm.httpMethod,
        sortOrder: epForm.sortOrder,
        enabled: epForm.enabledOn ? "ON" : "OFF",
        remark: epForm.remark,
      });
      ElMessage.success("已保存");
    }
    epDlg.value = false;
    await loadEndpoints();
    await loadPicker();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    epSaving.value = false;
  }
}

async function onEndpointDelete(row: epApi.ApiEndpointRow) {
  try {
    await ElMessageBox.confirm(`确定删除接口「${row.displayName}」?`, "确认", { type: "warning" });
    await epApi.deleteApiEndpoint(row.id);
    await loadEndpoints();
    await loadPicker();
    ElMessage.success("已删除");
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
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
      ElMessage.warning("请选择租户");
      return;
    }
    if (!rlForm.pathPattern.trim()) {
      ElMessage.warning("请填写路径模式");
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
      ElMessage.success("已创建");
    } else {
      await gwApi.updateRateLimit(rlEditId.value, {
        tenantId: tid,
        pathPattern: rlForm.pathPattern.trim(),
        httpMethod: rlForm.httpMethod,
        requestsPerMinute: rlForm.requestsPerMinute,
        enabled: rlForm.enabledOn ? "ON" : "OFF",
        remark: rlForm.remark,
      });
      ElMessage.success("已保存");
    }
    rlDlg.value = false;
    await loadRateLimits();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    rlSaving.value = false;
  }
}

async function onLimitDelete(row: gwApi.RateLimitRow) {
  try {
    await ElMessageBox.confirm(
      `确定删除限流规则？\n${row.httpMethod} ${row.pathPattern}`,
      "确认",
      { type: "warning" },
    );
    await gwApi.deleteRateLimit(row.id);
    await loadRateLimits();
    ElMessage.success("已删除");
  } catch (e: unknown) {
    if (e !== "cancel") {
      ElMessage.error(apiRequestErrorMessage(e, "删除失败"));
    }
  }
}

watch(activeTab, (t) => {
  if (t === "limits") void loadRateLimits();
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
  border: 1px solid #e5e7eb;
}
.hdr {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title {
  font-weight: 600;
  font-size: 15px;
  color: #0f172a;
}
.hdr-sub {
  margin: 0;
  font-size: 12px;
  color: #64748b;
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
  color: #475569;
}
.muted {
  font-size: 13px;
  color: #94a3b8;
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
  color: #64748b;
  margin-top: 4px;
}
</style>
