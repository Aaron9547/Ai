<template>
  <div class="page">
    <header class="head">
      <div>
        <h1>{{ t("admin.message.title") }}</h1>
        <p class="hint">{{ t("admin.message.hint") }}</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="reload">{{ t("common.refresh") }}</el-button>
    </header>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane :label="t('admin.message.tabChannels')" name="channels">
        <div class="toolbar">
          <el-button type="primary" @click="openChannelDialog()">{{ t("admin.message.addChannel") }}</el-button>
        </div>
        <el-table v-loading="loading" :data="channels" stripe border>
          <el-table-column prop="channelCode" :label="t('admin.message.colCode')" width="150" show-overflow-tooltip />
          <el-table-column prop="name" :label="t('admin.message.colName')" min-width="140" />
          <el-table-column :label="t('admin.message.colType')" width="168">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ channelTypeLabel(t, row.channelType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colProviderTemplate')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="isSmsChannelType(row.channelType)">{{ channelSmsProviderSummary(row) }}</span>
              <span v-else class="muted">{{ t("common.dash") }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colStatus')" width="96">
            <template #default="{ row }">
              <el-tag :type="enableStatusTagType(row.status)" size="small">
                {{ enableStatusLabel(t, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colSecret')" width="108">
            <template #default="{ row }">
              <el-tag :type="row.secretConfigured ? 'success' : 'warning'" size="small">
                {{ row.secretConfigured ? t("admin.message.secretYes") : t("admin.message.secretNo") }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openChannelDialog(row)">{{ t("common.edit") }}</el-button>
              <el-button link type="primary" @click="openTestDialog(row)">{{ t("admin.message.test") }}</el-button>
              <el-button link type="danger" @click="onDeleteChannel(row.id)">{{ t("common.delete") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="t('admin.message.tabTemplates')" name="templates">
        <div class="toolbar">
          <el-button type="primary" @click="openTemplateDialog()">{{ t("admin.message.addTemplate") }}</el-button>
        </div>
        <el-table v-loading="loading" :data="templates" stripe border>
          <el-table-column :label="t('admin.message.colScene')" min-width="200">
            <template #default="{ row }">
              <div class="cell-main">{{ sceneLabel(t, row.sceneCode) }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colChannel')" min-width="200">
            <template #default="{ row }">
              {{ resolveChannelLabel(t, row.channelId, channelById) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colLocale')" width="120">
            <template #default="{ row }">
              {{ localeLabel(t, row.locale) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colStatus')" width="96">
            <template #default="{ row }">
              <el-tag :type="enableStatusTagType(row.status)" size="small">
                {{ enableStatusLabel(t, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colMessageContent')" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ templateListPreview(row) }}
            </template>
          </el-table-column>
          <el-table-column
            v-if="templates.some((r) => templateRowUsesSms(r, channelById))"
            :label="t('admin.message.colProviderTemplate')"
            width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span v-if="templateRowUsesSms(row, channelById)">
                {{ templateProviderSummary(row) }}
              </span>
              <span v-else class="muted">{{ t("common.dash") }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTemplateDialog(row)">{{ t("common.edit") }}</el-button>
              <el-button link type="danger" @click="onDeleteTemplate(row.id)">{{ t("common.delete") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="t('admin.message.tabLogs')" name="logs">
        <div class="toolbar filters">
          <el-select
            v-model="logFilter.sceneCode"
            clearable
            :placeholder="t('admin.message.filterScene')"
            style="width: 220px"
          >
            <el-option
              v-for="s in MESSAGE_SCENE_CODES"
              :key="s"
              :label="sceneLabel(t, s)"
              :value="s"
            />
          </el-select>
          <el-input
            v-model="logFilter.recipient"
            clearable
            :placeholder="t('admin.message.filterRecipient')"
            style="width: 200px"
          />
          <el-select v-model="logFilter.status" clearable :placeholder="t('admin.message.filterStatus')" style="width: 140px">
            <el-option
              v-for="st in MESSAGE_DELIVERY_STATUSES"
              :key="st"
              :label="deliveryStatusLabel(t, st)"
              :value="st"
            />
          </el-select>
          <el-button type="primary" plain @click="loadLogs">{{ t("common.refresh") }}</el-button>
        </div>
        <el-table v-loading="logLoading" :data="logs" stripe border>
          <el-table-column prop="id" label="ID" width="72" />
          <el-table-column :label="t('admin.message.colScene')" width="200">
            <template #default="{ row }">
              {{ sceneLabel(t, row.sceneCode) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colChannelType')" width="150">
            <template #default="{ row }">
              <span v-if="row.channelType">{{ channelTypeLabel(t, row.channelType) }}</span>
              <span v-else class="muted">{{ t("common.dash") }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="recipient" :label="t('admin.message.colRecipient')" width="140" show-overflow-tooltip />
          <el-table-column :label="t('admin.message.colMessageContent')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ logMessagePreview(row, channelById) || t("common.dash") }}
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colStatus')" width="108">
            <template #default="{ row }">
              <el-tag :type="deliveryStatusTagType(row.status)" size="small">
                {{ deliveryStatusLabel(t, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.message.colCreated')" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="88" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openLogDetail(row)">{{ t("admin.message.viewDetail") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="logPage"
          v-model:page-size="logPageSize"
          class="pager"
          layout="total, prev, pager, next"
          :total="logTotal"
          @current-change="loadLogs"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="channelDialogVisible"
      :title="channelEditing ? t('admin.message.editChannel') : t('admin.message.addChannel')"
      width="720px"
      destroy-on-close
    >
      <el-form label-width="132px">
        <el-form-item :label="t('admin.message.colCode')" required>
          <el-input v-model="channelForm.channelCode" :disabled="!!channelEditing" :placeholder="t('admin.message.channelCodePh')" />
          <p v-if="!channelEditing" class="field-hint">{{ t("admin.message.channelCodeHint") }}</p>
        </el-form-item>
        <el-form-item :label="t('admin.message.colName')" required>
          <el-input v-model="channelForm.name" :placeholder="t('admin.message.colName')" />
        </el-form-item>
        <el-form-item :label="t('admin.message.colType')" required>
          <el-select
            v-model="channelForm.channelType"
            :disabled="!!channelEditing"
            style="width: 100%"
            @change="onChannelTypeChange"
          >
            <el-option
              v-for="ct in MESSAGE_CHANNEL_TYPES"
              :key="ct"
              :label="channelTypeLabel(t, ct)"
              :value="ct"
            >
              <div class="opt-title">{{ channelTypeLabel(t, ct) }}</div>
              <div class="opt-sub">{{ channelTypeHint(t, ct) }}</div>
            </el-option>
          </el-select>
        </el-form-item>

        <template v-if="channelForm.channelType === 'EMAIL_SMTP'">
          <el-form-item :label="t('admin.message.smtpHost')" required>
            <el-input v-model="smtpFields.smtpHost" placeholder="smtp.example.com" />
          </el-form-item>
          <el-form-item :label="t('admin.message.smtpPort')" required>
            <el-input-number v-model="smtpFields.smtpPort" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="t('admin.message.smtpUser')">
            <el-input v-model="smtpFields.username" autocomplete="off" />
          </el-form-item>
          <el-form-item :label="t('admin.message.smtpFrom')" required>
            <el-input v-model="smtpFields.from" placeholder="noreply@example.com" />
          </el-form-item>
          <el-form-item :label="t('admin.message.smtpSsl')">
            <el-switch v-model="smtpFields.ssl" :active-text="t('common.yes')" :inactive-text="t('common.no')" />
          </el-form-item>
          <el-form-item :label="t('admin.message.smtpPassword')">
            <el-input v-model="smtpPassword" type="password" show-password :placeholder="t('admin.message.secretPh')" />
          </el-form-item>
        </template>

        <template v-else-if="channelForm.channelType === 'SMS_ALIYUN'">
          <el-form-item :label="t('admin.message.aliyunAccessKeyId')" required>
            <el-input v-model="aliyunFields.accessKeyId" autocomplete="off" />
          </el-form-item>
          <el-form-item :label="t('admin.message.aliyunRegion')">
            <el-select v-model="aliyunFields.region" filterable allow-create style="width: 100%">
              <el-option
                v-for="r in ALIYUN_REGION_OPTIONS"
                :key="r.value"
                :label="t(r.labelKey)"
                :value="r.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.message.aliyunSignName')" required>
            <el-input v-model="aliyunFields.signName" />
          </el-form-item>
          <el-form-item :label="t('admin.message.aliyunTemplateCode')" required>
            <el-input v-model="aliyunFields.templateCode" />
          </el-form-item>
          <el-form-item :label="t('admin.message.aliyunAccessKeySecret')">
            <el-input v-model="aliyunSecret" type="password" show-password :placeholder="t('admin.message.secretPh')" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item :label="t('admin.message.tencentSdkAppId')" required>
            <el-input v-model="tencentFields.sdkAppId" />
          </el-form-item>
          <el-form-item :label="t('admin.message.tencentSignName')" required>
            <el-input v-model="tencentFields.signName" />
          </el-form-item>
          <el-form-item :label="t('admin.message.tencentTemplateId')" required>
            <el-input v-model="tencentFields.templateId" />
          </el-form-item>
          <el-form-item :label="t('admin.message.tencentRegion')">
            <el-select v-model="tencentFields.region" filterable allow-create style="width: 100%">
              <el-option
                v-for="r in TENCENT_REGION_OPTIONS"
                :key="r.value"
                :label="t(r.labelKey)"
                :value="r.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.message.tencentSecretId')">
            <el-input v-model="tencentSecretId" type="password" show-password :placeholder="t('admin.message.secretPh')" />
          </el-form-item>
          <el-form-item :label="t('admin.message.tencentSecretKey')">
            <el-input v-model="tencentSecretKey" type="password" show-password :placeholder="t('admin.message.secretPh')" />
          </el-form-item>
        </template>

        <el-form-item :label="t('admin.message.colStatus')">
          <el-radio-group v-model="channelForm.status">
            <el-radio-button
              v-for="st in MESSAGE_ENABLE_STATUSES"
              :key="st"
              :value="st"
            >
              {{ enableStatusLabel(t, st) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelDialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveChannel">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="templateDialogVisible"
      :title="templateEditing ? t('admin.message.editTemplate') : t('admin.message.addTemplate')"
      width="720px"
      destroy-on-close
    >
      <el-form label-width="120px">
        <el-form-item :label="t('admin.message.colScene')" required>
          <el-select
            v-model="templateForm.sceneCode"
            :disabled="!!templateEditing"
            style="width: 100%"
            @change="onSceneChange"
          >
            <el-option
              v-for="s in MESSAGE_SCENE_CODES"
              :key="s"
              :label="sceneLabel(t, s)"
              :value="s"
            >
              <div class="opt-title">{{ sceneLabel(t, s) }}</div>
              <div class="opt-sub">{{ sceneHint(t, s) }}</div>
            </el-option>
          </el-select>
          <p class="field-hint">{{ sceneHint(t, templateForm.sceneCode) }}</p>
        </el-form-item>
        <el-form-item :label="t('admin.message.colChannel')" required>
          <el-select
            v-model="templateForm.channelId"
            style="width: 100%"
            :placeholder="channelsForCurrentScene.length ? undefined : t('admin.message.noChannelsForScene')"
            :disabled="!channelsForCurrentScene.length"
          >
            <el-option
              v-for="c in channelsForCurrentScene"
              :key="c.id"
              :label="channelOptionLabel(t, c)"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.message.templateVars')">
          <p class="var-hint">{{ t("admin.message.templateVarHint") }}</p>
          <div class="var-tags">
            <el-tag
              v-for="v in sceneTemplateVars"
              :key="v.key"
              class="var-tag"
              effect="plain"
              type="info"
              @click="insertTemplateVar(v.key)"
            >
              {{ t(v.labelKey) }} · {{ templatePlaceholder(v.key) }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item
          v-if="templateForm.sceneCode !== 'SMS_LOGIN'"
          :label="t('admin.message.colSubject')"
        >
          <el-input
            ref="subjectInputRef"
            v-model="templateForm.subjectTemplate"
            @focus="templateFocusField = 'subject'"
            @input="touchTemplatePreview"
          />
        </el-form-item>
        <el-form-item :label="isTemplateFormSms ? t('admin.message.colMessageContent') : t('admin.message.colBody')" required>
          <el-input
            ref="bodyInputRef"
            v-model="templateForm.bodyTemplate"
            type="textarea"
            :rows="8"
            @focus="templateFocusField = 'body'"
            @input="touchTemplatePreview"
          />
          <p v-if="isTemplateFormSms" class="field-hint">
            {{ isTemplateFormAliyun ? t("admin.message.smsAliyunVarsHint") : t("admin.message.smsTencentVarsHint") }}
          </p>
          <p v-else-if="!isTemplateFormSms" class="field-hint">
            {{ t("admin.message.emailBodyTemplateHint") }}
          </p>
        </el-form-item>
        <el-form-item :label="t('admin.message.messagePreview')">
          <div class="preview-box">
            <p class="field-hint">{{ t("admin.message.messagePreviewSample") }}</p>
            <div v-if="!isTemplateFormSms && templatePreviewSubject" class="preview-line">
              <span class="preview-label">{{ t("admin.message.colSubject") }}：</span>{{ templatePreviewSubject }}
            </div>
            <div class="preview-line preview-body">{{ templatePreviewBody || t("common.dash") }}</div>
            <div v-if="isTemplateFormSms && templatePreviewVarsLine" class="preview-vars">
              <span class="preview-label">{{ t("admin.message.colTemplateVars") }}：</span>{{ templatePreviewVarsLine }}
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('admin.message.colLocale')">
          <el-select v-model="templateForm.locale" style="width: 100%">
            <el-option
              v-for="loc in MESSAGE_LOCALE_OPTIONS"
              :key="loc.value"
              :label="t(loc.labelKey)"
              :value="loc.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.message.colStatus')">
          <el-radio-group v-model="templateForm.status">
            <el-radio-button
              v-for="st in MESSAGE_ENABLE_STATUSES"
              :key="st"
              :value="st"
            >
              {{ enableStatusLabel(t, st) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">{{ t("common.save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDialogVisible" :title="t('admin.message.test')" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item v-if="testChannelRow" :label="t('admin.message.colType')">
          <span>{{ channelTypeLabel(t, testChannelRow.channelType) }}</span>
        </el-form-item>
        <el-form-item :label="t('admin.message.colRecipient')" required>
          <el-input
            v-model="testRecipient"
            :placeholder="testRecipientPlaceholder"
          />
          <p class="field-hint">{{ testRecipientHint }}</p>
        </el-form-item>
        <el-form-item :label="t('admin.message.messagePreview')">
          <div class="preview-box">
            <div class="preview-line preview-body">{{ testMessagePreview }}</div>
            <div v-if="testChannelRow && isSmsChannelType(testChannelRow.channelType)" class="preview-vars">
              <span class="preview-label">{{ t("admin.message.colTemplateVars") }}：</span>code=123456
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">{{ t("common.cancel") }}</el-button>
        <el-button type="primary" :loading="saving" @click="runTest">{{ t("admin.message.testSend") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logDetailVisible"
      :title="t('admin.message.logDetailTitle')"
      width="600px"
      destroy-on-close
    >
      <template v-if="logDetailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="ID">{{ logDetailRow.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colScene')">
            {{ sceneLabel(t, logDetailRow.sceneCode) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colChannelType')">
            {{ logDetailRow.channelType ? channelTypeLabel(t, logDetailRow.channelType) : t("common.dash") }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colRecipient')">{{ logDetailRow.recipient }}</el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colStatus')">
            <el-tag :type="deliveryStatusTagType(logDetailRow.status)" size="small">
              {{ deliveryStatusLabel(t, logDetailRow.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item
            v-if="logDetailRow.messageSubject && !isSmsChannelType(logDetailRow.channelType)"
            :label="t('admin.message.colSubject')"
          >
            {{ logDetailRow.messageSubject }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colMessageContent')">
            {{ logDetailRow.messageBody || t("common.dash") }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="formatTemplateVarsLine(logDetailRow.templateVars)"
            :label="t('admin.message.colTemplateVars')"
          >
            {{ formatTemplateVarsLine(logDetailRow.templateVars) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="logDetailRow.providerMsgId" :label="t('admin.message.providerMsgId')">
            {{ logDetailRow.providerMsgId }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('admin.message.colCreated')">
            {{ formatTime(logDetailRow.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="logDetailRow.finishedAt" :label="t('admin.message.colFinished')">
            {{ formatTime(logDetailRow.finishedAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="logDetailRow.errorMessage" :label="t('admin.message.colError')">
            {{ logDetailRow.errorMessage }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import type { InputInstance } from "element-plus";
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import {
  createMessageChannel,
  createMessageTemplate,
  deleteMessageChannel,
  deleteMessageTemplate,
  listMessageChannels,
  listMessageTemplates,
  pageMessageDeliveryLogs,
  testMessageChannel,
  updateMessageChannel,
  updateMessageTemplate,
  type MessageChannelRow,
  type MessageChannelType,
  type MessageDeliveryLogRow,
  type MessageTemplateRow,
  type MessageSceneCode,
} from "@/api/message";
import {
  buildConfigJson,
  buildSecretJson,
  defaultAliyunFields,
  defaultSmtpFields,
  defaultTencentFields,
  parseAliyunFromConfigJson,
  parseSmtpFromConfigJson,
  parseTencentFromConfigJson,
  type AliyunSmsChannelFields,
  type SmtpChannelFields,
  type TencentSmsChannelFields,
} from "@/utils/messageChannelForm";
import {
  ALIYUN_REGION_OPTIONS,
  channelOptionLabel,
  channelTypeHint,
  channelTypeLabel,
  deliveryStatusLabel,
  deliveryStatusTagType,
  enableStatusLabel,
  enableStatusTagType,
  filterChannelsForScene,
  isEmailChannelType,
  channelSmsProviderSummary,
  formatTemplateVarsLine,
  isSmsChannelType,
  isSmsScene,
  localeLabel,
  logMessagePreview,
  MESSAGE_CHANNEL_TYPES,
  MESSAGE_DELIVERY_STATUSES,
  MESSAGE_ENABLE_STATUSES,
  MESSAGE_LOCALE_OPTIONS,
  MESSAGE_SCENE_CODES,
  resolveChannelLabel,
  sceneHint,
  sceneLabel,
  templateRowUsesSms,
  TENCENT_REGION_OPTIONS,
} from "@/utils/messageDisplay";
import {
  applyTemplatePreview,
  insertAtCaret,
  templatePlaceholder,
  TEMPLATE_PREVIEW_SAMPLE_VARS,
  varsForScene,
} from "@/utils/messageTemplateVars";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

function tabFromPath(path: string): "channels" | "templates" | "logs" {
  if (path.includes("templates")) return "templates";
  if (path.includes("logs")) return "logs";
  return "channels";
}

const activeTab = ref(tabFromPath(route.path));
const loading = ref(false);
const logLoading = ref(false);
const saving = ref(false);
const channels = ref<MessageChannelRow[]>([]);
const templates = ref<MessageTemplateRow[]>([]);
const logs = ref<Awaited<ReturnType<typeof pageMessageDeliveryLogs>>["records"]>([]);
const logPage = ref(1);
const logPageSize = ref(20);
const logTotal = ref(0);
const logFilter = reactive({ sceneCode: "" as MessageSceneCode | "", recipient: "", status: "" });

const channelById = computed(() => new Map(channels.value.map((c) => [c.id, c])));

const channelDialogVisible = ref(false);
const channelEditing = ref<MessageChannelRow | null>(null);
const channelForm = reactive({
  channelCode: "",
  channelType: "EMAIL_SMTP" as MessageChannelType,
  name: "",
  status: "ACTIVE" as "ACTIVE" | "DISABLED",
});

const smtpFields = reactive<SmtpChannelFields>(defaultSmtpFields());
const smtpPassword = ref("");
const aliyunFields = reactive<AliyunSmsChannelFields>(defaultAliyunFields());
const aliyunSecret = ref("");
const tencentFields = reactive<TencentSmsChannelFields>(defaultTencentFields());
const tencentSecretId = ref("");
const tencentSecretKey = ref("");

const templateDialogVisible = ref(false);
const templateEditing = ref<MessageTemplateRow | null>(null);
const templateForm = reactive({
  sceneCode: "REGISTER_VERIFICATION" as MessageSceneCode,
  channelId: 0,
  subjectTemplate: "",
  bodyTemplate: "",
  locale: "zh-CN",
  status: "ACTIVE" as "ACTIVE" | "DISABLED",
});
const templateFocusField = ref<"subject" | "body">("body");
const subjectInputRef = ref<InputInstance>();
const bodyInputRef = ref<InputInstance>();

const sceneTemplateVars = computed(() => varsForScene(templateForm.sceneCode));
const channelsForCurrentScene = computed(() => filterChannelsForScene(channels.value, templateForm.sceneCode));

const testDialogVisible = ref(false);
const testChannelRow = ref<MessageChannelRow | null>(null);
const testRecipient = ref("");

const logDetailVisible = ref(false);
const logDetailRow = ref<MessageDeliveryLogRow | null>(null);

const templatePreviewTick = ref(0);

const selectedTemplateChannel = computed(() => channelById.value.get(templateForm.channelId) ?? null);

const isTemplateFormSms = computed(
  () => isSmsScene(templateForm.sceneCode) || isSmsChannelType(selectedTemplateChannel.value?.channelType),
);

const isTemplateFormAliyun = computed(() => selectedTemplateChannel.value?.channelType === "SMS_ALIYUN");

const templatePreviewSubject = computed(() => {
  templatePreviewTick.value;
  return applyTemplatePreview(templateForm.subjectTemplate, TEMPLATE_PREVIEW_SAMPLE_VARS);
});

const templatePreviewBody = computed(() => {
  templatePreviewTick.value;
  return applyTemplatePreview(templateForm.bodyTemplate, TEMPLATE_PREVIEW_SAMPLE_VARS);
});

const templatePreviewVarsLine = computed(() => {
  templatePreviewTick.value;
  const keys = varsForScene(templateForm.sceneCode).map((v) => v.key);
  const subset: Record<string, string> = {};
  for (const k of keys) {
    if (TEMPLATE_PREVIEW_SAMPLE_VARS[k] != null) subset[k] = TEMPLATE_PREVIEW_SAMPLE_VARS[k];
  }
  return formatTemplateVarsLine(subset);
});

const testMessagePreview = computed(() => {
  if (!testChannelRow.value) return "";
  if (isSmsChannelType(testChannelRow.value.channelType)) {
    return applyTemplatePreview("通道测试：{code}", { code: "123456" });
  }
  return applyTemplatePreview("测试消息 {code}", { code: "123456" });
});

const testRecipientPlaceholder = computed(() =>
  testChannelRow.value && isEmailChannelType(testChannelRow.value.channelType)
    ? t("admin.message.testRecipientEmailPh")
    : t("admin.message.testRecipientSmsPh"),
);

const testRecipientHint = computed(() =>
  testChannelRow.value && isEmailChannelType(testChannelRow.value.channelType)
    ? t("admin.message.testRecipientEmailHint")
    : t("admin.message.testRecipientSmsHint"),
);

watch(
  () => route.path,
  (path) => {
    activeTab.value = tabFromPath(path);
    if (activeTab.value === "logs") loadLogs();
  },
);

watch(channelsForCurrentScene, (list) => {
  if (!list.some((c) => c.id === templateForm.channelId)) {
    templateForm.channelId = list[0]?.id ?? 0;
  }
});

function onTabChange(name: string | number) {
  const tab = String(name);
  const path =
    tab === "templates"
      ? "/system/message-templates"
      : tab === "logs"
        ? "/system/message-delivery-logs"
        : "/system/message-channels";
  if (route.path !== path) {
    router.replace(path);
  }
}

function resetChannelSecrets() {
  smtpPassword.value = "";
  aliyunSecret.value = "";
  tencentSecretId.value = "";
  tencentSecretKey.value = "";
}

function applySmtpFields(cfg: SmtpChannelFields) {
  Object.assign(smtpFields, cfg);
}

function applyAliyunFields(cfg: AliyunSmsChannelFields) {
  Object.assign(aliyunFields, cfg);
}

function applyTencentFields(cfg: TencentSmsChannelFields) {
  Object.assign(tencentFields, cfg);
}

function onChannelTypeChange() {
  if (channelEditing.value) return;
  applySmtpFields(defaultSmtpFields());
  applyAliyunFields(defaultAliyunFields());
  applyTencentFields(defaultTencentFields());
  resetChannelSecrets();
}

function loadChannelFieldsFromRow(row: MessageChannelRow) {
  applySmtpFields(parseSmtpFromConfigJson(row.configJson));
  applyAliyunFields(parseAliyunFromConfigJson(row.configJson));
  applyTencentFields(parseTencentFromConfigJson(row.configJson));
  resetChannelSecrets();
}

async function reload() {
  loading.value = true;
  try {
    const [ch, tp] = await Promise.all([listMessageChannels(), listMessageTemplates()]);
    channels.value = ch;
    templates.value = tp;
    if (activeTab.value === "logs") await loadLogs();
  } finally {
    loading.value = false;
  }
}

async function loadLogs() {
  logLoading.value = true;
  try {
    const page = await pageMessageDeliveryLogs({
      page: logPage.value,
      pageSize: logPageSize.value,
      sceneCode: logFilter.sceneCode || undefined,
      recipient: logFilter.recipient || undefined,
      status: logFilter.status || undefined,
    });
    logs.value = page.records;
    logTotal.value = page.total;
  } finally {
    logLoading.value = false;
  }
}

function openChannelDialog(row?: MessageChannelRow) {
  channelEditing.value = row ?? null;
  if (row) {
    channelForm.channelCode = row.channelCode;
    channelForm.channelType = row.channelType;
    channelForm.name = row.name;
    channelForm.status = row.status;
    loadChannelFieldsFromRow(row);
  } else {
    channelForm.channelCode = "";
    channelForm.channelType = "EMAIL_SMTP";
    channelForm.name = "";
    channelForm.status = "ACTIVE";
    onChannelTypeChange();
  }
  channelDialogVisible.value = true;
}

async function saveChannel() {
  const configJson = buildConfigJson(channelForm.channelType, smtpFields, aliyunFields, tencentFields);
  const secretJson = buildSecretJson(
    channelForm.channelType,
    smtpPassword.value,
    aliyunSecret.value,
    tencentSecretId.value,
    tencentSecretKey.value,
  );
  saving.value = true;
  try {
    if (channelEditing.value) {
      await updateMessageChannel(channelEditing.value.id, {
        name: channelForm.name,
        configJson,
        secretJson,
        status: channelForm.status,
      });
    } else {
      await createMessageChannel({
        channelCode: channelForm.channelCode,
        channelType: channelForm.channelType,
        name: channelForm.name,
        configJson,
        secretJson,
        status: channelForm.status,
      });
    }
    channelDialogVisible.value = false;
    await reload();
    ElMessage.success(t("common.saved"));
  } catch {
    ElMessage.error(t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function onDeleteChannel(id: number) {
  await ElMessageBox.confirm(t("admin.message.deleteChannelConfirm"), { type: "warning" });
  await deleteMessageChannel(id);
  await reload();
}

function ph(name: string) {
  return templatePlaceholder(name);
}

function defaultSubjectForScene(scene: MessageSceneCode): string {
  if (scene === "KNOWLEDGE_PLANET_WEEKLY") {
    return t("admin.message.defaultSubjectWeekly", { tenantName: ph("tenantName") });
  }
  if (scene === "SMS_LOGIN") return "";
  return t("admin.message.defaultSubjectRegister", { tenantName: ph("tenantName") });
}

function defaultBodyForScene(scene: MessageSceneCode): string {
  if (scene === "KNOWLEDGE_PLANET_WEEKLY") {
    return t("admin.message.defaultBodyWeekly", {
      userName: ph("userName"),
      summary: ph("summary"),
      weekLabel: ph("weekLabel"),
    });
  }
  if (scene === "SMS_LOGIN") {
    return t("admin.message.defaultBodySmsLogin", { code: ph("code"), ttlMinutes: ph("ttlMinutes") });
  }
  return t("admin.message.defaultBodyRegister", { code: ph("code"), ttlMinutes: ph("ttlMinutes") });
}

function touchTemplatePreview() {
  templatePreviewTick.value += 1;
}

function templateListPreview(row: MessageTemplateRow): string {
  if (templateRowUsesSms(row, channelById.value)) {
    return applyTemplatePreview(row.bodyTemplate, TEMPLATE_PREVIEW_SAMPLE_VARS) || row.bodyTemplate;
  }
  const sub = row.subjectTemplate?.trim();
  const body = row.bodyTemplate?.trim();
  if (sub && body) return `${sub} — ${body}`;
  return sub || body || t("common.dash");
}

function templateProviderSummary(row: MessageTemplateRow): string {
  const ch = channelById.value.get(row.channelId);
  if (!ch) return t("common.dash");
  return channelSmsProviderSummary(ch);
}

function formatTime(v: string | null | undefined): string {
  if (!v) return t("common.dash");
  return v.replace("T", " ").slice(0, 19);
}

function openLogDetail(row: MessageDeliveryLogRow) {
  logDetailRow.value = row;
  logDetailVisible.value = true;
}

function onSceneChange() {
  if (templateEditing.value) return;
  templateForm.subjectTemplate = defaultSubjectForScene(templateForm.sceneCode);
  templateForm.bodyTemplate = defaultBodyForScene(templateForm.sceneCode);
  const list = filterChannelsForScene(channels.value, templateForm.sceneCode);
  templateForm.channelId = list[0]?.id ?? 0;
  touchTemplatePreview();
}

function openTemplateDialog(row?: MessageTemplateRow) {
  templateEditing.value = row ?? null;
  if (row) {
    templateForm.sceneCode = row.sceneCode;
    templateForm.channelId = row.channelId;
    templateForm.subjectTemplate = row.subjectTemplate ?? "";
    templateForm.bodyTemplate = row.bodyTemplate;
    templateForm.locale = row.locale;
    templateForm.status = row.status;
  } else {
    templateForm.sceneCode = "REGISTER_VERIFICATION";
    onSceneChange();
    templateForm.locale = "zh-CN";
    templateForm.status = "ACTIVE";
  }
  templateDialogVisible.value = true;
  touchTemplatePreview();
}

async function insertTemplateVar(key: string) {
  const token = templatePlaceholder(key);
  const field = templateFocusField.value;
  await nextTick();
  if (field === "subject") {
    const el = subjectInputRef.value?.input ?? subjectInputRef.value?.textarea;
    templateForm.subjectTemplate = insertAtCaret(el, templateForm.subjectTemplate, token);
    el?.focus();
  } else {
    const el = bodyInputRef.value?.textarea ?? bodyInputRef.value?.input;
    templateForm.bodyTemplate = insertAtCaret(el, templateForm.bodyTemplate, token);
    el?.focus();
  }
}

async function saveTemplate() {
  if (!templateForm.channelId) {
    ElMessage.warning(t("admin.message.noChannelsForScene"));
    return;
  }
  saving.value = true;
  try {
    if (templateEditing.value) {
      await updateMessageTemplate(templateEditing.value.id, { ...templateForm });
    } else {
      await createMessageTemplate({ ...templateForm });
    }
    templateDialogVisible.value = false;
    await reload();
    ElMessage.success(t("common.saved"));
  } catch {
    ElMessage.error(t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function onDeleteTemplate(id: number) {
  await ElMessageBox.confirm(t("admin.message.deleteTemplateConfirm"), { type: "warning" });
  await deleteMessageTemplate(id);
  await reload();
}

function openTestDialog(row: MessageChannelRow) {
  testChannelRow.value = row;
  testRecipient.value = "";
  testDialogVisible.value = true;
}

async function runTest() {
  saving.value = true;
  try {
    await testMessageChannel(testChannelIdFromRow(), testRecipient.value.trim(), { code: "123456" });
    ElMessage.success(t("admin.message.testOk"));
    testDialogVisible.value = false;
  } catch {
    ElMessage.error(t("admin.message.testFailed"));
  } finally {
    saving.value = false;
  }
}

function testChannelIdFromRow(): number {
  return testChannelRow.value?.id ?? 0;
}

onMounted(() => {
  reload().catch(() => ElMessage.error(t("common.loadFailed")));
});
</script>

<style scoped>
.page {
  padding: 16px 20px 32px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.hint {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.toolbar {
  margin-bottom: 12px;
}
.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.field-hint,
.var-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}
.var-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.var-tag {
  cursor: pointer;
  user-select: none;
}
.var-tag:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.preview-box {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.preview-line {
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.preview-body {
  margin-top: 4px;
}
.preview-vars {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.preview-label {
  font-weight: 500;
  color: var(--el-text-color-regular);
}
.cell-main {
  font-weight: 500;
}
.muted {
  color: var(--el-text-color-secondary);
}
.opt-title {
  font-size: 14px;
  line-height: 1.35;
}
.opt-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.3;
  white-space: normal;
}
</style>
