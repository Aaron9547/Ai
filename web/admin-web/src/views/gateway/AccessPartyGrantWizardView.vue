<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft } from "@element-plus/icons-vue";
import {
  fetchGrantWizard,
  replaceGrants,
  type AccessPartyRow,
  type GrantWizardModuleStep,
} from "@/api/gatewayAccessParty";
import type { ApiEndpointRow } from "@/api/gatewayApiEndpoints";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();

const partyId = computed(() => Number(route.params.partyId));
const loading = ref(true);
const submitting = ref(false);
const party = ref<AccessPartyRow | null>(null);
const modules = ref<GrantWizardModuleStep[]>([]);
const stepIndex = ref(0);
const defaultRpm = ref(60);
const selections = reactive<Record<number, number[]>>({});
const grantRpmByEndpoint = reactive<Record<number, number>>({});

const currentStep = computed(() => modules.value[stepIndex.value] ?? null);
const isLastStep = computed(() => stepIndex.value >= modules.value.length - 1);

const groupedEndpoints = computed(() => {
  const step = currentStep.value;
  if (!step) return [] as { group: string; items: ApiEndpointRow[] }[];
  const map = new Map<string, ApiEndpointRow[]>();
  for (const ep of step.endpoints) {
    const group = ep.remark?.trim() || t("views.gatewayAccessParty.grantWizardUncategorized");
    if (!map.has(group)) map.set(group, []);
    map.get(group)!.push(ep);
  }
  return Array.from(map.entries()).map(([group, items]) => ({ group, items }));
});

const currentModuleId = computed(() => currentStep.value?.module.id ?? null);

const currentEndpoints = computed(() => currentStep.value?.endpoints ?? []);

const selectedInStep = computed(() => {
  const mod = currentModuleId.value;
  if (!mod) return [] as number[];
  return selections[mod] ?? [];
});

const selectedCount = computed(() => selectedInStep.value.length);
const totalCount = computed(() => currentEndpoints.value.length);

const rpmCap = computed(() => party.value?.totalRpmCap ?? 0);
const hasRpmCap = computed(() => rpmCap.value > 0);

const totalGrantedRpm = computed(() => {
  let sum = 0;
  for (const step of modules.value) {
    for (const epId of selections[step.module.id] ?? []) {
      sum += grantRpmByEndpoint[epId] ?? defaultRpm.value;
    }
  }
  return sum;
});

const rpmOverCap = computed(() => hasRpmCap.value && totalGrantedRpm.value > rpmCap.value);

function currentSelection(): number[] {
  return selectedInStep.value;
}

function isStepDone(index: number) {
  return index < stepIndex.value;
}

function toggleEndpoint(epId: number, checked: boolean) {
  const mod = currentModuleId.value;
  if (!mod) return;
  const set = new Set(selections[mod] ?? []);
  if (checked) set.add(epId);
  else set.delete(epId);
  selections[mod] = Array.from(set);
  if (checked && grantRpmByEndpoint[epId] == null) {
    grantRpmByEndpoint[epId] = defaultRpm.value;
  }
}

function selectAllInGroup(items: ApiEndpointRow[], checked: boolean) {
  for (const ep of items) toggleEndpoint(ep.id, checked);
}

function selectAllCurrent(checked: boolean) {
  for (const ep of currentEndpoints.value) toggleEndpoint(ep.id, checked);
}

function groupAllSelected(items: ApiEndpointRow[]) {
  return items.length > 0 && items.every((e) => selectedInStep.value.includes(e.id));
}

async function load() {
  loading.value = true;
  try {
    const data = await fetchGrantWizard(partyId.value);
    party.value = data.party;
    modules.value = data.modules;
    for (const step of data.modules) {
      const modId = step.module.id;
      const epSet = new Set(step.endpoints.map((e) => e.id));
      selections[modId] = data.grants
        .filter((g) => epSet.has(g.grant.endpointId) && g.grant.enabled === "ON")
        .map((g) => g.grant.endpointId);
      for (const g of data.grants) {
        if (epSet.has(g.grant.endpointId)) {
          grantRpmByEndpoint[g.grant.endpointId] = g.grant.grantedRpm;
        }
      }
    }
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push("/gateway/access-parties");
}

function prevStep() {
  if (stepIndex.value > 0) stepIndex.value -= 1;
}

async function nextOrSubmit() {
  if (!isLastStep.value) {
    stepIndex.value += 1;
    return;
  }
  if (rpmOverCap.value) {
    ElMessage.warning(
      t("views.gatewayAccessParty.grantWizardRpmExceedsCap", {
        allocated: totalGrantedRpm.value,
        cap: rpmCap.value,
      }),
    );
    return;
  }
  submitting.value = true;
  try {
    const items: { endpointId: number; moduleId?: number; grantedRpm: number; enabled: "ON" | "OFF" }[] = [];
    for (const step of modules.value) {
      const ids = selections[step.module.id] ?? [];
      for (const epId of ids) {
        items.push({
          endpointId: epId,
          moduleId: step.module.id,
          grantedRpm: grantRpmByEndpoint[epId] ?? defaultRpm.value,
          enabled: "ON",
        });
      }
    }
    await replaceGrants(partyId.value, items);
    ElMessage.success(t("views.gatewayAccessParty.grantsSaved"));
    goBack();
  } catch (e) {
    ElMessage.error(
      apiRequestErrorMessage(e, t("views.gatewayAccessParty.saveFailed"), locale.value),
    );
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading" class="page grant-wizard">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <div class="hdr-main">
            <el-button class="back-btn" text :icon="ArrowLeft" @click="goBack">
              {{ t("views.gatewayAccessParty.grantWizardBack") }}
            </el-button>
            <div v-if="party" class="hdr-title-block">
              <span class="title">{{ t("views.gatewayAccessParty.bindGrant") }} · {{ party.displayName }}</span>
              <p class="hdr-sub">
                <span>App Id {{ party.appId }}</span>
                <span class="dot">·</span>
                <span>{{ t("views.gatewayAccessParty.colTotalRpm") }} {{ party.totalRpmCap || "∞" }}</span>
                <span class="dot">·</span>
                <span class="rpm-quota" :class="{ over: rpmOverCap }">
                  {{
                    hasRpmCap
                      ? t("views.gatewayAccessParty.grantWizardRpmQuota", {
                          allocated: totalGrantedRpm,
                          cap: rpmCap,
                        })
                      : t("views.gatewayAccessParty.grantWizardRpmUnlimited", {
                          allocated: totalGrantedRpm,
                        })
                  }}
                </span>
              </p>
            </div>
          </div>
        </div>
      </template>

      <el-steps
        v-if="modules.length"
        :active="stepIndex"
        finish-status="success"
        align-center
        class="steps"
      >
        <el-step
          v-for="(step, index) in modules"
          :key="step.module.id"
          :title="step.module.displayName"
          :status="isStepDone(index) ? 'success' : undefined"
        />
      </el-steps>

      <Transition name="fade" mode="out-in">
        <div v-if="currentStep" :key="currentStep.module.id" class="step-body">
          <div class="toolbar">
            <span class="toolbar-count">
              {{ t("views.gatewayAccessParty.grantWizardSelected", { n: selectedCount, total: totalCount }) }}
            </span>
            <div class="toolbar-actions">
              <el-checkbox
                :model-value="totalCount > 0 && selectedCount === totalCount"
                :indeterminate="selectedCount > 0 && selectedCount < totalCount"
                @change="(v: boolean) => selectAllCurrent(v)"
              >
                {{ t("views.gatewayAccessParty.grantWizardSelectAll") }}
              </el-checkbox>
              <span class="toolbar-divider" />
              <span class="rpm-label">{{ t("views.gatewayAccessParty.defaultRpm") }}</span>
              <el-input-number
                v-model="defaultRpm"
                :min="0"
                :step="10"
                size="small"
                controls-position="right"
                class="rpm-input"
              />
            </div>
          </div>

          <el-empty
            v-if="!currentStep.endpoints.length"
            :description="t('views.gatewayAccessParty.grantWizardEmptyModule')"
            :image-size="72"
          />

          <div v-else class="groups">
            <section v-for="grp in groupedEndpoints" :key="grp.group" class="group">
              <header class="group-hdr">
                <span class="group-name">{{ grp.group }}</span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="selectAllInGroup(grp.items, !groupAllSelected(grp.items))"
                >
                  {{
                    groupAllSelected(grp.items)
                      ? t("views.gatewayAccessParty.grantWizardClearGroup")
                      : t("views.gatewayAccessParty.grantWizardSelectGroup")
                  }}
                </el-button>
              </header>
              <ul class="ep-list">
                <li
                  v-for="ep in grp.items"
                  :key="ep.id"
                  class="ep-item"
                  :class="{ selected: currentSelection().includes(ep.id) }"
                >
                  <el-checkbox
                    :model-value="currentSelection().includes(ep.id)"
                    @change="(v: boolean) => toggleEndpoint(ep.id, v)"
                  />
                  <div class="ep-main" @click="toggleEndpoint(ep.id, !currentSelection().includes(ep.id))">
                    <span class="ep-name">{{ ep.displayName }}</span>
                    <span class="ep-path">{{ ep.httpMethod }} {{ ep.pathPattern }}</span>
                  </div>
                  <el-input-number
                    v-if="currentSelection().includes(ep.id)"
                    v-model="grantRpmByEndpoint[ep.id]"
                    :min="0"
                    :step="10"
                    size="small"
                    controls-position="right"
                    class="ep-rpm"
                    @click.stop
                  />
                </li>
              </ul>
            </section>
          </div>
        </div>
      </Transition>

      <div class="footer">
        <el-button @click="goBack">{{ t("common.cancel") }}</el-button>
        <div class="footer-right">
          <el-button :disabled="stepIndex === 0" @click="prevStep">
            {{ t("views.gatewayAccessParty.grantWizardPrev") }}
          </el-button>
          <el-button type="primary" :loading="submitting" :disabled="rpmOverCap" @click="nextOrSubmit">
            {{ isLastStep ? t("views.gatewayAccessParty.grantWizardSubmit") : t("views.gatewayAccessParty.grantWizardNext") }}
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
  max-width: 880px;
  margin: 0 auto;
}
.hdr-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.back-btn {
  align-self: flex-start;
  padding-left: 0;
  margin-bottom: 2px;
}
.hdr-title-block .title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}
.hdr-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.hdr-sub .dot {
  opacity: 0.5;
}
.rpm-quota {
  font-variant-numeric: tabular-nums;
}
.rpm-quota.over {
  color: var(--el-color-danger);
  font-weight: 600;
}
.steps {
  margin: 4px 0 20px;
  padding: 0 8px;
}
.step-body {
  min-height: 240px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-extra-light);
}
.toolbar-count {
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-divider {
  width: 1px;
  height: 16px;
  background: var(--el-border-color);
}
.rpm-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.rpm-input {
  width: 108px;
}
.groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.group-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  padding: 0 4px;
}
.group-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.ep-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}
.ep-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  transition: background 0.15s ease;
}
.ep-item:last-child {
  border-bottom: none;
}
.ep-item:hover {
  background: var(--el-fill-color-light);
}
.ep-item.selected {
  background: var(--el-color-primary-light-9);
}
.ep-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
  user-select: none;
}
.ep-name {
  display: block;
  font-size: 14px;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}
.ep-path {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ep-rpm {
  width: 100px;
  flex-shrink: 0;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.footer-right {
  display: flex;
  gap: 8px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
