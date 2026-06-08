<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="hdr">
          <span class="title">{{ t("views.observability.title") }}</span>
          <div class="hdr-actions">
            <el-select
              v-if="isFounder"
              v-model="listFilterTenantId"
              class="tenant-filter"
              clearable
              filterable
              :placeholder="t('views.observability.placeholderAllTenants')"
              @change="onTenantFilterChange"
            >
              <el-option
                v-for="tenant in tenantOptions"
                :key="tenant.id"
                :label="`${tenant.name} (${tenant.code})`"
                :value="tenant.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="tabLoading" @click="reloadActiveTab">
              {{ t("views.observability.refresh") }}
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="obs-tabs" @tab-change="onTabChange">
        <el-tab-pane :label="t('views.observability.tabMcp')" name="mcp">
          <div class="filter-row">
            <el-input
              v-model="mcpFilters.conversationKeyword"
              class="filter-input filter-input-wide"
              clearable
              :placeholder="t('views.observability.filterConversationTitle')"
              @keyup.enter="loadMcp"
            />
            <el-input
              v-model="mcpFilters.toolName"
              class="filter-input"
              clearable
              :placeholder="t('views.observability.filterToolName')"
              @keyup.enter="loadMcp"
            />
            <el-select
              v-model="mcpFilters.success"
              class="filter-select"
              clearable
              :placeholder="t('views.observability.filterSuccess')"
            >
              <el-option :label="t('views.observability.successYes')" :value="true" />
              <el-option :label="t('views.observability.successNo')" :value="false" />
            </el-select>
            <el-input-number v-model="mcpFilters.days" :min="1" :max="90" class="filter-days" />
            <el-button type="primary" plain @click="loadMcp">{{ t("views.observability.search") }}</el-button>
          </div>
          <el-table
            v-loading="mcpLoading"
            :data="mcpRows"
            stripe
            border
            class="log-table"
            :empty-text="t('views.observability.emptyMcp')"
            highlight-current-row
            @row-click="openMcpDetail"
          >
            <el-table-column prop="createdAt" :label="t('views.observability.colTime')" width="168">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="conversationTitle" :label="t('views.observability.colConversationTitle')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.conversationTitle || emDash }}</template>
            </el-table-column>
            <el-table-column prop="qualifiedToolName" :label="t('views.observability.colTool')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="sourceScene" :label="t('views.observability.colScene')" width="130" />
            <el-table-column prop="success" :label="t('views.observability.colSuccess')" width="88" align="center">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? t("views.observability.successYes") : t("views.observability.successNo") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latencyMs" :label="t('views.observability.colLatency')" width="102" align="right" />
            <el-table-column :label="t('views.observability.colActions')" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openMcpDetail(row)">
                  {{ t("views.observability.detail") }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="mcpPage"
              v-model:page-size="mcpSize"
              layout="total, sizes, prev, pager, next"
              :total="mcpTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              @current-change="loadMcp"
              @size-change="onMcpSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane :label="t('views.observability.tabRag')" name="rag">
          <div class="filter-row">
            <el-input
              v-model="ragFilters.conversationKeyword"
              class="filter-input filter-input-wide"
              clearable
              :placeholder="t('views.observability.filterConversationTitle')"
              @keyup.enter="loadRag"
            />
            <el-input
              v-model="ragFilters.queryKeyword"
              class="filter-input filter-input-wide"
              clearable
              :placeholder="t('views.observability.filterQueryKeyword')"
              @keyup.enter="loadRag"
            />
            <el-input
              v-model="ragFilters.kbNameKeyword"
              class="filter-input"
              clearable
              :placeholder="t('views.observability.filterKbName')"
              @keyup.enter="loadRag"
            />
            <el-input-number v-model="ragFilters.days" :min="1" :max="90" class="filter-days" />
            <el-button type="primary" plain @click="loadRag">{{ t("views.observability.search") }}</el-button>
          </div>
          <el-table
            v-loading="ragLoading"
            :data="ragRows"
            stripe
            border
            class="log-table"
            :empty-text="t('views.observability.emptyRag')"
            highlight-current-row
            @row-click="openRagBatch"
          >
            <el-table-column prop="createdAt" :label="t('views.observability.colTime')" width="168">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.observability.colUserQuestion')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ displayUserQuestion(row) }}</template>
            </el-table-column>
            <el-table-column prop="conversationTitle" :label="t('views.observability.colConversationTitle')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.conversationTitle || emDash }}</template>
            </el-table-column>
            <el-table-column prop="kbName" :label="t('views.observability.colKbName')" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.kbName || emDash }}</template>
            </el-table-column>
            <el-table-column :label="t('views.observability.colChunkLabel')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.chunkLabel || row.documentTitle || emDash }}</template>
            </el-table-column>
            <el-table-column prop="retrievalMode" :label="t('views.observability.colRetrievalMode')" width="120" />
            <el-table-column :label="t('views.observability.colSimilarity')" width="110" align="right">
              <template #default="{ row }">{{ formatScore(row.vectorSimilarity) }}</template>
            </el-table-column>
            <el-table-column prop="rankInBatch" :label="t('views.observability.colRank')" width="72" align="center" />
            <el-table-column :label="t('views.observability.colActions')" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openRagBatch(row)">
                  {{ t("views.observability.detail") }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="ragPage"
              v-model:page-size="ragSize"
              layout="total, sizes, prev, pager, next"
              :total="ragTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              @current-change="loadRag"
              @size-change="onRagSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane :label="t('views.observability.tabQuality')" name="quality">
          <div class="filter-row">
            <el-select v-model="qaFilters.scope" class="filter-select" clearable :placeholder="t('views.ragQuality.filterScope')">
              <el-option :label="t('views.ragQuality.scopeMessageTurn')" value="MESSAGE_TURN" />
              <el-option :label="t('views.ragQuality.scopeChunkQuery')" value="CHUNK_QUERY" />
            </el-select>
            <el-input
              v-model="qaFilters.conversationKeyword"
              class="filter-input filter-input-wide"
              clearable
              :placeholder="t('views.observability.filterConversationTitle')"
              @keyup.enter="loadQuality"
            />
            <el-input
              v-model="qaFilters.queryKeyword"
              class="filter-input filter-input-wide"
              clearable
              :placeholder="t('views.observability.filterQueryKeyword')"
              @keyup.enter="loadQuality"
            />
            <el-input-number v-model="qaFilters.days" :min="1" :max="180" class="filter-days" />
            <el-button type="primary" plain @click="loadQuality">{{ t("views.observability.search") }}</el-button>
          </div>
          <el-table
            v-loading="qaLoading"
            :data="qaRows"
            stripe
            border
            class="log-table"
            :empty-text="t('views.ragQuality.empty')"
            highlight-current-row
            @row-click="openQualityDetail"
          >
            <el-table-column prop="createdAt" :label="t('views.observability.colTime')" width="168">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="conversationTitle" :label="t('views.observability.colConversationTitle')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.conversationTitle || emDash }}</template>
            </el-table-column>
            <el-table-column prop="queryText" :label="t('views.observability.colUserQuestion')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.queryText || emDash }}</template>
            </el-table-column>
            <el-table-column prop="scope" :label="t('views.ragQuality.colScope')" width="120">
              <template #default="{ row }">{{ scopeLabel(row.scope) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('views.ragQuality.colStatus')" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.ragQuality.colRecall')" width="96" align="right">
              <template #default="{ row }">{{ formatPct(row.recallHitRate) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.ragQuality.colCitation')" width="96" align="right">
              <template #default="{ row }">{{ formatPct(row.citationAccuracy) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.ragQuality.colFaithfulness')" width="96" align="right">
              <template #default="{ row }">{{ formatPct(row.faithfulnessScore) }}</template>
            </el-table-column>
            <el-table-column :label="t('views.observability.colActions')" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openQualityDetail(row)">
                  {{ t("views.observability.detail") }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="qaPage"
              v-model:page-size="qaSize"
              layout="total, sizes, prev, pager, next"
              :total="qaTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              @current-change="loadQuality"
              @size-change="onQaSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="mcpDetailOpen"
      :title="t('views.observability.mcpDlgTitle')"
      width="720px"
      destroy-on-close
      class="detail-dlg"
    >
      <template v-if="mcpDetail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('views.observability.colConversationTitle')">
            {{ mcpDetail.conversationTitle || emDash }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colTool')">{{ mcpDetail.qualifiedToolName ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colScene')">{{ mcpDetail.sourceScene ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colSuccess')">
            {{ mcpDetail.success ? t("views.observability.successYes") : t("views.observability.successNo") }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colLatency')">{{ mcpDetail.latencyMs ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colTime')">{{ formatTime(mcpDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colTraceId')">{{ mcpDetail.traceId }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colConversationId')">{{ mcpDetail.conversationId ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.descOrchestrationTrace')">{{ mcpDetail.orchestrationTraceId ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.descHttpTrace')">{{ mcpDetail.httpTraceId ?? emDash }}</el-descriptions-item>
        </el-descriptions>
        <section v-if="mcpArgsRows.length || mcpArgsPlain" class="readable-section">
          <h4 class="section-title">{{ t("views.observability.sectionArgs") }}</h4>
          <el-descriptions v-if="mcpArgsRows.length" :column="1" border size="small">
            <el-descriptions-item v-for="(row, idx) in mcpArgsRows" :key="idx" :label="row.label">
              {{ row.value }}
            </el-descriptions-item>
          </el-descriptions>
          <p v-else class="readable-text">{{ mcpArgsPlain }}</p>
        </section>
        <section v-if="mcpResultView" class="readable-section">
          <h4 class="section-title">{{ t("views.observability.sectionResult") }}</h4>
          <el-alert
            v-if="mcpResultView.isError"
            type="error"
            :closable="false"
            show-icon
            class="result-alert"
            :title="t('views.observability.mcpResultError')"
          />
          <p v-if="mcpResultView.textContent" class="readable-text">{{ mcpResultView.textContent }}</p>
          <el-descriptions v-if="mcpResultMetaRows.length" :column="1" border size="small" class="result-meta">
            <el-descriptions-item v-for="(row, idx) in mcpResultMetaRows" :key="idx" :label="row.label">
              {{ row.value }}
            </el-descriptions-item>
          </el-descriptions>
          <p v-if="!mcpResultView.textContent && !mcpResultMetaRows.length && !mcpResultView.isError" class="readable-empty">
            {{ emDash }}
          </p>
        </section>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ragBatchOpen"
      :title="t('views.observability.ragDlgTitle')"
      width="860px"
      destroy-on-close
      class="detail-dlg"
    >
      <div v-loading="ragBatchLoading">
        <div v-if="ragBatchSummary" class="batch-summary">
          <div v-if="ragBatchSummary.conversationTitle" class="summary-row">
            <span class="summary-label">{{ t("views.observability.colConversationTitle") }}</span>
            <span class="summary-value">{{ ragBatchSummary.conversationTitle }}</span>
          </div>
          <div v-if="ragBatchSummary.userQuestion" class="summary-row">
            <span class="summary-label">{{ t("views.observability.colUserQuestion") }}</span>
            <span class="summary-value">{{ ragBatchSummary.userQuestion }}</span>
          </div>
          <div class="summary-row">
            <span class="summary-label">{{ t("views.observability.ragBatchHitCount") }}</span>
            <span class="summary-value">{{ ragBatchRows.length }}</span>
          </div>
        </div>
        <el-table v-if="ragBatchRows.length" :data="ragBatchRows" size="small" stripe border max-height="420">
          <el-table-column prop="rankInBatch" :label="t('views.observability.colRank')" width="72" align="center" />
          <el-table-column :label="t('views.observability.colChunkLabel')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.chunkLabel || row.documentTitle || emDash }}</template>
          </el-table-column>
          <el-table-column prop="kbName" :label="t('views.observability.colKbName')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.kbName || emDash }}</template>
          </el-table-column>
          <el-table-column prop="retrievalMode" :label="t('views.observability.colRetrievalMode')" width="120" />
          <el-table-column prop="hitSource" :label="t('views.observability.colHitSource')" width="110" />
          <el-table-column :label="t('views.observability.colSimilarity')" width="100" align="right">
            <template #default="{ row }">{{ formatScore(row.vectorSimilarity) }}</template>
          </el-table-column>
          <el-table-column :label="t('views.observability.colUserQuestion')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ displayUserQuestion(row) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else :description="t('views.observability.emptyRagBatch')" />
        <p v-if="ragBatchHitTraceId" class="batch-tech">{{ t("views.observability.ragBatchTechId", { id: ragBatchHitTraceId }) }}</p>
      </div>
    </el-dialog>

    <el-dialog
      v-model="qaDetailOpen"
      :title="t('views.ragQuality.dlgTitle')"
      width="720px"
      destroy-on-close
      class="detail-dlg"
    >
      <template v-if="qaDetail">
        <div class="score-cards">
          <div class="score-card">
            <span class="score-label">{{ t("views.ragQuality.colRecall") }}</span>
            <span class="score-value">{{ formatPct(qaDetail.recallHitRate) }}</span>
          </div>
          <div class="score-card">
            <span class="score-label">{{ t("views.ragQuality.colCitation") }}</span>
            <span class="score-value">{{ formatPct(qaDetail.citationAccuracy) }}</span>
          </div>
          <div class="score-card">
            <span class="score-label">{{ t("views.ragQuality.colFaithfulness") }}</span>
            <span class="score-value">{{ formatPct(qaDetail.faithfulnessScore) }}</span>
          </div>
        </div>
        <el-descriptions :column="1" border size="small" class="qa-desc">
          <el-descriptions-item :label="t('views.observability.colConversationTitle')">
            {{ qaDetail.conversationTitle || emDash }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colUserQuestion')">{{ qaDetail.queryText ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.ragQuality.colScope')">{{ scopeLabel(qaDetail.scope) }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.ragQuality.colStatus')">{{ statusLabel(qaDetail.status) }}</el-descriptions-item>
          <el-descriptions-item v-if="qaDetail.kbName" :label="t('views.observability.colKbName')">{{ qaDetail.kbName }}</el-descriptions-item>
          <el-descriptions-item v-if="qaDetail.chunkLabel" :label="t('views.observability.colChunkLabel')">{{ qaDetail.chunkLabel }}</el-descriptions-item>
          <el-descriptions-item v-if="qaDetail.errorMessage" :label="t('views.ragQuality.colError')">{{ qaDetail.errorMessage }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.ragQuality.colRunId')">{{ qaDetail.runId }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.observability.colConversationId')">{{ qaDetail.conversationId ?? emDash }}</el-descriptions-item>
          <el-descriptions-item :label="t('views.ragQuality.colAssistantMsg')">{{ qaDetail.assistantMessageId ?? emDash }}</el-descriptions-item>
        </el-descriptions>

        <section v-if="qaResultView?.recall" class="readable-section">
          <h4 class="section-title">{{ t("views.ragQuality.sectionRecall") }}</h4>
          <p v-if="qaResultView.recall.hintCode" class="readable-hint">{{ qualityHintLabel(qaResultView.recall.hintCode) }}</p>
          <p v-else-if="qaResultView.recall.rate != null" class="readable-summary">
            {{
              t("views.ragQuality.recallSummary", {
                cited: qaResultView.recall.citedInTopK ?? 0,
                total: qaResultView.recall.totalCited ?? 0,
                topK: qaResultView.recall.topK ?? 0,
                rate: qaResultView.recall.rate,
              })
            }}
          </p>
          <el-table v-if="qaResultView.recall.hits.length" :data="qaResultView.recall.hits" size="small" stripe border max-height="220">
            <el-table-column prop="rank" :label="t('views.observability.colRank')" width="72" align="center" />
            <el-table-column :label="t('views.observability.colChunkLabel')" min-width="120">
              <template #default="{ row }">{{ row.chunkId != null ? `#${row.chunkId}` : emDash }}</template>
            </el-table-column>
            <el-table-column :label="t('views.ragQuality.colInCitations')" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.inActualCitations ? 'success' : 'info'" size="small">
                  {{ row.inActualCitations ? t("views.ragQuality.yes") : t("views.ragQuality.no") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('views.observability.colSimilarity')" width="110" align="right">
              <template #default="{ row }">{{ row.vectorSimilarity ?? emDash }}</template>
            </el-table-column>
          </el-table>
          <el-descriptions v-if="qaResultView.recall.diagnostics.length" :column="2" border size="small" class="diag-desc">
            <el-descriptions-item
              v-for="(row, idx) in qaRecallDiagRows"
              :key="idx"
              :label="row.label"
            >
              {{ row.value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="qaResultView?.citation" class="readable-section">
          <h4 class="section-title">{{ t("views.ragQuality.sectionCitation") }}</h4>
          <p v-if="qaResultView.citation.hintCode" class="readable-hint">{{ qualityHintLabel(qaResultView.citation.hintCode) }}</p>
          <p v-else-if="qaResultView.citation.score" class="readable-summary">
            {{ t("views.ragQuality.citationSummary", { score: qaResultView.citation.score }) }}
          </p>
          <el-table v-if="qaResultView.citation.items.length" :data="qaResultView.citation.items" size="small" stripe border max-height="200">
            <el-table-column :label="t('views.observability.colChunkLabel')" width="100">
              <template #default="{ row }">{{ row.chunkId != null ? `#${row.chunkId}` : emDash }}</template>
            </el-table-column>
            <el-table-column :label="t('views.ragQuality.colRelevant')" width="96" align="center">
              <template #default="{ row }">
                <el-tag :type="row.relevant ? 'success' : 'warning'" size="small">
                  {{ row.relevant ? t("views.ragQuality.yes") : t("views.ragQuality.no") }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="judgeReason" :label="t('views.ragQuality.colJudgeReason')" min-width="180" show-overflow-tooltip />
          </el-table>
        </section>

        <section v-if="qaResultView?.faithfulness" class="readable-section">
          <h4 class="section-title">{{ t("views.ragQuality.sectionFaithfulness") }}</h4>
          <p v-if="qaResultView.faithfulness.hintCode" class="readable-hint">{{ qualityHintLabel(qaResultView.faithfulness.hintCode) }}</p>
          <template v-else>
            <p v-if="qaResultView.faithfulness.score" class="readable-summary">
              {{ t("views.ragQuality.faithfulnessSummary", { score: qaResultView.faithfulness.score }) }}
            </p>
            <p v-if="qaResultView.faithfulness.judgeReason" class="readable-text">{{ qaResultView.faithfulness.judgeReason }}</p>
            <div v-if="qaResultView.faithfulness.unsupportedClaims.length" class="claim-block">
              <p class="claim-title">{{ t("views.ragQuality.unsupportedClaims") }}</p>
              <ul class="claim-list">
                <li v-for="(claim, idx) in qaResultView.faithfulness.unsupportedClaims" :key="idx">{{ claim }}</li>
              </ul>
            </div>
          </template>
        </section>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import * as obsApi from "../../api/observabilityAdmin";
import {
  argLabel,
  flattenJsonToRows,
  mcpMetaLabel,
  parseMcpResultReadable,
  parseQualityResultReadable,
  qualityDiagLabel,
  qualityHintLabel as qualityHintLabelRaw,
} from "../../utils/observabilityReadable";
import type {
  EvalRunStatus,
  McpTraceRow,
  RagHitRow,
  RagQualityAssessmentListRow,
  RagQualityAssessmentScope,
  RagQualityAssessmentView,
} from "../../api/observabilityAdmin";
import { useAdminFounderListTenantFilter } from "../../composables/useAdminFounderTenantOptions";

const { t } = useI18n();
const emDash = "\u2014";
const { isFounder, tenantOptions, listFilterTenantId, listFilterQuery } = useAdminFounderListTenantFilter();

const activeTab = ref<"mcp" | "rag" | "quality">("mcp");

const mcpLoading = ref(false);
const mcpRows = ref<McpTraceRow[]>([]);
const mcpTotal = ref(0);
const mcpPage = ref(1);
const mcpSize = ref(20);
const mcpFilters = ref({ conversationKeyword: "", toolName: "", success: undefined as boolean | undefined, days: 7 });

const ragLoading = ref(false);
const ragRows = ref<RagHitRow[]>([]);
const ragTotal = ref(0);
const ragPage = ref(1);
const ragSize = ref(20);
const ragFilters = ref({ conversationKeyword: "", queryKeyword: "", kbNameKeyword: "", days: 7 });

const qaLoading = ref(false);
const qaRows = ref<RagQualityAssessmentListRow[]>([]);
const qaTotal = ref(0);
const qaPage = ref(1);
const qaSize = ref(20);
const qaFilters = ref({
  scope: undefined as RagQualityAssessmentScope | undefined,
  conversationKeyword: "",
  queryKeyword: "",
  days: 30,
});

const mcpDetailOpen = ref(false);
const mcpDetail = ref<McpTraceRow | null>(null);

const ragBatchOpen = ref(false);
const ragBatchLoading = ref(false);
const ragBatchHitTraceId = ref("");
const ragBatchRows = ref<RagHitRow[]>([]);

const qaDetailOpen = ref(false);
const qaDetail = ref<RagQualityAssessmentView | null>(null);

const tabLoading = computed(() => {
  if (activeTab.value === "mcp") return mcpLoading.value;
  if (activeTab.value === "rag") return ragLoading.value;
  return qaLoading.value;
});

const mcpArgsRows = computed(() => {
  if (!mcpDetail.value?.argumentsJson) return [];
  return flattenJsonToRows(mcpDetail.value.argumentsJson).map((row) => ({
    label: argLabel(row.label, t),
    value: row.value,
  }));
});

const mcpArgsPlain = computed(() => {
  if (mcpArgsRows.value.length || !mcpDetail.value?.argumentsJson?.trim()) return "";
  return mcpDetail.value.argumentsJson.trim();
});

const mcpResultView = computed(() => parseMcpResultReadable(mcpDetail.value?.resultJson));

const mcpResultMetaRows = computed(() => {
  const view = mcpResultView.value;
  if (!view?.metaRows.length) return [];
  return view.metaRows.map((row) => ({
    label: mcpMetaLabel(row.label, t),
    value: row.label === "error" ? (view.isError ? t("views.observability.successNo") : t("views.observability.successYes")) : row.value,
  }));
});

const qaResultView = computed(() => parseQualityResultReadable(qaDetail.value?.resultJson));

const qaRecallDiagRows = computed(() => {
  const diag = qaResultView.value?.recall?.diagnostics ?? [];
  return diag.map((row) => ({
    label: qualityDiagLabel(row.label, t),
    value: row.value,
  }));
});

const ragBatchSummary = computed(() => {
  const first = ragBatchRows.value[0];
  if (!first) return null;
  return {
    conversationTitle: first.conversationTitle ?? null,
    userQuestion: first.userQuestionPreview || first.queryText || null,
  };
});

function qualityHintLabel(code: string | null | undefined): string {
  return qualityHintLabelRaw(code, t);
}

function formatTime(v: string | null | undefined): string {
  if (!v) return emDash;
  return v.replace("T", " ").slice(0, 19);
}

function formatScore(v: number | null | undefined): string {
  if (v == null) return emDash;
  return Number(v).toFixed(4);
}

function formatPct(v: number | null | undefined): string {
  if (v == null) return emDash;
  return `${(Number(v) * 100).toFixed(1)}%`;
}

function scopeLabel(scope: RagQualityAssessmentScope | string | null | undefined): string {
  if (scope === "MESSAGE_TURN") return t("views.ragQuality.scopeMessageTurn");
  if (scope === "CHUNK_QUERY") return t("views.ragQuality.scopeChunkQuery");
  return scope ? String(scope) : emDash;
}

function statusLabel(status: EvalRunStatus | string | null | undefined): string {
  if (status === "QUEUED") return t("views.ragQuality.statusQueued");
  if (status === "RUNNING") return t("views.ragQuality.statusRunning");
  if (status === "SUCCEEDED") return t("views.ragQuality.statusSucceeded");
  if (status === "FAILED") return t("views.ragQuality.statusFailed");
  return status ? String(status) : emDash;
}

function statusTagType(status: EvalRunStatus | string): "success" | "warning" | "danger" | "info" {
  if (status === "SUCCEEDED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "RUNNING") return "warning";
  return "info";
}

function displayUserQuestion(row: RagHitRow): string {
  return row.userQuestionPreview || row.queryText || emDash;
}

async function loadMcp() {
  mcpLoading.value = true;
  try {
    const data = await obsApi.fetchMcpTraces(mcpPage.value, mcpSize.value, {
      ...listFilterQuery(),
      conversationKeyword: mcpFilters.value.conversationKeyword.trim() || undefined,
      toolName: mcpFilters.value.toolName.trim() || undefined,
      success: mcpFilters.value.success,
      days: mcpFilters.value.days,
    });
    mcpRows.value = data.records ?? [];
    mcpTotal.value = data.total ?? 0;
  } finally {
    mcpLoading.value = false;
  }
}

async function loadRag() {
  ragLoading.value = true;
  try {
    const data = await obsApi.fetchRagHits(ragPage.value, ragSize.value, {
      ...listFilterQuery(),
      conversationKeyword: ragFilters.value.conversationKeyword.trim() || undefined,
      queryKeyword: ragFilters.value.queryKeyword.trim() || undefined,
      kbNameKeyword: ragFilters.value.kbNameKeyword.trim() || undefined,
      days: ragFilters.value.days,
    });
    ragRows.value = data.records ?? [];
    ragTotal.value = data.total ?? 0;
  } finally {
    ragLoading.value = false;
  }
}

async function loadQuality() {
  qaLoading.value = true;
  try {
    const data = await obsApi.fetchRagQualityAssessments(qaPage.value, qaSize.value, {
      ...listFilterQuery(),
      scope: qaFilters.value.scope,
      conversationKeyword: qaFilters.value.conversationKeyword.trim() || undefined,
      queryKeyword: qaFilters.value.queryKeyword.trim() || undefined,
      days: qaFilters.value.days,
    });
    qaRows.value = data.records ?? [];
    qaTotal.value = data.total ?? 0;
  } finally {
    qaLoading.value = false;
  }
}

function reloadActiveTab() {
  if (activeTab.value === "mcp") void loadMcp();
  else if (activeTab.value === "rag") void loadRag();
  else void loadQuality();
}

function onTabChange(name: string | number) {
  if (name === "mcp" && !mcpRows.value.length) void loadMcp();
  else if (name === "rag" && !ragRows.value.length) void loadRag();
  else if (name === "quality" && !qaRows.value.length) void loadQuality();
}

function onTenantFilterChange() {
  mcpPage.value = 1;
  ragPage.value = 1;
  qaPage.value = 1;
  reloadActiveTab();
}

function onMcpSizeChange() {
  mcpPage.value = 1;
  void loadMcp();
}

function onRagSizeChange() {
  ragPage.value = 1;
  void loadRag();
}

function onQaSizeChange() {
  qaPage.value = 1;
  void loadQuality();
}

async function openMcpDetail(row: McpTraceRow) {
  mcpDetailOpen.value = true;
  mcpDetail.value = row;
  try {
    mcpDetail.value = await obsApi.fetchMcpTraceDetail(row.traceId);
  } catch {
    /* keep list row */
  }
}

async function openRagBatch(row: RagHitRow) {
  ragBatchOpen.value = true;
  ragBatchHitTraceId.value = row.hitTraceId;
  ragBatchRows.value = [];
  ragBatchLoading.value = true;
  try {
    ragBatchRows.value = await obsApi.fetchRagHitBatch(row.hitTraceId);
  } finally {
    ragBatchLoading.value = false;
  }
}

async function openQualityDetail(row: RagQualityAssessmentListRow) {
  qaDetailOpen.value = true;
  qaDetail.value = null;
  try {
    qaDetail.value = await obsApi.fetchRagQualityAssessment(row.runId);
  } catch {
    ElMessage.error(t("views.ragQuality.loadFailed"));
  }
}

onMounted(() => {
  void loadMcp();
});
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
}

.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.hdr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title {
  font-weight: 600;
  font-size: 15px;
}

.tenant-filter {
  width: min(280px, 40vw);
}

.obs-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}

.filter-input {
  width: min(180px, 36vw);
}

.filter-select {
  width: min(160px, 32vw);
}

.filter-input-wide {
  width: min(220px, 42vw);
}

.filter-days {
  width: 120px;
}

.log-table {
  width: 100%;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.batch-summary {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 13px;
}

.summary-label {
  flex: 0 0 auto;
  min-width: 72px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  flex: 1;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.batch-tech {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.readable-section {
  margin-top: 14px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.readable-text,
.readable-summary,
.readable-hint {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.readable-hint {
  color: var(--el-text-color-secondary);
}

.readable-empty {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.result-alert {
  margin-bottom: 10px;
}

.result-meta,
.diag-desc {
  margin-top: 8px;
}

.claim-block {
  margin-top: 8px;
}

.claim-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.claim-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-color-warning);
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.score-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.score-card {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(180deg, var(--el-fill-color-light) 0%, var(--el-bg-color) 100%);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.score-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.score-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.qa-desc {
  margin-top: 4px;
}

:global(html.dark) .batch-summary {
  border-color: var(--el-border-color);
  background: var(--el-fill-color);
}

:global(html.dark) .score-card {
  border-color: var(--el-border-color);
  background: linear-gradient(180deg, var(--el-fill-color) 0%, var(--el-bg-color-overlay) 100%);
}

:global(html.dark) .score-value {
  color: var(--el-color-primary-light-3);
}
</style>
