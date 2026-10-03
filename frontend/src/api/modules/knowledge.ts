import request from '@/api/request'
import type {
  ChatResponse,
  FileContentVO,
  FileVO,
  KnowledgeDTO,
  KnowledgeDetailVO,
  KnowledgePageVO,
  KnowledgeVO,
  Result,
} from '@/types/api'

// 知识列表用 GET /knowledge(getKnowledgeList2,Tree.vue 建树与 SaveAsNoteDialog 存笔记下拉使用)
// 注意:POST /knowledge 是"新增知识"接口,曾有一个误传 {action:'list'} 的死代码函数
// 会把 list 请求当成新增知识(已删除,防止误用写脏数据)
export function getKnowledgeList2() {
  return request.get<unknown, Result<KnowledgeVO[]>>('/knowledge')
}

/** B-104 服务端分页:关键词/分类过滤下沉服务端;size 上限 50(默认 10) */
export function getKnowledgePage(params: {
  page: number
  size: number
  keyword?: string
  category?: string
}) {
  return request.get<unknown, Result<KnowledgePageVO>>('/knowledge/page', { params })
}

/** B-130 分类下拉候选:当前用户去重非空分类(替代为生成选项而拉取全量知识的口径) */
export function getKnowledgeCategories() {
  return request.get<unknown, Result<string[]>>('/knowledge/categories')
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

/** B-112 在线预览:md 文本类返回 content;pdf/docx content=null */
export function getFileContent(id: number) {
  return request.get<unknown, Result<FileContentVO>>(`/file/${id}/content`)
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
