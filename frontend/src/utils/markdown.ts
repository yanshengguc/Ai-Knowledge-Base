import { marked, type Tokens } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import scss from 'highlight.js/lib/languages/scss'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'

// 按需注册白名单语言(避免全量包把 190+ 语言一起打进产物)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('xml', xml) // 兼顾 html 代码块
hljs.registerLanguage('css', css)
hljs.registerLanguage('scss', scss)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('markdown', markdown)

// marked 基础配置:GFM + 软换行
marked.setOptions({
  breaks: true,
  gfm: true,
})

/** HTML 转义(& < > " '),与 marked v18 默认 code renderer 语义一致 */
function escapeHtml(str: string): string {
  return str.replace(/[&<>"']/g, (ch) => {
    switch (ch) {
      case '&':
        return '&amp;'
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '"':
        return '&quot;'
      default:
        return '&#39;'
    }
  })
}

// 代码块渲染:白名单语言走 highlight.js,其余确定性降级为转义纯文本。
// 转义/换行语义照搬 marked v18 默认 code renderer,仅替换"生成高亮 HTML"这一段。
marked.use({
  renderer: {
    code({ text, lang, escaped }: Tokens.Code): string {
      const language = (lang || '').match(/^\S*/)?.[0] ?? ''
      const body = text.replace(/\n$/, '') + '\n'

      // escaped 为真说明 text 已是转义后的 HTML,直接走纯文本,避免重复转义
      if (language && !escaped) {
        try {
          if (hljs.getLanguage(language)) {
            const highlighted = hljs.highlight(body, { language }).value
            return `<pre><code class="hljs language-${escapeHtml(language)}">${highlighted}</code></pre>\n`
          }
        } catch {
          // 高亮失败:降级为纯文本(renderMarkdown 是同步模板调用,绝不能让异常外泄)
        }
      }

      const plain = escaped ? body : escapeHtml(body)
      const cls = language ? ` class="language-${escapeHtml(language)}"` : ''
      return `<pre><code${cls}>${plain}</code></pre>\n`
    },
  },
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
