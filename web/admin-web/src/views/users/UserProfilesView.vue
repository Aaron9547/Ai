<template>
  <div class="user-profiles">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint"
      :title="t('views.profiles.alertTitle')"
      :description="t('views.profiles.alertDesc')"
    />

    <el-card shadow="never" class="card">
      <template #header>
        <span class="hdr">{{ t("views.profiles.embedCardTitle") }}</span>
      </template>
      <p class="sub" v-html="t('views.profiles.embedHelp')" />
      <el-form label-width="160px">
        <el-form-item :label="t('views.profiles.embedLabel')">
          <el-select
            v-model="embedModelId"
            clearable
            filterable
            :loading="savingEmbed"
            :placeholder="t('views.profiles.embedPh')"
            style="width: 100%; max-width: 520px"
            @change="onEmbeddingModelChange"
          >
            <el-option
              v-for="m in vectorModels"
              :key="m.id"
              :label="vectorModelOptionLabel(m)"
              :value="m.id"
              :disabled="!m.active"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="hdr-row">
          <span class="hdr">{{ t("views.profiles.listTitle") }}</span>
          <div class="filters">
            <el-input
              v-model="keyword"
              clearable
              :placeholder="t('views.profiles.keywordPh')"
              style="width: 220px"
              @keyup.enter="reload"
            />
            <el-button type="primary" plain :loading="loading" @click="reload">{{ t("views.profiles.query") }}</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" stripe border :empty-text="t('views.profiles.empty')">
        <el-table-column prop="loginName" :label="t('views.profiles.colLoginName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="displayName" :label="t('views.profiles.colNickname')" min-width="120" />
        <el-table-column prop="profileTagCount" :label="t('views.profiles.colTagCount')" width="110" align="center" />
        <el-table-column :label="t('views.profiles.colHasAbstract')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.memoryAbstractPresent ? 'success' : 'info'" size="small">
              {{ row.memoryAbstractPresent ? t("views.profiles.yes") : t("views.profiles.no") }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memoryChunkCount" :label="t('views.profiles.colChunkCount')" width="110" align="center" />
        <el-table-column :label="t('views.profiles.colActions')" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.userId)">{{ t("views.profiles.view") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailOpen" :title="detailDlgTitle" width="860px" destroy-on-close class="profile-detail-dlg">
      <template v-if="detail">
        <div class="detail-wrap">
            <h3 class="detail-sec-title">{{ t("views.profiles.secBasic") }}</h3>
            <el-descriptions :column="2" border size="small" class="detail-desc">
              <el-descriptions-item :label="t('views.profiles.descMemoryTotal')">{{ detail.memoryChunkTotal }}</el-descriptions-item>
              <el-descriptions-item :label="t('views.profiles.colLoginName')">{{ detail.loginName || t("common.dash") }}</el-descriptions-item>
              <el-descriptions-item :label="t('views.profiles.colNickname')">{{ detail.displayName || t("common.dash") }}</el-descriptions-item>
            </el-descriptions>

            <h3 class="detail-sec-title">{{ t("views.profiles.secTags") }}</h3>
            <p class="detail-hint">{{ t("views.profiles.tagsHint") }}</p>
            <el-table
              v-if="detail.profileTags.length"
              :data="detail.profileTags"
              border
              size="small"
              class="detail-table"
            >
              <el-table-column :label="t('views.profiles.colMeaning')" min-width="140">
                <template #default="{ row }">{{ profileTagTitle(row.code) }}</template>
              </el-table-column>
              <el-table-column prop="code" :label="t('views.profiles.colTagCode')" width="160" show-overflow-tooltip />
              <el-table-column :label="t('views.profiles.colCurrentValue')" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.value || t("common.dash") }}</template>
              </el-table-column>
              <el-table-column :label="t('views.profiles.colDesc')" min-width="220">
                <template #default="{ row }">{{ profileTagDescription(row.code) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('views.profiles.emptyTags')" :image-size="64" />

            <h3 class="detail-sec-title">{{ t("views.profiles.secAbstract") }}</h3>
            <p class="detail-hint">{{ t("views.profiles.abstractHint") }}</p>
            <el-alert
              v-if="detail.memoryAbstractRaw"
              type="warning"
              :closable="false"
              show-icon
              :title="t('views.profiles.abstractParseErrTitle')"
              :description="t('views.profiles.abstractParseErrDesc')"
              class="detail-alert"
            />
            <pre v-if="detail.memoryAbstractRaw" class="json-pre">{{ detail.memoryAbstractRaw }}</pre>
            <template v-else-if="detail.memoryAbstract == null">
              <el-empty :description="t('views.profiles.emptyAbstract')" :image-size="64" />
            </template>
            <template v-else-if="!isPlainObject(detail.memoryAbstract)">
              <el-alert type="info" :closable="false" show-icon :title="t('views.profiles.abstractNotObjectTitle')" class="detail-alert" />
              <pre class="json-pre">{{ formatUnknownJson(detail.memoryAbstract) }}</pre>
            </template>
            <div v-else class="abs-blocks">
              <div v-for="block in abstractBlocks" :key="block.key" class="abs-block">
                <div class="abs-label">{{ block.label }}</div>
                <div class="abs-body">
                  <template v-if="block.value === null || block.value === undefined">
                    <span class="muted">{{ t("views.profiles.emptyParen") }}</span>
                  </template>
                  <template
                    v-else-if="
                      typeof block.value === 'string' ||
                      typeof block.value === 'number' ||
                      typeof block.value === 'boolean'
                    "
                  >
                    <p class="abs-text">{{ String(block.value) }}</p>
                  </template>
                  <template v-else-if="Array.isArray(block.value)">
                    <ul v-if="block.value.length && isArrayOfPrimitives(block.value)" class="plain-list">
                      <li v-for="(item, idx) in block.value" :key="idx">{{ formatPrimitive(item) }}</li>
                    </ul>
                    <pre v-else-if="block.value.length" class="json-snippet">{{ formatUnknownJson(block.value) }}</pre>
                    <span v-else class="muted">{{ t("views.profiles.emptyList") }}</span>
                  </template>
                  <template v-else-if="isPlainObject(block.value)">
                    <el-descriptions :column="1" border size="small" class="nested-desc">
                      <el-descriptions-item
                        v-for="[k, vv] in sortedObjectEntries(block.value)"
                        :key="k"
                        :label="k"
                      >
                        {{ formatLeafForAdmin(vv) }}
                      </el-descriptions-item>
                    </el-descriptions>
                  </template>
                  <pre v-else class="json-snippet">{{ formatUnknownJson(block.value) }}</pre>
                </div>
              </div>
            </div>

            <h3 class="detail-sec-title">{{ t("views.profiles.secRecent") }}</h3>
            <p class="detail-hint">{{ t("views.profiles.recentHint") }}</p>
            <el-table
              v-if="detail.recentMemoryChunks.length"
              :data="detail.recentMemoryChunks"
              border
              size="small"
              class="detail-table"
            >
              <el-table-column :label="t('views.profiles.colTime')" width="168" show-overflow-tooltip>
                <template #default="{ row }">{{ row.createdAt || t("common.dash") }}</template>
              </el-table-column>
              <el-table-column :label="t('views.profiles.colRole')" width="88" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.chunkRole === 'ASSISTANT' ? 'info' : undefined">
                    {{ chunkRoleLabel(row.chunkRole) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('views.profiles.colSnippet')" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">{{ row.snippet || t("common.dash") }}</template>
              </el-table-column>
              <el-table-column :label="t('views.profiles.colConvId')" width="100" align="center">
                <template #default="{ row }">{{ row.conversationId ?? t("common.dash") }}</template>
              </el-table-column>
              <el-table-column prop="id" :label="t('views.profiles.colChunkId')" width="90" align="center" />
            </el-table>
            <el-empty v-else :description="t('views.profiles.emptyRecent')" :image-size="64" />

            <h3 class="detail-sec-title">{{ t("views.profiles.secWeeklyTest") }}</h3>
            <p class="detail-hint">{{ t("views.profiles.weeklyTestHint") }}</p>
            <el-form label-width="0" class="weekly-test-form">
              <el-form-item>
                <el-checkbox v-model="weeklyTestPersist">{{ t("views.profiles.weeklyTestPersist") }}</el-checkbox>
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="weeklyTestSendEmail">{{ t("views.profiles.weeklyTestSendEmail") }}</el-checkbox>
              </el-form-item>
              <el-form-item :label="t('views.profiles.weeklyTestWeekStart')" label-width="140px">
                <el-date-picker
                  v-model="weeklyTestWeekStart"
                  type="date"
                  value-format="YYYY-MM-DD"
                  clearable
                  :placeholder="t('views.profiles.weeklyTestWeekStartPh')"
                  style="width: 220px"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="warning"
                  plain
                  :loading="weeklyTestPushing"
                  :disabled="detailUserId == null"
                  @click="runWeeklyTestPush"
                >
                  {{ weeklyTestPushing ? t("views.profiles.weeklyTestPushing") : t("views.profiles.weeklyTestPush") }}
                </el-button>
              </el-form-item>
            </el-form>

            <div class="raw-json-toggle">
              <el-button text type="primary" @click="showRawJson = !showRawJson">
                {{ showRawJson ? t("views.profiles.rawJsonHide") : t("views.profiles.rawJsonShow") }}{{ t("views.profiles.rawJsonSuffix") }}
              </el-button>
            </div>
            <pre v-show="showRawJson" class="json-pre">{{ detailRawJson }}</pre>
          </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import * as userProfilesApi from "@/api/userProfiles";
import type { AdminUserProfileDetail } from "@/api/userProfiles";
import {
  chunkRoleLabel,
  formatLeafForAdmin,
  formatUnknownJson,
  isArrayOfPrimitives,
  isPlainObject,
  memoryAbstractRows,
  profileTagDescription,
  profileTagTitle,
  sortedObjectEntries,
} from "@/utils/userProfileDetailSemantics";

const { t, locale } = useI18n();

const loading = ref(false);
const savingEmbed = ref(false);
const rows = ref<userProfilesApi.UserProfileSummaryRow[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const keyword = ref("");

const embedModelId = ref<number | undefined>(undefined);
const vectorModels = ref<userProfilesApi.VectorModelOption[]>([]);
/** 避免首次拉取配置写入 v-model 时触发自动保存 */
const embeddingHydrateSkipSave = ref(true);

const detailOpen = ref(false);
const detail = ref<AdminUserProfileDetail | null>(null);
const detailUserId = ref<number | null>(null);
const detailRawPayload = ref<unknown>(null);
const showRawJson = ref(false);

const weeklyTestPersist = ref(false);
const weeklyTestSendEmail = ref(true);
const weeklyTestWeekStart = ref<string | undefined>(undefined);
const weeklyTestPushing = ref(false);

const abstractBlocks = computed(() => {
  void locale.value;
  const d = detail.value;
  if (!d || d.memoryAbstractRaw || d.memoryAbstract == null) return [];
  if (!isPlainObject(d.memoryAbstract)) return [];
  return memoryAbstractRows(d.memoryAbstract);
});

const detailRawJson = computed(() => {
  if (detailRawPayload.value == null) return "";
  try {
    return JSON.stringify(detailRawPayload.value, null, 2);
  } catch {
    return String(detailRawPayload.value);
  }
});

const detailDlgTitle = computed(() => {
  const d = detail.value;
  if (!d) return t("views.profiles.dlgTitle");
  const name = (d.displayName || d.loginName || "").trim();
  return name ? `${t("views.profiles.dlgTitle")} · ${name}` : t("views.profiles.dlgTitle");
});

function formatPrimitive(v: unknown): string {
  if (v === null || v === undefined) return t("common.dash");
  return String(v);
}

function vectorModelOptionLabel(m: userProfilesApi.VectorModelOption): string {
  const dn = (m.displayName ?? "").trim();
  const al = (m.alias ?? "").trim();
  let base: string;
  if (dn && al && dn !== al) {
    base = `${dn} · ${al}`;
  } else {
    base = dn || al || t("views.profiles.unnamedModel");
  }
  return m.active ? base : `${base}${t("views.profiles.inactiveSuffix")}`;
}

async function loadEmbedding() {
  embeddingHydrateSkipSave.value = true;
  try {
    const v = await userProfilesApi.getMemoryEmbeddingModel();
    vectorModels.value = v.vectorModels || [];
    embedModelId.value = v.selectedLlmModelId == null ? undefined : v.selectedLlmModelId;
  } finally {
    await nextTick();
    embeddingHydrateSkipSave.value = false;
  }
}

async function onEmbeddingModelChange() {
  if (embeddingHydrateSkipSave.value) {
    return;
  }
  savingEmbed.value = true;
  try {
    await userProfilesApi.putMemoryEmbeddingModel({
      llmModelId: embedModelId.value == null ? null : embedModelId.value,
    });
    ElMessage.success(t("views.profiles.saved"));
    await loadEmbedding();
  } catch {
    ElMessage.error(t("views.profiles.saveFailed"));
    await loadEmbedding();
  } finally {
    savingEmbed.value = false;
  }
}

async function loadList() {
  loading.value = true;
  try {
    const res = await userProfilesApi.listUserProfiles({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    });
    rows.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

function reload() {
  page.value = 1;
  void loadList();
}

function onPage(p: number) {
  page.value = p;
  void loadList();
}

async function openDetail(userId: number) {
  try {
    showRawJson.value = false;
    weeklyTestPersist.value = false;
    weeklyTestSendEmail.value = true;
    weeklyTestWeekStart.value = undefined;
    const { raw, detail: d } = await userProfilesApi.fetchUserProfileDetail(userId);
    detailRawPayload.value = raw;
    detail.value = d;
    detailUserId.value = userId;
    detailOpen.value = true;
  } catch {
    ElMessage.error(t("views.profiles.loadDetailFailed"));
  }
}

async function runWeeklyTestPush() {
  const uid = detailUserId.value;
  if (uid == null) return;
  weeklyTestPushing.value = true;
  try {
    const res = await userProfilesApi.postKnowledgePlanetWeeklyTestPush(uid, {
      persist: weeklyTestPersist.value,
      sendEmail: weeklyTestSendEmail.value,
      weekStart: weeklyTestWeekStart.value?.trim() || undefined,
    });
    const computeLine = `${t("views.profiles.weeklyTestCompute")}: ${res.computeStatus}${res.computeMessage ? ` — ${res.computeMessage}` : ""}${res.persisted ? " ✓" : ""}`;
    const emailLine =
      res.emailStatus != null
        ? `${t("views.profiles.weeklyTestEmail")}: ${res.emailStatus}${res.emailMessage ? ` — ${res.emailMessage}` : ""}${res.emailRecipient ? ` → ${res.emailRecipient}` : ""}`
        : "";
    ElMessage.success({
      message: [computeLine, emailLine].filter(Boolean).join("\n"),
      duration: 8000,
      showClose: true,
    });
  } catch {
    ElMessage.error(t("views.profiles.weeklyTestFailed"));
  } finally {
    weeklyTestPushing.value = false;
  }
}

onMounted(() => {
  void loadEmbedding();
  void loadList();
});
</script>

<style scoped>
.user-profiles {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hint {
  margin-bottom: 0;
}
.card {
  border-radius: 10px;
}
.hdr-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.hdr {
  font-weight: 600;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sub {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.json-pre {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-wrap {
  padding-right: 8px;
}

.detail-sec-title {
  margin: 16px 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-sec-title:first-child {
  margin-top: 0;
}

.detail-hint {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.detail-desc {
  margin-bottom: 4px;
}

.detail-table {
  width: 100%;
}

.detail-alert {
  margin-bottom: 10px;
}

.abs-blocks {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.abs-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.abs-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.abs-body {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.abs-text {
  margin: 0;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.plain-list {
  margin: 0;
  padding-left: 1.2em;
  line-height: 1.65;
}

.muted {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.json-snippet {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.nested-desc {
  margin-top: 2px;
}

.weekly-test-form {
  max-width: 520px;
}

.raw-json-toggle {
  margin-top: 18px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color);
}
</style>
