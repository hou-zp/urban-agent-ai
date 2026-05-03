<template>
  <section class="chat-scene">
    <aside class="chat-history-rail">
      <div class="chat-rail-top">
        <div class="chat-brand-row">
          <div class="chat-brand-mark">AI</div>
          <div class="chat-brand-name">风险提升综合智能体</div>
          <div class="chat-brand-right">
            <a-dropdown placement="bottomRight" trigger="click">
              <a-button class="chat-rail-icon" type="text">
                <UserOutlined />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="user-info" disabled>
                    <div class="chat-user-menu-item">
                      <span class="chat-user-menu-item__name">{{ authStore.user?.userId }}</span>
                      <span class="chat-user-menu-item__role">{{ authStore.user?.role }}</span>
                    </div>
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item v-for="item in workbenchNavItems" :key="item.to" @click="handleWorkbenchNav(item)">{{ item.label }}</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>

        <a-button block class="chat-new-button" @click="handleCreateSession">
          <template #icon>
            <PlusOutlined />
          </template>
          开启新对话
        </a-button>
      </div>

      <div class="chat-history-scroll">
        <div v-for="group in historyGroups" :key="group.label" class="chat-history-group">
          <div class="chat-history-label">{{ group.label }}</div>
          <div
            v-for="session in group.sessions"
            :key="session.id"
            class="chat-history-item"
            :class="{ 'is-active': session.id === store.currentSession?.id }"
          >
            <a-dropdown :trigger="['contextMenu']" placement="leftTop">
              <button
                class="chat-history-item__btn"
                type="button"
                :disabled="store.loading || store.creatingSession || store.sending"
                @click="handleSelectSession(session.id)"
              >
                <div class="chat-history-title">{{ session.title }}</div>
                <div class="chat-history-tags">
                  <span v-for="type in sessionCategories(session)" :key="`${session.id}-${type}`" class="chat-history-tag">
                    {{ type }}
                  </span>
                </div>
              </button>
              <template #overlay>
                <a-menu @click="({ key }) => handleSessionAction(session.id, key as string)">
                  <a-menu-item key="delete">
                    <DeleteOutlined /> 删除会话
                  </a-menu-item>
                  <a-menu-item key="archive">
                    <FolderOutlined /> 归档会话
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>
      </div>
    </aside>

    <main class="chat-main-stage">
      <a-alert
        v-if="authRequired"
        class="chat-auth-alert"
        type="warning"
        show-icon
        message="当前后端需要 JWT 身份，请先配置后再继续对话。"
      >
        <template #description>
          <div class="chat-auth-alert__content">
            <span>{{ store.error || '请点击右上角“配置 JWT”完成登录。' }}</span>
            <a-button size="small" type="primary" @click="openAuthDrawer">
              去配置 JWT
            </a-button>
          </div>
        </template>
      </a-alert>

      <div class="chat-reading-column">
        <template v-for="message in displayMessages" :key="message.id">
          <div v-if="message.role === 'USER'" class="chat-user-block">
            <div class="chat-user-question">{{ message.content }}</div>
            <div class="chat-user-meta">
              <a-space :size="8">
                <a-button class="chat-inline-icon" type="text" aria-label="复制用户消息" @click="copyMessage(message.content)">
                  <CopyOutlined />
                </a-button>
                <a-button class="chat-inline-icon" type="text" aria-label="修改消息" @click="editMessage(message)">
                  <EditOutlined />
                </a-button>
              </a-space>
              <a-avatar class="chat-user-avatar" :size="34">
                <UserOutlined />
              </a-avatar>
            </div>
          </div>

          <div v-else :ref="(el) => setAssistantMessageRef(message.id, el)" class="chat-assistant-block">
            <div class="chat-assistant-header">
              <div class="chat-assistant-brand">AI</div>
            </div>

            <div class="chat-assistant-body">
              <div v-if="messageDisplayHeadline(message)" class="chat-message-headline">
                {{ messageDisplayHeadline(message) }}
              </div>
              <div v-if="!isOilFumeCompositeMessage(message) && (messageDisplayBody(message) || store.streaming)" class="chat-message-content">
                {{ messageDisplayBody(message) || '正在组织回答...' }}
              </div>

              <div v-if="messageDisplaySuggestion(message)" class="chat-suggestion-card">
                <div class="chat-suggestion-card__label">处置建议</div>
                <div class="chat-suggestion-card__content">{{ messageDisplaySuggestion(message) }}</div>
              </div>

              <div v-if="oilFumeOverviewItems(message).length" class="chat-overview-metrics">
                <div
                  v-for="item in oilFumeOverviewItems(message)"
                  :key="`${message.id}-${item.label}`"
                  class="chat-overview-metric"
                >
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                  <em v-if="item.detail">{{ item.detail }}</em>
                </div>
              </div>

              <div v-if="message.riskLevel || message.reviewStatus" class="chat-assistant-flags">
                <a-space wrap :size="[8, 8]">
                  <a-tag v-if="message.riskLevel" :color="riskColor(message.riskLevel)">{{ riskLabel(message.riskLevel) }}</a-tag>
                  <a-tag v-if="message.reviewStatus" color="gold">{{ reviewLabel(message.reviewStatus) }}</a-tag>
                </a-space>
              </div>

              <RiskWarning
                v-if="message === store.latestAssistantMessage && riskWarning"
                :risk-level="riskWarning.riskLevel"
                :risk-categories="riskWarning.riskCategories"
                :message="riskWarning.message"
                :requires-review="riskWarning.requiresReview"
                :review-status="riskWarning.reviewStatus"
              />

              <div
                v-for="(queryCard, cardIndex) in messageVisibleQueryCards(message)"
                :key="`${message.id}-query-${cardIndex}-${queryCard.metricCode || 'metric'}`"
                class="chat-query-report"
              >
                <div v-if="messageVisibleQueryCards(message).length > 1" class="chat-query-section-title">
                  {{ queryCard.metricName || '统计结果' }}
                </div>

                <div v-if="isScalarQuery(queryCard)" class="chat-query-kpi">
                  <span class="chat-query-kpi-label">{{ queryCard.metricName || '统计结果' }}</span>
                  <strong>{{ formatPrimaryQueryValue(queryCard) }}</strong>
                </div>

                <QueryChart
                  v-else-if="supportsQueryChart(queryCard)"
                  :query-card="queryCard"
                />

                <div v-if="shouldShowQueryTable(queryCard)" class="chat-query-table">
                  <a-table
                    :columns="queryTableColumns(queryCard)"
                    :data-source="queryCard.rows"
                    :pagination="queryPagination(queryCard.rowCount)"
                    :row-key="queryRowKey"
                    size="small"
                    :scroll="{ x: 'max-content' }"
                  >
                    <template #bodyCell="{ text, column }">
                      {{ formatCell(text, String(column.dataIndex), queryCard) }}
                    </template>
                  </a-table>
                </div>

                <div v-if="businessWarnings(queryCard).length" class="chat-query-tips">
                  <div
                    v-for="warning in businessWarnings(queryCard)"
                    :key="`${queryCard.metricCode}-${warning}`"
                    class="chat-query-tip"
                  >
                    {{ warning }}
                  </div>
                </div>
              </div>

              <div v-if="isOilFumeCompositeMessage(message) && messageDisplayBody(message)" class="chat-message-content chat-message-content--insight">
                {{ messageDisplayBody(message) }}
              </div>

              <div v-if="message === store.latestAssistantMessage && dataLineages.length" class="chat-data-lineage-list">
                <DataLineageCard
                  v-for="(lineage, index) in dataLineages"
                  :key="`lineage-${index}`"
                  :lineage="lineage"
                />
              </div>

              <CaliberExplanation
                v-if="message === store.latestAssistantMessage && caliberExplanationText"
                :text="caliberExplanationText"
              />

              <div class="chat-message-actions">
                <a-space :size="6">
                  <a-tooltip title="复制">
                    <a-button class="chat-inline-icon" type="text" aria-label="复制回复" @click="copyMessage(messageDisplayContent(message))">
                      <CopyOutlined />
                    </a-button>
                  </a-tooltip>
                  <a-tooltip title="有帮助">
                    <a-button
                      class="chat-inline-icon"
                      :class="{ 'is-active': feedbackByMessage[message.id] === 'like' }"
                      type="text"
                      aria-label="点赞回复"
                      @click="markFeedback(message.id, 'like')"
                    >
                      <LikeOutlined />
                    </a-button>
                  </a-tooltip>
                  <a-tooltip title="需改进">
                    <a-dropdown :trigger="['click']" placement="topLeft" overlay-class-name="chat-dislike-dropdown">
                      <a-button
                        class="chat-inline-icon"
                        :class="{ 'is-active': feedbackByMessage[message.id] === 'dislike' }"
                        type="text"
                        aria-label="点踩回复"
                      >
                        <DislikeOutlined />
                      </a-button>
                      <template #overlay>
                        <a-menu @click="({ key }) => markFeedbackWithReason(message.id, String(key))">
                          <a-menu-item key="inaccurate">内容不准确</a-menu-item>
                          <a-menu-item key="incomplete">信息不完整</a-menu-item>
                          <a-menu-item key="unclear">表述不清楚</a-menu-item>
                          <a-menu-item key="other">其他原因</a-menu-item>
                        </a-menu>
                      </template>
                    </a-dropdown>
                  </a-tooltip>
                  <a-popover
                    v-if="message.citations?.length"
                    placement="topLeft"
                    trigger="click"
                    overlay-class-name="chat-citation-popover"
                  >
                    <template #content>
                      <div class="chat-citation-panel">
                        <div
                          v-for="citation in message.citations"
                          :key="`${message.id}-${citation.documentId}-${citation.sectionTitle}`"
                        >
                          <EvidenceCard :citation="citation" />
                        </div>
                      </div>
                    </template>
                    <button class="chat-citation-link" type="button" :aria-label="`查看引用 ${message.citations.length} 条`">
                      查看依据
                    </button>
                  </a-popover>

                  <!-- 追问建议按钮组 -->
                  <div v-if="message === store.latestAssistantMessage && !store.sending" class="chat-suggestion-actions">
                    <span class="chat-suggestion-actions__label">试试再问：</span>
                    <a-space :size="4" wrap>
                      <a-button
                        v-for="sug in suggestedFollowUps"
                        :key="sug"
                        size="small"
                        class="chat-followup-btn"
                        @click="handleFollowUp(sug)"
                      >
                        {{ sug }}
                      </a-button>
                    </a-space>
                  </div>
                </a-space>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-bottom-dock">
        <div class="chat-suggestion-bar">
          <div class="chat-suggestion-content">
            <div class="chat-suggestion-list" aria-label="分类选择">
              <button
                v-for="item in quickSuggestions"
                :key="item.label"
                class="chat-suggestion-chip"
                :class="questionTypeClass(item.label)"
                type="button"
                :aria-pressed="isQuestionTypeActive(item.label)"
                @click="toggleQuestionType(item.label)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>

          <a-popover placement="topRight" trigger="click" overlay-class-name="chat-type-popover">
            <template #content>
              <div class="chat-type-menu">
                <button
                  v-for="item in quickSuggestions"
                  :key="`menu-${item.label}`"
                  class="chat-type-menu-item"
                  :class="questionTypeClass(item.label)"
                  type="button"
                  :aria-pressed="isQuestionTypeActive(item.label)"
                  @click="toggleQuestionType(item.label)"
                >
                  {{ item.label }}
                </button>
                <button class="chat-type-menu-clear" type="button" :disabled="!manualQuestionType" @click="clearManualQuestionType">
                  清除选择
                </button>
              </div>
            </template>
            <a-button class="chat-grid-button" type="text" aria-label="分类面板">
              <AppstoreOutlined />
            </a-button>
          </a-popover>
        </div>

        <div class="chat-input-shell">
          <div v-if="editingHint || editingMessageId" class="chat-input-toolbar">
            <div class="chat-input-hint">{{ editingHint }}</div>
            <a-space :size="4">
              <a-button
                v-if="editingMessageId"
                class="chat-toolbar-link"
                type="link"
                @click="resetComposerState"
              >
                重置
              </a-button>
            </a-space>
          </div>
          <a-textarea
            ref="messageInputRef"
            v-model:value="draft"
            class="chat-input-box"
            :auto-size="{ minRows: 1, maxRows: 5 }"
            :maxlength="2000"
            :disabled="!store.currentSession || store.sending"
            :placeholder="inputPlaceholder"
            @keydown="handleInputKeydown"
          />
          <a-button
            class="chat-send-button"
            type="text"
            :loading="store.sending"
            :disabled="!canSend"
            aria-label="发送"
            @click="handleSend"
          >
            <ArrowUpOutlined />
          </a-button>
        </div>
      </div>
    </main>

    <a-drawer
      v-model:open="detailsOpen"
      width="380"
      placement="right"
      title="运行详情"
    >
      <div class="chat-detail-drawer">
        <div class="chat-detail-actions">
          <a-tag v-if="store.currentSession" :color="statusColor(store.currentSession.status)">
            {{ statusLabel(store.currentSession.status) }}
          </a-tag>
          <a-tag v-if="store.currentRunId" color="blue">Run {{ shortId(store.currentRunId) }}</a-tag>
        </div>

        <div class="chat-detail-actions">
          <a-space>
            <a-button size="small" :disabled="!canCancel" @click="store.cancelCurrentRun">取消</a-button>
            <a-button size="small" :disabled="!canResume" @click="store.resumeCurrentRun">恢复</a-button>
          </a-space>
          <a-space :size="8">
            <a-switch v-model:checked="useStreaming" size="small" />
            <span class="chat-detail-caption">流式输出</span>
          </a-space>
        </div>

        <a-alert
          v-if="store.error"
          style="margin-top: 12px"
          type="error"
          show-icon
          :message="store.error"
        />

        <a-alert
          v-if="store.riskNotice"
          style="margin-top: 12px"
          type="warning"
          show-icon
          :message="`法制审核：${riskLabel(store.riskNotice.riskLevel)}`"
          :description="`审核单号：${store.riskNotice.reviewId ?? '待回填'}`"
        />

        <div class="chat-detail-section">
          <div class="chat-detail-title-row">
            <div class="chat-detail-title">计划步骤</div>
            <div v-if="store.currentSession" class="chat-plan-title-actions">
              <a-tooltip placement="left" title="整理当前会话、执行进度、系统接力和关键步骤">
                <a-button
                  size="small"
                  type="text"
                  class="chat-plan-share-button"
                  @click="copyPlanDiagnosticSummary"
                >
                  <template #icon>
                    <CopyOutlined />
                  </template>
                  复制诊断摘要
                </a-button>
              </a-tooltip>
              <a-tooltip placement="left" title="会带上当前会话、筛选条件和步骤定位">
                <a-button
                  size="small"
                  type="text"
                  class="chat-plan-share-button"
                  @click="copyPlanShareLink"
                >
                  <template #icon>
                    <CopyOutlined />
                  </template>
                  复制排查链接
                </a-button>
              </a-tooltip>
            </div>
          </div>
          <div v-if="store.activePlan?.steps.length" class="chat-plan-filters" aria-label="计划筛选">
            <button
              v-for="filter in planFilters"
              :key="filter.key"
              type="button"
              class="chat-plan-filters__item"
              :class="{ 'is-active': activePlanFilter === filter.key }"
              @click="setPlanFilter(filter.key)"
            >
              <span>{{ filter.label }}</span>
              <strong>{{ planFilterCount(filter.key) }}</strong>
            </button>
          </div>
          <div v-if="activePlanProgress" class="chat-plan-progress">
            <div class="chat-plan-progress__header">
              <strong>执行概览</strong>
              <span>{{ activePlanProgress.headline }}</span>
            </div>
            <a-progress
              :percent="activePlanProgress.percent"
              :show-info="false"
              :status="activePlanProgress.progressStatus"
              size="small"
            />
            <div class="chat-plan-progress__stats">
              <div class="chat-plan-progress__stat">
                <span class="chat-plan-progress__value">{{ activePlanProgress.completedCount }}/{{ activePlanProgress.totalCount }}</span>
                <span class="chat-plan-progress__label">已完成</span>
              </div>
              <div class="chat-plan-progress__stat">
                <span class="chat-plan-progress__value">{{ activePlanProgress.runningCount }}</span>
                <span class="chat-plan-progress__label">进行中</span>
              </div>
              <div class="chat-plan-progress__stat">
                <span class="chat-plan-progress__value">{{ activePlanProgress.failedCount }}</span>
                <span class="chat-plan-progress__label">异常</span>
              </div>
            </div>
            <div v-if="activePlanProgress.focusLine" class="chat-plan-progress__focus">
              {{ activePlanProgress.focusLine }}
            </div>
          </div>
          <div v-if="store.activePlan?.systemSummary" class="chat-plan-summary">
            <div class="chat-plan-summary__header">
              <strong>系统接力概览</strong>
              <span>{{ formatPlanActionTime(store.activePlan.systemSummary.lastActionAt) }}</span>
            </div>
            <div class="chat-plan-summary__stats">
              <div class="chat-plan-summary__stat">
                <span class="chat-plan-summary__value">{{ store.activePlan.systemSummary.autorunCount }}</span>
                <span class="chat-plan-summary__label">自动补跑</span>
              </div>
              <div class="chat-plan-summary__stat">
                <span class="chat-plan-summary__value">{{ store.activePlan.systemSummary.recoverCount }}</span>
                <span class="chat-plan-summary__label">自动重建</span>
              </div>
              <div class="chat-plan-summary__stat">
                <span class="chat-plan-summary__value">{{ store.activePlan.systemSummary.affectedStepCount }}</span>
                <span class="chat-plan-summary__label">影响步骤</span>
              </div>
            </div>
            <div class="chat-plan-summary__text">{{ store.activePlan.systemSummary.summary }}</div>
          </div>
          <a-steps v-if="filteredPlanSteps.length" direction="vertical" size="small">
            <a-step
              v-for="step in filteredPlanSteps"
              :key="step.id"
              :title="`${step.stepOrder}. ${step.name}`"
              :description="step.outputSummary || step.goal"
              :status="stepStatus(step.status)"
            >
              <template #description>
                <div
                  :ref="(el) => setPlanStepRef(step.stepOrder, el)"
                  class="chat-plan-step-detail"
                  :class="{ 'is-focused': focusedPlanStepOrder === step.stepOrder }"
                >
                  <div>{{ step.outputSummary || step.goal }}</div>
                  <div v-if="planTraceItems(step).length" class="chat-plan-step-trace">
                    <span class="chat-plan-step-trace__label">执行来源</span>
                    <div class="chat-plan-step-trace__list">
                      <span
                        v-for="trace in planTraceItems(step)"
                        :key="`${step.id}-${trace.label}-${trace.value}`"
                        class="chat-plan-step-trace__item"
                        :class="trace.tone ? `is-${trace.tone}` : ''"
                      >
                        <strong>{{ trace.label }}</strong>
                        <em>{{ trace.value }}</em>
                      </span>
                    </div>
                    <button
                      v-if="planResultEntry(step)"
                      type="button"
                      class="chat-plan-step-trace__action"
                      @click="openPlanResult(step)"
                    >
                      查看{{ planResultEntry(step)?.label }}
                    </button>
                  </div>
                  <div v-if="planDependencySteps(step).length" class="chat-plan-step-dependencies">
                    <span class="chat-plan-step-dependencies__label">依赖步骤</span>
                    <div class="chat-plan-step-dependencies__list">
                      <button
                        v-for="dependency in planDependencySteps(step)"
                        :key="`${step.id}-${dependency.id}`"
                        type="button"
                        class="chat-plan-step-dependencies__item"
                        @click="focusPlanStep(dependency.stepOrder)"
                      >
                        {{ dependency.stepOrder }}. {{ dependency.name }}
                      </button>
                    </div>
                  </div>
                  <div v-if="step.outputPayload" class="chat-plan-artifact">
                    <div class="chat-plan-artifact__header">
                      <a-tag color="cyan">{{ planArtifactTitle(step) }}</a-tag>
                      <span v-if="step.outputPayload.queryId" class="chat-plan-artifact__id">
                        Query {{ shortId(step.outputPayload.queryId) }}
                      </span>
                    </div>
                    <div v-if="planArtifactFacts(step).length" class="chat-plan-artifact__facts">
                      <div
                        v-for="fact in planArtifactFacts(step)"
                        :key="`${step.id}-${fact.label}`"
                        class="chat-plan-artifact__fact"
                      >
                        <span>{{ fact.label }}</span>
                        <strong>{{ fact.value }}</strong>
                      </div>
                    </div>
                    <div
                      v-if="step.outputPayload.summary && step.outputPayload.summary !== step.outputSummary"
                      class="chat-plan-artifact__summary"
                    >
                      {{ step.outputPayload.summary }}
                    </div>
                    <div v-if="step.outputPayload.warnings?.length" class="chat-plan-artifact__warnings">
                      <a-tag v-for="warning in step.outputPayload.warnings" :key="warning" color="orange">
                        {{ warning }}
                      </a-tag>
                    </div>
                  </div>
                  <div v-if="step.systemActions?.length" class="chat-plan-step-actions">
                    <div
                      v-for="action in step.systemActions"
                      :key="`${step.id}-${action.action}-${action.dependencyStepOrder}-${action.createdAt}`"
                      class="chat-plan-step-action"
                    >
                      <a-tag :color="action.action === 'RECOVER' ? 'gold' : 'blue'">
                        {{ action.action === 'RECOVER' ? '自动重建' : '自动补跑' }}
                      </a-tag>
                      <span>{{ formatPlanSystemAction(action) }}</span>
                    </div>
                  </div>
                  <div v-if="step.failureDetail && step.status === 'FAILED'" class="chat-plan-step-hint">
                    <div class="chat-plan-step-hint__header">
                      <a-tag :color="step.failureDetail.dependencyBlocked ? 'orange' : 'red'">
                        {{ step.failureDetail.actionLabel }}
                      </a-tag>
                      <span>{{ step.failureDetail.headline }}</span>
                    </div>
                    <div v-if="step.failureDetail.errorCode" class="chat-plan-step-hint__code">
                      错误码：{{ step.failureDetail.errorCode }}
                    </div>
                    <div v-if="step.failureDetail.handleCode" class="chat-plan-step-hint__code">
                      建议动作：{{ planHandleCodeLabel(step.failureDetail.handleCode) }}
                    </div>
                    <div>{{ step.failureDetail.reason }}</div>
                    <div v-if="planRetryDependencySteps(step).length" class="chat-plan-step-hint__deps">
                      <span>关联步骤：</span>
                      <button
                        v-for="dependency in planRetryDependencySteps(step)"
                        :key="`${step.id}-retry-${dependency.stepOrder}`"
                        type="button"
                        class="chat-plan-step-hint__link"
                        @click="focusPlanStep(dependency.stepOrder)"
                      >
                        {{ dependency.stepOrder }}. {{ dependency.name }}
                      </button>
                    </div>
                  </div>
                </div>
              </template>
            </a-step>
          </a-steps>
          <a-empty
            v-else
            :image="null"
            :description="store.activePlan?.steps.length ? '当前筛选下暂无步骤' : '暂无复杂任务计划'"
          />
        </div>

        <div ref="citationsSectionRef" class="chat-detail-section">
          <div class="chat-detail-title">引用来源</div>
          <template v-if="citations.length">
            <div v-for="citation in citations" :key="`${citation.documentId}-${citation.sectionTitle}`">
              <EvidenceCard :citation="citation" />
            </div>
          </template>
          <a-empty v-else :image="null" description="暂无引用来源" />
        </div>

        <div class="chat-detail-section">
          <div class="chat-detail-title">事件流</div>
          <template v-if="store.streamEvents.length">
            <div v-for="event in store.streamEvents" :key="event.id" class="chat-detail-card">
              <strong>{{ eventLabel(event.event) }}</strong>
              <div class="chat-detail-meta">{{ formatTime(event.createdAt) }}</div>
              <p>{{ event.data || '等待事件详情' }}</p>
            </div>
          </template>
          <a-empty v-else :image="null" description="等待下一次运行" />
        </div>
      </div>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import antMessage from 'ant-design-vue/es/message'
import {
  AppstoreOutlined,
  ArrowUpOutlined,
  CopyOutlined,
  DeleteOutlined,
  DislikeOutlined,
  EditOutlined,
  FolderOutlined,
  LikeOutlined,
  MenuOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { MessageCitationView, MessageView, PlanStepView, PlanView, QueryConversationView, SessionView } from '@/types/api'
import { inferSessionQuestionTypes, QUESTION_TYPE_OPTIONS, type QuestionTypeLabel } from '@/utils/chat'
import {
  businessWarnings,
  formatFriendlyCell,
  friendlyQueryColumns,
  getPrimaryMetricValue,
  inferQueryChartMode,
  isScalarQuery,
  prefersChartOnly,
} from '@/utils/queryPresentation'

const QueryChart = defineAsyncComponent(() => import('@/components/QueryChart.vue'))
const EvidenceCard = defineAsyncComponent(() => import('@/components/EvidenceCard.vue'))
const DataLineageCard = defineAsyncComponent(() => import('@/components/DataLineageCard.vue'))
const RiskWarning = defineAsyncComponent(() => import('@/components/RiskWarning.vue'))
const CaliberExplanation = defineAsyncComponent(() => import('@/components/CaliberExplanation.vue'))

const route = useRoute()
const router = useRouter()
const store = useChatStore()
const authStore = useAuthStore()
const draft = ref('')
const newSessionTitle = ref('默认会话')
const useStreaming = ref(true)
const detailsOpen = ref(false)
const messageInputRef = ref<{ focus?: () => void } | null>(null)
const manualQuestionType = ref<QuestionTypeLabel | null>(null)
const editingMessageId = ref<string | null>(null)
const feedbackByMessage = ref<Record<string, 'like' | 'dislike'>>({})
const focusedPlanStepOrder = ref<number | null>(null)
const selectedPlanStepOrder = ref<number | null>(null)
const citationsSectionRef = ref<HTMLElement | null>(null)
const activePlanFilter = ref<'all' | 'failed' | 'system' | 'result'>('all')
const sessionRouteReady = ref(false)
let manualCopyTextarea: HTMLTextAreaElement | null = null
const planStepRefs = new Map<number, HTMLElement>()
const assistantMessageRefs = new Map<string, HTMLElement>()
let clearPlanFocusTimer: number | null = null

const quickSuggestions = QUESTION_TYPE_OPTIONS

const suggestedFollowUps = [
  '这个政策的适用条件是什么？',
  '相关法规条款有哪些？',
  '处置流程是什么？',
  '处罚标准是多少？',
]

const workbenchNavItems = [
  { to: '/knowledge', label: '知识文档' },
  { to: '/audit', label: '审计简表' },
]

const planFilters = [
  { key: 'all', label: '全部' },
  { key: 'failed', label: '异常' },
  { key: 'system', label: '系统接力' },
  { key: 'result', label: '有结果' },
] as const
type PlanFilterKey = (typeof planFilters)[number]['key']
const planFilterKeys = new Set<PlanFilterKey>(planFilters.map((filter) => filter.key))

const welcomeMessage = `您好！我是您的智能助手，很高兴为您服务！

我是一款专为政府机关干部和基层工作人员设计的智能政务助手。我的使命是依托先进的人工智能技术，深度融合系统的政务服务知识库、权威的政策法规库、多维度的线索库以及动态的智能分析底座，为您在日常工作中的决策研判与高效执行提供精准、可靠的信息支撑，全面提升政务工作的智能化水平和综合办事效能。

我的核心功能包括：
1. 业务咨询：针对各类政务服务流程、办事指南提供清晰、准确的解答，是您便捷的“政务百科”。
2. 政策解读：对中央及地方发布的各项政策文件进行要点梳理、关联分析和通俗化解读，帮助您快速把握政策精髓。
3. 法律法规咨询：提供常用法律法规、部门规章的查询与条文释义，为您的工作提供坚实的法律依据参考。
4. 线索处置结果查询：协助您快速查询相关事件、投诉或建议的处置进度与最终结果，方便跟踪与反馈。
5. 智能问数：对接政务数据资源，支持通过自然语言提问的方式，快速获取统计分析数据、图表简报，让数据“说话”，辅助科学决策。

随时欢迎您向我提问，让我们携手让工作更轻松、更智慧！`

const canSend = computed(() => Boolean(store.currentSession?.id) && draft.value.trim().length > 0 && !store.sending)
const authRequired = computed(() => {
  if (store.currentSession?.id) {
    return false
  }
  const error = store.error.trim()
  if (!error) {
    return false
  }
  return error.includes('未通过身份校验')
    || error.includes('unauthorized')
    || error.includes('访问权限')
})
const canCancel = computed(() => Boolean(store.currentSession?.id) && Boolean(store.currentRunId) && store.streaming && !store.cancelling)
const canResume = computed(() => {
  return Boolean(store.currentSession?.id)
    && Boolean(store.currentRunId)
    && !store.streaming
    && !store.sending
    && (Boolean(store.error) || store.streamEvents.some((event) => ['agent.cancelled', 'agent.failed'].includes(event.event)))
})
const editingHint = computed(() => {
  return editingMessageId.value ? '正在修改消息，回车发送，Shift+Enter 换行' : ''
})
const autoQuestionTypes = computed(() => inferSessionQuestionTypes(store.messages))
const highlightedQuestionTypes = computed<QuestionTypeLabel[]>(() => {
  return manualQuestionType.value ? [manualQuestionType.value] : autoQuestionTypes.value
})
const citations = computed<MessageCitationView[]>(() => store.latestAssistantMessage?.citations ?? [])
const dataLineages = computed(() => {
  // 从 queryCards 中提取数据溯源信息（由 outputPayload 注入）
  const cards = messageVisibleQueryCards(store.latestAssistantMessage ?? null)
  return cards
    .filter((card) => card.queryId || card.metricCode)
    .map((card) => ({
      queryId: card.queryId ?? '',
      dataSourceName: extractDataSource(card),
      caliber: extractCaliber(card),
      dataUpdatedAt: card.executedAt ?? new Date().toISOString(),
      permissionStatus: extractPermissionStatus(card),
      rowCount: card.rowCount,
    }))
})
const riskWarning = computed(() => {
  const msg = store.latestAssistantMessage
  if (!msg) return null
  if (msg.riskLevel && ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(msg.riskLevel)) {
    return {
      riskLevel: msg.riskLevel as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
      riskCategories: [],
      message: '',
      requiresReview: Boolean(msg.reviewId),
      reviewStatus: (msg.reviewStatus as 'PENDING' | 'APPROVED' | 'REJECTED' | null) ?? null,
    }
  }
  return null
})
const caliberExplanationText = computed(() => {
  const cards = messageVisibleQueryCards(store.latestAssistantMessage ?? null)
  if (!cards.length) return ''
  const first = cards[0]
  if (first.scopeSummary) return `数据口径：${first.scopeSummary}`
  return ''
})
const latestAssistantMessageId = computed(() => {
  const latestAssistant = [...displayMessages.value].reverse().find((message) => message.role === 'ASSISTANT')
  return latestAssistant?.id ?? null
})
const activePlanProgress = computed(() => summarizePlanProgress(store.activePlan))
const filteredPlanSteps = computed(() => {
  const steps = store.activePlan?.steps ?? []
  switch (activePlanFilter.value) {
    case 'failed':
      return steps.filter((step) => step.status === 'FAILED')
    case 'system':
      return steps.filter((step) => (step.executionTrace?.systemActionCount ?? step.systemActions?.length ?? 0) > 0)
    case 'result':
      return steps.filter((step) => Boolean(step.executionTrace?.resultRef || step.resultRef))
    default:
      return steps
  }
})
const displayMessages = computed<MessageView[]>(() => {
  if (store.messages.length) {
    return sortMessagesByTime(store.messages)
  }
  return [
    {
      id: 'welcome-assistant',
      role: 'ASSISTANT',
      content: welcomeMessage,
      createdAt: new Date().toISOString(),
    },
  ]
})
const inputPlaceholder = computed(() => {
  if (authRequired.value) {
    return '当前后端需要 JWT，请先点击右上角“配置 JWT”'
  }
  return editingMessageId.value
    ? '修改消息后按回车发送，Shift+Enter 换行'
    : '请输入问题后发送，Enter 发送，Shift+Enter 换行'
})

function sortMessagesByTime(messages: MessageView[]) {
  return messages
    .map((message, index) => ({ message, index }))
    .sort((left, right) => {
      const timeDiff = readMessageTime(left.message.createdAt) - readMessageTime(right.message.createdAt)
      return timeDiff === 0 ? left.index - right.index : timeDiff
    })
    .map((item) => item.message)
}

function readMessageTime(value: string) {
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : 0
}

const historyGroups = computed(() => {
  const groups = new Map<string, SessionView[]>()
  const sortedSessions = [...store.sessions].sort((left, right) => {
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
  })

  sortedSessions.forEach((session) => {
    const label = sessionGroupLabel(session.createdAt)
    const list = groups.get(label) ?? []
    list.push(session)
    groups.set(label, list)
  })

  return Array.from(groups.entries()).map(([label, sessions]) => ({ label, sessions }))
})

onMounted(async () => {
  await store.bootstrap(parseRouteSessionId(route.query.sid))
  sessionRouteReady.value = true
  await syncPlanQuery()
  if (authRequired.value) {
    openAuthDrawer()
  }
})

onBeforeUnmount(() => {
  if (clearPlanFocusTimer !== null) {
    window.clearTimeout(clearPlanFocusTimer)
  }
})

watch(
  () => route.query.sid,
  async (value) => {
    if (!sessionRouteReady.value) {
      return
    }

    const nextSessionId = parseRouteSessionId(value)
    if (!nextSessionId || nextSessionId === store.currentSession?.id) {
      return
    }

    const changed = await store.selectSession(nextSessionId)
    if (!changed) {
      return
    }
    draft.value = ''
    resetComposerState()
  },
)

watch(
  () => route.query.pf,
  (value) => {
    const nextFilter = value == null ? store.currentPlanInspectorState.filter : normalizePlanFilter(value)
    if (activePlanFilter.value !== nextFilter) {
      activePlanFilter.value = nextFilter
    }
  },
  { immediate: true },
)

watch(
  () => route.query.ps,
  (value) => {
    const nextStepOrder = value == null ? store.currentPlanInspectorState.selectedStepOrder : parsePlanStepOrder(value)
    if (selectedPlanStepOrder.value !== nextStepOrder) {
      selectedPlanStepOrder.value = nextStepOrder
    }
  },
  { immediate: true },
)

watch(
  () => store.currentSession?.id,
  () => {
    if (route.query.pf == null) {
      activePlanFilter.value = store.currentPlanInspectorState.filter
    }
    if (route.query.ps == null) {
      selectedPlanStepOrder.value = store.currentPlanInspectorState.selectedStepOrder
    }
    if (sessionRouteReady.value) {
      void syncPlanQuery()
    }
  },
)

watch(
  authRequired,
  (required) => {
    if (required) {
      openAuthDrawer()
    }
  },
)

watch(
  [activePlanFilter, selectedPlanStepOrder],
  () => {
    store.updateCurrentPlanInspectorState({
      filter: activePlanFilter.value,
      selectedStepOrder: selectedPlanStepOrder.value,
    })
    void syncPlanQuery()
  },
)

watch(
  [detailsOpen, selectedPlanStepOrder, () => store.activePlan?.id],
  async ([open, stepOrder]) => {
    if (!open || stepOrder == null) {
      return
    }
    await nextTick()
    applyFocusedPlanStep(stepOrder, true)
  },
)

function normalizePlanFilter(value: unknown): PlanFilterKey {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (typeof rawValue === 'string' && planFilterKeys.has(rawValue as PlanFilterKey)) {
    return rawValue as PlanFilterKey
  }
  return 'all'
}

function parsePlanStepOrder(value: unknown) {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return null
  }

  const parsed = Number.parseInt(rawValue, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function parseRouteSessionId(value: unknown) {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (typeof rawValue !== 'string') {
    return null
  }
  const normalized = rawValue.trim()
  return normalized || null
}

function buildPlanShareLink() {
  const sessionId = store.currentSession?.id
  if (!sessionId || typeof window === 'undefined') {
    return ''
  }

  const query: Record<string, string> = { sid: sessionId }
  if (activePlanFilter.value !== 'all') {
    query.pf = activePlanFilter.value
  }
  if (selectedPlanStepOrder.value != null) {
    query.ps = String(selectedPlanStepOrder.value)
  }

  const resolved = router.resolve({
    path: '/',
    query,
  })
  return new URL(resolved.href, window.location.origin).toString()
}

function buildPlanDiagnosticSummary() {
  const session = store.currentSession
  if (!session) {
    return ''
  }

  const lines = [
    `会话：${session.title}`,
    `会话ID：${session.id}`,
    `会话状态：${statusLabel(session.status)}`,
  ]

  if (store.currentRunId) {
    lines.push(`运行ID：${store.currentRunId}`)
  }

  const filterLabel = planFilters.find((filter) => filter.key === activePlanFilter.value)?.label ?? '全部'
  lines.push(`当前筛选：${filterLabel}`)

  if (selectedPlanStepOrder.value != null) {
    lines.push(`定位步骤：${selectedPlanStepOrder.value}`)
  }

  const progress = activePlanProgress.value
  if (progress) {
    lines.push(`执行概览：${progress.headline}`)
    if (progress.focusLine) {
      lines.push(`当前说明：${progress.focusLine}`)
    }
    lines.push(`步骤统计：已完成 ${progress.completedCount}/${progress.totalCount}，进行中 ${progress.runningCount}，异常 ${progress.failedCount}`)
  } else {
    lines.push('执行概览：暂无复杂任务计划')
  }

  const systemSummary = store.activePlan?.systemSummary
  if (systemSummary) {
    lines.push(`系统接力：自动补跑 ${systemSummary.autorunCount} 次，自动重建 ${systemSummary.recoverCount} 次，影响步骤 ${systemSummary.affectedStepCount} 个`)
    if (systemSummary.summary) {
      lines.push(`系统说明：${systemSummary.summary}`)
    }
  }

  const steps = filteredPlanSteps.value
  if (steps.length) {
    lines.push(`关键步骤（展示 ${Math.min(steps.length, 6)} / ${steps.length}）：`)
    steps.slice(0, 6).forEach((step) => {
      const details = [`${step.stepOrder}. ${step.name}`, planStepStatusLabel(step.status)]
      if (step.executionTrace?.triggerLabel) {
        details.push(step.executionTrace.triggerLabel)
      }
      if (step.outputSummary) {
        details.push(step.outputSummary)
      } else if (step.goal) {
        details.push(step.goal)
      }
      if (step.failureDetail?.headline) {
        details.push(step.failureDetail.headline)
      }
      if (step.failureDetail?.errorCode) {
        details.push(`错误码 ${step.failureDetail.errorCode}`)
      }
      if (step.failureDetail?.handleCode) {
        details.push(`建议动作 ${planHandleCodeLabel(step.failureDetail.handleCode)}`)
      }
      lines.push(`- ${details.join('｜')}`)
    })
  }

  return lines.join('\n')
}

async function syncPlanQuery() {
  const nextSid = store.currentSession?.id || undefined
  const nextPf = activePlanFilter.value === 'all' ? undefined : activePlanFilter.value
  const nextPs = selectedPlanStepOrder.value == null ? undefined : String(selectedPlanStepOrder.value)
  const currentSid = parseRouteSessionId(route.query.sid)
  const currentPf = normalizePlanFilter(route.query.pf)
  const currentPs = parsePlanStepOrder(route.query.ps)

  const sidUnchanged = (nextSid ?? null) === currentSid
  const pfUnchanged = (nextPf ?? 'all') === currentPf
  const psUnchanged = (nextPs == null ? null : Number.parseInt(nextPs, 10)) === currentPs
  if (sidUnchanged && pfUnchanged && psUnchanged) {
    return
  }

  const nextQuery = { ...route.query }
  if (nextSid) {
    nextQuery.sid = nextSid
  } else {
    delete nextQuery.sid
  }

  if (nextPf) {
    nextQuery.pf = nextPf
  } else {
    delete nextQuery.pf
  }

  if (nextPs) {
    nextQuery.ps = nextPs
  } else {
    delete nextQuery.ps
  }

  await router.replace({ query: nextQuery })
}

function setPlanFilter(filter: PlanFilterKey) {
  if (activePlanFilter.value === filter) {
    return
  }
  activePlanFilter.value = filter
}

function applyFocusedPlanStep(stepOrder: number, shouldScroll: boolean) {
  focusedPlanStepOrder.value = stepOrder

  if (clearPlanFocusTimer !== null) {
    window.clearTimeout(clearPlanFocusTimer)
  }

  if (shouldScroll) {
    planStepRefs.get(stepOrder)?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
    })
  }

  clearPlanFocusTimer = window.setTimeout(() => {
    if (focusedPlanStepOrder.value === stepOrder) {
      focusedPlanStepOrder.value = null
    }
  }, 2200)
}

async function handleCreateSession() {
  if (authRequired.value) {
    openAuthDrawer()
    antMessage.warning('当前后端需要 JWT 身份，请先完成配置')
    return
  }
  const title = newSessionTitle.value.trim() || '默认会话'
  const created = await store.createNewSession(title)
  if (created) {
    newSessionTitle.value = '默认会话'
    draft.value = ''
    resetComposerState()
  }
}

async function handleSelectSession(sessionId: string) {
  await store.selectSession(sessionId)
  draft.value = ''
  resetComposerState()
}

async function handleSend() {
  if (authRequired.value) {
    openAuthDrawer()
    antMessage.warning('当前后端需要 JWT 身份，请先完成配置')
    return
  }
  if (!store.currentSession || !draft.value.trim()) {
    return
  }
  const content = draft.value.trim()
  draft.value = ''
  await store.send(store.currentSession.id, content, useStreaming.value, manualQuestionType.value)
  if (store.error) {
    draft.value = content
  } else {
    resetComposerState()
  }
}

async function handleInputKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }

  event.preventDefault()
  await handleSend()
}

function openAuthDrawer() {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent('urban-agent:open-auth-drawer'))
}

function handleWorkbenchNav(item: { to: string; label: string }) {
  void router.push(item.to)
}

function handleLogout() {
  authStore.logout()
}

function handleFollowUp(question: string) {
  draft.value = question
  void nextTick(() => {
    messageInputRef.value?.focus?.()
    const el = document.querySelector<HTMLTextAreaElement>('.chat-input-box textarea')
    if (el) {
      el.focus()
      el.setSelectionRange(el.value.length, el.value.length)
    }
  })
}

async function handleSessionAction(sessionId: string, action: string) {
  if (action === 'delete') {
    // 后端 DELETE /api/v1/agent/sessions/:id 支持后接入
    void sessionId
    antMessage.warning('删除会话需后端接口支持')
  } else if (action === 'archive') {
    antMessage.info('归档功能待后端支持')
  }
}

async function copyMessage(content: string) {
  if (!content) {
    return
  }

  try {
    const copied = await writeClipboardText(content)
    if (copied) {
      antMessage.success('已复制内容')
      return
    }
    selectContentForManualCopy(content)
    antMessage.info('已选中内容，请按 Ctrl+C 或 ⌘C 复制')
  } catch {
    selectContentForManualCopy(content)
    antMessage.info('已选中内容，请按 Ctrl+C 或 ⌘C 复制')
  }
}

async function copyPlanShareLink() {
  const shareLink = buildPlanShareLink()
  if (!shareLink) {
    antMessage.warning('当前没有可分享的会话')
    return
  }

  try {
    const copied = await writeClipboardText(shareLink)
    if (copied) {
      antMessage.success('已复制排查链接')
      return
    }
    selectContentForManualCopy(shareLink)
    antMessage.info('已选中排查链接，请按 Ctrl+C 或 ⌘C 复制')
  } catch {
    selectContentForManualCopy(shareLink)
    antMessage.info('已选中排查链接，请按 Ctrl+C 或 ⌘C 复制')
  }
}

async function copyPlanDiagnosticSummary() {
  const summary = buildPlanDiagnosticSummary()
  if (!summary) {
    antMessage.warning('当前没有可复制的诊断摘要')
    return
  }

  try {
    const copied = await writeClipboardText(summary)
    if (copied) {
      antMessage.success('已复制诊断摘要')
      return
    }
    selectContentForManualCopy(summary)
    antMessage.info('已选中诊断摘要，请按 Ctrl+C 或 ⌘C 复制')
  } catch {
    selectContentForManualCopy(summary)
    antMessage.info('已选中诊断摘要，请按 Ctrl+C 或 ⌘C 复制')
  }
}

async function writeClipboardText(content: string) {
  if (copyWithClipboardEvent(content)) {
    return true
  }

  if (copyWithTextArea(content)) {
    return true
  }

  if (navigator.clipboard?.writeText) {
    return withTimeout(navigator.clipboard.writeText(content), 1200)
  }

  return false
}

function copyWithClipboardEvent(content: string) {
  let copied = false
  const handler = (event: ClipboardEvent) => {
    event.clipboardData?.setData('text/plain', content)
    event.preventDefault()
    copied = true
  }

  document.addEventListener('copy', handler)
  try {
    document.execCommand('copy')
    return copied
  } finally {
    document.removeEventListener('copy', handler)
  }
}

function copyWithTextArea(content: string) {
  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '0'
  textarea.style.top = '0'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'
  document.body.appendChild(textarea)
  textarea.focus({ preventScroll: true })
  textarea.select()
  textarea.setSelectionRange(0, content.length)
  try {
    return document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
  }
}

function selectContentForManualCopy(content: string) {
  if (manualCopyTextarea) {
    document.body.removeChild(manualCopyTextarea)
    manualCopyTextarea = null
  }
  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.setAttribute('readonly', 'true')
  textarea.setAttribute('aria-label', '已选中的复制内容')
  textarea.style.position = 'fixed'
  textarea.style.left = '0'
  textarea.style.top = '0'
  textarea.style.width = '1px'
  textarea.style.height = '1px'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'
  document.body.appendChild(textarea)
  manualCopyTextarea = textarea
  textarea.focus({ preventScroll: true })
  textarea.select()
  textarea.setSelectionRange(0, content.length)
  window.setTimeout(() => {
    if (manualCopyTextarea === textarea) {
      document.body.removeChild(textarea)
      manualCopyTextarea = null
    }
  }, 8000)
}

async function withTimeout<T>(task: Promise<T>, timeoutMs: number) {
  let timer: number | undefined
  try {
    await Promise.race([
      task,
      new Promise((_, reject) => {
        timer = window.setTimeout(() => reject(new Error('copy timeout')), timeoutMs)
      }),
    ])
    return true
  } catch {
    return false
  } finally {
    if (timer !== undefined) {
      window.clearTimeout(timer)
    }
  }
}

function markFeedback(messageId: string, value: 'like' | 'dislike') {
  const next = { ...feedbackByMessage.value }
  if (next[messageId] === value) {
    delete next[messageId]
    feedbackByMessage.value = next
    return
  }
  next[messageId] = value
  feedbackByMessage.value = next
  antMessage.success(value === 'like' ? '已标记为有帮助' : '已记录改进意见')
}

function markFeedbackWithReason(messageId: string, reason: string) {
  const next = { ...feedbackByMessage.value }
  next[messageId] = 'dislike'
  feedbackByMessage.value = next
  antMessage.success(`已记录改进意见：${reason}`)
}

function toggleQuestionType(type: QuestionTypeLabel) {
  manualQuestionType.value = manualQuestionType.value === type ? null : type
}

async function editMessage(message: MessageView) {
  if (message.role !== 'USER') {
    return
  }
  draft.value = message.content
  editingMessageId.value = message.id
  manualQuestionType.value = null
  await nextTick()
  messageInputRef.value?.focus?.()
}

function resetComposerState() {
  editingMessageId.value = null
  manualQuestionType.value = null
}

function sessionCategories(session: SessionView) {
  return inferSessionQuestionTypes(session.messages).slice(0, 3)
}

function isQuestionTypeActive(type: QuestionTypeLabel) {
  return highlightedQuestionTypes.value.includes(type)
}

function questionTypeClass(type: QuestionTypeLabel) {
  return {
    'is-active': isQuestionTypeActive(type),
    'is-manual': manualQuestionType.value === type,
    'is-auto': !manualQuestionType.value && autoQuestionTypes.value.includes(type),
  }
}

function clearManualQuestionType() {
  manualQuestionType.value = null
}

function sessionGroupLabel(value: string) {
  const created = new Date(value)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(created.getFullYear(), created.getMonth(), created.getDate())
  const diffDays = Math.floor((today.getTime() - target.getTime()) / 86_400_000)

  if (diffDays <= 0) {
    return '今天'
  }
  if (diffDays < 7) {
    return '7天以内'
  }
  if (diffDays < 30) {
    return '30天以内'
  }
  return `${created.getFullYear()}-${String(created.getMonth() + 1).padStart(2, '0')}`
}

function riskLabel(value?: string | null) {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
  }
  return value ? labels[value] ?? value : '待判定'
}

function reviewLabel(value: string) {
  const labels: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已退回',
    REVISION_REQUIRED: '需修订',
    MORE_FACTS_REQUIRED: '补充事实',
  }
  return labels[value] ?? value
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    ACTIVE: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAILED: '失败',
  }
  return labels[value] ?? value
}

function planStepStatusLabel(value: string) {
  const labels: Record<string, string> = {
    TODO: '待执行',
    PENDING: '待执行',
    RUNNING: '进行中',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    SUCCESS: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return labels[value] ?? value
}

function planHandleCodeLabel(value: string) {
  const labels: Record<string, string> = {
    RETRY_STEP: '直接重试当前步骤',
    SWITCH_ROLE: '切换角色后重试',
    REBUILD_QUERY_PREVIEW: '重建查询准备',
    REBUILD_QUERY_RESULT: '重建数据结果',
    REBUILD_KNOWLEDGE: '重建依据检索',
    REBUILD_DEPENDENCIES: '补跑前置步骤',
  }
  return labels[value] ?? value
}

function shortId(value: string) {
  return value.length > 8 ? value.slice(0, 8) : value
}

function eventLabel(value: string) {
  const labels: Record<string, string> = {
    'data.query.completed': '问数完成',
    'data.query.failed': '问数失败',
    'message.meta': '运行开始',
    'message.delta': '回答片段',
    'plan.updated': '计划更新',
    'risk.pending_review': '待审核',
    'agent.completed': '运行完成',
    'agent.failed': '运行失败',
    'agent.cancelled': '运行取消',
  }
  return labels[value] ?? value
}

function statusColor(value: string) {
  if (value === 'COMPLETED') {
    return 'success'
  }
  if (value === 'FAILED') {
    return 'error'
  }
  if (value === 'CANCELLED') {
    return 'default'
  }
  return 'processing'
}

function riskColor(value: string) {
  if (value === 'HIGH') {
    return 'error'
  }
  if (value === 'MEDIUM') {
    return 'warning'
  }
  return 'success'
}

function stepStatus(value: string): 'wait' | 'process' | 'finish' | 'error' {
  if (['COMPLETED', 'SUCCESS'].includes(value)) {
    return 'finish'
  }
  if (['FAILED', 'CANCELLED'].includes(value)) {
    return 'error'
  }
  if (['RUNNING', 'IN_PROGRESS'].includes(value)) {
    return 'process'
  }
  return 'wait'
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function queryTableColumns(queryCard: QueryConversationView) {
  return friendlyQueryColumns(queryCard)
}

function queryPagination(rowCount: number) {
  if (rowCount <= 6) {
    return false
  }

  return {
    pageSize: 6,
    showSizeChanger: false,
  }
}

function queryRowKey(_: Record<string, unknown>, index: number) {
  return String(index)
}

function messageQueryCards(message: MessageView) {
  if (message.queryCards?.length) {
    return message.queryCards
  }
  if (message.composedAnswer?.queryCards?.length) {
    return message.composedAnswer.queryCards
  }
  return message.queryCard ? [message.queryCard] : []
}

function messageVisibleQueryCards(message: MessageView | null) {
  if (!message) return []
  if (!isOilFumeCompositeMessage(message)) {
    return messageQueryCards(message)
  }

  return messageQueryCards(message).filter((queryCard) => !isOilFumeUnclosedScalarCard(queryCard))
}

function extractDataSource(card: QueryConversationView): string {
  return card.metricName ?? card.scopeSummary ?? '政务数据'
}

function extractCaliber(card: QueryConversationView): string {
  return card.scopeSummary ?? '默认口径'
}

function extractPermissionStatus(card: QueryConversationView): string {
  if (card.permissionRewrite) return '授权（已脱敏）'
  return '已授权'
}

function messageDisplayContent(message: MessageView) {
  return message.content?.trim?.() ?? ''
}

function messageDisplaySummary(message: MessageView) {
  const { summary } = splitAssistantMessage(messageDisplayContent(message))
  return sanitizeSummaryText(summary, messageQueryCards(message)[0])
}

function messageDisplayBody(message: MessageView) {
  if (isOilFumeCompositeMessage(message)) {
    return oilFumeInsightText(message)
  }

  const summary = messageDisplaySummary(message)
  if (summary) {
    return summary
  }

  const queryCard = messageQueryCards(message)[0]
  if (!queryCard || store.streaming) {
    return ''
  }
  if (!queryCard.rows.length) {
    return '当前没有查询到符合条件的数据，可调整范围后再试。'
  }
  if (supportsQueryChart(queryCard)) {
    return '已生成统计图表，可查看下方结果。'
  }
  if (isScalarQuery(queryCard)) {
    return '已生成统计结果，可查看下方指标。'
  }
  return '已生成查询结果，可查看下方明细。'
}

function messageDisplaySuggestion(message: MessageView) {
  const { suggestion } = splitAssistantMessage(messageDisplayContent(message))
  return suggestion
}

function splitAssistantMessage(content: string) {
  const normalized = content.trim()
  if (!normalized) {
    return {
      summary: '',
      suggestion: '',
    }
  }

  const marker = '处置建议：'
  const markerIndex = normalized.indexOf(marker)
  if (markerIndex === -1) {
    return {
      summary: normalized,
      suggestion: '',
    }
  }

  return {
    summary: normalized.slice(0, markerIndex).trim(),
    suggestion: normalized.slice(markerIndex + marker.length).trim(),
  }
}

function messageDisplayHeadline(message: MessageView) {
  if (isOilFumeCompositeMessage(message)) {
    return '柯桥区油烟超标阈值与预警概览'
  }

  const queryCard = messageQueryCards(message)[0]
  if (!queryCard || !supportsQueryChart(queryCard)) {
    return ''
  }

  const mode = inferQueryChartMode(queryCard)
  const metricName = queryCard.metricName || '统计结果'
  const question = queryCard.question ?? ''
  if (/排行|排名/u.test(question)) {
    return metricName.includes('投诉') ? '本周投诉排行' : `${metricName}排行`
  }
  if (mode === 'line') {
    return `${metricName}趋势`
  }
  if (mode === 'pie') {
    return `${metricName}分布`
  }
  return `${metricName}排行`
}

function oilFumeOverviewItems(message: MessageView) {
  if (!isOilFumeCompositeMessage(message)) {
    return []
  }

  const content = messageDisplayContent(message)
  const threshold = matchFirst(content, /最高允许排放浓度为\s*([0-9.]+\s*mg\/m³)/u) ?? '2.00 mg/m³'
  const change = matchFirst(content, /整体(上升|下降)\s*([0-9.]+\s*mg\/m³)/u)
  const steady = /整体基本持平/u.test(content)
  const standardChange = oilFumeStandardChangeText(content)
  const unclosedCard = messageQueryCards(message).find(isOilFumeUnclosedScalarCard)
  const unclosed = unclosedCard
    ? formatPrimaryQueryValue(unclosedCard)
    : matchFirst(content, /未闭环油烟(?:浓度)?超标预警为\s*([0-9]+\s*起)/u)

  return [
    {
      label: '超标阈值',
      value: threshold,
      detail: 'GB 18483-2001',
    },
    {
      label: change || steady ? '较历史均值' : '较上一版',
      value: change ?? (steady ? '基本持平' : standardChange),
      detail: change || steady ? '近7天 vs 历史留存' : '标准口径',
    },
    {
      label: '未闭环预警',
      value: unclosed ?? '-',
      detail: '当前待处置',
    },
  ]
}

function oilFumeInsightText(message: MessageView) {
  const content = messageDisplayContent(message)
  const threshold = matchFirst(content, /最高允许排放浓度为\s*([0-9.]+\s*mg\/m³)/u) ?? '2.00 mg/m³'
  const change = matchFirst(content, /整体(上升|下降)\s*([0-9.]+\s*mg\/m³)/u)
  const steady = /整体基本持平/u.test(content)
  const standardChange = oilFumeStandardChangeText(content)
  const unclosedDate = matchFirst(content, /截至(\d{4}-\d{2}-\d{2})/u)
  const unclosed = matchFirst(content, /未闭环油烟(?:浓度)?超标预警为\s*([0-9]+\s*起)/u)
  const peak = matchFirst(content, /其中\s*(\d{4}-\d{2}-\d{2})\s*的平均浓度最高，为\s*([0-9.]+\s*mg\/m³)/u)
  const changeText = change
    ? `近7天均值较历史留存${change}`
    : steady
      ? '近7天均值较历史留存基本持平'
      : `较上一版标准${standardChange}`
  const unclosedText = unclosed
    ? `截至${unclosedDate ?? '最新快照'}，仍有 ${unclosed}未闭环`
    : '当前未取到未闭环预警数据'
  const peakText = peak ? `，${peak.split(' ')[0]}为阶段高点` : ''

  return `当前油烟超标阈值为 ${threshold}，${changeText}${peakText}；${unclosedText}。`
}

function oilFumeStandardChangeText(content: string) {
  if (/阈值口径未发生变化/u.test(content)) {
    return '未变化'
  }
  if (/阈值口径已调整/u.test(content)) {
    return '已调整'
  }
  return '待确认'
}

function isOilFumeCompositeMessage(message: MessageView) {
  const cards = messageQueryCards(message)
  const content = messageDisplayContent(message)
  const hasThreshold = /当前阈值口径|最高允许排放浓度|阈值口径/u.test(content)
  const hasConcentrationTrend = cards.some((card) => {
    const metricName = card.metricName ?? ''
    return metricName.includes('油烟') && metricName.includes('浓度') && supportsQueryChart(card)
  })
  const hasUnclosedWarning = cards.some((card) => {
    const metricName = card.metricName ?? ''
    return metricName.includes('未闭环') && metricName.includes('油烟')
  })

  return hasThreshold
    && (hasConcentrationTrend || hasUnclosedWarning)
    && /油烟|阈值|阀值|超标/u.test(content)
}

function isOilFumeUnclosedScalarCard(queryCard: QueryConversationView) {
  const metricName = queryCard.metricName ?? ''
  return metricName.includes('未闭环') && metricName.includes('油烟') && isScalarQuery(queryCard)
}

function matchFirst(value: string, pattern: RegExp) {
  const match = pattern.exec(value)
  if (!match) {
    return null
  }
  if (match.length > 2) {
    return `${match[1]} ${match[2]}`
  }
  return match[1] ?? null
}

function sanitizeSummaryText(value: string, queryCard?: QueryConversationView) {
  const cleaned = value
    .replace(/^当前(?:[\u4e00-\u9fa5A-Za-z0-9_]+)?排行中[，,][^\n。.]*(?:[。.]|$)\s*/u, '')
    .replace(/^当前统计中，\s*/u, '')
    .trim()
  if (queryCard && isAutoQuerySummary(cleaned)) {
    return ''
  }
  return cleaned
}

function isAutoQuerySummary(value: string) {
  return /已按您的问题整理为可直接查看的结果[。.]?$/u.test(value)
}

function shouldShowQueryTable(queryCard: QueryConversationView) {
  return queryCard.rows.length > 0
    && !isScalarQuery(queryCard)
    && !supportsQueryChart(queryCard)
    && !prefersChartOnly(queryCard)
}

function supportsQueryChart(queryCard: QueryConversationView) {
  return inferQueryChartMode(queryCard) !== null
}

function formatPrimaryQueryValue(queryCard: QueryConversationView) {
  return formatFriendlyCell(getPrimaryMetricValue(queryCard), 'metric_value', queryCard)
}

function formatCell(value: unknown, fieldName: string, queryCard: QueryConversationView | null) {
  return formatFriendlyCell(value, fieldName, queryCard ?? undefined)
}

function formatPlanActionTime(value?: string | null) {
  if (!value) {
    return '暂无系统动作'
  }
  return `最近动作 ${formatDateTime(value)}`
}

function planFilterCount(filterKey: (typeof planFilters)[number]['key']) {
  const steps = store.activePlan?.steps ?? []
  switch (filterKey) {
    case 'failed':
      return steps.filter((step) => step.status === 'FAILED').length
    case 'system':
      return steps.filter((step) => (step.executionTrace?.systemActionCount ?? step.systemActions?.length ?? 0) > 0).length
    case 'result':
      return steps.filter((step) => Boolean(step.executionTrace?.resultRef || step.resultRef)).length
    default:
      return steps.length
  }
}

function formatPlanSystemAction(
  action: NonNullable<PlanStepView['systemActions']>[number],
) {
  const actionLabel = action.action === 'RECOVER' ? '重建' : '补跑'
  return `${actionLabel}前置步骤 ${action.dependencyStepOrder}. ${action.dependencyStepName} · ${formatTime(action.createdAt)}`
}

function planArtifactTitle(step: PlanStepView) {
  switch (step.outputPayload?.kind) {
    case 'query-preview':
      return '查询准备'
    case 'query-execute':
      return '查询结果'
    case 'knowledge-hits':
      return '依据命中'
    case 'answer-compose':
      return '答案汇总'
    default:
      return '步骤产物'
  }
}

function planArtifactFacts(step: PlanStepView) {
  const payload = step.outputPayload
  if (!payload) {
    return []
  }

  switch (payload.kind) {
    case 'query-preview':
      return compactPlanFacts([
        { label: '指标', value: payload.metricName || payload.metricCode || '待识别' },
        { label: '权限', value: payload.permissionRewrite || '未改写' },
        { label: 'SQL', value: payload.validatedSql ? `${payload.validatedSql.length} 字符` : '' },
      ])
    case 'query-execute':
      return compactPlanFacts([
        { label: '返回行数', value: payload.rowCount != null ? String(payload.rowCount) : '0' },
        { label: '执行 SQL', value: payload.executedSql ? `${payload.executedSql.length} 字符` : '' },
      ])
    case 'knowledge-hits':
      return compactPlanFacts([
        { label: '命中文档', value: payload.documentIds?.length ? String(payload.documentIds.length) : '0' },
      ])
    case 'answer-compose':
      return compactPlanFacts([
        { label: '汇总卡片', value: payload.rowCount != null ? String(payload.rowCount) : '0' },
        { label: '引用依据', value: payload.documentIds?.length ? String(payload.documentIds.length) : '0' },
      ])
    default:
      return []
  }
}

function compactPlanFacts(facts: Array<{ label: string; value: string }>) {
  return facts.filter((fact) => fact.value)
}

function setAssistantMessageRef(messageId: string, element: Element | null) {
  if (element instanceof HTMLElement) {
    assistantMessageRefs.set(messageId, element)
    return
  }
  assistantMessageRefs.delete(messageId)
}

function planTraceItems(step: PlanStepView) {
  if (step.executionTrace) {
    const traces: Array<{ label: string; value: string; tone?: 'system' | 'user' | 'neutral' }> = [
      {
        label: '触发方式',
        value: step.executionTrace.triggerLabel,
        tone: step.executionTrace.triggerMode.startsWith('SYSTEM_') ? 'system' : 'user',
      },
    ]
    if (step.executionTrace.systemActionCount > 0) {
      traces.push({
        label: '接力次数',
        value: String(step.executionTrace.systemActionCount),
        tone: 'neutral',
      })
    }
    if (step.executionTrace.resultRef) {
      traces.push({
        label: step.executionTrace.resultLabel || '结果引用',
        value: shortenPlanRef(step.executionTrace.resultRef),
        tone: 'neutral',
      })
    }
    if (step.executionTrace.lastActionAt) {
      traces.push({
        label: '最近接力',
        value: formatDateTime(step.executionTrace.lastActionAt),
        tone: 'neutral',
      })
    } else if (step.updatedAt) {
      traces.push({
        label: '最近更新',
        value: formatDateTime(step.updatedAt),
        tone: 'neutral',
      })
    }
    return traces
  }

  const traces: Array<{ label: string; value: string; tone?: 'system' | 'user' | 'neutral' }> = []
  if (step.systemActions?.length) {
    const action = step.systemActions.some((item) => item.action === 'RECOVER') ? '系统重建' : '系统补跑'
    traces.push({
      label: '触发方式',
      value: action,
      tone: 'system',
    })
    traces.push({
      label: '接力次数',
      value: String(step.systemActions.length),
      tone: 'neutral',
    })
  } else if (['COMPLETED', 'SUCCESS', 'RUNNING', 'IN_PROGRESS', 'FAILED'].includes(step.status)) {
    traces.push({
      label: '触发方式',
      value: '用户执行',
      tone: 'user',
    })
  }

  const resultRef = step.resultRef || step.outputPayload?.queryId
  if (resultRef) {
    traces.push({
      label: '结果引用',
      value: shortenPlanRef(resultRef),
      tone: 'neutral',
    })
  }

  if (step.updatedAt) {
    traces.push({
      label: '最近更新',
      value: formatDateTime(step.updatedAt),
      tone: 'neutral',
    })
  }

  return traces
}

function shortenPlanRef(value: string) {
  if (value.length <= 18) {
    return value
  }
  return `${value.slice(0, 8)}...${value.slice(-6)}`
}

function planResultEntry(step: PlanStepView) {
  const resultRef = step.executionTrace?.resultRef || step.resultRef
  if (!resultRef) {
    return null
  }

  if (resultRef.startsWith('knowledge:') || resultRef.startsWith('citations:')) {
    return { label: '引用来源', target: 'citations' as const }
  }
  if (resultRef === 'answer' || resultRef === 'answer-blocked') {
    return { label: '回答结果', target: 'answer' as const }
  }
  return { label: step.executionTrace?.resultLabel || '步骤结果', target: 'step' as const }
}

async function openPlanResult(step: PlanStepView) {
  const entry = planResultEntry(step)
  if (!entry) {
    return
  }

  if (entry.target === 'citations') {
    detailsOpen.value = true
    await nextTick()
    citationsSectionRef.value?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
    })
    return
  }

  if (entry.target === 'answer') {
    const messageId = latestAssistantMessageId.value
    if (messageId) {
      assistantMessageRefs.get(messageId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      })
      return
    }
  }

  focusPlanStep(step.stepOrder)
}

function setPlanStepRef(stepOrder: number, element: Element | null) {
  if (element instanceof HTMLElement) {
    planStepRefs.set(stepOrder, element)
    return
  }
  planStepRefs.delete(stepOrder)
}

async function focusPlanStep(stepOrder: number) {
  selectedPlanStepOrder.value = stepOrder

  const isVisibleInCurrentFilter = filteredPlanSteps.value.some((step) => step.stepOrder === stepOrder)
  if (!isVisibleInCurrentFilter) {
    activePlanFilter.value = 'all'
    await nextTick()
  }

  applyFocusedPlanStep(stepOrder, true)
}

function planRetryDependencySteps(step: PlanStepView) {
  const dependencyOrders = step.failureDetail?.dependencyStepOrders ?? step.retryAdvice?.dependencyStepOrders ?? []
  if (!dependencyOrders.length || !store.activePlan?.steps.length) {
    return []
  }

  return dependencyOrders.map((order) => {
    const matched = store.activePlan?.steps.find((candidate) => candidate.stepOrder === order)
    return matched ?? {
      id: `virtual-${order}`,
      stepOrder: order,
      name: '前置步骤',
    }
  })
}

function planDependencySteps(step: PlanStepView) {
  const dependencyIds = step.dependencyStepIds
    ?.split(',')
    .map((value) => value.trim())
    .filter(Boolean) ?? []
  if (!dependencyIds.length || !store.activePlan?.steps.length) {
    return []
  }

  return dependencyIds
    .map((dependencyId) => store.activePlan?.steps.find((candidate) => candidate.id === dependencyId))
    .filter((candidate): candidate is PlanStepView => Boolean(candidate))
    .sort((left, right) => left.stepOrder - right.stepOrder)
}

function summarizePlanProgress(plan?: PlanView | null) {
  if (!plan?.steps.length) {
    return null
  }

  const totalCount = plan.steps.length
  const completedCount = plan.steps.filter((step) => ['COMPLETED', 'SUCCESS'].includes(step.status)).length
  const runningSteps = plan.steps.filter((step) => ['RUNNING', 'IN_PROGRESS'].includes(step.status))
  const failedSteps = plan.steps.filter((step) => ['FAILED', 'CANCELLED'].includes(step.status))
  const waitingCount = Math.max(totalCount - completedCount - runningSteps.length - failedSteps.length, 0)
  const percent = Math.min(100, Math.round((completedCount / totalCount) * 100))

  let headline = `已完成 ${completedCount}/${totalCount}`
  let focusLine = ''
  let progressStatus: 'normal' | 'exception' | 'success' | 'active' = 'normal'

  if (failedSteps.length) {
    const current = failedSteps[0]
    headline = `卡在 ${current.stepOrder}. ${current.name}`
    focusLine = current.retryAdvice?.reason || '当前步骤执行异常，请按提示处理后重试。'
    progressStatus = 'exception'
  } else if (runningSteps.length) {
    const current = runningSteps[0]
    headline = `正在处理 ${current.stepOrder}. ${current.name}`
    focusLine = current.outputSummary || current.goal
    progressStatus = 'active'
  } else if (completedCount === totalCount) {
    headline = '计划已完成'
    focusLine = plan.systemSummary?.summary || '所有步骤已经完成。'
    progressStatus = 'success'
  } else if (waitingCount > 0) {
    const nextStep = plan.steps.find((step) => step.status === 'TODO') ?? plan.steps.find((step) => step.status === 'PENDING')
    if (nextStep) {
      headline = `待执行 ${nextStep.stepOrder}. ${nextStep.name}`
      focusLine = nextStep.goal
    }
  }

  return {
    completedCount,
    failedCount: failedSteps.length,
    focusLine,
    headline,
    percent,
    progressStatus,
    runningCount: runningSteps.length,
    totalCount,
  }
}

</script>

<style scoped>
/* ===== 场景整体布局 ===== */
.chat-scene {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: calc(100vh - var(--header-height));
  background: var(--bg-base);
}

/* ===== 侧边会话栏 ===== */
.chat-history-rail {
  display: flex;
  flex-direction: column;
  border-inline-end: 1px solid var(--border-color);
  background: var(--bg-surface);
  overflow: hidden;
}

.chat-rail-top {
  padding: var(--space-4);
  border-bottom: 1px solid var(--border-color-light);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.chat-brand-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.chat-brand-mark {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: var(--text-inverse);
  display: grid;
  place-items: center;
  font-size: var(--text-base);
  font-weight: var(--font-bold);
  flex: 0 0 auto;
  box-shadow: var(--shadow-primary);
}

.chat-brand-name {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-rail-icon.ant-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.chat-rail-icon.ant-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.chat-new-button.ant-btn {
  width: 100%;
  height: 36px;
  border-radius: var(--radius-lg) !important;
  border: 1px dashed var(--border-color) !important;
  background: var(--bg-surface) !important;
  color: var(--text-secondary) !important;
  font-size: var(--text-sm) !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-new-button.ant-btn:hover {
  border-color: var(--color-primary-border) !important;
  background: var(--color-primary-bg) !important;
  color: var(--color-primary) !important;
}

.chat-history-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.chat-history-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-history-label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-weight: var(--font-medium);
  padding: var(--space-2) var(--space-2) var(--space-1);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.chat-history-item {
  width: 100%;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-default);
}

.chat-history-item:hover > .chat-history-item__btn {
  background: var(--bg-hover);
}

.chat-history-item.is-active > .chat-history-item__btn {
  background: var(--color-primary-bg);
}

.chat-history-item__btn {
  width: 100%;
  text-align: left;
  padding: var(--space-3);
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: var(--radius-md);
}

.chat-history-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-history-title {
  font-size: var(--text-sm);
  color: var(--text-primary);
  line-height: var(--leading-snug);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.chat-history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.chat-history-tag {
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
  font-size: var(--text-xs);
}

/* ===== 主舞台 ===== */
.chat-main-stage {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: calc(100vh - var(--header-height));
  position: relative;
}

/* ===== JWT 提示 ===== */
.chat-auth-alert {
  margin: var(--space-4) var(--space-6);
  border-radius: var(--radius-lg) !important;
  border: 1px solid var(--color-warning-border) !important;
}

.chat-auth-alert__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  font-size: var(--text-sm);
}

/* ===== 消息阅读区 ===== */
.chat-reading-column {
  flex: 1;
  width: 100%;
  max-width: var(--chat-max-width);
  margin: 0 auto;
  padding: var(--space-6) var(--space-6) 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* ===== 用户消息 ===== */
.chat-user-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.chat-user-question {
  max-width: 75%;
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-xl);
  border-bottom-right-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--text-inverse);
  font-size: var(--text-base);
  line-height: var(--leading-relaxed);
  box-shadow: var(--shadow-md);
}

.chat-user-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.chat-user-avatar.ant-avatar {
  background: var(--color-primary) !important;
  opacity: 0.85;
}

.chat-inline-icon.ant-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  border-radius: var(--radius-md);
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-inline-icon.ant-btn:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.chat-inline-icon.ant-btn.is-active {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

/* ===== 点踩下拉菜单 ===== */
.chat-dislike-dropdown .ant-dropdown-menu {
  min-width: 160px;
}

/* ===== 追问建议按钮组 ===== */
.chat-suggestion-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-top: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-color-light);
}

.chat-suggestion-actions__label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.chat-followup-btn {
  font-size: var(--text-xs) !important;
  height: 26px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-full);
  border-color: var(--border-color);
  color: var(--text-secondary);
}

.chat-followup-btn:hover {
  border-color: var(--color-primary-border);
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

/* ===== AI 助手消息 ===== */
.chat-assistant-block {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  margin-top: var(--space-5);
}

.chat-assistant-header {
  flex: 0 0 auto;
  padding-top: 2px;
}

.chat-assistant-brand {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-lg);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  font-size: var(--text-base);
  font-weight: var(--font-bold);
  border: 1px solid var(--color-primary-border);
}

.chat-assistant-body {
  min-width: 0;
  flex: 1;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  border-top-left-radius: var(--radius-sm);
  padding: var(--space-4) var(--space-5);
  box-shadow: var(--shadow-sm);
}

.chat-message-headline {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
  line-height: var(--leading-snug);
  margin-bottom: var(--space-2);
}

.chat-message-content {
  font-size: var(--text-base);
  line-height: var(--leading-loose);
  color: var(--text-primary);
  white-space: pre-wrap;
}

.chat-assistant-flags {
  margin-top: var(--space-3);
}

/* ===== 风险标签 ===== */
.chat-suggestion-card {
  margin-top: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-lg);
  background: var(--color-primary-bg);
}

.chat-suggestion-card__label {
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  color: var(--color-primary-text);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: var(--space-2);
}

.chat-suggestion-card__content {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-secondary);
}

/* ===== 指标概览 ===== */
.chat-overview-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.chat-overview-metric {
  padding: var(--space-4);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--bg-inset);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-overview-metric span {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-overview-metric strong {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--text-primary);
  line-height: var(--leading-tight);
}

.chat-overview-metric em {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 查询报告卡片 ===== */
.chat-query-report {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-color-light);
}

.chat-query-section-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--text-secondary);
}

.chat-query-kpi {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-3);
  padding-left: var(--space-3);
  border-inline-start: 3px solid var(--color-primary);
}

.chat-query-kpi-label {
  font-size: var(--text-md);
  color: var(--text-secondary);
}

.chat-query-kpi strong {
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
  color: var(--color-primary);
  line-height: 1;
}

.chat-query-table {
  width: 100%;
  overflow-x: auto;
}

.chat-query-table .ant-table {
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.chat-query-table .ant-table-thead > tr > th {
  background: var(--bg-inset) !important;
  color: var(--text-secondary);
  font-weight: var(--font-semibold);
  font-size: var(--text-xs);
  border-color: var(--border-color-light) !important;
}

.chat-query-table .ant-table-tbody > tr > td {
  border-color: var(--border-color-light) !important;
  line-height: var(--leading-relaxed);
}

.chat-query-table .ant-pagination {
  margin-top: var(--space-3);
}

.chat-query-tips {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-query-tip::before {
  color: var(--color-primary);
  content: "提示：";
  font-weight: var(--font-medium);
}

/* ===== 引用链接 ===== */
.chat-citation-link {
  padding: 2px 4px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: var(--text-xs);
  cursor: pointer;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-citation-link:hover {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

/* ===== 底部停靠区 ===== */
.chat-bottom-dock {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: var(--z-raised);
  padding: 0 var(--space-6) var(--space-5);
  background: linear-gradient(180deg, transparent 0%, var(--bg-base) 20%);
}

/* ===== 分类标签栏 ===== */
.chat-suggestion-bar {
  max-width: var(--chat-max-width);
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding: var(--space-3) var(--space-4);
  box-shadow: var(--shadow-md);
}

.chat-suggestion-content {
  flex: 1;
  min-width: 0;
}

.chat-suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chat-suggestion-chip {
  height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);
  white-space: nowrap;
}

.chat-suggestion-chip:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.chat-suggestion-chip.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--text-inverse);
}

.chat-suggestion-chip.is-manual {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
}

.chat-suggestion-chip.is-auto:not(.is-manual)::after {
  content: "";
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary);
  margin-left: var(--space-2);
}

.chat-grid-button.ant-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-grid-button.ant-btn:hover {
  background: var(--bg-hover);
  color: var(--color-primary);
}

/* ===== 输入区 ===== */
.chat-input-shell {
  max-width: var(--chat-max-width);
  margin: 0 auto;
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-top: none;
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.chat-input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4) 0;
  gap: var(--space-3);
}

.chat-input-hint {
  font-size: var(--text-xs);
  color: var(--color-warning-text);
}

.chat-toolbar-link.ant-btn {
  padding: 0;
  height: auto;
  font-size: var(--text-xs);
  color: var(--color-primary);
}

.chat-input-box.ant-input-affix-wrapper,
.chat-input-box.ant-input {
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: var(--space-3) var(--space-4) !important;
}

.chat-input-box textarea.ant-input {
  font-size: var(--text-base) !important;
  line-height: var(--leading-relaxed) !important;
  color: var(--text-primary) !important;
  resize: none !important;
}

.chat-send-button.ant-btn {
  position: absolute;
  right: var(--space-3);
  bottom: var(--space-3);
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-primary);
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-send-button.ant-btn:not(:disabled):hover {
  background: var(--color-primary-hover) !important;
  transform: scale(1.05);
}

.chat-send-button.ant-btn:disabled {
  background: var(--bg-hover) !important;
  border-color: var(--border-color) !important;
  color: var(--text-tertiary) !important;
  box-shadow: none !important;
}

/* ===== 用户菜单项 ===== */
.chat-user-menu-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-1) 0;
}

.chat-user-menu-item__name {
  font-weight: var(--font-semibold);
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.chat-user-menu-item__role {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-brand-right {
  margin-left: auto;
}

/* ===== 引用弹层面板 ===== */
.chat-citation-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 400px;
  max-height: 360px;
  overflow-y: auto;
  padding: var(--space-1);
}

/* ===== 计划详情抽屉 ===== */
.chat-detail-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.chat-detail-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.chat-detail-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.chat-detail-title {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.chat-plan-title-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  flex-wrap: wrap;
}

.chat-plan-share-button {
  flex-shrink: 0;
  padding-inline: var(--space-2);
  color: var(--color-primary) !important;
  font-size: var(--text-xs);
}

.chat-plan-share-button:hover {
  color: var(--color-primary-hover) !important;
  background: var(--color-primary-bg) !important;
}

.chat-plan-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.chat-plan-filters__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-plan-filters__item strong {
  font-weight: var(--font-semibold);
}

.chat-plan-filters__item.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
}

.chat-plan-progress {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--bg-inset);
}

.chat-plan-progress__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.chat-plan-progress__header strong {
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.chat-plan-progress__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.chat-plan-progress__stat {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  border: 1px solid var(--border-color-light);
  text-align: center;
}

.chat-plan-progress__value {
  display: block;
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  color: var(--text-primary);
}

.chat-plan-progress__label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-top: 2px;
}

.chat-plan-progress__focus {
  margin-top: var(--space-3);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: var(--leading-relaxed);
}

.chat-plan-summary {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--bg-inset);
}

.chat-plan-summary__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.chat-plan-summary__header strong {
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.chat-plan-summary__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.chat-plan-summary__stat {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  border: 1px solid var(--border-color-light);
  text-align: center;
}

.chat-plan-summary__value {
  display: block;
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  color: var(--color-primary);
}

.chat-plan-summary__label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-top: 2px;
}

.chat-plan-summary__text {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-secondary);
}

.chat-plan-step-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-default);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.chat-plan-step-detail.is-focused {
  background: var(--color-primary-bg);
  box-shadow: inset 0 0 0 2px var(--color-primary-border);
  padding: var(--space-2) var(--space-3);
}

.chat-plan-step-trace {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.chat-plan-step-trace__label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-weight: var(--font-medium);
}

.chat-plan-step-trace__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chat-plan-step-trace__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  background: var(--bg-inset);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  border: 1px solid var(--border-color-light);
}

.chat-plan-step-trace__item strong {
  font-weight: var(--font-semibold);
}

.chat-plan-step-trace__item em {
  font-style: normal;
}

.chat-plan-step-trace__item.is-system {
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
  border-color: var(--color-primary-border);
}

.chat-plan-step-trace__item.is-user {
  background: var(--color-success-bg);
  color: var(--color-success-text);
  border-color: var(--color-success-border);
}

.chat-plan-step-trace__action {
  align-self: flex-start;
  padding: 2px 0;
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-xs);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.chat-plan-step-dependencies {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.chat-plan-step-dependencies__label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-plan-step-dependencies__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chat-plan-step-dependencies__item {
  padding: 2px var(--space-3);
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color-light);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);
}

.chat-plan-step-dependencies__item:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
}

.chat-plan-step-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.chat-plan-step-action {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  flex-wrap: wrap;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.chat-plan-step-hint {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-warning-border);
  background: var(--color-warning-bg);
  font-size: var(--text-sm);
  color: var(--color-warning-text);
}

.chat-plan-step-hint__header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.chat-plan-step-hint__code {
  font-size: var(--text-xs);
}

.chat-plan-step-hint__deps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.chat-plan-step-hint__link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-warning-text);
  font-size: var(--text-xs);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.chat-plan-artifact {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
}

.chat-plan-artifact__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.chat-plan-artifact__id {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-plan-artifact__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.chat-plan-artifact__fact {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--bg-inset);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-plan-artifact__fact span {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.chat-plan-artifact__fact strong {
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: var(--font-semibold);
}

.chat-plan-artifact__summary {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-secondary);
}

.chat-plan-artifact__warnings {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* ===== 抽屉通用卡片 ===== */
.chat-detail-card {
  padding: var(--space-3);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  background: var(--bg-inset);
  font-size: var(--text-sm);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-detail-card p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.chat-detail-meta {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .chat-scene {
    grid-template-columns: 1fr;
  }

  .chat-history-rail {
    display: none;
  }

  .chat-reading-column {
    padding: var(--space-4) var(--space-3) 200px;
  }

  .chat-user-question {
    max-width: 88%;
  }

  .chat-bottom-dock {
    padding: 0 var(--space-3) var(--space-4);
  }
}
</style>
