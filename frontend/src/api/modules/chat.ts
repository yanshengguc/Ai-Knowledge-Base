import request from '@/api/request'
import type { Result } from '@/types/api'

export interface ChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export function getChatHistory() {
  return request.get<unknown, Result<ChatHistoryItem[]>>('/chat/history')
}
