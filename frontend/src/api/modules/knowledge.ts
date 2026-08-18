import request from '@/api/request'
import type {
  ChatResponse,
  KnowledgeDTO,
  KnowledgeDetailVO,
  KnowledgeVO,
  Result,
} from '@/types/api'

export function getKnowledgeList() {
  return request.post<unknown, Result<KnowledgeVO[]>>('/knowledge', { action: 'list' })
}

export function getKnowledgeList2() {
  return request.get<unknown, Result<KnowledgeVO[]>>('/knowledge')
}

export function getKnowledgeDetail(id: number) {
  return request.get<unknown, Result<KnowledgeDetailVO>>(`/knowledge/${id}`)
}

export function addKnowledge(data: KnowledgeDTO) {
  return request.post<unknown, Result>('/knowledge', data)
}

export function updateKnowledge(id: number, data: Partial<KnowledgeDTO>) {
  return request.put<unknown, Result>(`/knowledge/${id}`, data)
}

export function deleteKnowledge(id: number) {
  return request.delete<unknown, Result>(`/knowledge/${id}`)
}

export function uploadFile(knowledgeId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<unknown, Result>(`/file/upload/${knowledgeId}`, form)
}

export function chat(message: string) {
  return request.post<unknown, Result<ChatResponse>>('/chat', { message })
}

export function clearChat() {
  return request.post<unknown, Result>('/chat/clear')
}
