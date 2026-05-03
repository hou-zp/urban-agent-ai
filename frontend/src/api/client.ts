import type { ApiResponse } from '@/types/api'

type QueryParams = Record<string, string | number | boolean | null | undefined>

const STORAGE_KEYS = {
  token: 'urban-agent.jwt-token',
  userId: 'urban-agent.user-id',
  role: 'urban-agent.user-role',
  region: 'urban-agent.user-region',
} as const

class ResponseFormatError extends Error {
  constructor(
    message: string,
    readonly detail: {
      url: string
      status: number
      contentType: string
      bodyPreview: string
    },
  ) {
    super(message)
    this.name = 'ResponseFormatError'
  }
}

const DEFAULT_TIMEOUT_MS = 15_000

async function parseJson<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text()
  let payload: ApiResponse<T>

  try {
    payload = text
      ? (JSON.parse(text) as ApiResponse<T>)
      : ({ code: response.ok ? 0 : -1, data: null, message: '' } as ApiResponse<T>)
  } catch {
    const bodyPreview = text.slice(0, 240)
    if (bodyPreview.includes('Invalid CORS request')) {
      throw new Error('浏览器来源未被后端允许，请检查本地跨域配置')
    }

    throw new ResponseFormatError('服务返回格式异常', {
      url: response.url,
      status: response.status,
      contentType: response.headers.get('content-type') ?? '',
      bodyPreview,
    })
  }

  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || `请求失败：${response.status}`)
  }
  return payload
}

async function requestJson<T>(url: string, init: RequestInit, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<T> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
    })
    const payload = await parseJson<T>(response)
    return payload.data
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw new Error('请求超时，请稍后重试')
    }
    if (err instanceof ResponseFormatError) {
      console.error(`Response format error ${JSON.stringify(err.detail)}`)
      throw err
    }
    throw err
  } finally {
    window.clearTimeout(timer)
  }
}

export async function getJson<T>(url: string, params?: QueryParams): Promise<T> {
  return requestJson<T>(withQuery(url, params), {
    headers: defaultHeaders(),
  })
}

export async function postJson<T>(url: string, body: unknown): Promise<T> {
  return requestJson<T>(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...defaultHeaders(),
    },
    body: JSON.stringify(body),
  })
}

export async function putJson<T>(url: string, body: unknown): Promise<T> {
  return requestJson<T>(url, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...defaultHeaders(),
    },
    body: JSON.stringify(body),
  })
}

export async function deleteJson<T>(url: string): Promise<T> {
  return requestJson<T>(url, {
    method: 'DELETE',
    headers: defaultHeaders(),
  })
}

export async function postForm<T>(url: string, body: FormData): Promise<T> {
  return requestJson<T>(url, {
    method: 'POST',
    headers: defaultHeaders(),
    body,
  }, 30_000)
}

function defaultHeaders(): HeadersInit {
  const headers: Record<string, string> = {}
  const token = readStorage(STORAGE_KEYS.token)
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function readStorage(key: string): string {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.localStorage.getItem(key)?.trim() ?? ''
}

function withQuery(url: string, params?: QueryParams): string {
  if (!params) {
    return url
  }

  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })

  const query = search.toString()
  return query ? `${url}?${query}` : url
}

export { defaultHeaders, STORAGE_KEYS }
