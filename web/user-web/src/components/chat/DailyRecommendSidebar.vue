<template>
  <aside
    class="rec-panel"
    :class="{ 'rec-panel--collapsed': collapsed }"
    :aria-label="t('dailyRecommend.panelAria')"
  >
    <div class="rec-pane rec-pane--narrow" :aria-hidden="!collapsed">
      <button
        type="button"
        class="rec-rail"
        :aria-label="t('dailyRecommend.expand')"
        @click="collapsed = false"
      >
        <el-icon :size="20"><Reading /></el-icon>
      </button>
    </div>

    <div class="rec-pane rec-pane--wide" :aria-hidden="collapsed">
      <header class="rec-header">
        <div class="rec-header-text">
          <h2 class="rec-header-title">{{ t("dailyRecommend.title") }}</h2>
          <span class="rec-header-sub">{{ t("dailyRecommend.oncePerDay") }}</span>
        </div>
        <div class="rec-header-actions">
          <button
            type="button"
            class="rec-icon-btn"
            :disabled="refreshing || status === 'loading'"
            :title="refreshTitle"
            :aria-label="t('dailyRecommend.refresh')"
            @click="onRefresh"
          >
            <el-icon :size="16" :class="{ 'rec-spin': refreshing }"><Refresh /></el-icon>
          </button>
        </div>
      </header>

      <div class="rec-main">
        <div class="rec-main-scroll">
          <DailyRecommendSkeleton v-if="status === 'loading'" :count="3" />

          <div v-else-if="status === 'error'" class="rec-center-state">
            <p class="rec-center-msg">{{ errorMessage || t("dailyRecommend.errorDefault") }}</p>
            <button
              v-if="retryAllowed"
              type="button"
              class="rec-retry-btn"
              :disabled="retrying"
              @click="retry()"
            >
              {{ t("dailyRecommend.retry") }}
            </button>
          </div>

          <div v-else-if="status === 'empty'" class="rec-center-state">
            <p class="rec-center-title">{{ t("dailyRecommend.emptyTitle") }}</p>
            <p class="rec-center-msg">{{ t("dailyRecommend.emptyHint") }}</p>
            <RouterLink v-if="mePagePath" class="rec-center-link" :to="mePagePath">
              {{ t("dailyRecommend.profileLink") }}
            </RouterLink>
          </div>

          <ul v-else class="rec-list" role="list">
            <li v-for="item in recommendations" :key="item.id" role="listitem">
              <DailyRecommendCard :item="item" />
            </li>
          </ul>
        </div>
      </div>

      <footer class="rec-footer">{{ t("dailyRecommend.footerHint") }}</footer>
    </div>

    <SidebarCollapseTab
      v-if="!collapsed"
      side="right"
      :label="t('dailyRecommend.collapse')"
      @click="collapsed = true"
    />
  </aside>
</template>

<script setup lang="ts">
import { Reading, Refresh } from "@element-plus/icons-vue";
import SidebarCollapseTab from "./SidebarCollapseTab.vue";
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useDailyRecommend } from "../../composables/useDailyRecommend";
import DailyRecommendCard from "./DailyRecommendCard.vue";
import DailyRecommendSkeleton from "./DailyRecommendSkeleton.vue";

const props = withDefaults(
  defineProps<{
    mePagePath?: string;
    defaultCollapsed?: boolean;
    authBump?: number;
  }>(),
  { mePagePath: "", defaultCollapsed: false, authBump: 0 },
);

const collapsed = defineModel<boolean>("collapsed", { default: false });

const { t } = useI18n();
const refreshing = ref(false);

const {
  status,
  recommendations,
  errorMessage,
  retryAllowed,
  refreshAllowed,
  retrying,
  retry,
  refresh,
  reloadAfterLogin,
} = useDailyRecommend();

const refreshTitle = computed(() => {
  if (refreshAllowed.value) return t("dailyRecommend.refreshRetry");
  return t("dailyRecommend.refreshDone");
});

async function onRefresh(): Promise<void> {
  if (refreshing.value || status.value === "loading") return;
  refreshing.value = true;
  try {
    const outcome = await refresh();
    if (outcome === "noop") {
      ElMessage.info(t("dailyRecommend.refreshNoop"));
    }
  } finally {
    refreshing.value = false;
  }
}

watch(
  () => props.defaultCollapsed,
  (v) => {
    collapsed.value = v;
  },
  { immediate: true },
);

watch(
  () => props.authBump,
  (n, prev) => {
    if (n > 0 && n !== prev) {
      void reloadAfterLogin();
    }
  },
);

</script>

<style scoped>
.rec-panel {
  --rec-width-expanded: 300px;
  --rec-width-collapsed: 40px;
  --rec-ease: cubic-bezier(0.4, 0, 0.2, 1);
  --rec-duration: 0.28s;

  position: relative;
  flex-shrink: 0;
  width: var(--rec-width-expanded);
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  border-left: 1px solid #e8ecef;
  box-sizing: border-box;
  overflow: visible;
  transition: width var(--rec-duration) var(--rec-ease);
}

.rec-panel--collapsed {
  width: var(--rec-width-collapsed);
  background: #f5f8fc;
}

.rec-pane {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    opacity 0.22s var(--rec-ease),
    visibility 0.22s var(--rec-ease);
}

.rec-pane--wide {
  width: var(--rec-width-expanded);
}

.rec-pane--narrow {
  width: var(--rec-width-collapsed);
}

.rec-panel:not(.rec-panel--collapsed) .rec-pane--wide {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
}

.rec-panel--collapsed .rec-pane--narrow {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .rec-panel,
  .rec-pane {
    transition: none;
  }
}

.rec-rail {
  flex: 1;
  width: 100%;
  border: none;
  background: transparent;
  color: #5a7a94;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.rec-rail:hover {
  background: #eef4fa;
  color: #3d6f94;
}

.rec-header {
  flex-shrink: 0;
  min-height: 50px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #e8ecef;
  background: #fafbfc;
}

.rec-header-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rec-header-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
  color: #2c3e50;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rec-header-sub {
  font-size: 11px;
  line-height: 1.2;
  color: #a0adb8;
}

.rec-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.rec-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6b7c8a;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.rec-icon-btn:hover:not(:disabled) {
  background: #eef4fa;
  color: #3d6f94;
}

.rec-icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.rec-spin {
  animation: rec-spin 0.9s linear infinite;
}

@keyframes rec-spin {
  to {
    transform: rotate(360deg);
  }
}

.rec-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.rec-main-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: #d0d8e0 #fafbfc;
}

.rec-main-scroll::-webkit-scrollbar {
  width: 5px;
}

.rec-main-scroll::-webkit-scrollbar-thumb {
  background: #d0d8e0;
  border-radius: 3px;
}

.rec-list {
  list-style: none;
  margin: 0;
  padding: 12px 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rec-center-state {
  min-height: 200px;
  padding: 28px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px;
}

.rec-center-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2c3e50;
}

.rec-center-msg {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #7a8794;
  max-width: 220px;
}

.rec-center-link {
  margin-top: 4px;
  font-size: 12px;
  color: #3d6f94;
  text-decoration: none;
}

.rec-center-link:hover {
  text-decoration: underline;
}

.rec-retry-btn {
  margin-top: 6px;
  padding: 6px 16px;
  border: 1px solid #c5d9eb;
  border-radius: 8px;
  background: #fff;
  color: #3d6f94;
  font-size: 13px;
  cursor: pointer;
}

.rec-retry-btn:hover:not(:disabled) {
  background: #f0f6fb;
}

.rec-retry-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.rec-footer {
  flex-shrink: 0;
  min-height: 30px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  font-size: 11px;
  color: #b0bcc6;
  border-top: 1px solid #eef1f4;
  background: #fafbfc;
}
</style>
