import { defineStore } from 'pinia'
import { chat as apiChat, clearChat as apiClearChat } from '@/api/modules/knowledge'
import { getToken } from '@/utils/auth'
import type { ChatResponse } from '@/types/api'

export interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  references?: ChatResponse['references']
  loading?: boolean
}

// SSE 流式接口地址:与 axios baseURL 同源(来自 .env.*),开发走 vite 代理,生产走 nginx 反代
const SSE_URL = `${import.meta.env.VITE_API_BASE ?? '/api'}/chat/stream`

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
    /** 流式发送(SSE 打字机效果):token 增量拼接 + 结束带引用来源 */
    async sendStream(message: string, enableWebSearch = false) {
      this.messages.push({ role: 'user', content: message })
      this.sending = true
      const assistantMsg: MessageItem = { role: 'assistant', content: '', loading: true }
      this.messages.push(assistantMsg)

      try {
        const resp = await fetch(SSE_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getToken()}`,
          },
          body: JSON.stringify({ message, enableWebSearch }),
        })
        if (!resp.ok || !resp.body) {
          throw new Error(`stream failed: ${resp.status}`)
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
            // 按事件名区分:refs 事件是 JSON 数组,其余为 token 文本。
            // 注意 Spring SseEmitter 输出的是 "event:refs"(无空格),不能用 includes('event: refs')
            if (/^event:\s*refs$/m.test(evt)) {
              try {
                assistantMsg.references = JSON.parse(data)
              } catch {
                /* 忽略解析失败 */
              }
            } else {
              assistantMsg.content += data
              assistantMsg.loading = false
            }
          }
        }
      } catch (e) {
        assistantMsg.content = '抱歉,回答失败,请稍后再试。'
      } finally {
        assistantMsg.loading = false
        this.sending = false
      }
    },
    async clear() {
      await apiClearChat()
      this.messages = []
    },
  },
})
