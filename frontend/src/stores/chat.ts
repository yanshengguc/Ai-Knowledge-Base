import { defineStore } from 'pinia'
import { chat as apiChat, clearChat as apiClearChat } from '@/api/modules/knowledge'
import { getToken } from '@/utils/auth'
import type { ChatResponse, ToolTraceEvent } from '@/types/api'

export interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  references?: ChatResponse['references']
  loading?: boolean
  /** 流式生成中(首 token 到 refs 之间),用于打字机光标 */
  streaming?: boolean
  /** Agent 模式工具调用时间线(SSE tool 事件顺序追加) */
  toolCalls?: ToolTraceEvent[]
}

// SSE 流式接口地址:与 axios baseURL 同源(来自 .env.*),开发走 vite 代理,生产走 nginx 反代
const SSE_URL = `${import.meta.env.VITE_API_BASE ?? '/api'}/chat/stream`

// 工具名 → 时间线友好标签(未识别的工具原样展示)
const TOOL_LABELS: Record<string, string> = {
  file_search: '🔍 知识库检索',
  file_trace: '🧾 文件追溯',
  time_now: '⏰ 查询时间',
  knowledge_stats: '📊 知识库统计',
  web_search: '🌐 联网搜索',
  remember: '🧠 写入记忆',
}

export function toolLabel(name: string): string {
  return TOOL_LABELS[name] ?? name
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as MessageItem[],
    sending: false,
  }),
  actions: {
    async send(message: string) {
      this.messages.push({ role: 'user', content: message })
      this.sending = true
      const loadingMsg: MessageItem = { role: 'assistant', content: '', loading: true }
      this.messages.push(loadingMsg)
      try {
        const res = await apiChat(message)
        loadingMsg.content = res.data.answer
        loadingMsg.references = res.data.references
        loadingMsg.loading = false
      } catch (e) {
        loadingMsg.content = '抱歉,回答失败,请稍后再试。'
        loadingMsg.loading = false
      } finally {
        this.sending = false
      }
    },
    /** 流式发送(SSE 打字机效果):token 增量拼接 + 结束带引用来源;Agent 模式追加 tool 事件时间线 */
    async sendStream(message: string, enableWebSearch = false, enableAgent = false) {
      this.messages.push({ role: 'user', content: message })
      this.sending = true
      const assistantMsg: MessageItem = { role: 'assistant', content: '', loading: true }
      if (enableAgent) {
        assistantMsg.toolCalls = []
      }
      this.messages.push(assistantMsg)

      try {
        const resp = await fetch(SSE_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getToken()}`,
          },
          body: JSON.stringify({ message, enableWebSearch, enableAgent }),
        })
        if (!resp.ok || !resp.body) {
          throw new Error(`stream failed: ${resp.status}`)
        }

        // 业务错误(BusinessException 走 HTTP 200 + body code 500,如每日配额用尽):
        // 响应不是事件流,读 JSON 把原因显示在对话气泡里,避免静默卡住
        const contentType = resp.headers.get('content-type') || ''
        if (contentType.includes('application/json')) {
          const result = await resp.json()
          assistantMsg.content = result.message || '回答失败,请稍后再试。'
          return
        }

        const reader = resp.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          // 解析 SSE 事件(以空行分隔)
          const events = buffer.split('\n\n')
          buffer = events.pop() || ''
          for (const evt of events) {
            const dataLine = evt.split('\n').find((l) => l.startsWith('data:'))
            if (!dataLine) continue
            const data = dataLine.slice(5).trim()
            if (!data || data === '[DONE]') continue
            // 按事件名区分:refs 是 JSON 数组,tool 是工具轨迹 JSON,其余为 token 文本。
            // 注意 Spring SseEmitter 输出的是 "event:refs"(无空格),不能用 includes('event: refs')
            if (/^event:\s*refs$/m.test(evt)) {
              try {
                assistantMsg.references = JSON.parse(data)
              } catch {
                /* 忽略解析失败 */
              }
              assistantMsg.streaming = false
            } else if (/^event:\s*tool$/m.test(evt)) {
              try {
                if (!assistantMsg.toolCalls) assistantMsg.toolCalls = []
                assistantMsg.toolCalls.push(JSON.parse(data) as ToolTraceEvent)
              } catch {
                /* 忽略解析失败 */
              }
            } else {
              assistantMsg.content += data
              assistantMsg.loading = false
              assistantMsg.streaming = true
            }
          }
        }
      } catch (e) {
        assistantMsg.content = '抱歉,回答失败,请稍后再试。'
      } finally {
        assistantMsg.loading = false
        assistantMsg.streaming = false
        this.sending = false
      }
    },
    async clear() {
      await apiClearChat()
      this.messages = []
    },
  },
})
