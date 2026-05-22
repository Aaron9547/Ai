<script setup lang="ts">
import { useI18n } from "vue-i18n";
import ShellFieldLabel from "@/views/system/ShellFieldLabel.vue";
import type { SiteCrawlRuntimeForm } from "@/views/system/siteCrawlRuntimeFormModel";

const form = defineModel<SiteCrawlRuntimeForm>({ required: true });

const { t } = useI18n();
</script>

<template>
  <div class="site-crawl-custom-fields">
    <div class="outbound-section-head">{{ t("admin.shell.siteCrawl.subDiscovery") }}</div>
    <div class="outbound-fields-grid">
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.maxDepthDefault')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.maxDepthDefault"
          />
        </template>
        <el-input-number v-model="form.maxDepthDefault" :min="1" :max="6" :step="1" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.maxExplorePages')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.maxExplorePages"
          />
        </template>
        <el-input-number v-model="form.maxExplorePages" :min="50" :max="20000" :step="50" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.maxArticlesPerRun')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.maxArticlesPerRun"
          />
        </template>
        <el-input-number v-model="form.maxArticlesPerRun" :min="0" :max="20000" :step="50" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.jsRenderEnabled')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.jsRenderEnabled"
          />
        </template>
        <el-switch v-model="form.jsRenderEnabled" />
      </el-form-item>
    </div>

    <div class="outbound-section-head">{{ t("admin.shell.siteCrawl.subPoliteness") }}</div>
    <div class="outbound-fields-grid">
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.perHostQps')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.perHostQps"
          />
        </template>
        <el-input-number v-model="form.perHostQps" :min="0.05" :max="5" :step="0.05" :precision="2" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.perHostConcurrency')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.perHostConcurrency"
          />
        </template>
        <el-input-number v-model="form.perHostConcurrency" :min="1" :max="64" :step="1" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.globalConcurrency')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.globalConcurrency"
          />
        </template>
        <el-input-number v-model="form.globalConcurrency" :min="1" :max="128" :step="1" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.retryMax')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.retryMax"
          />
        </template>
        <el-input-number v-model="form.retryMax" :min="0" :max="8" :step="1" controls-position="right" class="num-wide" />
      </el-form-item>
    </div>

    <div class="outbound-section-head">{{ t("admin.shell.siteCrawl.subFetchIngest") }}</div>
    <div class="outbound-fields-grid">
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.maxBodyBytes')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.maxBodyBytes"
          />
        </template>
        <el-input-number v-model="form.maxBodyBytes" :min="65536" :max="20971520" :step="65536" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.timeoutMs')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.timeoutMs"
          />
        </template>
        <el-input-number v-model="form.timeoutMs" :min="3000" :max="120000" :step="500" controls-position="right" class="num-wide" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.readabilityFallbackEnabled')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.readabilityFallbackEnabled"
          />
        </template>
        <el-switch v-model="form.readabilityFallbackEnabled" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <ShellFieldLabel
            :label="t('admin.shell.siteCrawl.fields.minMarkdownChars')"
            tooltip-i18n-key="admin.shell.siteCrawl.tooltips.minMarkdownChars"
          />
        </template>
        <el-input-number v-model="form.minMarkdownChars" :min="1" :max="5000" :step="1" controls-position="right" class="num-wide" />
      </el-form-item>
    </div>
  </div>
</template>

<style scoped>
.site-crawl-custom-fields {
  width: 100%;
}

.site-crawl-custom-fields .outbound-section-head {
  margin: 20px 0 12px;
}

.site-crawl-custom-fields .outbound-section-head:first-child {
  margin-top: 4px;
}

.site-crawl-custom-fields .outbound-fields-grid {
  margin-bottom: 4px;
}
</style>
