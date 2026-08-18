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
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!selectedFile"
        style="margin-top: 12px"
        @click="onUpload"
      >
        上传并处理
      </el-button>
    </div>
  </div>
  <el-skeleton v-else animated :rows="6" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UploadFilled } from '@element-plus/icons-vue'
import { getKnowledgeDetail, uploadFile } from '@/api/modules/knowledge'
import type { KnowledgeDetailVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const detail = ref<KnowledgeDetailVO | null>(null)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)

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
    await uploadFile(detail.value.id, selectedFile.value)
    ElMessage.success('上传成功,后台处理中')
    selectedFile.value = null
  } catch {
  } finally {
    uploading.value = false
  }
}
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
</style>
