<template>
  <div class="tree-page">
    <div class="tree-header">
      <h2 class="tree-title">{{ t('nav.tree') }}</h2>
      <span class="tree-sub">{{ t('tree.subtitle') }}</span>
      <el-radio-group v-model="view" size="small" class="view-switch">
        <el-radio-button value="tree">{{ t('tree.viewTree') }}</el-radio-button>
        <el-radio-button value="graph">{{ t('tree.viewGraph') }}</el-radio-button>
      </el-radio-group>
    </div>

    <el-card v-if="view === 'tree'" shadow="never" class="tree-card">
      <el-tree
        :props="treeProps"
        :load="loadNode"
        lazy
        node-key="key"
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
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Document } from '@element-plus/icons-vue'
import { getKnowledgeList2, getFileList } from '@/api/modules/knowledge'
import type { KnowledgeVO, FileVO } from '@/types/api'

// B-107: 网图第二视图按需加载(echarts 独立 chunk,进树页不背这份体积)
const GraphPanel = defineAsyncComponent(() => import('./GraphPanel.vue'))

const view = ref<'tree' | 'graph'>('tree')

interface TreeNode {
  key: string
  label: string
  type: 'knowledge' | 'file'
  knowledgeId?: number
  status?: string
  isLeaf?: boolean
}

const { t } = useI18n()
const router = useRouter()

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

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PROCESSING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

/** 知识节点/文件节点点击均进入知识详情(文件无独立详情页,归属关系为 知识→文件) */
function onNodeClick(data: TreeNode) {
  if (data.knowledgeId != null) {
    router.push(`/knowledge/${data.knowledgeId}`)
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.tree-page {
  max-width: 860px;
  margin: 0 auto;
}

.tree-header {
  display: flex;
  align-items: baseline;
  gap: $space-3;
  margin-bottom: $space-4;

  .tree-title {
    margin: 0;
    font-size: $font-size-lg;
  }

  .tree-sub {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  .view-switch {
    margin-left: auto;
    align-self: center;
  }
}

.tree-graph-wrap {
  min-width: 0;
}

.tree-card {
  :deep(.el-card__body) {
    padding: $space-3;
  }

  :deep(.el-tree-node__content) {
    height: 32px;
  }
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: $space-2;
  min-width: 0;

  .node-icon {
    color: $color-primary;
    flex-shrink: 0;
  }

  .node-label {
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
</style>
