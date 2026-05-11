import { computed, onBeforeUnmount, onMounted, ref } from "vue";

/** 与样式中 @media 断点一致：&lt;720 手机，720–1023 平板，≥1024 桌面 */
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

  const isMobile = computed(() => width.value < 720);
  const isTablet = computed(() => width.value >= 720 && width.value < 1024);
  const isDesktop = computed(() => width.value >= 1024);

  return { width, isMobile, isTablet, isDesktop };
}
