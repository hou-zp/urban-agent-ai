export interface ApiResponse<T> {
  code: number
  data: T
  message: string
}

export interface MessageCitationView {
  documentId: string
  documentTitle: string
  fileName: string | null
  category: string
  sourceOrg: string | null
  documentNumber: string | null
  sourceUrl: string | null
  snippet: string
  sectionTitle: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
}

export interface QueryConversationView {
  question?: string | null
  metricCode: string | null
  metricName: string | null
  scopeSummary: string
  resultSummary: string
  permissionRewrite: string
  warnings: string[]
  rowCount: number
  executedAt: string
  rows: Array<Record<string, unknown>>
}

export interface QueryAnswerView {
  mode: string
  answer: string
  warnings: string[]
  queryCards: QueryConversationView[]
  citations?: MessageCitationView[] | null
}

export interface ComposedAnswerView {
  conclusion: string
  dataSection: string
  evidenceSection: string
  suggestionSection: string
  statementSection: string
  limitationSection: string
  queryCards: QueryConversationView[]
}

export interface MessageView {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  questionTypes?: string[] | null
  citations?: MessageCitationView[] | null
  composedAnswer?: ComposedAnswerView | null
  riskLevel?: string | null
  reviewId?: string | null
  reviewStatus?: string | null
  createdAt: string
  runId?: string | null
  planId?: string | null
  queryCard?: QueryConversationView | null
  queryCards?: QueryConversationView[] | null
}

export interface SessionView {
  id: string
  title: string
  status: string
  createdAt: string
  messages: MessageView[]
}

export interface SseEvent {
  event: string
  data: string
}

export interface StreamEventLog {
  id: string
  event: string
  data: string
  createdAt: string
}

export interface RunView {
  id: string
  sessionId: string
  userId: string
  question: string
  status: string
  modelName: string
  createdAt: string
  completedAt: string | null
}

export interface PlanStepView {
  id: string
  stepOrder: number
  taskCode: string
  taskType: string
  name: string
  goal: string
  status: string
  dependencyStepIds: string | null
  outputSummary: string | null
  resultRef?: string | null
  executionTrace?: {
    triggerMode: string
    triggerLabel: string
    systemActionCount: number
    lastActionAt?: string | null
    resultRef?: string | null
    resultLabel?: string | null
  } | null
  failureDetail?: {
    errorCode: string
    category: string
    headline: string
    reason: string
    action: string
    actionLabel: string
    handleCode: string
    dependencyBlocked: boolean
    dependencyStepOrders: number[]
  } | null
  retryAdvice?: {
    action: string
    reason: string
    dependencyStepOrders: number[]
  } | null
  systemActions?: Array<{
    action: string
    dependencyStepOrder: number
    dependencyStepName: string
    summary: string
    createdAt: string
  }>
  outputPayload?: {
    kind: string
    queryId?: string | null
    validatedSql?: string | null
    executedSql?: string | null
    metricCode?: string | null
    metricName?: string | null
    permissionRewrite?: string | null
    rowCount?: number | null
    warnings?: string[]
    documentIds?: string[]
    summary?: string | null
  } | null
  createdAt: string
  updatedAt: string
}

export interface PlanView {
  id: string
  runId: string
  goal: string
  status: string
  confirmStatus: string
  systemSummary?: {
    autorunCount: number
    recoverCount: number
    affectedStepCount: number
    lastActionAt: string | null
    summary: string
  } | null
  steps: PlanStepView[]
  createdAt: string
  updatedAt: string
}

export type KnowledgeCategory = 'POLICY' | 'LAW' | 'BUSINESS'
export type KnowledgeCategoryValue = KnowledgeCategory | Lowercase<KnowledgeCategory>
export type KnowledgeDocumentStatus = 'DRAFT' | 'INDEXING' | 'ACTIVE' | 'EXPIRED' | 'ABOLISHED' | 'FAILED'
export type KnowledgeDocumentStatusValue = KnowledgeDocumentStatus | Lowercase<KnowledgeDocumentStatus>

export interface KnowledgeDocumentView {
  id: string
  title: string
  category: KnowledgeCategoryValue
  sourceOrg: string | null
  documentNumber: string | null
  status: KnowledgeDocumentStatusValue
  effectiveFrom: string | null
  effectiveTo: string | null
  regionCode: string | null
  summary: string | null
  fileName: string | null
  mimeType: string | null
  createdAt: string
  updatedAt: string
  indexedAt: string | null
  failedReason: string | null
}

export interface UploadKnowledgeDocumentPayload {
  title: string
  category: KnowledgeCategoryValue
  sourceOrg?: string
  documentNumber?: string
  effectiveFrom?: string
  effectiveTo?: string
  regionCode?: string
  summary?: string
  file: File
}

export interface KnowledgeSearchHitView {
  documentId: string
  documentTitle: string
  category: KnowledgeCategory
  sourceOrg: string | null
  documentNumber: string | null
  sectionTitle: string | null
  snippet: string
  score: number
  effectiveFrom: string | null
  effectiveTo: string | null
}

export interface DataFieldView {
  fieldName: string
  businessName: string
  dataType: string
  sensitiveLevel: string
}

export interface DataTableView {
  tableName: string
  businessName: string
  permissionTag: string
  regionCode: string
  fields: DataFieldView[]
}

export interface MetricDefinitionView {
  metricCode: string
  metricName: string
  description: string
  aggregationExpr: string
  defaultTimeField: string
  commonDimensions: string
  tableName: string
}

export interface DataCatalogSyncResult {
  dataSourceCount: number
  tableCount: number
  fieldCount: number
  metricCount: number
}

export interface QueryPreviewView {
  metricCode: string | null
  metricName: string | null
  candidateSql: string
  validatedSql: string
  permissionRewrite: string
  summary: string
  warnings: string[]
}

export interface QueryExecuteView {
  executedSql: string
  summary: string
  rowCount: number
  executedAt: string
  rows: Array<Record<string, unknown>>
}

export type BusinessRecordType = 'CASE' | 'MERCHANT' | 'LAND_PLOT' | 'POINT' | 'WORK_ORDER'

export interface BusinessRecordFieldView {
  fieldName: string
  businessName: string
  dataType: string
  sensitiveLevel: string
  masked: boolean
}

export interface BusinessRecordQueryRequest {
  recordType: BusinessRecordType
  keyword?: string
  regionCode?: string
  streetName?: string
  status?: string
  timeRange?: string
  limit?: number
}

export interface BusinessRecordQueryView {
  recordQueryId: string
  recordType: BusinessRecordType
  tableName: string
  businessName: string
  permissionTag: string
  regionCode: string | null
  maskedFields: string[]
  fields: BusinessRecordFieldView[]
  rows: Array<Record<string, unknown>>
}

export interface AgentRunAuditView {
  id: string
  sessionId: string
  userId: string
  question: string
  status: string
  modelName: string
  createdAt: string
  completedAt: string | null
}

export interface ToolCallAuditView {
  id: string
  runId: string
  toolName: string
  inputSummary: string | null
  outputSummary: string | null
  createdAt: string
}

export interface QueryRecordAuditView {
  id: string
  userId: string
  question: string
  candidateSql: string | null
  executedSqlSummary: string | null
  permissionRewrite: string | null
  resultSummary: string | null
  status: string
  createdAt: string
}

export interface RiskEventAuditView {
  id: string
  runId: string
  sessionId: string
  userId: string
  question: string
  riskLevel: string
  riskCategories: string | null
  triggerReason: string
  reviewRequired: boolean
  createdAt: string
}

export interface ModelCallAuditView {
  id: string
  runId: string | null
  userId: string
  provider: string
  modelName: string
  operation: string
  status: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  latencyMs: number
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
}

// ========== 阶段 3 新增类型 ==========

/**
 * 数据溯源信息
 */
export interface DataLineage {
  queryId: string          // 查询编号
  dataSourceName: string  // 数据来源名称
  caliber: string         // 统计口径
  dataUpdatedAt: string   // 数据更新时间
  permissionStatus: string // 权限状态
  rowCount?: number        // 结果行数（可选）
}

/**
 * 证据引用（增强版）
 */
export interface EvidenceRefView {
  evidenceId: string
  sourceType: 'POLICY' | 'LAW' | 'BUSINESS_RULE' | string
  documentTitle: string
  issuingAuthority: string | null
  docNo: string | null
  sectionTitle: string | null
  articleNo: string | null
  quote: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
  effectiveAtQueryTime: boolean
  queryId?: string | null
}

/**
 * 数据声明
 */
export interface DataClaimView {
  claimText: string
  claimType: 'COUNT' | 'RANKING' | 'TREND' | 'COMPARISON' | 'STATUS' | 'CHART' | 'RESPONSIBILITY'
  queryId: string | null
  supported: boolean
}

/**
 * 风险等级
 */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/**
 * 风险提示
 */
export interface RiskWarningView {
  riskLevel: RiskLevel
  riskCategories: string[]
  message: string
  requiresReview: boolean
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null
}

/**
 * 图表规范
 */
export interface ChartSpecView {
  chartId: string
  chartType: 'BAR' | 'LINE' | 'PIE' | 'TABLE' | 'MAP' | 'SCATTER'
  title: string
  queryId: string
  dataSourceName: string
  caliber: string
  dataUpdatedAt: string
  xFields: string[]
  yFields: string[]
  dataset: Array<Record<string, unknown>>
}

/**
 * 增强的答案结构（AnswerDraft 渲染版）
 */
export interface EnhancedAnswerView {
  conclusion: string
  dataFindings: string[]
  policyFindings: string[]
  lawFindings: string[]
  businessJudgements: string[]
  suggestions: string[]
  limitations: string[]
  queryId?: string | null
  evidenceRefs?: EvidenceRefView[]
  dataClaims?: DataClaimView[]
  charts?: ChartSpecView[]
  riskWarning?: RiskWarningView | null
}

/**
 * 增强的消息视图（用于前端展示）
 */
export interface EnhancedMessageView extends MessageView {
  dataLineage?: DataLineage[]
  riskWarning?: RiskWarningView | null
  enhancedAnswer?: EnhancedAnswerView | null
}
