<template>
  <div class="chat-page">
    <!-- 消息流 -->
    <div ref="messageListRef" class="message-list">
      <div v-if="messages.length === 0" class="empty">
        <el-empty description="向知识库提问,开始智能问答" />
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg-row', msg.role]">
        <div class="msg-bubble">
          <div v-if="msg.loading" class="msg-loading">
            <span class="dot" />正在思考...
          </div>
          <div v-else class="msg-content">{{ msg.content }}</div>

          <!-- 引用来源 -->
          <div v-if="msg.references && msg.references.length" class="msg-refs">
            <div class="refs-title">📎 引用来源</div>
            <el-collapse>
              <el-collapse-item
                v-for="(ref, ri) in msg.references.slice(0, 3)"
                :key="ri"
                :title="`资料 ${ri + 1}`"
              >
                <div class="ref-content">{{ ref.content }}</div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          :disabled="chatStore.sending"
          placeholder="输入问题,回车发送(Ctrl+Enter 换行)"
          @keydown.enter.exact.prevent="onSend"
        />
        <div class="actions">
          <el-button text :icon="Delete" @click="onClear">清空</el-button>
          <el-button type="primary" :loading="chatStore.sending" :disabled="!input.trim()" @click="onSend">
            发送
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

const chatStore = useChatStore()
const { messages } = storeToRefs(chatStore)
const input = ref('')
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
  await chatStore.send(msg)
  scrollToBottom()
}

async function onClear() {
  if (chatStore.messages.length === 0) return
  await ElMessageBox.confirm('确定清空当前对话吗?', '清空会话', { type: 'warning' })
  try {
    await chatStore.clear()
    ElMessageBox.closeAll()
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

  .actions {
    display: flex;
    justify-content: flex-end;
    gap: $space-2;
    margin-top: $space-2;
  }
}
</style>
