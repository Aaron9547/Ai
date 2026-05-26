<template>
  <div
    class="locale-theme-toolbar"
    :class="{
      'locale-theme-toolbar--compact': compact,
      'locale-theme-toolbar--floating': floating,
      'locale-theme-toolbar--theme-only': themeOnly,
    }"
    role="toolbar"
    :aria-label="t('ui.toolbarAria')"
  >
    <div class="lt-group">
      <template v-if="!themeOnly">
        <el-dropdown trigger="click" teleported popper-class="lt-dropdown-popper" @command="onLocale">
          <button type="button" class="lt-trigger" :title="t('ui.language')">
            <span class="lt-locale-mark-wrap" aria-hidden="true">
              <Transition name="motion-crossfade" mode="out-in">
                <span :key="localeMark" class="lt-locale-mark">{{ localeMark }}</span>
              </Transition>
            </span>
            <span v-if="!compact" class="lt-value">{{ localeLabel(store.locale) }}</span>
            <el-icon v-if="!compact" class="lt-caret" aria-hidden="true"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">{{ t("ui.zh") }}</el-dropdown-item>
              <el-dropdown-item command="en-US">{{ t("ui.en") }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <span class="lt-divider" aria-hidden="true" />
      </template>

      <el-dropdown trigger="click" teleported popper-class="lt-dropdown-popper" @command="onColorMode">
        <button type="button" class="lt-trigger" :title="t('ui.theme')">
          <span class="lt-icon-wrap" aria-hidden="true">
            <Transition name="motion-crossfade" mode="out-in">
              <el-icon :key="store.colorMode"><component :is="themeIcon" /></el-icon>
            </Transition>
          </span>
          <span v-if="!compact" class="lt-value">{{ themeLabel(store.colorMode) }}</span>
          <el-icon v-if="!compact" class="lt-caret" aria-hidden="true"><ArrowDown /></el-icon>
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
import { ArrowDown, Moon, Monitor, Sunny } from "@element-plus/icons-vue";
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useUiPreferencesStore, type AppLocale, type ColorMode } from "@/stores/uiPreferences";

withDefaults(
  defineProps<{
    /** 顶栏紧凑模式：仅图标，适合对话区标题旁 */
    compact?: boolean;
    /** 右下角悬浮样式（空对话欢迎页） */
    floating?: boolean;
    /** 仅展示主题切换（悬浮时常用） */
    themeOnly?: boolean;
  }>(),
  { compact: false, floating: false, themeOnly: false },
);

const { t } = useI18n();
const store = useUiPreferencesStore();

const localeMark = computed(() => (store.locale.startsWith("en") ? "En" : "中"));

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
  flex-shrink: 0;
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

.locale-theme-toolbar--compact .lt-trigger {
  padding: 8px 10px;
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
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.25em;
  min-height: 1.25em;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.locale-theme-toolbar--compact .lt-icon-wrap {
  font-size: 18px;
}

.lt-locale-mark-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.5em;
  min-height: 1em;
}

.lt-locale-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.35em;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1;
  color: var(--el-text-color-primary);
  font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
}

.locale-theme-toolbar--compact .lt-locale-mark {
  font-size: 13px;
  min-width: 1.5em;
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

.locale-theme-toolbar--compact .lt-divider {
  min-height: 32px;
  margin: 4px 0;
}

.locale-theme-toolbar--floating .lt-group {
  border-radius: 999px;
  box-shadow:
    0 4px 16px rgba(15, 23, 42, 0.1),
    0 1px 3px rgba(15, 23, 42, 0.06);
}

.locale-theme-toolbar--floating.locale-theme-toolbar--theme-only .lt-trigger {
  width: 44px;
  height: 44px;
  padding: 0;
  justify-content: center;
  border-radius: 999px;
}

.locale-theme-toolbar--floating.locale-theme-toolbar--theme-only .lt-icon-wrap {
  font-size: 20px;
}

html.dark .locale-theme-toolbar--floating .lt-group {
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.45),
    0 1px 3px rgba(0, 0, 0, 0.3);
}
</style>

<style>
.lt-dropdown-popper.el-popper {
  border-radius: 10px;
  padding: 4px 0;
}
</style>
