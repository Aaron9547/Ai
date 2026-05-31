<template>
  <div class="auth-page">
    <UserAuthDialog
      v-model="open"
      mandatory
      :post-auth-redirect="postAuthRedirect"
      @done="onDone"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import UserAuthDialog from "../../components/UserAuthDialog.vue";

const route = useRoute();
const router = useRouter();
const open = ref(true);

const postAuthRedirect = computed(() => {
  const raw = route.query.redirect;
  if (typeof raw !== "string" || !raw.startsWith("/")) {
    return undefined;
  }
  return raw;
});

async function onDone() {
  const target = postAuthRedirect.value;
  if (target) {
    await router.replace(target);
    return;
  }
  const tenantCode = String(route.params.tenantCode || import.meta.env.VITE_TENANT_CODE || "default");
  await router.replace(`/${tenantCode}/chat`);
}
</script>

<style scoped>
.auth-page {
  min-height: 100dvh;
  background: radial-gradient(circle at 50% 0%, rgba(59, 130, 246, 0.08), transparent 55%),
    linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}
</style>
