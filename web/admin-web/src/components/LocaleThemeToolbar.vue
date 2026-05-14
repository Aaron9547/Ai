<template>
  <div class="locale-theme-toolbar" role="toolbar" :aria-label="t('ui.toolbarAria')">
    <div class="lt-group">
      <el-dropdown trigger="click" teleported popper-class="lt-dropdown-popper" @command="onLocale">
        <button type="button" class="lt-trigger">
          <span class="lt-icon-wrap" aria-hidden="true">
            <el-icon><Postcard /></el-icon>
          </span>
          <span class="lt-value">{{ localeLabel(store.locale) }}</span>
          <el-icon class="lt-caret" aria-hidden="true"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN">{{ t("ui.zh") }}</el-dropdown-item>
            <el-dropdown-item command="en-US">{{ t("ui.en") }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <span class="lt-divider" aria-hidden="true" />

      <el-dropdown trigger="click" teleported popper-class="lt-dropdown-popper" @command="onColorMode">
        <button type="button" class="lt-trigger">
          <span class="lt-icon-wrap" aria-hidden="true">
            <el-icon><component :is="themeIcon" /></el-icon>
          </span>
          <span class="lt-value">{{ themeLabel(store.colorMode) }}</span>
          <el-icon class="lt-caret" aria-hidden="true"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="light">{{ t("ui.themeLight") }}</el-dropdown-item>
            <el-dropdown-item command="dark">{{ t("ui.themeDark") }}</el-dropdown-item>
            <el-dropdown-item command="system">{{ t("ui.themeSystem") }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Moon, Monitor, Postcard, Sunny } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useUiPreferencesStore, type AppLocale, type ColorMode } from "@/stores/uiPreferences";

const { t } = useI18n();
const store = useUiPreferencesStore();

const themeIcon = computed(() => {
  if (store.colorMode === "dark") return Moon;
  if (store.colorMode === "light") return Sunny;
  return Monitor;
});

function localeLabel(l: AppLocale) {
  return l.startsWith("en") ? t("ui.en") : t("ui.zh");
}

function themeLabel(m: ColorMode) {
  if (m === "dark") return t("ui.themeDark");
  if (m === "light") return t("ui.themeLight");
  return t("ui.themeSystem");
}

function onLocale(cmd: string) {
  if (cmd === "zh-CN" || cmd === "en-US") store.setLocale(cmd);
}

function onColorMode(cmd: string) {
  if (cmd === "light" || cmd === "dark" || cmd === "system") store.setColorMode(cmd);
}
</script>

<style scoped>
.locale-theme-toolbar {
  display: inline-flex;
  align-items: center;
}

.lt-group {
  display: inline-flex;
  align-items: stretch;
  border-radius: 10px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

html.dark .lt-group {
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.lt-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  margin: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  line-height: 1.2;
  white-space: nowrap;
  transition: background-color 0.14s ease, color 0.14s ease;
}

.lt-trigger:hover {
  background: var(--el-fill-color-light);
}

.lt-trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
  z-index: 1;
}

.lt-icon-wrap {
  display: inline-flex;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.lt-value {
  min-width: 2.5em;
  text-align: left;
}

.lt-caret {
  margin-left: -1px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  opacity: 0.85;
}

.lt-divider {
  width: 1px;
  align-self: stretch;
  min-height: 28px;
  margin: 6px 0;
  background: var(--el-border-color-lighter);
  flex-shrink: 0;
}
</style>

<style>
.lt-dropdown-popper.el-popper {
  border-radius: 10px;
  padding: 4px 0;
}
</style>
