<template>
  <a
    class="rec-card"
    :href="safeUrl"
    target="_blank"
    rel="noopener noreferrer"
    @click="onCardClick"
  >
    <div v-if="item.tag && item.tag !== '—'" class="rec-card-meta">
      <span class="rec-card-tag" :class="tagToneClass">{{ item.tag }}</span>
      <span v-if="metaDate" class="rec-card-date">{{ metaDate }}</span>
    </div>
    <h4 class="rec-card-title">{{ item.title }}</h4>
    <p class="rec-card-summary">{{ item.summary || " " }}</p>
    <p v-if="metaSource && !metaDate" class="rec-card-foot">{{ metaSource }}</p>
    <span class="rec-card-action" aria-hidden="true">
      <el-icon :size="14"><TopRight /></el-icon>
    </span>
    <span v-if="isRecommendToday" class="rec-card-badge">{{ t("dailyRecommend.todayBadge") }}</span>
  </a>
</template>

<script setup lang="ts">
import { TopRight } from "@element-plus/icons-vue";
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import type { DailyRecommendItem } from "../../api/dailyRecommend";
import { trackDailyRecommendClick } from "../../api/dailyRecommend";
import { isHttpExternalUrl, normalizeExternalUrl } from "../../utils/externalUrl";

const props = defineProps<{
  item: DailyRecommendItem;
  /** 服务端推荐批次日期（yyyy-MM-dd），用于「今日」角标 */
  recommendDate?: string;
}>();

const { t } = useI18n();
const clicked = ref(false);

const safeUrl = computed(() => {
  const u = normalizeExternalUrl(props.item.url);
  return isHttpExternalUrl(u) ? u : "#";
});

const metaDate = computed(() => {
  const d = props.item.date?.trim() ?? "";
  return d && d !== "—" ? d : "";
});

const isRecommendToday = computed(() => {
  const batch = props.recommendDate?.trim();
  if (!batch) {
    return false;
  }
  const d = metaDate.value;
  if (!d) {
    return false;
  }
  return d === batch;
});

const metaSource = computed(() => {
  const s = props.item.source?.trim() ?? "";
  return s && s !== "—" ? s : "";
});

const tagToneClass = computed(() => {
  const h = (props.item.tag ?? "").charCodeAt(0) % 5;
  return `rec-card-tag--tone-${h}`;
});

function onCardClick(ev: MouseEvent): void {
  if (safeUrl.value === "#") {
    ev.preventDefault();
    return;
  }
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
    url: safeUrl.value,
  });
  window.setTimeout(() => {
    clicked.value = false;
  }, 1200);
}
</script>

<style scoped>
.rec-card {
  position: relative;
  display: block;
  padding: 16px;
  border-radius: 16px;
  background: var(--rec-card-bg, #ffffff);
  border: 1px solid var(--rec-card-border, rgba(0, 0, 0, 0.06));
  text-decoration: none;
  color: inherit;
  transition:
    border-color 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    box-shadow 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    transform 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    background-color 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1));
}

.rec-card:hover {
  border-color: var(--rec-card-hover-border, rgba(79, 70, 229, 0.22));
  background: rgba(79, 70, 229, 0.03);
  box-shadow: var(--nexus-shadow-card, 0 10px 15px -3px rgba(0, 0, 0, 0.04));
  transform: translateY(-2px);
}

.rec-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.rec-card-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.4;
}

.rec-card-tag--tone-0 {
  color: #4338ca;
  background: #eef2ff;
}
.rec-card-tag--tone-1 {
  color: #475569;
  background: #f1f5f9;
}
.rec-card-tag--tone-2 {
  color: #047857;
  background: #ecfdf5;
}
.rec-card-tag--tone-3 {
  color: #b45309;
  background: #fffbeb;
}
.rec-card-tag--tone-4 {
  color: #6d28d9;
  background: #f5f3ff;
}

.rec-card-date {
  font-size: 9px;
  color: var(--rec-card-foot, #94a3b8);
}

.rec-card-title {
  margin: 0 0 8px;
  padding-right: 20px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.55;
  color: var(--rec-card-title, #0f172a);
  transition: color 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1));
}

.rec-card:hover .rec-card-title {
  color: var(--nexus-brand-600, #4f46e5);
}

.rec-card-summary {
  margin: 0;
  font-size: 10px;
  line-height: 1.6;
  color: var(--rec-card-summary, #64748b);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  opacity: 0;
  max-height: 0;
  transition:
    opacity 0.25s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    max-height 0.25s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    margin 0.25s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1));
}

.rec-card:hover .rec-card-summary {
  opacity: 1;
  max-height: 4em;
  margin-top: 4px;
}

.rec-card-foot {
  margin: 8px 0 0;
  font-size: 10px;
  line-height: 1.4;
  color: var(--rec-card-foot, #94a3b8);
}

.rec-card-action {
  position: absolute;
  top: 14px;
  right: 12px;
  color: var(--rec-card-foot, #94a3b8);
  opacity: 0;
  transform: translateY(2px);
  transition:
    opacity 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    transform 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1)),
    color 0.2s var(--nexus-ease, cubic-bezier(0.4, 0, 0.2, 1));
}

.rec-card:hover .rec-card-action {
  opacity: 1;
  transform: translateY(0);
  color: var(--nexus-brand-600, #4f46e5);
}

.rec-card-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  font-size: 9px;
  font-weight: 700;
  color: var(--rec-card-badge-text, #4f46e5);
  background: var(--rec-card-badge-bg, #eef2ff);
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 0.04em;
}

:global(html.dark) .rec-card-tag--tone-0 {
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.15);
}
:global(html.dark) .rec-card-tag--tone-1 {
  color: #cbd5e1;
  background: rgba(255, 255, 255, 0.08);
}
:global(html.dark) .rec-card-tag--tone-2 {
  color: #86efac;
  background: rgba(16, 185, 129, 0.12);
}
:global(html.dark) .rec-card-tag--tone-3 {
  color: #fcd34d;
  background: rgba(245, 158, 11, 0.12);
}
:global(html.dark) .rec-card-tag--tone-4 {
  color: #c4b5fd;
  background: rgba(124, 58, 237, 0.15);
}
</style>
