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
          <template v-else>
            <!-- Agent 工具调用时间线(ReAct 循环可视:模型自主决策 → 工具执行 → 结果回传) -->
            <div v-if="msg.toolCalls && msg.toolCalls.length" class="msg-tool-trace">
              <div
                v-for="(tc, ti) in msg.toolCalls"
                :key="ti"
                class="tool-step"
              >
                <span class="tool-step-label">
                  {{ t('chat.toolStep', { n: tc.step }) }} · {{ toolLabel(tc.tool) }}
                </span>
                <span v-if="tc.summary" class="tool-step-summary" :title="tc.summary">
                  {{ tc.summary }}
                </span>
              </div>
            </div>
            <div class="msg-content markdown-body">
              <span v-html="renderMarkdown(msg.content)" />
              <span v-if="msg.streaming" class="stream-cursor" />
            </div>
          </template>

          <!-- 引用来源(文件 + 切片级溯源) -->
          <div v-if="msg.references && msg.references.length" class="msg-refs">
            <div class="refs-title">📎 {{ t('chat.references') }}</div>
            <el-collapse>
              <el-collapse-item
                v-for="(ref, ri) in msg.references.slice(0, 3)"
                :key="ri"
              >
                <template #title>
                  <span class="ref-title">
                    <span class="ref-file">{{ ref.fileName || `${t('chat.material')} ${ri + 1}` }}</span>
                    <el-tag v-if="ref.chunkIndex != null" size="small" type="info" class="ref-chunk-tag">
                      #{{ ref.chunkIndex }}
                    </el-tag>
                  </span>
                </template>
                <div class="ref-content markdown-body" v-html="renderMarkdown(ref.content)" />
              </el-collapse-item>
            </el-collapse>
          </div>

          <!-- 操作区:复制回答 + 存为笔记(AI 回答沉淀,人机确认闭环) -->
          <div
            v-if="msg.role === 'assistant' && msg.content && !msg.loading"
            class="msg-actions"
          >
            <el-button size="small" text :icon="DocumentCopy" @click="onCopy(msg.content)">
              {{ t('chat.copy') }}
            </el-button>
            <el-button size="small" text :icon="CollectionTag" @click="openSaveNote(idx)">
              {{ t('chat.saveNote') }}
            </el-button>
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
        <el-switch
          v-model="agentOn"
          :active-text="t('chat.agentMode')"
          size="small"
          class="agent-switch"
        />
        <span class="web-search-hint" v-if="agentOn">{{ t('chat.agentHint') }}</span>
        <span class="toolbar-right">
          <TokenUsageStrip />
        </span>
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

  <!-- AI 回答存为笔记(可编辑 + 选目标知识条目) -->
  <SaveAsNoteDialog
    v-model:visible="saveNoteVisible"
    :answer="saveNoteAnswer"
    :question="saveNoteQuestion"
  />
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CollectionTag, Delete, DocumentCopy } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useChatStore, toolLabel } from '@/stores/chat'
import { getChatHistory } from '@/api/modules/chat'
import { renderMarkdown } from '@/utils/markdown'
import { useI18n } from 'vue-i18n'
import SaveAsNoteDialog from './components/SaveAsNoteDialog.vue'
import TokenUsageStrip from './components/TokenUsageStrip.vue'

const chatStore = useChatStore()
const { t } = useI18n()
const { messages } = storeToRefs(chatStore)
const input = ref('')
const webSearchOn = ref(false)
const agentOn = ref(false)
const messageListRef = ref<HTMLElement>()

// 复制回答(剪贴板 API 不可用时降级 execCommand,兼容非 https 环境)
async function onCopy(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success(t('chat.copied'))
  } catch {
    const ta = document.createElement('textarea')
    ta.value = content
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success(t('chat.copied'))
  }
}

// 存为笔记:记录触发的那条回答与对应问题
const saveNoteVisible = ref(false)
const saveNoteAnswer = ref('')
const saveNoteQuestion = ref('')

function openSaveNote(idx: number) {
  saveNoteAnswer.value = messages.value[idx]?.content || ''
  // 同一轮的 user 消息(向上找最近一条 user)
  const q = messages.value[idx - 1]
  saveNoteQuestion.value = q && q.role === 'user' ? q.content : ''
  saveNoteVisible.value = true
}

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
  await chatStore.sendStream(msg, webSearchOn.value, agentOn.value)
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
  // 不用 pre-wrap:markdown 已渲染成 HTML,pre-wrap 会把标签间换行也显示,
  // 与 <p> 的 margin 叠加成双倍空行
}

// 流式生成中的打字机光标
.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: $color-primary;
  animation: blink 0.9s step-end infinite;

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0; }
  }
}

// Agent 工具调用时间线(ReAct 循环可视)
.msg-tool-trace {
  margin-bottom: $space-3;
  padding: $space-2 $space-3;
  border-left: 3px solid $color-primary;
  background: $color-bg;
  border-radius: 0 $radius-sm $radius-sm 0;

  .tool-step {
    display: flex;
    align-items: baseline;
    gap: $space-2;
    padding: 2px 0;
    font-size: $font-size-xs;
    line-height: 1.5;

    .tool-step-label {
      flex-shrink: 0;
      color: $color-text;
      font-weight: 600;
    }

    .tool-step-summary {
      color: $color-text-secondary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
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

  .ref-title {
    display: inline-flex;
    align-items: center;
    gap: $space-1;
    min-width: 0;

    .ref-file {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .ref-chunk-tag {
      flex-shrink: 0;
      font-variant-numeric: tabular-nums;
    }
  }

  .ref-content {
    font-size: $font-size-xs;
    color: $color-text-secondary;
    max-height: 100px;
    overflow-y: auto;
  }
}

.msg-actions {
  margin-top: $space-2;
  padding-top: $space-2;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
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

    .toolbar-right {
      margin-left: auto;
      display: inline-flex;
      align-items: center;
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
