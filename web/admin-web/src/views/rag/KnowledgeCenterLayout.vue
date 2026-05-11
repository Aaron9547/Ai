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
import { useRoute } from "vue-router";
import type { RouteLocationRaw } from "vue-router";

type Crumb = { label: string; to?: RouteLocationRaw };

const route = useRoute();
const hubPath = "/knowledge-center/knowledge-bases";

const crumbs = computed((): Crumb[] => {
  const p = route.path;
  const head: Crumb[] = [{ label: "知识中心", to: hubPath }];

  if (p === hubPath) {
    return [...head, { label: "知识库" }];
  }

  const chunks = /^\/knowledge-center\/workspace\/(\d+)\/documents\/(\d+)\/chunks$/.exec(p);
  if (chunks) {
    const kbId = chunks[1]!;
    return [...head, { label: "文档与入库", to: { path: hubPath, query: { kbId } } }, { label: "分片管理" }];
  }

  return head;
});
</script>

<style scoped>
.kc-page {
  min-height: 0;
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
