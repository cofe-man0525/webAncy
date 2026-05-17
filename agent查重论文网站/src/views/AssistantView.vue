<template>
  <section class="assistant-page">
    <aside class="assistant-sidebar">
      <div class="assistant-side-head">
        <div>
          <p class="eyebrow">论文助手</p>
          <h2>会话记录</h2>
        </div>
        <el-button type="primary" plain @click="newSession">新会话</el-button>
      </div>

      <div class="assistant-session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="assistant-session"
          :class="{ active: session.id === currentSessionId }"
          @click="selectSession(session.id)"
        >
          <strong>{{ session.title }}</strong>
          <span>{{ session.updatedAt }}</span>
        </button>
        <el-empty v-if="!sessions.length" description="暂无会话" />
      </div>
    </aside>

    <main class="assistant-main">
      <header class="assistant-header">
        <div>
          <p class="eyebrow">论文写作小助手</p>
          <h1>围绕论文修改、语句写法和报告解读提问</h1>
        </div>
        <el-tag :type="modelReady ? 'success' : 'warning'" effect="light">
          {{ modelReady ? `当前模型：${settings.llmModel}` : '请先配置个人模型' }}
        </el-tag>
      </header>

      <section ref="messageBoxRef" class="assistant-messages">
        <div v-if="!messages.length" class="assistant-empty">
          <h2>可以这样问我</h2>
          <button v-for="item in prompts" :key="item" @click="message = item">{{ item }}</button>
        </div>

        <article v-for="item in messages" :key="item.id" class="assistant-message" :class="item.role">
          <div class="message-bubble">
            <strong>{{ item.role === 'user' ? '我' : '论文助手' }}</strong>
            <p v-if="item.content" :class="{ streaming: item.streaming }">{{ item.content }}</p>
            <div v-else-if="item.loading" class="typing-dots" aria-label="AI 正在思考">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <small v-if="item.imageName">图片：{{ item.imageName }}</small>
            <small v-if="item.imageInfo">图片分析：{{ item.imageInfo }}</small>
            <small v-if="item.model">模型：{{ item.model }}</small>
          </div>
        </article>
      </section>

      <footer class="assistant-composer">
        <div v-if="selectedImage" class="selected-image-pill">
          <span>{{ selectedImage.name }}</span>
          <el-button text @click="clearImage">移除</el-button>
        </div>

        <div class="composer-box">
          <el-input
            v-model="message"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            resize="none"
            placeholder="输入论文修改问题，例如：这段话怎么写得更学术？"
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer-tools">
            <input ref="imageInputRef" class="hidden-file" type="file" accept="image/*" @change="handleImageChange" />
            <el-tooltip content="上传论文截图或图片">
              <el-button :icon="Picture" circle @click="imageInputRef?.click()" />
            </el-tooltip>
            <el-button type="primary" :icon="Promotion" :loading="sending" @click="send">发送</el-button>
          </div>
        </div>
      </footer>
    </main>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Promotion } from '@element-plus/icons-vue'
import { getAssistantMessages, getAssistantSessions, sendAssistantMessage } from '../services/assistantApi'
import { getUserSettings } from '../services/settingsApi'

const sessions = ref([])
const messages = ref([])
const currentSessionId = ref('')
const message = ref('')
const selectedImage = ref(null)
const sending = ref(false)
const imageInputRef = ref(null)
const messageBoxRef = ref(null)

const settings = reactive({
  llmModel: '',
  llmApiKeyConfigured: false
})

const modelReady = computed(() => Boolean(settings.llmModel && settings.llmApiKeyConfigured))

const prompts = [
  '帮我把这句话改得更学术、更自然',
  '这段论文表达为什么像 AI 写的？',
  '根据我的历史报告，帮我总结常见问题',
  '我上传一张论文截图，请帮我分析表达问题'
]

async function loadSessions() {
  sessions.value = await getAssistantSessions()
}

async function selectSession(sessionId) {
  currentSessionId.value = sessionId
  messages.value = await getAssistantMessages(sessionId)
  scrollToBottom()
}

function newSession() {
  currentSessionId.value = ''
  messages.value = []
  message.value = ''
  clearImage()
}

function handleImageChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片')
    return
  }
  selectedImage.value = file
}

function clearImage() {
  selectedImage.value = null
  if (imageInputRef.value) {
    imageInputRef.value.value = ''
  }
}

async function send() {
  if (sending.value) return
  const userText = message.value.trim()
  const imageFile = selectedImage.value

  if (!userText && !imageFile) {
    ElMessage.warning('请输入问题或上传图片')
    return
  }
  if (!modelReady.value) {
    ElMessage.warning('请先到个人设置中配置大模型 API Key 和模型名称')
    return
  }

  const formData = new FormData()
  if (currentSessionId.value) {
    formData.append('sessionId', currentSessionId.value)
  }
  formData.append('message', userText)
  if (imageFile) {
    formData.append('image', imageFile)
  }

  const localUserId = `local-user-${Date.now()}`
  const localAssistantId = `local-assistant-${Date.now()}`
  messages.value.push({
    id: localUserId,
    role: 'user',
    content: userText || '请分析这张图片中的论文内容',
    imageName: imageFile?.name || '',
    createdAt: new Date().toLocaleString('zh-CN', { hour12: false })
  })
  messages.value.push({
    id: localAssistantId,
    role: 'assistant',
    content: '',
    loading: true,
    streaming: true,
    model: settings.llmModel
  })

  message.value = ''
  clearImage()
  scrollToBottom()
  sending.value = true

  try {
    const result = await sendAssistantMessage(formData)
    currentSessionId.value = result.sessionId

    const userIndex = messages.value.findIndex((item) => item.id === localUserId)
    if (userIndex >= 0) {
      messages.value[userIndex] = result.userMessage
    }

    const assistantIndex = messages.value.findIndex((item) => item.id === localAssistantId)
    if (assistantIndex >= 0) {
      messages.value[assistantIndex] = {
        ...result.assistantMessage,
        content: '',
        loading: false,
        streaming: true
      }
      await streamAssistantText(assistantIndex, result.assistantMessage.content || '')
    }

    await loadSessions()
  } catch (error) {
    const assistantIndex = messages.value.findIndex((item) => item.id === localAssistantId)
    if (assistantIndex >= 0) {
      messages.value[assistantIndex] = {
        id: localAssistantId,
        role: 'assistant',
        content: error.message || '论文助手调用失败',
        loading: false,
        streaming: false
      }
    }
    ElMessage.error(error.message || '论文助手调用失败')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function streamAssistantText(index, text) {
  const chunkSize = text.length > 900 ? 6 : text.length > 360 ? 4 : 2
  for (let offset = 0; offset < text.length; offset += chunkSize) {
    const target = messages.value[index]
    if (!target) return
    target.content += text.slice(offset, offset + chunkSize)
    scrollToBottom()
    await sleep(16)
  }
  if (messages.value[index]) {
    messages.value[index].streaming = false
  }
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function scrollToBottom() {
  nextTick(() => {
    if (messageBoxRef.value) {
      messageBoxRef.value.scrollTop = messageBoxRef.value.scrollHeight
    }
  })
}

onMounted(async () => {
  Object.assign(settings, await getUserSettings())
  await loadSessions()
})
</script>
