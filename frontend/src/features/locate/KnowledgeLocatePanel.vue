<template>
  <el-dialog
    v-model="visible"
    :title="t('locate.resultTitle')"
    width="min(600px, 92vw)"
    append-to-body
  >
    <div v-if="loading" class="locate-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('locate.loading') }}</span>
    </div>
    <ul v-else class="locate-list">
      <li
        v-for="(hit, i) in hits"
        :key="`${hit.fileId}-${hit.chunkIndex}-${i}`"
        class="locate-item"
      >
        <div class="locate-item-head">
          <span class="locate-file" :title="hit.fileName">{{ hit.fileName || `#${hit.fileId}` }}</span>
          <el-tag
            v-if="hit.chunkIndex != null"
            size="small"
            type="info"
            class="locate-chunk"
          >
            {{ t('locate.chunkLabel', { n: hit.chunkIndex }) }}
          </el-tag>
        </div>
        <p class="locate-snippet">
          <template v-for="(seg, si) in segments(hit.content)" :key="si">
            <mark v-if="seg.hit" class="locate-hit">{{ seg.text }}</mark>
            <span v-else>{{ seg.text }}</span>
          </template>
        </p>
        <el-button size="small" type="primary" text :icon="View" @click="viewOriginal(hit)">
          {{ t('locate.viewOriginal') }}
        </el-button>
      </li>
    </ul>
  </el-dialog>
  <FilePreview ref="previewRef" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading, View } from '@element-plus/icons-vue'
import { locateChunks } from '@/api/modules/knowledge'
import type { ChunkHitVO } from '@/types/api'
import { splitByQuery, truncate } from '@/utils/highlight'
import FilePreview from '@/views/knowledge/components/FilePreview.vue'

/**
 * B-110 知识定位结果面板:
 * 选中文本 → locate 检索 → 命中列表(文件名 + chunkIndex + 片段预览,选中词高亮)
 * → 每条「查看原文」复用 FilePreview.open 定位高亮。
 * 空结果只提示、不跳转、不打开预览。
 */
const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const query = ref('')
const hits = ref<ChunkHitVO[]>([])
const previewRef = ref<InstanceType<typeof FilePreview> | null>(null)

async function locate(raw: string) {
  const q = raw.trim()
  if (!q || loading.value) return
  loading.value = true
  query.value = q
  hits.value = []
  visible.value = true
  try {
    const res = await locateChunks(q)
    const list = res.data || []
    if (!list.length) {
      visible.value = false
      ElMessage.warning(t('locate.notFound'))
      return
    }
    hits.value = list
  } catch {
    // 拦截器已提示
    visible.value = false
  } finally {
    loading.value = false
  }
}

function segments(content: string) {
  return splitByQuery(truncate(content, 220), query.value)
}

function viewOriginal(hit: ChunkHitVO) {
  previewRef.value?.open({
    id: hit.fileId,
    fileName: hit.fileName,
    locate: { content: hit.content, query: query.value },
  })
}

defineExpose({ locate })
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.locate-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  padding: $space-8 0;
  color: $color-text-secondary;
}

.locate-list {
  display: flex;
  flex-direction: column;
  gap: $space-3;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: min(60vh, 520px);
  overflow-y: auto;
}

.locate-item {
  padding: $space-3;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: $color-bg-card;
}

.locate-item-head {
  display: flex;
  align-items: center;
  gap: $space-2;
  min-width: 0;
  margin-bottom: $space-2;
}

.locate-file {
  overflow: hidden;
  color: $color-text;
  font-size: $font-size-sm;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.locate-chunk {
  flex-shrink: 0;
}

.locate-snippet {
  margin: 0 0 $space-2;
  color: $color-text-secondary;
  font-size: $font-size-xs;
  line-height: 1.7;
  word-break: break-word;
}

.locate-hit {
  background: rgba(245, 166, 35, 0.35);
  color: inherit;
  border-radius: 2px;
}
</style>
