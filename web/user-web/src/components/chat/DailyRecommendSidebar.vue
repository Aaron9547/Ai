<template>
  <aside
    class="rec-panel"
    :class="{
      'rec-panel--collapsed': collapsed,
      'rec-panel--planet': planetSummary?.enabled,
    }"
    :aria-label="t('dailyRecommend.panelAria')"
  >
    <div class="rec-clip">
      <div class="rec-inner">
      <div class="rec-body" :aria-hidden="collapsed">
      <!-- 思维核心：固定星球视窗 -->
      <KnowledgePlanetCard
        v-if="planetSummary?.enabled"
        :summary="planetSummary"
        @open="openPlanet"
      />

      <!-- 外界感知：可滚动画像推荐 -->
      <div class="rec-recommend-body">
        <header class="rec-recommend-head">
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

        <div class="rec-recommend-scroll">
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

      <div class="rec-rail-layer" :aria-hidden="!collapsed">
        <div class="rec-collapsed">
          <button
            type="button"
            class="rec-rail"
            :aria-label="t('dailyRecommend.expand')"
            @click="collapsed = false"
          >
            <el-icon :size="20"><Reading /></el-icon>
          </button>
        </div>
      </div>
    </div>
    </div>

    <SidebarCollapseTab
      v-if="!collapsed"
      side="right"
      :label="t('dailyRecommend.collapse')"
      @click="collapsed = true"
    />

    <KnowledgePlanetOverlay
      :visible="planetOpen"
      :universe="planetUniverse"
      :weekly="planetWeekly"
      :origin-rect="planetOriginRect"
      @close="planetOpen = false"
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
import KnowledgePlanetCard from "./KnowledgePlanetCard.vue";
import KnowledgePlanetOverlay from "./KnowledgePlanetOverlay.vue";
import {
  fetchKnowledgePlanetSummary,
  fetchKnowledgePlanetUniverse,
  fetchKnowledgePlanetWeeklyLatest,
  type KnowledgePlanetSummary,
  type KnowledgePlanetUniverse,
  type KnowledgePlanetWeeklyLatest,
  type KnowledgePlanetWarpOrigin,
} from "../../api/knowledgePlanet";

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
const planetSummary = ref<KnowledgePlanetSummary | null>(null);
const planetOpen = ref(false);
const planetOriginRect = ref<KnowledgePlanetWarpOrigin | null>(null);
const planetUniverse = ref<KnowledgePlanetUniverse | null>(null);
const planetWeekly = ref<KnowledgePlanetWeeklyLatest | null>(null);

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
      void loadPlanetSummary();
    }
  },
);

async function loadPlanetSummary(): Promise<void> {
  try {
    planetSummary.value = await fetchKnowledgePlanetSummary();
  } catch {
    planetSummary.value = null;
  }
}

async function openPlanet(origin: KnowledgePlanetWarpOrigin): Promise<void> {
  planetOriginRect.value = origin;
  planetOpen.value = true;
  try {
    const [u, w] = await Promise.all([
      fetchKnowledgePlanetUniverse(),
      fetchKnowledgePlanetWeeklyLatest().catch(() => ({
        weekStart: null,
        status: null,
        plan: null,
      })),
    ]);
    planetUniverse.value = u;
    planetWeekly.value = w;
  } catch {
    planetUniverse.value = { planets: [], nodes: [], links: [] };
  }
}

void loadPlanetSummary();

</script>

<style scoped>
.rec-panel {
  --rec-width-expanded: 300px;
  --rec-width-collapsed: 40px;
  position: relative;
  flex: 0 0 var(--rec-width-expanded);
  width: var(--rec-width-expanded);
  min-width: 0;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--rec-bg-panel, #fcfdfe);
  border-left: 1px solid var(--rec-border, #e8ecef);
  box-sizing: border-box;
  overflow: visible;
  transition:
    width var(--chat-shell-duration, 0.42s) var(--chat-shell-ease, cubic-bezier(0.32, 0.72, 0, 1)),
    flex-basis var(--chat-shell-duration, 0.42s) var(--chat-shell-ease, cubic-bezier(0.32, 0.72, 0, 1)),
    background-color var(--chat-shell-duration, 0.42s) var(--chat-shell-ease, cubic-bezier(0.32, 0.72, 0, 1)),
    border-color var(--chat-shell-duration, 0.42s) var(--chat-shell-ease, cubic-bezier(0.32, 0.72, 0, 1));
}

.rec-clip {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  overflow: hidden;
}

.rec-panel--planet:not(.rec-panel--collapsed) {
  --rec-width-expanded: 320px;
  background: var(--rec-bg-panel-planet, #f4f7fa);
  border-left-color: var(--rec-border, #dde3ea);
}

.rec-panel--collapsed {
  flex-basis: var(--rec-width-collapsed);
  width: var(--rec-width-collapsed);
  background: var(--rec-bg-collapsed, #f5f8fc);
}

.rec-inner {
  position: relative;
  width: var(--rec-width-expanded);
  height: 100%;
  min-height: 0;
  flex: 1;
}

.rec-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  opacity: 1;
  transition: opacity 0.32s var(--chat-shell-ease, cubic-bezier(0.4, 0, 0.2, 1)) 0.12s;
}

.rec-panel--collapsed .rec-body {
  opacity: 0;
  pointer-events: none;
  transition-delay: 0s;
}

.rec-rail-layer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--rec-width-collapsed);
  display: flex;
  flex-direction: column;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.32s var(--chat-shell-ease, cubic-bezier(0.4, 0, 0.2, 1)) 0.06s;
}

.rec-panel--collapsed .rec-rail-layer {
  opacity: 1;
  pointer-events: auto;
}

.rec-collapsed {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 12px 0 10px;
  box-sizing: border-box;
}

.rec-rail {
  flex: 1;
  width: 100%;
  min-height: 44px;
  border: none;
  background: transparent;
  color: var(--rec-rail-color, #5a7a94);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.rec-rail:hover {
  background: var(--rec-hover-bg, #eef4fa);
  color: var(--rec-text-link, #3d6f94);
}

.rec-recommend-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--rec-bg-body, rgba(252, 253, 254, 0.92));
  backdrop-filter: blur(8px);
  border-top: 1px solid var(--rec-border, #e8ecef);
}

.rec-panel--planet .rec-recommend-body {
  margin-top: 4px;
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.04);
}

.rec-recommend-head {
  flex-shrink: 0;
  min-height: 50px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--rec-border-subtle, #eef1f4);
}

.rec-header-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rec-header-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--rec-text-title, #2c3e50);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rec-header-sub {
  font-size: 11px;
  line-height: 1.2;
  color: var(--rec-text-muted, #a0adb8);
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
  color: var(--rec-icon, #6b7c8a);
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.rec-icon-btn:hover:not(:disabled) {
  background: var(--rec-hover-bg, #eef4fa);
  color: var(--rec-text-link, #3d6f94);
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

.rec-recommend-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: var(--rec-scrollbar, #d0d8e0) transparent;
}

.rec-recommend-scroll::-webkit-scrollbar {
  width: 5px;
}

.rec-recommend-scroll::-webkit-scrollbar-thumb {
  background: var(--rec-scrollbar, #d0d8e0);
  border-radius: 3px;
}

.rec-list {
  list-style: none;
  margin: 0;
  padding: 10px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rec-center-state {
  min-height: 160px;
  padding: 24px 16px;
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
  color: var(--rec-text-title, #2c3e50);
}

.rec-center-msg {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--rec-text-body, #7a8794);
  max-width: 220px;
}

.rec-center-link {
  margin-top: 4px;
  font-size: 12px;
  color: var(--rec-text-link, #3d6f94);
  text-decoration: none;
}

.rec-center-link:hover {
  text-decoration: underline;
}

.rec-retry-btn {
  margin-top: 6px;
  padding: 6px 16px;
  border: 1px solid var(--rec-btn-border, #c5d9eb);
  border-radius: 8px;
  background: var(--rec-btn-bg, #fff);
  color: var(--rec-text-link, #3d6f94);
  font-size: 13px;
  cursor: pointer;
}

.rec-retry-btn:hover:not(:disabled) {
  background: var(--rec-hover-bg, #f0f6fb);
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
  color: var(--rec-text-hint, #b0bcc6);
  border-top: 1px solid var(--rec-border-subtle, #eef1f4);
  background: var(--rec-bg-footer, #fafbfc);
}
</style>
