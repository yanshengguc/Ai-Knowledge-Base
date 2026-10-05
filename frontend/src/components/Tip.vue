<template>
  <el-tooltip
    v-if="hasHover && (content || $slots.content)"
    :placement="placement"
    :show-after="showAfter"
  >
    <template #content>
      <slot name="content">{{ content }}</slot>
    </template>
    <slot />
  </el-tooltip>
  <slot v-else />
</template>

<script setup lang="ts">
import { useHasHover } from '@/composables/useHasHover'

type TooltipPlacement =
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-start'
  | 'left-end'
  | 'right'
  | 'right-start'
  | 'right-end'

/**
 * 触屏安全 tooltip:仅在有 hover 能力的设备渲染 el-tooltip,否则直出插槽内容。
 * 触屏 tap 会经 focus 触发 el-tooltip 且不自动隐藏,popper 挂 body 后层级高于抽屉,
 * 因此无 hover 设备一律不渲染,避免气泡悬浮在已打开的抽屉之上。
 * 纯文本提示用 content;富文本提示用 #content 插槽。
 */
withDefaults(
  defineProps<{
    content?: string
    placement?: TooltipPlacement
    showAfter?: number
  }>(),
  {
    content: '',
    placement: 'top',
    showAfter: 0,
  },
)

const { hasHover } = useHasHover()
</script>
