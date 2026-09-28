<template>
  <div class="tree-page">
    <div class="tree-header">
      <div class="tree-heading">
        <h2 class="tree-title">{{ t('nav.tree') }}</h2>
        <span class="tree-sub">{{ t('tree.subtitle') }}</span>
      </div>
      <el-radio-group v-model="view" size="small" class="view-switch" :aria-label="t('tree.viewSwitch')">
        <el-radio-button value="tree">{{ t('tree.viewTree') }}</el-radio-button>
        <el-radio-button value="graph">{{ t('tree.viewGraph') }}</el-radio-button>
      </el-radio-group>
    </div>

    <el-card v-if="view === 'tree'" shadow="never" class="tree-card">
      <div class="tree-toolbar">
        <span class="tree-hint">{{ t('tree.expandHint') }}</span>
        <el-button size="small" :icon="Refresh" @click="refreshTree">
          {{ t('tree.refresh') }}
        </el-button>
      </div>
      <el-tree
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
          <span class="tree-node" :class="{ root: data.type === 'knowledge' }">
            <el-icon class="node-icon">
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
              {{ data.status }}
            </el-tag>
          </span>
        </template>
      </el-tree>
    </el-card>

    <div v-else class="tree-graph-wrap">
      <GraphPanel :show-header="false" />
    </div>

    <FilePreview ref="previewRef" />
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Document, Refresh } from '@element-plus/icons-vue'
import { getKnowledgeList2, getFileList } from '@/api/modules/knowledge'
import type { KnowledgeVO, FileVO } from '@/types/api'
import FilePreview from './components/FilePreview.vue'

// B-107: 网图第二视图按需加载(echarts 独立 chunk,进树页不背这份体积)
const GraphPanel = defineAsyncComponent(() => import('./GraphPanel.vue'))

const view = ref<'tree' | 'graph'>('tree')
const treeKey = ref(0)

interface TreeNode {
  key: string
  label: string
  type: 'knowledge' | 'file'
  knowledgeId?: number
  fileId?: number
  status?: string
  isLeaf?: boolean
}

const { t } = useI18n()
const router = useRouter()
const previewRef = ref<InstanceType<typeof FilePreview> | null>(null)

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
      resolve(
        list.map((k) => ({
          key: `k-${k.id}`,
          label: k.title,
          type: 'knowledge' as const,
          knowledgeId: k.id,
        })),
      )
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
    // 静默空节点会误导为"没有文件",必须显式反馈
    ElMessage.error(t('tree.loadFailed'))
    resolve([])
  }
}

function refreshTree() {
  treeKey.value += 1
}

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PROCESSING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

/** 知识节点点击进详情;文件节点点击直接预览原文(B-112,9/21 PO:点击对应位置看原文) */
function onNodeClick(data: TreeNode) {
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

  .view-switch {
    flex-shrink: 0;
  }
}

.tree-graph-wrap {
  width: 100%;
  min-width: 0;
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
}

@media (max-width: $bp-md) {
  .tree-header {
    flex-direction: column;
    gap: $space-3;

    .view-switch {
      align-self: flex-start;
    }
  }

  .tree-card .tree-toolbar {
    align-items: flex-start;

    .tree-hint {
      white-space: normal;
    }
  }
}
</style>
