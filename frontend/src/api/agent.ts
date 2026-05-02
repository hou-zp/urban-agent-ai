import { defaultHeaders, getJson, postJson } from '@/api/client'
import type { MessageView, PlanView, RunView, SessionView, SseEvent } from '@/types/api'

export function listSessions() {
  return getJson<SessionView[]>('/api/v1/agent/sessions')
}

export function createSession(title: string) {
  return postJson<SessionView>('/api/v1/agent/sessions', { title })
}

export function getSession(sessionId: string) {
  return getJson<SessionView>(`/api/v1/agent/sessions/${sessionId}`)
}

export function sendMessage(sessionId: string, content: string) {
  return postJson<MessageView>(`/api/v1/agent/sessions/${sessionId}/messages`, { content })
}

export function cancelSession(sessionId: string) {
  return postJson<RunView>(`/api/v1/agent/sessions/${sessionId}/cancel`, {})
}

export function resumeSession(sessionId: string) {
  return postJson<MessageView>(`/api/v1/agent/sessions/${sessionId}/resume`, {})
}

export function getRunPlan(runId: string) {
  return getJson<PlanView>(`/api/v1/agent/sessions/runs/${runId}/plan`)
}

export function executeNextPlanStep(runId: string) {
  return postJson<PlanView>(`/api/v1/agent/sessions/runs/${runId}/plan/execute-next`, {})
}

export async function streamMessage(
  sessionId: string,
  content: string,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/v1/agent/sessions/${sessionId}/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...defaultHeaders(),
    },
    body: JSON.stringify({ content }),
  })

  if (!response.ok || !response.body) {
    throw new Error('流式请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''

    for (const part of parts) {
      const event = parseSseEvent(part)
      if (event) {
        onEvent(event)
      }
    }
  }
}

function parseSseEvent(raw: string): SseEvent | null {
  const lines = raw.split(/\r?\n/)
  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.startsWith('data: ') ? line.slice(6) : line.slice(5))
    }
  }

  if (!dataLines.length) {
    return null
  }

  return { event, data: dataLines.join('\n') }
}
