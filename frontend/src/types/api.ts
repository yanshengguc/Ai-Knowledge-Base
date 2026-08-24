// 后端 Result 包装
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

// 登录/注册请求
export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO extends LoginDTO {
  nickname?: string
}

// 知识
export interface KnowledgeVO {
  id: number
  title: string
  category?: string
  author?: string
  content?: string
  createTime?: string
  updateTime?: string
}

export interface KnowledgeDetailVO extends KnowledgeVO {
  content?: string
}

export interface KnowledgeDTO {
  title: string
  content: string
  category?: string
}

// 文件
export interface FileVO {
  id: number
  fileName?: string
  fileUrl?: string
  fileSize?: number
  knowledgeId?: number
  status?: string
  /** MIME 类型;笔记来源编码于此(text/markdown;source=ai-chat|manual) */
  fileType?: string
  updateTime?: string
}

// 聊天
export interface SearchResult {
  knowledgeId: number
  fileId?: number
  content: string
  score: number
  /** 来源文件名(检索出口填充,引用面板展示出处) */
  fileName?: string
}

export interface ChatResponse {
  answer: string
  references: SearchResult[]
}
