<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h2>{{ t('nav.knowledge') }}</h2>
      <div>
        <el-button :icon="Download" @click="onExport">导出</el-button>
        <el-button type="primary" :icon="Plus" @click="dialogVisible = true">{{ t('knowledge.createTitle') }}</el-button>
      </div>
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

    <!-- 加载骨架 -->
    <div v-if="loading" class="grid">
      <el-skeleton v-for="i in 6" :key="i" animated class="card">
        <template #template>
          <el-skeleton-item variant="h3" style="width: 60%" />
          <el-skeleton-item variant="text" style="margin-top: 8px" />
          <el-skeleton-item variant="text" style="margin-top: 4px" />
        </template>
      </el-skeleton>
    </div>

    <!-- 加载失败态:与真实空库和筛选无结果区分 -->
    <div v-else-if="loadError" class="empty">
      <el-empty :description="t('knowledge.loadFailed')">
        <el-button type="primary" @click="load">{{ t('common.retry') }}</el-button>
      </el-empty>
    </div>

    <!-- 空态/筛选无结果态 -->
    <div v-else-if="list.length === 0" class="empty">
      <el-empty :description="hasFilters ? t('knowledge.noResults') : t('knowledge.empty')">
        <el-button v-if="hasFilters" @click="clearFilters">{{ t('knowledge.clearFilters') }}</el-button>
        <el-button v-else type="primary" @click="dialogVisible = true">{{ t('knowledge.createTitle') }}</el-button>
      </el-empty>
    </div>

    <!-- 列表 -->
    <div v-else>
    <div class="grid">
      <article
        v-for="item in list"
        :key="item.id"
        class="card"
      >
        <button
          type="button"
          class="card-main"
          :aria-label="t('knowledge.openItem', { name: item.title })"
          @click="openItem(item.id)"
        >
          <div class="card-title">{{ item.title }}</div>
          <div class="card-meta">
            <el-tag size="small" :type="categoryTagType(item.category)" effect="light">{{ item.category || t('knowledge.uncategorized') }}</el-tag>
            <span class="card-time">{{ item.updateTime || '' }}</span>
          </div>
        </button>
        <el-button
          class="card-delete"
          text
          type="danger"
          size="small"
          :icon="Delete"
          :aria-label="t('common.delete')"
          @click.stop="onDelete(item)"
        >
          {{ t('common.delete') }}
        </el-button>
      </article>
    </div>
    <el-pagination
      v-if="total > pageSize"
      class="pager"
      layout="prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="onPageChange"
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Delete, Search, Download } from '@element-plus/icons-vue'
import {
  addKnowledge,
  deleteKnowledge,
  getKnowledgeList2 as fetchAllList,
  getKnowledgePage,
} from '@/api/modules/knowledge'
import request from '@/api/request'
import type { KnowledgeVO } from '@/types/api'
import { useI18n } from 'vue-i18n'
import { categoryTagType } from '@/utils/category'

const router = useRouter()

/** 一键导出(数据主权):下载当前用户全部知识为 Markdown */
async function onExport() {
  try {
    const blob = await request.get<unknown, any>('/knowledge/export', { responseType: 'blob' })
    const url = URL.createObjectURL(blob as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `knowledge-export-${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    // 拦截器已提示
  }
}
const { t } = useI18n()
const list = ref<KnowledgeVO[]>([])
/** 服务端返回的过滤后总数(用于分页组件) */
const total = ref(0)
const page = ref(1)
const pageSize = 12
const keyword = ref('')
const categoryFilter = ref('')
const loadError = ref(false)
const hasFilters = computed(() => !!keyword.value.trim() || !!categoryFilter.value)
// 分类为自由文本,下拉选项需全量聚合;沿用旧全量接口(失败仅降级为无选项,不影响列表主体)
const categories = ref<string[]>([])

// 竞态保护:仅最后一次请求可写回(连续输入/快速翻页时,旧响应不得覆盖新结果)
let requestSeq = 0
// 关键词/分类变更统一防抖:同一 tick 内多次变更(如清空筛选同时改两项)只发 1 次请求
let reloadTimer: ReturnType<typeof setTimeout> | undefined
function scheduleReload() {
  if (reloadTimer) clearTimeout(reloadTimer)
  reloadTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 300)
}
watch(keyword, scheduleReload)
watch(categoryFilter, scheduleReload)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', category: '', content: '' })

const rules: FormRules = {
  title: [{ required: true, message: t('knowledge.nameRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('knowledge.contentRequired'), trigger: 'blur' }],
}

function openItem(id: number) {
  router.push(`/knowledge/${id}`)
}

function clearFilters() {
  keyword.value = ''
  categoryFilter.value = ''
}

async function load() {
  const seq = ++requestSeq
  loading.value = true
  loadError.value = false
  try {
    const res = await getKnowledgePage({
      page: page.value,
      size: pageSize,
      keyword: keyword.value.trim() || undefined,
      category: categoryFilter.value || undefined,
    })
    if (seq !== requestSeq) return // 已被更新的请求取代,丢弃本次响应
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    if (seq !== requestSeq) return
    loadError.value = true
    // 拦截器已提示
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

/** 分类下拉选项(全量聚合);失败不影响列表主体,仅降级为无选项 */
async function loadCategories() {
  try {
    const res = await fetchAllList()
    categories.value = Array.from(
      new Set((res.data || []).map((k) => k.category).filter(Boolean)),
    ) as string[]
  } catch {
    // 保留已有选项,避免网络抖动清空下拉
  }
}

function onPageChange(p: number) {
  page.value = p
  load()
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
    page.value = 1
    load()
    loadCategories()
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
    // 删掉本页最后一条时回退上一页:服务端对越界页返回空列表,不修正会停在空页
    // 注意:total 是删除前的旧值,先减 1 再换算,得到删除后的总页数
    const maxPage = Math.max(1, Math.ceil((total.value - 1) / pageSize))
    if (page.value > maxPage) page.value = maxPage
    load()
    loadCategories()
  } catch {
    // 取消或失败
  }
}

onMounted(() => {
  load()
  loadCategories()
})

onBeforeUnmount(() => {
  if (reloadTimer) clearTimeout(reloadTimer)
})
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
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: $shadow-pop;
  }
}

.card-main {
  display: block;
  width: 100%;
  padding: 0 0 $space-4;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid $color-primary;
    outline-offset: 2px;
    border-radius: $radius-sm;
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

  .card:hover &,
  .card:focus-within & {
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
