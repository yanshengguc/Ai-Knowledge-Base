import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 设备是否具备 hover 能力(鼠标/触控笔等指针)。
 * 触屏设备无 hover:el-tooltip 会因 tap 命中 focus 而弹出且不会自动隐藏,
 * 其 popper 又挂在 body 上、层级高于 el-drawer,导致气泡悬浮在已打开的抽屉内容之上。
 * 因此仅 hover 设备渲染 tooltip;按钮本身保留 aria-label,可访问性不受影响。
 */
export function useHasHover() {
  const mql =
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia('(hover: hover)')
      : null
  const hasHover = ref(mql ? mql.matches : false)

  function onChange(e: MediaQueryListEvent) {
    hasHover.value = e.matches
  }

  onMounted(() => {
    if (!mql) return
    hasHover.value = mql.matches
    // 兼容不支持 addEventListener 的旧内核
    if (mql.addEventListener) mql.addEventListener('change', onChange)
    else mql.addListener(onChange)
  })

  onBeforeUnmount(() => {
    if (!mql) return
    if (mql.removeEventListener) mql.removeEventListener('change', onChange)
    else mql.removeListener(onChange)
  })

  return { hasHover }
}
