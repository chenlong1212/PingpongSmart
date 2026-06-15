<template>
  <div v-if="username" class="app-container">
    <ChatView />
  </div>
  <div v-else class="app-container">
    <el-dialog
      v-model="showDialog"
      title="欢迎使用 PingpongSmart"
      width="400px"
      :close-on-click-modal="false"
      @opened="focusInput"
    >
      <el-input
        v-model="inputName"
        placeholder="请输入您的昵称"
        @keyup.enter="submitName"
        ref="inputRef"
      />
      <template #footer>
        <el-button type="primary" @click="submitName" :disabled="!inputName.trim()">
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import ChatView from './views/ChatView.vue'

const username = ref(localStorage.getItem('pp_username') || '')
const showDialog = ref(!username.value)
const inputName = ref('')
const inputRef = ref(null)

onMounted(() => {
  if (username.value) {
    showDialog.value = false
  } else {
    showDialog.value = true
  }
})

const focusInput = async () => {
  await nextTick()
  if (inputRef.value) {
    inputRef.value.focus()
  }
}

const submitName = () => {
  const name = inputName.value.trim()
  if (!name) return
  localStorage.setItem('pp_username', name)
  username.value = name
}
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: #f5f5f5;
}

.app-container {
  min-height: 100vh;
}
</style>
