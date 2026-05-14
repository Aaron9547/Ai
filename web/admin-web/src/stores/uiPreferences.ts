import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { i18n } from "@/i18n";
import type { AdminUiLocaleTag } from "@/api/adminUiNegotiation";

/** 与 {@link AdminUiLocaleTag} 一致；管理端语言切换与后端协商参数共用 */
export type AppLocale = AdminUiLocaleTag;export type ColorMode = "light" | "dark" | "system";

/** 与 axios {@code Accept-Language} 等共用，勿改字符串以免前后端/缓存键不一致 */
export const AI_ADMIN_LOCALE_LS_KEY = "ai-admin-locale";

const LS_LOCALE = AI_ADMIN_LOCALE_LS_KEY;
const LS_COLOR = "ai-admin-color-mode";

function prefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export const useUiPreferencesStore = defineStore("uiPreferences", () => {
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
    i18n.global.locale.value = next;
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
        i18n.global.locale.value = l;
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
