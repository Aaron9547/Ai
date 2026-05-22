<template>
  <div class="tenant-shell-page">
    <div class="page-header">
      <h2 class="page-title">{{ t("admin.shell.pageTitle") }}</h2>
      <el-button @click="reload">{{ t("common.refresh") }}</el-button>
    </div>

    <el-card class="block" shadow="never">
      <template #header>
        <span>{{ t("admin.shell.brandingBlock") }}</span>
      </template>
      <el-form label-width="140px" class="shell-form">
        <el-form-item :label="t('admin.shell.sideLogo')">
          <div class="logo-row">
            <el-upload
              class="logo-uploader"
              :show-file-list="false"
              accept="image/png,image/jpeg,image/jpg,image/gif,image/webp"
              :before-upload="onLogoBeforeUpload"
              :http-request="doLogoUpload"
            >
              <el-button type="primary">{{ t("admin.shell.logoUpload") }}</el-button>
            </el-upload>
            <el-button v-if="form.logoUrl.trim()" link type="danger" @click="clearLogo">{{
              t("admin.shell.logoClear")
            }}</el-button>
          </div>
          <div class="logo-hint">{{ t("admin.shell.logoUrlHint") }}</div>
          <el-input
            v-model="form.logoUrl"
            type="url"
            clearable
            class="logo-url-input"
            :placeholder="t('admin.shell.logoUrlPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('admin.shell.portalTitle')">
          <el-input v-model="form.portalTitle" clearable :placeholder="t('admin.shell.portalTitlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('admin.shell.footerText')">
          <el-input
            v-model="form.footerText"
            type="textarea"
            :rows="2"
            clearable
            :placeholder="t('admin.shell.footerPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('admin.shell.preview')">
          <div class="preview-brand">
            <img v-if="previewLogoAbs" :src="previewLogoAbs" class="preview-logo" alt="" />
            <span v-else class="preview-mark" aria-hidden="true" />
            <div class="preview-text">
              <div class="preview-title">{{ previewTitle }}</div>
              <div class="preview-sub">{{ t("admin.brandSub") }}</div>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingBranding" @click="saveBranding">{{
            t("admin.shell.saveBranding")
          }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="block outbound-policy-card model-calling-policy-card" shadow="never">
      <template #header>
        <div class="outbound-card-header">
          <span class="outbound-card-title">{{ t("admin.shell.modelCallingBlock") }}</span>
          <span class="outbound-card-sub">{{ t("admin.shell.modelCalling.blockSub") }}</span>
        </div>
      </template>
      <p class="model-calling-tip">{{ t("admin.shell.modelCallingBlockTip") }}</p>
      <div class="outbound-card-body">
        <el-form :key="locale" label-width="auto" class="outbound-fields-form model-calling-fields-form">
          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionEmbedding") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.memoryEmbedding')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.memoryEmbedding"
                />
              </template>
              <el-select
                v-model="memoryEmbeddingModelId"
                class="outbound-line-input memory-embedding-select"
                clearable
                filterable
                :loading="loadingVectorModels"
                :placeholder="t('admin.shell.modelCalling.memoryEmbeddingPlaceholder')"
              >
                <el-option
                  v-for="opt in memoryEmbeddingSelectOptions"
                  :key="opt.id"
                  :label="opt.label"
                  :value="opt.id"
                  :disabled="opt.disabled"
                />
              </el-select>
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionRagVector") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.ragVectorDimension')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.ragVectorDimension"
                />
              </template>
              <el-select
                v-model="ragVectorDimension"
                class="outbound-line-input"
                :disabled="ragVectorDimensionLocked"
                :placeholder="ragVectorDimensionPlaceholder"
              >
                <el-option
                  v-for="d in ragVectorDimensionOptions"
                  :key="d"
                  :label="String(d)"
                  :value="String(d)"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.ragRetrievalMode')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.ragRetrievalMode"
                />
              </template>
              <el-select
                v-model="ragRetrievalMode"
                class="outbound-line-input"
                clearable
                :placeholder="ragRetrievalModePlaceholder"
              >
                <el-option
                  :label="t('admin.shell.modelCalling.ragRetrievalMilvus')"
                  value="milvus"
                />
                <el-option
                  :label="t('admin.shell.modelCalling.ragRetrievalHybrid')"
                  value="milvus_es_hybrid"
                />
              </el-select>
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionChatPrompt") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.ragSnippetMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.ragSnippetMaxChars" /></template><el-input-number v-model="promptLimitsForm.ragSnippetMaxChars" :min="120" :max="16000" :step="10" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.ragMaxSnippets')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.ragMaxSnippets" /></template><el-input-number v-model="promptLimitsForm.ragMaxSnippets" :min="1" :max="12" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.webSummaryMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webSummaryMaxChars" /></template><el-input-number v-model="promptLimitsForm.webSummaryMaxChars" :min="200" :max="32000" :step="50" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.webMaxReferences')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webMaxReferences" /></template><el-input-number v-model="promptLimitsForm.webMaxReferences" :min="0" :max="24" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.webReferenceSnippetMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webReferenceSnippetMaxChars" /></template><el-input-number v-model="promptLimitsForm.webReferenceSnippetMaxChars" :min="40" :max="8000" :step="10" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.webReferenceUrlMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webReferenceUrlMaxChars" /></template><el-input-number v-model="promptLimitsForm.webReferenceUrlMaxChars" :min="32" :max="2048" :step="8" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.webGroundingTotalMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webGroundingTotalMaxChars" /></template><el-input-number v-model="promptLimitsForm.webGroundingTotalMaxChars" :min="400" :max="200000" :step="100" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.historyMaxMessages')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.historyMaxMessages" /></template><el-input-number v-model="promptLimitsForm.historyMaxMessages" :min="0" :max="200" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.historyMaxCharsPerMessage')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.historyMaxCharsPerMessage" /></template><el-input-number v-model="promptLimitsForm.historyMaxCharsPerMessage" :min="200" :max="128000" :step="100" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.historyTotalMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.historyTotalMaxChars" /></template><el-input-number v-model="promptLimitsForm.historyTotalMaxChars" :min="1024" :max="500000" :step="256" controls-position="right" class="num-wide" /></el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionMemoryPolicy") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.abstractRefreshEnabled')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.abstractRefreshEnabled" /></template><el-switch v-model="memoryPolicyForm.abstractRefreshEnabled" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.abstractSyncFallback')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.abstractSyncFallback" /></template><el-switch v-model="memoryPolicyForm.abstractSyncFallback" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.enqueueAbstractOnConversationCreate')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.enqueueAbstractOnConversationCreate" /></template><el-switch v-model="memoryPolicyForm.enqueueAbstractOnConversationCreate" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.abstractChunkWindow')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.abstractChunkWindow" /></template><el-input-number v-model="memoryPolicyForm.abstractChunkWindow" :min="4" :max="80" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.abstractDedupeTtlSeconds')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.abstractDedupeTtlSeconds" /></template><el-input-number v-model="memoryPolicyForm.abstractDedupeTtlSeconds" :min="5" :max="86400" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.promptAbstractBodyMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.promptAbstractBodyMaxChars" /></template><el-input-number v-model="memoryPolicyForm.promptAbstractBodyMaxChars" :min="1" :max="100000" :step="50" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.promptConcreteChunkLimit')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.promptConcreteChunkLimit" /></template><el-input-number v-model="memoryPolicyForm.promptConcreteChunkLimit" :min="1" :max="24" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.promptConcreteChunkMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.promptConcreteChunkMaxChars" /></template><el-input-number v-model="memoryPolicyForm.promptConcreteChunkMaxChars" :min="80" :max="1800" :step="10" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.vectorSearchTopK')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.vectorSearchTopK" /></template><el-input-number v-model="memoryPolicyForm.vectorSearchTopK" :min="1" :max="50" :step="1" controls-position="right" class="num-wide" /></el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionInputGuard") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.guardEnabled')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.guardEnabled" /></template><el-switch v-model="inputGuardForm.enabled" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.guardMinChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.guardMinChars" /></template><el-input-number v-model="inputGuardForm.minUserTextChars" :min="1" :max="100000" :step="1" controls-position="right" class="num-wide" /></el-form-item>
            <el-form-item><template #label><ShellFieldLabel :label="t('admin.shell.modelCalling.fields.guardMaxChars')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.guardMaxChars" /></template><el-input-number v-model="inputGuardForm.maxUserTextChars" :min="1" :max="200000" :step="100" controls-position="right" class="num-wide" /></el-form-item>
          </div>
          <el-form-item class="outbound-field-span">
            <template #label>
              <ShellFieldLabel :label="t('admin.shell.modelCalling.fields.blockedReplyTemplate')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.blockedReplyTemplate" />
            </template>
            <div class="field-stack">
              <div class="field-hint">{{ t("admin.shell.modelCalling.hints.blockedReplyTemplate") }}</div>
              <el-input
                v-model="inputGuardForm.blockedReplyTemplate"
                type="textarea"
                :rows="3"
                class="outbound-line-input"
                :placeholder="t('admin.shell.modelCalling.placeholders.blockedReplyTemplate')"
              />
            </div>
          </el-form-item>
          <el-form-item class="outbound-field-span">
            <template #label>
              <ShellFieldLabel :label="t('admin.shell.modelCalling.fields.sensitiveWords')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.sensitiveWords" />
            </template>
            <div class="field-stack">
              <div class="field-hint">{{ t("admin.shell.modelCalling.hints.sensitiveWords") }}</div>
              <el-input
                v-model="inputGuardForm.sensitiveWordsText"
                type="textarea"
                :rows="4"
                class="mono-textarea outbound-line-input"
                :placeholder="t('admin.shell.modelCalling.placeholders.sensitiveWords')"
              />
            </div>
          </el-form-item>
          <el-form-item class="outbound-field-span">
            <template #label>
              <ShellFieldLabel :label="t('admin.shell.modelCalling.fields.regexPatterns')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.regexPatterns" />
            </template>
            <div class="field-stack">
              <div class="field-hint">{{ t("admin.shell.modelCalling.hints.regexPatterns") }}</div>
              <el-input
                v-model="inputGuardForm.regexPatternsText"
                type="textarea"
                :rows="4"
                class="mono-textarea outbound-line-input"
                :placeholder="t('admin.shell.modelCalling.placeholders.regexPatterns')"
              />
            </div>
          </el-form-item>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionWebSearch") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item class="outbound-field-span">
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webSearchModel')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webSearchModel"
                />
              </template>
              <el-select
                v-model="webSearchGroundingModelId"
                filterable
                clearable
                class="outbound-line-input"
                :loading="loadingWebSearchModels"
                :placeholder="t('admin.shell.modelCalling.webSearchModelPlaceholder')"
              >
                <el-option
                  v-for="opt in webSearchModelSelectOptions"
                  :key="opt.id"
                  :label="opt.label"
                  :value="opt.id"
                  :disabled="opt.disabled"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel :label="t('admin.shell.modelCalling.webSearchRounds')" tooltip-i18n-key="admin.shell.modelCalling.tooltips.webSearchRounds" />
              </template>
              <el-input-number v-model="webSearchRounds" :min="1" :max="10" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>
          <div v-for="(_, sIdx) in suffixSlots" :key="'suf-' + sIdx" class="outbound-fields-grid suffix-row">
            <el-form-item class="outbound-field-span">
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.roundSuffixRound', { n: sIdx + 1 })"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.roundSuffix"
                />
              </template>
              <el-input v-model="suffixSlots[sIdx]" class="outbound-line-input" type="textarea" :rows="2" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.modelCalling.sectionWebSearchCache") }}</div>
          <p class="field-hint web-cache-hint">{{ t("admin.shell.modelCalling.hints.webSearchCache") }}</p>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheEnabled')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheEnabled"
                />
              </template>
              <el-switch v-model="webSearchCacheForm.enabled" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheSemantic')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheSemantic"
                />
              </template>
              <el-switch v-model="webSearchCacheForm.semanticEnabled" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheFreshHours')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheFreshHours"
                />
              </template>
              <el-input-number v-model="webSearchCacheForm.freshHours" :min="0" :max="336" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheWarmHours')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheWarmHours"
                />
              </template>
              <el-input-number v-model="webSearchCacheForm.warmHours" :min="0" :max="336" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheStaleHours')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheStaleHours"
                />
              </template>
              <el-input-number v-model="webSearchCacheForm.staleHours" :min="0" :max="336" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheSimilarity')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheSimilarity"
                />
              </template>
              <el-input-number
                v-model="webSearchCacheForm.similarityThreshold"
                :min="0.5"
                :max="0.999"
                :step="0.01"
                :precision="2"
                controls-position="right"
                class="num-wide"
              />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheIndexMax')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheIndexMax"
                />
              </template>
              <el-input-number v-model="webSearchCacheForm.indexMaxEntries" :min="10" :max="500" :step="10" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <ShellFieldLabel
                  :label="t('admin.shell.modelCalling.webCacheConvReuseHours')"
                  tooltip-i18n-key="admin.shell.modelCalling.tooltips.webCacheConvReuseHours"
                />
              </template>
              <el-input-number
                v-model="webSearchCacheForm.conversationReuseHours"
                :min="0"
                :max="336"
                :step="1"
                controls-position="right"
                class="num-wide"
              />
            </el-form-item>
          </div>

          <div class="site-crawl-block">
            <div class="outbound-section-head">{{ t("admin.shell.siteCrawl.sectionTitle") }}</div>
            <SiteCrawlPresetPicker v-model="siteCrawlPreset" />
            <p v-if="siteCrawlPreset !== 'CUSTOM'" class="site-crawl-preset-hint">
              {{ siteCrawlPresetSummary }}
            </p>
            <template v-if="siteCrawlPreset === 'CUSTOM'">
              <div class="outbound-section-head">{{ t("admin.shell.siteCrawl.customFieldsTitle") }}</div>
              <SiteCrawlRuntimeFields v-model="siteCrawlForm" />
            </template>
          </div>

          <el-form-item class="outbound-footer-actions">
            <div class="outbound-footer-actions-inner">
              <el-button type="primary" :loading="savingModelCalling" @click="saveModelCalling">{{ t("admin.shell.saveModelCalling") }}</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card class="block outbound-policy-card" shadow="never">
      <template #header>
        <div class="outbound-card-header">
          <span class="outbound-card-title">{{ t("admin.shell.outboundBlock") }}</span>
          <span class="outbound-card-sub">{{ t("admin.shell.outboundBlockTip") }}</span>
        </div>
      </template>
      <el-collapse v-model="outboundPanels" class="outbound-collapse">
        <el-collapse-item :title="t('admin.shell.outboundHelpTitle')" name="help">
          <div class="outbound-help-body">{{ t("admin.shell.outboundHelpIntro") }}</div>
          <el-alert type="warning" show-icon :closable="false" class="outbound-warn">
            {{ t("admin.shell.circuitBreakerHint") }}
          </el-alert>
        </el-collapse-item>
      </el-collapse>

      <div class="outbound-card-body">
        <el-form :key="locale" :model="outboundForm" label-width="auto" class="outbound-fields-form">
          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionGeneral") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel :label="t('admin.shell.outbound.fields.enabled')" tip-key="enabled" />
              </template>
              <el-switch v-model="outboundForm.enabled" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.connectTimeoutSeconds')"
                  tip-key="connectTimeoutSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.connectTimeoutSeconds" :min="1" :max="600" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionStreamRetry") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamMaxAttempts')"
                  tip-key="streamMaxAttempts"
                />
              </template>
              <el-input-number v-model="outboundForm.streamMaxAttempts" :min="0" :max="20" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamInitialBackoffMs')"
                  tip-key="streamInitialBackoffMs"
                />
              </template>
              <el-input-number v-model="outboundForm.streamInitialBackoffMs" :min="0" :max="600000" :step="50" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamMaxBackoffMs')"
                  tip-key="streamMaxBackoffMs"
                />
              </template>
              <el-input-number v-model="outboundForm.streamMaxBackoffMs" :min="0" :max="600000" :step="100" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionStreamTimeouts") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamRequestTimeoutSeconds')"
                  tip-key="streamRequestTimeoutSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.streamRequestTimeoutSeconds" :min="1" :max="86400" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamFirstLineTimeoutSeconds')"
                  tip-key="streamFirstLineTimeoutSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.streamFirstLineTimeoutSeconds" :min="1" :max="86400" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.streamLineIdleTimeoutSeconds')"
                  tip-key="streamLineIdleTimeoutSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.streamLineIdleTimeoutSeconds" :min="1" :max="86400" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionSyncRetry") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.syncMaxAttempts')"
                  tip-key="syncMaxAttempts"
                />
              </template>
              <el-input-number v-model="outboundForm.syncMaxAttempts" :min="0" :max="20" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.syncInitialBackoffMs')"
                  tip-key="syncInitialBackoffMs"
                />
              </template>
              <el-input-number v-model="outboundForm.syncInitialBackoffMs" :min="0" :max="600000" :step="50" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.syncMaxBackoffMs')"
                  tip-key="syncMaxBackoffMs"
                />
              </template>
              <el-input-number v-model="outboundForm.syncMaxBackoffMs" :min="0" :max="600000" :step="100" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionJitter") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel :label="t('admin.shell.outbound.fields.jitterRatio')" tip-key="jitterRatio" />
              </template>
              <el-input-number v-model="outboundForm.jitterRatio" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" class="num-wide" />
            </el-form-item>
          </div>

          <div class="outbound-section-head">{{ t("admin.shell.outbound.sectionTenantQuarantine") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item>
              <template #label>
                <OutboundFieldLabel :label="t('admin.shell.outbound.fields.tqEnabled')" tip-key="tqEnabled" />
              </template>
              <el-switch v-model="outboundForm.tenantQuarantine.enabled" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.tqWindowSeconds')"
                  tip-key="tqWindowSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.tenantQuarantine.windowSeconds" :min="1" :max="86400" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel :label="t('admin.shell.outbound.fields.tqThreshold')" tip-key="tqThreshold" />
              </template>
              <el-input-number v-model="outboundForm.tenantQuarantine.threshold" :min="1" :max="1000" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.tqCooldownSeconds')"
                  tip-key="tqCooldownSeconds"
                />
              </template>
              <el-input-number v-model="outboundForm.tenantQuarantine.cooldownSeconds" :min="1" :max="86400" :step="1" controls-position="right" class="num-wide" />
            </el-form-item>
            <el-form-item class="outbound-field-span">
              <template #label>
                <OutboundFieldLabel :label="t('admin.shell.outbound.fields.tqUserMessage')" tip-key="tqUserMessage" />
              </template>
              <el-input
                v-model="outboundForm.tenantQuarantine.userMessage"
                class="outbound-line-input"
                clearable
                maxlength="500"
                show-word-limit
                :placeholder="t('admin.shell.outbound.hints.tqUserMessagePlaceholder')"
              />
            </el-form-item>
          </div>

          <div class="outbound-section-head outbound-section-full">{{ t("admin.shell.outbound.sectionRetryableStatuses") }}</div>
          <div class="outbound-fields-grid">
            <el-form-item class="outbound-field-span">
              <template #label>
                <OutboundFieldLabel
                  :label="t('admin.shell.outbound.fields.retryableHttpStatuses')"
                  tip-key="retryableHttpStatuses"
                />
              </template>
              <el-input
                v-model="outboundForm.retryableHttpStatusesText"
                class="outbound-line-input"
                clearable
                :placeholder="t('admin.shell.outbound.hints.retryableHttpStatuses')"
              />
            </el-form-item>
          </div>
          <el-form-item class="outbound-footer-actions">
            <div class="outbound-footer-actions-inner">
              <el-button link class="outbound-reset-secondary" @click="resetOutboundToBaseline">
                {{ t("admin.shell.outbound.resetDefaults") }}
              </el-button>
              <el-button type="primary" :loading="savingOutbound" @click="saveOutbound">{{ t("admin.shell.saveOutbound") }}</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <el-card class="block outbound-policy-card cb-readonly-card" shadow="never">
      <template #header>
        <div class="outbound-card-header">
          <span class="outbound-card-title">{{ t("admin.shell.circuitBreakerReadonly.title") }}</span>
          <span class="outbound-card-sub">{{ t("admin.shell.circuitBreakerReadonly.sub") }}</span>
        </div>
      </template>
      <div class="outbound-card-body">
        <el-empty v-if="!circuitBreakerRows.length" :description="t('admin.shell.circuitBreakerReadonly.empty')" />
        <div v-else class="cb-ro-grid">
          <div v-for="row in circuitBreakerRows" :key="row.key" class="cb-ro-tile">
            <div class="cb-ro-tile-k">{{ row.label }}</div>
            <div class="cb-ro-tile-v">{{ row.value }}</div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import type { UploadRequestOptions } from "element-plus";
import * as tenantShellApi from "@/api/tenantShellConfig";
import type { TenantShellModelCallingRuntime } from "@/api/tenantShellConfig";
import { listLlmModels, type LlmModelAdminView } from "@/api/models";
import { AI_ADMIN_TENANT_SHELL_CHANGED_EVENT } from "@/constants/adminWorkspace";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";
import OutboundFieldLabel from "@/views/system/OutboundFieldLabel.vue";
import ShellFieldLabel from "@/views/system/ShellFieldLabel.vue";
import {
  buildTenantOutboundOverlayJson,
  readOutboundFormFromEffective,
  validateOutboundForm,
  type OutboundResilienceForm,
} from "@/views/system/outboundResilienceFormModel";
import { buildCircuitBreakerBaselineRows } from "@/views/system/circuitBreakerBaselineDisplay";
import {
  CHAT_PROMPT_DEFAULT,
  INPUT_GUARD_DEFAULT,
  MEMORY_POLICY_DEFAULT,
  parseChatPromptLimitsJson,
  parseInputGuardJson,
  parseMemoryPolicyJson,
  parseSuffixJsonArray,
  resizeSuffixSlots,
  serializeChatPromptLimitsJson,
  serializeInputGuardJson,
  serializeMemoryPolicyJson,
  serializeSuffixJson,
  validateInputGuard,
  parseMemoryEmbeddingModelId,
  parseWebSearchGroundingModelId,
  validateMemoryEmbeddingId,
  WEB_SEARCH_CACHE_DEFAULT,
  parseWebSearchCacheJson,
  serializeWebSearchCacheJson,
  normalizeSiteCrawlPreset,
  type SiteCrawlPresetValue,
} from "@/views/system/modelCallingRuntimeFormModel";
import SiteCrawlPresetPicker from "@/views/system/components/SiteCrawlPresetPicker.vue";
import SiteCrawlRuntimeFields from "@/views/system/components/SiteCrawlRuntimeFields.vue";
import {
  parseSiteCrawlRuntimeForm,
  serializeSiteCrawlRuntimeJson,
  SITE_CRAWL_RUNTIME_DEFAULT,
  type SiteCrawlRuntimeForm,
} from "@/views/system/siteCrawlRuntimeFormModel";

const { t, locale } = useI18n();

const form = reactive({
  logoUrl: "",
  portalTitle: "",
  footerText: "",
});

const outboundForm = reactive<OutboundResilienceForm>(readOutboundFormFromEffective({}));

const memoryEmbeddingModelId = ref<number | undefined>(undefined);
const ragVectorDimension = ref("");
const ragVectorDimensionEffective = ref(2048);
const ragVectorDimensionLocked = ref(false);
const processDefaultVectorDimension = ref(2048);
const ragVectorDimensionOptions = [512, 768, 1024, 1536, 2048, 3072, 4096];
const ragRetrievalMode = ref("");
const ragRetrievalModeEffective = ref("milvus_es_hybrid");
const webSearchGroundingModelId = ref<number | undefined>(undefined);
const vectorModelsForMemory = ref<LlmModelAdminView[]>([]);
const webSearchModelsForBinding = ref<LlmModelAdminView[]>([]);
const loadingVectorModels = ref(false);
const loadingWebSearchModels = ref(false);

const ragRetrievalModeEffectiveLabel = computed(() => {
  const m = ragRetrievalModeEffective.value;
  if (m === "milvus") return t("admin.shell.modelCalling.ragRetrievalMilvus");
  if (m === "milvus_es_hybrid") return t("admin.shell.modelCalling.ragRetrievalHybrid");
  return m;
});

const ragVectorDimensionPlaceholder = computed(() => {
  if (ragVectorDimensionLocked.value) {
    return t("admin.shell.modelCalling.ragVectorDimensionPlaceholderLocked", {
      dim: ragVectorDimensionEffective.value,
    });
  }
  return t("admin.shell.modelCalling.ragVectorDimensionPlaceholderOpen", {
    default: processDefaultVectorDimension.value,
  });
});

const ragRetrievalModePlaceholder = computed(() =>
  t("admin.shell.modelCalling.ragRetrievalModePlaceholder", {
    mode: ragRetrievalModeEffectiveLabel.value,
  }),
);

const memoryEmbeddingSelectOptions = computed(() => {
  void locale.value;
  const selected = memoryEmbeddingModelId.value;
  const rows = vectorModelsForMemory.value
    .slice()
    .sort((a, b) => {
      if (a.enabled !== b.enabled) return a.enabled ? -1 : 1;
      const ao = a.sortOrder ?? 0;
      const bo = b.sortOrder ?? 0;
      if (ao !== bo) return ao - bo;
      return a.id - b.id;
    })
    .map((m) => ({
      id: m.id,
      label: `${m.displayName} (${m.alias}) · ${m.openaiModelId}`,
      disabled: !m.enabled && m.id !== selected,
    }));
  if (typeof selected === "number" && !rows.some((r) => r.id === selected)) {
    return [
      {
        id: selected,
        label: t("admin.shell.modelCalling.memoryEmbeddingOrphan", { id: selected }),
        disabled: false,
      },
      ...rows,
    ];
  }
  return rows;
});

const webSearchModelSelectOptions = computed(() => {
  void locale.value;
  const selected = webSearchGroundingModelId.value;
  const rows = webSearchModelsForBinding.value
    .slice()
    .sort((a, b) => {
      if (a.enabled !== b.enabled) return a.enabled ? -1 : 1;
      const ao = a.sortOrder ?? 0;
      const bo = b.sortOrder ?? 0;
      if (ao !== bo) return ao - bo;
      return a.id - b.id;
    })
    .map((m) => ({
      id: m.id,
      label: `${m.displayName} (${m.alias}) · ${m.openaiModelId}`,
      disabled: !m.enabled && m.id !== selected,
    }));
  if (typeof selected === "number" && !rows.some((r) => r.id === selected)) {
    return [
      {
        id: selected,
        label: t("admin.shell.modelCalling.webSearchModelOrphan", { id: selected }),
        disabled: false,
      },
      ...rows,
    ];
  }
  return rows;
});

const promptLimitsForm = reactive({ ...CHAT_PROMPT_DEFAULT });
const memoryPolicyForm = reactive({ ...MEMORY_POLICY_DEFAULT });
const inputGuardForm = reactive({ ...INPUT_GUARD_DEFAULT });
const webSearchRounds = ref(3);
const suffixSlots = ref<string[]>([""]);
const webSearchCacheForm = reactive({ ...WEB_SEARCH_CACHE_DEFAULT });
const siteCrawlPreset = ref<SiteCrawlPresetValue>("BALANCED");
const siteCrawlForm = reactive<SiteCrawlRuntimeForm>({ ...SITE_CRAWL_RUNTIME_DEFAULT });
const siteCrawlPresetApplying = ref(false);

async function applySiteCrawlRuntimeJson(rawJson: string, preset: SiteCrawlPresetValue) {
  let json = String(rawJson ?? "").trim() || "{}";
  if (json === "{}") {
    try {
      const templatePreset = preset === "CUSTOM" ? "BALANCED" : preset;
      json = await tenantShellApi.fetchSiteCrawlRuntimeTemplate(templatePreset);
    } catch {
      ElMessage.warning(t("admin.shell.siteCrawl.templateLoadFailed"));
    }
  }
  Object.assign(siteCrawlForm, parseSiteCrawlRuntimeForm(json));
}

watch(siteCrawlPreset, async (next, prev) => {
  if (siteCrawlPresetApplying.value || next === prev || next !== "CUSTOM" || prev === "CUSTOM") {
    return;
  }
  siteCrawlPresetApplying.value = true;
  try {
    const json = await tenantShellApi.fetchSiteCrawlRuntimeTemplate("BALANCED");
    Object.assign(siteCrawlForm, parseSiteCrawlRuntimeForm(json));
  } catch {
    ElMessage.warning(t("admin.shell.siteCrawl.templateLoadFailed"));
  } finally {
    siteCrawlPresetApplying.value = false;
  }
});

const siteCrawlPresetSummary = computed(() => {
  const key =
    siteCrawlPreset.value === "CONSERVATIVE"
      ? "presetConservativeDesc"
      : siteCrawlPreset.value === "AGGRESSIVE"
        ? "presetAggressiveDesc"
        : "presetBalancedDesc";
  return t(`admin.shell.siteCrawl.${key}`);
});
watch(webSearchRounds, (n) => {
  suffixSlots.value = resizeSuffixSlots([...suffixSlots.value], n);
});

const savingBranding = ref(false);
const savingOutbound = ref(false);
const savingModelCalling = ref(false);
const portalTitleResolved = ref("");
const baselineOutboundRef = ref<unknown>({});
const outboundPanels = ref<string[]>(["help"]);

function resolvePublicAssetUrl(raw: string | undefined | null): string {
  const u = (raw ?? "").trim();
  if (!u) return "";
  if (/^https?:\/\//i.test(u)) return u;
  const base = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");
  const path = u.startsWith("/") ? u : `/${u}`;
  return base ? `${base}${path}` : path;
}

const previewLogoAbs = computed(() => resolvePublicAssetUrl(form.logoUrl));

const previewTitle = computed(() => {
  const pt = form.portalTitle.trim();
  if (pt) return pt;
  return portalTitleResolved.value || t("admin.brandTitle");
});

const circuitBreakerRows = computed(() => {
  const zh = String(locale.value ?? "").toLowerCase().startsWith("zh");
  return buildCircuitBreakerBaselineRows(baselineOutboundRef.value, (k) => t(k), zh);
});

async function applyModelCallingFromApi(mc: TenantShellModelCallingRuntime | undefined) {
  if (!mc) {
    return;
  }
  memoryEmbeddingModelId.value = parseMemoryEmbeddingModelId(mc.memoryEmbeddingVectorModelId);
  webSearchGroundingModelId.value = parseWebSearchGroundingModelId(mc.webSearchGroundingModelId);
  Object.assign(promptLimitsForm, parseChatPromptLimitsJson(mc.chatPromptLimitsJson ?? "{}"));
  Object.assign(memoryPolicyForm, parseMemoryPolicyJson(mc.memoryPolicyJson ?? "{}"));
  Object.assign(inputGuardForm, parseInputGuardJson(mc.chatInputGuardJson ?? "{}"));
  const rc = Number.parseInt(String(mc.webSearchGroundingMultiRoundCount ?? "3"), 10);
  webSearchRounds.value = Number.isFinite(rc) && rc >= 1 && rc <= 10 ? rc : 3;
  const rawSuffix = mc.webSearchGroundingRoundSuffixesJson?.trim()
    ? mc.webSearchGroundingRoundSuffixesJson
    : "[]";
  suffixSlots.value = resizeSuffixSlots(parseSuffixJsonArray(rawSuffix), webSearchRounds.value);
  Object.assign(
    webSearchCacheForm,
    parseWebSearchCacheJson(mc.webSearchGroundingCacheJson ?? "{}"),
  );
  siteCrawlPresetApplying.value = true;
  const preset = normalizeSiteCrawlPreset(mc.siteCrawlPreset);
  siteCrawlPreset.value = preset;
  await applySiteCrawlRuntimeJson(mc.siteCrawlRuntimeJson ?? "{}", preset);
  siteCrawlPresetApplying.value = false;
  ragVectorDimension.value = mc.ragVectorDimension?.trim() ?? "";
  ragVectorDimensionEffective.value = mc.ragVectorDimensionEffective ?? processDefaultVectorDimension.value;
  ragVectorDimensionLocked.value = mc.ragVectorDimensionLocked ?? false;
  processDefaultVectorDimension.value = mc.processDefaultVectorDimension ?? 2048;
  if (!ragVectorDimension.value && !ragVectorDimensionLocked.value) {
    ragVectorDimension.value = String(ragVectorDimensionEffective.value);
  }
  ragRetrievalMode.value = mc.ragRetrievalMode?.trim() ?? "";
  ragRetrievalModeEffective.value = mc.ragRetrievalModeEffective?.trim() || "milvus_es_hybrid";
}

async function reload() {
  loadingVectorModels.value = true;
  loadingWebSearchModels.value = true;
  try {
    const [shellRes, vecRes, webRes] = await Promise.allSettled([
      tenantShellApi.getTenantShellConfig(),
      listLlmModels({ modelKind: "VECTOR" }),
      listLlmModels({ modelKind: "WEB_SEARCH" }),
    ]);
    if (shellRes.status === "rejected") {
      throw shellRes.reason;
    }
    const data = shellRes.value;
    if (vecRes.status === "fulfilled") {
      vectorModelsForMemory.value = vecRes.value;
    } else {
      console.warn("[tenant shell] list VECTOR models", vecRes.reason);
      vectorModelsForMemory.value = [];
      ElMessage.warning(t("admin.shell.modelCalling.vectorModelsLoadFailed"));
    }
    if (webRes.status === "fulfilled") {
      webSearchModelsForBinding.value = webRes.value;
    } else {
      console.warn("[tenant shell] list WEB_SEARCH models", webRes.reason);
      webSearchModelsForBinding.value = [];
      ElMessage.warning(t("admin.shell.modelCalling.webSearchModelsLoadFailed"));
    }
    form.logoUrl = data.branding.logoUrl ?? "";
    form.portalTitle = data.branding.portalTitle ?? "";
    form.footerText = data.branding.footerText ?? "";
    portalTitleResolved.value =
      data.branding.portalTitleResolved ?? data.branding.portalTitle ?? t("admin.brandTitle");
    baselineOutboundRef.value = data.outbound.baselineJson ?? {};
    Object.assign(outboundForm, readOutboundFormFromEffective(data.outbound.effectiveMerged));
    await applyModelCallingFromApi(data.modelCallingRuntime);
  } finally {
    loadingVectorModels.value = false;
    loadingWebSearchModels.value = false;
  }
}

function onLogoBeforeUpload(raw: File) {
  const max = 2 * 1024 * 1024;
  if (raw.size > max) {
    ElMessage.error(t("admin.shell.logoTooLarge"));
    return false;
  }
  return true;
}

async function doLogoUpload(opt: UploadRequestOptions) {
  try {
    const file = opt.file as File;
    const { url } = await tenantShellApi.postTenantShellLogo(file);
    form.logoUrl = url;
    ElMessage.success(t("admin.shell.logoUploadOk"));
    opt.onSuccess?.({});
  } catch (e: unknown) {
    console.warn("[logo upload]", e);
    ElMessage.error(t("common.operationFailed"));
    opt.onError?.(e as never);
  }
}

function clearLogo() {
  form.logoUrl = "";
}

function resetOutboundToBaseline() {
  Object.assign(outboundForm, readOutboundFormFromEffective(baselineOutboundRef.value));
  ElMessage.success(t("admin.shell.outbound.resetOk"));
}

function trimToNull(s: string): string | null {
  const t = s.trim();
  return t.length ? t : null;
}

async function saveBranding() {
  savingBranding.value = true;
  try {
    const data = await tenantShellApi.putTenantShellBranding({
      logoUrl: trimToNull(form.logoUrl),
      portalTitle: trimToNull(form.portalTitle),
      footerText: trimToNull(form.footerText),
    });
    form.logoUrl = data.branding.logoUrl ?? "";
    form.portalTitle = data.branding.portalTitle ?? "";
    form.footerText = data.branding.footerText ?? "";
    portalTitleResolved.value =
      data.branding.portalTitleResolved ?? data.branding.portalTitle ?? t("admin.brandTitle");
    ElMessage.success(t("admin.shell.saveBrandingOk"));
    window.dispatchEvent(new Event(AI_ADMIN_TENANT_SHELL_CHANGED_EVENT));
  } catch (e: unknown) {
    console.warn("[tenant shell branding save]", e);
  } finally {
    savingBranding.value = false;
  }
}

async function saveOutbound() {
  const v = validateOutboundForm(outboundForm);
  if (v) {
    ElMessage.error(t(`admin.shell.outbound.validation.${v}`));
    return;
  }
  const outboundJson = buildTenantOutboundOverlayJson(outboundForm, baselineOutboundRef.value);
  savingOutbound.value = true;
  try {
    const data = await tenantShellApi.putTenantShellOutbound({ outboundResilienceJson: outboundJson });
    baselineOutboundRef.value = data.outbound.baselineJson ?? {};
    Object.assign(outboundForm, readOutboundFormFromEffective(data.outbound.effectiveMerged));
    ElMessage.success(t("admin.shell.saveOutboundOk"));
    window.dispatchEvent(new Event(AI_ADMIN_TENANT_SHELL_CHANGED_EVENT));
  } catch (e: unknown) {
    console.warn("[tenant shell outbound save]", e);
  } finally {
    savingOutbound.value = false;
  }
}

async function saveModelCalling() {
  const embStr = memoryEmbeddingModelId.value != null ? String(memoryEmbeddingModelId.value) : "";
  const webModelStr =
    webSearchGroundingModelId.value != null ? String(webSearchGroundingModelId.value) : "";
  if (!validateMemoryEmbeddingId(embStr)) {
    ElMessage.error(t("admin.shell.modelCalling.validation.embeddingId"));
    return;
  }
  if (!validateMemoryEmbeddingId(webModelStr)) {
    ElMessage.error(t("admin.shell.modelCalling.validation.webSearchModelId"));
    return;
  }
  if (!validateInputGuard(inputGuardForm)) {
    ElMessage.error(t("admin.shell.modelCalling.validation.guardRange"));
    return;
  }
  savingModelCalling.value = true;
  try {
    const body: tenantShellApi.TenantShellModelCallingPutBody = {
      memoryEmbeddingVectorModelId: embStr.trim(),
      webSearchGroundingModelId: webModelStr.trim(),
      chatPromptLimitsJson: serializeChatPromptLimitsJson(promptLimitsForm),
      memoryPolicyJson: serializeMemoryPolicyJson(memoryPolicyForm),
      chatInputGuardJson: serializeInputGuardJson(inputGuardForm),
      webSearchGroundingMultiRoundCount: String(webSearchRounds.value),
      webSearchGroundingRoundSuffixesJson: serializeSuffixJson(suffixSlots.value, webSearchRounds.value),
      webSearchGroundingCacheJson: serializeWebSearchCacheJson(webSearchCacheForm),
      siteCrawlPreset: siteCrawlPreset.value,
      siteCrawlRuntimeJson:
        siteCrawlPreset.value === "CUSTOM" ? serializeSiteCrawlRuntimeJson(siteCrawlForm) : "{}",
      ragVectorDimension: ragVectorDimension.value.trim(),
      ragRetrievalMode: ragRetrievalMode.value.trim(),
    };
    const data = await tenantShellApi.putTenantShellModelCallingRuntime(body);
    await applyModelCallingFromApi(data.modelCallingRuntime);
    ElMessage.success(t("admin.shell.saveModelCallingOk"));
    window.dispatchEvent(new Event(AI_ADMIN_TENANT_SHELL_CHANGED_EVENT));
  } catch (e: unknown) {
    console.warn("[tenant shell model-calling save]", e);
    ElMessage.error(apiRequestErrorMessage(e, t("views.runtime.saveFailed")));
  } finally {
    savingModelCalling.value = false;
  }
}

onMounted(() => {
  void reload().catch(() => {
    ElMessage.error(t("common.loadFailed"));
  });
});
</script>

<style scoped>
.tenant-shell-page {
  padding: 16px 24px 32px;
  width: 100%;
  max-width: min(1680px, 100%);
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.page-header :deep(.el-button) {
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.block {
  margin-top: 16px;
}

.outbound-policy-card :deep(.el-card__header) {
  padding: 14px 36px 14px 32px;
}

.outbound-policy-card :deep(.el-card__body) {
  padding: 18px 40px 32px 32px;
}

.outbound-card-body {
  width: 100%;
}

.outbound-card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.outbound-card-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.outbound-card-sub {
  font-size: 12px;
  font-weight: normal;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.shell-form {
  max-width: min(960px, 100%);
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.logo-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 6px 0 8px;
  line-height: 1.5;
  white-space: pre-line;
}

.logo-url-input {
  max-width: min(720px, 100%);
}

.outbound-collapse {
  margin-bottom: 8px;
}

.outbound-help-body {
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
  white-space: pre-line;
}

.outbound-warn {
  margin-top: 10px;
}

.outbound-footer-actions {
  margin-bottom: 0;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.outbound-footer-actions-inner {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px 16px;
  width: 100%;
}

/* 次要操作：与保存同排，易发现；样式弱于主按钮 */
.outbound-reset-secondary {
  height: auto;
  padding: 6px 4px;
  font-size: 13px;
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-thickness: 1px;
}

.outbound-reset-secondary,
.outbound-reset-secondary :deep(span) {
  color: var(--el-text-color-regular) !important;
}

.outbound-reset-secondary:hover,
.outbound-reset-secondary:hover :deep(span) {
  color: var(--el-color-primary) !important;
}

.outbound-fields-form {
  width: 100%;
}

/* 宽屏：每项最大宽度 + auto-fill 自动列数，避免一行被拉得过散 */
.outbound-fields-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  justify-content: start;
  align-items: start;
  gap: 18px 48px;
  width: 100%;
  margin-bottom: 16px;
  padding: 4px 2px 2px;
  box-sizing: border-box;
}

.outbound-fields-grid :deep(.el-form-item) {
  margin-bottom: 0;
  min-width: 0;
  width: 100%;
  max-width: 360px;
  justify-self: start;
}

.outbound-fields-grid :deep(.el-form-item.outbound-field-span) {
  grid-column: 1 / -1;
  max-width: none;
  width: 100%;
  justify-self: stretch;
}

.outbound-fields-grid :deep(.outbound-field-span .el-form-item__content) {
  max-width: min(720px, 100%);
}

/* 自定义 #label 后与控件对齐：标签列右对齐、与内容区垂直居中 */
.outbound-fields-form :deep(.el-form-item) {
  align-items: center;
  margin-bottom: 18px;
}

.outbound-fields-form .outbound-fields-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.outbound-fields-form :deep(.el-form-item__label) {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: auto;
  min-height: var(--el-component-size);
  padding-right: 8px;
  box-sizing: border-box;
}

.outbound-fields-form :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  min-height: var(--el-component-size);
}

/* 多行输入占满一行，不用与短控件同一 flex 行规则 */
.outbound-fields-form :deep(.outbound-field-span .el-form-item__content) {
  display: block;
  width: 100%;
  min-height: 0;
}

.outbound-fields-form :deep(.outbound-footer-actions .el-form-item__content) {
  display: block;
  width: 100%;
  min-height: 0;
}

.outbound-line-input {
  width: 100%;
  max-width: min(520px, 100%);
}

.outbound-fields-grid :deep(.outbound-field-span .outbound-line-input) {
  max-width: min(640px, 100%);
}

.outbound-fields-form > .outbound-section-head:first-of-type {
  margin-top: 4px;
}

.outbound-section-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.02em;
  margin: 28px 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.outbound-section-head.outbound-section-full {
  margin-top: 32px;
}

.outbound-fields-form :deep(.el-input-number.num-wide) {
  width: 100%;
  /* 右侧步进条占宽，192px 时三位数易被裁切；略放宽以适配常见整数与毫秒级配置 */
  max-width: min(280px, 100%);
}

.preview-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  max-width: 420px;
}

.preview-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: contain;
  background: var(--el-fill-color);
}

.preview-mark {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  flex-shrink: 0;
  background: linear-gradient(145deg, #6366f1 0%, #4f46e5 55%, #4338ca 100%);
}

.preview-text {
  min-width: 0;
}

.preview-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.preview-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.model-calling-tip {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  white-space: pre-line;
}

.model-calling-fields-form .field-stack {
  width: 100%;
  max-width: min(720px, 100%);
}

.model-calling-fields-form .field-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.model-calling-fields-form .memory-embedding-select {
  width: 100%;
  max-width: min(520px, 100%);
}

.model-calling-fields-form .suffix-row {
  margin-bottom: 4px;
}

/* 模型对话卡片：长标签 + 数字框同列时避免过窄 */
.model-calling-fields-form .outbound-fields-grid {
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
}

.model-calling-fields-form .outbound-fields-grid :deep(.el-form-item) {
  max-width: none;
  width: 100%;
}

.site-crawl-block {
  width: 100%;
  margin-bottom: 8px;
}

.site-crawl-block .outbound-section-head:first-child {
  margin-top: 4px;
}

.site-crawl-block :deep(.site-crawl-preset-cards) {
  margin-bottom: 12px;
}

.site-crawl-preset-hint {
  margin: 0 0 16px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.model-calling-fields-form .site-crawl-custom-fields .outbound-fields-grid {
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
}

.model-calling-fields-form .site-crawl-custom-fields .outbound-fields-grid :deep(.el-form-item) {
  max-width: none;
  width: 100%;
}

.mono-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 13px;
}

.cb-readonly-card :deep(.el-card__header) {
  padding: 14px 36px 14px 32px;
}

.cb-readonly-card :deep(.el-card__body) {
  padding: 18px 40px 32px 32px;
}

.cb-ro-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px 16px;
  width: 100%;
}

.cb-ro-tile {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  box-sizing: border-box;
  min-width: 0;
}

.cb-ro-tile-k {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  margin-bottom: 6px;
}

.cb-ro-tile-v {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  line-height: 1.5;
  word-break: break-word;
  font-variant-numeric: tabular-nums;
}
</style>
