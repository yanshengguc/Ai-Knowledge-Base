<template>
  <div v-if="detail" class="detail">
    <div class="detail-header">
      <el-button :icon="ArrowLeft" text @click="router.push('/knowledge')">返回</el-button>
      <h2>{{ detail.title }}</h2>
      <el-tag v-if="detail.category" size="small" effect="plain">{{ detail.category }}</el-tag>
    </div>

    <div class="detail-content">{{ detail.content || '暂无内容' }}</div>

    <!-- 文件上传 -->
    <div class="upload-section">
      <h3>上传文档</h3>
      <el-upload
        :auto-upload="false"
        :limit="1"
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
          :disabled="!selectedFile || !!uploadedFile"
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
import { ElMessage } from 'element-plus'
import { ArrowLeft, UploadFilled } from '@element-plus/icons-vue'
import { getFileById, getKnowledgeDetail, uploadFile } from '@/api/modules/knowledge'
import type { FileVO } from '@/types/api'
import type { KnowledgeDetailVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const detail = ref<KnowledgeDetailVO | null>(null)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadedFile = ref<FileVO | null>(null)
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
      }
    } catch {
      stopPolling()
    }
  }, 2000)
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
</style>
