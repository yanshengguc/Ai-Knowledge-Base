<template>
  <div v-if="files.length" class="upload-section">
    <h3>{{ t('upload.fileList') }}</h3>
    <div v-for="f in files" :key="f.id" class="file-row">
      <span class="file-name">
        {{ f.fileName }}
        <!-- AI 来源标记:自增强循环防线——检索命中时可分辨内容出处 -->
        <el-tag v-if="isAiSourced(f)" size="small" type="warning" effect="plain" class="ai-tag">AI</el-tag>
      </span>
      <div class="file-right">
        <el-tag size="small" :type="fileTagType(f.status)" effect="light">{{ fileStatusLabel(f.status) }}</el-tag>
        <el-button size="small" type="danger" text :icon="Delete" @click="onDeleteFile(f)">{{ t('common.delete') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { deleteFile } from '@/api/modules/knowledge'
import type { FileVO } from '@/types/api'
import { useI18n } from 'vue-i18n'

// 文件列表:展示 + 删除。列表数据由父级持有(上传完成/笔记创建后父级刷新传入)
defineProps<{ files: FileVO[] }>()
const emit = defineEmits<{ (e: 'deleted', fileId: number): void }>()
const { t } = useI18n()

function fileStatusLabel(status?: string) {
  if (status === 'SUCCESS') return t('upload.success')
  if (status === 'FAILED') return t('upload.failed')
  return t('upload.processing')
}
function fileTagType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

// AI 来源:后端把来源编码进 fileType(text/markdown;source=ai-chat)
function isAiSourced(f: FileVO) {
  return !!f.fileType && f.fileType.includes('source=ai-chat')
}

async function onDeleteFile(f: FileVO) {
  await ElMessageBox.confirm(t('upload.deleteConfirm', { name: f.fileName }), t('upload.title'), {
    type: 'warning',
  })
  try {
    await deleteFile(f.id)
    ElMessage.success(t('upload.deleteSuccess'))
    // 从列表移除与"是否在轮询"的判断由父级协调(列表数据归父级)
    emit('deleted', f.id)
  } catch {
    // 用户取消或失败,拦截器已提示
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

    .ai-tag {
      margin-left: $space-1;
    }
  }

  .file-right {
    display: flex;
    align-items: center;
    gap: $space-2;
    flex-shrink: 0;
  }
}
</style>
