<template>
  <el-drawer
    v-model="visible"
    :title="fileName || t('filePreview.title')"
    size="min(640px, 92vw)"
    :destroy-on-close="true"
  >
    <div v-if="loading" class="fp-state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('filePreview.loading') }}</span>
    </div>
    <div v-else-if="loadFailed" class="fp-state">
      <el-empty :description="t('filePreview.loadFailed')">
        <el-button type="primary" @click="retry">{{ t('common.retry') }}</el-button>
      </el-empty>
    </div>
    <div v-else-if="notSupported" class="fp-state">
      <el-empty :description="t('filePreview.notSupported')" />
    </div>
    <!-- eslint-disable-next-line vue/no-v-html —— 内容经 DOMPurify 消毒(renderMarkdown) -->
    <article v-else class="markdown-preview" v-html="renderedHtml" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getFileContent } from '@/api/modules/knowledge'
import { renderMarkdown } from '@/utils/markdown'

/**
 * B-112 文件在线预览抽屉:树/图谱/文件列表三处入口共用。
 * 用法:const preview = ref(); preview.value.open({ id, fileName })
 */
const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const loadFailed = ref(false)
const notSupported = ref(false)
const fileName = ref('')
const activeFileId = ref<number | null>(null)
const requestSeq = ref(0)
const rawContent = ref('')

const renderedHtml = computed(() =>
  rawContent.value ? renderMarkdown(rawContent.value) : '',
)

function open(file: { id: number; fileName?: string }) {
  activeFileId.value = file.id
  fileName.value = file.fileName || `#${file.id}`
  rawContent.value = ''
  notSupported.value = false
  loadFailed.value = false
  visible.value = true
  load(file.id)
}

function retry() {
  const id = activeFileId.value
  if (id == null) {
    loadFailed.value = true
    return
  }
  void load(id)
}

async function load(id: number) {
  const requestId = ++requestSeq.value
  activeFileId.value = id
  loading.value = true
  loadFailed.value = false
  notSupported.value = false
  try {
    const res = await getFileContent(id)
    if (requestId !== requestSeq.value) return

    const vo = res.data
    if (vo?.content == null) {
      notSupported.value = true
    } else {
      rawContent.value = vo.content
    }
  } catch {
    if (requestId !== requestSeq.value) return

    loadFailed.value = true
    ElMessage.error(t('filePreview.loadFailed'))
  } finally {
    if (requestId === requestSeq.value) {
      loading.value = false
    }
  }
}

defineExpose({ open, retry })
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.fp-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  height: 100%;
  color: $color-text-secondary;
}

// 原文排版(与 B-111 渲染同源基调:表格边框/代码块/引用块/标题层级)
.markdown-preview {
  font-size: $font-size-sm;
  line-height: 1.75;
  color: $color-text;
  word-break: break-word;

  :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
    margin: $space-4 0 $space-2;
    line-height: 1.4;
  }

  :deep(h1) { font-size: 1.35em; }
  :deep(h2) { font-size: 1.2em; border-bottom: 1px solid $color-border; padding-bottom: $space-1; }
  :deep(h3) { font-size: 1.08em; }
  :deep(h4) { font-size: 1em; color: $color-text-secondary; }

  :deep(p) { margin: $space-2 0; }

  :deep(ul), :deep(ol) { padding-left: 1.4em; margin: $space-2 0; }
  :deep(li) { margin: 2px 0; }

  :deep(blockquote) {
    margin: $space-2 0;
    padding: $space-2 $space-3;
    border-left: 3px solid $color-primary;
    background: $color-primary-light;
    color: $color-text-secondary;
    border-radius: 0 $radius-sm $radius-sm 0;
  }

  :deep(code) {
    font-family: $font-family-mono;
    background: $color-bg;
    border: 1px solid $color-border;
    padding: 1px 5px;
    border-radius: $radius-sm;
    font-size: 0.92em;
  }

  :deep(pre) {
    background: $color-bg;
    border: 1px solid $color-border;
    padding: $space-3;
    border-radius: $radius-sm;
    overflow-x: auto;
  }

  :deep(pre code) { background: none; border: none; padding: 0; }

  :deep(table) {
    border-collapse: collapse;
    margin: $space-3 0;
    width: 100%;
  }

  :deep(th), :deep(td) {
    border: 1px solid $color-border;
    padding: $space-1 $space-2;
    text-align: left;
  }

  :deep(th) { background: $color-bg; font-weight: 600; }
  :deep(tbody tr:nth-child(even)) { background: $color-bg; }

  :deep(hr) { border: none; border-top: 1px solid $color-border; margin: $space-4 0; }

  :deep(img) { max-width: 100%; }

  :deep(a) { color: $color-primary; }

  :deep(.hljs-comment), :deep(.hljs-quote) { color: $color-text-muted; font-style: italic; }
  :deep(.hljs-keyword), :deep(.hljs-selector-tag), :deep(.hljs-literal), :deep(.hljs-section), :deep(.hljs-doctag), :deep(.hljs-type), :deep(.hljs-name) { color: $color-primary; }
  :deep(.hljs-string), :deep(.hljs-attr), :deep(.hljs-template-tag), :deep(.hljs-template-variable), :deep(.hljs-addition) { color: $color-success; }
  :deep(.hljs-number), :deep(.hljs-symbol), :deep(.hljs-bullet), :deep(.hljs-meta), :deep(.hljs-link) { color: $color-accent; }
  :deep(.hljs-title), :deep(.hljs-built_in), :deep(.hljs-variable), :deep(.hljs-selector-id), :deep(.hljs-selector-class) { color: $color-secondary; }
  :deep(.hljs-deletion) { color: $color-danger; }
}
</style>
