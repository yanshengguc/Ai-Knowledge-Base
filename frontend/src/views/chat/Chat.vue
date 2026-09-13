<template>
  <div class="chat-page">
    <!-- 消息流 -->
    <div ref="messageListRef" class="message-list" @scroll="onMessageScroll">
      <div v-if="messages.length === 0" class="empty">
        <el-empty :description="t('chat.empty')" />
      </div>

      <div v-for="(msg, idx) in messages" :key="msg.id" :class="['msg-row', msg.role]">
        <div class="msg-bubble">
          <div v-if="msg.request?.enableAgent" class="agent-status" :class="{ active: msg.loading }">
            <span class="agent-status-dot" aria-hidden="true" />
            <span>
              {{ msg.loading
                ? (msg.toolCalls?.length ? t('chat.agentRunning') : t('chat.agentThinking'))
                : t('chat.agentDone') }}
            </span>
            <span v-if="msg.toolCalls?.length" class="agent-status-meta">
              {{ t('chat.toolCount', { n: msg.toolCalls.length }) }}
            </span>
          </div>

          <!-- Agent 工具调用时间线(ReAct 循环可视:模型自主决策 → 工具执行 → 结果回传) -->
          <div v-if="msg.toolCalls && msg.toolCalls.length" class="msg-tool-trace">
            <div class="trace-header">
              <span class="trace-title">
                <el-icon aria-hidden="true"><SetUp /></el-icon>
                <span>{{ t('chat.agentTrace') }}</span>
              </span>
              <span class="trace-count">{{ t('chat.toolCount', { n: msg.toolCalls.length }) }}</span>
            </div>
            <div
              v-for="tc in msg.toolCalls"
              :key="`${tc.step}-${tc.tool}`"
              class="tool-step"
            >
              <span class="tool-step-number" aria-hidden="true">{{ tc.step }}</span>
              <span class="tool-step-body">
                <span class="tool-step-label">
                  <el-icon class="tool-step-icon" aria-hidden="true">
                    <component :is="toolIcon(tc.tool)" />
                  </el-icon>
                  <span>{{ toolLabel(tc.tool) }}</span>
                </span>
                <span v-if="tc.summary" class="tool-step-summary" :title="tc.summary">
                  {{ tc.summary }}
                </span>
              </span>
              <span class="tool-step-state">{{ t('chat.toolDone') }}</span>
            </div>
          </div>

          <div v-if="msg.loading && !msg.content" class="msg-loading">
            <span class="dot" />{{ msg.toolCalls?.length ? t('chat.agentRunning') : t('chat.thinking') }}...
          </div>
          <div v-if="msg.content" class="msg-content markdown-body">
            <span v-html="renderMarkdown(msg.content)" />
            <span v-if="msg.streaming" class="stream-cursor" />
          </div>
          <div v-if="msg.stopped" class="msg-status stopped">
            <span>{{ t('chat.generationStopped') }}</span>
            <el-button
              v-if="msg.retryable"
              size="small"
              text
              type="primary"
              :icon="RefreshRight"
              @click="onRetry(idx)"
            >
              {{ t('chat.retry') }}
            </el-button>
          </div>
          <div v-if="msg.error" class="msg-status error">
            <span>{{ t('chat.answerFailed') }}</span>
            <el-button
              v-if="msg.retryable"
              size="small"
              text
              type="primary"
              :icon="RefreshRight"
              @click="onRetry(idx)"
            >
              {{ t('chat.retry') }}
            </el-button>
          </div>

          <!-- 引用来源(文件 + 切片级溯源) -->
          <div v-if="msg.references && msg.references.length" class="msg-refs">
            <div class="refs-title">
              <el-icon aria-hidden="true"><DocumentCopy /></el-icon>
              <span>{{ t('chat.references') }}</span>
            </div>
            <el-collapse>
              <el-collapse-item
                v-for="(ref, ri) in msg.references.slice(0, 3)"
                :key="`${ref.fileId ?? ref.fileName ?? 'ref'}-${ref.chunkIndex ?? ri}`"
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
      <button
        v-if="showScrollButton"
        type="button"
        class="scroll-bottom"
        :aria-label="t('chat.jumpToLatest')"
        @click="scrollToBottom(true)"
      >
        ↓ {{ t('chat.jumpToLatest') }}
      </button>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-toolbar">
        <div class="toolbar-heading">
          <span class="toolbar-title">{{ t('chat.answerOptions') }}</span>
          <button
            type="button"
            class="mobile-tools-toggle"
            :aria-expanded="toolsExpanded"
            @click="toolsExpanded = !toolsExpanded"
          >
            {{ activeModeCount ? t('chat.activeModes', { n: activeModeCount }) : t('chat.noModes') }}
            <el-icon :class="{ rotated: toolsExpanded }"><ArrowDown /></el-icon>
          </button>
        </div>
        <div class="toolbar-options" :class="{ expanded: toolsExpanded }">
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
          <el-button text :icon="Delete" :disabled="chatStore.sending || messages.length === 0" @click="onClear">
            {{ t('common.clear') }}
          </el-button>
          <el-button v-if="chatStore.sending" type="warning" plain :icon="VideoPause" @click="onStop">
            {{ t('chat.stop') }}
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!input.trim()"
            @click="onSend"
          >
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Clock,
  CollectionTag,
  DataAnalysis,
  Delete,
  Document,
  DocumentCopy,
  RefreshRight,
  Search,
  SetUp,
  VideoPause,
} from '@element-plus/icons-vue'
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
const toolsExpanded = ref(false)
const showScrollButton = ref(false)
const messageListRef = ref<HTMLElement>()
const activeModeCount = computed(() => Number(webSearchOn.value) + Number(agentOn.value))

const TOOL_ICONS = {
  file_search: Search,
  file_trace: Document,
  time_now: Clock,
  knowledge_stats: DataAnalysis,
  web_search: Search,
  remember: CollectionTag,
}

function toolIcon(name: string) {
  return TOOL_ICONS[name as keyof typeof TOOL_ICONS] ?? SetUp
}

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
      chatStore.messages = history.map((h) => ({
        id: `history-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`,
        role: h.role,
        content: h.content,
      }))
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
  toolsExpanded.value = false
  scrollToBottom()
  await chatStore.sendStream(msg, webSearchOn.value, agentOn.value)
  scrollToBottom()
}

function onStop() {
  if (chatStore.stop()) ElMessage.info(t('chat.stopped'))
}

async function onRetry(idx: number) {
  if (chatStore.sending) return
  scrollToBottom()
  await chatStore.retry(idx)
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

function onMessageScroll() {
  const el = messageListRef.value
  if (!el) return
  showScrollButton.value = el.scrollHeight - el.scrollTop - el.clientHeight > 120
}

function scrollToBottom(smooth = false) {
  nextTick(() => {
    const el = messageListRef.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
    showScrollButton.value = false
  })
}

// 流式内容变化时仅在用户仍停留在底部才跟随,避免打断用户阅读历史消息
watch(
  () => messages.value[messages.value.length - 1]?.content,
  () => {
    if (!showScrollButton.value) scrollToBottom()
  },
)
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.chat-page {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
  max-width: 860px;
  margin: 0 auto;
}

.message-list {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: $space-4 $space-2;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.scroll-bottom {
  position: sticky;
  z-index: 2;
  bottom: $space-3;
  display: block;
  margin: -44px auto $space-2;
  padding: 6px $space-3;
  border: 1px solid $color-primary;
  border-radius: 999px;
  background: $color-bg-card;
  color: $color-primary;
  box-shadow: $shadow-pop;
  cursor: pointer;
  font: inherit;
  font-size: $font-size-xs;
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
  min-width: 0;
  max-width: 80%;
  padding: $space-3 $space-4;
  box-shadow: $shadow-card;
  word-break: break-word;
}

.agent-status {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-bottom: $space-2;
  color: $color-text-secondary;
  font-size: $font-size-xs;
  font-weight: 600;

  &.active {
    color: $color-primary;
  }

  .agent-status-dot {
    width: 7px;
    height: 7px;
    flex: 0 0 7px;
    border-radius: 50%;
    background: $color-success;
  }

  &.active .agent-status-dot {
    background: $color-primary;
    animation: pulse 1s infinite;
  }

  .agent-status-meta {
    margin-left: auto;
    color: $color-text-muted;
    font-weight: 400;
  }
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

  .trace-header {
    display: flex;
    justify-content: space-between;
    gap: $space-2;
    margin-bottom: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    font-weight: 600;

    .trace-title {
      display: inline-flex;
      align-items: center;
      gap: $space-1;
    }

    .trace-count {
      color: $color-text-muted;
      font-weight: 400;
    }
  }

  .tool-step {
    display: flex;
    align-items: flex-start;
    gap: $space-2;
    padding: 5px 0;
    font-size: $font-size-xs;
    line-height: 1.5;

    .tool-step-number {
      display: inline-flex;
      width: 18px;
      height: 18px;
      flex: 0 0 18px;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      background: $color-primary-light;
      color: $color-primary;
      font-size: 11px;
      font-variant-numeric: tabular-nums;
    }

    .tool-step-body {
      display: flex;
      min-width: 0;
      flex: 1;
      flex-direction: column;
      gap: 1px;
    }

    .tool-step-label {
      display: inline-flex;
      align-items: center;
      gap: $space-1;
      color: $color-text;
      font-weight: 600;

      .tool-step-icon {
        flex: 0 0 auto;
        color: $color-primary;
        font-size: 14px;
      }
    }

    .tool-step-summary {
      overflow: hidden;
      color: $color-text-secondary;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .tool-step-state {
      flex-shrink: 0;
      color: $color-success;
      font-size: 11px;
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
    display: inline-flex;
    align-items: center;
    gap: $space-1;
    color: $color-text-muted;
    font-size: $font-size-xs;
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

.msg-status {
  display: inline-flex;
  align-items: center;
  gap: $space-2;
  margin-top: $space-2;
  padding-top: $space-2;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  font-size: $font-size-xs;

  &.stopped {
    color: $color-text-secondary;
  }

  &.error {
    color: $color-danger;
  }
}

.msg-actions {
  margin-top: $space-2;
  padding-top: $space-2;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.input-area {
  padding: $space-3 0 max($space-2, env(safe-area-inset-bottom));
  background: $color-bg-card;

  .input-toolbar {
    margin-bottom: $space-2;

    .toolbar-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 24px;
    }

    .toolbar-title {
      color: $color-text-muted;
      font-size: $font-size-xs;
      font-weight: 600;
    }

    .mobile-tools-toggle {
      display: none;
      align-items: center;
      gap: $space-1;
      border: 0;
      padding: 2px 0;
      background: transparent;
      color: $color-primary;
      cursor: pointer;
      font: inherit;
      font-size: $font-size-xs;

      .el-icon {
        transition: transform $transition-fast;

        &.rotated {
          transform: rotate(180deg);
        }
      }
    }

    .toolbar-options {
      display: flex;
      align-items: center;
      gap: $space-3;
      min-height: 24px;

      .web-search-hint {
        color: $color-text-secondary;
        font-size: $font-size-xs;
      }

      .toolbar-right {
        display: inline-flex;
        align-items: center;
        margin-left: auto;
      }
    }
  }

  .input-row {
    min-width: 0;
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

@media (max-width: $bp-md) {
  .message-list {
    padding: $space-3 0 $space-4;
  }

  .msg-row {
    margin-bottom: $space-3;

    &.user .msg-bubble {
      max-width: 90%;
    }

    &.assistant .msg-bubble {
      max-width: 96%;
    }
  }

  .msg-bubble {
    padding: $space-3;
    border-radius: $radius-md;
  }

  .msg-tool-trace {
    padding: $space-2;

    .tool-step {
      gap: $space-1;

      .tool-step-summary {
        white-space: normal;
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
      }

      .tool-step-state {
        display: none;
      }
    }
  }

  .input-area {
    position: sticky;
    bottom: 0;
    z-index: 3;
    margin: 0 -8px;
    padding: $space-2 $space-2 max($space-2, env(safe-area-inset-bottom));
    border-top: 1px solid $color-border;
    box-shadow: 0 -4px 14px rgba(15, 23, 42, 0.05);

    .toolbar-title {
      font-size: 11px;
    }

    .mobile-tools-toggle {
      display: inline-flex;
    }

    .toolbar-options {
      display: none;
      flex-wrap: wrap;
      align-items: center;
      gap: $space-2;
      padding-top: $space-2;

      &.expanded {
        display: flex;
      }

      .web-search-hint {
        width: 100%;
        padding-left: 24px;
        line-height: 1.4;
      }

      .toolbar-right {
        width: 100%;
        margin-left: 0;
        padding-top: $space-1;
      }
    }

    .input-row :deep(.el-textarea__inner) {
      min-height: 48px !important;
      padding: 10px 12px;
      font-size: $font-size-md;
    }

    .actions {
      justify-content: space-between;
      margin-top: $space-1;

      .el-button {
        min-height: 36px;
      }
    }
  }

  .markdown-body {
    :deep(pre) {
      max-width: 100%;
    }

    :deep(table) {
      display: block;
      max-width: 100%;
      overflow-x: auto;
      white-space: nowrap;
    }
  }
}
</style>
