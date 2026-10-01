import request from '@/api/request'
import type { Result } from '@/types/api'

// 管理端只读接口(2026-10 Sprint 3 MVP):概览 + 用户列表分页

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

export interface AdminUser {
  id: number
  username: string
  nickname?: string
}

export interface AdminUserPage {
  total: number
  page: number
  size: number
  list: AdminUser[]
}

export function getAdminOverview() {
  return request.get<unknown, Result<AdminOverview>>('/admin/overview')
}

export function getAdminUsers(params: { page: number; size: number; keyword?: string }) {
  return request.get<unknown, Result<AdminUserPage>>('/admin/users', { params })
}