<template>
  <Teleport to="body">
    <div
      ref="shellRef"
      class="anchor-motion-toolbar"
      :class="{ 'anchor-motion-toolbar--hidden': !anchorEl }"
      :style="shellStyle"
    >
      <LocaleThemeToolbar :compact="compact" :floating="floating" :theme-only="themeOnly" :locale-only="localeOnly" />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import gsap from "gsap";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useReducedMotion } from "@/composables/useReducedMotion";
import LocaleThemeToolbar from "@/components/LocaleThemeToolbar.vue";

/** 与 sidebar-collapse.css --chat-shell-duration 对齐，略留余量 */
const LAYOUT_SETTLE_MS = 480;

const props = withDefaults(
  defineProps<{
    anchorEl?: HTMLElement | null;
    /** 随侧栏展开/收起变化的布局容器，用于监听宽度过渡 */
    layoutRootEl?: HTMLElement | null;
    /** 侧栏态组合键（如左右侧栏收起态），变化时触发重新定位 */
    layoutKey?: string;
    compact?: boolean;
    floating?: boolean;
    themeOnly?: boolean;
    localeOnly?: boolean;
  }>(),
  {
    anchorEl: null,
    layoutRootEl: null,
    layoutKey: "",
    compact: false,
    floating: false,
    themeOnly: false,
    localeOnly: false,
  },
);

const shellRef = ref<HTMLElement | null>(null);
const shellStyle = ref<{ visibility: "visible" | "hidden" }>({ visibility: "hidden" });
const { reducedMotion } = useReducedMotion();

let tween: gsap.core.Tween | null = null;
let anchorResizeObserver: ResizeObserver | null = null;
let layoutResizeObserver: ResizeObserver | null = null;
let lastPos: { left: number; top: number } | null = null;
let rafId = 0;
let layoutSettleTimer: ReturnType<typeof setTimeout> | null = null;

function killTween() {
  tween?.kill();
  tween = null;
}

function resetShellTransform(shell: HTMLElement) {
  killTween();
  gsap.set(shell, { x: 0, y: 0, clearProps: "transform" });
}

function snapToAnchor(animate: boolean) {
  const anchor = props.anchorEl;
  const shell = shellRef.value;
  if (!anchor || !shell) {
    shellStyle.value = { visibility: "hidden" };
    return;
  }

  const rect = anchor.getBoundingClientRect();
  if (rect.width <= 0 && rect.height <= 0) {
    shellStyle.value = { visibility: "hidden" };
    return;
  }

  shellStyle.value = { visibility: "visible" };
  const next = { left: rect.left, top: rect.top };

  if (!animate || reducedMotion.value || lastPos === null) {
    resetShellTransform(shell);
    gsap.set(shell, { left: next.left, top: next.top });
    lastPos = next;
    return;
  }

  resetShellTransform(shell);
  const dx = lastPos.left - next.left;
  const dy = lastPos.top - next.top;
  gsap.set(shell, { left: next.left, top: next.top });
  tween = gsap.fromTo(
    shell,
    { x: dx, y: dy },
    {
      x: 0,
      y: 0,
      duration: 0.32,
      ease: "power2.out",
      onComplete: () => {
        gsap.set(shell, { clearProps: "transform" });
      },
    },
  );
  lastPos = next;
}

function scheduleSnap(animate: boolean) {
  cancelAnimationFrame(rafId);
  rafId = requestAnimationFrame(() => {
    rafId = requestAnimationFrame(() => {
      snapToAnchor(animate);
    });
  });
}

function scheduleLayoutSettle() {
  scheduleSnap(false);
  if (layoutSettleTimer) clearTimeout(layoutSettleTimer);
  layoutSettleTimer = setTimeout(() => {
    layoutSettleTimer = null;
    scheduleSnap(false);
  }, LAYOUT_SETTLE_MS);
}

function onResize() {
  scheduleSnap(false);
}

function observeAnchor(el: HTMLElement | null) {
  anchorResizeObserver?.disconnect();
  anchorResizeObserver = null;
  if (!el) return;
  anchorResizeObserver = new ResizeObserver(() => scheduleSnap(false));
  anchorResizeObserver.observe(el);
}

function observeLayoutRoot(el: HTMLElement | null) {
  layoutResizeObserver?.disconnect();
  layoutResizeObserver = null;
  if (!el) return;
  layoutResizeObserver = new ResizeObserver(() => scheduleSnap(false));
  layoutResizeObserver.observe(el);
}

watch(
  () => props.anchorEl,
  async (el, prev) => {
    observeAnchor(el);
    const animate = prev != null && prev !== el;
    await nextTick();
    scheduleSnap(animate);
  },
);

watch(
  () => props.layoutRootEl,
  (el) => {
    observeLayoutRoot(el);
  },
);

watch(
  () => props.layoutKey,
  async () => {
    await nextTick();
    scheduleLayoutSettle();
  },
);

onMounted(() => {
  window.addEventListener("resize", onResize);
  observeAnchor(props.anchorEl ?? null);
  observeLayoutRoot(props.layoutRootEl ?? null);
  scheduleSnap(false);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
  cancelAnimationFrame(rafId);
  if (layoutSettleTimer) clearTimeout(layoutSettleTimer);
  anchorResizeObserver?.disconnect();
  layoutResizeObserver?.disconnect();
  killTween();
});
</script>

<style scoped>
.anchor-motion-toolbar {
  position: fixed;
  z-index: 1200;
  pointer-events: auto;
  will-change: transform, left, top;
}

.anchor-motion-toolbar--hidden {
  pointer-events: none;
}
</style>
