<template>
  <el-dialog
    :model-value="visible"
    :title="t('knowledge.editTitle')"
    width="min(520px, 92vw)"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="60px">
      <el-form-item :label="t('knowledge.name')" prop="title">
        <el-input v-model="editForm.title" />
      </el-form-item>
      <el-form-item :label="t('knowledge.category')" prop="category">
        <el-input v-model="editForm.category" :placeholder="t('knowledge.categoryPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('knowledge.content')" prop="content">
        <el-input v-model="editForm.content" type="textarea" :rows="6" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="editing" @click="onSaveEdit">{{ t('knowledge.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { updateKnowledge } from '@/api/modules/knowledge'
import type { KnowledgeDetailVO } from '@/types/api'
import { useI18n } from 'vue-i18n'

// 编辑弹窗:表单状态内聚,打开时从 detail 同步;保存成功只发事件,详情刷新由父级负责
const props = defineProps<{ visible: boolean; detail: KnowledgeDetailVO }>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()
const { t } = useI18n()

const editing = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({ title: '', category: '', content: '' })
const editRules: FormRules = {
  title: [{ required: true, message: t('knowledge.nameRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('knowledge.contentRequired'), trigger: 'blur' }],
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      editForm.title = props.detail.title
      editForm.category = props.detail.category || ''
      editForm.content = props.detail.content || ''
    }
  },
)

async function onSaveEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  editing.value = true
  try {
    await updateKnowledge(props.detail.id, {
      title: editForm.title,
      content: editForm.content,
      category: editForm.category || undefined,
    })
    ElMessage.success(t('knowledge.editSuccess'))
    emit('update:visible', false)
    emit('saved')
  } catch {
  } finally {
    editing.value = false
  }
}
</script>
