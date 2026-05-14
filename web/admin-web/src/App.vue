<template>
  <el-config-provider :locale="elementLocale">
    <RouterView />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { computed } from "vue";
import { useI18n } from "vue-i18n";

const { locale } = useI18n();

const elementLocale = computed(() => (locale.value.startsWith("en") ? en : zhCn));
</script>
