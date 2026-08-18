import { defineStore } from 'pinia'
import { chat as apiChat, clearChat as apiClearChat } from '@/api/modules/knowledge'
import type { ChatResponse } from '@/types/api'

export interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  references?: ChatResponse['references']
  loading?: boolean
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
    async clear() {
      await apiClearChat()
      this.messages = []
    },
  },
})
