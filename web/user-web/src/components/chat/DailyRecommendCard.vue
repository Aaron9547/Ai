<template>
  <a
    class="rec-card"
    :href="safeUrl"
    target="_blank"
    rel="noopener noreferrer"
    @click="onCardClick"
  >
    <span
      v-if="item.tag && item.tag !== '—'"
      class="rec-card-tag"
      :class="tagToneClass"
    >{{ item.tag }}</span>
    <h4 class="rec-card-title">{{ item.title }}</h4>
    <p class="rec-card-summary">{{ item.summary || " " }}</p>
    <p class="rec-card-foot">{{ metaLine }}</p>
    <span v-if="showTodayBadge" class="rec-card-badge">{{ t("dailyRecommend.todayBadge") }}</span>
  </a>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import type { DailyRecommendItem } from "../../api/dailyRecommend";
import { trackDailyRecommendClick } from "../../api/dailyRecommend";

const props = withDefaults(
  defineProps<{ item: DailyRecommendItem; showTodayBadge?: boolean }>(),
  { showTodayBadge: true },
);

const { t } = useI18n();
const clicked = ref(false);

const safeUrl = computed(() => {
  const u = props.item.url?.trim() ?? "";
  if (u.startsWith("http://") || u.startsWith("https://")) return u;
  return "#";
});

const metaLine = computed(() => {
  const parts: string[] = [];
  if (props.item.source && props.item.source !== "—") parts.push(props.item.source);
  if (props.item.date && props.item.date !== "—") parts.push(props.item.date);
  return parts.length ? parts.join(" · ") : t("dailyRecommend.metaFallback");
});

const tagToneClass = computed(() => {
  const h = (props.item.tag ?? "").charCodeAt(0) % 5;
  return `rec-card-tag--tone-${h}`;
});

function onCardClick(ev: MouseEvent): void {
  if (clicked.value) {
    ev.preventDefault();
    return;
  }
  clicked.value = true;
  trackDailyRecommendClick({
    itemId: props.item.id,
    tag: props.item.tag,
    title: props.item.title,
    summary: props.item.summary,
    source: props.item.source,
    date: props.item.date,
    url: props.item.url,
  });
  if (safeUrl.value === "#") ev.preventDefault();
  window.setTimeout(() => {
    clicked.value = false;
  }, 1200);
}
</script>

<style scoped>
.rec-card {
  position: relative;
  display: block;
  padding: 14px 14px 12px;
  border-radius: 12px;
  background: var(--rec-card-bg, #ffffff);
  border: 1px solid var(--rec-card-border, #e9edf2);
  text-decoration: none;
  color: inherit;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.rec-card:hover {
  border-color: var(--rec-card-hover-border, #c5d9eb);
  box-shadow: 0 8px 20px rgba(61, 122, 184, 0.12);
  transform: translateY(-3px);
}

.rec-card-tag {
  display: inline-block;
  margin-bottom: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.rec-card-tag--tone-0 {
  color: #2f6f9e;
  background: #e8f3fb;
}
.rec-card-tag--tone-1 {
  color: #5a6b8a;
  background: #eef1f6;
}
.rec-card-tag--tone-2 {
  color: #3d7a6a;
  background: #e8f5f0;
}
.rec-card-tag--tone-3 {
  color: #8a6b3d;
  background: #f7f0e6;
}
.rec-card-tag--tone-4 {
  color: #6b5a8a;
  background: #f0ecf7;
}

.rec-card-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--rec-card-title, #2c3e50);
}

.rec-card-summary {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--rec-card-summary, #7a8794);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
}

.rec-card-foot {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--rec-card-foot, #a0adb8);
}

.rec-card-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  font-size: 10px;
  font-weight: 600;
  color: var(--rec-card-badge-text, #6b8aa8);
  background: var(--rec-card-badge-bg, #f0f6fb);
  padding: 2px 6px;
  border-radius: 3px;
  letter-spacing: 0.02em;
}
</style>
