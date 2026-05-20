<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";

export type CrawlFormMode = "recurring" | "single";

const mode = defineModel<CrawlFormMode>({ required: true });

const { t } = useI18n();

const options = computed(() => [
  {
    value: "recurring" as const,
    title: t("views.kbMatrix.crawlModeRecurring"),
    desc: t("views.kbMatrix.crawlModeRecurringDesc"),
  },
  {
    value: "single" as const,
    title: t("views.kbMatrix.crawlModeSingle"),
    desc: t("views.kbMatrix.crawlModeSingleDesc"),
  },
]);

function select(next: CrawlFormMode) {
  mode.value = next;
}
</script>

<template>
  <div class="crawl-mode-cards" role="radiogroup" :aria-label="t('views.kbMatrix.crawlModeLabel')">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="crawl-mode-card"
      :class="{ 'is-active': mode === opt.value }"
      :aria-pressed="mode === opt.value"
      @click="select(opt.value)"
    >
      <span class="crawl-mode-card-title">{{ opt.title }}</span>
      <span class="crawl-mode-card-desc">{{ opt.desc }}</span>
    </button>
  </div>
</template>

<style scoped>
.crawl-mode-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.crawl-mode-card {
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
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.crawl-mode-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-blank);
}

.crawl-mode-card:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

.crawl-mode-card.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}

.crawl-mode-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.35;
}

.crawl-mode-card.is-active .crawl-mode-card-title {
  color: var(--el-color-primary);
}

.crawl-mode-card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}
</style>
