import request from '@/api/request'
import type { GraphVO, Result } from '@/types/api'

/** 知识图谱:节点(条目+文件)+ 结构边(归属)+ 相似边(向量余弦 top2) */
export function getKnowledgeGraph() {
  return request.get<unknown, Result<GraphVO>>('/graph')
}
