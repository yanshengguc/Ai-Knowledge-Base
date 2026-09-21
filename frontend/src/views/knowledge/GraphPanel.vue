<template>
  <div class="graph-page">
    <div v-if="showHeader" class="graph-header">
      <h2 class="graph-title">{{ t('nav.graph') }}</h2>
      <span class="graph-sub">{{ t('graph.subtitle') }}</span>
      <el-button class="refresh-btn" size="small" :icon="Refresh" @click="loadGraph">
        {{ t('graph.refresh') }}
      </el-button>
    </div>

    <div class="graph-body">
      <el-card shadow="never" class="graph-card">
        <div v-if="loading" class="graph-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('graph.loading') }}</span>
        </div>
        <div v-else-if="loadFailed" class="graph-empty">
          <el-empty :description="t('graph.loadFailed')">
            <el-button type="primary" @click="loadGraph">{{ t('graph.retry') }}</el-button>
          </el-empty>
        </div>
        <div v-else-if="!hasData" class="graph-empty">
          <el-empty :description="t('graph.empty')" />
        </div>
        <div v-show="hasData && !loading" ref="chartRef" class="chart-container" />
      </el-card>

      <el-card v-if="selected" shadow="never" class="side-card">
        <template #header>
          <div class="side-header">
            <el-icon class="side-icon">
              <Collection v-if="selected.type === 'knowledge'" />
              <Document v-else />
            </el-icon>
            <span class="side-title">{{ selected.name }}</span>
          </div>
        </template>
        <div class="side-body">
          <div class="side-row">
            <span class="side-label">{{ t('graph.nodeType') }}</span>
            <el-tag size="small" :type="selected.type === 'knowledge' ? 'primary' : 'info'">
              {{ selected.type === 'knowledge' ? t('graph.knowledgeNode') : t('graph.fileNode') }}
            </el-tag>
          </div>
          <div v-if="selected.type === 'file' && selected.status" class="side-row">
            <span class="side-label">{{ t('graph.status') }}</span>
            <el-tag size="small" :type="statusTagType(selected.status)">{{ selected.status }}</el-tag>
          </div>
          <div v-if="selectedCategory" class="side-row">
            <span class="side-label">{{ t('graph.category') }}</span>
            <span class="side-value">{{ selectedCategory }}</span>
          </div>
          <div class="side-row">
            <span class="side-label">{{ t('graph.relatedCount') }}</span>
            <span class="side-value">{{ relatedCount(selected.id) }}</span>
          </div>
          <div class="side-actions">
            <el-button type="primary" size="small" @click="goDetail">
              {{ t('graph.viewDetail') }}
            </el-button>
            <el-button
              v-if="selected.type === 'file'"
              size="small"
              :icon="View"
              @click="openOriginal"
            >
              {{ t('filePreview.viewOriginal') }}
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <div class="graph-legend">
      <span class="legend-item">
        <span class="legend-dot dot-knowledge" />{{ t('graph.knowledgeNode') }}
      </span>
      <span class="legend-item">
        <span class="legend-dot dot-file" />{{ t('graph.fileNode') }}
      </span>
      <span class="legend-item">
        <span class="legend-line line-structure" />{{ t('graph.structureEdge') }}
      </span>
      <span class="legend-item">
        <span class="legend-line line-similar" />{{ t('graph.similarEdge') }}
      </span>
    </div>

    <FilePreview ref="previewRef" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Document, Loading, Refresh, View } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import { getKnowledgeGraph } from '@/api/modules/graph'
import type { GraphEdgeVO, GraphNodeVO } from '@/types/api'
import FilePreview from './components/FilePreview.vue'

/** 独立 /graph 页(含标题)与 /tree 页第二视图(仅图体)共用;嵌入时数据随挂载自动加载,切走即销毁重置 */
withDefaults(defineProps<{ showHeader?: boolean }>(), { showHeader: true })

echarts.use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const loadFailed = ref(false)
const nodes = ref<GraphNodeVO[]>([])
const edges = ref<GraphEdgeVO[]>([])
const selected = ref<GraphNodeVO | null>(null)

const chartRef = ref<HTMLDivElement>()
let chart: EChartsType | null = null
let resizeHandler: (() => void) | null = null

/** 按条目配色(Obsidian 文件夹分色):8 色循环 */
const PALETTE = [
  '#2563EB', '#0891B2', '#16A34A', '#D97706',
  '#7C3AED', '#DB2777', '#DC2626', '#4F46E5',
]

const knowledgeNodes = computed(() => nodes.value.filter((n) => n.type === 'knowledge'))
const hasData = computed(() => nodes.value.length > 0)
const selectedCategory = computed(() => {
  if (!selected.value) return ''
  const k = knowledgeNodes.value.find((x) => x.group === selected.value?.group)
  return k?.category || k?.name || ''
})

/** 邻居数:结构边 + 相似边(点击节点侧栏展示关联度) */
function relatedCount(id: string): number {
  return edges.value.filter((e) => e.source === id || e.target === id).length
}

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PROCESSING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function goDetail() {
  if (selected.value) {
    router.push(`/knowledge/${selected.value.group}`)
  }
}

/** B-112: 文件节点侧栏"查看原文"——节点 id 形如 "f-65",解析出数字文件 id */
const previewRef = ref<InstanceType<typeof FilePreview> | null>(null)

function openOriginal() {
  const node = selected.value
  if (!node || node.type !== 'file') return
  const fileId = Number.parseInt(node.id.slice(2), 10)
  if (Number.isNaN(fileId)) {
    ElMessage.error(t('filePreview.loadFailed'))
    return
  }
  previewRef.value?.open({ id: fileId, fileName: node.name })
}

async function loadGraph() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await getKnowledgeGraph()
    const vo = res.data
    nodes.value = vo?.nodes ?? []
    edges.value = vo?.edges ?? []
    selected.value = null
    await nextTick()
    renderChart()
  } catch {
    loadFailed.value = true
    ElMessage.error(t('graph.loadFailed'))
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    resizeHandler = () => chart?.resize()
    window.addEventListener('resize', resizeHandler)
    chart.on('click', (params) => {
      // ECElementEvent.data 实际载荷是 setOption 时的节点对象(含我们附加的 node 字段)
      const node = (params as unknown as { data?: { node?: GraphNodeVO } }).data?.node
      if (node) selected.value = node
    })
  }

  // 条目 → category 索引(配色/图例)
  const categories = knowledgeNodes.value.map((k, i) => ({
    name: k.name,
    itemStyle: { color: PALETTE[i % PALETTE.length] },
  }))
  const catIndex = new Map<number, number>()
  knowledgeNodes.value.forEach((k, i) => catIndex.set(k.group, i))

  const chartNodes = nodes.value.map((n) => ({
    ...n,
    node: n,
    category: catIndex.get(n.group) ?? 0,
    symbolSize: n.type === 'knowledge' ? 34 : 14,
    label: {
      show: n.type === 'knowledge',
      position: 'bottom' as const,
      fontSize: 12,
      color: '#475569',
      formatter: (p: { name: string }) =>
        p.name.length > 14 ? p.name.slice(0, 14) + '…' : p.name,
    },
  }))

  const chartEdges = edges.value.map((e) => ({
    ...e,
    lineStyle:
      e.type === 'structure'
        ? { color: '#CBD5E1', width: 1.5, type: 'solid' as const, curveness: 0 }
        : {
            color: '#2563EB',
            width: 0.8 + e.weight * 2.2,
            type: 'dashed' as const,
            opacity: 0.35 + e.weight * 0.5,
            curveness: 0.18,
          },
  }))

  chart.setOption({
    tooltip: {
      formatter: (p: { data?: { node?: GraphNodeVO } }) =>
        p.data?.node ? `${p.data.node.name}` : '',
    },
    legend: categories.length
      ? [{ data: categories.map((c) => c.name), type: 'scroll', bottom: 8, icon: 'circle' }]
      : [],
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        data: chartNodes,
        links: chartEdges,
        categories,
        force: {
          repulsion: 260,
          edgeLength: [60, 140],
          gravity: 0.12,
          layoutAnimation: true,
        },
        emphasis: {
          // Obsidian 式邻居高亮:点击后聚焦相邻节点,其余淡出
          focus: 'adjacency',
          lineStyle: { width: 3 },
        },
        scaleLimit: { min: 0.4, max: 4 },
      },
    ],
  })
}

onMounted(loadGraph)
onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  chart?.dispose()
  chart = null
})
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.graph-page {
  max-width: 1100px;
  margin: 0 auto;
}

.graph-header {
  display: flex;
  align-items: baseline;
  gap: $space-3;
  margin-bottom: $space-4;

  .graph-title {
    margin: 0;
    font-size: $font-size-lg;
  }

  .graph-sub {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  .refresh-btn {
    margin-left: auto;
  }
}

.graph-body {
  display: flex;
  gap: $space-4;
  align-items: stretch;
}

.graph-card {
  flex: 1;
  min-width: 0;

  :deep(.el-card__body) {
    padding: $space-2;
  }
}

.chart-container {
  width: 100%;
  height: 560px;
}

.graph-loading,
.graph-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 560px;
  gap: $space-2;
  color: $color-text-secondary;
}

.side-card {
  width: 260px;
  flex-shrink: 0;

  .side-header {
    display: flex;
    align-items: center;
    gap: $space-2;
    min-width: 0;

    .side-icon {
      color: $color-primary;
    }

    .side-title {
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .side-body {
    display: flex;
    flex-direction: column;
    gap: $space-3;
  }

  .side-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $space-2;

    .side-label {
      color: $color-text-secondary;
      font-size: $font-size-sm;
      flex-shrink: 0;
    }

    .side-value {
      font-size: $font-size-sm;
      text-align: right;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .side-actions {
    display: flex;
    gap: $space-2;
  }
}

.graph-legend {
  display: flex;
  gap: $space-6;
  margin-top: $space-3;
  padding: 0 $space-2;
  flex-wrap: wrap;

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: $space-2;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }

  .legend-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
  }

  .dot-knowledge {
    background: $color-primary;
  }

  .dot-file {
    background: $color-text-muted;
  }

  .legend-line {
    display: inline-block;
    width: 22px;
    height: 0;
  }

  .line-structure {
    border-top: 2px solid #cbd5e1;
  }

  .line-similar {
    border-top: 2px dashed $color-primary;
  }
}

@media (max-width: 768px) {
  .graph-body {
    flex-direction: column;
  }

  .side-card {
    width: 100%;
  }

  .chart-container {
    height: 420px;
  }
}
</style>
