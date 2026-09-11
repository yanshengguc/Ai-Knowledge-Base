<template>
  <el-dialog
    :model-value="visible"
    :title="t('knowledge.createNote')"
    width="min(560px, 92vw)"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="60px">
      <el-form-item :label="t('knowledge.name')">
        <el-input v-model="noteForm.title" maxlength="100" show-word-limit :placeholder="t('knowledge.name')" />
      </el-form-item>
      <el-form-item :label="t('knowledge.content')">
        <el-input
          v-model="noteForm.content"
          type="textarea"
          :rows="10"
          :placeholder="t('knowledge.notePlaceholder')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="success" :loading="noteSaving" @click="onSaveNote">
        {{ t('knowledge.createAndIndex') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createNote } from '@/api/modules/knowledge'
import { useI18n } from 'vue-i18n'

// 新建笔记弹窗(写优先:内容同步向量化,立刻可检索);创建成功发事件,文件列表刷新由父级负责
const props = defineProps<{ visible: boolean; knowledgeId: number }>()
const { t } = useI18n()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'created'): void
}>()

const noteSaving = ref(false)
const noteForm = reactive({ title: '', content: '' })

watch(
  () => props.visible,
  (v) => {
    if (v) {
      noteForm.title = ''
      noteForm.content = ''
    }
  },
)

async function onSaveNote() {
  if (!noteForm.title.trim() || !noteForm.content.trim()) {
    ElMessage.warning(t('knowledge.noteRequired'))
    return
  }
  noteSaving.value = true
  try {
    await createNote(props.knowledgeId, {
      title: noteForm.title.trim(),
      content: noteForm.content,
    })
    ElMessage.success(t('knowledge.noteCreated'))
    emit('update:visible', false)
    emit('created')
  } catch {
    // 拦截器已提示
  } finally {
    noteSaving.value = false
  }
}
</script>
