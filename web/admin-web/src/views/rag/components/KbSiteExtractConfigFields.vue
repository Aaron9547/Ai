<script setup lang="ts">
import { useI18n } from "vue-i18n";
import KbFormLabelTip from "./KbFormLabelTip.vue";
import {
  SITE_CRAWL_DISCOVERY_STRATEGY_IDS,
  type SiteExtractConfigForm,
} from "@/views/rag/siteExtractConfigFormModel";

const form = defineModel<SiteExtractConfigForm>({ required: true });

const { t } = useI18n();

const presetOptions = [
  { value: "", labelKey: "views.kbMatrix.siteExtract.presetDefault" },
  { value: "CONSERVATIVE", labelKey: "admin.shell.siteCrawl.presetConservative" },
  { value: "BALANCED", labelKey: "admin.shell.siteCrawl.presetBalanced" },
  { value: "AGGRESSIVE", labelKey: "admin.shell.siteCrawl.presetAggressive" },
] as const;

const extractorOptions = [
  { value: "", labelKey: "views.kbMatrix.siteExtract.extractorDefault" },
  { value: "jsoup", labelKey: "views.kbMatrix.siteExtract.extractorJsoup" },
  { value: "readability", labelKey: "views.kbMatrix.siteExtract.extractorReadability" },
] as const;

const jsRenderTriState = [
  { value: "inherit", labelKey: "views.kbMatrix.siteExtract.followTenant" },
  { value: "on", labelKey: "views.kbMatrix.siteExtract.enabled" },
  { value: "off", labelKey: "views.kbMatrix.siteExtract.disabled" },
] as const;

function jsRenderModeFromForm(v: boolean | undefined): "inherit" | "on" | "off" {
  if (v === true) return "on";
  if (v === false) return "off";
  return "inherit";
}

function jsRenderModeToForm(mode: "inherit" | "on" | "off"): boolean | undefined {
  if (mode === "on") return true;
  if (mode === "off") return false;
  return undefined;
}

function strategyLabel(id: string): string {
  const key = `views.kbMatrix.siteExtract.strategies.${id}`;
  const text = t(key);
  return text === key ? id : String(text);
}
</script>

<template>
  <div class="kb-site-extract-fields">
    <el-alert type="info" show-icon :closable="false" class="extract-hint">
      {{ t("views.kbMatrix.siteExtractAdvancedHint") }}
    </el-alert>

    <div class="extract-section-head">{{ t("views.kbMatrix.siteExtract.sectionPolicy") }}</div>
    <div class="extract-fields-grid">
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.presetLock')"
            :tip="t('views.kbMatrix.siteExtract.presetLockTip')"
          />
        </template>
        <el-select v-model="form.presetLock" clearable :placeholder="t('views.kbMatrix.siteExtract.followTenant')" style="width: 100%">
          <el-option
            v-for="opt in presetOptions.filter((o) => o.value)"
            :key="opt.value"
            :label="t(opt.labelKey)"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </div>

    <div class="extract-section-head">{{ t("views.kbMatrix.siteExtract.sectionContent") }}</div>
    <div class="extract-fields-grid extract-fields-grid--stack">
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.extractor')"
            :tip="t('views.kbMatrix.siteExtract.extractorTip')"
          />
        </template>
        <el-select v-model="form.extractor" clearable :placeholder="t('views.kbMatrix.siteExtract.followTenant')" style="width: 100%">
          <el-option
            v-for="opt in extractorOptions.filter((o) => o.value)"
            :key="opt.value"
            :label="t(opt.labelKey)"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.contentSelector')"
            :tip="t('views.kbMatrix.siteExtract.contentSelectorTip')"
          />
        </template>
        <el-input
          v-model="form.contentSelector"
          clearable
          :placeholder="t('views.kbMatrix.siteExtract.selectorPh')"
        />
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.titleSelector')"
            :tip="t('views.kbMatrix.siteExtract.titleSelectorTip')"
          />
        </template>
        <el-input
          v-model="form.titleSelector"
          clearable
          :placeholder="t('views.kbMatrix.siteExtract.selectorPh')"
        />
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.excludeSelectors')"
            :tip="t('views.kbMatrix.siteExtract.excludeSelectorsTip')"
          />
        </template>
        <el-input
          v-model="form.excludeSelectorsText"
          type="textarea"
          :rows="3"
          :placeholder="t('views.kbMatrix.siteExtract.excludeSelectorsPh')"
        />
      </el-form-item>
    </div>

    <div class="extract-section-head">{{ t("views.kbMatrix.siteExtract.sectionDiscovery") }}</div>
    <div class="extract-fields-grid extract-fields-grid--stack">
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.discoveryStrategies')"
            :tip="t('views.kbMatrix.siteExtract.discoveryStrategiesTip')"
          />
        </template>
        <el-select
          v-model="form.discoveryStrategies"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :placeholder="t('views.kbMatrix.siteExtract.followTenant')"
          style="width: 100%"
        >
          <el-option
            v-for="id in SITE_CRAWL_DISCOVERY_STRATEGY_IDS"
            :key="id"
            :label="strategyLabel(id)"
            :value="id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.discoveryMaxDepth')"
            :tip="t('views.kbMatrix.siteExtract.discoveryMaxDepthTip')"
          />
        </template>
        <el-input-number
          v-model="form.discoveryMaxDepth"
          :min="1"
          :max="6"
          :step="1"
          controls-position="right"
          class="num-wide"
        />
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('views.kbMatrix.siteExtract.jsRenderEnabled')"
            :tip="t('views.kbMatrix.siteExtract.jsRenderEnabledTip')"
          />
        </template>
        <el-select
          :model-value="jsRenderModeFromForm(form.jsRenderEnabled)"
          style="width: 100%"
          @update:model-value="(v: 'inherit' | 'on' | 'off') => (form.jsRenderEnabled = jsRenderModeToForm(v))"
        >
          <el-option
            v-for="opt in jsRenderTriState"
            :key="opt.value"
            :label="t(opt.labelKey)"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <div class="extract-inline-pair">
        <el-form-item>
          <template #label>
            <KbFormLabelTip
              :label="t('views.kbMatrix.siteExtract.jsMaxPages')"
              :tip="t('views.kbMatrix.siteExtract.jsMaxPagesTip')"
            />
          </template>
          <el-input-number
            v-model="form.jsMaxPagesPerRun"
            :min="1"
            :max="15"
            :step="1"
            controls-position="right"
            class="num-wide"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <KbFormLabelTip
              :label="t('views.kbMatrix.siteExtract.jsLinkThreshold')"
              :tip="t('views.kbMatrix.siteExtract.jsLinkThresholdTip')"
            />
          </template>
          <el-input-number
            v-model="form.jsOnlyWhenLinkCountBelow"
            :min="1"
            :max="200"
            :step="1"
            controls-position="right"
            class="num-wide"
          />
        </el-form-item>
      </div>
    </div>

    <div class="extract-section-head">{{ t("views.kbMatrix.siteExtract.sectionPoliteness") }}</div>
    <div class="extract-fields-grid">
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('admin.shell.siteCrawl.fields.perHostQps')"
            :tip="t('views.kbMatrix.siteExtract.politenessTip')"
          />
        </template>
        <el-input-number
          v-model="form.perHostQps"
          :min="0.05"
          :max="5"
          :step="0.05"
          :precision="2"
          controls-position="right"
          class="num-wide"
        />
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('admin.shell.siteCrawl.fields.perHostConcurrency')"
            :tip="t('views.kbMatrix.siteExtract.politenessTip')"
          />
        </template>
        <el-input-number
          v-model="form.perHostConcurrency"
          :min="1"
          :max="64"
          :step="1"
          controls-position="right"
          class="num-wide"
        />
      </el-form-item>
      <el-form-item>
        <template #label>
          <KbFormLabelTip
            :label="t('admin.shell.siteCrawl.fields.globalConcurrency')"
            :tip="t('views.kbMatrix.siteExtract.politenessTip')"
          />
        </template>
        <el-input-number
          v-model="form.globalConcurrency"
          :min="1"
          :max="128"
          :step="1"
          controls-position="right"
          class="num-wide"
        />
      </el-form-item>
    </div>
  </div>
</template>

<style scoped>
.kb-site-extract-fields {
  width: 100%;
}

.extract-hint {
  margin-bottom: 4px;
}

.extract-hint :deep(.el-alert__content) {
  line-height: 1.55;
}

.extract-section-head {
  margin: 16px 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.extract-section-head:first-of-type {
  margin-top: 8px;
}

.extract-fields-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.extract-fields-grid--stack {
  grid-template-columns: 1fr;
}

.extract-inline-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.num-wide {
  width: 100%;
}

@media (max-width: 640px) {
  .extract-fields-grid,
  .extract-inline-pair {
    grid-template-columns: 1fr;
  }
}
</style>
