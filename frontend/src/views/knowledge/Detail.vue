<template>
  <div v-if="detail" class="detail">
    <div class="detail-header">
      <el-button :icon="ArrowLeft" text @click="router.push('/knowledge')">{{ t('common.back') }}</el-button>
      <h2>{{ detail.title }}</h2>
      <el-tag v-if="detail.category" size="small" effect="plain">{{ detail.category }}</el-tag>
      <el-button type="primary" text :icon="Edit" @click="editVisible = true">编辑</el-button>
      <el-button type="success" text :icon="EditPen" @click="noteVisible = true">新建笔记</el-button>
    </div>

    <div class="detail-content">{{ detail.content || t('upload.noContent') }}</div>

    <!-- 已有文件列表(删除后通知父级同步列表与上传轮询) -->
    <FileListPanel :files="fileList" @deleted="onFileDeleted" />

    <!-- 文件上传(选择/校验/轮询内聚,处理完成通知父级刷新列表) -->
    <FileUploadPanel ref="uploadPanelRef" :knowledge-id="detail.id" @file-processed="loadFileList" />
  </div>
  <el-skeleton v-else-if="!loadFailed" animated :rows="6" />
  <!-- 加载失败(权限不足/不存在):给明确反馈 + 出口,不再永远骨架屏 -->
  <div v-else class="detail-error">
    <el-empty :description="t('knowledge.loadFailed')" />
    <el-button type="primary" @click="router.push('/knowledge')">{{ t('common.back') }}</el-button>
  </div>

  <!-- 编辑弹窗(保存后父级刷新详情) -->
  <EditKnowledgeDialog v-if="detail" v-model:visible="editVisible" :detail="detail" @saved="reloadDetail" />

  <!-- 新建笔记弹窗(写优先:内容同步向量化,立刻可检索) -->
  <NoteCreateDialog v-if="detail" v-model:visible="noteVisible" :knowledge-id="detail.id" @created="loadFileList" />
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

// 父组件只负责:详情/文件列表数据持有 + 子组件编排(弹窗开合、事件路由)
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const detail = ref<KnowledgeDetailVO | null>(null)
const fileList = ref<FileVO[]>([])
const loadFailed = ref(false)
const editVisible = ref(false)
const noteVisible = ref(false)
const uploadPanelRef = ref<InstanceType<typeof FileUploadPanel> | null>(null)

async function loadFileList() {
  if (!detail.value) return
  const res = await getFileList(detail.value.id)
  fileList.value = res.data || []
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

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) return
  try {
    const res = await getKnowledgeDetail(id)
    detail.value = res.data
    // 加载已有文件列表(刷新后仍显示)
    await loadFileList()
  } catch {
    // 拦截器已提示;标记失败退出骨架屏,否则权限不足时永远加载中
    loadFailed.value = true
  }
})
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
  white-space: pre-wrap;
  box-shadow: $shadow-card;
}
</style>
