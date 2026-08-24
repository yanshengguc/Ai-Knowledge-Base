<template>
  <div class="token-strip" v-if="summary">
    <el-tooltip placement="top" :show-after="300">
      <template #content>
        <div class="tip">
          <div>{{ t('tokenUsage.tipChat') }}：{{ fmtTokens(chat.monthTokens) }} · ¥{{ fmtCost(chat.monthCost) }}</div>
          <div>{{ t('tokenUsage.tipEmbedding') }}：{{ fmtTokens(embedding.monthTokens) }} · ¥{{ fmtCost(embedding.monthCost) }}</div>
          <div>{{ t('tokenUsage.tipEstimate') }}</div>
        </div>
      </template>
      <span class="strip-inner">
        <el-icon :size="13"><DataLine /></el-icon>
        <span class="seg">{{ t('tokenUsage.today') }} <b>{{ fmtTokens(chat.todayTokens) }}</b></span>
        <span class="sep" />
        <span class="seg">{{ t('tokenUsage.monthCost') }} <b>¥{{ fmtCost(monthCostAll) }}</b></span>
      </span>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { DataLine } from '@element-plus/icons-vue'
import { getTokenUsageSummary, type TokenUsageSummary } from '@/api/modules/tokenUsage'
import { useChatStore } from '@/stores/chat'

const { t } = useI18n()
const chatStore = useChatStore()
const summary = ref<TokenUsageSummary | null>(null)
const chat = computed(() => summary.value?.chat ?? {})
const embedding = computed(() => summary.value?.embedding ?? {})
const monthCostAll = computed(() =>
  (Number(chat.value.monthCost) || 0) + (Number(embedding.value.monthCost) || 0)
)

async function refresh() {
  try {
    const res = await getTokenUsageSummary()
    if (res.code === 200) summary.value = res.data
  } catch {
    // 用量条是非关键信息,拉取失败静默隐藏
  }
}

function fmtTokens(n?: number) {
  const v = Number(n) || 0
  if (v >= 10000) return (v / 10000).toFixed(1) + 'w'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return String(v)
}

function fmtCost(n?: number) {
  const v = Number(n) || 0
  return v >= 0.01 ? v.toFixed(2) : v.toFixed(4)
}

onMounted(refresh)
// 一轮回答结束后自动刷新(sending: true -> false)
watch(() => chatStore.sending, (nv, ov) => {
  if (ov && !nv) refresh()
})
</script>

<style scoped>
.token-strip {
  display: inline-flex;
  align-items: center;
}
.strip-inner {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 10px;
  padding: 3px 10px;
  cursor: default;
}
.strip-inner b {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}
.sep {
  width: 1px;
  height: 10px;
  background: var(--el-border-color);
}
.tip {
  line-height: 1.7;
  font-size: 12px;
}
</style>
