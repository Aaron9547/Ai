import { computed, onBeforeUnmount, onMounted, ref } from "vue";

/** 与样式中 @media 断点一致：&lt;768 手机，768–1024 中屏（含 1024），&gt;1024 桌面 */
export function useWindowBreakpoints() {
  const width = ref(typeof window !== "undefined" ? window.innerWidth : 1280);

  function read() {
    width.value = window.innerWidth;
  }

  onMounted(() => {
    read();
    window.addEventListener("resize", read, { passive: true });
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", read);
  });

  const isMobile = computed(() => width.value < 768);
  const isTablet = computed(() => width.value >= 768 && width.value <= 1024);
  const isDesktop = computed(() => width.value > 1024);

  return { width, isMobile, isTablet, isDesktop };
}
