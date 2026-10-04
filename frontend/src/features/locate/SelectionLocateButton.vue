<template>
  <Teleport to="body">
    <button
      v-if="visible"
      ref="btnRef"
      type="button"
      class="selection-locate-btn"
      :style="style"
      @mousedown.prevent
      @click="onClick"
    >
      <el-icon aria-hidden="true"><Aim /></el-icon>
      <span>{{ t('locate.entry') }}</span>
    </button>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Aim } from '@element-plus/icons-vue'

// B-110 选中文本后在选区附近浮现的「在知识库定位」按钮(仅展示,逻辑由页面 composable 驱动)
// 定位要求:必须始终落在视口内可点 —— 水平按实际宽度 clamp,垂直空间不足时翻到选区下方。
const props = defineProps<{
  visible: boolean
  x: number
  top: number
  bottom: number
  text: string
}>()

const emit = defineEmits<{ (e: 'locate', text: string): void }>()

const { t } = useI18n()

const GAP = 8 // 与选区的间距
const MARGIN = 6 // 与视口边缘的安全边距

const btnRef = ref<HTMLElement | null>(null)
const btnWidth = ref(0)
const btnHeight = ref(0)
const placement = ref<'top' | 'bottom'>('top')

async function measure() {
  await nextTick()
  const el = btnRef.value
  if (!el) return
  // offsetWidth/Height 不受 transform 影响,取按钮自身盒子尺寸
  btnWidth.value = el.offsetWidth
  btnHeight.value = el.offsetHeight
  placement.value = props.top - GAP - btnHeight.value >= MARGIN ? 'top' : 'bottom'
}

watch(
  () => [props.visible, props.x, props.top, props.bottom],
  () => {
    if (props.visible) void measure()
  },
  { immediate: true, flush: 'post' },
)

const left = computed(() => {
  const half = btnWidth.value / 2
  const min = half + MARGIN
  const max = Math.max(min, window.innerWidth - half - MARGIN)
  return Math.min(Math.max(props.x, min), max)
})

const anchorTop = computed(() => (placement.value === 'top' ? props.top : props.bottom))

const style = computed(() => ({
  left: `${left.value}px`,
  top: `${anchorTop.value}px`,
  transform:
    placement.value === 'top'
      ? `translate(-50%, calc(-100% - ${GAP}px))`
      : `translate(-50%, ${GAP}px)`,
}))

function onClick() {
  emit('locate', props.text)
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.selection-locate-btn {
  position: fixed;
  z-index: 3000;
  display: inline-flex;
  align-items: center;
  gap: $space-1;
  padding: 5px $space-3;
  border: 1px solid $color-primary;
  border-radius: 999px;
  background: $color-bg-card;
  color: $color-primary;
  box-shadow: $shadow-pop;
  cursor: pointer;
  font: inherit;
  font-size: $font-size-xs;
  line-height: 1.4;
  white-space: nowrap;
}
</style>
