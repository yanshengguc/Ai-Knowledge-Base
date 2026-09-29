<template>
  <div class="tree-page">
    <div class="tree-header">
      <div class="tree-heading">
        <h2 class="tree-title">{{ t('nav.tree') }}</h2>
        <span class="tree-sub">{{ t('tree.subtitle') }}</span>
      </div>
    </div>

    <el-card shadow="never" class="tree-card">
      <div class="tree-toolbar">
        <span class="tree-hint">{{ t('tree.expandHint') }}</span>
        <el-button size="small" :icon="Refresh" @click="refreshTree">
          {{ t('tree.refresh') }}
        </el-button>
      </div>
      <el-tree
        ref="treeRef"
        :key="treeKey"
        :props="treeProps"
        :load="loadNode"
        lazy
        node-key="key"
        :empty-text="t('tree.empty')"
        :expand-on-click-node="false"
        highlight-current
        @node-click="onNodeClick"
      >
        <template #default="{ data }">
          <span v-if="data.type === 'error'" class="tree-node error-node">
            <el-icon class="node-icon"><WarningFilled /></el-icon>
            <span class="node-label">{{ data.label }}</span>
            <el-button link type="danger" size="small" @click.stop="refreshTree">
              {{ t('common.retry') }}
            </el-button>
          </span>
          <span v-else class="tree-node" :class="{ root: data.type === 'knowledge' }">
            <el-icon class="node-icon" :class="{ 'file-icon': data.type === 'file' }">
              <Collection v-if="data.type === 'knowledge'" />
              <Document v-else />
            </el-icon>
            <span class="node-label">{{ data.label }}</span>
            <el-tag
              v-if="data.type === 'file' && data.status"
              size="small"
              :type="statusTagType(data.status)"
              class="node-tag"
            >
              {{ statusLabel(data.status) }}
            </el-tag>
          </span>
        </template>
      </el-tree>
    </el-card>

    <FilePreview ref="previewRef" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Document, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { getKnowledgeList2, getFileList } from '@/api/modules/knowledge'
import type { KnowledgeVO, FileVO } from '@/types/api'
import FilePreview from './components/FilePreview.vue'

const treeKey = ref(0)
const rootNodes = ref<TreeNode[]>([])
const rootLoaded = ref(false)

interface TreeNode {
  key: string
  label: string
  type: 'knowledge' | 'file' | 'error'
  knowledgeId?: number
  fileId?: number
  status?: string
  isLeaf?: boolean
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const previewRef = ref<InstanceType<typeof FilePreview> | null>(null)

type TreeRef = {
  setCurrentKey: (key: string, shouldAutoExpandParent?: boolean) => void
  getNode: (key: string) => { expand: () => void } | undefined
}

const treeRef = ref<TreeRef | null>(null)

function queryKnowledgeId(): number | null {
  const raw = route.query.knowledgeId
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

async function focusKnowledgeNode(nodes: TreeNode[] = rootNodes.value) {
  const knowledgeId = queryKnowledgeId()
  if (knowledgeId == null || (!rootLoaded.value && nodes.length === 0)) return

  const target = nodes.find((node) => node.knowledgeId === knowledgeId)
  if (!target) {
    ElMessage.warning(t('tree.knowledgeNotFound'))
    return
  }

  await nextTick()
  treeRef.value?.setCurrentKey(target.key, true)
  treeRef.value?.getNode(target.key)?.expand()
}

watch(() => route.query.knowledgeId, () => {
  void focusKnowledgeNode()
})

const treeProps = {
  label: 'label',
  isLeaf: (data: TreeNode) => data.isLeaf === true,
}

/** 懒加载:根层=知识条目;展开=该条目下的文件(复用现有接口,零后端改动) */
async function loadNode(node: unknown, resolve: (data: TreeNode[]) => void) {
  const n = node as { level: number; data?: TreeNode }
  try {
    if (n.level === 0) {
      const res = await getKnowledgeList2()
      const list = (res.data || []) as KnowledgeVO[]
      const nodes = list.map((k) => ({
        key: `k-${k.id}`,
        label: k.title,
        type: 'knowledge' as const,
        knowledgeId: k.id,
      }))
      rootNodes.value = nodes
      rootLoaded.value = true
      resolve(nodes)
      void focusKnowledgeNode(nodes)
    } else if (n.data?.type === 'knowledge' && n.data.knowledgeId != null) {
      const res = await getFileList(n.data.knowledgeId)
      const files = (res.data || []) as FileVO[]
      resolve(
        files.map((f) => ({
          key: `f-${f.id}`,
          label: f.fileName || `#${f.id}`,
          type: 'file' as const,
          knowledgeId: n.data?.knowledgeId,
          fileId: f.id,
          status: f.status,
          isLeaf: true,
        })),
      )
    } else {
      resolve([])
    }
  } catch {
    const errorKey = n.level === 0
      ? 'tree-load-error'
      : `file-list-load-error-${n.data?.knowledgeId ?? 'unknown'}`
    ElMessage.error(t('tree.loadFailed'))
    resolve([{
      key: errorKey,
      label: t('tree.nodeLoadFailed'),
      type: 'error' as const,
      isLeaf: true,
    }])
  }
}

function refreshTree() {
  rootNodes.value = []
  rootLoaded.value = false
  treeKey.value += 1
}

function statusLabel(status?: string): string {
  if (status === 'SUCCESS') return t('upload.success')
  if (status === 'FAILED') return t('upload.failed')
  return t('upload.processing')
}

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PROCESSING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

/** 知识节点点击进详情;文件节点点击直接预览原文(B-112,9/21 PO:点击对应位置看原文) */
function onNodeClick(data: TreeNode) {
  if (data.type === 'error') {
    refreshTree()
    return
  }
  if (data.fileId != null) {
    previewRef.value?.open({ id: data.fileId, fileName: data.label })
    return
  }
  if (data.knowledgeId != null) {
    router.push(`/knowledge/${data.knowledgeId}`)
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.tree-page {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding-bottom: $space-6;
}

.tree-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $space-4;
  margin-bottom: $space-4;

  .tree-heading {
    min-width: 0;
  }

  .tree-title {
    margin: 0;
    font-size: $font-size-lg;
    line-height: 1.35;
  }

  .tree-sub {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-sm;
    line-height: 1.5;
  }
}

.tree-card {
  min-height: 360px;

  :deep(.el-card__body) {
    min-height: 360px;
    padding: $space-3;
  }

  .tree-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-3;
    min-height: 36px;
    margin-bottom: $space-2;
    padding-bottom: $space-2;
    border-bottom: 1px solid $color-border;

    .tree-hint {
      min-width: 0;
      overflow: hidden;
      color: $color-text-secondary;
      font-size: $font-size-xs;
      line-height: 1.4;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  :deep(.el-tree) {
    width: 100%;
  }

  :deep(.el-tree-node__content) {
    width: 100%;
    height: 36px;
  }
}

.tree-node {
  display: flex;
  align-items: center;
  gap: $space-2;
  width: 100%;
  min-width: 0;
  padding-right: $space-2;

  .node-icon {
    color: $color-primary;
    flex-shrink: 0;

    &.file-icon {
      color: $color-secondary;
    }
  }

  .node-label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-tag {
    flex-shrink: 0;
  }

  &.root .node-label {
    font-weight: 600;
  }

  &.error-node {
    color: $color-danger;

    .node-icon {
      color: $color-danger;
    }
  }
}

@media (max-width: $bp-md) {
  .tree-card .tree-toolbar {
    align-items: flex-start;

    .tree-hint {
      white-space: normal;
    }
  }
}
</style>
