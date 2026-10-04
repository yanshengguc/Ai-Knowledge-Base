<template>
  <div v-if="detail" class="detail">
    <div class="detail-header">
      <el-button :icon="ArrowLeft" text @click="router.push('/knowledge')">{{ t('common.back') }}</el-button>
      <h2>{{ detail.title }}</h2>
      <el-tag v-if="detail.category" size="small" effect="plain">{{ detail.category }}</el-tag>
      <el-button type="primary" text :icon="Edit" @click="editVisible = true">{{ t('knowledge.edit') }}</el-button>
      <el-button type="success" text :icon="EditPen" @click="noteVisible = true">{{ t('knowledge.createNote') }}</el-button>
    </div>

    <div ref="detailContentRef" class="detail-content markdown-body" v-html="renderMarkdown(detail.content || t('upload.noContent'))" />

    <!-- 已有文件列表(删除后通知父级同步列表与上传轮询) -->
    <FileListPanel
      :files="fileList"
      :load-error="fileListLoadFailed"
      @deleted="onFileDeleted"
      @retry="loadFileList"
    />

    <!-- 文件上传(选择/校验/轮询内聚,处理完成通知父级刷新列表) -->
    <FileUploadPanel ref="uploadPanelRef" :knowledge-id="detail.id" @file-processed="loadFileList" />
  </div>
  <el-skeleton v-else-if="!loadFailed" animated :rows="6" />
  <!-- 加载失败(权限不足/不存在):给明确反馈 + 出口,不再永远骨架屏 -->
  <div v-else class="detail-error">
    <el-empty :description="t('knowledge.loadFailed')" />
    <div class="detail-error-actions">
      <el-button @click="router.push('/knowledge')">{{ t('common.back') }}</el-button>
      <el-button type="primary" @click="loadDetail">{{ t('common.retry') }}</el-button>
    </div>
  </div>

  <!-- 编辑弹窗(保存后父级刷新详情) -->
  <EditKnowledgeDialog v-if="detail" v-model:visible="editVisible" :detail="detail" @saved="reloadDetail" />

  <!-- 新建笔记弹窗(写优先:内容同步向量化,立刻可检索) -->
  <NoteCreateDialog v-if="detail" v-model:visible="noteVisible" :knowledge-id="detail.id" @created="loadFileList" />

  <!-- B-110 选中文本 → 在知识库定位 -->
  <SelectionLocateButton
    :visible="selVisible"
    :x="selX"
    :top="selTop"
    :bottom="selBottom"
    :text="selText"
    @locate="onLocate"
  />
  <KnowledgeLocatePanel ref="locatePanelRef" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit, EditPen } from '@element-plus/icons-vue'
import { getFileList, getKnowledgeDetail } from '@/api/modules/knowledge'
import type { FileVO, KnowledgeDetailVO } from '@/types/api'
import { useI18n } from 'vue-i18n'
import FileListPanel from './components/FileListPanel.vue'
import FileUploadPanel from './components/FileUploadPanel.vue'
import EditKnowledgeDialog from './components/EditKnowledgeDialog.vue'
import NoteCreateDialog from './components/NoteCreateDialog.vue'
import { renderMarkdown } from '@/utils/markdown'
import SelectionLocateButton from '@/features/locate/SelectionLocateButton.vue'
import KnowledgeLocatePanel from '@/features/locate/KnowledgeLocatePanel.vue'
import { useTextSelection } from '@/composables/useTextSelection'

// 父组件只负责:详情/文件列表数据持有 + 子组件编排(弹窗开合、事件路由)
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const detail = ref<KnowledgeDetailVO | null>(null)
const fileList = ref<FileVO[]>([])
const fileListLoadFailed = ref(false)
const loadFailed = ref(false)
const editVisible = ref(false)
const noteVisible = ref(false)
const uploadPanelRef = ref<InstanceType<typeof FileUploadPanel> | null>(null)
const detailContentRef = ref<HTMLElement | null>(null)
const locatePanelRef = ref<InstanceType<typeof KnowledgeLocatePanel> | null>(null)

// B-110 仅在知识正文(.detail-content)内选中文本时浮现定位按钮
const {
  visible: selVisible,
  x: selX,
  top: selTop,
  bottom: selBottom,
  text: selText,
  clearSelection,
} = useTextSelection({
  isWithin: (node) => {
    const el = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement
    return !!el?.closest('.detail-content') && (detailContentRef.value?.contains(node) ?? false)
  },
})

function onLocate(text: string) {
  clearSelection()
  void locatePanelRef.value?.locate(text)
}

async function loadFileList() {
  if (!detail.value) return
  fileListLoadFailed.value = false
  try {
    const res = await getFileList(detail.value.id)
    fileList.value = res.data || []
  } catch {
    fileList.value = []
    fileListLoadFailed.value = true
  }
}

async function reloadDetail() {
  if (!detail.value) return
  const res = await getKnowledgeDetail(detail.value.id)
  detail.value = res.data
}

function onFileDeleted(fileId: number) {
  fileList.value = fileList.value.filter((x) => x.id !== fileId)
  // 删除的若是轮询中的文件,停掉轮询并清空上传状态
  uploadPanelRef.value?.resetIf(fileId)
}

async function loadDetail() {
  const id = Number(route.params.id)
  if (!id) {
    detail.value = null
    loadFailed.value = true
    return
  }
  loadFailed.value = false
  try {
    const res = await getKnowledgeDetail(id)
    detail.value = res.data
    // 加载已有文件列表(刷新后仍显示);文件列表失败不阻断详情展示
    try {
      await loadFileList()
    } catch {
      fileList.value = []
    }
  } catch {
    detail.value = null
    // 拦截器已提示;标记失败退出骨架屏,否则权限不足时永远加载中
    loadFailed.value = true
  }
}

onMounted(loadDetail)
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.detail {
  max-width: 800px;
  margin: 0 auto;
}

.detail-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $space-4;
  padding-top: $space-12;
}

.detail-error-actions {
  display: flex;
  gap: $space-3;
}

.detail-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $space-3;
  margin-bottom: $space-4;

  h2 {
    font-size: $font-size-lg;
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }
}

.detail-content {
  background: $color-bg-card;
  border-radius: $radius-md;
  padding: $space-6;
  line-height: 1.8;
  box-shadow: $shadow-card;
}

.markdown-body {
  word-break: break-word;

  :deep(p) { margin: 0 0 10px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { padding-left: 1.4em; margin: 0 0 10px; }
  :deep(li) { margin: 3px 0; }
  :deep(pre) {
    background: $color-bg;
    border: 1px solid $color-border;
    border-radius: $radius-sm;
    padding: $space-3;
    overflow-x: auto;
    font-size: $font-size-xs;
    margin: 0 0 10px;
  }
  :deep(code) {
    font-family: $font-family-mono;
    background: $color-bg;
    border: 1px solid $color-border;
    border-radius: 3px;
    padding: 1px 4px;
    font-size: 0.92em;
  }
  :deep(pre code) { background: transparent; border: none; padding: 0; }
  :deep(h1), :deep(h2), :deep(h3), :deep(h4) { font-weight: 600; margin: 14px 0 8px; line-height: 1.4; }
  :deep(h1) { font-size: 1.4em; }
  :deep(h2) { font-size: 1.2em; padding-bottom: $space-1; border-bottom: 1px solid $color-border; }
  :deep(h3) { font-size: 1.05em; }
  :deep(h4) { font-size: 1em; color: $color-text-secondary; }
  :deep(blockquote) {
    border-left: 3px solid $color-primary;
    background: $color-primary-light;
    padding: $space-2 $space-3;
    color: $color-text-secondary;
    margin: 0 0 10px;
    border-radius: 0 $radius-sm $radius-sm 0;
  }
  :deep(a) { color: $color-primary; text-decoration: none; &:hover { text-decoration: underline; } }
  :deep(table) { width: 100%; border-collapse: collapse; margin: 0 0 10px; font-size: $font-size-sm; }
  :deep(th), :deep(td) { border: 1px solid $color-border; padding: 6px 10px; text-align: left; }
  :deep(th) { background: $color-bg; font-weight: 600; }
  :deep(tbody tr:nth-child(even)) { background: $color-bg; }
  :deep(hr) { border: none; border-top: 1px solid $color-border; margin: $space-4 0; }
  :deep(img) { max-width: 100%; }
  :deep(.hljs-comment), :deep(.hljs-quote) { color: $color-text-muted; font-style: italic; }
  :deep(.hljs-keyword), :deep(.hljs-selector-tag), :deep(.hljs-literal), :deep(.hljs-section), :deep(.hljs-doctag), :deep(.hljs-type), :deep(.hljs-name) { color: $color-primary; }
  :deep(.hljs-string), :deep(.hljs-attr), :deep(.hljs-template-tag), :deep(.hljs-template-variable), :deep(.hljs-addition) { color: $color-success; }
  :deep(.hljs-number), :deep(.hljs-symbol), :deep(.hljs-bullet), :deep(.hljs-meta), :deep(.hljs-link) { color: $color-accent; }
  :deep(.hljs-title), :deep(.hljs-built_in), :deep(.hljs-variable), :deep(.hljs-selector-id), :deep(.hljs-selector-class) { color: $color-secondary; }
  :deep(.hljs-deletion) { color: $color-danger; }
}
</style>
