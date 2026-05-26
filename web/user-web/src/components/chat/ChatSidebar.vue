<template>
  <aside
    class="sidebar"
    :class="{
      'sidebar--collapsed': showCollapsed,
      'sidebar--drawer-open': drawerOpen,
    }"
  >
    <div class="sidebar-clip">
      <div class="sidebar-inner">
      <div class="sidebar-body" :aria-hidden="showCollapsed">
      <div class="sidebar-top">
        <div class="brand-logo" aria-hidden="true">{{ brandLabel }}</div>
        <div class="sidebar-top-actions">
          <button type="button" class="btn-new" :aria-label="t('chat.ariaNewChat')" @click="emit('newConv')">
            <el-icon><Plus /></el-icon>
            <span class="btn-new-text">{{ t("chat.newChat") }}</span>
          </button>
        </div>
      </div>
      <div class="sidebar-divider" aria-hidden="true" />

      <el-scrollbar class="conv-scroll">
        <div v-if="!convs.length" class="conv-empty">
          <el-icon class="conv-empty-icon" :size="40"><ChatLineRound /></el-icon>
          <p class="conv-empty-text">{{ t("chat.convEmpty") }}</p>
        </div>
        <div v-else class="conv-groups">
          <section v-for="group in convGroups" :key="group.key" class="conv-group">
            <h3 class="conv-group-label">{{ group.label }}</h3>
            <ul class="conv-list">
              <li
                v-for="c in group.items"
                :key="c.id"
                :class="['conv-item', { active: c.id === convId }]"
                @click="emit('select', c.id)"
              >
                <span class="conv-accent" aria-hidden="true" />
                <div class="conv-body">
                  <span class="conv-title">{{ c.title }}</span>
                </div>
                <div class="conv-actions" @click.stop>
                  <button
                    type="button"
                    class="conv-act-btn"
                    :title="t('chat.renameConv')"
                    :aria-label="t('chat.renameConv')"
                    @click="emit('rename', c.id)"
                  >
                    <el-icon :size="14"><EditPen /></el-icon>
                  </button>
                  <button
                    type="button"
                    class="conv-act-btn conv-act-btn--danger"
                    :title="t('chat.deleteConv')"
                    :aria-label="t('chat.deleteConv')"
                    @click="emit('delete', c.id)"
                  >
                    <el-icon :size="14"><Delete /></el-icon>
                  </button>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </el-scrollbar>

      <div class="sidebar-foot">
        <div class="foot-row">
          <el-dropdown
            v-if="loggedInUsername"
            class="foot-dropdown"
            trigger="click"
            teleported
            placement="top-start"
            popper-class="sidebar-user-menu-popper"
            @command="onUserMenuCommand"
          >
            <button
              type="button"
              class="foot-entry foot-entry--user"
              :title="loggedInUsername"
              :aria-label="t('chat.userMenuAria')"
            >
              <el-icon :size="18"><User /></el-icon>
              <span class="foot-entry-label">{{ loggedInUsername }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="account" class="foot-menu-item">
                  <el-icon :size="16"><Setting /></el-icon>
                  <span>{{ t("chat.deviceMe") }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided class="foot-menu-item foot-menu-item--danger">
                  <el-icon :size="16"><SwitchButton /></el-icon>
                  <span>{{ t("chat.logout") }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <button v-else type="button" class="foot-entry foot-entry--accent" @click="emit('login')">
            <el-icon :size="18"><User /></el-icon>
            <span class="foot-entry-label">{{ t("chat.loginRegister") }}</span>
          </button>
          <RouterLink
            v-if="!loggedInUsername"
            class="foot-entry"
            :to="mePagePath"
            :title="t('chat.deviceMe')"
          >
            <el-icon :size="18"><Setting /></el-icon>
            <span class="foot-entry-label">{{ t("chat.settingsShort") }}</span>
          </RouterLink>
        </div>
      </div>
      </div>

      <div class="sidebar-rail-layer" :aria-hidden="!showCollapsed">
        <div class="sidebar-collapsed">
          <button
            type="button"
            class="sidebar-fab"
            :aria-label="t('chat.ariaNewChat')"
            @click="emit('newConv')"
          >
            <el-icon :size="18"><Plus /></el-icon>
          </button>
          <div class="sidebar-collapsed-gap" aria-hidden="true" />
          <button
            type="button"
            class="sidebar-rail"
            :aria-label="t('chat.expandConvs')"
            @click="collapsed = false"
          >
            <el-icon :size="20"><ChatLineRound /></el-icon>
          </button>
        </div>
      </div>
    </div>
    </div>

    <SidebarCollapseTab
      v-if="collapsible && !showCollapsed"
      side="left"
      :label="t('chat.collapseConvs')"
      @click="collapsed = true"
    />
  </aside>
</template>

<script setup lang="ts">
import {
  ChatLineRound,
  Delete,
  EditPen,
  Plus,
  Setting,
  SwitchButton,
  User,
} from "@element-plus/icons-vue";
import SidebarCollapseTab from "./SidebarCollapseTab.vue";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";

export type ConvListItem = { id: number; title: string; updatedAt?: string | null };

type ConvGroup = { key: string; label: string; items: ConvListItem[] };

const props = withDefaults(
  defineProps<{
    convs: ConvListItem[];
    convId: number | null;
    loggedInUsername: string | null;
    mePagePath: string;
    drawerOpen?: boolean;
    brandLabel?: string;
    collapsible?: boolean;
    defaultCollapsed?: boolean;
  }>(),
  { brandLabel: "Ai", collapsible: true, defaultCollapsed: false },
);

const emit = defineEmits<{
  select: [id: number];
  newConv: [];
  logout: [];
  login: [];
  rename: [id: number];
  delete: [id: number];
}>();

const { t, locale } = useI18n();
const router = useRouter();
const collapsed = ref(props.defaultCollapsed);

function onUserMenuCommand(command: string | number): void {
  if (command === "account") {
    void router.push(props.mePagePath);
    return;
  }
  if (command === "logout") {
    emit("logout");
  }
}

const showCollapsed = computed(
  () => props.collapsible && collapsed.value && !props.drawerOpen,
);

watch(
  () => props.defaultCollapsed,
  (v) => {
    collapsed.value = v;
  },
);

const GROUP_ORDER = ["today", "yesterday"] as const;

function dateGroupKey(updatedAt: string | null | undefined): string {
  if (!updatedAt) return "unknown";
  const d = new Date(updatedAt);
  if (Number.isNaN(d.getTime())) return "unknown";
  const now = new Date();
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startD = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diffDays = Math.round((startToday - startD) / 86400000);
  if (diffDays === 0) return "today";
  if (diffDays === 1) return "yesterday";
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `d:${y}-${m}-${day}`;
}

function groupLabel(key: string): string {
  if (key === "today") return t("chat.convGroupToday");
  if (key === "yesterday") return t("chat.convGroupYesterday");
  if (key === "unknown") return t("chat.convGroupEarlier");
  const m = key.match(/^d:(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return t("chat.convGroupEarlier");
  const loc = locale.value.startsWith("en") ? "en-US" : "zh-CN";
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.toLocaleDateString(loc, { month: "long", day: "numeric", year: "numeric" });
}

function sortGroupKeys(keys: string[]): string[] {
  const fixed = keys
    .filter((k) => (GROUP_ORDER as readonly string[]).includes(k))
    .sort(
      (a, b) =>
        GROUP_ORDER.indexOf(a as (typeof GROUP_ORDER)[number]) -
        GROUP_ORDER.indexOf(b as (typeof GROUP_ORDER)[number]),
    );
  const dates = keys.filter((k) => k.startsWith("d:")).sort((a, b) => b.localeCompare(a));
  const unknown = keys.filter((k) => k === "unknown");
  return [...fixed, ...dates, ...unknown];
}

const convGroups = computed<ConvGroup[]>(() => {
  const map = new Map<string, ConvListItem[]>();
  const order: string[] = [];
  for (const c of props.convs) {
    const key = dateGroupKey(c.updatedAt);
    if (!map.has(key)) {
      map.set(key, []);
      order.push(key);
    }
    map.get(key)!.push(c);
  }
  return sortGroupKeys(order).map((key) => ({
    key,
    label: groupLabel(key),
    items: map.get(key) ?? [],
  }));
});
</script>

<style scoped>
.sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: visible;
  background: #fafafa;
  border-right: 1px solid var(--chat-border, rgba(0, 0, 0, 0.06));
}

.sidebar--collapsed {
  background: var(--chat-bg-subtle, #f5f8fc);
}

.sidebar-inner {
  position: relative;
  height: 100%;
  min-height: 0;
  flex: 1;
}

.sidebar-body {
  display: flex;
  flex-direction: column;
}

.sidebar-rail-layer {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 40px;
  display: flex;
  flex-direction: column;
}

.sidebar-collapsed {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 10px;
  box-sizing: border-box;
}

.sidebar-fab {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border: 1px solid var(--chat-border-subtle, #dce4ec);
  border-radius: 10px;
  background: var(--chat-bg-elevated, #fff);
  color: var(--chat-accent, #5b9fd4);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(91, 159, 212, 0.1);
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.sidebar-fab:hover {
  background: var(--sidebar-fab-hover-bg, rgba(91, 159, 212, 0.1));
  border-color: var(--sidebar-fab-hover-border, #c5d9eb);
  color: var(--sidebar-rail-hover-color, #3d7ab8);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(91, 159, 212, 0.16);
}

.sidebar-collapsed-gap {
  flex-shrink: 0;
  width: 100%;
  height: 10px;
}

.sidebar-rail {
  flex: 1;
  width: 100%;
  min-height: 44px;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--sidebar-rail-color, #5a7a94);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.sidebar-rail:hover {
  background: var(--sidebar-rail-hover-bg, rgba(79, 70, 229, 0.06));
  color: var(--sidebar-rail-hover-color, var(--nexus-brand-600, #4f46e5));
}

.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 12px 10px;
}

.sidebar-top-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(145deg, #5b9fd4 0%, #3d7ab8 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -0.02em;
  flex-shrink: 0;
}

.btn-new {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid var(--chat-border-subtle, #dce4ec);
  background: var(--chat-bg-elevated, #fff);
  font-size: 12px;
  font-weight: 500;
  color: var(--chat-text-primary, #2c3e50);
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.btn-new:hover {
  background: var(--sidebar-btn-hover-bg, rgba(79, 70, 229, 0.06));
  border-color: var(--sidebar-btn-hover-border, rgba(99, 102, 241, 0.22));
}

.btn-new-text {
  max-width: 4.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-divider {
  height: 1px;
  margin: 0 12px 8px;
  background: var(--chat-border, #e8edf2);
}

.conv-scroll {
  flex: 1;
  min-height: 0;
}

.conv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  text-align: center;
  gap: 12px;
}

.conv-empty-icon {
  color: #c5d4e0;
}

.conv-empty-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #9aa8b5;
}

.conv-groups {
  padding: 4px 8px 12px;
}

.conv-group + .conv-group {
  margin-top: 12px;
}

.conv-group-label {
  margin: 0 0 6px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--chat-text-muted, #a0adb8);
  letter-spacing: 0.02em;
}

.conv-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: stretch;
  min-height: 44px;
  margin-bottom: 2px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.conv-item:hover {
  background: var(--chat-conv-hover, rgba(91, 159, 212, 0.08));
}

.conv-item.active {
  background: var(--chat-conv-active-bg, rgba(91, 159, 212, 0.12));
}

.conv-accent {
  flex-shrink: 0;
  width: 3px;
  margin: 8px 0 8px 4px;
  border-radius: 2px;
  background: transparent;
  transition: background 0.15s ease;
}

.conv-item.active .conv-accent {
  background: var(--chat-conv-accent, #5b9fd4);
}

.conv-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 10px 12px 10px 8px;
  transition: padding-right 0.15s ease;
}

.conv-item:hover .conv-body,
.conv-item:focus-within .conv-body {
  padding-right: 56px;
}

.conv-title {
  width: 100%;
  font-size: 14px;
  line-height: 1.3;
  color: var(--chat-text-muted, #5a6b7a);
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-item.active .conv-title {
  font-weight: 600;
  color: var(--chat-text-primary, #2c3e50);
}

.conv-actions {
  position: absolute;
  right: 6px;
  top: 50%;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%) translateX(6px);
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.conv-item:hover .conv-actions,
.conv-item:focus-within .conv-actions {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(-50%) translateX(0);
}

.conv-act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: #7a8794;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(30, 60, 90, 0.08);
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.conv-act-btn:hover {
  color: #3d6f94;
}

.conv-act-btn--danger:hover {
  color: #c45656;
  background: #fef2f2;
}

.sidebar-foot {
  flex-shrink: 0;
  min-width: 0;
  padding: 10px 8px 14px;
  border-top: 1px solid var(--chat-border, #e8edf2);
  overflow: hidden;
}

.foot-row {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-width: 0;
}

.foot-dropdown {
  flex: 1;
  min-width: 0;
  max-width: 100%;
}

.foot-dropdown :deep(.el-dropdown) {
  display: block;
  width: 100%;
  max-width: 100%;
}

.foot-dropdown .foot-entry {
  flex: none;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.foot-entry {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #6b7c8a;
  font-size: 11px;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.foot-entry:hover {
  background: var(--sidebar-rail-hover-bg, rgba(91, 159, 212, 0.1));
  color: var(--sidebar-rail-hover-color, #3d6f94);
}

.foot-entry--accent {
  color: #3d7ab8;
}

.foot-entry-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}
</style>
