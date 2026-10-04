export interface HighlightSegment {
  text: string
  hit: boolean
}

/** 超长片段截断(仅用于预览展示,不改变原文语义) */
export function truncate(text: string, max: number): string {
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

/**
 * 将文本按查询词切分为安全片段,供模板插值渲染(不拼 v-html,规避 XSS)。
 * 大小写不敏感;query 去空白后为空时原样返回整段。
 */
export function splitByQuery(text: string, query: string): HighlightSegment[] {
  if (!text) return []
  const q = query.trim()
  if (!q) return [{ text, hit: false }]
  const hay = text.toLowerCase()
  const needle = q.toLowerCase()
  const out: HighlightSegment[] = []
  let from = 0
  let idx = hay.indexOf(needle, from)
  if (idx < 0) return [{ text, hit: false }]
  while (idx >= 0) {
    if (idx > from) out.push({ text: text.slice(from, idx), hit: false })
    out.push({ text: text.slice(idx, idx + q.length), hit: true })
    from = idx + q.length
    idx = hay.indexOf(needle, from)
  }
  if (from < text.length) out.push({ text: text.slice(from), hit: false })
  return out
}
