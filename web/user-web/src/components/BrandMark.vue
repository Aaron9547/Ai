<template>
  <img
    v-if="logoSrc"
    :src="logoSrc"
    class="brand-mark brand-mark--img"
    :class="sizeClass"
    alt=""
  />
  <span v-else class="brand-mark brand-mark--text" :class="sizeClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { resolvePublicAssetUrl } from "@/utils/publicAssetUrl";
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    logoUrl?: string | null;
    label: string;
    size?: "sidebar" | "hero" | "share" | "auth";
  }>(),
  { logoUrl: "", size: "sidebar" },
);

const logoSrc = computed(() => resolvePublicAssetUrl(props.logoUrl));

const sizeClass = computed(() => `brand-mark--${props.size}`);
</script>

<style scoped>
.brand-mark--text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: -0.02em;
  flex-shrink: 0;
  color: #fff;
}

.brand-mark--img {
  display: block;
  object-fit: contain;
  flex-shrink: 0;
}

.brand-mark--sidebar.brand-mark--text,
.brand-mark--sidebar.brand-mark--img {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 14px;
}

.brand-mark--sidebar.brand-mark--text {
  background: linear-gradient(145deg, #5b9fd4 0%, #3d7ab8 100%);
}

.brand-mark--hero.brand-mark--text,
.brand-mark--hero.brand-mark--img {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  font-size: 17px;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.22);
}

.brand-mark--hero.brand-mark--text {
  background: linear-gradient(
    145deg,
    var(--nexus-brand-600, #4f46e5) 0%,
    var(--nexus-violet-600, #7c3aed) 100%
  );
}

.brand-mark--share.brand-mark--text,
.brand-mark--share.brand-mark--img {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 15px;
}

.brand-mark--share.brand-mark--text {
  background: linear-gradient(145deg, #5b9fd4 0%, #3d7ab8 100%);
}

.brand-mark--auth.brand-mark--text,
.brand-mark--auth.brand-mark--img {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  margin: 0 auto 12px;
}

.brand-mark--auth.brand-mark--text {
  background: linear-gradient(
    145deg,
    var(--nexus-brand-600, #4f46e5) 0%,
    var(--nexus-violet-600, #7c3aed) 100%
  );
}
</style>
