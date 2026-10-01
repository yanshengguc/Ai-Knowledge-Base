import request from '@/api/request'
import type { Result } from '@/types/api'

/**
 * B-114 Outline 大纲导航层(前端消费方,后端契约冻结在 OutlineController)。
 *  - GET  /api/outline/file/{fileId}          标题树(先序平坦列表,前端按 parentId 组装)
 *  - GET  /api/outline/node/{nodeId}          节点详情 + source_chunks 溯源
 *  - POST /api/outline/file/{fileId}/rebuild  补偿重建,返回节点数
 * 以下 VO 类型逐字段对齐后端 vo 包,不增不减。
 */

/** Outline 标题树(先序平坦列表 + 各节点关联切片数) */
export interface OutlineTreeVO {
  fileId: number
  fileName?: string
  nodeCount: number
  nodes: OutlineNodeVO[]
}

/** Outline 节点(不含正文,用于标题树导航) */
export interface OutlineNodeVO {
  id: number
  /** 根节点该字段为 null */
  parentId: number | null
  nodeIndex: number
  level: number
  title: string
  headingPath?: string
  sourceStartOffset?: number
  sourceEndOffset?: number
  /** 该节点关联的真实切片数;空父标题节点为 0(B 方案) */
  sourceChunkCount: number
}

/** source_chunks 溯源条目:指向真实 knowledge_chunk,preview 由服务端截断(200 字) */
export interface OutlineSourceChunkVO {
  chunkId: number
  chunkIndex: number
  contentLength: number
  preview?: string
}

/** Outline 节点详情:节点元信息 + source_chunks 溯源列表 */
export interface OutlineNodeDetailVO {
  id: number
  fileId: number
  nodeIndex: number
  level: number
  title: string
  headingPath?: string
  sourceChunks: OutlineSourceChunkVO[]
}

export function getOutlineTree(fileId: number) {
  return request.get<unknown, Result<OutlineTreeVO>>(`/outline/file/${fileId}`)
}

export function getOutlineNodeDetail(nodeId: number) {
  return request.get<unknown, Result<OutlineNodeDetailVO>>(`/outline/node/${nodeId}`)
}

/** 存量补偿:用 OSS 原文重建导航层,返回节点数 */
export function rebuildOutline(fileId: number) {
  return request.post<unknown, Result<number>>(`/outline/file/${fileId}/rebuild`)
}