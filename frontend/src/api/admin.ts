import { getJson, postJson, putJson, deleteJson } from '@/api/client'
import type { UserInfo } from '@/stores/auth'

interface AdminUserItem extends UserInfo {
  status: string
}

const MOCK_USERS: AdminUserItem[] = [
  { userId: 'admin', role: 'ADMIN', region: 'shaoxing-keqiao', status: 'ACTIVE' },
  { userId: 'auditor01', role: 'AUDITOR', region: 'shaoxing-keqiao', status: 'ACTIVE' },
  { userId: 'demo-user', role: 'USER', region: 'shaoxing-keqiao', status: 'ACTIVE' },
  { userId: 'operator01', role: 'USER', region: 'shaoxing-keqiao', status: 'ACTIVE' },
  { userId: 'operator02', role: 'USER', region: 'shaoxing-keqiao', status: 'DISABLED' },
]

let _users = [...MOCK_USERS]

export async function listUsers(): Promise<AdminUserItem[]> {
  try {
    return await getJson<AdminUserItem[]>('/api/v1/admin/users')
  } catch {
    return [..._users]
  }
}

export async function createUser(payload: { userId: string; role: string; region: string; password: string }): Promise<void> {
  try {
    await postJson('/api/v1/admin/users', payload)
  } catch {
    _users.push({ ...payload, status: 'ACTIVE' })
  }
}

export async function updateUser(payload: { userId: string; role: string; region: string; password: string }): Promise<void> {
  try {
    await putJson(`/api/v1/admin/users/${payload.userId}`, payload)
  } catch {
    const idx = _users.findIndex((u) => u.userId === payload.userId)
    if (idx >= 0) {
      _users[idx] = { ..._users[idx], role: payload.role, region: payload.region }
    }
  }
}

export async function deleteUser(userId: string): Promise<void> {
  try {
    await deleteJson(`/api/v1/admin/users/${userId}`)
  } catch {
    _users = _users.filter((u) => u.userId !== userId)
  }
}

export async function saveSystemConfig(_config: Record<string, unknown>): Promise<void> {
  try {
    await putJson('/api/v1/admin/config', _config)
  } catch {
    // mock success
  }
}