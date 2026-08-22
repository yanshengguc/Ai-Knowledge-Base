<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h2>{{ t('nav.knowledge') }}</h2>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">{{ t('knowledge.createTitle') }}</el-button>
    </div>

    <!-- 工具条:搜索 + 分类筛选 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('knowledge.searchPlaceholder')"
        :prefix-icon="Search"
        clearable
        class="search-input"
      />
      <el-select v-model="categoryFilter" clearable :placeholder="t('knowledge.allCategories')" class="category-select">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
    </div>

    <!-- 空态 -->
    <div v-if="!loading && filteredList.length === 0" class="empty">
      <el-empty :description="t('knowledge.empty') + ',' + t('knowledge.emptyHint')">
        <el-button type="primary" @click="dialogVisible = true">{{ t('knowledge.createTitle') }}</el-button>
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
    <div v-else>
    <div class="grid">
      <div
        v-for="item in pagedList"
        :key="item.id"
        class="card"
        @click="router.push(`/knowledge/${item.id}`)"
      >
        <div class="card-title">{{ item.title }}</div>
        <div class="card-meta">
          <el-tag size="small" :type="categoryTagType(item.category)" effect="light">{{ item.category || t('knowledge.uncategorized') }}</el-tag>
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
          {{ t('common.delete') }}
        </el-button>
      </div>
    </div>
    <el-pagination
      v-if="filteredList.length > pageSize"
      class="pager"
      layout="prev, pager, next"
      :total="list.length"
      :page-size="pageSize"
      :current-page="page"
      @current-change="page = $event"
    />
    </div>

    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" :title="t('knowledge.createTitle')" width="min(480px, 92vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
        <el-form-item :label="t('knowledge.name')" prop="title">
          <el-input v-model="form.title" :placeholder="t('knowledge.name')" />
        </el-form-item>
        <el-form-item :label="t('knowledge.category')" prop="category">
          <el-input v-model="form.category" :placeholder="t('knowledge.categoryPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledge.content')" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" :placeholder="t('knowledge.content')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">{{ t('knowledge.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete, Search } from '@element-plus/icons-vue'
import {
  addKnowledge,
  deleteKnowledge,
  getKnowledgeList2 as fetchList,
} from '@/api/modules/knowledge'
import type { KnowledgeVO } from '@/types/api'
import { useI18n } from 'vue-i18n'
import { categoryTagType } from '@/utils/category'

const router = useRouter()
const { t } = useI18n()
const list = ref<KnowledgeVO[]>([])
const page = ref(1)
const pageSize = 12
const keyword = ref('')
const categoryFilter = ref('')
const categories = computed(() => Array.from(new Set(list.value.map((k) => k.category).filter(Boolean))))
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((k) => {
    const matchKw =
      !kw ||
      k.title.toLowerCase().includes(kw) ||
      (k.content || '').toLowerCase().includes(kw)
    const matchCat = !categoryFilter.value || k.category === categoryFilter.value
    return matchKw && matchCat
  })
})
const pagedList = computed(() => filteredList.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', category: '', content: '' })

const rules: FormRules = {
  title: [{ required: true, message: t('knowledge.nameRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('knowledge.contentRequired'), trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await fetchList()
    list.value = res.data || []
    page.value = 1
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
    ElMessage.success(t('knowledge.createSuccess'))
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
  await ElMessageBox.confirm(t('knowledge.deleteConfirm', { name: item.title }), t('common.confirm'), { type: 'warning' })
  try {
    await deleteKnowledge(item.id)
    ElMessage.success(t('knowledge.deleteSuccess'))
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
  flex-wrap: wrap;
  gap: $space-2;
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

.toolbar {
  display: flex;
  gap: $space-3;
  margin-bottom: $space-4;
  flex-wrap: wrap;

  .search-input {
    max-width: 320px;
    flex: 1;
  }

  .category-select {
    width: 160px;
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: $space-6;
}
</style>
