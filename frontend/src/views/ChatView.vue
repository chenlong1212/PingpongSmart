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
import { ref, nextTick, watch } from 'vue'
import { Message } from '@element-plus/icons-vue'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const currentContent = ref('')
const messagesRef = ref(null)

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
 * 发送消息
 */
const sendMessage = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  // 1. 添加用户消息
  messages.value.push({ role: 'user', content: text })
  input.value = ''

  // 2. 设置加载状态
  loading.value = true
  currentContent.value = ''

  try {
    // 3. 发起 SSE 请求
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text })
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    // 4. 读取 SSE 流
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk

      // 按行分割处理
      const lines = buffer.split('\n')
      buffer = lines.pop() // 保留最后一个不完整的行

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue

        // Remove SSE "data:" prefix if present
        const jsonStr = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed

        try {
          const data = JSON.parse(jsonStr)
          if (data.done) {
            // 完成：将完整回复加入消息列表
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

          // 如果是错误消息（content 且 done），同样处理
          if (data.content && data.done) {
            if (currentContent.value) {
              messages.value.push({ role: 'assistant', content: currentContent.value })
              currentContent.value = ''
            }
            loading.value = false
          }
        } catch (e) {
          // 跳过解析失败的行
        }
      }
    }
  } catch (err) {
    console.error('SSE request failed:', err)
    // 显示错误
    if (currentContent.value) {
      messages.value.push({ role: 'assistant', content: currentContent.value })
      currentContent.value = ''
    }
    loading.value = false
  }
}
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
