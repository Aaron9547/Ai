<template>
  <button
    v-if="summary?.enabled"
    ref="cardRef"
    type="button"
    class="planet-portal-wrapper"
    :class="{ 'planet-portal-wrapper--pulse': pulseActive }"
    :aria-label="t('knowledgePlanet.enter')"
    @click="onOpen"
    @mouseenter="sceneHandle?.setHoverBoost(true)"
    @mouseleave="sceneHandle?.setHoverBoost(false)"
  >
    <div class="portal-atmosphere" aria-hidden="true" />
    <div ref="canvasHost" class="planet-portal-canvas" />
    <div class="portal-content" aria-hidden="true">
      <div class="portal-content-top">
        <span class="portal-tag">{{ t("knowledgePlanet.portalTag") }}</span>
        <span class="portal-sync">
          <span class="portal-sync-dot" />
          {{ t("knowledgePlanet.syncLive") }}
        </span>
      </div>
      <div class="portal-main">
        <span class="planet-name">{{ dominantName }}</span>
        <div class="portal-stats">
          <div class="portal-stat">
            <span class="portal-stat-val">{{ summary?.nodeCount ?? 0 }}</span>
            <span class="portal-stat-lbl">{{ t("knowledgePlanet.statNodes") }}</span>
          </div>
          <div class="portal-stat">
            <span class="portal-stat-val">{{ summary?.planetCount ?? 0 }}</span>
            <span class="portal-stat-lbl">{{ t("knowledgePlanet.statDimensions") }}</span>
          </div>
        </div>
      </div>
      <span class="portal-enter-hint">{{ t("knowledgePlanet.enterHint") }}</span>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { KnowledgePlanetSummary } from "../../api/knowledgePlanet";
import { warpOriginFromRect } from "../../api/knowledgePlanet";
import { useKnowledgePlanetPulse } from "../../composables/useKnowledgePlanetPulse";
import {
  useKnowledgePlanetScene,
  type PlanetSceneHandle,
} from "../../composables/useKnowledgePlanetScene";

const props = defineProps<{
  summary: KnowledgePlanetSummary | null;
}>();

const emit = defineEmits<{
  open: [origin: ReturnType<typeof warpOriginFromRect>];
}>();

const { t } = useI18n();
const { pulseGeneration } = useKnowledgePlanetPulse();

const cardRef = shallowRef<HTMLButtonElement | null>(null);
const pulseActive = ref(false);
let pulseTimer = 0;

const dominantName = computed(
  () => props.summary?.dominantPlanetName?.trim() || t("knowledgePlanet.cardTitle"),
);

const canvasHost = shallowRef<HTMLElement | null>(null);
const { mount } = useKnowledgePlanetScene(canvasHost);
const sceneHandle = ref<PlanetSceneHandle | null>(null);

function onOpen() {
  const el = cardRef.value;
  if (!el) return;
  emit("open", warpOriginFromRect(el.getBoundingClientRect()));
}

watch(pulseGeneration, () => {
  pulseActive.value = true;
  window.clearTimeout(pulseTimer);
  pulseTimer = window.setTimeout(() => {
    pulseActive.value = false;
  }, 900);
});

onMounted(() => {
  sceneHandle.value = mount();
});

onUnmounted(() => {
  window.clearTimeout(pulseTimer);
  sceneHandle.value?.dispose();
});

watch(
  () => props.summary?.enabled,
  (en) => {
    if (en) {
      sceneHandle.value?.dispose();
      sceneHandle.value = mount();
    }
  },
);
</script>

<style scoped>
.planet-portal-wrapper {
  position: relative;
  flex-shrink: 0;
  display: block;
  width: 100%;
  height: 300px;
  margin: 0 0 16px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  overflow: visible;
  text-align: center;
}

.planet-portal-wrapper--pulse .portal-atmosphere {
  animation: portal-flash 0.85s ease-out;
}

.portal-atmosphere {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 220px;
  height: 220px;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(0, 242, 254, 0.18) 0%, transparent 70%);
  filter: blur(36px);
  z-index: 0;
  pointer-events: none;
  animation: atmosphere-breath 4s ease-in-out infinite;
}

.planet-portal-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  -webkit-mask-image: radial-gradient(
    circle at 50% 48%,
    #000 28%,
    rgba(0, 0, 0, 0.55) 58%,
    transparent 88%
  );
  mask-image: radial-gradient(
    circle at 50% 48%,
    #000 28%,
    rgba(0, 0, 0, 0.55) 58%,
    transparent 88%
  );
}

.portal-content {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 14px 16px 18px;
  pointer-events: none;
}

.portal-content-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.portal-tag {
  font-size: 9px;
  letter-spacing: 0.22em;
  color: rgba(0, 242, 254, 0.65);
  font-family: ui-monospace, monospace;
}

.portal-sync {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  color: var(--rec-text-muted, #99aabb);
}

.portal-sync-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #00f2fe;
  box-shadow: 0 0 8px #00f2fe;
}

.portal-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: auto;
}

.planet-name {
  font-size: 15px;
  font-weight: 300;
  letter-spacing: 0.08em;
  color: var(--rec-text-title, #334455);
}

.portal-stats {
  display: flex;
  gap: 28px;
  justify-content: center;
}

.portal-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.portal-stat-val {
  font-size: 20px;
  font-weight: 300;
  color: var(--rec-text-title, #2c3e50);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.portal-stat-lbl {
  font-size: 9px;
  color: rgba(0, 242, 254, 0.55);
  letter-spacing: 0.08em;
}

.portal-enter-hint {
  font-size: 9px;
  letter-spacing: 0.2em;
  color: rgba(0, 242, 254, 0.55);
  font-family: ui-monospace, monospace;
}

@keyframes atmosphere-breath {
  0%,
  100% {
    opacity: 0.45;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.15);
  }
}

@keyframes portal-flash {
  45% {
    filter: blur(36px) brightness(1.4);
  }
}

:global(html.dark) .planet-name,
:global(html.dark) .portal-stat-val {
  color: #e4e4e7;
}

:global(html.dark) .portal-sync {
  color: #a1a1aa;
}
</style>
