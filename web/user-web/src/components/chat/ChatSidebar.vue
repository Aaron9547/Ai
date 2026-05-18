<template>
  <aside class="sidebar" :class="{ 'sidebar--drawer-open': drawerOpen }">
    <div class="sidebar-head">
      <button type="button" class="btn-new" @click="emit('newConv')">
        <el-icon><Plus /></el-icon>
        {{ t("chat.newChat") }}
      </button>
    </div>
    <el-scrollbar class="conv-scroll">
      <ul class="conv-list">
        <li
          v-for="c in convs"
          :key="c.id"
          :class="['conv-item', { active: c.id === convId }]"
          @click="emit('select', c.id)"
        >
          <span class="conv-title">{{ c.title }}</span>
        </li>
      </ul>
    </el-scrollbar>
    <div class="sidebar-foot">
      <div class="foot-auth">
        <template v-if="loggedInUsername">
          <span class="foot-user" :title="loggedInUsername">{{ loggedInUsername }}</span>
          <button type="button" class="foot-btn" @click="emit('logout')">{{ t("chat.logout") }}</button>
        </template>
        <button v-else type="button" class="foot-btn foot-btn--primary" @click="emit('login')">
          {{ t("chat.loginRegister") }}
        </button>
      </div>
      <RouterLink class="foot-link" :to="mePagePath">
        <el-icon><Cpu /></el-icon>
        {{ t("chat.deviceMe") }}
      </RouterLink>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Cpu, Plus } from "@element-plus/icons-vue";
import { useI18n } from "vue-i18n";
export type ConvListItem = { id: number; title: string };

defineProps<{
  convs: ConvListItem[];
  convId: number | null;
  loggedInUsername: string | null;
  mePagePath: string;
  drawerOpen?: boolean;
}>();

const emit = defineEmits<{
  select: [id: number];
  newConv: [];
  logout: [];
  login: [];
}>();

const { t } = useI18n();
</script>

<style scoped>
.sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--chat-bg-subtle, #f9f9f9);
  border-right: 1px solid var(--chat-border, #ececec);
}

.sidebar-head {
  padding: 12px 10px 10px;
}

.btn-new {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--chat-border-subtle, #e8e8e8);
  background: var(--chat-bg-elevated, #fff);
  font-size: 14px;
  font-weight: 500;
  color: var(--chat-text-primary, #202020);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.btn-new:hover {
  background: #f3f3f3;
  border-color: #d9d9d9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.conv-scroll {
  flex: 1;
  min-height: 0;
}

.conv-list {
  list-style: none;
  margin: 0;
  padding: 4px 8px 12px;
}

.conv-item {
  padding: 10px 12px 10px 9px;
  margin-bottom: 2px;
  border-radius: 8px;
  border-left: 3px solid transparent;
  font-size: 14px;
  color: #3f3f46;
  cursor: pointer;
  line-height: 1.35;
  transition: background 0.12s;
}

.conv-item:hover {
  background: #e4e4e7;
}

.conv-item.active {
  background: #ececec;
  font-weight: 500;
  border-left-color: #202020;
}

.conv-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sidebar-foot {
  padding: 10px 12px 14px;
  border-top: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.foot-auth {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.foot-user {
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 2px;
}

.foot-btn {
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  cursor: pointer;
  text-align: center;
}

.foot-btn--primary {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}

.foot-btn:hover {
  border-color: #cbd5e1;
}

.foot-link {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #52525b;
  text-decoration: none;
  padding: 8px 10px;
  border-radius: 8px;
}

.foot-link:hover {
  background: #e4e4e7;
  color: #18181b;
}
</style>
