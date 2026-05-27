<template>
  <KnowledgePlanetOverlay
    :visible="visible"
    layout="mobile"
    :universe="universe"
    :weekly="weekly"
    :origin-rect="props.originRect"
    @close="onOverlayClose"
  />
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import {
  fetchKnowledgePlanetSummary,
  fetchKnowledgePlanetUniverse,
  fetchKnowledgePlanetWeeklyLatest,
  type KnowledgePlanetUniverse,
  type KnowledgePlanetWeeklyLatest,
  type KnowledgePlanetWarpOrigin,
} from "../../api/knowledgePlanet";
import KnowledgePlanetOverlay from "./KnowledgePlanetOverlay.vue";

const visible = defineModel<boolean>("visible", { default: false });

const props = withDefaults(
  defineProps<{
    authBump?: number;
    originRect?: KnowledgePlanetWarpOrigin | null;
  }>(),
  { authBump: 0, originRect: null },
);

const universe = ref<KnowledgePlanetUniverse | null>(null);
const weekly = ref<KnowledgePlanetWeeklyLatest | null>(null);

function onOverlayClose(): void {
  visible.value = false;
}

async function loadData(): Promise<void> {
  try {
    const summary = await fetchKnowledgePlanetSummary();
    if (!summary.enabled) {
      universe.value = { planets: [], nodes: [], links: [] };
      weekly.value = null;
      return;
    }
    const [u, w] = await Promise.all([
      fetchKnowledgePlanetUniverse(),
      fetchKnowledgePlanetWeeklyLatest().catch(() => ({
        weekStart: null,
        status: null,
        plan: null,
      })),
    ]);
    universe.value = u;
    weekly.value = w;
  } catch {
    universe.value = { planets: [], nodes: [], links: [] };
    weekly.value = null;
  }
}

watch(visible, (open) => {
  if (open) void loadData();
});

watch(
  () => props.authBump,
  (n, prev) => {
    if (n > 0 && n !== prev && visible.value) void loadData();
  },
);
</script>
