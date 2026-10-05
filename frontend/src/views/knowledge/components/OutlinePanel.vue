<template>
  <div class="outline-panel">
    <el-drawer v-model="visible" :size="size" :destroy-on-close="true">
      <template #header="{ titleId, titleClass }">
        <div class="op-header">
          <span
            :id="titleId"
            :class="titleClass"
            class="op-header-title"
            role="heading"
            aria-level="2"
          >
            {{ fileName || t('outline.title') }}
          </span>
          <el-button
            class="op-size-toggle"
            size="small"
            text
            :icon="fullscreen ? Fold : FullScreen"
            @click="toggle"
          >
            {{ fullscreen ? t('common.halfscreen') : t('common.fullscreen') }}
          </el-button>
        </div>
      </template>
      <div v-if="treeLoading" class="op-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('outline.loading') }}</span>
      </div>

      <!-- 失败态:请求异常(网络/权限/服务端错误),与真实空态严格分离 -->
      <div v-else-if="treeFailed" class="op-state">
        <el-empty :description="t('outline.loadFailed')">
          <el-button type="primary" @click="reloadTree">{{ t('common.retry') }}</el-button>
        </el-empty>
      </div>

      <!-- 真实空态:请求成功但该文件没有可用标题 -->
      <div v-else-if="!treeData.length" class="op-state">
        <el-empty :description="t('outline.empty')">
          <el-button
            type="primary"
            :loading="rebuilding"
            :disabled="activeFileId == null"
            @click="onRebuild"
          >
            {{ t('outline.rebuild') }}
          </el-button>
        </el-empty>
      </div>

      <div v-else class="op-body">
        <aside class="op-tree">
          <div class="op-tree-head">
            <span class="op-count">{{ t('outline.nodeCount', { n: totalCount }) }}</span>
            <el-button size="small" :icon="Refresh" :loading="rebuilding" @click="onRebuild">
              {{ t('outline.rebuild') }}
            </el-button>
          </div>
          <el-tree
            :data="treeData"
            :props="treeProps"
            node-key="id"
            highlight-current
            :expand-on-click-node="false"
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="op-node" :class="`lv-${data.level}`">
                <span class="op-node-label">{{ data.label }}</span>
                <span v-if="data.sourceChunkCount > 0" class="op-node-badge">
                  {{ data.sourceChunkCount }}
                </span>
              </span>
            </template>
          </el-tree>
        </aside>

        <section class="op-detail">
          <div v-if="activeNodeId == null" class="op-state subtle">
            <span>{{ t('outline.noSelection') }}</span>
          </div>

          <div v-else-if="detailLoading" class="op-state">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ t('outline.detailLoading') }}</span>
          </div>

          <!-- 详情失败态 -->
          <div v-else-if="detailFailed" class="op-state">
            <el-empty :description="t('outline.detailFailed')">
              <el-button type="primary" @click="loadDetail(activeNodeId)">
                {{ t('common.retry') }}
              </el-button>
            </el-empty>
          </div>

          <template v-else-if="detail">
            <header class="op-detail-head">
              <h3 class="op-detail-title">{{ detail.title || t('outline.title') }}</h3>
              <div class="op-detail-meta">
                <el-tag size="small" type="info" effect="plain">
                  {{ t('outline.level') }} {{ detail.level }}
                </el-tag>
                <span v-if="detail.headingPath" class="op-path">{{ detail.headingPath }}</span>
              </div>
            </header>

            <div class="op-chunks">
              <div class="op-chunks-head">
                <span class="op-chunks-title">{{ t('outline.sourceChunks') }}</span>
                <span class="op-chunks-count">{{ detail.sourceChunks.length }}</span>
              </div>

              <!-- 真实空态:节点存在但其下无关联切片 -->
              <el-empty
                v-if="!detail.sourceChunks.length"
                :description="t('outline.chunkEmpty')"
                :image-size="72"
              />

              <button
                v-for="chunk in detail.sourceChunks"
                :key="chunk.chunkId"
                type="button"
                class="op-chunk"
                @click="openSource"
              >
                <span class="op-chunk-head">
                  <span class="op-chunk-index">{{ t('outline.chunkIndexLabel', { n: chunk.chunkIndex }) }}</span>
                  <span class="op-chunk-len">{{ t('outline.chunkLength', { n: chunk.contentLength }) }}</span>
                </span>
                <span class="op-chunk-preview">{{ chunk.preview || t('upload.noContent') }}</span>
                <span class="op-chunk-action">{{ t('outline.viewOriginal') }}</span>
              </button>
            </div>
          </template>
          <!-- 兜底:详情接口成功但 data=null,避免右侧整块空白无提示 -->
          <div v-else class="op-state">
            <el-empty :description="t('outline.chunkEmpty')">
              <el-button type="primary" @click="loadDetail(activeNodeId)">
                {{ t('common.retry') }}
              </el-button>
            </el-empty>
          </div>
        </section>
      </div>
    </el-drawer>

    <!-- 复用 B-112 原文预览:点击溯源项打开该文件原文(不改 FilePreview 既有契约) -->
    <FilePreview ref="previewRef" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Fold, FullScreen, Loading, Refresh } from '@element-plus/icons-vue'
import { getOutlineNodeDetail, getOutlineTree, rebuildOutline } from '@/api/outline'
import type { OutlineNodeDetailVO, OutlineNodeVO } from '@/api/outline'
import FilePreview from './FilePreview.vue'
import { useDrawerSize } from '@/composables/useDrawerSize'

/**
 * B-114 文档大纲面板:左侧标题树导航 + 右侧节点详情与 source_chunks 溯源。
 * 用法:const outline = ref(); outline.value.open({ id, fileName })  (与 FilePreview 同一套 open 约定)
 * 空态语义:treeFailed / detailFailed 为"请求失败";无标题 / 无切片为"真实空",两者分开渲染。
 */
const { t } = useI18n()

// 半屏/全屏由用户决定:桌面默认半屏、移动端默认全屏,偏好持久化、与原文预览共享
const { fullscreen, size, toggle } = useDrawerSize('min(900px, 94vw)')

interface OutlineTreeNode extends OutlineNodeVO {
  label: string
  children: OutlineTreeNode[]
}

const visible = ref(false)
const activeFileId = ref<number | null>(null)
const fileName = ref('')

const treeLoading = ref(false)
const treeFailed = ref(false)
const treeData = ref<OutlineTreeNode[]>([])
const totalCount = ref(0)
const treeSeq = ref(0)

const activeNodeId = ref<number | null>(null)
const detailLoading = ref(false)
const detailFailed = ref(false)
const detail = ref<OutlineNodeDetailVO | null>(null)
const detailSeq = ref(0)

const rebuilding = ref(false)
const previewRef = ref<InstanceType<typeof FilePreview> | null>(null)

const treeProps = {
  label: 'label',
  children: 'children',
}

/** 后端返回先序平坦列表,按 parentId 组装成 el-tree 需要的树 */
function buildTree(nodes: OutlineNodeVO[]): OutlineTreeNode[] {
  const map = new Map<number, OutlineTreeNode>()
  const roots: OutlineTreeNode[] = []
  for (const n of nodes) {
    map.set(n.id, {
      ...n,
      label: n.title || n.headingPath || `#${n.nodeIndex}`,
      children: [],
    })
  }
  for (const n of nodes) {
    const node = map.get(n.id)
    if (!node) continue
    const parent = n.parentId != null ? map.get(n.parentId) : undefined
    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

function open(file: { id: number; fileName?: string }) {
  activeFileId.value = file.id
  fileName.value = file.fileName || `#${file.id}`
  visible.value = true
  resetDetail()
  void loadTree(file.id)
}

/** 关闭/切换节点时递增序号,丢弃在途响应,避免竞态覆盖 */
function resetDetail() {
  detailSeq.value += 1
  activeNodeId.value = null
  detail.value = null
  detailLoading.value = false
  detailFailed.value = false
}

async function loadTree(fileId: number) {
  const seq = ++treeSeq.value
  treeLoading.value = true
  treeFailed.value = false
  try {
    const res = await getOutlineTree(fileId)
    if (seq !== treeSeq.value) return
    const vo = res.data
    const nodes = (vo?.nodes || []) as OutlineNodeVO[]
    treeData.value = buildTree(nodes)
    totalCount.value = vo?.nodeCount ?? nodes.length
  } catch {
    if (seq !== treeSeq.value) return
    treeData.value = []
    totalCount.value = 0
    treeFailed.value = true
    ElMessage.error(t('outline.loadFailed'))
  } finally {
    if (seq === treeSeq.value) {
      treeLoading.value = false
    }
  }
}

function reloadTree() {
  const id = activeFileId.value
  if (id == null) {
    treeFailed.value = true
    return
  }
  resetDetail()
  void loadTree(id)
}

function onNodeClick(data: OutlineTreeNode) {
  void loadDetail(data.id)
}

async function loadDetail(nodeId: number | null) {
  if (nodeId == null) return
  const seq = ++detailSeq.value
  activeNodeId.value = nodeId
  detailLoading.value = true
  detailFailed.value = false
  detail.value = null
  try {
    const res = await getOutlineNodeDetail(nodeId)
    if (seq !== detailSeq.value) return
    const vo = res.data
    detail.value = vo ? { ...vo, sourceChunks: vo.sourceChunks || [] } : null
  } catch {
    if (seq !== detailSeq.value) return
    detailFailed.value = true
    ElMessage.error(t('outline.detailFailed'))
  } finally {
    if (seq === detailSeq.value) {
      detailLoading.value = false
    }
  }
}

async function onRebuild() {
  const id = activeFileId.value
  if (id == null || rebuilding.value) return
  try {
    await ElMessageBox.confirm(t('outline.rebuildConfirm'), t('outline.rebuild'), {
      type: 'warning',
    })
  } catch {
    return
  }
  rebuilding.value = true
  try {
    const res = await rebuildOutline(id)
    const n = res.data ?? 0
    ElMessage.success(t('outline.rebuildSuccess', { n }))
    resetDetail()
    await loadTree(id)
  } catch {
    // 拦截器已提示
  } finally {
    rebuilding.value = false
  }
}

/** 打开溯源项:跳转该文件的原文预览(复用既有 FilePreview) */
function openSource() {
  const id = activeFileId.value ?? detail.value?.fileId ?? null
  if (id == null) return
  previewRef.value?.open({ id, fileName: fileName.value })
}

defineExpose({ open })
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.op-header {
  display: flex;
  align-items: center;
  gap: $space-2;
  min-width: 0;
  width: 100%;
}

.op-header-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.op-size-toggle {
  flex-shrink: 0;
}

.op-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  min-height: 240px;
  height: 100%;
  color: $color-text-secondary;

  &.subtle {
    font-size: $font-size-sm;
  }
}

.op-body {
  display: flex;
  gap: $space-3;
  height: 100%;
  min-height: 340px;
}

.op-tree {
  display: flex;
  flex: 0 0 280px;
  flex-direction: column;
  min-width: 0;
  padding-right: $space-2;
  border-right: 1px solid $color-border;

  .op-tree-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-2;
    margin-bottom: $space-2;

    .op-count {
      color: $color-text-secondary;
      font-size: $font-size-xs;
    }
  }

  :deep(.el-tree) {
    flex: 1;
    overflow: auto;
  }
}

.op-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-2;
  width: 100%;
  min-width: 0;

  .op-node-label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .op-node-badge {
    flex-shrink: 0;
    min-width: 18px;
    padding: 0 5px;
    border-radius: $radius-sm;
    background: $color-bg;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    line-height: 18px;
    text-align: center;
  }

  &.lv-1 .op-node-label {
    font-weight: 600;
  }
}

.op-detail {
  flex: 1;
  min-width: 0;
  overflow: auto;
}

.op-detail-head {
  margin-bottom: $space-3;

  .op-detail-title {
    margin: 0 0 $space-1;
    font-size: $font-size-md;
    line-height: 1.4;
  }

  .op-detail-meta {
    display: flex;
    align-items: center;
    gap: $space-2;
    min-width: 0;
  }

  .op-path {
    min-width: 0;
    overflow: hidden;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.op-chunks {
  .op-chunks-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $space-2;

    .op-chunks-title {
      font-weight: 600;
    }

    .op-chunks-count {
      color: $color-text-secondary;
      font-size: $font-size-xs;
    }
  }
}

.op-chunk {
  display: block;
  width: 100%;
  margin-bottom: $space-2;
  padding: $space-2 $space-3;
  border: 1px solid $color-border;
  border-radius: $radius-sm;
  background: $color-bg-card;
  text-align: left;
  cursor: pointer;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:hover {
    border-color: $color-primary;
    box-shadow: $shadow-card;
  }

  &:focus-visible {
    outline: 2px solid $color-primary;
    outline-offset: 2px;
  }

  .op-chunk-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-2;
  }

  .op-chunk-index {
    color: $color-primary;
    font-size: $font-size-xs;
    font-weight: 600;
  }

  .op-chunk-len {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }

  .op-chunk-preview {
    display: block;
    margin: $space-1 0;
    color: $color-text;
    font-size: $font-size-sm;
    line-height: 1.6;
    word-break: break-word;
  }

  .op-chunk-action {
    color: $color-primary;
    font-size: $font-size-xs;
  }
}

@media (max-width: $bp-md) {
  .op-body {
    flex-direction: column;
  }

  .op-tree {
    flex: 0 0 auto;
    max-height: 240px;
    padding-right: 0;
    padding-bottom: $space-2;
    border-right: none;
    border-bottom: 1px solid $color-border;
  }
}
</style>