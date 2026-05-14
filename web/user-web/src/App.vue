<template>
  <el-config-provider :locale="elementLocale">
    <RouterView />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { computed, onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

const { locale } = useI18n();
const pref = useUiPreferencesStore();

const elementLocale = computed(() => (locale.value.startsWith("en") ? en : zhCn));

let mq: MediaQueryList | null = null;
function onScheme() {
  if (pref.colorMode === "system") pref.applyDom();
}

onMounted(() => {
  mq = window.matchMedia("(prefers-color-scheme: dark)");
  mq.addEventListener("change", onScheme);
});

onUnmounted(() => {
  mq?.removeEventListener("change", onScheme);
});
</script>
