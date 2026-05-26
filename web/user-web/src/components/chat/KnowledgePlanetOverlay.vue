<template>
  <Teleport to="body">
    <div
      v-if="isPresent"
      class="kp-overlay"
      :class="{ 'kp-overlay--closing': isClosing }"
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

      <button ref="exitFab" type="button" class="kp-exit-fab" :disabled="isClosing" @click="requestClose">
        <span class="kp-exit-icon">×</span>
        <span>{{ t("knowledgePlanet.exit") }}</span>
        <kbd class="kp-exit-kbd">Esc</kbd>
      </button>

      <div ref="overlayRoot" class="kp-universe-root">
        <div class="kp-main">
          <header class="kp-toolbar">
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

            <div v-if="mainTab === 'map'" class="kp-toolbar-actions">
              <input
                v-model="searchQuery"
                type="search"
                class="kp-search"
                :placeholder="t('knowledgePlanet.searchPlaceholder')"
                @input="onSearchInput"
              />
              <button type="button" class="kp-tool-btn" @click="fitStarMap">
                {{ t("knowledgePlanet.fitMap") }}
              </button>
              <button
                type="button"
                class="kp-tool-btn"
                :class="{ 'kp-tool-btn--active': renderMode === '2d' }"
                @click="setRenderMode('2d')"
              >
                {{ t("knowledgePlanet.view2d") }}
              </button>
              <button
                type="button"
                class="kp-tool-btn"
                :class="{ 'kp-tool-btn--active': renderMode === '3d' }"
                @click="setRenderMode('3d')"
              >
                {{ t("knowledgePlanet.view3d") }}
              </button>
              <label v-if="selectedPlanet" class="kp-focus-toggle">
                <input v-model="focusPlanetOnly" type="checkbox" @change="onFocusToggle" />
                {{ t("knowledgePlanet.focusPlanetOnly") }}
              </label>
            </div>
          </header>

          <p v-if="mainTab === 'map'" class="kp-map-hint">
            {{
              focusPlanetOnly && selectedPlanet
                ? t("knowledgePlanet.focusPlanetHint", { name: selectedPlanet.name })
                : t("knowledgePlanet.starMapHint")
            }}
          </p>

          <button
            v-if="mainTab === 'map' && focusPlanetOnly && selectedPlanet"
            type="button"
            class="kp-back-map"
            @click="clearPlanetFocus"
          >
            {{ t("knowledgePlanet.backFullMap") }}
          </button>

          <div v-show="mainTab === 'map'" class="kp-graph-wrap">
            <div ref="graphHost" class="kp-graph-host" />
            <div v-if="graphEmpty" class="kp-graph-empty">
              <p class="kp-graph-empty-title">{{ t("knowledgePlanet.emptyGraphTitle") }}</p>
              <p class="kp-graph-empty-hint">{{ t("knowledgePlanet.emptyGraphHint") }}</p>
            </div>
          </div>

          <div v-show="mainTab === 'archive'" class="kp-archive">
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
        </div>

        <aside class="kp-side">
          <header class="kp-side-head">
            <h2>{{ t("knowledgePlanet.overlayTitle") }}</h2>
            <button type="button" class="kp-icon-btn" :aria-label="t('knowledgePlanet.close')" @click="requestClose">
              ×
            </button>
          </header>

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
            <p class="kp-select-hint">{{ t("knowledgePlanet.selectHint") }}</p>

            <section class="kp-side-section">
              <h3>{{ t("knowledgePlanet.planetsSection") }}</h3>
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
          </section>

          <div v-if="tourOpen" class="kp-tour">
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
        </aside>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
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
import { warpOriginCenter } from "../../api/knowledgePlanet";
import { useKnowledgePlanetStarMap } from "../../composables/useKnowledgePlanetStarMap";
import { useKnowledgePlanetUniverseGraph } from "../../composables/useKnowledgePlanetUniverseGraph";
import { useRandomDotNetwork } from "../../composables/useRandomDotNetwork";

const TOUR_STORAGE_KEY = "kp-tour-v1";

const props = defineProps<{
  visible: boolean;
  universe: KnowledgePlanetUniverse | null;
  weekly: KnowledgePlanetWeeklyLatest | null;
  originRect?: KnowledgePlanetWarpOrigin | null;
}>();

const emit = defineEmits<{
  close: [];
}>();

const { t } = useI18n();
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
const selectedPlanetId = ref<string | null>(null);
const selectedKnowledgeId = ref<string | null>(null);
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
  dotCount: 80,
  linkDistance: 55,
  mouseRadius: 100,
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
  applyStarMapFocus();
  if (mainTab.value === "map" && renderMode.value === "2d") {
    starMap.centerOnNode(planetId);
  }
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
  selectedPlanetId.value = null;
  selectedKnowledgeId.value = null;
  starMap.setFocusPlanet(null);
  starMap.fitView();
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
    ok = graph3d.mount(props.universe, "full", null);
    if (ok) graph3d.resizeGraph();
  }

  graphEmpty.value = !ok;
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
  position: fixed;
  top: 18px;
  left: 18px;
  z-index: 9010;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 8px 10px;
  border: 1px solid rgba(0, 242, 254, 0.35);
  border-radius: 999px;
  background: rgba(10, 14, 20, 0.82);
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
  width: 100vw;
  height: 100vh;
  display: flex;
  pointer-events: auto;
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
  gap: 10px 16px;
  padding: 12px 16px 0;
  padding-left: 120px;
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

.kp-tool-btn--active {
  border-color: rgba(0, 242, 254, 0.55);
  background: rgba(0, 242, 254, 0.14);
  color: #00f2fe;
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
  padding-left: 104px;
  font-size: 12px;
  color: rgba(224, 224, 224, 0.55);
  line-height: 1.45;
}

.kp-back-map {
  margin: 6px 16px 0;
  margin-left: 120px;
  align-self: flex-start;
  padding: 5px 12px;
  border-radius: 16px;
  border: 1px solid rgba(0, 242, 254, 0.35);
  background: transparent;
  color: #00f2fe;
  font-size: 12px;
  cursor: pointer;
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
  padding: 12px;
  overflow: auto;
  border-radius: 12px;
  border: 1px solid rgba(0, 242, 254, 0.12);
  background: rgba(0, 0, 0, 0.25);
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
  overflow: auto;
}

.kp-side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kp-side-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #00f2fe;
  letter-spacing: 0.06em;
}

.kp-icon-btn {
  border: none;
  background: transparent;
  color: #9ab;
  font-size: 26px;
  cursor: pointer;
  line-height: 1;
}

.kp-icon-btn:hover {
  color: #00f2fe;
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
