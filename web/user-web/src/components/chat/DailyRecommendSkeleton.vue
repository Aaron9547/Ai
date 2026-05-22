<template>
  <div class="rec-skel-wrap" role="status" :aria-label="t('dailyRecommend.loadingAria')">
    <article v-for="n in count" :key="n" class="rec-skel-card">
      <div class="rec-skel-tag skeleton-shimmer" />
      <div class="rec-skel-title skeleton-shimmer" />
      <div class="rec-skel-line skeleton-shimmer" />
      <div class="rec-skel-line rec-skel-line--short skeleton-shimmer" />
      <div class="rec-skel-foot skeleton-shimmer" />
    </article>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from "vue-i18n";

withDefaults(defineProps<{ count?: number }>(), { count: 3 });

const { t } = useI18n();
</script>

<style scoped>
.rec-skel-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px 8px;
}

.rec-skel-card {
  padding: 14px 14px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #eef1f5;
  min-height: 118px;
  box-sizing: border-box;
}

.rec-skel-tag {
  width: 52px;
  height: 18px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.rec-skel-title {
  width: 88%;
  height: 16px;
  border-radius: 4px;
  margin-bottom: 10px;
}

.rec-skel-line {
  width: 100%;
  height: 12px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.rec-skel-line--short {
  width: 70%;
  margin-bottom: 10px;
}

.rec-skel-foot {
  width: 48%;
  height: 11px;
  border-radius: 4px;
}

.skeleton-shimmer {
  background: linear-gradient(90deg, #f2f4f7 22%, #e6eaef 50%, #f2f4f7 78%);
  background-size: 200% 100%;
  animation: rec-skel-flow 1.45s ease-in-out infinite;
}

@keyframes rec-skel-flow {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
