import { defineStore } from 'vue'
import { postJson, STORAGE_KEYS } from './client'
import type { ApiResponse } from '@/types/api'

export interface LoginResponse {
  accessToken: string
  tokenType: string
}

export interface UserInfo {
  userId: string
  role: string
  region: string
}

const TOKEN_KEY = STORAGE_KEYS.token
const USER_KEY = 'urban-agent.user-info'

function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY) || null)
  const user = ref<UserInfo | null>(loadUserInfo())

  function loadUserInfo(): UserInfo | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserInfo
    } catch {
      return null
    }
  }

  async function login(userId: string, password: string): Promise<boolean> {
    try {
      const resp = await postJson<LoginResponse>('/api/v1/auth/login', { userId, password })
      token.value = resp.accessToken
      saveToken(resp.accessToken)
      await fetchMe()
      return true
    } catch {
      return false
    }
  }

  async function fetchMe(): Promise<void> {
    if (!token.value) return
    try {
      const resp = await postJson<UserInfo>('/api/v1/auth/me', {})
      user.value = resp
      localStorage.setItem(USER_KEY, JSON.stringify(resp))
    } catch {
      // token may be invalid
    }
  }

  function logout() {
    token.value = null
    user.value = null
    clearToken()
    window.location.href = '/login'
  }

  const isLoggedIn = computed(() => Boolean(token.value) && Boolean(user.value))

  return { token, user, login, logout, fetchMe, isLoggedIn }
})