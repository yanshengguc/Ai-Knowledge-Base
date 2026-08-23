import request from '@/api/request'
import type {
  ChatResponse,
  FileVO,
  KnowledgeDTO,
  KnowledgeDetailVO,
  KnowledgeVO,
  Result,
} from '@/types/api'

// 知识列表用 GET /knowledge(getKnowledgeList2,List.vue 使用)
// 注意:POST /knowledge 是"新增知识"接口,曾有一个误传 {action:'list'} 的死代码函数
// 会把 list 请求当成新增知识(已删除,防止误用写脏数据)
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

export function uploadFile(knowledgeId: number, file: File, onProgress?: (percent: number) => void) {
  const form = new FormData()
  form.append('file', file)
  return request.post<unknown, Result>(`/file/upload/${knowledgeId}`, form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    },
  })
}

export function chat(message: string) {
  return request.post<unknown, Result<ChatResponse>>('/chat', { message })
}

export function clearChat() {
  return request.post<unknown, Result>('/chat/clear')
}

export function getFileById(id: number) {
  return request.get<unknown, Result<FileVO>>(`/file/${id}`)
}

export function getFileList(knowledgeId: number) {
  return request.get<unknown, Result<FileVO[]>>(`/file/list/${knowledgeId}`)
}

export function deleteFile(id: number) {
  return request.delete<unknown, Result<null>>(`/file/${id}`)
}

/** 写优先:在知识条目下新建 Markdown 笔记(内容同步向量化,立刻可检索);source=ai-chat 时标记 AI 来源 */
export function createNote(
  knowledgeId: number,
  data: { title: string; content: string; source?: string },
) {
  return request.post<unknown, Result>(`/knowledge/${knowledgeId}/note`, data)
}
