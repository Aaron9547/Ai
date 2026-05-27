<template>
  <div class="prompt-page">
    <section class="hero">
      <div class="hero-text">
        <p class="eyebrow">{{ t("views.promptTemplate.eyebrow") }}</p>
        <h1>{{ t("views.promptTemplate.title") }}</h1>
        <p class="hint">{{ t("views.promptTemplate.hint") }}</p>
      </div>
      <div class="hero-actions">
        <div class="hero-btns">
          <el-button @click="router.push('/chat/starter-prompts')">
            {{ t("views.promptTemplate.goStarter") }}
          </el-button>
          <el-button type="primary" plain :loading="loading" @click="reload">
            {{ t("common.refresh") }}
          </el-button>
          <el-button type="primary" @click="openCreate()">
            {{ t("views.promptTemplate.create") }}
          </el-button>
        </div>
        <div class="cache-bar" :title="t('views.promptTemplate.cacheHelp')">
          <span class="cache-mini" :class="{ muted: !cacheStats.redisEnabled || !cacheHasActivity }">
            {{ cacheStatusLine }}
          </span>
          <el-input
            v-model="evictCode"
            size="small"
            clearable
            :disabled="!cacheStats.redisEnabled"
            :placeholder="t('views.promptTemplate.evictCodePh')"
            class="cache-input"
          />
          <el-button size="small" :disabled="!cacheStats.redisEnabled" @click="evictByCode">
            {{ t("views.promptTemplate.evictCode") }}
          </el-button>
          <el-button
            size="small"
            type="warning"
            plain
            :disabled="!cacheStats.redisEnabled"
            @click="evictAll"
          >
            {{ t("views.promptTemplate.evictAllShort") }}
          </el-button>
        </div>
      </div>
    </section>

    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-label">{{ t("views.promptTemplate.statTotal") }}</span>
        <strong class="stat-value">{{ stats.total }}</strong>
      </div>
      <div class="stat-card stat-platform">
        <span class="stat-label">{{ t("views.promptTemplate.statPlatform") }}</span>
        <strong class="stat-value">{{ stats.platform }}</strong>
      </div>
      <div class="stat-card stat-tenant">
        <span class="stat-label">{{ t("views.promptTemplate.statTenant") }}</span>
        <strong class="stat-value">{{ stats.tenant }}</strong>
      </div>
    </div>

    <el-alert
      v-if="!loading && rows.length === 0"
      type="warning"
      :closable="false"
      show-icon
      class="empty-alert"
    >
      <template #title>{{ t("views.promptTemplate.emptyTitle") }}</template>
      {{ t("views.promptTemplate.emptyBody") }}
    </el-alert>

    <el-card shadow="never" class="panel">
      <div class="toolbar">
        <el-radio-group v-model="scopeFilter" size="default" @change="onScopeChange">
          <el-radio-button value="all">{{ t("views.promptTemplate.scopeAll") }}</el-radio-button>
          <el-radio-button value="platform">{{ t("views.promptTemplate.scopePlatform") }}</el-radio-button>
          <el-radio-button value="tenant">{{ t("views.promptTemplate.scopeTenant") }}</el-radio-button>
        </el-radio-group>
        <el-form inline class="filters">
          <el-form-item :label="t('views.promptTemplate.filterDomain')">
            <el-select v-model="filterDomain" clearable style="width: 130px" @change="reload">
              <el-option
                v-for="d in domainOptions"
                :key="d"
                :label="t(`views.promptTemplate.domain.${d}`)"
                :value="d"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('views.promptTemplate.filterKind')">
            <el-select v-model="filterKind" clearable style="width: 130px" @change="reload">
              <el-option
                v-for="k in kindOptions"
                :key="k"
                :label="t(`views.promptTemplate.kind.${k}`)"
                :value="k"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('views.promptTemplate.filterLocale')">
            <el-select v-model="filterLocale" clearable style="width: 148px" @change="reload">
              <el-option
                v-for="loc in localeOptions"
                :key="loc"
                :label="localeLabel(loc)"
                :value="loc"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="codeSearch"
              clearable
              :prefix-icon="Search"
              :placeholder="t('views.promptTemplate.searchCode')"
              style="width: 220px"
            />
          </el-form-item>
        </el-form>
      </div>

      <div class="body-grid">
        <el-table
          v-loading="loading"
          :data="displayRows"
          stripe
          class="data-table"
          highlight-current-row
          :empty-text="t('views.promptTemplate.tableEmpty')"
          @current-change="onSelectRow"
        >
          <el-table-column :label="t('views.promptTemplate.colScope')" width="108">
            <template #default="{ row }">
              <el-tag
                :type="row.platformDefault ? 'info' : 'warning'"
                size="small"
                effect="light"
                round
              >
                {{
                  row.platformDefault
                    ? t("views.promptTemplate.badgePlatform")
                    : t("views.promptTemplate.badgeTenant")
                }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="promptCode" :label="t('views.promptTemplate.colCode')" min-width="200">
            <template #default="{ row }">
              <code class="code-pill">{{ row.promptCode }}</code>
            </template>
          </el-table-column>
          <el-table-column :label="t('views.promptTemplate.colKind')" width="120">
            <template #default="{ row }">
              <span class="kind-chip">{{ t(`views.promptTemplate.kind.${row.promptKind}`) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('views.promptTemplate.colDomain')" width="96">
            <template #default="{ row }">
              {{ t(`views.promptTemplate.domain.${row.domain}`) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('views.promptTemplate.colLocale')" width="120">
            <template #default="{ row }">
              {{ localeLabel(row.locale) }}
            </template>
          </el-table-column>
          <el-table-column prop="version" :label="t('views.promptTemplate.colVersion')" width="64" align="center" />
          <el-table-column :label="t('views.promptTemplate.colPreview')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="preview">{{ preview(row.content) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('views.promptTemplate.colEnabled')" width="88" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">
                {{ row.enabled ? t("common.enabled") : t("common.disabled") }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="248" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEdit(row)">{{ t("common.edit") }}</el-button>
              <el-button
                v-if="row.platformDefault"
                link
                type="primary"
                @click.stop="openOverride(row)"
              >
                {{ t("views.promptTemplate.override") }}
              </el-button>
              <el-button
                link
                type="warning"
                :disabled="!cacheStats.redisEnabled"
                @click.stop="evictRow(row)"
              >
                {{ t("views.promptTemplate.evictCode") }}
              </el-button>
              <el-button link type="danger" @click.stop="onDelete(row)">{{ t("common.delete") }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <aside class="detail" :class="{ 'detail--empty': !selectedRow }">
          <template v-if="selectedRow">
            <p class="detail-label">{{ t("views.promptTemplate.usageTitle") }}</p>
            <code class="detail-code">{{ selectedRow.promptCode }}</code>
            <p class="detail-hint">
              {{ usageHint(selectedRow.promptCode) ?? t("views.promptTemplate.usageFallback") }}
            </p>
            <el-divider />
            <p class="detail-label">{{ t("views.promptTemplate.previewFull") }}</p>
            <pre class="detail-pre">{{ selectedRow.content }}</pre>
          </template>
          <p v-else class="detail-placeholder">{{ t("views.promptTemplate.selectRowHint") }}</p>
        </aside>
      </div>
    </el-card>

    <prompt-template-edit-drawer
      v-model="drawerOpen"
      :row="editRow"
      :clone-from="cloneFrom"
      @saved="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  deletePromptTemplate,
  evictPromptTemplateCache,
  fetchPromptTemplateCacheStats,
  listPromptTemplates,
  type PromptTemplateDomain,
  type PromptTemplateKind,
  type PromptTemplateRow,
} from "@/api/promptTemplate";
import { PROMPT_TEMPLATE_LOCALES, promptTemplateLocaleLabel } from "./promptTemplateLocale";
import { promptTemplateUsageHint } from "./promptTemplateUsageHints";
import PromptTemplateEditDrawer from "./components/PromptTemplateEditDrawer.vue";

const { t } = useI18n();
const router = useRouter();

const loading = ref(false);
const rows = ref<PromptTemplateRow[]>([]);
const scopeFilter = ref<"all" | "platform" | "tenant">("all");
const filterDomain = ref<PromptTemplateDomain | undefined>();
const filterKind = ref<PromptTemplateKind | undefined>();
const filterLocale = ref<string | undefined>();
const codeSearch = ref("");
const selectedRow = ref<PromptTemplateRow | null>(null);
const drawerOpen = ref(false);
const editRow = ref<PromptTemplateRow | null>(null);
const cloneFrom = ref<PromptTemplateRow | null>(null);
const cacheStats = ref({
  redisEnabled: false,
  approximateKeyCount: 0,
  hits: 0,
  misses: 0,
});

const cacheHasActivity = computed(
  () =>
    cacheStats.value.approximateKeyCount > 0 ||
    cacheStats.value.hits > 0 ||
    cacheStats.value.misses > 0,
);

const cacheStatusLine = computed(() => {
  const s = cacheStats.value;
  if (!s.redisEnabled) {
    return t("views.promptTemplate.cacheHintRedisOff");
  }
  if (!cacheHasActivity.value) {
    return t("views.promptTemplate.cacheHintIdle");
  }
  return t("views.promptTemplate.cacheMini", {
    keys: s.approximateKeyCount,
    hits: s.hits,
    misses: s.misses,
  });
});
const evictCode = ref("");

const domainOptions: PromptTemplateDomain[] = [
  "CHAT",
  "MEMORY",
  "RAG",
  "WEB",
  "PLANET",
  "STARTER",
  "GUARD",
];
const kindOptions: PromptTemplateKind[] = ["SYSTEM", "USER", "FRAGMENT", "QUERY"];
const localeOptions = PROMPT_TEMPLATE_LOCALES;

function localeLabel(locale: string) {
  return promptTemplateLocaleLabel(locale, t);
}

const stats = computed(() => {
  const platform = rows.value.filter((r) => r.platformDefault).length;
  const tenant = rows.value.filter((r) => !r.platformDefault).length;
  return { total: rows.value.length, platform, tenant };
});

const displayRows = computed(() => {
  let list = rows.value;
  if (scopeFilter.value === "platform") {
    list = list.filter((r) => r.platformDefault);
  } else if (scopeFilter.value === "tenant") {
    list = list.filter((r) => !r.platformDefault);
  }
  const q = codeSearch.value.trim().toLowerCase();
  if (!q) return list;
  return list.filter((r) => r.promptCode.toLowerCase().includes(q));
});

function preview(content: string) {
  const text = content.replace(/\s+/g, " ").trim();
  return text.length > 72 ? `${text.slice(0, 72)}…` : text;
}

function usageHint(code: string) {
  return promptTemplateUsageHint(code);
}

function onSelectRow(row: PromptTemplateRow | null) {
  selectedRow.value = row;
  if (row) evictCode.value = row.promptCode;
}

function onScopeChange() {
  if (selectedRow.value) {
    const still = displayRows.value.find((r) => r.id === selectedRow.value?.id);
    if (!still) selectedRow.value = null;
  }
}

async function reload() {
  loading.value = true;
  try {
    rows.value = await listPromptTemplates({
      domain: filterDomain.value,
      promptKind: filterKind.value,
      locale: filterLocale.value,
    });
    if (selectedRow.value) {
      selectedRow.value = rows.value.find((r) => r.id === selectedRow.value?.id) ?? null;
    }
    await loadCacheStats();
  } catch {
    ElMessage.error(t("views.promptTemplate.loadFailed"));
  } finally {
    loading.value = false;
  }
}

async function loadCacheStats() {
  try {
    cacheStats.value = await fetchPromptTemplateCacheStats();
  } catch {
    /* optional */
  }
}

function openCreate() {
  editRow.value = null;
  cloneFrom.value = null;
  drawerOpen.value = true;
}

function openEdit(row: PromptTemplateRow) {
  editRow.value = row;
  cloneFrom.value = null;
  drawerOpen.value = true;
}

function openOverride(row: PromptTemplateRow) {
  editRow.value = null;
  cloneFrom.value = row;
  drawerOpen.value = true;
}

async function onDelete(row: PromptTemplateRow) {
  const msg = row.platformDefault
    ? t("views.promptTemplate.deletePlatformConfirm")
    : t("views.promptTemplate.deleteConfirm");
  try {
    await ElMessageBox.confirm(msg, { type: "warning" });
    await deletePromptTemplate(row.id);
    ElMessage.success(t("common.deleted"));
    await reload();
  } catch {
    /* cancel */
  }
}

async function evictAll() {
  await ElMessageBox.confirm(t("views.promptTemplate.evictAllConfirm"), { type: "warning" });
  await evictPromptTemplateCache();
  ElMessage.success(t("views.promptTemplate.evictDone"));
  await loadCacheStats();
}

async function evictByCode() {
  const code = evictCode.value.trim();
  if (!code) return;
  await evictPromptTemplateCache({ promptCode: code });
  ElMessage.success(t("views.promptTemplate.evictDone"));
  await loadCacheStats();
}

async function evictRow(row: PromptTemplateRow) {
  await evictPromptTemplateCache({ promptCode: row.promptCode, locale: row.locale });
  ElMessage.success(t("views.promptTemplate.evictDone"));
  await loadCacheStats();
}

onMounted(() => {
  reload();
});
</script>

<style scoped>
.prompt-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 24px;
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 20px 22px;
  border-radius: 12px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--el-color-primary) 8%, var(--el-bg-color)) 0%,
    var(--el-bg-color) 55%
  );
  border: 1px solid var(--el-border-color-lighter);
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--el-color-primary);
  font-weight: 600;
}

.hero h1 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.hint {
  margin: 0;
  max-width: 52rem;
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.hero-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.cache-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.cache-mini {
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.45;
  max-width: 280px;
}

.cache-mini.muted {
  color: var(--el-text-color-secondary);
}

.cache-input {
  width: 132px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.stat-platform {
  border-color: color-mix(in srgb, var(--el-color-info) 35%, var(--el-border-color-lighter));
}

.stat-tenant {
  border-color: color-mix(in srgb, var(--el-color-warning) 35%, var(--el-border-color-lighter));
}

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.empty-alert {
  margin: 0;
}

.panel {
  border-radius: 12px;
}

.panel :deep(.el-card__body) {
  padding: 16px 18px 18px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 16px;
  margin-bottom: 16px;
}

.filters {
  margin: 0;
}

.filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.body-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 320px);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1200px) {
  .body-grid {
    grid-template-columns: 1fr;
  }
  .detail {
    order: -1;
  }
}

.data-table {
  width: 100%;
}

.code-pill {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.kind-chip {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.preview {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.detail {
  padding: 16px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  min-height: 200px;
  max-height: 520px;
  overflow: auto;
}

.detail--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-label {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.detail-code {
  display: block;
  margin: 0 0 10px;
  font-size: 13px;
  word-break: break-all;
}

.detail-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
}

.detail-pre {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, Consolas, monospace;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  max-height: 280px;
  overflow: auto;
}

.detail-placeholder {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  text-align: center;
}

</style>
