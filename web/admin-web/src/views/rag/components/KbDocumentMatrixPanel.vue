<template>
  <div class="kb-dmx-root">
    <el-alert
      v-if="!vectorMilvusEnabled"
      type="warning"
      show-icon
      :closable="false"
      class="rag-cap-inline-alert"
      :title="t('views.kbMatrix.vecWarnTitle')"
      :description="t('views.kbMatrix.vecWarnDesc')"
    />
    <div class="docs-matrix">
            <aside class="docs-nav" v-loading="loadingCategories">
              <el-button type="primary" class="new-cat-btn" @click="openCategoryCreate">{{ t("views.kbMatrix.newCategory") }}</el-button>
              <nav class="cat-nav">
                <button
                  type="button"
                  class="cat-item"
                  :class="{ 'is-active': selectedCategoryId === null }"
                  @click="selectCategoryFilter(null)"
                >
                  {{ t("views.kbMatrix.allCategories") }}
                </button>
                <div
                  v-for="c in categories"
                  :key="c.id"
                  class="cat-row"
                  :class="{ 'is-active': selectedCategoryId === c.id }"
                >
                  <button type="button" class="cat-item cat-item-grow" @click="selectCategoryFilter(c.id)">
                    {{ c.name }}
                  </button>
                  <el-dropdown trigger="click" @command="(cmd: string) => onCategoryRowCommand(cmd, c)">
                    <el-button class="cat-more" text type="primary" size="small">···</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">{{ t("views.kbMatrix.edit") }}</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>{{ t("views.kbMatrix.delete") }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </nav>
            </aside>

            <div class="docs-main">
              <div class="docs-toolbar">
                <div class="docs-toolbar-left">
                  <el-input
                    v-model="queryTitle"
                    :placeholder="t('views.kbMatrix.docTitlePh')"
                    clearable
                    class="q-title"
                    @keyup.enter="runDocQuery"
                  />
                  <el-select v-model="queryDisplayStatus" :placeholder="t('views.kbMatrix.statusPh')" clearable class="q-status">
                    <el-option :label="t('views.kbMatrix.statusPublished')" value="PUBLISHED" />
                    <el-option :label="t('views.kbMatrix.statusParsing')" value="PARSING" />
                    <el-option :label="t('views.kbMatrix.statusFailed')" value="PARSE_FAILED" />
                  </el-select>
                  <el-button @click="resetDocQuery">{{ t("views.kbMatrix.reset") }}</el-button>
                  <el-button type="primary" @click="runDocQuery">{{ t("views.kbMatrix.query") }}</el-button>
                </div>
                <div class="docs-toolbar-right">
                  <el-button
                    type="warning"
                    plain
                    :loading="indexingLoading"
                    :disabled="!vectorMilvusEnabled"
                    @click="triggerIndex"
                  >
                    {{ t("views.kbMatrix.triggerIndex") }}
                  </el-button>
                  <el-button plain @click="openWebCrawlProgressDlg">{{ t("views.kbMatrix.webCrawlProgressBtn") }}</el-button>
                  <el-button plain :disabled="!vectorMilvusEnabled" @click="openRetrievalTestDlg">
                    {{ t("views.kbMatrix.retrievalTestBtn") }}
                  </el-button>
                  <el-button :loading="loadingDocPage || loadingCategories" @click="refreshDocs">{{ t("views.kbMatrix.refresh") }}</el-button>
                  <el-button type="primary" :disabled="!vectorMilvusEnabled" @click="openIngest">{{ t("views.kbMatrix.uploadIngest") }}</el-button>
                </div>
              </div>

              <div ref="docsTableWrapRef" class="docs-table-wrap">
                <el-table
                  ref="docTableRef"
                  v-loading="loadingDocPage"
                  :data="docRows"
                  class="docs-table"
                  border
                  stripe
                  :height="docTableBodyHeight"
                  :empty-text="t('views.kbMatrix.emptyDocs')"
                  @selection-change="onDocSelectionChange"
                >
                <el-table-column type="selection" width="48" align="center" />
                <el-table-column :label="t('views.kbMatrix.colDocTitle')" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <router-link class="doc-title-link" :to="docChunksRoute(row)">
                      {{ row.title || t("views.kbMatrix.noTitle") }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colHits')" width="88" align="right">
                  <template #default="{ row }">{{ row.hitCount ?? 0 }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colScope')" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.applicableScope || emDash }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colUploader')" width="120" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.uploadedByLabel || emDash }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colUpdated')" width="172">
                  <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colStatus')" width="112" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.displayStatus === 'PUBLISHED'" type="success" size="small">{{
                      t("views.kbMatrix.statusPublished")
                    }}</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSING'" type="warning" size="small">{{
                      t("views.kbMatrix.statusParsing")
                    }}</el-tag>
                    <el-tag v-else-if="row.displayStatus === 'PARSE_FAILED'" type="danger" size="small">{{
                      t("views.kbMatrix.statusFailed")
                    }}</el-tag>
                    <el-tag v-else size="small">{{ ragDocumentDisplayStatusLabel(row.displayStatus, t) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('views.kbMatrix.colActions')" width="300" align="right" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.displayStatus === 'PUBLISHED' || !row.displayStatus">
                      <el-button link type="primary" size="small" @click="openDocSettings(row)">{{ t("views.kbMatrix.settings") }}</el-button>
                      <el-button link type="primary" size="small" @click="openIngest">{{ t("views.kbMatrix.updateDoc") }}</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">{{ t("views.kbMatrix.removeDoc") }}</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">{{ t("views.kbMatrix.download") }}</el-button>
                      <el-button link type="primary" size="small" @click="openChunksDrawer(row)">{{ t("views.kbMatrix.chunks") }}</el-button>
                    </template>
                    <template v-else-if="row.displayStatus === 'PARSE_FAILED'">
                      <el-button link type="primary" size="small" @click="openIngest">{{ t("views.kbMatrix.reupload") }}</el-button>
                      <el-button link type="danger" size="small" @click="removeDoc(row)">{{ t("views.kbMatrix.removeDoc") }}</el-button>
                      <el-button link type="primary" size="small" @click="downloadDocMarkdown(row)">{{ t("views.kbMatrix.download") }}</el-button>
                    </template>
                    <span v-else class="muted">{{ emDash }}</span>
                  </template>
                </el-table-column>
                </el-table>
              </div>

              <div class="docs-footer">
                <div class="docs-footer-left">
                  <span class="doc-range-text">{{ t("views.kbMatrix.rangeText", { range: docRangeText, total: docPageTotal }) }}</span>
                  <el-button v-if="selectedDocs.length" link type="danger" size="small" @click="batchRemoveDocs">
                    {{ t("views.kbMatrix.batchDelete") }}
                  </el-button>
                </div>
                <el-pagination
                  v-model:current-page="docPageCurrent"
                  v-model:page-size="docPageSize"
                  :total="docPageTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="sizes, prev, pager, next, jumper"
                  background
                  @current-change="loadDocPage"
                  @size-change="onDocPageSizeChange"
                />
              </div>
            </div>
    </div>

    <el-drawer v-model="ingestOpen" :title="t('views.kbMatrix.ingestDrawerTitle')" size="640px" destroy-on-close @closed="resetIngestForm">
      <el-radio-group v-model="ingestType" class="ingest-type">
        <el-radio-button label="crawl">{{ t("views.kbMatrix.ingestTabCrawl") }}</el-radio-button>
        <el-radio-button label="upload">{{ t("views.kbMatrix.ingestTabUpload") }}</el-radio-button>
        <el-radio-button label="paste">{{ t("views.kbMatrix.ingestTabPaste") }}</el-radio-button>
      </el-radio-group>
      <p class="ingest-tip">{{ ingestTip }}</p>

      <el-form label-width="108px" class="ingest-form">
        <template v-if="ingestType === 'crawl'">
          <el-form-item :label="t('views.kbMatrix.crawlModeLabel')" class="crawl-mode-form-item">
            <KbCrawlModePicker v-model="crawlForm.mode" />
          </el-form-item>
          <template v-if="crawlForm.mode === 'single'">
            <el-form-item class="crawl-intro-form-item" :label-width="0">
              <el-alert type="info" show-icon :closable="false" class="crawl-warn">
                {{ t("views.kbMatrix.singleCrawlIntro") }}
              </el-alert>
            </el-form-item>
            <el-form-item :label="t('views.kbMatrix.labelWebUrl')" required>
              <el-input v-model="crawlForm.url" :placeholder="t('views.ingest.urlPh')" type="url" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item class="crawl-intro-form-item" :label-width="0">
              <el-alert type="info" show-icon :closable="false" class="crawl-warn">
                {{ t("views.kbMatrix.webCrawlIntro") }}
              </el-alert>
            </el-form-item>
            <el-form-item v-if="crawlSites.length > 0" :label="t('views.kbMatrix.labelLoadSaved')">
              <el-select
                v-model="crawlForm.siteId"
                clearable
                filterable
                style="width: 100%"
                :placeholder="t('views.kbMatrix.labelLoadSavedPh')"
                @change="onRecurringSitePick"
                @clear="resetRecurringSiteForm"
              >
                <el-option v-for="s in crawlSites" :key="s.id" :label="savedSiteOptionLabel(s)" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item required>
              <template #label>
                <KbFormLabelTip
                  :label="t('views.kbMatrix.labelWebsiteUrl')"
                  :tip="t('views.kbMatrix.labelWebsiteUrlHint')"
                />
              </template>
              <el-input v-model="crawlForm.baseUrl" :placeholder="t('views.kbMatrix.siteBaseUrlPh')" type="url" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <KbFormLabelTip
                  :label="t('views.kbMatrix.labelCrawlLayers')"
                  :tip="t('views.kbMatrix.labelCrawlLayersHint')"
                />
              </template>
              <el-input-number v-model="crawlForm.maxDepth" :min="1" :max="8" controls-position="right" />
            </el-form-item>
            <el-form-item :label="t('views.kbMatrix.labelTaskNote')">
              <el-input v-model="crawlForm.name" maxlength="128" :placeholder="t('views.kbMatrix.labelTaskNotePh')" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <KbFormLabelTip :label="t('views.kbMatrix.labelRunNow')" :tip="t('views.kbMatrix.labelRunNowHint')" />
              </template>
              <el-switch v-model="crawlForm.runNow" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <KbFormLabelTip
                  :label="t('views.kbMatrix.labelAutoRepeat')"
                  :tip="t('views.kbMatrix.labelAutoRepeatHint')"
                />
              </template>
              <el-switch v-model="crawlForm.autoRepeat" />
            </el-form-item>
            <template v-if="crawlForm.autoRepeat">
              <el-form-item :label="t('views.kbMatrix.labelRepeatEvery')">
                <el-select v-model="crawlForm.schedulePreset" style="width: 100%">
                  <el-option v-for="o in crawlSchedulePresets" :key="o.code" :label="o.label" :value="o.code" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('views.kbMatrix.labelRepeatAt')">
                <el-time-picker
                  v-model="crawlForm.runAtTime"
                  format="HH:mm"
                  value-format="HH:mm"
                  style="width: 100%"
                  clearable
                />
              </el-form-item>
            </template>
            <el-form-item :label="t('views.kbMatrix.ingestLabelCategory')">
              <el-select
                v-model="ingestCategoryId"
                clearable
                filterable
                :placeholder="t('views.chunks.categoryPh')"
                style="width: 100%"
              >
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <KbFormLabelTip
                  :label="t('views.ingest.labelChunkOverride')"
                  :tip="t('views.kbMatrix.chunkStrategyWebHint')"
                />
              </template>
              <el-select
                v-model="ingestChunkStrategy"
                clearable
                :placeholder="t('views.ingest.chunkDefaultPh')"
                style="width: 100%"
              >
                <el-option :label="t('views.ingest.chunk0')" :value="0" />
                <el-option :label="t('views.ingest.chunk1')" :value="1" />
                <el-option :label="t('views.ingest.chunk2')" :value="2" />
                <el-option :label="t('views.ingest.chunk3')" :value="3" />
                <el-option :label="t('views.ingest.chunk4')" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <template #label>
                <KbFormLabelTip
                  :label="t('views.kbMatrix.labelOnlyNewPages')"
                  :tip="t('views.kbMatrix.labelOnlyNewPagesHint')"
                />
              </template>
              <el-switch v-model="crawlForm.filterCrawled" />
            </el-form-item>
          </template>
        </template>
        <template v-else-if="ingestType === 'upload'">
          <el-form-item :label="t('views.kbMatrix.labelPickFile')" required>
            <el-upload
              class="ingest-upload-drop"
              drag
              multiple
              :auto-upload="false"
              :file-list="uploadFileList"
              :on-change="onUploadFileChange"
              :on-remove="onUploadFileRemove"
              accept=".txt,.md,.pdf,.doc,.docx,.html,.htm"
            >
              <el-icon class="ingest-upload-ico"><UploadFilled /></el-icon>
              <div class="el-upload__text">{{ t("views.kbMatrix.uploadDragHint") }}</div>
              <template #tip>
                <div class="el-upload__tip">{{ t("views.kbMatrix.uploadDragTip") }}</div>
              </template>
            </el-upload>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="t('views.ingest.labelFilename')" required>
            <el-input v-model="pasteForm.originalFilename" :placeholder="t('views.ingest.filenamePh')" />
          </el-form-item>
          <el-form-item :label="t('views.ingest.labelContentType')">
            <el-input v-model="pasteForm.contentType" :placeholder="t('views.ingest.ctPh')" />
          </el-form-item>
          <el-form-item :label="t('views.ingest.labelMd')">
            <el-input v-model="pasteForm.markdownContent" type="textarea" :rows="8" :placeholder="t('views.ingest.mdPh')" />
          </el-form-item>
        </template>

        <template v-if="ingestType !== 'crawl' || crawlForm.mode === 'single'">
          <el-form-item :label="t('views.kbMatrix.ingestLabelCategory')">
            <el-select
              v-model="ingestCategoryId"
              clearable
              filterable
              :placeholder="t('views.chunks.categoryPh')"
              style="width: 100%"
            >
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('views.ingest.labelChunkOverride')">
            <el-select v-model="ingestChunkStrategy" clearable :placeholder="t('views.ingest.chunkDefaultPh')" style="width: 100%">
              <el-option :label="t('views.ingest.chunk0')" :value="0" />
              <el-option :label="t('views.ingest.chunk1')" :value="1" />
              <el-option :label="t('views.ingest.chunk2')" :value="2" />
              <el-option :label="t('views.ingest.chunk3')" :value="3" />
              <el-option :label="t('views.ingest.chunk4')" :value="4" />
            </el-select>
            <p v-if="ingestType === 'upload' || ingestType === 'paste'" class="field-hint">
              {{ t("views.kbMatrix.chunkStrategyUploadHint") }}
            </p>
          </el-form-item>
        </template>

        <el-form-item class="ingest-submit-row">
          <div class="ingest-submit-actions">
            <el-button
              v-if="ingestType === 'crawl'"
              :loading="chunkPreviewLoading"
              @click="runChunkPreview"
            >
              {{ t("views.kbMatrix.chunkPreviewBtn") }}
            </el-button>
            <el-button type="primary" class="accent-btn" :loading="ingestSubmitting" @click="submitIngest">{{
              ingestSubmitLabel
            }}</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-drawer>

    <el-dialog v-model="chunkDlg" :title="t('views.chunks.dlgEditChunk')" width="800px" destroy-on-close @closed="onChunkDlgClosed">
      <el-tabs v-model="chunkEditTab" class="chunk-edit-tabs">
        <el-tab-pane :label="t('views.chunks.tabChunkPreview')" name="preview">
          <el-scrollbar class="chunk-edit-scrollbar" max-height="min(62vh, 520px)">
            <div class="chunk-edit-preview chunk-md" v-html="chunkEditMarkdownHtml" />
          </el-scrollbar>
        </el-tab-pane>
        <el-tab-pane :label="t('views.chunks.tabChunkSource')" name="source">
          <el-input v-model="chunkEditText" type="textarea" :rows="16" class="chunk-edit-source" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="chunkDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="saveChunk">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="chunksDrawerOpen"
      :title="chunksDrawerTitle"
      size="680px"
      destroy-on-close
      @closed="onChunksDrawerClosed"
    >
      <div v-if="chunksDrawerDoc" class="chunks-drawer-bar">
        <el-button size="small" type="primary" plain :loading="chunksDrawerLoading" @click="loadChunksInDrawer">
          {{ t("views.kbMatrix.chunksLoadBtn") }}
        </el-button>
      </div>
      <el-table
        v-if="chunksDrawerDoc && chunksDrawerRows.length"
        :data="chunksDrawerRows"
        size="small"
        border
        class="chunks-drawer-table"
      >
        <el-table-column prop="seq" label="#" width="52" />
        <el-table-column :label="t('views.kbMatrix.colHits')" width="72" align="right">
          <template #default="{ row: c }">{{ c.hitCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column :label="t('views.kbMatrix.colPreview')" min-width="220">
          <template #default="{ row: c }">{{ preview(c.content) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('views.kbMatrix.colCreatedShort')" width="156" />
        <el-table-column :label="t('views.kbMatrix.colActions')" width="88" align="center">
          <template #default="{ row: c }">
            <el-button link type="primary" size="small" @click="openChunkEditFromDrawer(c)">{{ t("views.kbMatrix.edit") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else-if="chunksDrawerDoc"
        :description="t('views.kbMatrix.chunksEmptyHint')"
        :image-size="56"
      />
    </el-drawer>

    <el-dialog v-model="docSettingsDlg" :title="t('views.kbMatrix.docSettingsTitle')" width="520px" destroy-on-close @closed="docSettingsRow = null">
      <el-form v-if="docSettingsRow" label-width="100px">
        <el-form-item :label="t('views.chunks.categoryLabel')">
          <el-select v-model="docSettingsCategoryId" clearable :placeholder="t('views.chunks.categoryPh')" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('views.chunks.scopeLabel')">
          <el-input v-model="docSettingsScope" type="textarea" :rows="3" :placeholder="t('views.kbMatrix.scopeOptionalPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="docSettingsDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="docSettingsSaving" @click="saveDocSettings">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>


    <el-dialog
      v-model="retrievalTestDlgOpen"
      :title="t('views.kbMatrix.retrievalTestDlgTitle')"
      width="760px"
      append-to-body
      class="retrieval-test-dlg"
      destroy-on-close
      @closed="onRetrievalTestDlgClosed"
    >
      <p class="jobs-dlg-hint">{{ t("views.kbMatrix.retrievalTestDlgHint") }}</p>
      <el-form label-width="88px" @submit.prevent="runRetrievalTest">
        <el-form-item :label="t('views.kbMatrix.retrievalQueryLabel')" required>
          <el-input
            v-model="retrievalQuery"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            :placeholder="t('views.kbMatrix.retrievalQueryPh')"
          />
        </el-form-item>
        <el-form-item :label="t('views.kbMatrix.retrievalTopKLabel')">
          <el-input-number v-model="retrievalTopK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="retrievalTestLoading" @click="runRetrievalTest">
            {{ t("views.kbMatrix.retrievalRunBtn") }}
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="retrievalTestResult" class="retrieval-result-wrap">
        <el-descriptions :column="2" border size="small" class="retrieval-meta">
          <el-descriptions-item :label="t('views.kbMatrix.retrievalModeLabel')">
            {{ retrievalModeLabel(retrievalTestResult.retrievalMode) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('views.kbMatrix.retrievalHitCountLabel')">
            {{ retrievalTestResult.hitCount }}
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="retrievalTestResult.hits.length" class="retrieval-section">
          <div class="job-section-title">{{ t("views.kbMatrix.retrievalHitsTitle") }}</div>
          <el-table :data="retrievalTestResult.hits" size="small" stripe border max-height="280">
            <el-table-column :label="t('views.kbMatrix.retrievalColDoc')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.documentTitle || emDash }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.retrievalColChunk')" width="88" align="center">
              <template #default="{ row }">#{{ row.chunkSeq + 1 }}</template>
            </el-table-column>
            <el-table-column :label="t('views.kbMatrix.colPreview')" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contentPreview || emDash }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="retrievalTestResult.snippets.length" class="retrieval-section">
          <div class="job-section-title">{{ t("views.kbMatrix.retrievalSnippetsTitle") }}</div>
          <ul class="retrieval-snippet-list">
            <li v-for="(s, si) in retrievalTestResult.snippets" :key="'sn-' + si">{{ s }}</li>
          </ul>
        </div>
        <el-empty v-else-if="!retrievalTestResult.hits.length" :description="t('views.kbMatrix.retrievalEmpty')" />
      </div>
    </el-dialog>

    <el-dialog
      v-model="categoryDlg"
      :title="categoryDlgMode === 'create' ? t('views.kbMatrix.categoryDlgNew') : t('views.kbMatrix.categoryDlgEdit')"
      width="420px"
      destroy-on-close
      @closed="onCategoryDlgClosed"
    >
      <el-form label-width="80px">
        <el-form-item :label="t('views.kbMatrix.labelCatName')" required>
          <el-input v-model="categoryForm.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('views.kbMatrix.labelSort')">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDlg = false">{{ t("views.kbMatrix.formCancel") }}</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategoryDlg">{{ t("views.kbMatrix.formSave") }}</el-button>
      </template>
    </el-dialog>

    <KbWebCrawlProgressDialog v-model="webCrawlProgressDlgOpen" :kb-id="kid" />
    <KbChunkPreviewDialog ref="chunkPreviewRef" v-model="chunkPreviewDlgOpen" :kb-id="kid" />
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
import KbChunkPreviewDialog from "./KbChunkPreviewDialog.vue";
import KbCrawlModePicker from "./KbCrawlModePicker.vue";
import KbFormLabelTip from "./KbFormLabelTip.vue";
import KbWebCrawlProgressDialog from "./KbWebCrawlProgressDialog.vue";
import type { RagWebCrawlSiteRow } from "../../../api/ragAdmin";
import { ElMessage, ElMessageBox } from "element-plus";
import type { UploadFile } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import * as ragApi from "../../../api/ragAdmin";
import type { RagChunkAdminRow, RagDocumentAdminRow, RagDocumentCategoryAdminRow } from "../../../types/admin";
import type { RagRetrievalTestResult } from "../../../api/ragAdmin";
import { renderMarkdownToSafeHtml } from "../../../utils/renderMarkdown";
import { ragDocumentDisplayStatusLabel } from "../../../utils/ragJobDisplay";

const { t } = useI18n();
const emDash = "\u2014";

/** 上传/粘贴入库前：长文档等特征命中时询问是否改用子母分片。 */
async function resolveUploadChunkStrategy(
  kbId: number,
  baseStrategy: number | undefined,
  source: { file?: File; markdown?: string },
): Promise<number | undefined> {
  if (baseStrategy === ragApi.RAG_CHUNK_STRATEGY_PARENT_CHILD) {
    return baseStrategy;
  }
  try {
    const analysis =
      source.file != null
        ? await ragApi.analyzeIngestUpload(kbId, source.file)
        : source.markdown != null && source.markdown.trim()
          ? await ragApi.analyzeIngestMarkdown(kbId, source.markdown)
          : null;
    if (analysis == null || !analysis.suggestParentChild) {
      return baseStrategy;
    }
    const reasonLines =
      analysis.reasons.length > 0
        ? `\n\n${analysis.reasons.map((r) => `· ${r}`).join("\n")}`
        : "";
    const label = source.file?.name ?? t("views.kbMatrix.parentChildPasteLabel");
    await ElMessageBox.confirm(
      `${t("views.kbMatrix.parentChildSuggestMsg", { name: label })}${reasonLines}`,
      t("views.kbMatrix.parentChildSuggestTitle"),
      {
        confirmButtonText: t("views.kbMatrix.parentChildYes"),
        cancelButtonText: t("views.kbMatrix.parentChildNo"),
        type: "info",
      },
    );
    return ragApi.RAG_CHUNK_STRATEGY_PARENT_CHILD;
  } catch {
    return baseStrategy;
  }
}

/** 鐭ヨ瘑搴撴枃妗ｅ鍑?Markdown 鐨勬湰鍦版枃浠跺悕锛氬幓鎺夊父瑙佹簮鏂囦欢鍚庣紑锛岄伩鍏嶅嚭鐜般€屾姤鍛?doc.md銆嶃€?*/
function filenameForMarkdownExport(title: string | null | undefined): string {
  let base = (title || t("views.kbMatrix.docFallback")).trim().replace(/[/\\?%*:|"<>]/g, "_").slice(0, 120);
  if (!base) base = t("views.kbMatrix.docFallback");
  const lower = base.toLowerCase();
  if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
    return base;
  }
  const stripped = base.replace(
    /\.(docx?|pdf|html?|txt|rtf|pptx?|xlsx?|csv|json|xml|epub|odt|pages)$/i,
    "",
  );
  const root = stripped.trim() || t("views.kbMatrix.docFallback");
  return `${root}.md`;
}

const props = defineProps<{ kbId: number; vectorMilvusEnabled?: boolean }>();
const kid = computed(() => props.kbId);
/** 鏈紶鏃堕粯璁ゅ彲鐢紝閬垮厤鍏跺畠鍏ュ彛璇激锛涚煡璇嗗簱棣栭〉浼氭樉寮忎紶鍏ャ€?*/
const vectorMilvusEnabled = computed(() => props.vectorMilvusEnabled !== false);

const loadingDocPage = ref(false);
const loadingCategories = ref(false);
const indexingLoading = ref(false);

const docRows = ref<RagDocumentAdminRow[]>([]);
const docPageCurrent = ref(1);
const docPageSize = ref(10);
const docPageTotal = ref(0);
const categories = ref<RagDocumentCategoryAdminRow[]>([]);
const selectedCategoryId = ref<number | null>(null);
const queryTitle = ref("");
const queryDisplayStatus = ref<string | undefined>(undefined);
const selectedDocs = ref<RagDocumentAdminRow[]>([]);
const docTableRef = ref<{ clearSelection: () => void } | null>(null);
const docsTableWrapRef = ref<HTMLElement | null>(null);
/** 渚?el-table 鍥哄畾楂樺害锛屼娇鏃犳暟鎹椂琛ㄤ綋鍖哄煙浠嶅崰婊″墿浣欑┖闂?*/
const docTableBodyHeight = ref(360);
let docTableResizeObserver: ResizeObserver | null = null;

const chunksByDoc = ref<Record<number, RagChunkAdminRow[]>>({});
const chunksDrawerOpen = ref(false);
const chunksDrawerDoc = ref<RagDocumentAdminRow | null>(null);
const chunksDrawerLoading = ref(false);

const docSettingsDlg = ref(false);
const docSettingsRow = ref<RagDocumentAdminRow | null>(null);
const docSettingsCategoryId = ref<number | undefined>(undefined);
const docSettingsScope = ref("");
const docSettingsSaving = ref(false);

const categoryDlg = ref(false);
const categoryDlgMode = ref<"create" | "edit">("create");
const categoryEditingId = ref<number | null>(null);
const categoryForm = reactive({ name: "", sortOrder: 0 });
const categorySaving = ref(false);

const chunkDlg = ref(false);
const chunkEditTab = ref<"preview" | "source">("preview");
const chunkEditText = ref("");
const chunkSaving = ref(false);
const editingChunk = ref<{ doc: RagDocumentAdminRow; chunk: RagChunkAdminRow } | null>(null);

const chunkEditMarkdownHtml = computed(() => renderMarkdownToSafeHtml(chunkEditText.value || ""));

const webCrawlProgressDlgOpen = ref(false);

const retrievalTestDlgOpen = ref(false);
const retrievalQuery = ref("");
const retrievalTopK = ref(8);
const retrievalTestLoading = ref(false);
const retrievalTestResult = ref<RagRetrievalTestResult | null>(null);

const ingestOpen = ref(false);
const ingestType = ref<"crawl" | "upload" | "paste">("crawl");
const ingestChunkStrategy = ref<number | undefined>(2);
const chunkPreviewDlgOpen = ref(false);
const chunkPreviewRef = ref<InstanceType<typeof KbChunkPreviewDialog> | null>(null);
const chunkPreviewLoading = ref(false);
const crawlForm = reactive({
  mode: "recurring" as "single" | "recurring",
  url: "",
  siteId: null as number | null,
  name: "",
  baseUrl: "",
  schedulePreset: "DAILY",
  runAtTime: undefined as string | undefined,
  maxDepth: 3,
  filterCrawled: true,
  runNow: true,
  autoRepeat: false,
});
const crawlSchedulePresets = ref<{ code: string; label: string }[]>([]);
const crawlSites = ref<RagWebCrawlSiteRow[]>([]);
const pasteForm = reactive({ originalFilename: "", contentType: "", markdownContent: "" });
const uploadFileList = ref<UploadFile[]>([]);
const ingestCategoryId = ref<number | undefined>(undefined);
const ingestSubmitting = ref(false);

const docRangeText = computed(() => {
  if (docPageTotal.value <= 0) return "0-0";
  const start = (docPageCurrent.value - 1) * docPageSize.value + 1;
  const end = Math.min(docPageCurrent.value * docPageSize.value, docPageTotal.value);
  return `${start}-${end}`;
});

const chunksDrawerTitle = computed(() =>
  chunksDrawerDoc.value
    ? t("views.kbMatrix.chunksTitle", {
        title: chunksDrawerDoc.value.title || t("views.kbMatrix.docFallback"),
      })
    : t("views.kbMatrix.chunksTitleFallback"),
);

const chunksDrawerRows = computed(() => {
  const d = chunksDrawerDoc.value;
  if (!d) return [];
  return chunksByDoc.value[d.id] ?? [];
});

const ingestTip = computed(() => {
  if (ingestType.value === "crawl" && crawlForm.mode === "recurring") {
    return "";
  }
  if (ingestType.value === "crawl") return "";
  if (ingestType.value === "upload") return t("views.kbMatrix.ingestTipUpload");
  return t("views.kbMatrix.ingestTipPaste");
});

const ingestSubmitLabel = computed(() => {
  if (ingestType.value === "crawl" && crawlForm.mode === "recurring") {
    return crawlForm.runNow ? t("views.kbMatrix.submitStartCrawl") : t("views.kbMatrix.submitSaveCrawlTask");
  }
  if (ingestType.value === "crawl") return t("views.kbMatrix.submitSinglePage");
  return t("views.kbMatrix.submitIngest");
});

function defaultSiteNameFromUrl(raw: string): string {
  try {
    return new URL(raw.trim()).hostname;
  } catch {
    return raw.trim().slice(0, 64);
  }
}

function savedSiteOptionLabel(s: RagWebCrawlSiteRow): string {
  return s.name?.trim() ? `${s.name} · ${s.baseUrl}` : s.baseUrl;
}

function docChunksRoute(row: RagDocumentAdminRow) {
  return {
    path: `/knowledge-center/workspace/${kid.value}/documents/${row.id}/chunks`,
    query: { docTitle: row.title || "" },
  };
}

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function preview(s: string): string {
  const t = (s || "").replace(/\s+/g, " ");
  return t.length > 160 ? `${t.slice(0, 160)}...` : t;
}

async function triggerIndex() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusWarnIdx"));
    return;
  }
  indexingLoading.value = true;
  try {
    await ragApi.enqueueRagKbIndexJob(kid.value);
    ElMessage.success(t("views.kbMatrix.indexQueued"));
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.enqueueFailed");
    ElMessage.error(msg);
  } finally {
    indexingLoading.value = false;
  }
}

async function loadCategories() {
  loadingCategories.value = true;
  try {
    categories.value = await ragApi.fetchRagKbDocumentCategories(kid.value);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadCatFailed");
    ElMessage.error(msg);
  } finally {
    loadingCategories.value = false;
  }
}

async function loadDocPage() {
  loadingDocPage.value = true;
  try {
    const p = await ragApi.fetchRagKbDocumentsPage(kid.value, {
      page: docPageCurrent.value,
      size: docPageSize.value,
      categoryId: selectedCategoryId.value ?? undefined,
      displayStatus: queryDisplayStatus.value,
      titleKeyword: queryTitle.value.trim() || undefined,
    });
    docRows.value = p.records ?? [];
    docPageTotal.value = p.total ?? 0;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadDocFailed");
    ElMessage.error(msg);
  } finally {
    loadingDocPage.value = false;
    void nextTick().then(() => bindDocTableResize());
  }
}

function openWebCrawlProgressDlg() {
  webCrawlProgressDlgOpen.value = true;
}

function retrievalModeLabel(mode: string): string {
  if (mode === "milvus_es_hybrid") return t("views.kbMatrix.retrievalModeHybrid");
  if (mode === "milvus") return t("views.kbMatrix.retrievalModeMilvus");
  return mode;
}

function openRetrievalTestDlg() {
  retrievalTestDlgOpen.value = true;
}

function onRetrievalTestDlgClosed() {
  retrievalTestResult.value = null;
}

async function runRetrievalTest() {
  const q = retrievalQuery.value.trim();
  if (!q) {
    ElMessage.warning(t("views.kbMatrix.retrievalQueryRequired"));
    return;
  }
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusWarnIdx"));
    return;
  }
  retrievalTestLoading.value = true;
  try {
    retrievalTestResult.value = await ragApi.testRagKbRetrieval(kid.value, {
      query: q,
      topK: retrievalTopK.value,
    });
  } catch {
    ElMessage.error(t("views.kbMatrix.retrievalTestFailed"));
  } finally {
    retrievalTestLoading.value = false;
  }
}

/** 浠呮枃妗ｄ笌鍒嗙被锛堜富鍖哄煙銆屽埛鏂般€嶏級銆?*/
async function refreshDocs() {
  await Promise.all([loadCategories(), loadDocPage()]);
}

function selectCategoryFilter(id: number | null) {
  selectedCategoryId.value = id;
  docPageCurrent.value = 1;
  void loadDocPage();
}

function resetDocQuery() {
  queryTitle.value = "";
  queryDisplayStatus.value = undefined;
  docPageCurrent.value = 1;
  void loadDocPage();
}

function runDocQuery() {
  docPageCurrent.value = 1;
  void loadDocPage();
}

function onDocPageSizeChange() {
  docPageCurrent.value = 1;
  void loadDocPage();
}

function onDocSelectionChange(rows: RagDocumentAdminRow[]) {
  selectedDocs.value = rows;
}

function openCategoryCreate() {
  categoryDlgMode.value = "create";
  categoryEditingId.value = null;
  categoryForm.name = "";
  categoryForm.sortOrder = 0;
  categoryDlg.value = true;
}

function onCategoryDlgClosed() {
  categoryEditingId.value = null;
}

async function onCategoryRowCommand(cmd: string, c: RagDocumentCategoryAdminRow) {
  if (cmd === "edit") {
    categoryDlgMode.value = "edit";
    categoryEditingId.value = c.id;
    categoryForm.name = c.name;
    categoryForm.sortOrder = c.sortOrder ?? 0;
    categoryDlg.value = true;
    return;
  }
  if (cmd === "delete") {
    try {
      await ElMessageBox.confirm(t("views.kbMatrix.deleteCatConfirm", { name: c.name }), t("views.menuItems.confirm"), {
        type: "warning",
      });
      await ragApi.deleteRagKbDocumentCategory(kid.value, c.id);
      ElMessage.success(t("views.kbMatrix.deleted"));
      if (selectedCategoryId.value === c.id) {
        selectedCategoryId.value = null;
      }
      await loadCategories();
      await loadDocPage();
    } catch {
      /* cancel or conflict */
    }
  }
}

async function saveCategoryDlg() {
  const name = categoryForm.name.trim();
  if (!name) {
    ElMessage.warning(t("views.kbMatrix.fillCatName"));
    return;
  }
  categorySaving.value = true;
  try {
    if (categoryDlgMode.value === "create") {
      await ragApi.createRagKbDocumentCategory(kid.value, { name, sortOrder: categoryForm.sortOrder });
      ElMessage.success(t("views.kbMatrix.catCreated"));
    } else if (categoryEditingId.value != null) {
      await ragApi.updateRagKbDocumentCategory(kid.value, categoryEditingId.value, {
        name,
        sortOrder: categoryForm.sortOrder,
      });
      ElMessage.success(t("views.kbMatrix.catSaved"));
    }
    categoryDlg.value = false;
    await loadCategories();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    categorySaving.value = false;
  }
}

function openDocSettings(row: RagDocumentAdminRow) {
  docSettingsRow.value = row;
  docSettingsCategoryId.value = row.categoryId ?? undefined;
  docSettingsScope.value = row.applicableScope ?? "";
  docSettingsDlg.value = true;
}

async function saveDocSettings() {
  const row = docSettingsRow.value;
  if (!row) return;
  docSettingsSaving.value = true;
  try {
    const body =
      docSettingsCategoryId.value == null
        ? { clearCategory: true as const, applicableScope: docSettingsScope.value.trim() }
        : {
            categoryId: docSettingsCategoryId.value,
            applicableScope: docSettingsScope.value.trim(),
          };
    const updated = await ragApi.patchRagKbDocument(kid.value, row.id, body);
    const i = docRows.value.findIndex((x) => x.id === updated.id);
    if (i >= 0) docRows.value[i] = updated;
    ElMessage.success(t("views.kbMatrix.docSaved"));
    docSettingsDlg.value = false;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    docSettingsSaving.value = false;
  }
}

function openChunksDrawer(row: RagDocumentAdminRow) {
  chunksDrawerDoc.value = row;
  chunksDrawerOpen.value = true;
  void loadChunksInDrawer();
}

async function loadChunksInDrawer() {
  const d = chunksDrawerDoc.value;
  if (!d) return;
  chunksDrawerLoading.value = true;
  try {
    chunksByDoc.value[d.id] = (await ragApi.fetchRagKbChunks(kid.value, d.id)).chunks;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.loadFailed");
    ElMessage.error(msg);
  } finally {
    chunksDrawerLoading.value = false;
  }
}

function onChunksDrawerClosed() {
  chunksDrawerDoc.value = null;
}

function onChunkDlgClosed() {
  editingChunk.value = null;
  chunkEditTab.value = "preview";
}

function openChunkEditFromDrawer(chunk: RagChunkAdminRow) {
  const doc = chunksDrawerDoc.value;
  if (!doc) return;
  editingChunk.value = { doc, chunk };
  chunkEditText.value = chunk.content;
  chunkEditTab.value = "preview";
  chunkDlg.value = true;
}

async function downloadDocMarkdown(row: RagDocumentAdminRow) {
  try {
    const md = await ragApi.exportRagKbDocumentMarkdown(kid.value, row.id);
    const blob = new Blob([md], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filenameForMarkdownExport(row.title);
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.downloadFailed");
    ElMessage.error(msg);
  }
}

async function batchRemoveDocs() {
  const rows = selectedDocs.value;
  if (!rows.length) return;
  try {
    await ElMessageBox.confirm(t("views.kbMatrix.batchDeleteConfirm", { n: rows.length }), t("views.menuItems.confirm"), {
      type: "warning",
    });
    for (const d of rows) {
      await ragApi.deleteRagKbDocument(kid.value, d.id);
    }
    ElMessage.success(t("views.kbMatrix.deleted"));
    docTableRef.value?.clearSelection();
    await refreshDocs();
  } catch {
    /* cancel */
  }
}

async function openIngest() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusUploadDisabled"));
    return;
  }
  resetIngestForm();
  await loadCrawlMeta();
  ingestOpen.value = true;
}

async function loadCrawlMeta() {
  try {
    const meta = await ragApi.fetchWebCrawlSiteMeta();
    crawlSchedulePresets.value = meta.schedulePresets ?? [];
    crawlSites.value = await ragApi.fetchWebCrawlSites(kid.value);
  } catch {
    /* ignore */
  }
}

function applyRecurringSiteToForm(row: RagWebCrawlSiteRow) {
  crawlForm.siteId = row.id;
  crawlForm.name = row.name;
  crawlForm.baseUrl = row.baseUrl;
  crawlForm.schedulePreset = row.schedulePreset;
  crawlForm.runAtTime = row.runAtTime ?? undefined;
  crawlForm.maxDepth = row.maxDepth ?? 3;
  crawlForm.filterCrawled = row.filterCrawled;
  crawlForm.autoRepeat = row.enabled;
  crawlForm.runNow = false;
  if (row.chunkStrategy != null) ingestChunkStrategy.value = row.chunkStrategy;
  if (row.categoryId != null) ingestCategoryId.value = row.categoryId;
}

function resetRecurringSiteForm() {
  crawlForm.siteId = null;
  crawlForm.name = "";
  crawlForm.baseUrl = "";
  crawlForm.schedulePreset = "DAILY";
  crawlForm.runAtTime = undefined;
  crawlForm.maxDepth = 3;
  crawlForm.filterCrawled = true;
  crawlForm.runNow = true;
  crawlForm.autoRepeat = false;
}

function onRecurringSitePick(siteId: number | null) {
  if (siteId == null) {
    resetRecurringSiteForm();
    return;
  }
  const row = crawlSites.value.find((s) => s.id === siteId);
  if (row) applyRecurringSiteToForm(row);
}

function resetIngestForm() {
  ingestType.value = "crawl";
  ingestChunkStrategy.value = 2;
  ingestCategoryId.value = selectedCategoryId.value ?? undefined;
  crawlForm.mode = "recurring";
  crawlForm.url = "";
  resetRecurringSiteForm();
  pasteForm.originalFilename = "";
  pasteForm.contentType = "";
  pasteForm.markdownContent = "";
  uploadFileList.value = [];
}

function onUploadFileChange(_file: UploadFile, fileList: UploadFile[]) {
  uploadFileList.value = fileList;
}

function onUploadFileRemove(_file: UploadFile, fileList: UploadFile[]) {
  uploadFileList.value = fileList;
}

async function runChunkPreview() {
  if (ingestType.value !== "crawl") return;
  const cs = ingestChunkStrategy.value;
  chunkPreviewLoading.value = true;
  try {
    if (crawlForm.mode === "single") {
      const u = crawlForm.url.trim();
      if (!u) {
        ElMessage.warning(t("views.ingest.fillUrl"));
        return;
      }
      await chunkPreviewRef.value?.run({ url: u, chunkStrategy: cs });
    } else {
      const base = crawlForm.baseUrl.trim();
      if (!base) {
        ElMessage.warning(t("views.kbMatrix.fillSiteBaseUrl"));
        return;
      }
      await chunkPreviewRef.value?.run({
        baseUrl: base,
        maxDepth: crawlForm.maxDepth,
        chunkStrategy: cs,
        siteId: crawlForm.siteId ?? undefined,
      });
    }
  } finally {
    chunkPreviewLoading.value = false;
  }
}

async function submitIngest() {
  if (!vectorMilvusEnabled.value) {
    ElMessage.warning(t("views.kbMatrix.milvusSubmitDisabled"));
    return;
  }
  const cs = ingestChunkStrategy.value;
  const catId = ingestCategoryId.value;
  ingestSubmitting.value = true;
  try {
    if (ingestType.value === "crawl") {
      if (crawlForm.mode === "single") {
        const u = crawlForm.url.trim();
        if (!u) {
          ElMessage.warning(t("views.ingest.fillUrl"));
          return;
        }
        await ragApi.enqueueUrlImportJob(kid.value, u, cs, catId);
        ElMessage.success(t("views.kbMatrix.crawlJobCreated"));
      } else {
        const base = crawlForm.baseUrl.trim();
        if (!base) {
          ElMessage.warning(t("views.kbMatrix.fillSiteBaseUrl"));
          return;
        }
        if (!crawlForm.runNow && !crawlForm.autoRepeat) {
          ElMessage.warning(t("views.kbMatrix.crawlNeedRunOrRepeat"));
          return;
        }
        const name = crawlForm.name.trim() || defaultSiteNameFromUrl(base);
        const body = {
          name,
          baseUrl: base,
          syncMode: "FIRST_FULL_THEN_INCREMENTAL",
          schedulePreset: crawlForm.autoRepeat ? crawlForm.schedulePreset : "MANUAL",
          runAtTime: crawlForm.autoRepeat ? crawlForm.runAtTime : undefined,
          maxDepth: crawlForm.maxDepth,
          filterCrawled: crawlForm.filterCrawled,
          enabled: crawlForm.autoRepeat,
          chunkStrategy: cs,
          categoryId: catId,
        };
        let siteId = crawlForm.siteId;
        if (siteId != null) {
          await ragApi.updateWebCrawlSite(kid.value, siteId, body);
        } else {
          const created = await ragApi.createWebCrawlSite(kid.value, body);
          siteId = created.id;
        }
        const startedNow = crawlForm.runNow && siteId != null;
        if (startedNow) {
          await ragApi.runWebCrawlSiteNow(kid.value, siteId);
        }
        ingestOpen.value = false;
        ElMessage.success(
          startedNow ? t("views.kbMatrix.recurringCrawlSavedAndRun") : t("views.kbMatrix.webCrawlSitesSaved"),
        );
        if (startedNow) {
          openWebCrawlProgressDlg();
        }
        void refreshDocs();
        return;
      }
    } else if (ingestType.value === "upload") {
      const files = uploadFileList.value.map((item) => item.raw as File).filter((f): f is File => !!f);
      if (!files.length) {
        ElMessage.warning(t("views.kbMatrix.pickFileWarning"));
        return;
      }
      let ok = 0;
      let fail = 0;
      for (const f of files) {
        try {
          const strategy = await resolveUploadChunkStrategy(kid.value, cs, { file: f });
          await ragApi.uploadRagKbDocument(kid.value, f, strategy, catId);
          ok++;
        } catch {
          fail++;
        }
      }
      if (ok > 0 && fail === 0) {
        ElMessage.success(
          ok === 1 ? t("views.kbMatrix.uploadIngestDone") : t("views.kbMatrix.uploadIngestDoneMulti", { n: ok }),
        );
      } else if (ok > 0) {
        ElMessage.warning(t("views.kbMatrix.uploadPartialFailed", { ok, fail }));
      } else {
        ElMessage.error(t("views.kbMatrix.submitFailed"));
        return;
      }
    } else {
      const name = pasteForm.originalFilename.trim();
      if (!name) {
        ElMessage.warning(t("views.ingest.fillName"));
        return;
      }
      const md = pasteForm.markdownContent.trim();
      const strategy =
        md.length > 0 ? await resolveUploadChunkStrategy(kid.value, cs, { markdown: md }) : cs;
      await ragApi.enqueueFileIngestJob(kid.value, {
        originalFilename: name,
        contentType: pasteForm.contentType.trim() || undefined,
        markdownContent: md || undefined,
        chunkStrategy: strategy,
        categoryId: catId,
      });
      ElMessage.success(t("views.kbMatrix.fileJobCreated"));
    }
    ingestOpen.value = false;
    void refreshDocs();
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.submitFailed");
    ElMessage.error(msg);
  } finally {
    ingestSubmitting.value = false;
  }
}

async function removeDoc(row: RagDocumentAdminRow) {
  try {
    await ElMessageBox.confirm(
      t("views.kbMatrix.deleteDocConfirm", { title: row.title || t("views.kbMatrix.noTitle") }),
      t("views.menuItems.confirm"),
      { type: "warning" },
    );
    await ragApi.deleteRagKbDocument(kid.value, row.id);
    ElMessage.success(t("views.kbMatrix.deleted"));
    delete chunksByDoc.value[row.id];
    await refreshDocs();
  } catch {
    /* cancel */
  }
}

async function saveChunk() {
  if (!editingChunk.value) return;
  chunkSaving.value = true;
  try {
    const u = await ragApi.patchRagKbChunk(kid.value, editingChunk.value.doc.id, editingChunk.value.chunk.id, {
      content: chunkEditText.value,
    });
    const arr = chunksByDoc.value[editingChunk.value.doc.id];
    if (arr) {
      const i = arr.findIndex((x) => x.id === u.id);
      if (i >= 0) arr[i] = u;
    }
    ElMessage.success(t("views.kbMatrix.chunkUpdated"));
    chunkDlg.value = false;
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "message" in e ? String((e as { message?: string }).message) : t("views.kbMatrix.saveFailed");
    ElMessage.error(msg);
  } finally {
    chunkSaving.value = false;
  }
}

function unbindDocTableResize() {
  if (docTableResizeObserver) {
    docTableResizeObserver.disconnect();
    docTableResizeObserver = null;
  }
}

/**
 * 琛ㄩ珮鍙栬嚜銆屾枃妗ｈ〃鏍煎灞傛Ы銆峽@code .docs-table-wrap} 鐨?{@code clientHeight}锛坒lex:1 + min-height:0 涓嬬殑鍙敤楂樺害锛夛紝
 * 鍕跨敤 {@code docs-main} 鐨?{@code getBoundingClientRect().height} 鍙備笌鍥炵畻锛氳〃浣撶暐瓒呭嚭鏃朵細鎶?main 鎾戦珮锛? * ResizeObserver 鍙嶅璇诲埌鏇村ぇ楂樺害 鈫?鏃犻檺澧為珮锛屽垎椤靛櫒琚《鍑鸿鍙ｏ紙鐩磋繛甯??kbId= 杩涘叆鏃舵洿鏄撹Е鍙戯級銆? */
function bindDocTableResize() {
  unbindDocTableResize();
  const wrap = docsTableWrapRef.value;
  if (!wrap || typeof ResizeObserver === "undefined") {
    return;
  }
  const capByViewport = () =>
    typeof window !== "undefined" ? Math.max(240, window.innerHeight - 200) : 720;

  const apply = () => {
    let h = Math.floor(wrap.clientHeight);
    if (h < 80) {
      return;
    }
    h = Math.min(h, capByViewport());
    docTableBodyHeight.value = Math.max(200, h);
  };
  docTableResizeObserver = new ResizeObserver(() => {
    window.requestAnimationFrame(apply);
  });
  docTableResizeObserver.observe(wrap);
  requestAnimationFrame(apply);
}

watch(
  () => props.kbId,
  () => {
    if (!Number.isFinite(props.kbId)) return;
    selectedCategoryId.value = null;
    docPageCurrent.value = 1;
    queryTitle.value = "";
    queryDisplayStatus.value = undefined;
    chunksByDoc.value = {};
    void refreshDocs().then(() => nextTick()).then(() => bindDocTableResize());
  },
  { immediate: true },
);

watch(docPageSize, () => {
  void nextTick().then(() => bindDocTableResize());
});

onMounted(() => {
  void nextTick().then(() => bindDocTableResize());
});

onBeforeUnmount(() => {
  unbindDocTableResize();
});
</script>

<style scoped>
.rag-cap-inline-alert {
  margin-bottom: 12px;
  flex-shrink: 0;
}

.crawl-mode-form-item :deep(.el-form-item__label) {
  align-self: flex-start;
  padding-top: 12px;
}

.crawl-mode-form-item :deep(.el-form-item__content) {
  line-height: normal;
}

/* 说明条通栏展示，不占右侧控件列宽 */
.crawl-intro-form-item.el-form-item {
  align-items: stretch;
  margin-bottom: 8px;
}

.crawl-intro-form-item :deep(.el-form-item__content) {
  margin-left: 0 !important;
  width: 100%;
  max-width: 100%;
  min-height: 0;
  display: block;
  align-items: stretch;
}

.crawl-warn {
  margin: 0;
  width: 100%;
}

.crawl-warn :deep(.el-alert__content) {
  line-height: 1.55;
}

.crawl-warn :deep(.el-alert__description) {
  margin: 0;
  line-height: inherit;
}

.ingest-form :deep(.el-form-item) {
  align-items: center;
}

.ingest-form :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: auto;
  min-height: var(--el-component-size);
  line-height: 1.35;
  padding-right: 12px;
}

.ingest-form :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-height: var(--el-component-size);
}

.ingest-form :deep(.el-form-item__content > .el-input),
.ingest-form :deep(.el-form-item__content > .el-select),
.ingest-form :deep(.el-form-item__content > .el-input-number) {
  flex: 1;
  width: 100%;
  max-width: 100%;
}

.ingest-form :deep(.el-form-item__content > .el-input-number) {
  flex: 0 1 auto;
  width: auto;
}

.ingest-submit-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.crawl-depth-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

.kb-dmx-root {
  --ws-accent: var(--el-color-primary);
  --ws-accent-weak: var(--el-color-primary-light-9);
  --ws-card: var(--el-bg-color);
  --ws-border: var(--el-border-color-lighter);
  --ws-muted: var(--el-text-color-secondary);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.doc-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.doc-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.accent-btn {
  background: linear-gradient(135deg, var(--el-color-primary-dark-2), var(--el-color-primary)) !important;
  border: none !important;
}

.doc-toolbar-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.pipe-table {
  border-radius: 12px;
}

.pipe-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.cell-title {
  font-weight: 500;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.cell-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ws-muted);
  word-break: break-all;
}

.expand-inner {
  padding: 8px 12px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.job-expand .json-hdr {
  margin-top: 10px;
}

.job-kv-block {
  margin-top: 12px;
}

.job-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.job-raw-collapse {
  margin-top: 10px;
}

.expand-hdr {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
}

.json-hdr {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.json-block {
  margin-top: 10px;
}

.json-pre {
  margin: 0;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}

.timeline-wrap {
  margin-top: 10px;
}

.st {
  color: var(--ws-muted);
  font-size: 12px;
}

.td {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
}

.muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.ingest-type {
  width: 100%;
  display: flex;
  margin-bottom: 12px;
}

.ingest-type :deep(.el-radio-button) {
  flex: 1;
}

.ingest-type :deep(.el-radio-button__inner) {
  width: 100%;
}

.ingest-tip {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.ingest-form {
  padding-top: 4px;
}

.ingest-upload-drop {
  width: 100%;
}

.ingest-upload-drop :deep(.el-upload) {
  width: 100%;
}

.ingest-upload-drop :deep(.el-upload-dragger) {
  width: 100%;
  padding: 20px 12px;
}

.ingest-upload-ico {
  font-size: 40px;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

/* 文档矩阵（左右分栏） */
.docs-matrix {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.docs-nav {
  flex: 0 0 220px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-self: stretch;
  padding: 12px;
  border: 1px solid var(--ws-border);
  border-radius: 12px;
  background: var(--ws-card);
}

.new-cat-btn {
  width: 100%;
  margin-bottom: 12px;
}

.cat-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.cat-row {
  display: flex;
  align-items: stretch;
  border-radius: 8px;
  border: 1px solid transparent;
}

.cat-row.is-active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.cat-item {
  flex: 0 0 auto;
  text-align: left;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 14px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.cat-item:hover {
  background: var(--el-fill-color);
}

.cat-item.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.cat-item-grow {
  flex: 1;
  min-width: 0;
}

.cat-more {
  flex-shrink: 0;
  padding: 0 6px !important;
}

.docs-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.docs-table-wrap {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid var(--ws-border);
  background: var(--ws-card);
}

.docs-table-wrap :deep(.el-table) {
  --el-table-border-color: var(--ws-border);
}

.docs-toolbar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--ws-border);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
}

.docs-toolbar-left,
.docs-toolbar-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.q-title {
  width: 200px;
  max-width: 100%;
}

.q-status {
  width: 140px;
}

.docs-table {
  border-radius: 12px;
}

.docs-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.doc-range-text {
  font-size: 13px;
  color: var(--ws-muted);
}

.jobs-dlg-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ws-muted);
  line-height: 1.5;
}

.jobs-dlg-toolbar {
  margin-bottom: 10px;
}

.retrieval-result-wrap {
  margin-top: 8px;
}
.retrieval-meta {
  margin-bottom: 12px;
}
.retrieval-section {
  margin-top: 12px;
}
.retrieval-snippet-list {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}
.retrieval-snippet-list li + li {
  margin-top: 8px;
}

.jobs-dlg-table {
  border-radius: 10px;
}

.chunks-drawer-bar {
  margin-bottom: 12px;
}

.chunks-drawer-table {
  border-radius: 8px;
}

.doc-title-link {
  color: var(--el-color-primary);
  font-weight: 500;
  text-decoration: none;
}

.doc-title-link:hover {
  text-decoration: underline;
}

.chunk-edit-tabs {
  margin-top: -4px;
}

.chunk-edit-scrollbar {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.chunk-edit-scrollbar :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.chunk-edit-scrollbar :deep(.el-scrollbar__view) {
  padding: 10px 12px;
}

.chunk-edit-preview {
  min-height: 240px;
  padding: 0;
}

.chunk-edit-preview.chunk-md :deep(p) {
  margin: 0 0 0.45em;
}

.chunk-edit-preview.chunk-md :deep(p:last-child) {
  margin-bottom: 0;
}

.chunk-edit-preview.chunk-md :deep(h1),
.chunk-edit-preview.chunk-md :deep(h2),
.chunk-edit-preview.chunk-md :deep(h3),
.chunk-edit-preview.chunk-md :deep(h4) {
  margin: 0 0 0.35em;
  font-size: 14px;
  font-weight: 600;
}

.chunk-edit-preview.chunk-md :deep(ul),
.chunk-edit-preview.chunk-md :deep(ol) {
  margin: 0 0 0.45em;
  padding-left: 1.25em;
}

.chunk-edit-preview.chunk-md :deep(pre) {
  margin: 0 0 0.45em;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color);
  font-size: 12px;
  overflow-x: auto;
}

.chunk-edit-source :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}
</style>
