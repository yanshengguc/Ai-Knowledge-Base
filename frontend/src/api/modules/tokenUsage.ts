import request from '@/api/request'
import type { Result } from '@/types/api'

export interface UsageSection {
  totalTokens?: number
  totalCost?: number
  todayTokens?: number
  todayCost?: number
  monthTokens?: number
  monthCost?: number
  promptTokens?: number
  completionTokens?: number
}

export interface UsageTrendItem {
  day: string
  tokens: number
  cost: number
}

export interface TokenUsageSummary {
  chat: UsageSection
  embedding: UsageSection
  trend: UsageTrendItem[]
}

export function getTokenUsageSummary() {
  return request.get<unknown, Result<TokenUsageSummary>>('/token-usage/summary')
}
