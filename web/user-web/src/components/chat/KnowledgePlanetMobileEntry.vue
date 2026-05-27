<template>
  <div v-if="summary?.enabled" class="kp-m-entry-wrap">
    <button
      ref="entryRef"
      type="button"
      class="kp-m-entry"
      :aria-label="t('knowledgePlanet.enter')"
      @click="onOpen"
    >
      <span class="kp-m-entry-orbit" aria-hidden="true">
        <span class="kp-m-entry-core" />
      </span>
      <span class="kp-m-entry-text">
        <span class="kp-m-entry-title">{{ t("knowledgePlanet.sectionTitle") }}</span>
        <span class="kp-m-entry-sub">
          {{
            t("knowledgePlanet.mobile.entrySubtitle", {
              nodes: summary?.nodeCount ?? 0,
              planets: summary?.planetCount ?? 0,
            })
          }}
        </span>
      </span>
      <el-icon class="kp-m-entry-chevron" :size="16"><ArrowRight /></el-icon>
    </button>

    <KnowledgePlanetMobilePage
      v-model:visible="pageOpen"
      :auth-bump="authBump"
      :origin-rect="originRect"
    />
  </div>
</template>

<script setup lang="ts">
import { ArrowRight } from "@element-plus/icons-vue";
import { ref, shallowRef, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  fetchKnowledgePlanetSummary,
  warpOriginFromRect,
  type KnowledgePlanetSummary,
  type KnowledgePlanetWarpOrigin,
} from "../../api/knowledgePlanet";
import KnowledgePlanetMobilePage from "./KnowledgePlanetMobilePage.vue";

const props = withDefaults(
  defineProps<{
    authBump?: number;
  }>(),
  { authBump: 0 },
);

const emit = defineEmits<{
  opened: [];
}>();

const { t } = useI18n();
const summary = ref<KnowledgePlanetSummary | null>(null);
const pageOpen = ref(false);
const entryRef = shallowRef<HTMLButtonElement | null>(null);
const originRect = ref<KnowledgePlanetWarpOrigin | null>(null);

async function loadSummary(): Promise<void> {
  try {
    summary.value = await fetchKnowledgePlanetSummary();
  } catch {
    summary.value = null;
  }
}

function onOpen(): void {
  const el = entryRef.value;
  originRect.value = el ? warpOriginFromRect(el.getBoundingClientRect()) : null;
  emit("opened");
  pageOpen.value = true;
}

watch(
  () => props.authBump,
  (n, prev) => {
    if (n > 0 && n !== prev) void loadSummary();
  },
);

void loadSummary();
</script>

<style scoped>
.kp-m-entry-wrap {
  flex-shrink: 0;
  padding: 0 12px 10px;
}

.kp-m-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--chat-border, rgba(0, 0, 0, 0.08));
  border-radius: 14px;
  background: var(--chat-bg-elevated, #fff);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.kp-m-entry:active {
  transform: scale(0.99);
}

.kp-m-entry-orbit {
  position: relative;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, rgba(99, 102, 241, 0.35), rgba(79, 70, 229, 0.08) 70%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.kp-m-entry-core {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.45);
}

.kp-m-entry-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kp-m-entry-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--chat-text, #0f172a);
  line-height: 1.25;
}

.kp-m-entry-sub {
  font-size: 12px;
  color: var(--chat-text-muted, #64748b);
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kp-m-entry-chevron {
  flex-shrink: 0;
  color: var(--chat-accent, #4f46e5);
}

:global(html.dark) .kp-m-entry {
  background: var(--chat-bg-elevated, #18181b);
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .kp-m-entry-title {
  color: #f4f4f5;
}

:global(html.dark) .kp-m-entry-sub {
  color: #a1a1aa;
}
</style>
