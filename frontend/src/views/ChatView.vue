<template>
  <div class="chat-view">
    <!-- 消息列表 -->
    <div class="messages-container" ref="messagesRef">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message-row"
        :class="msg.role"
      >
        <div class="bubble" :class="msg.role">
          <div class="bubble-content">{{ msg.content }}</div>
        </div>
      </div>

      <!-- 当前正在接收的回复（打字机效果） -->
      <div v-if="currentContent !== ''" class="message-row assistant">
        <div class="bubble assistant">
          <div class="bubble-content">{{ currentContent }}</div>
        </div>
      </div>

      <!-- 思考中状态 -->
      <div v-if="loading" class="thinking-row">
        <div class="bubble assistant">
          <span class="thinking-dots">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </span>
          <span class="thinking-text">思考中...</span>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-container">
      <el-button
        type="primary"
        plain
        size="small"
        @click="newConversation"
        class="new-chat-btn"
      >
        新对话
      </el-button>
      <el-input
        v-model="input"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="请输入问题...（按 Enter 发送，Shift+Enter 换行）"
        :disabled="loading"
        @keydown.enter.exact.prevent="sendMessage"
        class="chat-input"
        resize="none"
      />
      <el-button
        type="primary"
        :disabled="loading || !input.trim()"
        @click="sendMessage"
        class="send-btn"
        :icon="Message"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { Message } from '@element-plus/icons-vue'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const currentContent = ref('')
const messagesRef = ref(null)

// Session ID: from localStorage or generate new UUID
const sessionId = ref(localStorage.getItem('pp_session_id') || '')
if (!sessionId.value) {
  sessionId.value = generateUUID()
  localStorage.setItem('pp_session_id', sessionId.value)
}

/**
 * Generate a simple UUID v4.
 */
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 监听 currentContent 变化，自动滚动
watch(currentContent, () => {
  scrollToBottom()
})

/**
 * Load conversation history from the server.
 */
const loadHistory = async () => {
  try {
    const response = await fetch(
      `/api/chat/history?sessionId=${sessionId.value}&limit=20`
    )
    if (!response.ok) return
    const data = await response.json()
    messages.value = data.messages || []
    scrollToBottom()
  } catch (err) {
    console.error('Failed to load history:', err)
  }
}

/**
 * Send a message with conversation context.
 */
const sendMessage = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  // 1. Add user message to frontend list
  messages.value.push({ role: 'user', content: text })
  input.value = ''

  // 2. Set loading state
  loading.value = true
  currentContent.value = ''

  try {
    // 3. SSE request with sessionId
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: text,
        sessionId: sessionId.value
      })
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    // 4. Read SSE stream
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk

      // Process line by line
      const lines = buffer.split('\n')
      buffer = lines.pop() // Keep last incomplete line

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue

        // Remove SSE "data:" prefix if present
        const jsonStr = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed

        try {
          const data = JSON.parse(jsonStr)
          if (data.done) {
            // Complete: add full reply to message list
            if (currentContent.value) {
              messages.value.push({ role: 'assistant', content: currentContent.value })
              currentContent.value = ''
            }
            loading.value = false
            return
          }

          if (data.content) {
            currentContent.value += data.content
          }

          // Error message handling
          if (data.content && data.done) {
            if (currentContent.value) {
              messages.value.push({ role: 'assistant', content: currentContent.value })
              currentContent.value = ''
            }
            loading.value = false
          }
        } catch (e) {
          // Skip lines that fail JSON parsing
        }
      }
    }
  } catch (err) {
    console.error('SSE request failed:', err)
    // Show error message
    if (currentContent.value) {
      messages.value.push({ role: 'assistant', content: currentContent.value })
      currentContent.value = ''
    }
    loading.value = false
  }
}

/**
 * Start a new conversation.
 */
const newConversation = () => {
  sessionId.value = generateUUID()
  localStorage.setItem('pp_session_id', sessionId.value)
  messages.value = []
}

// Load history on mount
onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 800px;
  margin: 0 auto;
  background: #fff;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
  width: 100%;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  word-break: break-word;
}

.bubble .bubble-content {
  line-height: 1.6;
  font-size: 15px;
}

.bubble.user {
  background-color: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble.assistant {
  background-color: #f0f2f5;
  color: #333;
  border-top-left-radius: 4px;
}

.thinking-row {
  display: flex;
  width: 100%;
  justify-content: flex-start;
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
  margin-right: 8px;
}

.thinking-dots .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #909399;
  animation: bounce 1.4s infinite ease-in-out;
}

.thinking-dots .dot:nth-child(1) { animation-delay: 0s; }
.thinking-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.thinking-text {
  color: #909399;
  font-size: 14px;
}

.input-container {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: #fff;
}

.new-chat-btn {
  flex-shrink: 0;
  border-radius: 8px;
}

.chat-input {
  flex: 1;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: 8px;
  padding: 10px 14px;
}

.send-btn {
  border-radius: 8px;
  min-width: 70px;
}
</style>
