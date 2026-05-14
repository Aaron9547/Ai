<template>
  <div class="kc-page">
    <el-breadcrumb class="kc-crumb" separator="/">
      <el-breadcrumb-item v-for="(c, i) in crumbs" :key="i">
        <router-link v-if="c.to != null" :to="c.to" class="kc-crumb-link">{{ c.label }}</router-link>
        <span v-else>{{ c.label }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
    <div class="kc-router-body">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import type { RouteLocationRaw } from "vue-router";

type Crumb = { label: string; to?: RouteLocationRaw };

const route = useRoute();
const { t } = useI18n();
const hubPath = "/knowledge-center/knowledge-bases";

const crumbs = computed((): Crumb[] => {
  const p = route.path;
  const head: Crumb[] = [{ label: t("views.kcLayout.hub"), to: hubPath }];

  if (p === hubPath) {
    return [...head, { label: t("views.kcLayout.kb") }];
  }

  const chunks = /^\/knowledge-center\/workspace\/(\d+)\/documents\/(\d+)\/chunks$/.exec(p);
  if (chunks) {
    const kbId = chunks[1]!;
    return [
      ...head,
      { label: t("views.kcLayout.docsIngest"), to: { path: hubPath, query: { kbId } } },
      { label: t("views.kcLayout.chunks") },
    ];
  }

  return head;
});
</script>

<style scoped>
.kc-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.kc-crumb {
  flex-shrink: 0;
  margin-bottom: 14px;
  font-size: 13px;
}

.kc-crumb-link {
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}

.kc-crumb-link:hover {
  text-decoration: underline;
}

.kc-router-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
