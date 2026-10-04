<template>
  <el-drawer
    v-model="visible"
    :title="fileName || t('filePreview.title')"
    size="min(640px, 92vw)"
    :destroy-on-close="true"
  >
    <!-- B-110 定位失败降级:抽屉顶部展示命中片段(模板插值渲染,不走 v-html) -->
    <div v-if="locateBanner" class="fp-locate-banner">
      <div class="fp-locate-notice">
        <el-icon aria-hidden="true"><WarningFilled /></el-icon>
        <span>{{ t('filePreview.locateFailed') }}</span>
      </div>
      <p class="fp-locate-snippet">
        <template v-for="(seg, si) in bannerSegments" :key="si">
          <mark v-if="seg.hit" class="fp-hit">{{ seg.text }}</mark>
          <span v-else>{{ seg.text }}</span>
        </template>
      </p>
    </div>
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
    <article v-else ref="articleRef" class="markdown-preview" v-html="renderedHtml" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { getFileContent } from '@/api/modules/knowledge'
import { renderMarkdown } from '@/utils/markdown'
import { splitByQuery, truncate } from '@/utils/highlight'

/** B-110 可选定位入参:命中片段原文 + 选中查询词(不传则行为与既有完全一致) */
export interface FilePreviewLocate {
  content: string
  query: string
}

export interface FilePreviewOpenOptions {
  id: number
  fileName?: string
  locate?: FilePreviewLocate
}

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
const articleRef = ref<HTMLElement | null>(null)
const locateBanner = ref(false)
const locateChunkText = ref('')
const locateQuery = ref('')

let pendingLocate: FilePreviewLocate | null = null

const bannerSegments = computed(() =>
  splitByQuery(truncate(locateChunkText.value, 240), locateQuery.value),
)

const renderedHtml = computed(() =>
  rawContent.value ? renderMarkdown(rawContent.value) : '',
)

function open(file: FilePreviewOpenOptions) {
  activeFileId.value = file.id
  fileName.value = file.fileName || `#${file.id}`
  rawContent.value = ''
  notSupported.value = false
  loadFailed.value = false
  locateBanner.value = false
  locateChunkText.value = ''
  locateQuery.value = ''
  pendingLocate = file.locate ?? null
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
  let shouldLocate = false
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
      pendingLocate = null
    } else {
      rawContent.value = vo.content
      shouldLocate = true
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
  // loading 关闭、原文渲染后再定位(article 仅在非 loading 时渲染)
  if (shouldLocate && requestId === requestSeq.value) {
    await applyLocate(requestId)
  }
}

// ── B-110 定位高亮 ──────────────────────────────────────────────
// 渲染后对原文 DOM 文本做「归一化匹配」,用 Range 包裹 <mark> 打高亮。
// 归一化 = 折叠/移除所有空白 + 去除 markdown 语法符(与渲染后 DOM 文本同一规则);
// 匹配失败绝不静默错位,降级为抽屉顶部展示片段 + 提示。
const SKIP_CHARS = new Set([
  ' ', '\t', '\n', '\r', '\f', '\v', '\u00a0',
  '#', '*', '_', '`', '>', '|', '[', ']', '(', ')',
])

const ENTITY_MAP: Record<string, string> = {
  amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'", apos: "'", nbsp: ' ',
}

function decodeEntities(src: string): string {
  return src.replace(/&(amp|lt|gt|quot|#39|apos|nbsp);/g, (m, e) => ENTITY_MAP[e] ?? m)
}

/** 归一化命中片段文本(markdown 链接还原为可见文本,再逐字符跳过语法符/空白) */
function normalizeChunk(src: string): string {
  const noLinks = src.replace(/!?\[([^\]]*)\]\([^()]*\)/g, '$1')
  const decoded = decodeEntities(noLinks)
  let out = ''
  // 按 UTF-16 code unit 遍历,与 buildDomIndex 完全对称(含代理对/emoji)
  for (let i = 0; i < decoded.length; i++) {
    const ch = decoded[i]
    if (SKIP_CHARS.has(ch)) continue
    out += ch
  }
  return out
}

interface NormalizedIndex {
  text: string
  map: Array<{ node: Text; offset: number }>
}

/** 遍历渲染后 DOM 的文本节点,构建「归一化字符串 + 归一化索引→{文本节点,偏移}」映射 */
function buildDomIndex(root: HTMLElement): NormalizedIndex {
  const chars: string[] = []
  const map: Array<{ node: Text; offset: number }> = []
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode() as Text | null
  while (node) {
    const data = node.data
    for (let i = 0; i < data.length; i++) {
      const ch = data[i]
      if (SKIP_CHARS.has(ch)) continue
      chars.push(ch)
      map.push({ node, offset: i })
    }
    node = walker.nextNode() as Text | null
  }
  return { text: chars.join(''), map }
}

/** 在已渲染的原文里定位并高亮命中片段;返回是否成功 */
function highlightChunk(chunkText: string): boolean {
  const root = articleRef.value
  if (!root) return false
  const needle = normalizeChunk(chunkText)
  if (!needle) return false
  const index = buildDomIndex(root)
  const start = index.text.indexOf(needle)
  if (start < 0) return false
  const startPoint = index.map[start]
  const endPoint = index.map[start + needle.length - 1]
  if (!startPoint || !endPoint) return false
  try {
    const range = document.createRange()
    range.setStart(startPoint.node, startPoint.offset)
    range.setEnd(endPoint.node, endPoint.offset + 1)
    const mark = document.createElement('mark')
    mark.className = 'fp-locate-highlight'
    mark.appendChild(range.extractContents())
    range.insertNode(mark)
    mark.scrollIntoView({ block: 'center', behavior: 'smooth' })
    mark.classList.add('fp-locate-flash')
    window.setTimeout(() => mark.classList.remove('fp-locate-flash'), 1600)
    return true
  } catch {
    return false
  }
}

/** 内容渲染完成后按可选 locate 入参定位;失败则降级为顶部片段展示 */
async function applyLocate(requestId: number) {
  const locate = pendingLocate
  pendingLocate = null
  if (!locate) return
  locateChunkText.value = locate.content
  locateQuery.value = locate.query
  await nextTick()
  if (requestId !== requestSeq.value) return
  if (!highlightChunk(locate.content)) {
    locateBanner.value = true
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

.fp-locate-banner {
  margin-bottom: $space-3;
  padding: $space-2 $space-3;
  border: 1px solid $color-warning;
  border-radius: $radius-sm;
  background: rgba(245, 166, 35, 0.08);
}

.fp-locate-notice {
  display: inline-flex;
  align-items: center;
  gap: $space-1;
  color: $color-warning;
  font-size: $font-size-xs;
  font-weight: 600;
}

.fp-locate-snippet {
  margin: $space-2 0 0;
  color: $color-text-secondary;
  font-size: $font-size-xs;
  line-height: 1.6;
  word-break: break-word;
}

.fp-hit {
  background: rgba(245, 166, 35, 0.35);
  color: inherit;
  border-radius: 2px;
}

.markdown-preview :deep(.fp-locate-highlight) {
  background: rgba(37, 99, 235, 0.22);
  color: inherit;
  border-radius: 2px;
  padding: 0 1px;
}

.markdown-preview :deep(.fp-locate-highlight.fp-locate-flash) {
  animation: fp-locate-flash 0.5s ease-in-out 2;
}

@keyframes fp-locate-flash {
  0%, 100% { background: rgba(37, 99, 235, 0.22); }
  50% { background: rgba(37, 99, 235, 0.55); }
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
