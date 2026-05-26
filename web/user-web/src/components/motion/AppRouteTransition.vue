<template>
  <RouterView v-slot="{ Component, route: viewRoute }">
    <Transition name="route-fade" mode="out-in">
      <component :is="Component" :key="viewRoute.path" />
    </Transition>
  </RouterView>
</template>

<script setup lang="ts"></script>
