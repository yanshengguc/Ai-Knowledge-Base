<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h2>知识库</h2>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">新建知识</el-button>
    </div>

    <!-- 空态 -->
    <div v-if="!loading && list.length === 0" class="empty">
      <el-empty description="还没有知识,点击右上角新建">
        <el-button type="primary" @click="dialogVisible = true">新建知识</el-button>
      </el-empty>
    </div>

    <!-- 加载骨架 -->
    <div v-else-if="loading" class="grid">
      <el-skeleton v-for="i in 6" :key="i" animated class="card">
        <template #template>
          <el-skeleton-item variant="h3" style="width: 60%" />
          <el-skeleton-item variant="text" style="margin-top: 8px" />
          <el-skeleton-item variant="text" style="margin-top: 4px" />
        </template>
      </el-skeleton>
    </div>

    <!-- 列表 -->
    <div v-else class="grid">
      <div
        v-for="item in list"
        :key="item.id"
        class="card"
        @click="router.push(`/knowledge/${item.id}`)"
      >
        <div class="card-title">{{ item.title }}</div>
        <div class="card-meta">
          <el-tag size="small" effect="plain">{{ item.category || '未分类' }}</el-tag>
          <span class="card-time">{{ item.updateTime || '' }}</span>
        </div>
        <el-button
          class="card-delete"
          text
          type="danger"
          size="small"
          :icon="Delete"
          @click.stop="onDelete(item)"
        >
          删除
        </el-button>
      </div>
    </div>

    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" title="新建知识" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="知识标题" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="form.category" placeholder="如:Java / 面试 / 读书笔记" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="知识内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  addKnowledge,
  deleteKnowledge,
  getKnowledgeList2 as fetchList,
} from '@/api/modules/knowledge'
import type { KnowledgeVO } from '@/types/api'

const router = useRouter()
const list = ref<KnowledgeVO[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', category: '', content: '' })

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await fetchList()
    list.value = res.data || []
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    await addKnowledge({ title: form.title, content: form.content, category: form.category || undefined })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    form.title = ''
    form.category = ''
    form.content = ''
    load()
  } catch {
  } finally {
    saving.value = false
  }
}

async function onDelete(item: KnowledgeVO) {
  await ElMessageBox.confirm(`确定删除「${item.title}」吗?`, '删除确认', { type: 'warning' })
  try {
    await deleteKnowledge(item.id)
    ElMessage.success('已删除')
    load()
  } catch {
    // 取消或失败
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $space-4;

  h2 {
    font-size: $font-size-lg;
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: $space-4;
}

.card {
  position: relative;
  padding: $space-4;
  background: $color-bg-card;
  border-radius: $radius-md;
  box-shadow: $shadow-card;
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: $shadow-pop;
  }
}

.card-title {
  font-size: $font-size-md;
  font-weight: 600;
  margin-bottom: $space-2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: $space-2;

  .card-time {
    color: $color-text-muted;
    font-size: $font-size-xs;
  }
}

.card-delete {
  position: absolute;
  right: $space-2;
  bottom: $space-2;
  opacity: 0;
  transition: opacity 0.2s;

  .card:hover & {
    opacity: 1;
  }
}

.empty {
  padding: $space-12 0;
}
</style>
