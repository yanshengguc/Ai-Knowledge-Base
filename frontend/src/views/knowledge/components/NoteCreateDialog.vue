<template>
  <el-dialog
    :model-value="visible"
    title="新建笔记"
    width="min(560px, 92vw)"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="60px">
      <el-form-item label="标题">
        <el-input v-model="noteForm.title" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="noteForm.content" type="textarea" :rows="10" placeholder="支持 Markdown,写下你的笔记…保存后立刻可被知识库检索" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="success" :loading="noteSaving" @click="onSaveNote">创建并入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createNote } from '@/api/modules/knowledge'

// 新建笔记弹窗(写优先:内容同步向量化,立刻可检索);创建成功发事件,文件列表刷新由父级负责
const props = defineProps<{ visible: boolean; knowledgeId: number }>()
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
    ElMessage.warning('标题和内容不能为空')
    return
  }
  noteSaving.value = true
  try {
    await createNote(props.knowledgeId, {
      title: noteForm.title.trim(),
      content: noteForm.content,
    })
    ElMessage.success('笔记已创建并入库,现在就能被检索到')
    emit('update:visible', false)
    emit('created')
  } catch {
    // 拦截器已提示
  } finally {
    noteSaving.value = false
  }
}
</script>
