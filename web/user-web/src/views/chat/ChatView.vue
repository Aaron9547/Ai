<template>
  <div
    class="chat-app"
    :class="{
      'chat-app--mobile': isMobile,
      'chat-app--tablet': isTablet,
      'chat-app--sidebar-open': isMobile && sidebarOpen,
    }"
  >
    <div v-if="isMobile && sidebarOpen" class="sidebar-scrim" aria-hidden="true" @click="sidebarOpen = false" />
    <ChatSidebar
      :convs="convs"
      :conv-id="convId"
      :logged-in-username="loggedInUsername"
      :me-page-path="mePagePath"
      :drawer-open="isMobile && sidebarOpen"
      @select="selectConv"
      @new-conv="newConv"
      @logout="logoutUser"
      @login="authOpen = true"
    />
    <UserAuthDialog v-model="authOpen" @done="onAuthDone" />

    <section class="main">
      <header v-if="isMobile" class="mobile-nav">
        <button
          type="button"
          class="mobile-nav-btn"
          :aria-label="t('chat.ariaOpenConvs')"
          @click="sidebarOpen = !sidebarOpen"
        >
          <el-icon :size="22"><Menu /></el-icon>
        </button>
        <span class="mobile-nav-title">{{ activeTitle }}</span>
        <LocaleThemeToolbar v-if="isMobile" compact class="thread-head-tools" />
        <button
          type="button"
          class="mobile-nav-btn mobile-nav-btn--accent"
          :aria-label="t('chat.ariaNewChat')"
          @click="onMobileNewConv"
        >
          <el-icon :size="22"><Plus /></el-icon>
        </button>
      </header>
      <header class="thread-head">
        <div class="thread-head-row">
          <h1 v-if="!isMobile" class="thread-title">{{ activeTitle }}</h1>
          <LocaleThemeToolbar v-if="!isMobile" compact class="thread-head-tools" />
        </div>
        <p class="thread-hint">
          {{ isMobile ? t("chat.threadHintMobile") : t("chat.threadHintDesktop") }}
        </p>
        <p v-if="sessionTokenTotal > 0" class="thread-tokens" v-html="t('chat.tokenLine', { n: sessionTokenTotal })" />
      </header>

      <el-scrollbar ref="scrollAreaRef" class="messages-scroll" tag="div">
        <div class="messages-scroll-inner">
        <div v-if="messages.length === 0" class="empty">
          <div class="empty-brand">{{ t("chat.emptyBrand") }}</div>
          <p class="empty-welcome">{{ t("chat.emptyWelcome") }}</p>
          <div class="quick-prompts" role="list">
            <button
              v-for="q in emptyStarterPrompts"
              :key="q.id ?? q.text"
              type="button"
              class="quick-prompt-chip"
              @click="applyStarterPrompt(q, 'EMPTY')"
            >
              {{ q.text }}
            </button>
            <button
              type="button"
              class="quick-prompt-chip quick-prompt-chip--refresh"
              :disabled="emptyPromptsLoading"
              @click="refreshEmptyStarterPrompts"
            >
              {{ t("chat.starterRefresh") }}
            </button>
          </div>
        </div>
        <div v-else class="messages">
          <div
            v-for="(m, idx) in messages"
            :key="messageRowKey(m, idx)"
            :class="['bubble-row', m.role === 'user' ? 'user' : 'assistant']"
          >
            <div
              class="avatar"
              :class="m.role === 'user' ? 'avatar--user' : 'avatar--assistant'"
              :aria-label="m.role === 'user' ? t('chat.roleMe') : t('chat.roleAssistant')"
            >
              <el-icon :size="17">
                <User v-if="m.role === 'user'" />
                <ChatLineRound v-else />
              </el-icon>
            </div>
            <div class="bubble">
              <div
                v-if="m.role === 'assistant' && showAssistantReasoningShell(m)"
                class="reasoning"
              >
                <button
                  type="button"
                  class="reasoning-bar"
                  :class="{ 'reasoning-bar--live': m.reasoningStreaming }"
                  @click="onReasoningBarClick(m)"
                >
                  <span class="reasoning-bar-title">{{ t("chat.reasoningTitle") }}</span>
                  <span v-if="m.reasoningStreaming" class="reasoning-live">{{ intentReasoningLiveLabel(m) }}</span>
                  <span v-else class="reasoning-meta">
                    {{ m.reasoningCollapsed ? t("chat.reasoningCollapsed") : t("chat.reasoningExpand") }}
                  </span>
                  <el-icon class="reasoning-chevron">
                    <ArrowDown v-if="!isReasoningBodyVisible(m)" />
                    <ArrowUp v-else />
                  </el-icon>
                </button>
                <div v-show="isReasoningBodyVisible(m)" class="reasoning-body-wrap">
                  <div class="reasoning-body">
                    <template v-if="m.reasoning && m.reasoning.length">
                      {{ m.reasoning }}<span v-if="m.reasoningStreaming" class="cursor" />
                    </template>
                    <p
                      v-else-if="reasoningIntentOrchestrationHint(m)"
                      class="reasoning-intent-hint"
                      v-html="t('chat.reasoningIntentHint')"
                    />
                    <span v-else-if="m.reasoningStreaming" class="cursor" />
                  </div>
                </div>
              </div>
              <div v-if="m.role === 'assistant' && showIntentWorkflowShell(m)" class="intent-workflow">
                <div class="intent-workflow-shell">
                  <div class="intent-workflow-head">{{ t("chat.workflowHead") }}</div>
                  <div v-for="seg in m.workflowSegments" :key="seg.segmentId" class="wf-step">
                    <button
                      type="button"
                      class="wf-bar"
                      :class="{ 'wf-bar--live': seg.status !== 'done' }"
                      @click="onWorkflowBarClick(m, seg)"
                    >
                      <span class="wf-bar-lead" aria-hidden="true">
                        <el-icon v-if="seg.status === 'done'" class="wf-bar-done" :size="17">
                          <CircleCheck />
                        </el-icon>
                        <el-icon v-else class="wf-spin" :size="17">
                          <Loading />
                        </el-icon>
                      </span>
                      <span class="wf-bar-title">{{ wfSegmentBarTitle(seg) }}</span>
                      <span v-if="seg.status === 'loading'" class="wf-bar-meta">{{ t("chat.wfProcessing") }}</span>
                      <span v-else-if="seg.status === 'streaming'" class="wf-bar-meta">{{ t("chat.wfStreaming") }}</span>
                      <span v-else-if="seg.status === 'done'" class="wf-bar-meta">
                        {{
                          isWfSegmentBodyVisible(seg) ? t("chat.wfDoneCollapse") : t("chat.wfDoneExpand")
                        }}
                      </span>
                      <span v-else class="wf-bar-meta">{{ t("chat.wfWaiting") }}</span>
                      <el-icon class="wf-bar-chevron">
                        <ArrowDown v-if="!isWfSegmentBodyVisible(seg)" />
                        <ArrowUp v-else />
                      </el-icon>
                    </button>
                    <div v-show="isWfSegmentBodyVisible(seg)" class="wf-body-outer">
                      <div v-if="seg.status === 'loading'" class="wf-loading">
                        <el-icon class="wf-spin" :size="18"><Loading /></el-icon>
                        <span>{{ t("chat.wfProcessing") }}</span>
                      </div>
                      <template v-else-if="seg.status === 'streaming'">
                        <div
                          class="wf-body-md bubble-md bubble-md--streaming"
                          v-html="workflowSegmentStreamingHtml(seg)"
                        />
                      </template>
                      <template v-else-if="seg.status === 'done'">
                        <div class="wf-body-md bubble-md" v-html="workflowSegmentRichHtml(seg.text || '')" />
                      </template>
                      <div v-else class="wf-loading">
                        <el-icon class="wf-spin" :size="18"><Loading /></el-icon>
                        <span>{{ t("chat.wfWaiting") }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div
                v-if="m.role === 'assistant' && showAssistantMdBubble(m)"
                class="bubble-inner bubble-inner--assistant"
              >
                <div
                  class="bubble-md"
                  :class="{ 'bubble-md--streaming': m.streaming }"
                  v-html="assistantMdStreamingHtml(m)"
                />
              </div>
              <div v-else-if="m.role === 'user'" class="bubble-inner bubble-inner--user">
                <span class="user-msg-text">{{ m.content }}<span v-if="m.streaming" class="cursor" /></span>
                <button
                  type="button"
                  class="user-msg-copy-btn"
                  :title="t('chat.copyQuestion')"
                  :aria-label="t('chat.copyQuestion')"
                  @click="copyUserMessage(m, idx)"
                >
                  <el-icon v-if="userCopyFlashKey !== rowUserCopyKeyUser(idx)" :size="16"><DocumentCopy /></el-icon>
                  <el-icon v-else class="msg-act-ico-success" :size="16"><CircleCheck /></el-icon>
                </button>
                <div v-if="m.attachments?.length" class="user-msg-attach-strip" role="list">
                  <span class="rag-sources-label">{{ t("chat.attachments") }}</span>
                  <span
                    v-for="a in m.attachments"
                    :key="a.id"
                    class="user-msg-attach-chip"
                    :title="a.fileName + (a.charLength != null ? t('chat.attachChars', { n: a.charLength }) : '')"
                  >
                    <el-icon class="user-msg-attach-ico"><Document /></el-icon>
                    <span class="user-msg-attach-name">{{ truncateName(a.fileName) }}</span>
                  </span>
                </div>
              </div>
              <div
                v-if="m.role === 'user' && m.intentTurnHit"
                class="intent-hit-hint"
                role="note"
              >
                <template v-if="m.intentTurnHit.keywordPhrase">
                  {{ t("chat.intentKeyword", { phrase: m.intentTurnHit.keywordPhrase }) }}
                  <span class="intent-hit-sub">（{{ formatIntentMatchSource(m.intentTurnHit.matchSource) }}）</span>
                </template>
                <template v-else>
                  {{ t("chat.intentFlow") }}
                  <span class="intent-hit-sub">（{{ formatIntentMatchSource(m.intentTurnHit.matchSource) }}）</span>
                </template>
              </div>
              <div
                v-if="m.role === 'assistant' && m.ragRetrievalTitles?.length"
                class="rag-sources-strip"
                role="status"
                aria-live="polite"
              >
                <span class="rag-sources-label">{{ t("chat.refDocs") }}</span>
                <span v-for="(t, ti) in m.ragRetrievalTitles" :key="`${ti}-${t}`" class="rag-doc-chip">{{ t }}</span>
              </div>
              <div
                v-if="m.webSearchReferences?.length && m.role === 'assistant'"
                class="web-search-refs-strip"
                :class="{ 'web-search-refs-strip--foldable': webSearchRefsFoldable(m.webSearchReferences) }"
              >
                <button
                  v-if="webSearchRefsFoldable(m.webSearchReferences)"
                  type="button"
                  class="web-search-refs-bar"
                  @click="onWebSearchRefsBarClick(m)"
                >
                  <span class="rag-sources-label">{{ t("chat.webRefs") }}</span>
                  <span class="web-search-refs-meta">
                    {{
                      isWebSearchRefsBodyVisible(m)
                        ? t("chat.webRefsExpand")
                        : t("chat.webRefsCollapsed", { n: m.webSearchReferences!.length })
                    }}
                  </span>
                  <el-icon class="web-search-refs-chevron">
                    <ArrowDown v-if="!isWebSearchRefsBodyVisible(m)" />
                    <ArrowUp v-else />
                  </el-icon>
                </button>
                <span v-else class="rag-sources-label">{{ t("chat.webRefs") }}</span>
                <div v-show="isWebSearchRefsBodyVisible(m)" class="web-search-refs-body" role="list">
                  <template v-for="(w, wi) in m.webSearchReferences" :key="`${wi}-${w.url || w.title || ''}`">
                    <a
                      v-if="(w.url ?? '').trim()"
                      class="web-ref-chip"
                      :href="w.url"
                      target="_blank"
                      rel="noopener noreferrer"
                      :title="(w.summary || '').trim() || undefined"
                    >
                      <img
                        v-if="(w.logoUrl ?? '').trim()"
                        class="web-ref-logo"
                        :src="w.logoUrl!"
                        alt=""
                      />
                      <span class="web-ref-chip-text">{{ webRefLabel(w) }}</span>
                    </a>
                    <span
                      v-else
                      class="web-ref-chip web-ref-chip--nolink"
                      :title="(w.summary || '').trim() || undefined"
                    >
                      <img
                        v-if="(w.logoUrl ?? '').trim()"
                        class="web-ref-logo"
                        :src="w.logoUrl!"
                        alt=""
                      />
                      <span class="web-ref-chip-text">{{ webRefLabel(w) }}</span>
                    </span>
                  </template>
                </div>
              </div>
              <div
                v-if="
                  m.role === 'assistant' &&
                  !m.streaming &&
                  m.id &&
                  (m.followUpPrompts?.length ?? 0) > 0
                "
                class="follow-up-prompts"
                role="list"
              >
                <span class="follow-up-prompts-label">{{ t("chat.followUpLabel") }}</span>
                <button
                  v-for="fp in m.followUpPrompts"
                  :key="fp.id ?? fp.text"
                  type="button"
                  class="quick-prompt-chip quick-prompt-chip--compact"
                  @click="applyStarterPrompt(fp, 'FOLLOW_UP')"
                >
                  {{ fp.text }}
                </button>
              </div>
              <div
                v-if="m.role === 'assistant' && (m.modelAlias || showAssistantMainBubble(m))"
                class="model-meta-row"
                :class="{ 'model-meta-row--streaming': m.streaming && showAssistantMainBubble(m) }"
              >
                <span v-if="m.modelAlias" class="model-meta">{{ t("chat.modelLabel", { alias: m.modelAlias }) }}</span>
                <div
                  v-if="showAssistantMainBubble(m)"
                  class="msg-actions msg-actions--after-model"
                  :class="{ 'msg-actions--during-stream': m.streaming }"
                  role="toolbar"
                  :aria-label="t('chat.msgActionsAria')"
                >
                  <div class="msg-actions-bar">
                  <span class="msg-copy-group">
                    <el-dropdown
                      class="msg-copy-dd"
                      split-button
                      type="default"
                      size="small"
                      trigger="click"
                      popper-class="msg-actions-dd-popper"
                      @click="copyAssistantPlain(m, idx)"
                      @command="(cmd: string) => onAssistantCopyMenu(cmd, m, idx)"
                    >
                      <span class="msg-copy-dd-main">
                        <el-icon v-if="copyFlashKey !== rowCopyKey(m, idx)" class="msg-copy-dd-ico" :size="17">
                          <DocumentCopy />
                        </el-icon>
                        <el-icon v-else class="msg-copy-dd-ico msg-act-ico-success" :size="17"><CircleCheck /></el-icon>
                      </span>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="md">{{ t("chat.copyAsMd") }}</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </span>
                  <div
                    v-if="assistantVariantNavShow(m)"
                    class="msg-variant-nav"
                    role="group"
                    :aria-label="t('chat.variantNavAria')"
                  >
                    <button
                      type="button"
                      class="msg-variant-btn"
                      :disabled="(m.activeVariantIndex ?? 0) <= 0"
                      :aria-label="t('chat.variantPrevAria')"
                      @click="stepAssistantVariant(m, -1)"
                    >
                      <el-icon :size="14"><ArrowLeft /></el-icon>
                    </button>
                    <span class="msg-variant-txt">
                      <span class="msg-variant-cur">{{ (m.activeVariantIndex ?? 0) + 1 }}</span>
                      <span class="msg-variant-slash"> / </span>
                      <span class="msg-variant-tot">{{ m.replyVariants!.length }}</span>
                    </span>
                    <button
                      type="button"
                      class="msg-variant-btn"
                      :disabled="(m.activeVariantIndex ?? 0) >= m.replyVariants!.length - 1"
                      :aria-label="t('chat.variantNextAria')"
                      @click="stepAssistantVariant(m, 1)"
                    >
                      <el-icon :size="14"><ArrowRight /></el-icon>
                    </button>
                  </div>
                  <span class="msg-actions-sep" aria-hidden="true" />
                  <div v-if="m.id" class="msg-act-vote" role="group" :aria-label="t('chat.voteAria')">
                    <button
                      type="button"
                      class="msg-act-ico-btn"
                      :class="{ 'msg-act-ico-btn--like-on': m.userFeedback === 'LIKE' }"
                      :title="t('chat.likeTitle')"
                      :disabled="feedbackSendingId === m.id || !assistantFeedbackEligible(m)"
                      @click="toggleAssistantLike(m)"
                    >
                      <el-icon :size="17">
                        <StarFilled v-if="m.userFeedback === 'LIKE'" />
                        <Star v-else />
                      </el-icon>
                    </button>
                    <button
                      type="button"
                      class="msg-act-ico-btn"
                      :class="{ 'msg-act-ico-btn--dislike-on': m.userFeedback === 'DISLIKE' }"
                      :title="t('chat.dislikeTitle')"
                      :disabled="feedbackSendingId === m.id || !assistantFeedbackEligible(m)"
                      @click="toggleAssistantDislike(m)"
                    >
                      <svg class="msg-thumb-svg msg-thumb-svg--down" viewBox="0 0 24 24" aria-hidden="true">
                        <path
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.75"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11a2 2 0 0 0 2-2v-4a2 2 0 0 0-2-2h-4zM7 22V10H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h3z"
                        />
                      </svg>
                    </button>
                  </div>
                  <span v-if="m.id" class="msg-actions-sep" aria-hidden="true" />
                  <button
                    type="button"
                    class="msg-act-ico-btn"
                    :title="t('chat.shareTitle')"
                    @click="openShareDialog(m, idx)"
                  >
                    <el-icon :size="17"><Share /></el-icon>
                  </button>
                  <button
                    v-if="isLastAssistantIndex(idx) && assistantLatestPersistedDbId(m) != null"
                    type="button"
                    class="msg-act-ico-btn"
                    :title="t('chat.regenerateTitle')"
                    :disabled="sending"
                    @click="retryAssistantAt(idx)"
                  >
                    <el-icon :size="17"><RefreshRight /></el-icon>
                  </button>
                  <el-dropdown
                    trigger="click"
                    popper-class="msg-actions-dd-popper"
                    @command="(cmd: string) => onAssistantMore(cmd, idx)"
                  >
                    <button type="button" class="msg-act-ico-btn" :title="t('chat.moreTitle')" :aria-label="t('chat.moreAria')">
                      <el-icon :size="17"><More /></el-icon>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="report">{{ t("chat.report") }}</el-dropdown-item>
                        <el-dropdown-item command="editPrompt">{{ t("chat.editPrompt") }}</el-dropdown-item>
                        <el-dropdown-item command="speak">{{ t("chat.speak") }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  </div>
                </div>
              </div>
              <div
                v-if="m.role === 'assistant' && m.usage && m.usage.totalTokens > 0"
                class="token-meta"
              >
                {{
                  t("chat.tokenLineMsg", {
                    total: m.usage.totalTokens,
                    prompt: m.usage.promptTokens,
                    completion: m.usage.completionTokens,
                  })
                }}
              </div>
            </div>
          </div>
        </div>
        </div>
      </el-scrollbar>

      <footer class="composer">
        <div
          class="composer-surface"
          :class="{ 'composer-surface--drag': dragOver }"
          @dragenter.prevent="onDragEnter"
          @dragleave.prevent="onDragLeave"
          @dragover.prevent
          @drop.prevent="onDropFiles"
        >
          <div v-if="pendingFiles.length" class="attach-strip">
            <div class="attach-chips">
              <span v-for="(f, i) in pendingFiles" :key="`${i}-${f.name}-${f.size}`" class="attach-chip">
                <el-icon class="attach-chip-icon"><Document /></el-icon>
                <span class="attach-chip-name" :title="f.name">{{ truncateName(f.name) }}</span>
                <button
                  type="button"
                  class="attach-chip-remove"
                  :aria-label="t('chat.removeAttachAria')"
                  @click="removePending(i)"
                >
                  <span aria-hidden="true">×</span>
                </button>
              </span>
            </div>
            <span class="attach-limit">{{ pendingFiles.length }}/{{ maxAttachmentsLimit }}</span>
          </div>
          <div class="composer-input-wrap">
            <button
              v-show="input.length > 0"
              type="button"
              class="input-clear-btn"
              :title="t('chat.clearInput')"
              :aria-label="t('chat.clearInputAria')"
              @click="input = ''"
            >
              {{ t("chat.clearInput") }}
            </button>
            <el-input
              v-model="input"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 8 }"
              resize="none"
              maxlength="8000"
              :placeholder="t('chat.inputPlaceholder')"
              class="composer-input"
              @keydown="onKeydown"
            />
          </div>
          <div class="composer-footer-bar">
            <div class="footer-bar-primary">
              <el-upload
                class="footer-upload"
                :disabled="attachDisabled"
                :auto-upload="false"
                :show-file-list="false"
                multiple
                :on-change="onFilePick"
              >
                <template #trigger>
                  <button
                    type="button"
                    class="footer-icon-btn"
                    :disabled="attachDisabled"
                    :title="t('chat.addAttachTitle')"
                    :aria-label="t('chat.addAttachAria')"
                  >
                    <el-icon :size="22"><Paperclip /></el-icon>
                  </button>
                </template>
              </el-upload>
              <span class="footer-vdiv" aria-hidden="true" />
              <el-select
                v-model="modelAlias"
                class="model-pill-select"
                :placeholder="t('chat.selectModel')"
                size="default"
                :disabled="!models.length"
                :style="{ width: modelSelectWidthPx + 'px' }"
                popper-class="model-select-dropdown"
              >
                <el-option
                  v-for="m in models"
                  :key="m.alias"
                  :label="modelOptionLabel(m)"
                  :value="m.alias"
                  :disabled="m.quotaExhausted === true"
                />
              </el-select>
              <div
                v-if="currentModel?.supportsThinking || webSearchAllowed"
                class="deep-think-group"
              >
                <div v-if="currentModel?.supportsThinking" class="deep-think-wrap">
                  <button
                    type="button"
                    class="deep-think-toggle deep-think-toggle--think"
                    :class="{ 'deep-think-toggle--on': thinkingEnabled }"
                    :aria-pressed="thinkingEnabled"
                    :aria-label="t('chat.thinkingAria')"
                    @click="thinkingEnabled = !thinkingEnabled"
                  >
                    <span
                      class="deep-think-toggle-dot"
                      :class="{ 'deep-think-toggle-dot--live': thinkingEnabled }"
                      aria-hidden="true"
                    />
                    {{ t("chat.thinking") }}
                  </button>
                </div>
                <div v-if="webSearchAllowed" class="deep-think-wrap">
                  <button
                    type="button"
                    class="deep-think-toggle deep-think-toggle--web"
                    :class="{ 'deep-think-toggle--on': webSearchEnabled }"
                    :aria-pressed="webSearchEnabled"
                    :aria-label="t('chat.webSearchAria')"
                    @click="webSearchEnabled = !webSearchEnabled"
                  >
                    <span
                      class="deep-think-toggle-dot"
                      :class="{ 'deep-think-toggle-dot--live': webSearchEnabled }"
                      aria-hidden="true"
                    />
                    {{ t("chat.webSearch") }}
                  </button>
                </div>
              </div>
            </div>
            <el-button
              class="send-fab"
              type="primary"
              circle
              :loading="false"
              :disabled="sending ? false : !canSend"
              :aria-label="sending ? t('chat.ariaStop') : t('chat.ariaSend')"
              @click="sending ? stopGenerating() : send()"
            >
              <el-icon v-if="sending"><VideoPause /></el-icon>
              <el-icon v-else><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
        <p class="composer-note">{{ t("chat.composerNote") }}</p>
      </footer>
    </section>

    <ChatShareDialog
      v-model="shareOpen"
      :conversation-id="convId"
      :conversation-title="activeTitle"
      :messages="messages"
      :anchor-assistant-idx="shareAnchorAssistantIdx"
      :tenant-code="tenantCodeParam"
    />
  </div>
</template>

<script setup lang="ts">
import type { UploadFile } from "element-plus";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  ChatLineRound,
  CircleCheck,
  Document,
  DocumentCopy,
  Loading,
  Menu,
  More,
  Paperclip,
  Plus,
  Promotion,
  VideoPause,
  RefreshRight,
  Share,
  Star,
  StarFilled,
  User,
} from "@element-plus/icons-vue";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import { useWindowBreakpoints } from "../../composables/useWindowBreakpoints";
import ChatSidebar from "../../components/chat/ChatSidebar.vue";
import ChatShareDialog from "../../components/chat/ChatShareDialog.vue";
import LocaleThemeToolbar from "../../components/LocaleThemeToolbar.vue";
import UserAuthDialog from "../../components/UserAuthDialog.vue";
import * as chatApi from "../../api/chat";
import { AI_USER_ACCESS_TOKEN_KEY, clearUserSession } from "../../plugins/http";
import { TENANT_CODE_PATH_RE } from "../../utils/outboundTenant";
import { copyTextToUserClipboard } from "../../utils/clipboard";
import { renderMarkdownToSafeHtml, renderStreamingMarkdownToSafeHtml } from "../../utils/renderMarkdown";
import { apiRequestErrorMessage } from "../../utils/apiRequestErrorMessage";
import { isAbortError } from "../../utils/isAbortError";
import { toChatResponseLocale } from "../../utils/chatResponseLocale";
import { useUiPreferencesStore } from "../../stores/uiPreferences";

const route = useRoute();
const { t, locale } = useI18n();
const uiPrefs = useUiPreferencesStore();
const chatResponseLocale = computed(() => toChatResponseLocale(uiPrefs.locale));
const { isMobile, isTablet } = useWindowBreakpoints();
const sidebarOpen = ref(false);

/** 每次发起新的助手流式回复自增；丢弃代数已过期的 SSE 分帧，避免上一轮 {@code ragDoc} 写入本轮气泡。 */
let assistantStreamGeneration = 0;
let activeStreamAbort: AbortController | null = null;

function cancelActiveStream() {
  assistantStreamGeneration++;
  activeStreamAbort?.abort();
  activeStreamAbort = null;
}

function beginActiveStream(): { signal: AbortSignal; generation: number } {
  activeStreamAbort?.abort();
  activeStreamAbort = null;
  const generation = ++assistantStreamGeneration;
  const ac = new AbortController();
  activeStreamAbort = ac;
  return { signal: ac.signal, generation };
}

const tenantCodeParam = computed(() => {
  const raw = route.params.tenantCode;
  return typeof raw === "string" && TENANT_CODE_PATH_RE.test(raw)
    ? raw
    : String(import.meta.env.VITE_TENANT_CODE || "default");
});

const mePagePath = computed(() => `/${tenantCodeParam.value}/system/me`);

watch(isMobile, (m) => {
  if (!m) sidebarOpen.value = false;
});

/** 模型下拉触发器宽度：随当前展示文案变化（避免占满半行）。 */
function measureSelectLabelWidthPx(label: string, extraPad = 44): number {
  if (typeof document === "undefined") return 120;
  const s = document.createElement("span");
  s.textContent = label || t("chat.selectModel");
  s.style.cssText =
    "position:fixed;left:-9999px;top:0;visibility:hidden;white-space:nowrap;font-size:13px;font-weight:400;font-family:'PingFang SC','Microsoft YaHei',system-ui,sans-serif";
  document.body.appendChild(s);
  const textW = s.getBoundingClientRect().width;
  document.body.removeChild(s);
  return Math.min(280, Math.max(72, Math.ceil(textW + extraPad)));
}

/** 移动端：为附件、发送、思考/联网等预留宽度后再给模型选择器封顶。 */
function mobileModelSelectMaxWidthPx(
  hasThinking: boolean,
  hasWebSearch: boolean,
  viewportW: number,
): number {
  let reserved = 40 + 48 + 28;
  if (hasThinking) reserved += 50;
  if (hasWebSearch) reserved += 50;
  if (hasThinking && hasWebSearch) reserved += 6;
  const cap = viewportW - reserved;
  return Math.max(84, Math.min(148, cap));
}

/** 与下拉项字号接近，用于量最长选项宽度（略小于真实 padding，后面统一加余量）。 */
function measureDropdownOptionTextWidthPx(label: string): number {
  if (typeof document === "undefined") return 100;
  const s = document.createElement("span");
  s.textContent = label || "";
  s.style.cssText =
    "position:fixed;left:-9999px;top:0;visibility:hidden;white-space:nowrap;font-size:14px;font-weight:400;font-family:'PingFang SC','Microsoft YaHei',system-ui,sans-serif";
  document.body.appendChild(s);
  const w = Math.ceil(s.getBoundingClientRect().width);
  document.body.removeChild(s);
  return w;
}

/** 单条助手答复的一版内容（重新生成后多版并存于 {@link Msg.replyVariants}） */
type ReplyVariant = {
  id?: number;
  content: string;
  reasoning?: string;
  reasoningCollapsed?: boolean;
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number };
  modelAlias?: string;
  userFeedback?: string;
  /** 重新生成流式过程中按 SSE {@code ragDoc} 累积的检索文档标题 */
  ragRetrievalTitles?: string[];
  /** 联网引用（SSE {@code webSearchRefs}；历史由 meta {@code webSearchReferences} 恢复） */
  webSearchReferences?: chatApi.WebSearchRefItem[];
  /** 引用条数较多时默认折叠 */
  webSearchRefsCollapsed?: boolean;
};

type Msg = {
  id?: number;
  /** 乐观插入、尚未有库表 id 时用于列表 :key，避免仅用序号复用上一轮气泡 DOM（含「参考文档」条） */
  clientRowKey?: string;
  role: "user" | "assistant";
  content: string;
  /** 当前轮次检索到的文档标题（SSE {@code ragDoc} 帧；刷新后由接口 {@code ragCitations} 恢复） */
  ragRetrievalTitles?: string[];
  /** 联网检索引用（SSE {@code webSearchRefs}；刷新后由 {@code webSearchReferences} 恢复） */
  webSearchReferences?: chatApi.WebSearchRefItem[];
  /** 引用条数较多时默认折叠 */
  webSearchRefsCollapsed?: boolean;
  streaming?: boolean;
  reasoning?: string;
  reasoningStreaming?: boolean;
  /** 思考流结束后默认 true（折叠）；流式中为 false/undefined 表示展开 */
  reasoningCollapsed?: boolean;
  /** 助手消息落库 meta 或 SSE end 帧中的 token 用量 */
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number };
  modelAlias?: string;
  userFeedback?: string;
  /** 多版回答（服务端 meta {@code priorVersions} + 当前正文；刷新后仍可从历史接口恢复） */
  replyVariants?: ReplyVariant[];
  activeVariantIndex?: number;
  /** 意图工作流阶段（SSE {@code workflowStage}；历史由 {@code workflowSegments} 恢复） */
  workflowSegments?: chatApi.WorkflowStagePayload[];
  /** 本回合意图命中（历史接口 {@code intentTurnHit}） */
  intentTurnHit?: chatApi.ChatIntentTurnHit;
  /** 用户消息已上传附件（历史或发送后回显） */
  attachments?: chatApi.ChatAttachmentMessage[];
  /** 助手回复后「猜你想问」（接口按需拉取） */
  followUpPrompts?: chatApi.StarterPromptItem[];
};

/** 后端 {@code segmentId} 缺省时用于顶栏标题（与 Java 侧步骤 id 对齐）。 */
const WF_TITLE_FALLBACK_KEY: Record<string, string> = {
  "doc-parse": "chat.wfDocParse",
  "doc-need-file": "chat.wfDocNeedFile",
  "doc-parse-empty": "chat.wfDocParse",
  "doc-parse-error": "chat.wfDocParse",
  "doc-expired": "chat.wfDocNeedFile",
  "plan-apply": "chat.wfPlanApply",
  "plan-no-apply": "chat.wfPlanNoApply",
  "plan-conflict": "chat.wfPlanConflict",
  "plan-kb": "chat.wfPlanKb",
  "plan-final": "chat.wfPlanFinal",
};

function wfSegmentBarTitle(seg: chatApi.WorkflowStagePayload): string {
  const titleText = (seg.title ?? "").trim();
  if (titleText) return titleText;
  const key = WF_TITLE_FALLBACK_KEY[seg.segmentId];
  return key ? t(key) : t("chat.wfStepDefault");
}

/** 进行中步骤始终展开；已完成步骤在新步骤进入 loading 时自动折叠。未完成（含等待）始终展开。 */
function isWfSegmentBodyVisible(seg: chatApi.WorkflowStagePayload): boolean {
  if (seg.status === "loading" || seg.status === "streaming" || seg.status !== "done") {
    return true;
  }
  return seg.wfCollapsed !== true;
}

function onWorkflowBarClick(m: Msg, seg: chatApi.WorkflowStagePayload) {
  if (seg.status !== "done") {
    return;
  }
  const row = m.workflowSegments?.find((x) => x.segmentId === seg.segmentId);
  if (!row) return;
  row.wfCollapsed = !row.wfCollapsed;
}

/** 历史消息：除最后一步外默认折叠，避免长流程占屏。 */
function decorateHistoryWorkflowSegments(
  segs: chatApi.WorkflowStagePayload[],
): chatApi.WorkflowStagePayload[] {
  const n = segs.length;
  return segs.map((s, i) => ({
    ...s,
    wfCollapsed: n > 1 && i < n - 1,
  }));
}

/** 将落库 meta 中的 RAG 引用转为对话条顶栏展示用标题列表（去重、去空）。 */
function mergeWorkflowStage(m: Msg, stage: chatApi.WorkflowStagePayload) {
  if (m.role !== "assistant") return;
  if (!m.workflowSegments) {
    m.workflowSegments = [];
  }
  const arr = m.workflowSegments;
  const i = arr.findIndex((x) => x.segmentId === stage.segmentId);
  if (stage.status === "loading") {
    for (const x of arr) {
      if (x.segmentId !== stage.segmentId && x.status === "done") {
        x.wfCollapsed = true;
      }
    }
  }
  const prev = i >= 0 ? arr[i]! : undefined;
  const row: chatApi.WorkflowStagePayload = { ...stage };
  if (stage.status === "loading" || stage.status === "streaming") {
    row.wfCollapsed = false;
  } else if (stage.status === "done") {
    row.wfCollapsed = prev?.wfCollapsed ?? false;
  }
  if (i >= 0) {
    arr.splice(i, 1, row);
  } else {
    arr.push(row);
  }
}

function citationsToTitles(c: chatApi.RagCitationItem[] | null | undefined): string[] | undefined {
  if (!c?.length) return undefined;
  const titles = [...new Set(c.map((x) => (x.documentTitle ?? "").trim()).filter(Boolean))];
  return titles.length ? titles : undefined;
}

/** 超过该条数时联网参考默认折叠 */
const WEB_SEARCH_REFS_FOLD_THRESHOLD = 4;

function webSearchRefsFoldable(refs: chatApi.WebSearchRefItem[] | null | undefined): boolean {
  return (refs?.length ?? 0) > WEB_SEARCH_REFS_FOLD_THRESHOLD;
}

function defaultWebSearchRefsCollapsed(
  refs: chatApi.WebSearchRefItem[] | null | undefined,
): boolean | undefined {
  return webSearchRefsFoldable(refs) ? true : undefined;
}

function isWebSearchRefsBodyVisible(m: Msg): boolean {
  if (!webSearchRefsFoldable(m.webSearchReferences)) {
    return true;
  }
  return m.webSearchRefsCollapsed !== true;
}

function onWebSearchRefsBarClick(m: Msg) {
  const nextCollapsed = isWebSearchRefsBodyVisible(m);
  m.webSearchRefsCollapsed = nextCollapsed;
  if (m.replyVariants?.length) {
    const v = m.replyVariants[m.activeVariantIndex ?? 0];
    if (v) {
      v.webSearchRefsCollapsed = nextCollapsed;
    }
  }
}

function webRefLabel(w: chatApi.WebSearchRefItem): string {
  const titleText = (w.title ?? "").trim();
  if (titleText) return titleText;
  const s = (w.siteName ?? "").trim();
  if (s) return s;
  const u = (w.url ?? "").trim();
  if (!u) return t("chat.intentSourceFallback");
  try {
    return new URL(u).hostname;
  } catch {
    return u.slice(0, 48);
  }
}

/** 将后端 {@code ChatIntentMatchSource} 枚举名转为简短说明 */
function formatIntentMatchSource(src: string | null | undefined): string {
  if (!src) return t("chat.intentMatchFallback");
  const map: Record<string, string> = {
    TRIGGER_PHRASE: "chat.intentSrcTrigger",
    PLAN_CONTINUE_PHRASE: "chat.intentSrcPlanContinue",
    PLAN_CONTINUE_DEFAULT_PHRASE: "chat.intentSrcPlanContinueDefault",
    PLAN_CONTINUE_REGEX: "chat.intentSrcPlanContinueRegex",
    DOC_ATTACHMENT: "chat.intentSrcDocAttachment",
  };
  const key = map[src];
  return key ? t(key) : src.replace(/_/g, " ");
}

function priorApiRowToVariant(pv: chatApi.PriorAssistantVersion): ReplyVariant {
  const usage =
    pv.totalTokens != null && pv.totalTokens > 0
      ? {
          promptTokens: pv.promptTokens ?? 0,
          completionTokens: pv.completionTokens ?? 0,
          totalTokens: pv.totalTokens,
        }
      : undefined;
  const hasR = !!(pv.reasoning && pv.reasoning.length > 0);
  return {
    content: pv.content ?? "",
    reasoning: pv.reasoning ?? undefined,
    reasoningCollapsed: hasR ? true : undefined,
    usage,
    modelAlias: pv.modelAlias ?? undefined,
  };
}

function mapHistoryToMsgs(rows: chatApi.ChatHistoryMessage[]): Msg[] {
  const out: Msg[] = [];
  for (const r of rows) {
    if (r.role !== "user" && r.role !== "assistant") continue;
    const usage =
      r.totalTokens != null && r.totalTokens > 0
        ? {
            promptTokens: r.promptTokens ?? 0,
            completionTokens: r.completionTokens ?? 0,
            totalTokens: r.totalTokens,
          }
        : undefined;
    const hasReason = !!(r.reasoning && r.reasoning.length > 0);
    if (r.role === "assistant" && r.priorVersions && r.priorVersions.length > 0) {
      const ragTitles = citationsToTitles(r.ragCitations);
      const ragForTail = ragTitles ? [...ragTitles] : undefined;
      const ragForFlat = ragTitles ? [...ragTitles] : undefined;
      const webRefs =
        r.webSearchReferences && r.webSearchReferences.length > 0
          ? [...r.webSearchReferences]
          : undefined;
      const variants: ReplyVariant[] = r.priorVersions.map(priorApiRowToVariant);
      variants.push({
        id: r.id,
        content: r.content ?? "",
        reasoning: r.reasoning ?? undefined,
        reasoningCollapsed: hasReason ? true : undefined,
        usage,
        modelAlias: r.modelAlias ?? undefined,
        userFeedback: r.userFeedback ?? undefined,
        ragRetrievalTitles: ragForTail,
        webSearchReferences: webRefs,
        webSearchRefsCollapsed: defaultWebSearchRefsCollapsed(webRefs),
      });
      out.push({
        id: r.id,
        role: "assistant",
        content: r.content ?? "",
        reasoning: r.reasoning ?? undefined,
        reasoningCollapsed: hasReason ? true : undefined,
        usage,
        modelAlias: r.modelAlias ?? undefined,
        userFeedback: r.userFeedback ?? undefined,
        ragRetrievalTitles: ragForFlat,
        webSearchReferences: webRefs,
        webSearchRefsCollapsed: defaultWebSearchRefsCollapsed(webRefs),
        replyVariants: variants,
        activeVariantIndex: variants.length - 1,
        workflowSegments:
          r.workflowSegments && r.workflowSegments.length > 0
            ? decorateHistoryWorkflowSegments([...r.workflowSegments])
            : undefined,
        intentTurnHit: r.intentTurnHit ?? undefined,
      });
      continue;
    }
    const ragSingle = citationsToTitles(r.ragCitations);
    out.push({
      id: r.id,
      role: r.role,
      content: r.content ?? "",
      reasoning: r.reasoning ?? undefined,
      reasoningCollapsed: hasReason ? true : undefined,
      usage,
      modelAlias: r.modelAlias ?? undefined,
      userFeedback: r.userFeedback ?? undefined,
      ...(r.role === "assistant" && ragSingle?.length
        ? { ragRetrievalTitles: [...ragSingle] }
        : {}),
      ...((r.role === "assistant" || r.role === "user") && r.webSearchReferences?.length
        ? {
            webSearchReferences: [...r.webSearchReferences],
            ...(r.role === "assistant"
              ? { webSearchRefsCollapsed: defaultWebSearchRefsCollapsed(r.webSearchReferences) }
              : {}),
          }
        : {}),
      ...(r.role === "assistant" && r.workflowSegments?.length
        ? { workflowSegments: decorateHistoryWorkflowSegments([...r.workflowSegments]) }
        : {}),
      ...(r.intentTurnHit ? { intentTurnHit: { ...r.intentTurnHit } } : {}),
      ...(r.role === "user" && r.attachments && r.attachments.length > 0
        ? { attachments: r.attachments.map((a) => ({ ...a })) }
        : {}),
    });
  }
  return out;
}

function deepCloneReplyVariants(v: ReplyVariant[]): ReplyVariant[] {
  return v.map((x) => ({
    ...x,
    usage: x.usage ? { ...x.usage } : undefined,
    ragRetrievalTitles:
      x.ragRetrievalTitles && x.ragRetrievalTitles.length > 0 ? [...x.ragRetrievalTitles] : undefined,
    webSearchReferences:
      x.webSearchReferences && x.webSearchReferences.length > 0
        ? [...x.webSearchReferences]
        : undefined,
  }));
}

/** 当前仍存于库、可用于重新生成/反馈的助手消息 id（多版时取最后一个带 id 的版本） */
function assistantLatestPersistedDbId(m: Msg): number | undefined {
  if (m.role !== "assistant") return undefined;
  if (m.replyVariants?.length) {
    for (let i = m.replyVariants.length - 1; i >= 0; i--) {
      const id = m.replyVariants[i]?.id;
      if (id != null) return id;
    }
  }
  return m.id;
}

/** 将当前展示版同步到 Msg 顶层字段，供模板与复制/分享等沿用 */
function syncAssistantActiveToFlat(m: Msg) {
  if (m.role !== "assistant") return;
  const vs = m.replyVariants;
  if (!vs?.length) return;
  const idx = Math.min(Math.max(0, m.activeVariantIndex ?? 0), vs.length - 1);
  const v = vs[idx]!;
  m.content = v.content;
  m.id = v.id;
  m.usage = v.usage;
  m.modelAlias = v.modelAlias;
  m.userFeedback = v.userFeedback;
  m.reasoning = v.reasoning;
  m.reasoningCollapsed = v.reasoningCollapsed;
  m.ragRetrievalTitles =
    v.ragRetrievalTitles && v.ragRetrievalTitles.length > 0 ? [...v.ragRetrievalTitles] : undefined;
  m.webSearchReferences =
    v.webSearchReferences && v.webSearchReferences.length > 0 ? [...v.webSearchReferences] : undefined;
  m.webSearchRefsCollapsed =
    v.webSearchRefsCollapsed ?? defaultWebSearchRefsCollapsed(v.webSearchReferences);
}

function assistantFeedbackEligible(m: Msg): boolean {
  if (m.role !== "assistant" || !m.id) return false;
  if (!m.replyVariants?.length) return true;
  const last = m.replyVariants.length - 1;
  return (m.activeVariantIndex ?? 0) === last;
}

function assistantVariantNavShow(m: Msg): boolean {
  return m.role === "assistant" && !!m.replyVariants && m.replyVariants.length > 1 && !m.streaming;
}

function stepAssistantVariant(m: Msg, delta: number) {
  if (!m.replyVariants?.length) return;
  const n = m.replyVariants.length;
  const cur = m.activeVariantIndex ?? 0;
  const ni = Math.min(n - 1, Math.max(0, cur + delta));
  if (ni === cur) return;
  m.activeVariantIndex = ni;
  syncAssistantActiveToFlat(m);
}

async function loadMessagesForConv(id: number) {
  clearThread();
  try {
    const rows = await chatApi.listConversationMessages(id);
    messages.value = mapHistoryToMsgs(rows);
  } catch {
    ElMessage.error(t("chat.loadHistoryFail"));
  }
  await scrollToBottom();
}

function threadContentMatches(local: Msg, server: Msg): boolean {
  if (local.role !== server.role) {
    return false;
  }
  return (local.content ?? "").trim() === (server.content ?? "").trim();
}

/** 流式结束后只同步库表 id / 用量等元数据，避免整表替换导致气泡 DOM 重建闪烁。 */
function patchMsgMetadataFromServer(local: Msg, server: Msg): void {
  local.id = server.id;
  if (server.usage) {
    local.usage = { ...server.usage };
  }
  if (server.userFeedback != null) {
    local.userFeedback = server.userFeedback;
  }
  if (server.role !== "assistant") {
    if (server.attachments?.length) {
      local.attachments = server.attachments.map((a) => ({ ...a }));
    }
    return;
  }
  if (server.replyVariants?.length) {
    local.replyVariants = deepCloneReplyVariants(server.replyVariants);
    local.activeVariantIndex = server.activeVariantIndex ?? local.replyVariants.length - 1;
    syncAssistantActiveToFlat(local);
  }
  if (server.workflowSegments?.length) {
    local.workflowSegments = [...server.workflowSegments];
  }
  if (server.intentTurnHit) {
    local.intentTurnHit = { ...server.intentTurnHit };
  }
  if (server.webSearchReferences?.length && !(local.webSearchReferences?.length)) {
    local.webSearchReferences = [...server.webSearchReferences];
    local.webSearchRefsCollapsed = defaultWebSearchRefsCollapsed(server.webSearchReferences);
  }
}

async function syncThreadAfterStream(conversationId: number): Promise<void> {
  try {
    const rows = await chatApi.listConversationMessages(conversationId);
    const serverMsgs = mapHistoryToMsgs(rows);
    const local = messages.value;
    if (!serverMsgs.length || serverMsgs.length !== local.length) {
      messages.value = serverMsgs;
      return;
    }
    for (let i = 0; i < local.length; i++) {
      if (!threadContentMatches(local[i]!, serverMsgs[i]!)) {
        messages.value = serverMsgs;
        return;
      }
    }
    for (let i = 0; i < local.length; i++) {
      patchMsgMetadataFromServer(local[i]!, serverMsgs[i]!);
    }
  } catch {
    /* 保留本地流式结果，仅缺库表 id 时影响反馈/重新生成 */
  }
}

function assistantMdHtml(text: string): string {
  return renderMarkdownToSafeHtml(text ?? "");
}

/** 流式阶段行内光标（紧跟最后一个字符，不用 markdown 块级闭合标签注入） */
const STREAM_CURSOR_HTML = '<span class="stream-md-cursor" aria-hidden="true"></span>';

function assistantMdStreamingHtml(m: Msg): string {
  if (m.role !== "assistant" || !m.streaming) {
    return assistantMdHtml(m.content);
  }
  return renderStreamingMarkdownToSafeHtml(m.content, STREAM_CURSOR_HTML);
}

function workflowSegmentStreamingHtml(seg: { status: string; text?: string | null }): string {
  if (seg.status === "streaming") {
    return renderStreamingMarkdownToSafeHtml(seg.text || "", STREAM_CURSOR_HTML);
  }
  return workflowSegmentRichHtml(seg.text || "");
}

/** 工作流阶段正文：Markdown 默认会合并单换行；将换行转为硬换行并兼容字面量 \\n。 */
function workflowSegmentRichHtml(text: string): string {
  let s = text ?? "";
  s = s.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  s = s.replace(/\\n/g, "\n");
  if (s.includes("\n")) {
    s = s
      .split("\n")
      .map((line) => line.trimEnd())
      .join("  \n");
  }
  return renderMarkdownToSafeHtml(s);
}

/** 是否展示「思考过程」外壳（含意图编排且已开思考；流结束后仍保留，避免说明与正文割裂） */
function showAssistantReasoningShell(m: Msg): boolean {
  if (m.role !== "assistant") return false;
  if (m.reasoningStreaming || (m.reasoning != null && m.reasoning.length > 0)) {
    return true;
  }
  return !!(
    thinkingEnabled.value &&
    currentModel.value?.supportsThinking &&
    (m.workflowSegments?.length ?? 0) > 0
  );
}

function intentReasoningLiveLabel(m: Msg): string {
  if (m.reasoning && m.reasoning.length > 0) {
    return t("chat.thinkingLive");
  }
  if ((m.workflowSegments?.length ?? 0) > 0) {
    return t("chat.intentOrchestrating");
  }
  return t("chat.thinkingLive");
}

/** 思考区展示意图说明（模型无 reasoning 分片、且已有流程步骤；流结束后仍展示） */
function reasoningIntentOrchestrationHint(m: Msg): boolean {
  if (!thinkingEnabled.value || !currentModel.value?.supportsThinking) {
    return false;
  }
  if (m.reasoning && m.reasoning.length > 0) {
    return false;
  }
  return (m.workflowSegments?.length ?? 0) > 0;
}

/** 流程卡片：仅在有 SSE 工作流阶段数据时展示（未命中意图的正常对话不占位）。 */
function showIntentWorkflowShell(m: Msg): boolean {
  if (m.role !== "assistant") return false;
  return (m.workflowSegments?.length ?? 0) > 0;
}

function isReasoningBodyVisible(m: Msg): boolean {
  const hasMain = m.content.trim().length > 0;
  // 仅有思考流时展开正文；主回复一旦出现则默认收起思考正文（保留顶栏）
  if (m.reasoningStreaming && !hasMain) {
    return true;
  }
  if (reasoningIntentOrchestrationHint(m)) {
    return m.reasoningCollapsed !== true;
  }
  return m.reasoningCollapsed !== true;
}

/**
 * 是否渲染主 Markdown 气泡。意图工作流已在 {@code workflowSegments} 卡片中展示分段正文，
 * 助手 {@code content} 多为各段拼接，再画主气泡会整段重复。
 */
function showAssistantMdBubble(m: Msg): boolean {
  if (m.role !== "assistant") {
    return false;
  }
  if ((m.workflowSegments?.length ?? 0) > 0) {
    return false;
  }
  const reasonUi =
    m.reasoningStreaming === true || (m.reasoning != null && m.reasoning.length > 0);
  if (!reasonUi) {
    return true;
  }
  return m.content.trim().length > 0 || !m.streaming;
}

/** 是否视为「有助手主区」：用于模型行、复制等（含仅工作流卡片、无 Markdown 气泡的情况） */
function showAssistantMainBubble(m: Msg): boolean {
  if (m.role !== "assistant") {
    return false;
  }
  if ((m.workflowSegments?.length ?? 0) > 0) {
    return true;
  }
  return showAssistantMdBubble(m);
}

function onReasoningBarClick(m: Msg) {
  if (m.reasoningStreaming) return;
  if (m.replyVariants?.length) {
    const v = m.replyVariants[m.activeVariantIndex ?? 0];
    if (v) v.reasoningCollapsed = !v.reasoningCollapsed;
  } else {
    m.reasoningCollapsed = !m.reasoningCollapsed;
  }
  syncAssistantActiveToFlat(m);
}

function finishAssistantStreamState(m: Msg) {
  m.streaming = false;
  m.reasoningStreaming = false;
  const hasWorkflow = (m.workflowSegments?.length ?? 0) > 0;
  if (m.replyVariants?.length) {
    const tail = m.replyVariants[m.replyVariants.length - 1];
    if (tail?.reasoning != null && tail.reasoning.length > 0 && !hasWorkflow) {
      tail.reasoningCollapsed = true;
    }
  } else if (m.reasoning != null && m.reasoning.length > 0 && !hasWorkflow) {
    m.reasoningCollapsed = true;
  }
  syncAssistantActiveToFlat(m);
  void loadFollowUpForMessage(m);
}

function applyAssistantStreamPart(m: Msg, part: chatApi.StreamPart, tail: ReplyVariant | null) {
  const body = tail ?? m;
  if (part.type === "content" && part.v) {
    if (!body.content && part.v.trim().length > 0 && (m.workflowSegments?.length ?? 0) === 0) {
      if (tail) {
        tail.reasoningCollapsed = true;
      } else {
        m.reasoningCollapsed = true;
      }
    }
    body.content += part.v;
  } else if (part.type === "ragDoc" && part.title) {
    if (!m.streaming) {
      return;
    }
    body.ragRetrievalTitles = [...(body.ragRetrievalTitles ?? []), part.title];
  } else if (part.type === "webSearchRefs" && part.references?.length) {
    if (!m.streaming) {
      return;
    }
    body.webSearchReferences = [...part.references];
    if (webSearchRefsFoldable(part.references)) {
      m.webSearchRefsCollapsed = true;
      body.webSearchRefsCollapsed = true;
    }
  } else if (part.type === "reasoning" && part.v) {
    m.reasoningStreaming = true;
    body.reasoning = (body.reasoning ?? "") + part.v;
  } else if (part.type === "inputBlocked") {
    finishAssistantStreamState(m);
    const reason = (part.reason ?? "").trim();
    ElMessage.warning(reason || t("chat.inputBlocked"));
    if (!body.content) {
      body.content = reason || t("chat.inputBlockedBody");
    }
  } else if (part.type === "error") {
    const msg = (part.message || part.code || t("chat.modelFailDefault")).replace(/\s+/g, " ").trim();
    const short = msg.length > 140 ? msg.slice(0, 140) + "…" : msg;
    ElMessage.warning(t("chat.streamErrorRetry", { msg: short }));
    body.content += `\n\n（${msg}）`;
  } else if (part.type === "workflowStage") {
    mergeWorkflowStage(m, part.stage);
  } else if (part.type === "end") {
    finishAssistantStreamState(m);
    if ("usage" in part && part.usage && part.usage.totalTokens > 0) {
      body.usage = { ...part.usage };
    }
  }
}

function rowCopyKey(m: Msg, idx: number): string {
  return `r-${idx}-${m.replyVariants?.length ? (m.activeVariantIndex ?? 0) : 0}`;
}

const copyFlashKey = ref<string | null>(null);
const userCopyFlashKey = ref<string | null>(null);

function rowUserCopyKeyUser(idx: number): string {
  return `u-${idx}`;
}

function flashCopyRow(m: Msg, idx: number) {
  const k = rowCopyKey(m, idx);
  copyFlashKey.value = k;
  window.setTimeout(() => {
    if (copyFlashKey.value === k) {
      copyFlashKey.value = null;
    }
  }, 2000);
}

function assistantPlainTextFromMd(content: string): string {
  if (typeof document === "undefined") {
    return (content ?? "").trim();
  }
  const tmp = document.createElement("div");
  tmp.innerHTML = renderMarkdownToSafeHtml(content ?? "");
  const t = (tmp.innerText || tmp.textContent || "").trim();
  return t || (content ?? "").trim();
}

async function copyUserMessage(m: Msg, idx: number) {
  const att = (m.attachments ?? [])
    .map((a) => a.fileName)
    .filter((n) => n && n.trim())
    .join("、");
  const body =
    att ? `${m.content ?? ""}\n\n${t("chat.attachInCopy", { names: att })}` : (m.content ?? "");
  const ok = await copyTextToUserClipboard(body);
  if (ok) {
    ElMessage.success(t("chat.copied"));
    const k = rowUserCopyKeyUser(idx);
    userCopyFlashKey.value = k;
    window.setTimeout(() => {
      if (userCopyFlashKey.value === k) {
        userCopyFlashKey.value = null;
      }
    }, 2000);
  } else {
    ElMessage.warning(t("chat.copyFail"));
  }
}

async function copyAssistantPlain(m: Msg, idx: number) {
  let plain: string;
  try {
    plain = assistantPlainTextFromMd(m.content ?? "");
  } catch {
    plain = (m.content ?? "").trim();
  }
  const ok = await copyTextToUserClipboard(plain);
  if (ok) {
    ElMessage.success(t("chat.copied"));
    flashCopyRow(m, idx);
  } else {
    ElMessage.warning(t("chat.copyFail"));
  }
}

async function copyAssistantMarkdown(m: Msg, idx: number) {
  const ok = await copyTextToUserClipboard(m.content ?? "");
  if (ok) {
    ElMessage.success(t("chat.copiedMd"));
    flashCopyRow(m, idx);
  } else {
    ElMessage.warning(t("chat.copyFail"));
  }
}

async function onAssistantCopyMenu(cmd: string, m: Msg, idx: number) {
  if (cmd === "md") {
    await copyAssistantMarkdown(m, idx);
  }
}

const shareOpen = ref(false);
const shareAnchorAssistantIdx = ref(0);

function openShareDialog(_m: Msg, assistantIdx: number) {
  shareAnchorAssistantIdx.value = assistantIdx;
  shareOpen.value = true;
}

function isLastAssistantIndex(idx: number): boolean {
  if (idx !== messages.value.length - 1) {
    return false;
  }
  return messages.value[idx]?.role === "assistant";
}

async function setAssistantFeedback(m: Msg, vote: chatApi.ChatAssistantFeedbackVote) {
  if (!m.id || !convId.value || m.role !== "assistant") {
    return;
  }
  if (!assistantFeedbackEligible(m)) {
    return;
  }
  feedbackSendingId.value = m.id;
  try {
    await chatApi.submitAssistantFeedback(convId.value, m.id, vote);
    const normalized = vote === "NONE" ? undefined : vote;
    m.userFeedback = normalized;
    const vs = m.replyVariants;
    if (vs?.length) {
      const tail = vs[vs.length - 1];
      if (tail && tail.id === m.id) {
        tail.userFeedback = normalized;
      }
    }
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, t("chat.feedbackFail")));
  } finally {
    feedbackSendingId.value = null;
  }
}

async function toggleAssistantLike(m: Msg) {
  await setAssistantFeedback(m, m.userFeedback === "LIKE" ? "NONE" : "LIKE");
}

async function toggleAssistantDislike(m: Msg) {
  await setAssistantFeedback(m, m.userFeedback === "DISLIKE" ? "NONE" : "DISLIKE");
}

function onAssistantMore(cmd: string, idx: number) {
  if (cmd === "report") {
    ElMessage.info(t("chat.reportThanks"));
    return;
  }
  if (cmd === "editPrompt") {
    const prev = messages.value[idx - 1];
    if (prev && prev.role === "user") {
      input.value = prev.content;
      ElMessage.success(t("chat.editPromptFilled"));
    } else {
      ElMessage.warning(t("chat.noPriorUserMsg"));
    }
    return;
  }
  if (cmd === "speak") {
    const m = messages.value[idx];
    if (!m || m.role !== "assistant") {
      return;
    }
    const t = assistantPlainTextFromMd(m.content ?? "");
    if (!t) {
      ElMessage.warning(t("chat.nothingToSpeak"));
      return;
    }
    if (typeof window === "undefined" || !window.speechSynthesis) {
      ElMessage.warning(t("chat.speechUnsupported"));
      return;
    }
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(t);
    u.lang = "zh-CN";
    window.speechSynthesis.speak(u);
  }
}

const convs = ref<{ id: number; title: string }[]>([]);
const convId = ref<number | null>(null);
const input = ref("");
const messages = ref<Msg[]>([]);

/** 乐观插入行稳定键（无库表 id 前）；勿与后端 id 混用。 */
function newClientRowKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `k-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

function messageRowKey(m: Msg, idx: number): string {
  const cid = convId.value ?? "n";
  if (m.clientRowKey) {
    return `${cid}-c-${m.clientRowKey}`;
  }
  if (m.id != null) {
    return `${cid}-db-${m.id}`;
  }
  return `${cid}-i-${idx}`;
}

const sending = ref(false);
const scrollAreaRef = ref<InstanceType<typeof import("element-plus").ElScrollbar> | null>(null);

const models = ref<chatApi.LlmModelOption[]>([]);
const modelAlias = ref("");
/** 默认开启；不支持思考的模型由 watch(modelAlias) 置为 false */
const thinkingEnabled = ref(true);
const webSearchAllowed = ref(false);
const webSearchEnabled = ref(false);
const feedbackSendingId = ref<number | null>(null);
const pendingFiles = ref<File[]>([]);
const dragDepth = ref(0);
const dragOver = ref(false);

const emptyStarterPrompts = ref<chatApi.StarterPromptItem[]>([]);
const emptyPromptsLoading = ref(false);
const emptyPromptExcludeIds = ref<number[]>([]);

const fallbackEmptyPrompts = computed<chatApi.StarterPromptItem[]>(() => [
  { id: null, text: t("chat.quick1"), source: "FALLBACK" },
  { id: null, text: t("chat.quick2"), source: "FALLBACK" },
  { id: null, text: t("chat.quick3"), source: "FALLBACK" },
]);

async function loadEmptyStarterPrompts(refresh = false) {
  emptyPromptsLoading.value = true;
  try {
    const res = await chatApi.fetchStarterPrompts({
      scene: "EMPTY",
      limit: 6,
      refresh,
      excludeIds: refresh ? emptyPromptExcludeIds.value : undefined,
      thinkingEnabled: thinkingEnabled.value,
      webSearchEnabled: webSearchEnabled.value,
    });
    const items = res.items?.length ? res.items : fallbackEmptyPrompts.value;
    emptyStarterPrompts.value = items;
    if (refresh) {
      for (const p of items) {
        if (p.id != null) {
          emptyPromptExcludeIds.value.push(p.id);
        }
      }
    }
  } catch {
    emptyStarterPrompts.value = fallbackEmptyPrompts.value;
  } finally {
    emptyPromptsLoading.value = false;
  }
}

function refreshEmptyStarterPrompts() {
  void loadEmptyStarterPrompts(true);
}

/** 点击推荐问句时默认开启联网（租户已配置联网模型时）。 */
function enableWebSearchForStarterPrompt() {
  if (webSearchAllowed.value) {
    webSearchEnabled.value = true;
  }
}

async function applyStarterPrompt(
  q: chatApi.StarterPromptItem,
  scene: "EMPTY" | "FOLLOW_UP",
) {
  enableWebSearchForStarterPrompt();
  input.value = q.text;
  if (q.id != null) {
    try {
      await chatApi.recordStarterPromptEvent({
        promptId: q.id,
        scene,
        eventType: "CLICK",
      });
    } catch {
      /* ignore */
    }
  }
}

async function loadFollowUpForMessage(m: Msg) {
  if (m.role !== "assistant" || m.streaming || m.id == null || convId.value == null) {
    return;
  }
  try {
    const res = await chatApi.fetchFollowUpPrompts(convId.value, m.id, 3);
    m.followUpPrompts = res.items?.length ? res.items : [];
  } catch {
    m.followUpPrompts = [];
  }
}

const currentModel = computed(() => models.value.find((m) => m.alias === modelAlias.value));
const maxAttachmentsLimit = computed(() => currentModel.value?.maxAttachments ?? 10);

const attachDisabled = computed(
  () => sending.value || pendingFiles.value.length >= maxAttachmentsLimit.value,
);

const canSend = computed(
  () =>
    !!input.value.trim() &&
    !sending.value &&
    !!modelAlias.value &&
    models.value.some((m) => m.alias === modelAlias.value && m.quotaExhausted !== true),
);

function modelOptionLabel(m: chatApi.LlmModelOption): string {
  if (m.quotaExhausted) {
    return `${m.displayName}${t("chat.quotaExhausted")}`;
  }
  return m.displayName;
}

const modelSelectWidthPx = computed(() => {
  const m = currentModel.value;
  const label = m ? modelOptionLabel(m) : modelAlias.value ? modelAlias.value : t("chat.selectModel");
  if (typeof window === "undefined") {
    return Math.min(measureSelectLabelWidthPx(label), 280);
  }
  if (isMobile.value) {
    const raw = measureSelectLabelWidthPx(label, 36);
    const cap = mobileModelSelectMaxWidthPx(
      !!m?.supportsThinking,
      webSearchAllowed.value,
      window.innerWidth,
    );
    return Math.min(raw, cap);
  }
  const raw = measureSelectLabelWidthPx(label);
  const cap = Math.min(280, Math.max(120, Math.floor(window.innerWidth * 0.52)));
  return Math.min(raw, cap);
});

/** 下拉层宽度：按所有选项文案量宽 + 少量余量，且不小于触发器宽度。 */
const modelDropdownMinWidthPx = computed(() => {
  const pad = 44;
  const vwCap =
    typeof window !== "undefined" ? Math.min(560, Math.floor(window.innerWidth * 0.92)) : 560;
  let maxText = measureDropdownOptionTextWidthPx(t("chat.selectModel"));
  for (const m of models.value) {
    maxText = Math.max(maxText, measureDropdownOptionTextWidthPx(modelOptionLabel(m)));
  }
  const fromContent = maxText + pad;
  return Math.min(vwCap, Math.max(100, fromContent, modelSelectWidthPx.value));
});

watch(
  modelDropdownMinWidthPx,
  (v) => {
    document.documentElement.style.setProperty("--chat-model-dd-min", `${v}px`);
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  cancelActiveStream();
  if (scrollBottomRaf != null) {
    cancelAnimationFrame(scrollBottomRaf);
    scrollBottomRaf = null;
  }
  document.documentElement.style.removeProperty("--chat-model-dd-min");
});

const activeTitle = computed(() => {
  if (!convId.value) return t("chat.titleNew");
  const c = convs.value.find((x) => x.id === convId.value);
  return c?.title ?? t("chat.titleChat");
});

const sessionTokenTotal = computed(() =>
  messages.value.reduce((s, m) => s + (m.usage?.totalTokens ?? 0), 0),
);

async function scrollToBottom() {
  await nextTick();
  const sb = scrollAreaRef.value;
  if (!sb?.wrapRef) return;
  sb.setScrollTop(sb.wrapRef.scrollHeight);
}

let scrollBottomRaf: number | null = null;

function scheduleScrollToBottom() {
  if (scrollBottomRaf != null) {
    return;
  }
  scrollBottomRaf = requestAnimationFrame(() => {
    scrollBottomRaf = null;
    void scrollToBottom();
  });
}

function stopGenerating() {
  if (!sending.value) {
    return;
  }
  cancelActiveStream();
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i];
    if (m?.role === "assistant" && m.streaming) {
      finishAssistantStreamState(m);
      break;
    }
  }
  sending.value = false;
  void scrollToBottom();
}

async function retryAssistantAt(assistantIdx: number) {
  const prev = messages.value[assistantIdx];
  if (!prev || prev.role !== "assistant" || !convId.value || sending.value) {
    return;
  }
  if (!isLastAssistantIndex(assistantIdx)) {
    ElMessage.warning(t("chat.retryLastOnly"));
    return;
  }
  const oldDbId = assistantLatestPersistedDbId(prev);
  if (oldDbId == null) {
    ElMessage.warning(t("chat.waitForReply"));
    return;
  }

  const think = !!currentModel.value?.supportsThinking && thinkingEnabled.value;
  const useWeb = webSearchAllowed.value && webSearchEnabled.value;

  const committed: ReplyVariant[] = prev.replyVariants?.length
    ? deepCloneReplyVariants(prev.replyVariants)
    : [
        {
          id: prev.id,
          content: prev.content,
          reasoning: prev.reasoning,
          reasoningCollapsed: prev.reasoningCollapsed,
          usage: prev.usage,
          modelAlias: prev.modelAlias,
          userFeedback: prev.userFeedback,
        },
      ];
  committed.push({
    content: "",
    ragRetrievalTitles: [],
    reasoning: undefined,
    reasoningCollapsed: think ? false : undefined,
    modelAlias: modelAlias.value,
  });
  prev.replyVariants = committed;
  prev.activeVariantIndex = committed.length - 1;
  prev.workflowSegments = [];
  prev.streaming = true;
  prev.reasoningStreaming = false;
  syncAssistantActiveToFlat(prev);

  sending.value = true;
  await scrollToBottom();

  const { signal: streamSignal, generation: regenGen } = beginActiveStream();
  let syncHistory = true;
  try {
    await chatApi.streamRegenerateAssistantReply(
      convId.value,
      oldDbId,
      {
        modelAlias: modelAlias.value,
        thinkingEnabled: think,
        webSearchEnabled: useWeb,
        responseLocale: chatResponseLocale.value,
      },
      (part) => {
        if (regenGen !== assistantStreamGeneration) {
          return;
        }
        const m = messages.value[assistantIdx];
        if (!m?.replyVariants?.length) {
          return;
        }
        const tail = m.replyVariants[m.replyVariants.length - 1]!;
        applyAssistantStreamPart(m, part, tail);
        syncAssistantActiveToFlat(m);
        scheduleScrollToBottom();
      },
      { signal: streamSignal },
    );
    const m = messages.value[assistantIdx];
    if (m) {
      finishAssistantStreamState(m);
    }
  } catch (e: unknown) {
    if (isAbortError(e)) {
      syncHistory = false;
    } else {
      ElMessage.error(apiRequestErrorMessage(e, t("chat.regenerateFail")));
    }
  } finally {
    activeStreamAbort = null;
    sending.value = false;
    if (syncHistory && convId.value) {
      await syncThreadAfterStream(convId.value);
    }
    await scrollToBottom();
  }
}

/** 流式时按内容体量分桶滚底，避免每个 token 触发 layout */
watch(
  () => {
    if (!sending.value) {
      return -1;
    }
    let total = 0;
    for (const m of messages.value) {
      if (m.streaming) {
        total += (m.content?.length ?? 0) + (m.reasoning?.length ?? 0);
      }
    }
    return Math.floor(total / 200);
  },
  () => {
    if (sending.value) {
      scheduleScrollToBottom();
    }
  },
);

watch(
  () => messages.value.length,
  () => {
    scheduleScrollToBottom();
  },
);

async function refresh() {
  convs.value = await chatApi.listConversations();
  if (!convId.value && convs.value.length) {
    convId.value = convs.value[0].id;
  }
}

function clearThread() {
  messages.value = [];
}

function selectConv(id: number) {
  if (sending.value) {
    cancelActiveStream();
    sending.value = false;
  }
  convId.value = id;
  webSearchEnabled.value = false;
  if (isMobile.value) {
    sidebarOpen.value = false;
  }
  void loadMessagesForConv(id);
}

async function onMobileNewConv() {
  sidebarOpen.value = false;
  await newConv();
}

function applyDefaultModelAlias() {
  const preferred = models.value.find((m) => !m.quotaExhausted)?.alias ?? models.value[0]?.alias ?? "";
  modelAlias.value = preferred;
}

const authOpen = ref(false);
const loggedInUsername = ref<string | null>(null);

/** 避免与 onMounted 首屏加载重复执行 */
const tenantShellReady = ref(false);

async function loadChatShellForCurrentTenant() {
  try {
    models.value = await chatApi.listChatModels();
    try {
      const av = await chatApi.getWebSearchAvailability();
      webSearchAllowed.value = !!av?.allowed;
    } catch {
      webSearchAllowed.value = false;
    }
    if (!webSearchAllowed.value) {
      webSearchEnabled.value = false;
    }
    if (!models.value.length) {
      ElMessage.warning(t("chat.noModels"));
    } else if (!models.value.some((m) => m.alias === modelAlias.value)) {
      applyDefaultModelAlias();
    }
  } catch (e: unknown) {
    models.value = [];
    ElMessage.warning(apiRequestErrorMessage(e, t("chat.loadModelsFail")));
  }
  await refresh();
  if (convId.value) {
    await loadMessagesForConv(convId.value);
  } else {
    await scrollToBottom();
  }
  if (messages.value.length === 0) {
    emptyPromptExcludeIds.value = [];
    await loadEmptyStarterPrompts(false);
  }
}

function readJwtSub(token: string | null): string | null {
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length < 2) return null;
    const json = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof json.sub === "string" ? json.sub : null;
  } catch {
    return null;
  }
}

function refreshAuthLabel() {
  loggedInUsername.value = readJwtSub(localStorage.getItem(AI_USER_ACCESS_TOKEN_KEY));
}

async function onAuthDone() {
  refreshAuthLabel();
  await loadChatShellForCurrentTenant();
}

function logoutUser() {
  clearUserSession();
  refreshAuthLabel();
  webSearchEnabled.value = false;
  void refresh();
  clearThread();
  ElMessage.success(t("chat.loggedOut"));
}

watch(
  () => tenantCodeParam.value,
  async () => {
    if (!tenantShellReady.value) return;
    clearThread();
    convId.value = null;
    await loadChatShellForCurrentTenant();
  },
);

onMounted(async () => {
  refreshAuthLabel();
  await loadChatShellForCurrentTenant();
  tenantShellReady.value = true;
});

function addPendingFiles(files: File[]) {
  if (!files.length) return;
  const cap = maxAttachmentsLimit.value;
  const merged = [...pendingFiles.value, ...files].slice(0, cap);
  pendingFiles.value = merged;
}

function onFilePick(uploadFile: UploadFile) {
  const raw = uploadFile.raw;
  if (!raw) return;
  addPendingFiles([raw]);
}

function removePending(i: number) {
  pendingFiles.value = pendingFiles.value.filter((_, idx) => idx !== i);
}

function truncateName(name: string, max = 28): string {
  if (name.length <= max) return name;
  const ext = name.includes(".") ? name.slice(name.lastIndexOf(".")) : "";
  const base = ext ? name.slice(0, name.lastIndexOf(".")) : name;
  const keep = max - ext.length - 1;
  return `${base.slice(0, Math.max(4, keep))}…${ext}`;
}

function onDragEnter() {
  if (attachDisabled.value) return;
  dragDepth.value += 1;
  dragOver.value = true;
}

function onDragLeave() {
  dragDepth.value = Math.max(0, dragDepth.value - 1);
  if (dragDepth.value === 0) {
    dragOver.value = false;
  }
}

function onDropFiles(e: DragEvent) {
  dragDepth.value = 0;
  dragOver.value = false;
  if (attachDisabled.value) return;
  const list = e.dataTransfer?.files;
  if (!list?.length) return;
  addPendingFiles(Array.from(list));
}

watch(modelAlias, () => {
  if (!currentModel.value?.supportsThinking) {
    thinkingEnabled.value = false;
  }
  pendingFiles.value = pendingFiles.value.slice(0, maxAttachmentsLimit.value);
});

async function newConv() {
  const dateLoc = locale.value.startsWith("en") ? "en-US" : "zh-CN";
  const c = await chatApi.createConversation(
    `${t("chat.newConvPrefix")} ${new Date().toLocaleString(dateLoc, { hour12: false })}`,
  );
  convId.value = c.id;
  webSearchEnabled.value = false;
  if (isMobile.value) {
    sidebarOpen.value = false;
  }
  await refresh();
  clearThread();
  ElMessage.success(t("chat.convCreated"));
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    void send();
  }
}

async function send() {
  const text = input.value.trim();
  if (!text || sending.value) return;
  if (!modelAlias.value) {
    ElMessage.warning(t("chat.pickModelFirst"));
    return;
  }

  if (!convId.value) {
    await newConv();
  }
  if (!convId.value) return;

  let intentFlowTicket: string | undefined;
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const row = messages.value[i]!;
    if (row.role !== "user" && row.role !== "assistant") continue;
    const t = row.intentTurnHit?.intentFlowTicket;
    if (t) {
      intentFlowTicket = t;
      break;
    }
  }

  let attachmentIds: number[] = [];
  let uploadedAttachmentViews: chatApi.ChatAttachmentMessage[] = [];
  if (pendingFiles.value.length) {
    try {
      const ups = await chatApi.uploadChatAttachments(convId.value, pendingFiles.value);
      attachmentIds = ups.map((u) => u.id);
      uploadedAttachmentViews = ups.map((u) => ({
        id: u.id,
        fileName: u.fileName,
        charLength: u.charLength ?? null,
      }));
      pendingFiles.value = [];
    } catch (e: unknown) {
      ElMessage.error(apiRequestErrorMessage(e, t("chat.uploadFail")));
      return;
    }
  }

  const think =
    !!currentModel.value?.supportsThinking && thinkingEnabled.value;
  const useWeb = webSearchAllowed.value && webSearchEnabled.value;

  messages.value.push({
    role: "user",
    content: text,
    clientRowKey: newClientRowKey(),
    ...(uploadedAttachmentViews.length ? { attachments: uploadedAttachmentViews } : {}),
  });
  input.value = "";
  messages.value.push({
    role: "assistant",
    clientRowKey: newClientRowKey(),
    content: "",
    ragRetrievalTitles: [],
    workflowSegments: [],
    streaming: true,
    reasoning: undefined,
    reasoningStreaming: false,
    modelAlias: modelAlias.value,
  });
  const assistantIdx = messages.value.length - 1;
  sending.value = true;
  await scrollToBottom();

  const { signal: streamSignal, generation: sendGen } = beginActiveStream();
  let syncHistory = true;
  try {
    await chatApi.streamAssistantReply(
      convId.value,
      {
        content: text,
        modelAlias: modelAlias.value,
        thinkingEnabled: think,
        webSearchEnabled: useWeb,
        attachmentIds,
        ...(intentFlowTicket ? { intentFlowTicket } : {}),
        responseLocale: chatResponseLocale.value,
      },
      (part) => {
        if (sendGen !== assistantStreamGeneration) {
          return;
        }
        const m = messages.value[assistantIdx];
        if (!m) {
          return;
        }
        applyAssistantStreamPart(m, part, null);
        scheduleScrollToBottom();
      },
      { signal: streamSignal },
    );
    const m = messages.value[assistantIdx];
    if (m) {
      finishAssistantStreamState(m);
    }
  } catch (e: unknown) {
    if (isAbortError(e)) {
      syncHistory = false;
    } else {
      const m = messages.value[assistantIdx];
      if (m) {
        m.content = m.content || t("chat.replyFailed");
        finishAssistantStreamState(m);
      }
      ElMessage.error(apiRequestErrorMessage(e, t("chat.sendFail")));
    }
  } finally {
    activeStreamAbort = null;
    sending.value = false;
    if (syncHistory) {
      await refresh();
      if (convId.value) {
        await syncThreadAfterStream(convId.value);
      }
    }
    await scrollToBottom();
  }
}
</script>

<style scoped>
.chat-app {
  display: flex;
  height: 100vh;
  min-height: 100vh;
  max-height: 100vh;
  background: var(--chat-bg-app, #fff);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--chat-bg-main, #fafafa);
}

.thread-head {
  /* 与 .messages / .composer-surface 同宽居中，避免侧栏存在时标题贴左、对话区视觉上「整体偏右」 */
  max-width: 58rem;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
  padding: 14px 24px 12px;
  border-bottom: 1px solid var(--chat-border, #ececec);
  flex-shrink: 0;
  background: var(--chat-bg-main, #fafafa);
}

.thread-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.thread-head-tools {
  flex-shrink: 0;
}

.thread-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--chat-text-primary, #202020);
  letter-spacing: -0.02em;
}

.thread-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6e6e6e;
  line-height: 1.45;
  max-width: 54rem;
}

.thread-tokens {
  margin: 8px 0 0;
  font-size: 12px;
  color: #52525b;
  line-height: 1.45;
  max-width: 54rem;
}

.thread-tokens strong {
  color: #18181b;
  font-weight: 600;
}

.reasoning {
  margin-bottom: 10px;
  border-radius: 10px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  overflow: hidden;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.reasoning-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  color: #3f3f46;
  transition: background 0.12s;
}

.reasoning-bar:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.04);
}

.reasoning-bar--live {
  cursor: default;
}

.reasoning-bar--live:hover {
  background: transparent;
}

.reasoning-bar-title {
  font-size: 12px;
  font-weight: 600;
  color: #52525b;
  letter-spacing: 0.02em;
}

.reasoning-live {
  font-size: 12px;
  color: #19c37d;
  font-weight: 500;
}

.reasoning-meta {
  margin-left: auto;
  font-size: 12px;
  color: #71717a;
}

.reasoning-chevron {
  flex-shrink: 0;
  font-size: 14px;
  color: #71717a;
}

.reasoning-body-wrap {
  padding: 0 12px 10px;
  border-top: 1px solid #e4e4e7;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.reasoning-body {
  padding-top: 8px;
  font-size: 13px;
  color: #52525b;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  min-width: 0;
  max-width: 100%;
}

.reasoning-intent-hint {
  margin: 0;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.reasoning-intent-hint strong {
  font-weight: 600;
  color: #27272a;
}

/* 意图工作流：与「思考过程」类似的顶栏 + 可折叠正文，支持 Markdown */
.intent-workflow {
  margin-bottom: 10px;
  min-width: 0;
  max-width: 100%;
}

.intent-workflow-shell {
  border: 1px solid #e4e4e7;
  border-radius: 10px;
  background: #fafafa;
  overflow: hidden;
}

.intent-workflow-head {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #52525b;
  background: #f4f4f5;
  border-bottom: 1px solid #e4e4e7;
}

.wf-step + .wf-step {
  border-top: 1px solid #e4e4e7;
}

.wf-bar-lead {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
}

.wf-bar-done {
  color: #16a34a;
}

.wf-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: #fafafa;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: #3f3f46;
  transition: background 0.15s ease;
}

.wf-bar:hover {
  background: #f4f4f5;
}

.wf-bar--live {
  background: #f0fdf4;
}

.wf-bar-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #27272a;
}

.wf-bar-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: #71717a;
}

.wf-bar-chevron {
  flex-shrink: 0;
  color: #a1a1aa;
}

.wf-body-outer {
  padding: 0 12px 12px;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.wf-body-md {
  padding-top: 6px;
  font-size: 14px;
  line-height: 1.55;
  color: #3f3f46;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.wf-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  color: #71717a;
  font-size: 13px;
}

.wf-spin {
  animation: wf-spin 0.9s linear infinite;
}

@keyframes wf-spin {
  to {
    transform: rotate(360deg);
  }
}

.bubble-inner--assistant {
  white-space: normal !important;
}

.bubble-md {
  min-height: 0;
}

/* 松散列表常见结构 <li><p>…</p></li>，去掉内部 p 的外边距，避免「1.」与加粗标题错开多行 */
.bubble-md :deep(li > p) {
  margin: 0;
}

.bubble-md :deep(li > p + p) {
  margin-top: 0.35em;
}

.bubble-md :deep(p) {
  margin: 0 0 0.65em;
  color: var(--chat-text-body, #18181b);
}

.bubble-md :deep(p:last-child) {
  margin-bottom: 0;
}

.bubble-md :deep(ul),
.bubble-md :deep(ol) {
  margin: 0 0 0.65em;
  padding-left: 1.35em;
}

.bubble-md :deep(li) {
  margin: 0.2em 0;
  color: var(--chat-md-td-text, #18181b);
}

.bubble-md :deep(h1),
.bubble-md :deep(h2),
.bubble-md :deep(h3),
.bubble-md :deep(h4) {
  margin: 0.75em 0 0.45em;
  font-weight: 600;
  line-height: 1.35;
  color: var(--chat-md-heading, #18181b);
}

.bubble-md :deep(strong),
.bubble-md :deep(b) {
  color: var(--chat-md-strong, #18181b);
  font-weight: 600;
}

.bubble-md :deep(h1) {
  font-size: 1.25em;
}

.bubble-md :deep(h2) {
  font-size: 1.12em;
}

.bubble-md :deep(h3) {
  font-size: 1.05em;
}

.bubble-md :deep(pre) {
  margin: 0.5em 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--chat-md-pre-bg, #ececec);
  color: var(--chat-text-body, #18181b);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.9em;
}

.bubble-md :deep(p code),
.bubble-md :deep(li code) {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--chat-md-code-bg, #ececec);
  color: var(--chat-text-body, #18181b);
}

.bubble-md :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.bubble-md :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.35em 0 0.35em 12px;
  border-left: 3px solid var(--chat-md-blockquote-border, #d4d4d8);
  color: var(--chat-md-muted, #52525b);
}

.bubble-md :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin: 0.5em 0;
  color: var(--chat-md-td-text, #18181b);
}

.bubble-md :deep(th),
.bubble-md :deep(td) {
  border: 1px solid var(--chat-md-table-border, #e4e4e7);
  padding: 6px 8px;
  text-align: left;
}

.bubble-md :deep(th) {
  background: var(--chat-md-th-bg, #ececec);
  color: var(--chat-md-th-text, #18181b);
  font-weight: 600;
}

.bubble-md :deep(td) {
  color: var(--chat-md-td-text, #18181b);
}

.bubble-md :deep(a) {
  color: var(--chat-link, #2563eb);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.bubble-md :deep(hr) {
  border: none;
  border-top: 1px solid var(--chat-md-table-border, #e4e4e7);
  margin: 0.75em 0;
}

/* 流式结束前先占位操作条高度，避免结束后操作条插入引起跳动 */
.model-meta-row--streaming {
  min-height: 34px;
}

.msg-actions--during-stream {
  visibility: hidden;
  pointer-events: none;
}

/* 经 v-html 注入的流式光标无 Vue scoped 的 data-v-*，须 :deep；紧跟文末同一行 */
.bubble-md :deep(.stream-md-cursor) {
  display: inline-block;
  width: 2px;
  min-width: 2px;
  height: 1em;
  margin-left: 1px;
  vertical-align: baseline;
  border-radius: 1px;
  background: var(--chat-accent, #19c37d);
  animation: blink 1s step-end infinite;
}

.messages-scroll {
  flex: 1;
  min-height: 0;
  background: var(--chat-bg-main, #fafafa);
}

.messages-scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.messages-scroll :deep(.el-scrollbar__view) {
  min-height: 100%;
}

.messages-scroll-inner {
  padding: 20px 0 16px;
  box-sizing: border-box;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  padding: 40px 24px 56px;
  text-align: center;
}

.empty-brand {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #202020;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.empty-welcome {
  margin: 0 0 20px;
  font-size: 15px;
  line-height: 1.65;
  color: #3f3f46;
  max-width: 34rem;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 52rem;
}

.quick-prompt-chip {
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid #e5e5e5;
  background: #fff;
  color: #3f3f46;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.quick-prompt-chip:hover {
  border-color: #d0d0d0;
  background: #f7f7f7;
}

.quick-prompt-chip--refresh {
  border-style: dashed;
  color: #52525b;
}

.quick-prompt-chip--compact {
  padding: 5px 10px;
  font-size: 12px;
}

.follow-up-prompts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  max-width: 100%;
}

.follow-up-prompts-label {
  font-size: 12px;
  color: #71717a;
  flex-shrink: 0;
}

.messages {
  width: 100%;
  max-width: 58rem;
  min-width: 0;
  margin: 0 auto;
  padding: 0 24px;
  box-sizing: border-box;
}

.bubble-row {
  display: flex;
  gap: 10px;
  margin-bottom: 22px;
  align-items: flex-start;
  min-width: 0;
}

.bubble-row.user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar--assistant {
  background: #19c37d;
  color: #fff;
}

.avatar--user {
  background: #202020;
  color: #fff;
}

.bubble {
  min-width: 0;
  max-width: min(100%, 900px);
}

/* 助手列：气泡占满消息列可用宽度，避免「仅思考很长」时展开把整列撑宽导致布局跳动 */
.bubble-row.assistant .bubble {
  flex: 1 1 0%;
  min-width: 0;
  max-width: min(100%, 900px);
}

.bubble-row.user .bubble {
  flex: 0 1 auto;
}

.model-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 4px;
  padding-left: 2px;
  min-width: 0;
}

.model-meta {
  margin-top: 0;
  padding-left: 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.token-meta {
  margin-top: 6px;
  padding-left: 2px;
  font-size: 11px;
  color: #71717a;
  line-height: 1.4;
}

.bubble-inner {
  position: relative;
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #18181b;
}

.bubble-row.assistant .bubble-inner {
  background: #f4f4f4;
  border: none;
  border-bottom-left-radius: 4px;
}

.bubble-row.user .bubble-inner {
  background: #f4f4f4;
  border: none;
  border-bottom-right-radius: 4px;
}

.bubble-inner--user {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: 8px;
  row-gap: 6px;
  align-items: start;
}

.user-msg-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #71717a;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.12s, color 0.12s;
}

.user-msg-copy-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #27272a;
}

.user-msg-text {
  display: block;
  min-width: 0;
}

.user-msg-attach-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  grid-column: 1 / -1;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #e4e4e7;
}

.user-msg-attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e4e4e7;
  font-size: 12px;
  color: #3f3f46;
}

.user-msg-attach-ico {
  flex-shrink: 0;
  font-size: 14px;
  color: #71717a;
}

.user-msg-attach-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.intent-hit-hint {
  margin: 8px 0 0 2px;
  padding: 6px 10px;
  border-radius: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  font-size: 12px;
  color: #1e40af;
  line-height: 1.45;
  text-align: right;
}

.intent-hit-sub {
  color: #3b82f6;
  font-weight: 500;
}

.rag-sources-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin: 10px 0 0 2px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  font-size: 12px;
  color: #166534;
}

.rag-sources-label {
  font-weight: 600;
  margin-right: 2px;
}

.rag-doc-chip {
  display: inline-block;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #86efac;
  color: #14532d;
  line-height: 1.35;
}

.web-search-refs-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin: 10px 0 0 2px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--chat-bg-web-refs, #f8fafc);
  border: 1px solid var(--chat-border-web-refs, #e2e8f0);
  font-size: 12px;
  color: var(--chat-text-web-refs, #0f172a);
}

.web-search-refs-strip--foldable {
  display: block;
  padding: 0;
  overflow: hidden;
}

.web-search-refs-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  color: inherit;
  transition: background 0.12s;
}

.web-search-refs-bar:hover {
  background: rgba(0, 0, 0, 0.04);
}

.web-search-refs-meta {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: #64748b;
}

.web-search-refs-chevron {
  flex-shrink: 0;
  font-size: 14px;
  color: #94a3b8;
}

.web-search-refs-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  padding: 0 10px 8px;
}

.web-ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--chat-web-chip-bg, #fff);
  border: 1px solid var(--chat-web-chip-border, #cbd5e1);
  color: var(--chat-text-web-refs, #0f172a);
  text-decoration: none;
  line-height: 1.35;
}

.web-ref-chip:hover {
  border-color: #94a3b8;
  color: var(--chat-link, #0369a1);
}

.web-ref-chip--nolink {
  cursor: default;
}

.web-ref-logo {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  object-fit: cover;
  flex-shrink: 0;
}

.web-ref-chip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: min(320px, 70vw);
}

.bubble-row.assistant .cursor {
  background: #19c37d;
}

.bubble-row.user .cursor {
  background: #19c37d;
}

.cursor {
  display: inline-block;
  width: 6px;
  height: 1em;
  margin-left: 2px;
  vertical-align: -2px;
  border-radius: 1px;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.composer {
  flex-shrink: 0;
  padding: 8px 24px 16px;
  background: var(--chat-bg-main, #fafafa);
  border-top: none;
}

.composer-surface {
  max-width: 58rem;
  margin: 0 auto;
  border-radius: 22px;
  border: 1px solid var(--chat-border-subtle, #e3e3e3);
  background: var(--chat-bg-elevated, #fff);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.composer-surface:focus-within {
  border-color: #c8c8c8;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}

.composer-surface--drag {
  border-color: #19c37d;
  background: #f6fffb;
  box-shadow: 0 0 0 2px rgba(25, 195, 125, 0.2);
}

.composer-input-wrap {
  position: relative;
  padding: 14px 16px 6px;
}

.input-clear-btn {
  position: absolute;
  right: 12px;
  top: 10px;
  z-index: 2;
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  transition: color 0.12s, background 0.12s;
}

.input-clear-btn:hover {
  color: #202020;
  background: #f0f0f0;
}

.composer-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px 10px 8px;
  border-top: 1px solid #f0f0f0;
}

.footer-bar-primary {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  gap: 0;
}

.footer-upload :deep(.el-upload) {
  display: flex;
}

.footer-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #3f3f46;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.footer-icon-btn:hover:not(:disabled) {
  background: #ececec;
  color: #202020;
}

.footer-icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.footer-vdiv {
  width: 1px;
  height: 18px;
  margin: 0 8px 0 4px;
  flex-shrink: 0;
  background: #e8e8e8;
}

.model-pill-select {
  flex: 0 0 auto;
  --el-select-input-color: var(--chat-text-primary, #202020);
  --el-text-color-regular: var(--chat-text-primary, #202020);
  --el-text-color-placeholder: var(--chat-text-muted, #71717a);
}

.model-pill-select :deep(.el-select__selected-item),
.model-pill-select :deep(.el-input__inner) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deep-think-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  flex-shrink: 0;
}

.footer-bar-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.footer-bar-tools--inline {
  flex: 0 0 auto;
  margin-left: 6px;
}

/* 宽度由 :style 绑定；默认无框，悬停/聚焦才显形 */
.model-pill-select :deep(.el-select__wrapper),
.model-pill-select :deep(.el-input__wrapper) {
  border-radius: 10px !important;
  box-shadow: none !important;
  background: transparent !important;
  border: 1px solid transparent !important;
  padding: 2px 8px !important;
  min-height: 32px;
  transition: background 0.12s, border-color 0.12s, box-shadow 0.12s !important;
}

.model-pill-select :deep(.el-select__selection),
.model-pill-select :deep(.el-input__inner),
.model-pill-select :deep(.el-select__selected-item),
.model-pill-select :deep(.el-select__selected-item span),
.model-pill-select :deep(.el-select__placeholder) {
  color: var(--chat-text-primary, #202020) !important;
  font-size: 13px;
}

.model-pill-select :deep(.el-input__wrapper:hover),
.model-pill-select :deep(.el-select__wrapper:hover) {
  background: var(--chat-model-select-hover, #f4f4f4) !important;
}

.model-pill-select :deep(.el-input__wrapper.is-focus),
.model-pill-select :deep(.el-select__wrapper.is-focused) {
  border-color: var(--chat-border-subtle, #d0d0d0) !important;
  box-shadow: none !important;
}

.model-pill-select :deep(.el-select__caret),
.model-pill-select :deep(.el-input__suffix .el-icon) {
  color: var(--chat-text-muted, #909399);
}

.deep-think-wrap {
  display: inline-flex;
  align-items: center;
  margin-left: 0;
}

.deep-think-toggle {
  --toggle-glow: rgba(32, 32, 32, 0.12);
  --toggle-ring: rgba(32, 32, 32, 0.35);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  padding: 6px 12px;
  border: 1px solid #e4e4e7;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  line-height: 1.35;
  color: #71717a;
  background: #fafafa;
  cursor: pointer;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    border-color 0.18s ease;
}

.deep-think-toggle-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
  transform-origin: center;
  opacity: 0;
  background: transparent;
  pointer-events: none;
}

.deep-think-toggle-dot--live {
  background: var(--toggle-ring);
  animation: composer-toggle-dot-breathe 2.4s ease-in-out infinite;
  will-change: transform, opacity;
}

.deep-think-toggle--think {
  --toggle-glow: var(--chat-toggle-think-glow);
  --toggle-ring: var(--chat-toggle-think-ring);
}

.deep-think-toggle--web {
  --toggle-glow: var(--chat-toggle-web-glow);
  --toggle-ring: var(--chat-toggle-web-ring);
}

.deep-think-toggle:hover:not(.deep-think-toggle--on) {
  color: #3f3f46;
  background: #f4f4f5;
  border-color: #d4d4d8;
}

.deep-think-toggle--on {
  color: #18181b;
  font-weight: 500;
  background: #fff;
  border-color: #202020;
}

.deep-think-toggle--on:hover {
  color: #18181b;
  background: #fff;
  border-color: #202020;
}

@keyframes composer-toggle-dot-breathe {
  0%,
  100% {
    transform: scale(0.72);
    opacity: 0.45;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .deep-think-toggle-dot--live {
    animation: none;
    transform: scale(1);
    opacity: 0.85;
  }
}

.msg-actions {
  margin-top: 12px;
}

.msg-actions--after-model {
  margin-top: 0;
  flex: 0 1 auto;
  min-width: 0;
}

.msg-actions-bar {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 0;
  border-radius: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  max-width: 100%;
}

.msg-actions-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: rgba(148, 163, 184, 0.45);
  border-radius: 1px;
  flex-shrink: 0;
}

.msg-copy-group {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  gap: 0;
}

.msg-copy-group :deep(.msg-copy-dd.el-dropdown) {
  margin: 0;
}

.msg-copy-group :deep(.msg-copy-dd .el-button-group) {
  gap: 0;
  border-radius: 0;
  overflow: visible;
}

/* split 下拉里竖线为 caret 按钮 ::before，去掉后与主键贴紧、类似原生 select */
.msg-copy-group :deep(.el-dropdown__caret-button::before) {
  display: none !important;
}

.msg-copy-group :deep(.msg-copy-dd .el-button-group > .el-button + .el-button) {
  margin-left: 0;
  border-left: none !important;
}

.msg-variant-nav {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: 2px;
  padding: 0 4px;
  flex-shrink: 0;
}

.msg-variant-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: color 0.12s, background 0.12s;
}

.msg-variant-btn:hover:not(:disabled) {
  color: #475569;
  background: rgba(15, 23, 42, 0.06);
}

.msg-variant-btn:disabled {
  opacity: 0.35;
  cursor: default;
}

.msg-variant-txt {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  padding: 0 2px;
  user-select: none;
}

.msg-variant-cur {
  font-weight: 600;
  color: #1e293b;
}

.msg-variant-slash {
  color: #64748b;
}

.msg-variant-tot {
  color: #94a3b8;
}

.msg-copy-dd {
  vertical-align: middle;
}

.msg-copy-dd-main {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #475569;
}

.msg-copy-dd-ico {
  flex-shrink: 0;
}

.msg-actions-bar :deep(.msg-copy-dd.el-dropdown) {
  margin: 0;
}

.msg-actions-bar :deep(.msg-copy-dd .el-button-group) {
  display: inline-flex;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: none;
}

.msg-actions-bar :deep(.msg-copy-dd .el-button-group > .el-button) {
  --el-button-bg-color: transparent;
  --el-button-border-color: transparent;
  --el-button-hover-bg-color: rgba(15, 23, 42, 0.06);
  --el-button-hover-border-color: transparent;
  --el-button-hover-text-color: #0f172a;
  --el-button-text-color: #475569;
  height: 30px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
}

.msg-actions-bar :deep(.msg-copy-dd .el-button-group > .el-button:first-child) {
  border-radius: 8px 0 0 8px;
}

.msg-actions-bar :deep(.msg-copy-dd .el-button-group > .el-button:last-child) {
  border-radius: 0 8px 8px 0;
}

.msg-actions-bar :deep(.msg-copy-dd .el-button-group > .el-button:focus-visible) {
  outline: 2px solid rgba(59, 130, 246, 0.45);
  outline-offset: 1px;
}

/* 覆盖上方通用 padding，使复制主键与下拉箭头无竖线且间距更紧（与 .msg-copy-group 内 ::before 隐藏配合） */
.msg-actions-bar .msg-copy-group :deep(.msg-copy-dd .el-button-group > .el-button:first-child) {
  padding: 0 2px 0 6px;
  min-width: 26px;
}

.msg-actions-bar .msg-copy-group :deep(.msg-copy-dd .el-button-group > .el-button:last-child) {
  padding: 0 6px 0 2px;
  min-width: 24px;
}

.msg-act-vote {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.msg-act-ico-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition:
    background 0.14s ease,
    color 0.14s ease,
    transform 0.12s ease;
}

.msg-act-ico-btn:hover:not(:disabled) {
  background: rgba(15, 23, 42, 0.06);
  color: #334155;
}

.msg-act-ico-btn:active:not(:disabled) {
  transform: scale(0.96);
}

.msg-act-ico-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.msg-act-ico-success {
  color: #059669;
}

.msg-act-ico-btn--like-on {
  color: #d97706;
  background: rgba(254, 243, 199, 0.85);
}

.msg-act-ico-btn--like-on:hover:not(:disabled) {
  color: #b45309;
  background: rgba(253, 230, 138, 0.95);
}

.msg-act-ico-btn--dislike-on {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.75);
}

.msg-act-ico-btn--dislike-on:hover:not(:disabled) {
  color: #991b1b;
  background: rgba(254, 202, 202, 0.9);
}

.msg-thumb-svg {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
}

.msg-thumb-svg--down {
  transform: scaleY(-1);
}

.share-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.share-dlg :deep(.el-dialog__header) {
  padding-bottom: 8px;
  margin-right: 0;
}

.share-dlg :deep(.el-dialog__title) {
  font-weight: 700;
  font-size: 16px;
  color: #0f172a;
}

.share-dlg :deep(.el-dialog__body) {
  padding-top: 4px;
}

.share-ta :deep(.el-textarea__inner) {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
  padding: 12px 14px;
  resize: none;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
}

.share-dlg :deep(.el-dialog__footer) {
  padding-top: 8px;
  gap: 10px;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.send-fab {
  width: 40px !important;
  height: 40px !important;
  min-width: 40px !important;
  padding: 0 !important;
  flex-shrink: 0;
  background: #202020 !important;
  border-color: #202020 !important;
}

.send-fab:hover:not(:disabled) {
  background: #000 !important;
  border-color: #000 !important;
}

.send-fab.is-disabled {
  background: #e4e4e4 !important;
  border-color: #e4e4e4 !important;
  color: #a1a1aa !important;
}

.attach-strip {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px 8px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.attach-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 6px 4px 10px;
  border-radius: 999px;
  background: #ececec;
  font-size: 13px;
  color: #303030;
}

.attach-chip-icon {
  flex-shrink: 0;
  color: #6e6e6e;
  font-size: 14px;
}

.attach-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.attach-chip-remove {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 2px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #6e6e6e;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.attach-chip-remove:hover {
  background: rgba(0, 0, 0, 0.08);
  color: #202020;
}

.attach-limit {
  flex-shrink: 0;
  font-size: 12px;
  color: #8e8e8e;
  padding-top: 4px;
}

.composer-input {
  width: 100%;
}

.composer-input :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none !important;
  padding: 6px 52px 10px 4px;
  min-height: 72px;
  font-size: 15px;
  line-height: 1.65;
  background: transparent;
  color: #202020;
}

.composer-input :deep(.el-textarea__inner::placeholder) {
  color: #c0c4cc;
}

.composer-note {
  max-width: 58rem;
  margin: 10px auto 0;
  padding: 0 24px;
  box-sizing: border-box;
  font-size: 11px;
  color: #9b9b9b;
  text-align: center;
}

/* —— 响应式：平板收窄侧栏；手机侧栏抽屉 + 顶栏 + 安全区 + 100dvh —— */
.chat-app--tablet :deep(.sidebar) {
  width: 216px;
}

.chat-app--tablet .messages {
  padding: 0 14px;
}

.chat-app--tablet .thread-head {
  padding: 12px 14px 10px;
}

.chat-app--tablet .composer {
  padding-left: 14px;
  padding-right: 14px;
}

.chat-app--tablet .composer-note {
  padding-left: 14px;
  padding-right: 14px;
}

.sidebar-scrim {
  position: fixed;
  inset: 0;
  z-index: 150;
  background: rgba(15, 23, 42, 0.42);
  touch-action: none;
}

.mobile-nav {
  display: none;
}

.chat-app--mobile {
  height: 100dvh;
  min-height: 100dvh;
  max-height: 100dvh;
}

.chat-app--mobile.chat-app--sidebar-open {
  touch-action: none;
}

.chat-app--mobile :deep(.sidebar) {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: min(300px, 88vw);
  z-index: 160;
  transform: translateX(-105%);
  transition: transform 0.22s ease;
  box-shadow: 4px 0 28px rgba(0, 0, 0, 0.14);
  border-right: 1px solid #e5e5e5;
}

.chat-app--mobile :deep(.sidebar.sidebar--drawer-open) {
  transform: translateX(0);
}

.chat-app--mobile .mobile-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  padding-left: max(10px, env(safe-area-inset-left));
  padding-right: max(10px, env(safe-area-inset-right));
  padding-top: max(8px, env(safe-area-inset-top));
  border-bottom: 1px solid var(--chat-border, #ececec);
  background: var(--chat-bg-main, #fafafa);
  flex-shrink: 0;
}

.chat-app--mobile .mobile-nav .thread-head-tools {
  margin-left: auto;
}

.mobile-nav-title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: #202020;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-nav-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 12px;
  background: #fff;
  color: #3f3f46;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.mobile-nav-btn--accent {
  color: #1d4ed8;
  background: #eff6ff;
}

.chat-app--mobile .thread-head {
  max-width: none;
  margin: 0;
  width: auto;
  padding: 10px 14px 8px;
  padding-left: max(14px, env(safe-area-inset-left));
  padding-right: max(14px, env(safe-area-inset-right));
}

.chat-app--mobile .thread-hint {
  max-width: none;
  font-size: 11px;
}

.chat-app--mobile .thread-tokens {
  max-width: none;
  font-size: 11px;
}

.chat-app--mobile .messages-scroll-inner {
  padding: 12px 0 12px;
}

.chat-app--mobile .bubble-inner--user {
  column-gap: 6px;
}

.chat-app--mobile .user-msg-copy-btn {
  width: 26px;
  height: 26px;
}

.chat-app--mobile .messages {
  padding: 0 12px;
  max-width: none;
}

.chat-app--mobile .bubble-row {
  margin-bottom: 16px;
}

.chat-app--mobile .bubble-inner {
  padding: 10px 14px;
  font-size: 14px;
}

.chat-app--mobile .composer {
  padding: 6px 10px max(12px, env(safe-area-inset-bottom));
  padding-left: max(10px, env(safe-area-inset-left));
  padding-right: max(10px, env(safe-area-inset-right));
}

.chat-app--mobile .composer-surface {
  max-width: none;
  border-radius: 16px;
}

.chat-app--mobile .composer-footer-bar {
  flex-wrap: wrap;
  row-gap: 8px;
}

.chat-app--mobile .footer-bar-primary {
  gap: 6px;
}

.chat-app--mobile .footer-vmotion {
  margin: 0;
}

.chat-app--mobile .model-pill-select {
  flex-shrink: 1;
  min-width: 0;
  max-width: min(148px, calc(100vw - 11.5rem));
}

.chat-app--mobile .model-pill-select :deep(.el-select__wrapper),
.chat-app--mobile .model-pill-select :deep(.el-input__wrapper) {
  padding: 2px 6px !important;
}

.chat-app--mobile .deep-think-group {
  margin-left: 0;
  gap: 6px;
}

.chat-app--mobile .deep-think-toggle {
  gap: 4px;
  padding: 6px 9px;
}

.chat-app--mobile .deep-think-toggle-dot {
  width: 4px;
  height: 4px;
}

.chat-app--mobile .send-fab {
  width: 48px !important;
  height: 48px !important;
}

.chat-app--mobile .footer-icon-btn {
  width: 44px;
  height: 44px;
}

.chat-app--mobile .quick-prompts {
  padding: 0 8px;
}

.chat-app--mobile .empty {
  padding: 28px 16px 40px;
}

@media (max-width: 719px) {
  .bubble-md :deep(table) {
    display: block;
    overflow-x: auto;
    max-width: 100%;
  }
}
</style>

<!-- 下拉挂到 body，须非 scoped。组件会把下拉 min-width 内联成与触发器同宽，须 !important 加宽 -->
<style>
.el-select-dropdown.model-select-dropdown {
  min-width: var(--chat-model-dd-min, 200px) !important;
  max-width: min(92vw, 560px);
  width: max-content;
}

.el-select-dropdown.model-select-dropdown .el-select-dropdown__item {
  white-space: normal;
  word-break: break-word;
  height: auto;
  min-height: 36px;
  line-height: 1.45;
  padding: 10px 14px;
}

/* 助手消息操作条：复制 / 更多 下拉挂 body */
.msg-actions-dd-popper.el-popper {
  border-radius: 12px !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.12) !important;
}

.msg-actions-dd-popper .el-dropdown-menu {
  padding: 6px !important;
}

.msg-actions-dd-popper .el-dropdown-menu__item {
  border-radius: 8px;
  padding: 10px 12px !important;
  margin: 0 !important;
  line-height: 1.35 !important;
}

.msg-actions-dd-popper .el-dropdown-menu__item:not(.is-disabled):hover {
  background: #f1f5f9 !important;
  color: #0f172a !important;
}
</style>
