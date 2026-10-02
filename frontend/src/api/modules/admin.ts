import request from '@/api/request'
import type { Result } from '@/types/api'

// 管理端接口:概览 + 用户列表(只读) + 用户写操作(B-116)

export interface AdminTokenUsage {
  totalTokens?: number
  totalCost?: number
  todayTokens?: number
  todayCost?: number
  chatTokens?: number
  embeddingTokens?: number
}

export interface AdminOverview {
  userCount: number
  knowledgeCount: number
  fileCount: number
  chunkCount: number
  /** 文件处理状态分布:SUCCESS / FAILED / PROCESSING -> 数量 */
  fileStatus: Record<string, number>
  tokenUsage: AdminTokenUsage
}

/** user.role 二值模型(B-116) */
export type AdminRole = 'admin' | 'user'

export interface AdminUser {
  id: number
  username: string
  nickname?: string
  /** admin / user;生效判据仍叠加后端 ADMIN_USERNAMES 白名单 */
  role?: AdminRole
}

export interface AdminUserPage {
  total: number
  page: number
  size: number
  list: AdminUser[]
}

export interface AdminUserCreateDTO {
  username: string
  password: string
  nickname?: string
  /** 省略时后端默认 user */
  role?: AdminRole
}

export function getAdminOverview() {
  return request.get<unknown, Result<AdminOverview>>('/admin/overview')
}

export function getAdminUsers(params: { page: number; size: number; keyword?: string }) {
  return request.get<unknown, Result<AdminUserPage>>('/admin/users', { params })
}

// ===== B-116 写操作 =====

/** 管理员建号 */
export function createAdminUser(data: AdminUserCreateDTO) {
  return request.post<unknown, Result<void>>('/admin/users', data)
}

/** 改角色 */
export function updateAdminUserRole(id: number, role: AdminRole) {
  return request.patch<unknown, Result<void>>(`/admin/users/${id}/role`, { role })
}

/** 重置密码 */
export function resetAdminUserPassword(id: number, password: string) {
  return request.put<unknown, Result<void>>(`/admin/users/${id}/password`, { password })
}

/** 删除用户 */
export function deleteAdminUser(id: number) {
  return request.delete<unknown, Result<void>>(`/admin/users/${id}`)
}