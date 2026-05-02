import { getJson, postForm, postJson } from '@/api/client'
import type {
  KnowledgeCategory,
  KnowledgeDocumentStatus,
  KnowledgeDocumentView,
  KnowledgeSearchHitView,
  UploadKnowledgeDocumentPayload,
} from '@/types/api'

export function listKnowledgeDocuments() {
  return getJson<KnowledgeDocumentView[]>('/api/v1/knowledge/documents')
}

export function uploadKnowledgeDocument(payload: UploadKnowledgeDocumentPayload) {
  const form = new FormData()
  form.set('title', payload.title)
  form.set('category', payload.category)
  appendIfPresent(form, 'sourceOrg', payload.sourceOrg)
  appendIfPresent(form, 'documentNumber', payload.documentNumber)
  appendIfPresent(form, 'effectiveFrom', payload.effectiveFrom)
  appendIfPresent(form, 'effectiveTo', payload.effectiveTo)
  appendIfPresent(form, 'regionCode', payload.regionCode)
  appendIfPresent(form, 'summary', payload.summary)
  form.set('file', payload.file)

  return postForm<KnowledgeDocumentView>('/api/v1/knowledge/documents', form)
}

export function indexKnowledgeDocument(documentId: string) {
  return postJson<KnowledgeDocumentView>(`/api/v1/knowledge/documents/${documentId}/index`, {})
}

export function updateKnowledgeDocumentStatus(documentId: string, status: KnowledgeDocumentStatus) {
  return postJson<KnowledgeDocumentView>(`/api/v1/knowledge/documents/${documentId}/status`, { status })
}

export function searchKnowledge(query: string, category?: KnowledgeCategory, limit = 5) {
  return getJson<KnowledgeSearchHitView[]>('/api/v1/knowledge/search', { query, category, limit })
}

function appendIfPresent(form: FormData, key: string, value?: string) {
  if (value?.trim()) {
    form.set(key, value.trim())
  }
}
