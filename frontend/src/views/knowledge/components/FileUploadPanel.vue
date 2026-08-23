<template>
  <div class="upload-section">
    <h3>{{ t('upload.title') }}</h3>
    <el-upload
      :auto-upload="false"
      :limit="1"
      :disabled="uploading || !!uploadedFile"
      accept=".pdf,.docx,.md"
      :on-change="onFileChange"
      :on-remove="() => (selectedFile = null)"
      drag
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">{{ t('upload.dragText') }}<em>{{ t('upload.clickSelect') }}</em></div>
      <template #tip>
        <div class="el-upload__tip">{{ t('upload.formatHint') }},{{ t('upload.sizeHint') }}</div>
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
        {{ t('upload.uploadAndProcess') }}
      </el-button>
      <el-tag v-if="uploadedFile" :type="fileStatusType" effect="light" class="file-status">
        {{ fileStatusText }}
      </el-tag>
      <el-progress v-if="uploading" :percentage="uploadProgress" class="upload-progress" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getFileById, uploadFile } from '@/api/modules/knowledge'
import type { FileVO } from '@/types/api'
import { useI18n } from 'vue-i18n'

// 上传面板:文件选择/校验/上传 + 处理状态轮询,全部内聚;
// 处理完成(成功或失败)通知父级刷新文件列表
const props = defineProps<{ knowledgeId: number }>()
const emit = defineEmits<{ (e: 'file-processed'): void }>()
const { t } = useI18n()

const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadedFile = ref<FileVO | null>(null)
let pollTimer: number | undefined

const fileStatusText = computed(() => {
  if (!uploadedFile.value) return ''
  const st = uploadedFile.value.status
  if (st === 'SUCCESS') return t('upload.success') + ' ✅'
  if (st === 'FAILED') return t('upload.failed') + ' ❌'
  return t('upload.processing') + '...'
})
const fileStatusType = computed(() => {
  const st = uploadedFile.value?.status
  if (st === 'SUCCESS') return 'success'
  if (st === 'FAILED') return 'danger'
  return 'warning'
})

/** 父级删除文件时调用:若删除的正是轮询中的文件,停止轮询并清空状态 */
function resetIf(fileId: number) {
  if (uploadedFile.value?.id === fileId) {
    stopPolling()
    uploadedFile.value = null
  }
}
defineExpose({ resetIf })

function startPolling(fileId: number) {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getFileById(fileId)
      uploadedFile.value = res.data
      if (res.data.status === 'SUCCESS' || res.data.status === 'FAILED') {
        stopPolling()
        if (res.data.status === 'SUCCESS') ElMessage.success(t('upload.docSuccess'))
        else ElMessage.error(t('upload.docFailed'))
        emit('file-processed')
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
onBeforeUnmount(stopPolling)

// 与后端 FileServiceImpl 白名单保持一致(accept 只过滤选择器,拖拽文件靠这里的校验兜底)
const ALLOWED_EXTS = ['pdf', 'docx', 'md']

function onFileChange(file: any) {
  const raw = file.raw as File
  if (!raw) return
  const ext = raw.name.split('.').pop()?.toLowerCase()
  if (!ext || !ALLOWED_EXTS.includes(ext)) {
    ElMessage.error(t('upload.formatHint'))
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.error(t('upload.sizeError'))
    return
  }
  selectedFile.value = raw
}

async function onUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    uploadProgress.value = 0
    const res = await uploadFile(props.knowledgeId, selectedFile.value, (p) => (uploadProgress.value = p))
    const fileId = (res.data as FileVO)?.id
    ElMessage.success(t('upload.uploadSuccess'))
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
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

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
  flex-wrap: wrap;
  gap: $space-3;

  .file-status {
    margin-top: 12px;
  }
}

.upload-progress {
  width: 200px;
}
</style>
