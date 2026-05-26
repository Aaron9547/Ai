import { onBeforeUnmount, onMounted, ref } from "vue";

const QUERY = "(prefers-reduced-motion: reduce)";

export function useReducedMotion() {
  const reducedMotion = ref(false);
  let mql: MediaQueryList | null = null;

  function sync() {
    reducedMotion.value = mql?.matches ?? false;
  }

  onMounted(() => {
    mql = window.matchMedia(QUERY);
    sync();
    mql.addEventListener("change", sync);
  });

  onBeforeUnmount(() => {
    mql?.removeEventListener("change", sync);
    mql = null;
  });

  return { reducedMotion };
}
