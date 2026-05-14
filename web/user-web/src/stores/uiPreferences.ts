import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { i18n } from "@/i18n";

export type AppLocale = "zh-CN" | "en-US";
export type ColorMode = "light" | "dark" | "system";

const LS_LOCALE = "ai-user-locale";
const LS_COLOR = "ai-user-color-mode";

function prefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export const useUiPreferencesStore = defineStore("userUiPreferences", () => {
  const locale = ref<AppLocale>("zh-CN");
  const colorMode = ref<ColorMode>("system");

  const isDark = computed(() => {
    if (colorMode.value === "dark") return true;
    if (colorMode.value === "light") return false;
    return prefersDark();
  });

  function applyDom() {
    const dark = isDark.value;
    document.documentElement.classList.toggle("dark", dark);
    document.documentElement.lang = locale.value.startsWith("en") ? "en" : "zh-CN";
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
  }

  function setLocale(next: AppLocale) {
    locale.value = next;
    (i18n.global.locale as unknown as { value: string }).value = next;
    try {
      localStorage.setItem(LS_LOCALE, next);
    } catch {
      /* ignore */
    }
    applyDom();
  }

  function setColorMode(next: ColorMode) {
    colorMode.value = next;
    try {
      localStorage.setItem(LS_COLOR, next);
    } catch {
      /* ignore */
    }
    applyDom();
  }

  function hydrate() {
    try {
      const l = localStorage.getItem(LS_LOCALE);
      if (l === "en-US" || l === "zh-CN") {
        locale.value = l;
        (i18n.global.locale as unknown as { value: string }).value = l;
      }
      const c = localStorage.getItem(LS_COLOR);
      if (c === "light" || c === "dark" || c === "system") {
        colorMode.value = c;
      }
    } catch {
      /* ignore */
    }
    applyDom();
  }

  return {
    locale,
    colorMode,
    isDark,
    setLocale,
    setColorMode,
    hydrate,
    applyDom,
  };
});
