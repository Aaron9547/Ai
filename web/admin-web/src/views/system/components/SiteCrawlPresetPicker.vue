<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";

export type SiteCrawlPresetValue = "CONSERVATIVE" | "BALANCED" | "AGGRESSIVE" | "CUSTOM";

const preset = defineModel<SiteCrawlPresetValue>({ required: true });

const { t } = useI18n();

const options = computed(() => [
  {
    value: "CONSERVATIVE" as const,
    title: t("admin.shell.siteCrawl.presetConservative"),
    desc: t("admin.shell.siteCrawl.presetConservativeDesc"),
  },
  {
    value: "BALANCED" as const,
    title: t("admin.shell.siteCrawl.presetBalanced"),
    desc: t("admin.shell.siteCrawl.presetBalancedDesc"),
  },
  {
    value: "AGGRESSIVE" as const,
    title: t("admin.shell.siteCrawl.presetAggressive"),
    desc: t("admin.shell.siteCrawl.presetAggressiveDesc"),
  },
  {
    value: "CUSTOM" as const,
    title: t("admin.shell.siteCrawl.presetCustom"),
    desc: t("admin.shell.siteCrawl.presetCustomDesc"),
  },
]);

function select(next: SiteCrawlPresetValue) {
  preset.value = next;
}
</script>

<template>
  <div class="site-crawl-preset-cards" role="radiogroup" :aria-label="t('admin.shell.siteCrawl.presetLabel')">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="site-crawl-preset-card"
      :class="{ 'is-active': preset === opt.value }"
      :aria-pressed="preset === opt.value"
      @click="select(opt.value)"
    >
      <span class="site-crawl-preset-card-title">{{ opt.title }}</span>
      <span class="site-crawl-preset-card-desc">{{ opt.desc }}</span>
    </button>
  </div>
</template>

<style scoped>
.site-crawl-preset-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  width: 100%;
}

.site-crawl-preset-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: var(--el-bg-color);
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.site-crawl-preset-card.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.site-crawl-preset-card-title {
  font-weight: 600;
  font-size: 14px;
}

.site-crawl-preset-card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
