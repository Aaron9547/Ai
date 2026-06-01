<template>
  <nav
    v-show="visible"
    class="thread-question-rail"
    :class="{
      'thread-question-rail--mobile': mobile,
      'thread-question-rail--dragging': dragging,
    }"
    :aria-label="t('chat.questionRailAria')"
  >
    <div
      ref="trackRef"
      class="thread-question-rail-track"
      @pointerdown.prevent="onTrackPointerDown"
    >
      <div
        class="thread-question-rail-viewport"
        aria-hidden="true"
        :style="{ top: `${viewportTopPct}%`, height: `${viewportHeightPct}%` }"
      />
      <template v-for="(node, i) in layoutNodes" :key="node.rowKey">
        <el-tooltip
          v-if="!mobile"
          :content="node.preview"
          placement="left"
          :show-after="200"
          :disabled="dragging"
          popper-class="thread-question-rail-tooltip"
        >
          <span
            role="button"
            tabindex="0"
            class="thread-question-node"
            :class="{ 'thread-question-node--active': activeIndex === node.messageIndex }"
            :style="{ top: `${node.topPct}%` }"
            :data-message-index="node.messageIndex"
            :aria-label="t('chat.questionNodeAria', { n: i + 1 })"
          />
        </el-tooltip>
        <el-popover
          v-else
          placement="left"
          :width="260"
          trigger="click"
          :disabled="dragging"
          popper-class="thread-question-rail-popover"
        >
          <template #reference>
            <span
              role="button"
              tabindex="0"
              class="thread-question-node"
              :class="{ 'thread-question-node--active': activeIndex === node.messageIndex }"
              :style="{ top: `${node.topPct}%` }"
              :data-message-index="node.messageIndex"
              :aria-label="t('chat.questionNodeAria', { n: i + 1 })"
            />
          </template>
          <p class="thread-question-popover-preview">{{ node.preview }}</p>
          <button type="button" class="thread-question-popover-jump" @click="onMobileJump(node)">
            {{ t("chat.questionJump") }}
          </button>
        </el-popover>
      </template>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

export type QuestionAnchor = {
  messageIndex: number;
  preview: string;
  rowKey: string;
};

const DRAG_THRESHOLD_PX = 5;

const props = defineProps<{
  anchors: QuestionAnchor[];
  scrollWrap: HTMLElement | null;
  mobile?: boolean;
}>();

const emit = defineEmits<{
  navigate: [messageIndex: number];
}>();

const { t } = useI18n();

const trackRef = ref<HTMLElement | null>(null);
const layoutNodes = ref<{ messageIndex: number; preview: string; rowKey: string; topPct: number }[]>([]);
const visible = ref(false);
const activeIndex = ref<number | null>(null);
const viewportTopPct = ref(0);
const viewportHeightPct = ref(100);
const dragging = ref(false);

let resizeObserver: ResizeObserver | null = null;
let scrollRaf: number | null = null;
let railDrag: {
  startY: number;
  startScrollTop: number;
  moved: boolean;
  tapMessageIndex: number | null;
} | null = null;

function updateViewport() {
  const wrap = props.scrollWrap;
  if (!wrap) return;
  const { scrollTop, scrollHeight, clientHeight } = wrap;
  const maxScroll = scrollHeight - clientHeight;
  if (maxScroll <= 0) {
    viewportHeightPct.value = 100;
    viewportTopPct.value = 0;
    return;
  }
  viewportHeightPct.value = Math.max(8, (clientHeight / scrollHeight) * 100);
  const travel = 100 - viewportHeightPct.value;
  viewportTopPct.value = (scrollTop / maxScroll) * travel;
}

function measureLayout() {
  const wrap = props.scrollWrap;
  if (!wrap) {
    layoutNodes.value = [];
    visible.value = false;
    return;
  }
  const scrollHeight = wrap.scrollHeight;
  const clientHeight = wrap.clientHeight;
  const scrollable = scrollHeight > clientHeight + 4;
  if (!scrollable) {
    layoutNodes.value = [];
    visible.value = false;
    return;
  }
  const nodes: typeof layoutNodes.value = [];
  for (const a of props.anchors) {
    const el = wrap.querySelector(`[data-msg-idx="${a.messageIndex}"]`) as HTMLElement | null;
    if (!el) continue;
    const top = el.offsetTop + Math.min(el.offsetHeight * 0.15, 24);
    const topPct = Math.min(98, Math.max(1, (top / scrollHeight) * 100));
    nodes.push({ ...a, topPct });
  }
  layoutNodes.value = nodes;
  visible.value = true;
  updateViewport();
  updateActiveFromScroll();
}

function updateActiveFromScroll() {
  const wrap = props.scrollWrap;
  if (!wrap || layoutNodes.value.length === 0) {
    activeIndex.value = null;
    return;
  }
  const scrollTop = wrap.scrollTop + 48;
  let best: number | null = null;
  for (const n of layoutNodes.value) {
    const el = wrap.querySelector(`[data-msg-idx="${n.messageIndex}"]`) as HTMLElement | null;
    if (!el) continue;
    if (el.offsetTop <= scrollTop) {
      best = n.messageIndex;
    }
  }
  activeIndex.value = best;
}

function onWrapScroll() {
  if (scrollRaf != null) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = null;
    updateViewport();
    updateActiveFromScroll();
  });
}

function scheduleMeasure() {
  void nextTick(() => measureLayout());
}

function scrollWrapToClientY(clientY: number) {
  const wrap = props.scrollWrap;
  const track = trackRef.value;
  if (!wrap || !track) return;
  const rect = track.getBoundingClientRect();
  const ratio = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height));
  const maxScroll = wrap.scrollHeight - wrap.clientHeight;
  wrap.scrollTop = ratio * maxScroll;
}

function scrollWrapByPointerDelta(clientY: number) {
  const wrap = props.scrollWrap;
  const track = trackRef.value;
  if (!wrap || !track || !railDrag) return;
  const trackH = track.clientHeight;
  const maxTravel = Math.max(1, trackH);
  const delta = clientY - railDrag.startY;
  const maxScroll = wrap.scrollHeight - wrap.clientHeight;
  wrap.scrollTop = Math.min(
    maxScroll,
    Math.max(0, railDrag.startScrollTop + (delta / maxTravel) * maxScroll),
  );
}

function resolveTapMessageIndex(target: EventTarget | null): number | null {
  const el = (target as HTMLElement | null)?.closest?.("[data-message-index]");
  if (!el) return null;
  const raw = el.getAttribute("data-message-index");
  if (raw == null) return null;
  const idx = Number.parseInt(raw, 10);
  return Number.isFinite(idx) ? idx : null;
}

function onTrackPointerDown(e: PointerEvent) {
  const wrap = props.scrollWrap;
  if (!wrap || !trackRef.value) return;
  railDrag = {
    startY: e.clientY,
    startScrollTop: wrap.scrollTop,
    moved: false,
    tapMessageIndex: resolveTapMessageIndex(e.target),
  };
  dragging.value = true;
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  document.addEventListener("pointermove", onTrackPointerMove);
  document.addEventListener("pointerup", onTrackPointerUp);
  document.addEventListener("pointercancel", onTrackPointerUp);
}

function onTrackPointerMove(e: PointerEvent) {
  if (!railDrag) return;
  if (Math.abs(e.clientY - railDrag.startY) >= DRAG_THRESHOLD_PX) {
    railDrag.moved = true;
  }
  scrollWrapByPointerDelta(e.clientY);
}

function onTrackPointerUp(e: PointerEvent) {
  if (!railDrag) return;
  const { moved, tapMessageIndex } = railDrag;
  if (!moved) {
    if (tapMessageIndex != null) {
      emit("navigate", tapMessageIndex);
    } else {
      scrollWrapToClientY(e.clientY);
    }
  }
  endRailDrag(e.pointerId);
}

function endRailDrag(pointerId?: number) {
  if (trackRef.value && pointerId != null) {
    try {
      trackRef.value.releasePointerCapture(pointerId);
    } catch {
      /* already released */
    }
  }
  railDrag = null;
  dragging.value = false;
  document.removeEventListener("pointermove", onTrackPointerMove);
  document.removeEventListener("pointerup", onTrackPointerUp);
  document.removeEventListener("pointercancel", onTrackPointerUp);
}

function onMobileJump(node: QuestionAnchor) {
  emit("navigate", node.messageIndex);
}

function bindWrap(wrap: HTMLElement | null) {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  wrap?.removeEventListener("scroll", onWrapScroll);
  if (!wrap) return;
  wrap.addEventListener("scroll", onWrapScroll, { passive: true });
  resizeObserver = new ResizeObserver(() => scheduleMeasure());
  resizeObserver.observe(wrap);
  const inner = wrap.firstElementChild;
  if (inner) resizeObserver.observe(inner);
  scheduleMeasure();
}

watch(
  () => props.scrollWrap,
  (wrap) => bindWrap(wrap),
  { immediate: true },
);

watch(
  () => props.anchors,
  () => scheduleMeasure(),
  { deep: true },
);

onMounted(() => scheduleMeasure());

onBeforeUnmount(() => {
  if (scrollRaf != null) cancelAnimationFrame(scrollRaf);
  props.scrollWrap?.removeEventListener("scroll", onWrapScroll);
  resizeObserver?.disconnect();
  endRailDrag();
});
</script>

<style scoped>
.thread-question-rail {
  flex: 0 0 40px;
  width: 40px;
  min-width: 40px;
  align-self: stretch;
  position: relative;
  z-index: 12;
  box-sizing: border-box;
  border-left: 1px solid var(--chat-border, rgba(0, 0, 0, 0.06));
  background: var(--chat-bg-main, #f8fafc);
}

.thread-question-rail--mobile {
  flex: 0 0 32px;
  width: 32px;
  min-width: 32px;
}

.thread-question-rail-track {
  position: relative;
  height: 100%;
  min-height: 120px;
  margin: 8px 4px;
  border-radius: 6px;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.thread-question-rail--dragging .thread-question-rail-track {
  cursor: grabbing;
}

.thread-question-rail-viewport {
  position: absolute;
  left: 2px;
  right: 2px;
  min-height: 24px;
  border-radius: 5px;
  background: rgba(79, 70, 229, 0.1);
  border: 1px solid rgba(79, 70, 229, 0.22);
  pointer-events: none;
  z-index: 1;
  transition:
    top 0.45s cubic-bezier(0.33, 1, 0.68, 1),
    height 0.3s ease,
    background 0.12s ease,
    border-color 0.12s ease;
  will-change: top;
}

.thread-question-rail--dragging .thread-question-rail-viewport {
  transition:
    background 0.12s ease,
    border-color 0.12s ease;
  background: rgba(79, 70, 229, 0.18);
  border-color: rgba(79, 70, 229, 0.38);
}

.thread-question-node {
  position: absolute;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 18px;
  height: 5px;
  border-radius: 2px;
  background: var(--chat-accent, #19c37d);
  opacity: 0.55;
  z-index: 2;
  transition:
    top 0.45s cubic-bezier(0.33, 1, 0.68, 1),
    opacity 0.15s ease,
    width 0.15s ease,
    background 0.15s ease;
}

.thread-question-rail--dragging .thread-question-node {
  transition:
    opacity 0.15s ease,
    width 0.15s ease,
    background 0.15s ease;
}

.thread-question-node:hover,
.thread-question-node--active {
  opacity: 1;
  width: 22px;
  background: var(--nexus-brand-600, #4f46e5);
}

.thread-question-node--active {
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.25);
}

.thread-question-popover-preview {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--chat-text-primary, #0f172a);
  word-break: break-word;
  max-height: 120px;
  overflow-y: auto;
}

.thread-question-popover-jump {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: var(--nexus-brand-600, #4f46e5);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.thread-question-popover-jump:hover {
  filter: brightness(1.06);
}
</style>

<style>
.thread-question-rail-tooltip {
  max-width: min(320px, 70vw);
  line-height: 1.5;
  word-break: break-word;
}
</style>
