import { marked } from 'marked'
import DOMPurify from 'dompurify'

// marked 基础配置:代码高亮不做(轻量),链接新窗口打开
marked.setOptions({
  breaks: true,
  gfm: true,
})

/**
 * 渲染 Markdown 为安全的 HTML(DOMPurify 清洗,防 XSS)
 * 用于对话回答/引用内容的展示
 */
export function renderMarkdown(src: string): string {
  if (!src) return ''
  const html = marked.parse(src) as string
  return DOMPurify.sanitize(html)
}
