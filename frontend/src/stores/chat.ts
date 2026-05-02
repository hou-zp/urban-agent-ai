import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { cancelSession, createSession, getSession, listSessions, resumeSession, sendMessage, streamMessage } from '@/api/agent'
import { answerDataQuery } from '@/api/query'
import { buildQueryAnswerText } from '@/utils/queryPresentation'
import type {
  MessageView,
  PlanView,
  QueryAnswerView,
  QueryConversationView,
  SessionView,
  SseEvent,
  StreamEventLog,
} from '@/types/api'
import { normalizeQuestionTypes, type QuestionTypeLabel } from '@/utils/chat'

const CLIENT_MESSAGE_CACHE_KEY = 'urban-agent-chat-client-messages'
const PLAN_INSPECTOR_CACHE_KEY = 'urban-agent-plan-inspector-state'

type DataQueryMode = 'strict' | 'try' | 'none'
type DataQueryOutcome = 'done' | 'failed' | 'fallback'
type PlanInspectorFilter = 'all' | 'failed' | 'system' | 'result'

interface PlanInspectorState {
  filter: PlanInspectorFilter
  selectedStepOrder: number | null
}

const DEFAULT_PLAN_INSPECTOR_STATE: PlanInspectorState = {
  filter: 'all',
  selectedStepOrder: null,
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionView[]>([])
  const currentSession = ref<SessionView | null>(null)
  const localMessages = ref<Record<string, MessageView[]>>(loadClientMessageCache())
  const planInspectorStateBySession = ref<Record<string, PlanInspectorState>>(loadPlanInspectorCache())
  const loading = ref(false)
  const creatingSession = ref(false)
  const sending = ref(false)
  const streaming = ref(false)
  const cancelling = ref(false)
  const error = ref('')
  const currentRunId = ref('')
  const activePlan = ref<PlanView | null>(null)
  const riskNotice = ref<{ reviewId?: string; riskLevel?: string } | null>(null)
  const streamEvents = ref<StreamEventLog[]>([])

  const messages = computed(() => currentSession.value?.messages ?? [])
  const latestAssistantMessage = computed(() => {
    return [...messages.value].reverse().find((message) => message.role === 'ASSISTANT') ?? null
  })
  const currentPlanInspectorState = computed<PlanInspectorState>(() => {
    const sessionId = currentSession.value?.id
    if (!sessionId) {
      return DEFAULT_PLAN_INSPECTOR_STATE
    }
    return normalizePlanInspectorState(planInspectorStateBySession.value[sessionId])
  })

  async function bootstrap(preferredSessionId?: string | null) {
    loading.value = true
    error.value = ''
    try {
      sessions.value = (await listSessions()).map(mergeSession)
      if (!sessions.value.length) {
        const session = await createSession('默认会话')
        sessions.value = [mergeSession(session)]
      }
      const targetSessionId = preferredSessionId && sessions.value.some((session) => session.id === preferredSessionId)
        ? preferredSessionId
        : sessions.value[0].id
      await selectSession(targetSessionId)
    } catch (err) {
      error.value = toMessage(err)
    } finally {
      loading.value = false
    }
  }

  async function createNewSession(title: string) {
    if (creatingSession.value) {
      return false
    }

    creatingSession.value = true
    error.value = ''
    try {
      const session = mergeSession(await createSession(title))
      sessions.value = [session, ...sessions.value]
      await selectSession(session.id)
      return true
    } catch (err) {
      error.value = toMessage(err)
      return false
    } finally {
      creatingSession.value = false
    }
  }

  async function selectSession(sessionId: string) {
    error.value = ''
    try {
      const session = mergeSession(await getSession(sessionId))
      currentSession.value = session
      syncSessionSummary(session)
      activePlan.value = null
      riskNotice.value = null
      streamEvents.value = []
      currentRunId.value = [...session.messages].reverse().find((message) => Boolean(message.runId))?.runId ?? ''
      return true
    } catch (err) {
      error.value = toMessage(err)
      return false
    }
  }

  async function send(sessionId: string, content: string, useStreaming: boolean, preferredType?: QuestionTypeLabel | null) {
    error.value = ''
    if (!currentSession.value || sending.value) {
      return
    }

    sending.value = true
    const questionTypes = normalizeQuestionTypes(content, preferredType)

    const dataQueryMode = resolveDataQueryMode(content, preferredType)
    if (dataQueryMode !== 'none') {
      const outcome = await runDataQueryInConversation(sessionId, content, dataQueryMode, questionTypes)
      if (outcome !== 'fallback') {
        sending.value = false
        return
      }
    }

    const optimisticUserMessage = createOptimisticMessage('USER', content, { questionTypes })
    currentSession.value.messages = [...currentSession.value.messages, optimisticUserMessage]
    syncCurrentSession()

    if (!useStreaming) {
      try {
        const assistant = normalizeMessage(await sendMessage(sessionId, content))
        currentSession.value.messages = [...currentSession.value.messages, assistant]
        syncCurrentSession()
        currentRunId.value = assistant.runId ?? ''
        riskNotice.value = assistant.reviewId ? { reviewId: assistant.reviewId, riskLevel: assistant.riskLevel ?? undefined } : null
      } catch (err) {
        error.value = toMessage(err)
      } finally {
        sending.value = false
      }
      return
    }

    streaming.value = true
    streamEvents.value = []
    activePlan.value = null
    riskNotice.value = null
    const assistant = createOptimisticMessage('ASSISTANT', '')
    currentSession.value.messages = [...currentSession.value.messages, assistant]
    syncCurrentSession()

    try {
      await streamMessage(sessionId, content, (event) => {
        applyStreamEvent(event)
      })
      await selectSession(sessionId)
    } catch (err) {
      error.value = toMessage(err)
    } finally {
      streaming.value = false
      sending.value = false
    }
  }

  async function cancelCurrentRun() {
    if (!currentSession.value || cancelling.value) {
      return
    }

    cancelling.value = true
    error.value = ''
    try {
      const run = await cancelSession(currentSession.value.id)
      currentRunId.value = run.id
      appendStreamEvent('agent.cancelled', `运行已取消：${run.status}`)
    } catch (err) {
      error.value = toMessage(err)
    } finally {
      cancelling.value = false
    }
  }

  async function resumeCurrentRun() {
    if (!currentSession.value || streaming.value) {
      return
    }

    streaming.value = true
    error.value = ''
    try {
      const assistant = await resumeSession(currentSession.value.id)
      currentSession.value.messages = [...currentSession.value.messages, assistant]
      currentRunId.value = assistant.runId ?? ''
      await selectSession(currentSession.value.id)
    } catch (err) {
      error.value = toMessage(err)
    } finally {
      streaming.value = false
    }
  }

  function applyStreamEvent(event: SseEvent) {
    appendStreamEvent(event.event, event.data)
    if (!currentSession.value) {
      return
    }

    if (event.event === 'message.meta' || event.event === 'agent.completed') {
      currentRunId.value = readJsonField(event.data, 'runId') ?? currentRunId.value
      return
    }

    if (event.event === 'plan.updated') {
      activePlan.value = parseJson<PlanView>(event.data)
      return
    }

    if (event.event === 'risk.pending_review') {
      riskNotice.value = parseJson<{ reviewId?: string; riskLevel?: string }>(event.data)
      return
    }

    if (event.event === 'agent.failed' || event.event === 'agent.cancelled') {
      error.value = event.data || '任务已停止'
      const lastMessage = currentSession.value.messages.at(-1)
      if (lastMessage?.role === 'ASSISTANT' && !lastMessage.content) {
        currentSession.value.messages = currentSession.value.messages.map((message) => {
          if (message.id !== lastMessage.id) {
            return message
          }
          return {
            ...message,
            content: error.value,
          }
        })
        syncCurrentSession()
      }
      return
    }

    const lastMessage = currentSession.value.messages.at(-1)
    if (!lastMessage || lastMessage.role !== 'ASSISTANT') {
      return
    }
    if (event.event === 'message.delta') {
      currentSession.value.messages = currentSession.value.messages.map((message) => {
        if (message.id !== lastMessage.id) {
          return message
        }
        return {
          ...message,
          content: message.content + event.data,
          runId: currentRunId.value || message.runId,
        }
      })
      syncCurrentSession()
    }
  }

  function createOptimisticMessage(
    role: 'USER' | 'ASSISTANT',
    content: string,
    extras?: Partial<Pick<MessageView, 'questionTypes' | 'queryCard' | 'queryCards' | 'citations'>>,
  ): MessageView {
    return {
      id: crypto.randomUUID(),
      role,
      content,
      createdAt: new Date().toISOString(),
      ...extras,
    }
  }

  function appendStreamEvent(event: string, data: string) {
    streamEvents.value = [
      {
        id: crypto.randomUUID(),
        event,
        data,
        createdAt: new Date().toISOString(),
      },
      ...streamEvents.value,
    ].slice(0, 12)
  }

  async function runDataQueryInConversation(
    sessionId: string,
    content: string,
    mode: DataQueryMode,
    questionTypes: string[],
  ): Promise<DataQueryOutcome> {
    const userMessage = createOptimisticMessage('USER', content, { questionTypes })
    const loadingMessage = createOptimisticMessage('ASSISTANT', '正在查询统计结果，请稍候...', { questionTypes })
    appendClientMessages(sessionId, [userMessage, loadingMessage])
    currentRunId.value = ''
    activePlan.value = null
    riskNotice.value = null
    streamEvents.value = []

    try {
      const answer = await answerDataQuery(content)
      const assistant = createDataQuerySuccessMessage(answer, questionTypes)
      replaceClientMessage(sessionId, loadingMessage.id, assistant)
      appendStreamEvent(
        'data.query.completed',
        `已完成智能问数，生成 ${answer.queryCards.length || 0} 项结果`,
      )
      error.value = ''
      return 'done'
    } catch (err) {
      if (mode === 'try') {
        removeClientMessages(sessionId, [userMessage.id, loadingMessage.id])
        return 'fallback'
      }

      replaceClientMessage(sessionId, loadingMessage.id, createDataQueryFailureMessage(toMessage(err), questionTypes))
      appendStreamEvent('data.query.failed', `问数识别失败：${toMessage(err)}`)
      error.value = toMessage(err)
      return 'failed'
    }
  }

  function createDataQuerySuccessMessage(
    answer: QueryAnswerView,
    questionTypes: string[] = ['智能问数'],
  ): MessageView {
    const cards = answer.queryCards ?? []
    const primaryCard = cards[0] ?? null
    return {
      ...createOptimisticMessage('ASSISTANT', answer.answer, {
        questionTypes,
        queryCard: primaryCard,
        queryCards: cards.length > 1 ? cards : null,
        citations: answer.citations ?? null,
      }),
    }
  }

  function createDataQueryFailureMessage(content: string, questionTypes: string[] = ['智能问数']): MessageView {
    return createOptimisticMessage('ASSISTANT', content, { questionTypes })
  }

  function buildDataQueryAnswerFromCard(queryCard: QueryConversationView) {
    return buildQueryAnswerText(queryCard)
  }

  function resolveDataQueryMode(content: string, preferredType?: QuestionTypeLabel | null): DataQueryMode {
    if (preferredType === '智能问数') {
      return 'strict'
    }

    const normalized = content.replace(/\s+/g, '')
    const mixedAnalysisIntent =
      /(法规|法条|政策|依据|条款|建议|处置建议|研判|解读|怎么处理|如何处理|原因|为什么)/.test(normalized)

    if (mixedAnalysisIntent) {
      return 'none'
    }

    if (/(智能问数|统计|汇总|排行|排名|同比|环比|趋势|占比|分布|图表|预警|峰值|均值|明细|清单|列表)/.test(normalized)) {
      return 'strict'
    }

    if (/(数据|查询|分析|多少|数量|监测|监控|变化)/.test(normalized)) {
      return 'try'
    }

    return 'none'
  }

  function appendClientMessages(sessionId: string, messagesToAppend: MessageView[]) {
    const current = localMessages.value[sessionId] ?? []
    localMessages.value = {
      ...localMessages.value,
      [sessionId]: [...current, ...messagesToAppend],
    }
    persistClientMessageCache(localMessages.value)
    sessions.value = sessions.value.map((session) => {
      if (session.id !== sessionId) {
        return session
      }
      return {
        ...session,
        messages: [...session.messages, ...messagesToAppend],
      }
    })

    if (currentSession.value?.id === sessionId) {
      currentSession.value = {
        ...currentSession.value,
        messages: [...currentSession.value.messages, ...messagesToAppend],
      }
    }
  }

  function replaceClientMessage(sessionId: string, messageId: string, nextMessage: MessageView) {
    const replaceMessage = (message: MessageView) => message.id === messageId ? nextMessage : message
    const current = localMessages.value[sessionId] ?? []
    if (current.some((message) => message.id === messageId)) {
      localMessages.value = {
        ...localMessages.value,
        [sessionId]: current.map(replaceMessage),
      }
      persistClientMessageCache(localMessages.value)
    }

    sessions.value = sessions.value.map((session) => {
      if (session.id !== sessionId) {
        return session
      }
      return {
        ...session,
        messages: session.messages.map(replaceMessage),
      }
    })

    if (currentSession.value?.id === sessionId) {
      currentSession.value = {
        ...currentSession.value,
        messages: currentSession.value.messages.map(replaceMessage),
      }
    }
  }

  function removeClientMessages(sessionId: string, messageIds: string[]) {
    const current = localMessages.value[sessionId] ?? []
    if (!current.length) {
      return
    }

    const remaining = current.filter((message) => !messageIds.includes(message.id))
    const nextCache = { ...localMessages.value }
    if (remaining.length) {
      nextCache[sessionId] = remaining
    } else {
      delete nextCache[sessionId]
    }
    localMessages.value = nextCache
    persistClientMessageCache(localMessages.value)
    sessions.value = sessions.value.map((session) => {
      if (session.id !== sessionId) {
        return session
      }
      return {
        ...session,
        messages: session.messages.filter((message) => !messageIds.includes(message.id)),
      }
    })

    if (currentSession.value?.id === sessionId) {
      currentSession.value = {
        ...currentSession.value,
        messages: currentSession.value.messages.filter((message) => !messageIds.includes(message.id)),
      }
    }
  }

  function mergeSession(session: SessionView): SessionView {
    const normalizedSession = normalizeSession(session)
    const extras = localMessages.value[session.id] ?? []
    if (!extras.length) {
      return normalizedSession
    }

    return {
      ...normalizedSession,
      messages: [...normalizedSession.messages, ...extras.map(normalizeMessage)],
    }
  }

  function normalizeSession(session: SessionView): SessionView {
    return {
      ...session,
      messages: session.messages.map(normalizeMessage),
    }
  }

  function syncCurrentSession() {
    if (!currentSession.value) {
      return
    }
    syncSessionSummary(currentSession.value)
  }

  function syncSessionSummary(session: SessionView) {
    sessions.value = sessions.value.map((item) => {
      if (item.id !== session.id) {
        return item
      }
      return {
        ...item,
        title: session.title,
        status: session.status,
        createdAt: session.createdAt,
        messages: session.messages.map(normalizeMessage),
      }
    })
  }

  function normalizeMessage(message: MessageView): MessageView {
    const sourceCards = message.queryCards?.length
      ? message.queryCards
      : message.queryCard
        ? [message.queryCard]
        : message.composedAnswer?.queryCards?.length
          ? message.composedAnswer.queryCards
          : []

    if (!sourceCards.length) {
      return {
        ...message,
        content: formatAssistantMessageContent(message),
      }
    }

    const cards = sourceCards
      .map((card) => {
        const rows = sanitizeQueryRows(card.rows)
        return {
          ...card,
          rows,
          rowCount: rows.length,
        }
      })
    const queryCard = cards[0] ?? null
    const content = formatAssistantMessageContent(message, queryCard)
      || (queryCard ? buildDataQueryAnswerFromCard(queryCard) : '')

    return {
      ...message,
      content,
      queryCard: queryCard ?? undefined,
      queryCards: cards.length > 1 ? cards : undefined,
    }
  }

  function sanitizeQueryRows(rows: Array<Record<string, unknown>>) {
    return rows.filter((row) => Object.values(row).some(hasMeaningfulValue))
  }

  function formatAssistantMessageContent(message: MessageView, queryCard?: QueryConversationView | null) {
    if (message.role !== 'ASSISTANT') {
      return message.content
    }

    const composedAnswer = message.composedAnswer
    if (composedAnswer) {
      const conclusion = sanitizeConclusion(composedAnswer.conclusion)
      const suggestion = sanitizeSuggestion(composedAnswer.suggestionSection)
      const sections = [conclusion]
      if (suggestion) {
        sections.push(`处置建议：${suggestion}`)
      }
      const displayContent = sections.filter(Boolean).join('\n\n').trim()
      if (displayContent) {
        return displayContent
      }
    }

    if (!message.content) {
      return queryCard ? buildDataQueryAnswerFromCard(queryCard) : ''
    }

    const fallbackSections = parseStructuredSections(message.content)
    if (fallbackSections.conclusion || fallbackSections.suggestion) {
      const sections = [
        sanitizeConclusion(fallbackSections.conclusion),
        fallbackSections.suggestion ? `处置建议：${sanitizeSuggestion(fallbackSections.suggestion)}` : '',
      ]
      return sections.filter(Boolean).join('\n\n').trim()
    }

    return message.content
  }

  function parseStructuredSections(content: string) {
    const labels = ['结论', '数据', '依据', '建议', '口径', '限制']
    const pattern = /(结论|数据|依据|建议|口径|限制)：/gu
    const sections: Partial<Record<(typeof labels)[number], string>> = {}
    const matches = [...content.matchAll(pattern)]
    if (!matches.length) {
      return {
        conclusion: '',
        suggestion: '',
      }
    }

    matches.forEach((match, index) => {
      const start = match.index ?? 0
      const label = match[1] as (typeof labels)[number]
      const contentStart = start + match[0].length
      const contentEnd = index + 1 < matches.length ? (matches[index + 1].index ?? content.length) : content.length
      sections[label] = content.slice(contentStart, contentEnd).trim()
    })

    return {
      conclusion: sections.结论 ?? '',
      suggestion: sections.建议 ?? '',
    }
  }

  function sanitizeConclusion(value?: string | null) {
    if (!value) {
      return ''
    }
    return value
      .replace(/[，,]?并补充\d+条依据[。.]?/gu, '')
      .replace(/\s+/gu, ' ')
      .trim()
  }

  function sanitizeSuggestion(value?: string | null) {
    if (!value) {
      return ''
    }
    return value
      .replace(/^建议[:：]\s*/u, '')
      .replace(/\s+/gu, ' ')
      .trim()
  }

  function hasMeaningfulValue(value: unknown) {
    if (value === null || value === undefined) {
      return false
    }
    if (typeof value === 'string') {
      return value.trim().length > 0
    }
    if (Array.isArray(value)) {
      return value.length > 0
    }
    if (typeof value === 'object') {
      return Object.keys(value as Record<string, unknown>).length > 0
    }
    return true
  }

  function parseJson<T>(value: string): T | null {
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }

  function readJsonField(value: string, field: string): string | undefined {
    const data = parseJson<Record<string, string>>(value)
    return data?.[field]
  }

  function toMessage(err: unknown): string {
    return err instanceof Error ? err.message : '未知错误'
  }

  function updateCurrentPlanInspectorState(patch: Partial<PlanInspectorState>) {
    const sessionId = currentSession.value?.id
    if (!sessionId) {
      return
    }
    updatePlanInspectorState(sessionId, patch)
  }

  function updatePlanInspectorState(sessionId: string, patch: Partial<PlanInspectorState>) {
    const current = normalizePlanInspectorState(planInspectorStateBySession.value[sessionId])
    const next: PlanInspectorState = {
      filter: patch.filter ?? current.filter,
      selectedStepOrder: patch.selectedStepOrder === undefined ? current.selectedStepOrder : patch.selectedStepOrder,
    }

    if (current.filter === next.filter && current.selectedStepOrder === next.selectedStepOrder) {
      return
    }

    planInspectorStateBySession.value = {
      ...planInspectorStateBySession.value,
      [sessionId]: next,
    }
    persistPlanInspectorCache(planInspectorStateBySession.value)
  }

  return {
    sessions,
    currentSession,
    messages,
    latestAssistantMessage,
    loading,
    creatingSession,
    sending,
    streaming,
    cancelling,
    error,
    currentRunId,
    activePlan,
    riskNotice,
    streamEvents,
    currentPlanInspectorState,
    bootstrap,
    createNewSession,
    selectSession,
    send,
    cancelCurrentRun,
    resumeCurrentRun,
    updateCurrentPlanInspectorState,
  }
})

function loadClientMessageCache(): Record<string, MessageView[]> {
  if (typeof window === 'undefined') {
    return {}
  }

  try {
    const raw = window.localStorage.getItem(CLIENT_MESSAGE_CACHE_KEY)
    return raw ? JSON.parse(raw) as Record<string, MessageView[]> : {}
  } catch {
    return {}
  }
}

function persistClientMessageCache(value: Record<string, MessageView[]>) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(CLIENT_MESSAGE_CACHE_KEY, JSON.stringify(value))
  } catch {
    // 忽略本地缓存写入失败，避免影响主流程
  }
}

function normalizePlanInspectorState(value: Partial<PlanInspectorState> | null | undefined): PlanInspectorState {
  const filter = value?.filter
  const selectedStepOrder = value?.selectedStepOrder
  return {
    filter: filter === 'failed' || filter === 'system' || filter === 'result' ? filter : 'all',
    selectedStepOrder: typeof selectedStepOrder === 'number' && Number.isInteger(selectedStepOrder) && selectedStepOrder > 0
      ? selectedStepOrder
      : null,
  }
}

function loadPlanInspectorCache(): Record<string, PlanInspectorState> {
  if (typeof window === 'undefined') {
    return {}
  }

  try {
    const raw = window.localStorage.getItem(PLAN_INSPECTOR_CACHE_KEY)
    if (!raw) {
      return {}
    }

    const parsed = JSON.parse(raw) as Record<string, Partial<PlanInspectorState>>
    return Object.fromEntries(
      Object.entries(parsed).map(([sessionId, state]) => [sessionId, normalizePlanInspectorState(state)]),
    )
  } catch {
    return {}
  }
}

function persistPlanInspectorCache(value: Record<string, PlanInspectorState>) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(PLAN_INSPECTOR_CACHE_KEY, JSON.stringify(value))
  } catch {
    // 忽略本地缓存写入失败，避免影响主流程
  }
}
