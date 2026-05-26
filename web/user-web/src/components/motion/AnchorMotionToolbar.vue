<template>
  <Teleport to="body">
    <div
      ref="shellRef"
      class="anchor-motion-toolbar"
      :class="{ 'anchor-motion-toolbar--hidden': !anchorEl }"
      :style="shellStyle"
    >
      <LocaleThemeToolbar :compact="compact" :floating="floating" />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import gsap from "gsap";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useReducedMotion } from "@/composables/useReducedMotion";
import LocaleThemeToolbar from "@/components/LocaleThemeToolbar.vue";

const props = withDefaults(
  defineProps<{
    anchorEl?: HTMLElement | null;
    compact?: boolean;
    floating?: boolean;
    /** 外部布局变化时触发重新定位（如推荐栏收起） */
    layoutHint?: boolean;
  }>(),
  { anchorEl: null, compact: false, floating: false, layoutHint: false },
);

const shellRef = ref<HTMLElement | null>(null);
const shellStyle = ref<{ visibility: "visible" | "hidden" }>({ visibility: "hidden" });
const { reducedMotion } = useReducedMotion();

let tween: gsap.core.Tween | null = null;
let resizeObserver: ResizeObserver | null = null;
let lastPos: { left: number; top: number } | null = null;
let rafId = 0;

function killTween() {
  tween?.kill();
  tween = null;
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
    killTween();
    gsap.set(shell, { left: next.left, top: next.top, x: 0, y: 0, clearProps: "transform" });
    lastPos = next;
    return;
  }

  const dx = lastPos.left - next.left;
  const dy = lastPos.top - next.top;
  gsap.set(shell, { left: next.left, top: next.top });
  killTween();
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

function onResize() {
  scheduleSnap(false);
}

function observeAnchor(el: HTMLElement | null) {
  resizeObserver?.disconnect();
  resizeObserver = null;
  if (!el) return;
  resizeObserver = new ResizeObserver(() => scheduleSnap(true));
  resizeObserver.observe(el);
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
  () => props.floating,
  async () => {
    await nextTick();
    scheduleSnap(true);
  },
);

watch(
  () => props.layoutHint,
  async () => {
    await nextTick();
    scheduleSnap(true);
  },
);

onMounted(() => {
  window.addEventListener("resize", onResize);
  observeAnchor(props.anchorEl ?? null);
  scheduleSnap(false);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
  cancelAnimationFrame(rafId);
  resizeObserver?.disconnect();
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
