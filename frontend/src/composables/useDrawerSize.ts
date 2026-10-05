import { computed, ref, watch } from 'vue'

const STORAGE_KEY = 'aikb.drawer.fullscreen'

function readStored(): boolean | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw === '1') return true
    if (raw === '0') return false
  } catch {
    // 存储不可用(如隐私模式)时退回按视口判断默认值
  }
  return null
}

function isNarrowViewport(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(max-width: 768px)').matches
  )
}

// 模块级单例:原文预览与大纲面板共享同一「半屏/全屏」偏好,一处切换另一处即时同步
const fullscreen = ref(readStored() ?? isNarrowViewport())

watch(fullscreen, (v) => {
  try {
    localStorage.setItem(STORAGE_KEY, v ? '1' : '0')
  } catch {
    // 存储不可用则仅本次会话生效
  }
})

/**
 * 抽屉「半屏/全屏」显示偏好:桌面默认半屏、移动端默认全屏,用户切换后持久化,
 * 下次打开沿用上次选择(半屏或全屏由用户决定)。
 * @param halfSize 半屏宽度(如 'min(640px, 92vw)');全屏恒为 100%,不留缝隙。
 */
export function useDrawerSize(halfSize: string) {
  const size = computed(() => (fullscreen.value ? '100%' : halfSize))

  function toggle() {
    fullscreen.value = !fullscreen.value
  }

  return { fullscreen, size, toggle }
}
