import { getJson } from '@/api/client'
import type {
  AgentRunAuditView,
  ModelCallAuditView,
  QueryRecordAuditView,
  RiskEventAuditView,
  ToolCallAuditView,
} from '@/types/api'

export function listAgentRunAudits() {
  return getJson<AgentRunAuditView[]>('/api/v1/audit/agent-runs')
}

export function listToolCallAudits() {
  return getJson<ToolCallAuditView[]>('/api/v1/audit/tool-calls')
}

export function listDataAccessAudits() {
  return getJson<QueryRecordAuditView[]>('/api/v1/audit/data-access')
}

export function listRiskEventAudits() {
  return getJson<RiskEventAuditView[]>('/api/v1/audit/risk-events')
}

export function listModelCallAudits() {
  return getJson<ModelCallAuditView[]>('/api/v1/audit/model-calls')
}
