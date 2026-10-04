import { onBeforeUnmount, onMounted, ref } from 'vue'

export interface TextSelectionOptions {
  /** 判断选区锚点是否落在允许选中的区域内(仅区域内文本触发浮层) */
  isWithin: (node: Node) => boolean
}

/**
 * B-110 通用「选中文本 → 浮现按钮」能力。
 * - 桌面(mouseup / selectionchange)与移动端(touchend)均可触发;
 * - 选区为空(trim 后)/ 折叠 / 落在允许区域外 → 隐藏;
 * - 滚动 / Esc / 点击别处 → 隐藏;
 * - 仅读取选区,不 preventDefault,不干扰既有滚动、点击、复制等交互。
 * 位置以选区矩形 top/bottom(视口坐标)暴露,交由浮层组件做视口收敛。
 */
export function useTextSelection(options: TextSelectionOptions) {
  const visible = ref(false)
  const x = ref(0)
  const top = ref(0)
  const bottom = ref(0)
  const text = ref('')

  function hide() {
    visible.value = false
    text.value = ''
  }

  /**
   * 隐藏浮层并清空当前选区:点击浮层按钮后调用。
   * 仅 hide 不够——该次点击自身的 mouseup 会在下一个宏任务触发 evaluate,
   * 此时选区仍在,浮层会被重新置为可见;清空选区让 evaluate 走 hide 分支。
   */
  function clearSelection() {
    hide()
    window.getSelection()?.removeAllRanges()
  }

  function evaluate() {
    const sel = window.getSelection()
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      hide()
      return
    }
    const value = sel.toString().trim()
    if (!value) {
      hide()
      return
    }
    const range = sel.getRangeAt(0)
    if (!options.isWithin(range.startContainer) || !options.isWithin(range.endContainer)) {
      hide()
      return
    }
    const rect = range.getBoundingClientRect()
    if (!rect || (rect.width === 0 && rect.height === 0)) {
      hide()
      return
    }
    text.value = value
    x.value = rect.left + rect.width / 2
    top.value = rect.top
    bottom.value = rect.bottom
    visible.value = true
  }

  // 等一个宏任务,确保浏览器已更新 selection 后再读取
  function evaluateDeferred() {
    window.setTimeout(evaluate, 0)
  }

  function onKeyDown(e: KeyboardEvent) {
    if (e.key === 'Escape') hide()
  }

  onMounted(() => {
    document.addEventListener('mouseup', evaluateDeferred)
    document.addEventListener('touchend', evaluateDeferred)
    document.addEventListener('selectionchange', evaluate)
    document.addEventListener('keydown', onKeyDown)
    // capture:滚动事件不冒泡,用捕获监听容器内滚动以隐藏浮层
    window.addEventListener('scroll', hide, true)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('mouseup', evaluateDeferred)
    document.removeEventListener('touchend', evaluateDeferred)
    document.removeEventListener('selectionchange', evaluate)
    document.removeEventListener('keydown', onKeyDown)
    window.removeEventListener('scroll', hide, true)
  })

  return { visible, x, top, bottom, text, hide, clearSelection, evaluate }
}
