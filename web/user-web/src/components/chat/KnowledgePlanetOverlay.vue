<template>
  <Teleport to="body">
    <div
      v-if="isPresent"
      class="kp-overlay"
      :class="{
        'kp-overlay--closing': isClosing,
        'kp-overlay--mobile': isMobileLayout,
      }"
      role="dialog"
      aria-modal="true"
      :aria-label="t('knowledgePlanet.overlayTitle')"
    >
      <div ref="warpBg" class="kp-warp-bg" aria-hidden="true" />
      <canvas
        ref="randomDotCanvas"
        class="kp-random-dot-canvas"
        :class="{ 'kp-random-dot-canvas--off': !introDotsActive }"
        aria-hidden="true"
      />
      <canvas ref="transitionCanvas" class="kp-transition-canvas" />

      <div ref="overlayRoot" class="kp-universe-root">
        <div class="kp-main">
          <header class="kp-toolbar">
            <button
              v-if="isMobileLayout"
              type="button"
              class="kp-mobile-back"
              :disabled="isClosing"
              :aria-label="t('knowledgePlanet.close')"
              @click="requestClose"
            >
              <el-icon :size="20"><ArrowLeft /></el-icon>
            </button>
            <button
              v-else
              ref="exitFab"
              type="button"
              class="kp-exit-fab"
              :disabled="isClosing"
              @click="requestClose"
            >
              <span class="kp-exit-icon">×</span>
              <span>{{ t("knowledgePlanet.exit") }}</span>
              <kbd class="kp-exit-kbd">Esc</kbd>
            </button>
            <h2 v-if="isMobileLayout" class="kp-mobile-title">{{ t("knowledgePlanet.overlayTitle") }}</h2>
            <div
              v-if="isMobileLayout && mainTab === 'map'"
              class="kp-render-toggle kp-render-toggle--head"
              role="group"
              :aria-label="t('knowledgePlanet.renderModeAria')"
            >
              <button
                type="button"
                class="kp-render-toggle__btn"
                :class="{ 'kp-render-toggle__btn--active': renderMode === '2d' }"
                :aria-pressed="renderMode === '2d'"
                @click="setRenderMode('2d')"
              >
                {{ t("knowledgePlanet.view2d") }}
              </button>
              <button
                type="button"
                class="kp-render-toggle__btn"
                :class="{ 'kp-render-toggle__btn--active': renderMode === '3d' }"
                :aria-pressed="renderMode === '3d'"
                @click="setRenderMode('3d')"
              >
                {{ t("knowledgePlanet.view3d") }}
              </button>
            </div>
            <nav class="kp-tabs" role="tablist">
              <button
                type="button"
                role="tab"
                class="kp-tab"
                :class="{ 'kp-tab--active': mainTab === 'map' }"
                :aria-selected="mainTab === 'map'"
                @click="mainTab = 'map'"
              >
                {{ t("knowledgePlanet.tabStarMap") }}
              </button>
              <button
                type="button"
                role="tab"
                class="kp-tab"
                :class="{ 'kp-tab--active': mainTab === 'archive' }"
                :aria-selected="mainTab === 'archive'"
                @click="mainTab = 'archive'"
              >
                {{ t("knowledgePlanet.tabArchive") }}
              </button>
            </nav>

            <div
              v-if="mainTab === 'map'"
              class="kp-toolbar-actions"
              :class="{ 'kp-toolbar-actions--mobile': isMobileLayout }"
            >
              <input
                v-model="searchQuery"
                type="search"
                class="kp-search"
                :placeholder="t('knowledgePlanet.searchPlaceholder')"
                @input="onSearchInput"
              />
              <button v-if="!isMobileLayout" type="button" class="kp-tool-btn" @click="fitStarMap">
                {{ t("knowledgePlanet.fitMap") }}
              </button>
              <div
                v-if="!isMobileLayout"
                class="kp-render-toggle"
                role="group"
                :aria-label="t('knowledgePlanet.renderModeAria')"
              >
                <button
                  type="button"
                  class="kp-render-toggle__btn"
                  :class="{ 'kp-render-toggle__btn--active': renderMode === '2d' }"
                  :aria-pressed="renderMode === '2d'"
                  @click="setRenderMode('2d')"
                >
                  {{ t("knowledgePlanet.view2d") }}
                </button>
                <button
                  type="button"
                  class="kp-render-toggle__btn"
                  :class="{ 'kp-render-toggle__btn--active': renderMode === '3d' }"
                  :aria-pressed="renderMode === '3d'"
                  @click="setRenderMode('3d')"
                >
                  {{ t("knowledgePlanet.view3d") }}
                </button>
              </div>
              <label v-if="selectedPlanet && !isMobileLayout" class="kp-focus-toggle">
                <input v-model="focusPlanetOnly" type="checkbox" @change="onFocusToggle" />
                {{ t("knowledgePlanet.focusPlanetOnly") }}
              </label>
            </div>

            <nav
              v-if="mainTab === 'map' && mapCanGoBack"
              class="kp-map-nav"
              :class="{ 'kp-map-nav--mobile': isMobileLayout }"
              :aria-label="t('knowledgePlanet.mapNavAria')"
            >
              <span v-if="mapBackCrumb" class="kp-map-nav-crumb">{{ mapBackCrumb }}</span>
              <template v-if="isMobileLayout">
                <button type="button" class="kp-map-nav-btn" @click="goBackInMap">
                  {{ mapBackLabel }}
                </button>
              </template>
              <template v-else-if="selectedKnowledge">
                <button type="button" class="kp-map-nav-btn" @click="backToPlanetFromKnowledge">
                  {{ t("knowledgePlanet.backToPlanet") }}
                </button>
                <button type="button" class="kp-map-nav-btn kp-map-nav-btn--ghost" @click="clearMapSelection">
                  {{ t("knowledgePlanet.backClearSelection") }}
                </button>
              </template>
              <template v-else-if="renderMode === '3d' && universe3dMode === 'planet' && selectedPlanet">
                <button type="button" class="kp-map-nav-btn" @click="goBackInMap">
                  {{ t("knowledgePlanet.backFullMap") }}
                </button>
                <button type="button" class="kp-map-nav-btn kp-map-nav-btn--ghost" @click="clearMapSelection">
                  {{ t("knowledgePlanet.backClearSelection") }}
                </button>
              </template>
              <button v-else type="button" class="kp-map-nav-btn" @click="goBackInMap">
                {{ mapBackLabel }}
              </button>
            </nav>
          </header>

          <p v-if="mainTab === 'map'" class="kp-map-hint">
            {{ mapHintText }}
          </p>

          <div v-show="mainTab === 'map'" class="kp-graph-wrap">
            <div ref="graphHost" class="kp-graph-host" />
            <div v-if="graphEmpty" class="kp-graph-empty">
              <p class="kp-graph-empty-title">{{ t("knowledgePlanet.emptyGraphTitle") }}</p>
              <p class="kp-graph-empty-hint">{{ t("knowledgePlanet.emptyGraphHint") }}</p>
            </div>
          </div>

          <div v-show="mainTab === 'archive'" class="kp-archive">
            <el-scrollbar class="kp-archive-scroll">
              <div class="kp-archive-scroll-inner">
                <p v-if="archiveNodes.length === 0" class="kp-archive-empty">
                  {{ t("knowledgePlanet.archiveEmpty") }}
                </p>
                <ul v-else class="kp-archive-list">
                  <li v-for="kn in archiveNodes" :key="kn.id">
                    <button type="button" class="kp-archive-item" @click="openFromArchive(kn.id)">
                      <strong>{{ kn.title }}</strong>
                      <span class="kp-archive-planet">{{ planetName(kn.planetId) }}</span>
                      <span class="kp-archive-summary">{{ kn.summary }}</span>
                    </button>
                  </li>
                </ul>
              </div>
            </el-scrollbar>
          </div>
        </div>

        <aside
          class="kp-side"
          :class="{ 'kp-side--mobile-collapsed': isMobileMapBrowseView && mobileSideSheetCollapsed }"
        >
          <header v-if="!isMobileLayout" class="kp-side-head">
            <h2>{{ t("knowledgePlanet.overlayTitle") }}</h2>
          </header>

          <button
            v-if="isMobileMapBrowseView"
            type="button"
            class="kp-mobile-side-toggle"
            :aria-expanded="!mobileSideSheetCollapsed"
            @click="toggleMobileSideSheet"
          >
            <span class="kp-mobile-side-toggle__title">{{ t("knowledgePlanet.planetsSection") }}</span>
            <span class="kp-mobile-side-toggle__meta">
              {{ t("knowledgePlanet.mobile.planetCount", { n: universe?.planets?.length ?? 0 }) }}
            </span>
            <el-icon class="kp-mobile-side-toggle__chev" :size="16">
              <ArrowDown v-if="mobileSideSheetCollapsed" />
              <ArrowUp v-else />
            </el-icon>
          </button>

          <el-scrollbar
            v-show="!isMobileMapBrowseView || !mobileSideSheetCollapsed"
            class="kp-side-scroll"
          >
          <div class="kp-side-scroll-inner">
          <section v-if="selectedKnowledge" class="kp-detail">
            <h3>{{ t("knowledgePlanet.knowledgeDetail") }}</h3>
            <p class="kp-detail-title">{{ selectedKnowledge.title }}</p>
            <p class="kp-detail-body">{{ selectedKnowledge.summary }}</p>
            <p class="kp-meta">
              <span>{{ t("knowledgePlanet.tagsLabel") }}：</span>
              {{
                selectedKnowledge.topicTags.length
                  ? selectedKnowledge.topicTags.join(" · ")
                  : t("knowledgePlanet.noTags")
              }}
            </p>
          </section>

          <section v-else-if="selectedPlanet" class="kp-detail">
            <h3>{{ selectedPlanet.name }}</h3>
            <p>{{ selectedPlanet.summary }}</p>
            <p class="kp-meta">{{ t("knowledgePlanet.nodeCount", { n: selectedPlanet.nodeCount }) }}</p>
            <ul class="kp-knowledge-list">
              <li v-for="kn in planetKnowledge" :key="kn.id">
                <button type="button" class="kp-knowledge-btn" @click="selectKnowledge(kn.id)">
                  <strong>{{ kn.title }}</strong>
                  <span>{{ kn.summary }}</span>
                </button>
              </li>
            </ul>
          </section>

          <template v-else>
            <p v-if="!isMobileLayout" class="kp-select-hint">{{ t("knowledgePlanet.selectHint") }}</p>

            <section class="kp-side-section">
              <h3 v-if="!isMobileMapBrowseView">{{ t("knowledgePlanet.planetsSection") }}</h3>
              <ul class="kp-planet-picks">
                <li v-for="p in universe?.planets ?? []" :key="p.id">
                  <button type="button" class="kp-planet-pick" @click="selectPlanet(p.id)">
                    <span class="kp-planet-dot" :style="{ background: planetColor(p) }" />
                    <span class="kp-planet-pick-name">{{ p.name }}</span>
                    <span class="kp-planet-pick-n">{{ p.nodeCount }}</span>
                  </button>
                </li>
              </ul>
            </section>

            <section v-if="recentKnowledge.length" class="kp-side-section">
              <h3>{{ t("knowledgePlanet.knowledgeSection") }}</h3>
              <ul class="kp-knowledge-list">
                <li v-for="kn in recentKnowledge" :key="kn.id">
                  <button type="button" class="kp-knowledge-btn" @click="selectKnowledge(kn.id)">
                    <strong>{{ kn.title }}</strong>
                    <span>{{ kn.summary }}</span>
                  </button>
                </li>
              </ul>
            </section>
          </template>

          <section v-if="weekly?.plan" class="kp-weekly">
            <h3>{{ t("knowledgePlanet.weeklyTitle") }}</h3>
            <p class="kp-summary">{{ weekly.plan.summary }}</p>
            <p v-if="weekly.plan.inferredPersona" class="kp-persona">{{ weekly.plan.inferredPersona }}</p>
            <p v-if="weekly.plan.progressNotes" class="kp-progress">{{ weekly.plan.progressNotes }}</p>
            <div v-if="weekly.plan.thinkDirections?.length" class="kp-weekly-block">
              <h4>{{ t("knowledgePlanet.think") }}</h4>
              <ul>
                <li v-for="(item, i) in weekly.plan.thinkDirections" :key="'t' + i">{{ item }}</li>
              </ul>
            </div>
            <div v-if="weekly.plan.gapAreas?.length" class="kp-weekly-block">
              <h4>{{ t("knowledgePlanet.gaps") }}</h4>
              <ul>
                <li v-for="(item, i) in weekly.plan.gapAreas" :key="'g' + i">{{ item }}</li>
              </ul>
            </div>
            <div v-if="weekly.plan.bookRecommendations?.length" class="kp-weekly-block">
              <h4>{{ t("knowledgePlanet.books") }}</h4>
              <ul class="kp-books">
                <li v-for="(book, i) in weekly.plan.bookRecommendations" :key="'b' + i">
                  <a v-if="book.url" :href="book.url" target="_blank" rel="noopener noreferrer"
                    >《{{ book.title }}》</a
                  >
                  <span v-else>《{{ book.title }}》</span>
                  <span v-if="book.reason" class="kp-book-reason"> — {{ book.reason }}</span>
                </li>
              </ul>
            </div>
            <div class="kp-weekly-feedback">
              <span>{{ t("knowledgePlanet.weeklyFeedbackPrompt") }}</span>
              <button type="button" class="kp-tour-btn" @click="submitWeeklyFeedback(true)">
                {{ t("knowledgePlanet.weeklyFeedbackYes") }}
              </button>
              <button type="button" class="kp-tour-btn" @click="submitWeeklyFeedback(false)">
                {{ t("knowledgePlanet.weeklyFeedbackNo") }}
              </button>
            </div>
          </section>

          <div v-if="tourOpen && !isMobileLayout" class="kp-tour">
            <p>{{ tourSteps[tourIndex] }}</p>
            <div class="kp-tour-actions">
              <button
                v-if="tourIndex < tourSteps.length - 1"
                type="button"
                class="kp-tour-btn kp-tour-btn--primary"
                @click="tourIndex++"
              >
                {{ t("knowledgePlanet.tourNext") }}
              </button>
              <button type="button" class="kp-tour-btn" @click="finishTour">
                {{ t("knowledgePlanet.tourDone") }}
              </button>
            </div>
          </div>
          </div>
          </el-scrollbar>
        </aside>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowLeft, ArrowUp } from "@element-plus/icons-vue";
import gsap from "gsap";
import { computed, nextTick, onUnmounted, ref, shallowRef, watch } from "vue";
import { useI18n } from "vue-i18n";
import type {
  KnowledgeGraphNode,
  KnowledgePlanetUniverse,
  KnowledgePlanetView,
  KnowledgePlanetWeeklyLatest,
  KnowledgePlanetWarpOrigin,
} from "../../api/knowledgePlanet";
import { postKnowledgePlanetWeeklyFeedback, warpOriginCenter } from "../../api/knowledgePlanet";
import { ElMessage } from "element-plus";
import { useKnowledgePlanetStarMap } from "../../composables/useKnowledgePlanetStarMap";
import {
  useKnowledgePlanetUniverseGraph,
  type UniverseViewMode,
} from "../../composables/useKnowledgePlanetUniverseGraph";
import { useRandomDotNetwork } from "../../composables/useRandomDotNetwork";

const TOUR_STORAGE_KEY = "kp-tour-v1";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    universe: KnowledgePlanetUniverse | null;
    weekly: KnowledgePlanetWeeklyLatest | null;
    originRect?: KnowledgePlanetWarpOrigin | null;
    layout?: "desktop" | "mobile";
  }>(),
  { layout: "desktop" },
);

const emit = defineEmits<{
  close: [];
}>();

const { t } = useI18n();
const isMobileLayout = computed(() => props.layout === "mobile");
const weeklyFeedbackSent = ref(false);

async function submitWeeklyFeedback(helpful: boolean) {
  if (weeklyFeedbackSent.value) {
    return;
  }
  try {
    await postKnowledgePlanetWeeklyFeedback(helpful);
    weeklyFeedbackSent.value = true;
    ElMessage.success(t("knowledgePlanet.weeklyFeedbackThanks"));
  } catch {
    ElMessage.error(t("knowledgePlanet.weeklyFeedbackFail"));
  }
}
const warpBg = shallowRef<HTMLElement | null>(null);
const overlayRoot = shallowRef<HTMLElement | null>(null);
const graphHost = shallowRef<HTMLElement | null>(null);
const randomDotCanvas = shallowRef<HTMLCanvasElement | null>(null);
const transitionCanvas = shallowRef<HTMLCanvasElement | null>(null);
const exitFab = shallowRef<HTMLButtonElement | null>(null);

type MainTab = "map" | "archive";
type RenderMode = "2d" | "3d";

const mainTab = ref<MainTab>("map");
const renderMode = ref<RenderMode>("2d");
const searchQuery = ref("");
const focusPlanetOnly = ref(false);
const universe3dMode = ref<UniverseViewMode>("full");
const selectedPlanetId = ref<string | null>(null);
const selectedKnowledgeId = ref<string | null>(null);

/** 移动端星图浏览态（未选中星球/知识点）：底部面板可整页收起 */
const mobileSideSheetCollapsed = ref(true);
const isMobileMapBrowseView = computed(
  () =>
    isMobileLayout.value &&
    mainTab.value === "map" &&
    !selectedPlanetId.value &&
    !selectedKnowledgeId.value,
);

function toggleMobileSideSheet() {
  mobileSideSheetCollapsed.value = !mobileSideSheetCollapsed.value;
  void nextTick(() => fitStarMap());
}

watch([selectedPlanetId, selectedKnowledgeId], ([planetId, knowledgeId]) => {
  if (isMobileLayout.value && (planetId || knowledgeId)) {
    mobileSideSheetCollapsed.value = false;
  }
});

const graphEmpty = ref(false);
const isPresent = ref(false);
const isClosing = ref(false);
const introDotsActive = ref(false);

const tourOpen = ref(false);
const tourIndex = ref(0);
const tourSteps = computed(() => [
  t("knowledgePlanet.tourStep1"),
  t("knowledgePlanet.tourStep2"),
  t("knowledgePlanet.tourStep3"),
]);

useRandomDotNetwork(randomDotCanvas, introDotsActive, {
  dotCount: isMobileLayout.value ? 56 : 80,
  linkDistance: 55,
  mouseRadius: isMobileLayout.value ? 72 : 100,
  minColor: 80,
  baseStroke: "rgba(0, 242, 254, 0.1)",
});

const starMap = useKnowledgePlanetStarMap(graphHost);
const graph3d = useKnowledgePlanetUniverseGraph(graphHost);

const selectedPlanet = computed((): KnowledgePlanetView | null => {
  if (!props.universe || !selectedPlanetId.value) return null;
  return props.universe.planets.find((p) => p.id === selectedPlanetId.value) ?? null;
});

const selectedKnowledge = computed((): KnowledgeGraphNode | null => {
  if (!props.universe || !selectedKnowledgeId.value) return null;
  return props.universe.nodes.find((n) => n.id === selectedKnowledgeId.value) ?? null;
});

const planetKnowledge = computed((): KnowledgeGraphNode[] => {
  if (!props.universe || !selectedPlanetId.value) return [];
  return props.universe.nodes.filter(
    (n) => n.kind === "knowledge" && n.planetId === selectedPlanetId.value,
  );
});

const archiveNodes = computed((): KnowledgeGraphNode[] => {
  if (!props.universe) return [];
  return props.universe.nodes.filter((n) => n.kind === "knowledge");
});

const recentKnowledge = computed((): KnowledgeGraphNode[] => archiveNodes.value.slice(0, 8));

const mapCanGoBack = computed(() => {
  if (mainTab.value !== "map") return false;
  if (renderMode.value === "3d" && universe3dMode.value === "planet") return true;
  if (focusPlanetOnly.value && selectedPlanet.value) return true;
  if (selectedKnowledgeId.value) return true;
  if (selectedPlanetId.value) return true;
  return false;
});

const mapBackLabel = computed(() => {
  if (renderMode.value === "3d" && universe3dMode.value === "planet") {
    return t("knowledgePlanet.backFullMap");
  }
  if (selectedKnowledgeId.value) return t("knowledgePlanet.backToPlanet");
  if (focusPlanetOnly.value) return t("knowledgePlanet.backFullMap");
  return t("knowledgePlanet.backClearSelection");
});

const mapBackCrumb = computed(() => {
  if (selectedKnowledge.value) return selectedKnowledge.value.title;
  if (selectedPlanet.value) return selectedPlanet.value.name;
  return "";
});

const mapHintText = computed(() => {
  if (isMobileLayout.value) {
    if (selectedKnowledge.value) {
      return t("knowledgePlanet.mobile.knowledgeHint");
    }
    if (universe3dMode.value === "planet" && selectedPlanet.value) {
      return t("knowledgePlanet.mobile.planetHint", { name: selectedPlanet.value.name });
    }
    return t("knowledgePlanet.mobile.starMapHint");
  }
  if (selectedKnowledge.value) return t("knowledgePlanet.knowledgeNodeHint");
  if (focusPlanetOnly.value && selectedPlanet.value) {
    return t("knowledgePlanet.focusPlanetHint", { name: selectedPlanet.value.name });
  }
  if (universe3dMode.value === "planet" && selectedPlanet.value) {
    return t("knowledgePlanet.drillPlanetHint", { name: selectedPlanet.value.name });
  }
  return t("knowledgePlanet.starMapHint");
});

function planetColor(p: KnowledgePlanetView): string {
  return `#${(p.colorRgb & 0xffffff).toString(16).padStart(6, "0")}`;
}

function planetName(planetId: string | null): string {
  if (!planetId || !props.universe) return "—";
  return props.universe.planets.find((p) => p.id === planetId)?.name ?? "—";
}

let animId = 0;
let gsapTween: gsap.core.Tween | null = null;
let syncTimer = 0;
let introDotsTimer = 0;

function originCenter(): { x: number; y: number } {
  if (props.originRect && props.originRect.width > 0) {
    return warpOriginCenter(props.originRect);
  }
  return { x: window.innerWidth / 2, y: window.innerHeight / 2 };
}

function onKeydown(e: KeyboardEvent) {
  if (!isPresent.value || isClosing.value || e.key !== "Escape") return;
  e.preventDefault();
  e.stopPropagation();
  void requestClose();
}

function disposeGraphs() {
  starMap.dispose();
  graph3d.dispose();
}

function teardownState() {
  gsapTween?.kill();
  window.clearTimeout(syncTimer);
  window.clearTimeout(introDotsTimer);
  if (animId) cancelAnimationFrame(animId);
  disposeGraphs();
  mainTab.value = "map";
  renderMode.value = "2d";
  searchQuery.value = "";
  focusPlanetOnly.value = false;
  universe3dMode.value = "full";
  selectedPlanetId.value = null;
  selectedKnowledgeId.value = null;
  graphEmpty.value = false;
  introDotsActive.value = false;
  tourOpen.value = false;
  document.body.style.overflow = "";
  window.removeEventListener("keydown", onKeydown, true);
}

async function requestClose() {
  if (isClosing.value || !isPresent.value) return;
  await finalizeClose(true);
}

async function finalizeClose(animated: boolean) {
  if (isClosing.value) return;
  isClosing.value = true;
  window.removeEventListener("keydown", onKeydown, true);

  if (animated) {
    disposeGraphs();
    await runPortalDismiss();
  } else {
    teardownState();
  }

  isPresent.value = false;
  isClosing.value = false;
  emit("close");
}

function resizeCanvas() {
  const c = transitionCanvas.value;
  if (!c) return;
  c.width = window.innerWidth;
  c.height = window.innerHeight;
}

function runParticleWarpOut() {
  const c = transitionCanvas.value;
  if (!c) return;
  const ctx = c.getContext("2d");
  if (!ctx) return;
  resizeCanvas();
  const { x: cx, y: cy } = originCenter();
  const maxR = Math.hypot(window.innerWidth, window.innerHeight) * 0.55;

  let particles: Array<{
    x: number;
    y: number;
    vx: number;
    vy: number;
    alpha: number;
    size: number;
  }> = [];

  for (let i = 0; i < 200; i++) {
    const angle = Math.random() * Math.PI * 2;
    const speed = 4 + Math.random() * 14;
    particles.push({
      x: cx + (Math.random() - 0.5) * 12,
      y: cy + (Math.random() - 0.5) * 12,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      alpha: 0.95,
      size: Math.random() * 2 + 0.8,
    });
  }

  const tick = () => {
    ctx.clearRect(0, 0, c.width, c.height);
    particles = particles.filter((p) => {
      p.x += p.vx;
      p.y += p.vy;
      p.vx *= 1.02;
      p.vy *= 1.02;
      p.alpha -= 0.012;
      if (Math.hypot(p.x - cx, p.y - cy) > maxR) p.alpha -= 0.04;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(0, 242, 254, ${Math.max(0, p.alpha)})`;
      ctx.fill();
      return p.alpha > 0;
    });
    if (particles.length > 0) animId = requestAnimationFrame(tick);
  };
  tick();
  window.setTimeout(() => cancelAnimationFrame(animId), 1600);
}

function runParticleWarpIn() {
  const c = transitionCanvas.value;
  if (!c) return;
  const ctx = c.getContext("2d");
  if (!ctx) return;
  resizeCanvas();
  const { x: cx, y: cy } = originCenter();
  const maxR = Math.hypot(window.innerWidth, window.innerHeight) * 0.55;

  let particles: Array<{
    x: number;
    y: number;
    vx: number;
    vy: number;
    alpha: number;
    size: number;
  }> = [];

  for (let i = 0; i < 180; i++) {
    const angle = Math.random() * Math.PI * 2;
    const dist = maxR * (0.35 + Math.random() * 0.65);
    const px = cx + Math.cos(angle) * dist;
    const py = cy + Math.sin(angle) * dist;
    const dx = cx - px;
    const dy = cy - py;
    const len = Math.hypot(dx, dy) || 1;
    const speed = 6 + Math.random() * 10;
    particles.push({
      x: px,
      y: py,
      vx: (dx / len) * speed,
      vy: (dy / len) * speed,
      alpha: 0.85,
      size: Math.random() * 2 + 0.8,
    });
  }

  const tick = () => {
    ctx.clearRect(0, 0, c.width, c.height);
    particles = particles.filter((p) => {
      const dx = cx - p.x;
      const dy = cy - p.y;
      p.x += p.vx;
      p.y += p.vy;
      p.vx += dx * 0.002;
      p.vy += dy * 0.002;
      if (Math.hypot(dx, dy) < 16) p.alpha -= 0.04;
      else p.alpha -= 0.008;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(0, 242, 254, ${Math.max(0, p.alpha)})`;
      ctx.fill();
      return p.alpha > 0;
    });
    if (particles.length > 0) animId = requestAnimationFrame(tick);
  };
  tick();
  window.setTimeout(() => cancelAnimationFrame(animId), 1400);
}

function runPortalDismiss(): Promise<void> {
  return new Promise((resolve) => {
    const bg = warpBg.value;
    const root = overlayRoot.value;
    const fab = exitFab.value;

    if (!bg || !root) {
      resolve();
      return;
    }

    const { x, y } = originCenter();
    runParticleWarpIn();

    gsapTween?.kill();
    gsap.to(root, { opacity: 0, duration: 0.38, ease: "power2.in" });
    if (fab) {
      gsap.to(fab, { opacity: 0, duration: 0.25, ease: "power2.in" });
    }
    gsapTween = gsap.to(bg, {
      clipPath: `circle(0px at ${x}px ${y}px)`,
      duration: 0.82,
      ease: "power3.in",
      onComplete: () => {
        teardownState();
        resolve();
      },
    });
  });
}

function runPortalReveal(onDone: () => void) {
  const bg = warpBg.value;
  const root = overlayRoot.value;
  if (!bg || !root) {
    onDone();
    return;
  }

  const { x, y } = originCenter();
  const endR = Math.hypot(window.innerWidth, window.innerHeight) * 1.05;

  gsapTween?.kill();
  gsap.set(bg, { clipPath: `circle(0px at ${x}px ${y}px)` });
  gsap.set(root, { opacity: 0 });
  if (exitFab.value) gsap.set(exitFab.value, { opacity: 1 });
  gsapTween = gsap.to(bg, {
    clipPath: `circle(${endR}px at ${x}px ${y}px)`,
    duration: 0.88,
    ease: "power3.inOut",
    onComplete: onDone,
  });
  gsap.to(root, { opacity: 1, duration: 0.55, delay: 0.1, ease: "power2.out" });
}

function startIntroDots() {
  introDotsActive.value = true;
  window.clearTimeout(introDotsTimer);
  introDotsTimer = window.setTimeout(() => {
    introDotsActive.value = false;
  }, 1600);
}

function maybeOpenTour() {
  if (isMobileLayout.value) return;
  try {
    if (localStorage.getItem(TOUR_STORAGE_KEY) === "1") return;
    tourIndex.value = 0;
    tourOpen.value = true;
  } catch {
    /* ignore */
  }
}

function finishTour() {
  tourOpen.value = false;
  try {
    localStorage.setItem(TOUR_STORAGE_KEY, "1");
  } catch {
    /* ignore */
  }
}

function applyStarMapFocus() {
  if (focusPlanetOnly.value && selectedPlanetId.value) {
    starMap.setFocusPlanet(selectedPlanetId.value);
  } else {
    starMap.setFocusPlanet(null);
  }
}

function onSearchInput() {
  if (renderMode.value === "2d") {
    starMap.setSearchQuery(searchQuery.value);
  }
}

function selectPlanet(planetId: string) {
  selectedPlanetId.value = planetId;
  selectedKnowledgeId.value = null;
  if (renderMode.value === "2d") {
    applyStarMapFocus();
    if (mainTab.value === "map") starMap.centerOnNode(planetId);
    return;
  }
  universe3dMode.value = "planet";
  void syncGraph().then(() => {
    graph3d.focusPlanet(planetId);
  });
}

function selectKnowledge(nodeId: string) {
  selectedKnowledgeId.value = nodeId;
  const node = props.universe?.nodes.find((n) => n.id === nodeId);
  if (node?.planetId) selectedPlanetId.value = node.planetId;
  if (mainTab.value === "map" && renderMode.value === "2d") {
    starMap.centerOnNode(nodeId);
  }
}

function clearPlanetFocus() {
  focusPlanetOnly.value = false;
  universe3dMode.value = "full";
  selectedPlanetId.value = null;
  selectedKnowledgeId.value = null;
  starMap.setFocusPlanet(null);
  starMap.fitView();
  if (isMobileLayout.value) {
    mobileSideSheetCollapsed.value = true;
    void nextTick(() => fitStarMap());
  }
}

function backToPlanetFromKnowledge() {
  selectedKnowledgeId.value = null;
}

function clearMapSelection() {
  if (renderMode.value === "3d" && universe3dMode.value === "planet") {
    universe3dMode.value = "full";
    void syncGraph();
  }
  focusPlanetOnly.value = false;
  selectedPlanetId.value = null;
  selectedKnowledgeId.value = null;
  starMap.setFocusPlanet(null);
  applyStarMapFocus();
  if (renderMode.value === "2d") starMap.fitView();
  if (isMobileLayout.value) {
    mobileSideSheetCollapsed.value = true;
    void nextTick(() => fitStarMap());
  }
}

function goBackInMap() {
  if (renderMode.value === "3d" && universe3dMode.value === "planet") {
    universe3dMode.value = "full";
    void syncGraph();
    return;
  }
  if (selectedKnowledgeId.value) {
    backToPlanetFromKnowledge();
    return;
  }
  if (focusPlanetOnly.value) {
    clearPlanetFocus();
    return;
  }
  if (selectedPlanetId.value) {
    selectedPlanetId.value = null;
    selectedKnowledgeId.value = null;
    applyStarMapFocus();
    if (renderMode.value === "2d") starMap.fitView();
  }
}

function onFocusToggle() {
  applyStarMapFocus();
}

function fitStarMap() {
  if (renderMode.value === "2d") starMap.fitView();
  else graph3d.resizeGraph();
}

function setRenderMode(mode: RenderMode) {
  if (renderMode.value === mode) return;
  renderMode.value = mode;
  if (mode === "2d") {
    universe3dMode.value = "full";
  } else if (selectedPlanetId.value) {
    universe3dMode.value = "planet";
  }
  void syncGraph();
}

function openFromArchive(nodeId: string) {
  mainTab.value = "map";
  selectKnowledge(nodeId);
  void nextTick(() => void syncGraph());
}

async function syncGraph() {
  if (!props.visible || mainTab.value !== "map") return;
  if (!props.universe) {
    graphEmpty.value = true;
    return;
  }

  await nextTick();
  await new Promise<void>((r) => requestAnimationFrame(() => requestAnimationFrame(() => r())));

  const el = graphHost.value;
  if (!el || el.clientWidth < 8 || el.clientHeight < 8) {
    window.clearTimeout(syncTimer);
    syncTimer = window.setTimeout(() => void syncGraph(), 80);
    return;
  }

  disposeGraphs();

  let ok = false;
  if (renderMode.value === "2d") {
    ok = starMap.mount(props.universe);
    if (ok) {
      starMap.setSearchQuery(searchQuery.value);
      applyStarMapFocus();
      starMap.resizeGraph();
    }
  } else {
    const drillId = universe3dMode.value === "planet" ? selectedPlanetId.value : null;
    ok = graph3d.mount(props.universe, universe3dMode.value, drillId);
    if (ok) {
      graph3d.resizeGraph();
      if (universe3dMode.value === "planet" && selectedPlanetId.value) {
        graph3d.focusPlanet(selectedPlanetId.value);
      }
    }
  }

  graphEmpty.value = !ok;
  if (ok && renderMode.value === "2d" && isMobileLayout.value) {
    starMap.fitView();
  }
}

starMap.setPlanetClickHandler((planetId) => selectPlanet(planetId));
starMap.setKnowledgeClickHandler((nodeId) => selectKnowledge(nodeId));
graph3d.setPlanetClickHandler((planet) => selectPlanet(planet.id));

watch(
  () => props.visible,
  async (v) => {
    if (!v) {
      if (isPresent.value && !isClosing.value) {
        await finalizeClose(true);
      }
      return;
    }

    if (isPresent.value) return;

    isPresent.value = true;
    isClosing.value = false;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeydown, true);

    await nextTick();
    runParticleWarpOut();
    startIntroDots();
    runPortalReveal(() => {
      maybeOpenTour();
      void syncGraph();
    });
  },
);

watch(
  () => props.universe,
  () => {
    if (props.visible) void syncGraph();
  },
);

watch(mainTab, (tab) => {
  if (tab === "map" && props.visible) void syncGraph();
});

onUnmounted(() => {
  teardownState();
  isPresent.value = false;
  isClosing.value = false;
});
</script>

<style scoped>
.kp-overlay {
  position: fixed;
  inset: 0;
  z-index: 9000;
  pointer-events: auto;
  overflow: hidden;
}

.kp-warp-bg {
  position: fixed;
  inset: 0;
  z-index: 9000;
  background: radial-gradient(ellipse at 50% 35%, #0a192f 0%, #030712 48%, #000 100%);
  pointer-events: none;
}

.kp-warp-bg::after {
  content: "";
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    rgba(0, 0, 0, 0.04) 0,
    rgba(0, 0, 0, 0.04) 1px,
    transparent 1px,
    transparent 3px
  );
  pointer-events: none;
}

.kp-random-dot-canvas {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 9000;
  pointer-events: none;
  opacity: 0.7;
  transition: opacity 0.6s ease;
}

.kp-random-dot-canvas--off {
  opacity: 0;
}

.kp-transition-canvas {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9001;
}

.kp-exit-fab {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-right: 4px;
  padding: 8px 14px 8px 10px;
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-radius: 999px;
  background: rgba(10, 14, 20, 0.72);
  backdrop-filter: blur(12px);
  color: #e8f4ff;
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}

.kp-exit-fab:hover:not(:disabled) {
  border-color: rgba(0, 242, 254, 0.75);
  box-shadow: 0 0 20px rgba(0, 242, 254, 0.2);
  background: rgba(10, 20, 30, 0.92);
}

.kp-exit-fab:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.kp-overlay--closing {
  pointer-events: none;
}

.kp-overlay--closing .kp-universe-root {
  pointer-events: none;
}

.kp-exit-icon {
  font-size: 20px;
  line-height: 1;
  color: #00f2fe;
}

.kp-exit-kbd {
  margin-left: 2px;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  font-size: 10px;
  font-family: ui-monospace, monospace;
  color: rgba(255, 255, 255, 0.55);
}

.kp-universe-root {
  position: relative;
  z-index: 9002;
  width: 100%;
  max-width: 100%;
  height: 100vh;
  height: 100dvh;
  display: flex;
  pointer-events: auto;
  box-sizing: border-box;
  overflow-x: hidden;
}

.kp-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.kp-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  padding: 12px 16px 0;
}

.kp-map-nav {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
  margin-left: auto;
  max-width: min(100%, 520px);
}

.kp-map-nav-crumb {
  font-size: 13px;
  font-weight: 600;
  color: rgba(232, 244, 255, 0.92);
  max-width: min(240px, 36vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 4px;
}

.kp-tabs {
  display: flex;
  gap: 4px;
}

.kp-tab {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  color: rgba(224, 232, 240, 0.65);
  font-size: 13px;
  cursor: pointer;
}

.kp-tab--active {
  border-color: rgba(0, 242, 254, 0.35);
  background: rgba(0, 242, 254, 0.08);
  color: #00f2fe;
}

.kp-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.kp-search {
  flex: 1;
  min-width: 140px;
  max-width: 220px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid rgba(0, 242, 254, 0.2);
  background: rgba(0, 0, 0, 0.35);
  color: #e8f4ff;
  font-size: 12px;
}

.kp-search::placeholder {
  color: rgba(224, 232, 240, 0.4);
}

.kp-tool-btn {
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid rgba(0, 242, 254, 0.25);
  background: rgba(0, 242, 254, 0.06);
  color: rgba(232, 244, 255, 0.85);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.kp-render-toggle {
  display: inline-flex;
  align-items: stretch;
  padding: 2px;
  border-radius: 9px;
  border: 1px solid rgba(0, 242, 254, 0.28);
  background: rgba(0, 0, 0, 0.4);
  gap: 2px;
}

.kp-render-toggle__btn {
  padding: 5px 12px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: rgba(232, 244, 255, 0.72);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.kp-render-toggle__btn:hover:not(.kp-render-toggle__btn--active) {
  background: rgba(255, 255, 255, 0.06);
  color: rgba(232, 244, 255, 0.92);
}

.kp-render-toggle__btn--active {
  background: rgba(0, 242, 254, 0.2);
  color: #00f2fe;
  box-shadow: 0 0 0 1px rgba(0, 242, 254, 0.35);
}

html.dark .kp-render-toggle {
  border-color: rgba(99, 102, 241, 0.4);
  background: rgba(255, 255, 255, 0.06);
}

html.dark .kp-render-toggle__btn {
  color: rgba(212, 212, 216, 0.85);
}

html.dark .kp-render-toggle__btn:hover:not(.kp-render-toggle__btn--active) {
  background: rgba(255, 255, 255, 0.08);
  color: #e4e4e7;
}

html.dark .kp-render-toggle__btn--active {
  background: rgba(99, 102, 241, 0.28);
  color: #c7d2fe;
  box-shadow: 0 0 0 1px rgba(129, 140, 248, 0.45);
}

.kp-focus-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: rgba(224, 232, 240, 0.75);
  cursor: pointer;
  white-space: nowrap;
}

.kp-map-hint {
  margin: 8px 16px 0;
  padding: 0 16px;
  font-size: 12px;
  color: rgba(224, 224, 224, 0.55);
  line-height: 1.45;
  text-align: right;
}

.kp-map-nav-btn {
  padding: 6px 14px;
  border-radius: 16px;
  border: 1px solid rgba(0, 242, 254, 0.45);
  background: rgba(0, 242, 254, 0.1);
  color: #00f2fe;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.kp-map-nav-btn:hover {
  background: rgba(0, 242, 254, 0.18);
  border-color: rgba(0, 242, 254, 0.65);
}

.kp-map-nav-btn--ghost {
  border-color: rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(232, 244, 255, 0.88);
}

.kp-map-nav-btn--ghost:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.35);
  color: #fff;
}

.kp-graph-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  margin: 8px 12px 12px;
  border-radius: 12px;
  border: 1px solid rgba(0, 242, 254, 0.12);
  overflow: hidden;
  background: rgba(0, 0, 0, 0.25);
}

.kp-graph-host {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.kp-graph-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  pointer-events: none;
}

.kp-graph-empty-title {
  margin: 0 0 8px;
  font-size: 15px;
  color: #00f2fe;
  letter-spacing: 0.06em;
}

.kp-graph-empty-hint {
  margin: 0;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(224, 224, 224, 0.65);
}

.kp-archive {
  flex: 1;
  min-height: 0;
  margin: 8px 12px 12px;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(0, 242, 254, 0.12);
  background: rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
}

.kp-archive-scroll {
  flex: 1;
  min-height: 0;
}

.kp-archive-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.kp-archive-scroll :deep(.el-scrollbar__bar.is-vertical) {
  width: 6px;
  right: 2px;
}

.kp-archive-scroll :deep(.el-scrollbar__thumb) {
  background: rgba(0, 242, 254, 0.32);
  border-radius: 4px;
  opacity: 1;
}

.kp-archive-scroll :deep(.el-scrollbar__thumb:hover) {
  background: rgba(0, 242, 254, 0.5);
}

.kp-archive-scroll-inner {
  padding: 12px;
}

.kp-archive-empty {
  margin: 24px 0;
  text-align: center;
  font-size: 13px;
  color: rgba(224, 224, 224, 0.6);
}

.kp-archive-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kp-archive-item {
  width: 100%;
  text-align: left;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid rgba(0, 242, 254, 0.12);
  background: rgba(0, 242, 254, 0.04);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kp-archive-item:hover {
  border-color: rgba(0, 242, 254, 0.35);
}

.kp-archive-item strong {
  font-size: 13px;
  color: #e8f4ff;
}

.kp-archive-planet {
  font-size: 11px;
  color: #00f2fe;
}

.kp-archive-summary {
  font-size: 12px;
  color: rgba(224, 224, 224, 0.65);
  line-height: 1.45;
}

.kp-side {
  width: min(360px, 38vw);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px 24px;
  background: linear-gradient(90deg, rgba(10, 14, 20, 0.94) 0%, rgba(10, 14, 20, 0.82) 100%);
  backdrop-filter: blur(16px);
  border-left: 1px solid rgba(0, 242, 254, 0.15);
  color: #e0e0e0;
  overflow: hidden;
  min-height: 0;
}

.kp-side-scroll {
  flex: 1;
  min-height: 0;
}

.kp-side-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.kp-side-scroll :deep(.el-scrollbar__bar.is-vertical) {
  width: 6px;
  right: 2px;
}

.kp-side-scroll :deep(.el-scrollbar__thumb) {
  background: rgba(0, 242, 254, 0.32);
  border-radius: 4px;
  opacity: 1;
}

.kp-side-scroll :deep(.el-scrollbar__thumb:hover) {
  background: rgba(0, 242, 254, 0.5);
}

.kp-side-scroll-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.kp-side-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #00f2fe;
  letter-spacing: 0.06em;
}

.kp-select-hint {
  margin: 0;
  font-size: 12px;
  color: rgba(224, 224, 224, 0.6);
  line-height: 1.5;
}

.kp-side-section h3,
.kp-detail h3,
.kp-weekly h3 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #7b9cff;
  font-weight: 500;
}

.kp-side-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.kp-side-section-head h3 {
  margin: 0;
  flex: 1;
  min-width: 0;
}

.kp-mobile-side-toggle {
  flex-shrink: 0;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid rgba(0, 242, 254, 0.22);
  border-radius: 12px;
  background: rgba(0, 242, 254, 0.06);
  color: #e8f4ff;
  cursor: pointer;
  text-align: left;
  box-sizing: border-box;
}

.kp-mobile-side-toggle:hover {
  border-color: rgba(0, 242, 254, 0.4);
  background: rgba(0, 242, 254, 0.1);
}

.kp-mobile-side-toggle__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #7b9cff;
}

.kp-mobile-side-toggle__meta {
  flex-shrink: 0;
  font-size: 11px;
  color: rgba(224, 224, 224, 0.55);
}

.kp-mobile-side-toggle__chev {
  flex-shrink: 0;
  color: #9ecbff;
}

.kp-planet-picks {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kp-planet-pick {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.kp-planet-pick:hover {
  border-color: rgba(0, 242, 254, 0.2);
  background: rgba(0, 242, 254, 0.05);
}

.kp-planet-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 8px currentColor;
}

.kp-planet-pick-name {
  flex: 1;
  font-size: 13px;
  color: #e8f4ff;
}

.kp-planet-pick-n {
  font-size: 11px;
  color: #00f2fe;
}

.kp-detail-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 500;
  color: #e8f4ff;
}

.kp-detail-body,
.kp-detail p,
.kp-summary {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(224, 224, 224, 0.88);
}

.kp-meta {
  font-size: 11px;
  color: #00f2fe;
}

.kp-knowledge-list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kp-knowledge-btn {
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(0, 242, 254, 0.12);
  background: rgba(0, 242, 254, 0.04);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kp-knowledge-btn:hover {
  border-color: rgba(0, 242, 254, 0.3);
}

.kp-knowledge-btn strong {
  font-size: 12px;
  color: #e8f4ff;
}

.kp-knowledge-btn span {
  font-size: 11px;
  color: rgba(224, 224, 240, 0.7);
}

/* —— 移动端：上下分栏，精简顶栏 —— */
.kp-overlay--mobile .kp-universe-root {
  flex-direction: column;
  height: 100dvh;
  padding-top: env(safe-area-inset-top, 0);
  padding-left: env(safe-area-inset-left, 0);
  padding-right: env(safe-area-inset-right, 0);
  padding-bottom: env(safe-area-inset-bottom, 0);
  box-sizing: border-box;
}

.kp-overlay--mobile .kp-main {
  flex: 1;
  min-height: 0;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.kp-overlay--mobile .kp-toolbar {
  padding: 8px max(12px, env(safe-area-inset-right, 0px)) 0 max(12px, env(safe-area-inset-left, 0px));
  gap: 8px;
  max-width: 100%;
  box-sizing: border-box;
}

.kp-overlay--mobile .kp-mobile-back {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.35);
  color: #e8f4ff;
  cursor: pointer;
}

.kp-overlay--mobile .kp-mobile-title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #e8f4ff;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kp-overlay--mobile .kp-tabs {
  flex-shrink: 0;
  margin-left: auto;
}

.kp-overlay--mobile .kp-render-toggle--head {
  flex-shrink: 0;
}

.kp-overlay--mobile .kp-toolbar-actions--mobile {
  flex-basis: 100%;
  max-width: 100%;
}

.kp-overlay--mobile .kp-search {
  flex: 1;
  min-width: 0;
  max-width: none;
}

.kp-overlay--mobile .kp-render-toggle {
  flex-shrink: 0;
}

.kp-overlay--mobile .kp-tab {
  padding: 6px 10px;
  font-size: 12px;
}

.kp-overlay--mobile .kp-render-toggle--head .kp-render-toggle__btn {
  padding: 5px 8px;
  font-size: 11px;
}

.kp-overlay--mobile .kp-map-hint {
  padding: 6px 12px 0;
  font-size: 11px;
  line-height: 1.4;
}

.kp-overlay--mobile .kp-map-nav--mobile {
  flex-basis: 100%;
  margin-left: 0;
  justify-content: flex-start;
}

.kp-overlay--mobile .kp-graph-wrap {
  flex: 1;
  min-height: 36vh;
}

.kp-overlay--mobile .kp-side {
  width: 100%;
  max-width: 100%;
  max-height: min(42vh, 360px);
  flex-shrink: 0;
  border-left: none;
  border-top: 1px solid rgba(0, 242, 254, 0.15);
  padding: 12px max(14px, env(safe-area-inset-right, 0px)) max(16px, env(safe-area-inset-bottom, 0px))
    max(14px, env(safe-area-inset-left, 0px));
  box-sizing: border-box;
  overflow: hidden;
}

.kp-overlay--mobile .kp-side--mobile-collapsed {
  max-height: none;
  flex: 0 0 auto;
  gap: 0;
  padding-top: 8px;
  padding-bottom: max(10px, env(safe-area-inset-bottom, 0px));
}

.kp-overlay--mobile .kp-planet-picks {
  max-height: min(28vh, 220px);
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.kp-overlay--mobile .kp-archive {
  max-height: min(42vh, 360px);
}

.kp-weekly {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 242, 254, 0.1);
}

.kp-tour {
  margin-top: 8px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(0, 242, 254, 0.3);
  background: rgba(0, 242, 254, 0.08);
}

.kp-tour p {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #e8f4ff;
}

.kp-tour-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.kp-tour-btn {
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid rgba(0, 242, 254, 0.3);
  background: transparent;
  color: #9ab;
  font-size: 12px;
  cursor: pointer;
}

.kp-tour-btn--primary {
  background: rgba(0, 242, 254, 0.15);
  color: #00f2fe;
}
</style>
