<template>
  <div v-if="detail" class="detail">
    <div class="detail-header">
      <el-button :icon="ArrowLeft" text @click="router.push('/knowledge')">返回</el-button>
      <h2>{{ detail.title }}</h2>
      <el-tag v-if="detail.category" size="small" effect="plain">{{ detail.category }}</el-tag>
    </div>

    <div class="detail-content">{{ detail.content || '暂无内容' }}</div>

    <!-- 已有文件列表 -->
    <div v-if="fileList.length" class="upload-section">
      <h3>已上传文档</h3>
      <div v-for="f in fileList" :key="f.id" class="file-row">
        <span class="file-name">{{ f.fileName }}</span>
        <div class="file-right">
          <el-tag size="small" :type="fileTagType(f.status)" effect="light">{{ fileStatusLabel(f.status) }}</el-tag>
          <el-button size="small" type="danger" text :icon="Delete" @click="onDeleteFile(f)">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 文件上传 -->
    <div class="upload-section">
      <h3>上传文档</h3>
      <el-upload
        :auto-upload="false"
        :limit="1"
        :disabled="uploading || !!uploadedFile"
        accept=".pdf,.docx"
        :on-change="onFileChange"
        :on-remove="() => (selectedFile = null)"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处,或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 pdf / docx,不超过 20MB</div>
        </template>
      </el-upload>
      <div class="upload-actions">
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="uploading || !selectedFile || !!uploadedFile"
          style="margin-top: 12px"
          @click="onUpload"
        >
          上传并处理
        </el-button>
        <el-tag v-if="uploadedFile" :type="fileStatusType" effect="light" class="file-status">
          {{ fileStatusText }}
        </el-tag>
      </div>
    </div>
  </div>
  <el-skeleton v-else animated :rows="6" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, UploadFilled } from '@element-plus/icons-vue'
import { deleteFile, getFileById, getFileList, getKnowledgeDetail, uploadFile } from '@/api/modules/knowledge'
import type { FileVO } from '@/types/api'
import { Delete } from '@element-plus/icons-vue'
import type { KnowledgeDetailVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const detail = ref<KnowledgeDetailVO | null>(null)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadedFile = ref<FileVO | null>(null)
const fileList = ref<FileVO[]>([])
let pollTimer: number | undefined

const fileStatusText = computed(() => {
  if (!uploadedFile.value) return ''
  const st = uploadedFile.value.status
  if (st === 'SUCCESS') return '处理完成 ✅'
  if (st === 'FAILED') return '处理失败 ❌'
  return '处理中...'
})
const fileStatusType = computed(() => {
  const st = uploadedFile.value?.status
  if (st === 'SUCCESS') return 'success'
  if (st === 'FAILED') return 'danger'
  return 'warning'
})

function startPolling(fileId: number) {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getFileById(fileId)
      uploadedFile.value = res.data
      if (res.data.status === 'SUCCESS' || res.data.status === 'FAILED') {
        stopPolling()
        if (res.data.status === 'SUCCESS') ElMessage.success('文档处理完成')
        else ElMessage.error('文档处理失败')
        // 刷新文件列表
        if (detail.value) {
          const listRes = await getFileList(detail.value.id)
          fileList.value = listRes.data || []
        }
      }
    } catch {
      stopPolling()
    }
  }, 2000)
}

function fileStatusLabel(status?: string) {
  if (status === 'SUCCESS') return '处理完成'
  if (status === 'FAILED') return '处理失败'
  return '处理中'
}
function fileTagType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

async function onDeleteFile(f: FileVO) {
  await ElMessageBox.confirm(`确定删除文件「${f.fileName}」吗?删除后该文档将无法检索。`, '删除文件', {
    type: 'warning',
  })
  try {
    await deleteFile(f.id)
    ElMessage.success('文件已删除')
    fileList.value = fileList.value.filter((x) => x.id !== f.id)
    if (uploadedFile.value?.id === f.id) {
      stopPolling()
      uploadedFile.value = null
    }
  } catch {
    // 用户取消或失败,拦截器已提示
  }
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}


onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) return
  try {
    const res = await getKnowledgeDetail(id)
    detail.value = res.data
    // 加载已有文件列表(刷新后仍显示)
    const fileRes = await getFileList(id)
    fileList.value = fileRes.data || []
  } catch {
    // 拦截器已提示
  }
})

function onFileChange(file: any) {
  const raw = file.raw as File
  if (!raw) return
  const ext = raw.name.split('.').pop()?.toLowerCase()
  if (ext !== 'pdf' && ext !== 'docx') {
    ElMessage.error('仅支持 pdf / docx 文件')
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.error('文件不能超过 20MB')
    return
  }
  selectedFile.value = raw
}

async function onUpload() {
  if (!selectedFile.value || !detail.value) return
  uploading.value = true
  try {
    const res = await uploadFile(detail.value.id, selectedFile.value)
    const fileId = (res.data as FileVO)?.id
    ElMessage.success('上传成功,后台处理中')
    selectedFile.value = null
    if (fileId) {
      uploadedFile.value = { id: fileId, status: 'PROCESSING' }
      startPolling(fileId)
    }
  } catch {
  } finally {
    uploading.value = false
  }
}
onBeforeUnmount(stopPolling)
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.detail {
  max-width: 800px;
  margin: 0 auto;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: $space-3;
  margin-bottom: $space-4;

  h2 {
    font-size: $font-size-lg;
    flex: 1;
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

.upload-section {
  margin-top: $space-6;
  background: $color-bg-card;
  border-radius: $radius-md;
  padding: $space-6;
  box-shadow: $shadow-card;

  h3 {
    margin-bottom: $space-4;
  }
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: $space-3;

  .file-status {
    margin-top: 12px;
  }
}

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-2 0;
  border-bottom: 1px solid $color-border;

  .file-name {
    color: $color-text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-right: $space-3;
    flex: 1;
  }

  .file-right {
    display: flex;
    align-items: center;
    gap: $space-2;
    flex-shrink: 0;
  }
}
</style>
