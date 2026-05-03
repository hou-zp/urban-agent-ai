import { getJson, putJson } from '@/api/client'
import type { UserInfo } from '@/stores/auth'

export function getCurrentUser() {
  return getJson<UserInfo>('/api/v1/auth/me')
}

export function updatePassword(payload: { oldPassword: string; newPassword: string }) {
  return putJson<void>('/api/v1/auth/password', payload)
}