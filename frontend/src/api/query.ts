import { getJson, postJson } from '@/api/client'
import type {
  BusinessRecordQueryRequest,
  BusinessRecordQueryView,
  DataCatalogSyncResult,
  DataTableView,
  MetricDefinitionView,
  QueryAnswerView,
  QueryExecuteView,
  QueryPreviewView,
} from '@/types/api'

export function syncDataCatalog() {
  return postJson<DataCatalogSyncResult>('/api/v1/data/catalog/sync', {})
}

export function listMetrics() {
  return getJson<MetricDefinitionView[]>('/api/v1/data/catalog/metrics')
}

export function listAuthorizedTables(keyword?: string) {
  return getJson<DataTableView[]>('/api/v1/data/catalog/tables', { keyword })
}

export function previewDataQuery(question: string) {
  return postJson<QueryPreviewView>('/api/v1/data/query/preview', { question })
}

export function answerDataQuery(question: string) {
  return postJson<QueryAnswerView>('/api/v1/data/query/answer', { question })
}

export function executeDataQuery(question: string, sql: string) {
  return postJson<QueryExecuteView>('/api/v1/data/query/execute', { question, sql })
}

export function queryBusinessRecords(request: BusinessRecordQueryRequest) {
  return postJson<BusinessRecordQueryView>('/api/v1/data/query/records', request)
}
