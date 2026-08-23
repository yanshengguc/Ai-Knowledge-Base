<template>
  <el-dialog
    :model-value="visible"
    :title="t('chat.saveNoteTitle')"
    width="min(560px, 92vw)"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="70px">
      <el-form-item :label="t('chat.saveNoteTarget')">
        <el-select v-model="targetId" :placeholder="t('chat.saveNoteTargetPlaceholder')" style="width: 100%">
          <el-option
            v-for="k in knowledgeList"
            :key="k.id"
            :label="k.title"
            :value="k.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('knowledge.name')">
        <el-input v-model="form.title" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('knowledge.content')">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="10"
          :placeholder="t('chat.saveNoteEditHint')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="success" :loading="saving" :disabled="!targetId" @click="onSave">
        {{ t('chat.saveNoteConfirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createNote, getKnowledgeList2 } from '@/api/modules/knowledge'
import type { KnowledgeVO } from '@/types/api'
import { useI18n } from 'vue-i18n'

/**
 * AI 回答存为笔记(人机确认闭环):
 * 保存前可编辑(质量闸门,删掉 AI 寒暄/冗余),source=ai-chat 打来源标记防自增强循环。
 * 打开时加载知识条目列表供选择;预填 AI 回答全文,用户确认/修改后入库。
 */
const props = defineProps<{ visible: boolean; answer: string; question?: string }>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()
const { t } = useI18n()

const knowledgeList = ref<KnowledgeVO[]>([])
const targetId = ref<number>()
const saving = ref(false)
const form = reactive({ title: '', content: '' })

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    // 预填:问题做标题(截断),回答做内容;用户可改
    form.title = (props.question || '').slice(0, 60)
    form.content = props.answer
    if (!knowledgeList.value.length) {
      try {
        const res = await getKnowledgeList2()
        knowledgeList.value = res.data || []
      } catch {
        // 拦截器已提示
      }
    }
  },
)

async function onSave() {
  if (!targetId.value || !form.title.trim() || !form.content.trim()) {
    ElMessage.warning(t('chat.saveNoteRequired'))
    return
  }
  saving.value = true
  try {
    await createNote(targetId.value, {
      title: form.title.trim(),
      content: form.content,
      source: 'ai-chat',
    })
    ElMessage.success(t('chat.saveNoteSuccess'))
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}
</script>
