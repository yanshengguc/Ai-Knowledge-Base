<template>
  <div class="chat-page">
    <!-- 消息流 -->
    <div ref="messageListRef" class="message-list">
      <div v-if="messages.length === 0" class="empty">
        <el-empty :description="t('chat.empty')" />
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg-row', msg.role]">
        <div class="msg-bubble">
          <div v-if="msg.loading" class="msg-loading">
            <span class="dot" />{{ t('chat.thinking') }}...
          </div>
          <div v-else class="msg-content markdown-body" v-html="renderMarkdown(msg.content)" />

          <!-- 引用来源 -->
          <div v-if="msg.references && msg.references.length" class="msg-refs">
            <div class="refs-title">📎 {{ t('chat.references') }}</div>
            <el-collapse>
              <el-collapse-item
                v-for="(ref, ri) in msg.references.slice(0, 3)"
                :key="ri"
                :title="`${t('chat.material')} ${ri + 1}`"
              >
                <div class="ref-content markdown-body" v-html="renderMarkdown(ref.content)" />
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-toolbar">
        <el-switch
          v-model="webSearchOn"
          :active-text="t('chat.webSearch')"
          size="small"
          class="web-search-switch"
        />
        <span class="web-search-hint" v-if="webSearchOn">{{ t('chat.webSearchHint') }}</span>
      </div>
      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          :disabled="chatStore.sending"
          :placeholder="t('chat.placeholder')"
          @keydown.enter.exact.prevent="onSend"
        />
        <div class="actions">
          <el-button text :icon="Delete" @click="onClear">{{ t('common.clear') }}</el-button>
          <el-button type="primary" :loading="chatStore.sending" :disabled="!input.trim()" @click="onSend">
            {{ t('common.send') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { getChatHistory } from '@/api/modules/chat'
import { renderMarkdown } from '@/utils/markdown'
import { useI18n } from 'vue-i18n'

const chatStore = useChatStore()
const { t } = useI18n()
const { messages } = storeToRefs(chatStore)
const input = ref('')
const webSearchOn = ref(false)
const messageListRef = ref<HTMLElement>()

// 刷新后从后端恢复会话历史(Redis 存 chat:{userId})
onMounted(async () => {
  try {
    const res = await getChatHistory()
    const history = res.data || []
    if (history.length) {
      chatStore.messages = history.map((h) => ({ role: h.role, content: h.content }))
      scrollToBottom()
    }
  } catch {
    // 拦截器已提示,刷新后无历史可接受
  }
})

async function onSend() {
  const msg = input.value.trim()
  if (!msg || chatStore.sending) return
  input.value = ''
  await chatStore.sendStream(msg, webSearchOn.value)
  scrollToBottom()
}

async function onClear() {
  if (chatStore.messages.length === 0) return
  await ElMessageBox.confirm(t('chat.clearConfirm'), t('chat.clearTitle'), { type: 'warning' })
  try {
    await chatStore.clear()
    ElMessageBox.close()
  } catch {
    // 取消
  }
}

function scrollToBottom() {
  nextTick(() => {
    messageListRef.value?.scrollTo({ top: messageListRef.value.scrollHeight, behavior: 'smooth' })
  })
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 860px;
  margin: 0 auto;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: $space-4 $space-2;
}

.empty {
  padding-top: $space-12;
}

.msg-row {
  display: flex;
  margin-bottom: $space-4;

  &.user {
    justify-content: flex-end;

    .msg-bubble {
      background: $color-primary;
      color: #fff;
      border-radius: $radius-md $radius-md $radius-sm $radius-md;
    }
  }

  &.assistant {
    justify-content: flex-start;

    .msg-bubble {
      background: $color-bg-card;
      border: 1px solid $color-border;
      border-radius: $radius-md $radius-md $radius-md $radius-sm;
    }
  }
}

.msg-bubble {
  max-width: 80%;
  padding: $space-3 $space-4;
  box-shadow: $shadow-card;
  word-break: break-word;
}

.msg-content {
  line-height: 1.7;
  white-space: pre-wrap;
}

.msg-loading {
  color: $color-text-muted;

  .dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    margin-right: 8px;
    border-radius: 50%;
    background: $color-primary;
    animation: pulse 1s infinite;
  }

  @keyframes pulse {
    0%,
    100% {
      opacity: 0.3;
    }
    50% {
      opacity: 1;
    }
  }
}

.msg-refs {
  margin-top: $space-3;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding-top: $space-2;

  .refs-title {
    font-size: $font-size-xs;
    color: $color-text-muted;
    margin-bottom: $space-2;
  }

  .ref-content {
    font-size: $font-size-xs;
    color: $color-text-secondary;
    max-height: 100px;
    overflow-y: auto;
  }
}

.input-area {
  padding: $space-3 0 $space-2;

  .input-toolbar {
    display: flex;
    align-items: center;
    gap: $space-3;
    margin-bottom: $space-2;
    min-height: 24px;

    .web-search-hint {
      font-size: $font-size-xs;
      color: $color-text-secondary;
    }
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    gap: $space-2;
    margin-top: $space-2;
  }
}
.markdown-body {
  word-break: break-word;
  line-height: 1.7;

  :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { padding-left: 1.4em; margin: 0 0 8px; }
  :deep(pre) {
    background: $color-bg;
    border: 1px solid $color-border;
    border-radius: $radius-sm;
    padding: $space-3;
    overflow-x: auto;
    font-size: $font-size-xs;
    margin: 0 0 8px;
  }
  :deep(code) {
    font-family: $font-family-mono;
    background: $color-bg;
    border-radius: 3px;
    padding: 1px 4px;
    font-size: 0.92em;
  }
  :deep(pre code) { background: transparent; padding: 0; }
  :deep(h1), :deep(h2), :deep(h3) { font-weight: 600; margin: 12px 0 8px; }
  :deep(h1) { font-size: 1.15em; }
  :deep(h2) { font-size: 1.08em; }
  :deep(h3) { font-size: 1em; }
  :deep(blockquote) {
    border-left: 3px solid $color-border;
    padding-left: $space-3;
    color: $color-text-secondary;
    margin: 0 0 8px;
  }
  :deep(a) { color: $color-primary; }
  :deep(table) { border-collapse: collapse; margin: 0 0 8px; }
  :deep(th), :deep(td) { border: 1px solid $color-border; padding: 4px 8px; font-size: $font-size-xs; }
}
</style>
