<template>
  <section class="page home-page">
    <div class="home-hero">
      <div>
        <p class="eyebrow">论文 AI 风格风险分析</p>
        <h1>上传论文，生成句子级分析报告</h1>
        <p>
          支持 Word、PDF、TXT 与图片内容解析，自动识别 AI 风格风险、检索写作规范，并给出可复制的表达优化建议。
        </p>
      </div>
      <div class="hero-badges">
        <span>Agent 工具路由</span>
        <span>RAG 写作规范</span>
        <span>个人模型配置</span>
      </div>
    </div>

    <div class="home-layout">
      <section class="check-card">
        <div class="check-card-head">
          <div>
            <p class="eyebrow">立即检测</p>
            <h2>选择论文或图片开始分析</h2>
          </div>
          <el-tag :type="auth.isLoggedIn ? 'success' : 'warning'" effect="light">
            {{ auth.isLoggedIn ? '已登录' : '请先登录' }}
          </el-tag>
        </div>

        <el-alert
          v-if="auth.isLoggedIn"
          class="model-alert"
          :type="modelReady ? 'success' : 'warning'"
          :title="modelReady ? `已配置模型：${settings.llmModel}，分析时会自动使用` : '当前账号还没有配置大模型 API Key，建议先到个人设置中保存模型参数'"
          show-icon
          :closable="false"
        />

        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".doc,.docx,.pdf,.txt,.png,.jpg,.jpeg"
          :on-change="handleFileChange"
          :on-remove="handleRemove"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="upload-copy">
            <strong>拖拽论文或图片到这里</strong>
            <span>支持 Word、PDF、TXT、PNG、JPG；图片会进入 OCR / 图像文字解析流程</span>
          </div>
        </el-upload>

        <div class="option-grid compact-options">
          <label>
            <span>分析深度</span>
            <el-segmented v-model="form.depth" :options="depthOptions" />
          </label>
          <label>
            <span>推荐语句数量</span>
            <el-input-number v-model="form.suggestionCount" :min="1" :max="3" />
          </label>
          <label>
            <span>写作风格</span>
            <el-select v-model="form.style">
              <el-option label="正式学术" value="academic" />
              <el-option label="自然清晰" value="natural" />
              <el-option label="简洁严谨" value="concise" />
            </el-select>
          </label>
          <label class="inline-setting">
            <span>启用 RAG</span>
            <el-switch v-model="form.enableRag" />
          </label>
        </div>

        <div class="action-row">
          <el-button
            type="primary"
            size="large"
            :icon="Promotion"
            :loading="submitting"
            :disabled="!selectedFile"
            @click="startAnalysis"
          >
            开始分析
          </el-button>
          <span>{{ selectedFile ? selectedFile.name : '上传后会生成任务并进入后台分析' }}</span>
        </div>
      </section>

      <aside class="auth-card">
        <template v-if="!auth.isLoggedIn">
          <div class="auth-card-title">
            <p class="eyebrow">账号登录</p>
            <h2>登录后保存报告和模型配置</h2>
          </div>

          <el-form class="inline-login-form" :model="loginForm" label-position="top" @submit.prevent>
            <el-segmented v-model="authMode" :options="authModeOptions" />

            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" autocomplete="username" placeholder="请输入用户名" />
            </el-form-item>

            <el-form-item v-if="authMode === 'register'" label="昵称">
              <el-input v-model="loginForm.nickname" placeholder="可选，默认使用用户名" />
            </el-form-item>

            <el-form-item label="密码">
              <el-input
                v-model="loginForm.password"
                autocomplete="current-password"
                type="password"
                show-password
                placeholder="请输入密码"
              />
            </el-form-item>

            <el-button type="primary" size="large" :loading="authLoading" @click="submitAuth">
              {{ authMode === 'login' ? '登录并继续' : '注册并继续' }}
            </el-button>
          </el-form>

          <p class="auth-note">未登录也可以浏览功能说明；上传分析、历史报告和个人设置需要登录后使用。</p>
        </template>

        <template v-else>
          <div class="account-card-head">
            <span class="account-avatar">{{ accountInitial }}</span>
            <div>
              <p class="eyebrow">当前账号</p>
              <h2>{{ auth.user?.nickname || auth.user?.username }}</h2>
            </div>
          </div>

          <div class="account-status">
            <article>
              <strong>{{ settings.llmModel || '未配置模型' }}</strong>
              <span>{{ settings.llmApiKeyConfigured ? 'API Key 已保存，分析自动使用' : '请到个人设置保存 API Key' }}</span>
            </article>
            <article>
              <strong>最多 3 个任务</strong>
              <span>同一账号可后台排队分析，结果保存在历史报告中</span>
            </article>
          </div>

          <div class="account-actions">
            <el-button type="primary" plain @click="router.push('/settings')">模型设置</el-button>
            <el-button plain @click="router.push('/history')">历史报告</el-button>
          </div>
        </template>
      </aside>
    </div>

    <section class="service-strip">
      <article v-for="item in services" :key="item.title">
        <el-icon><component :is="item.icon" /></el-icon>
        <strong>{{ item.title }}</strong>
        <span>{{ item.desc }}</span>
      </article>
    </section>

    <section class="flow-panel wide-flow">
      <div>
        <p class="eyebrow">Agent 工作流</p>
        <h2>从文档解析到表达优化的一体化流程</h2>
      </div>
      <div class="flow-list">
        <article v-for="step in steps" :key="step.title">
          <el-icon><component :is="step.icon" /></el-icon>
          <div>
            <strong>{{ step.title }}</strong>
            <span>{{ step.desc }}</span>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, DataAnalysis, Document, MagicStick, Promotion, Search, UploadFilled } from '@element-plus/icons-vue'
import { useAnalysisStore } from '../stores/analysisStore'
import { useAuthStore } from '../stores/authStore'
import { getUserSettings } from '../services/settingsApi'

const route = useRoute()
const router = useRouter()
const store = useAnalysisStore()
const auth = useAuthStore()
const selectedFile = ref(null)
const submitting = ref(false)
const authLoading = ref(false)
const authMode = ref(route.query.auth === 'register' ? 'register' : 'login')

const settings = reactive({
  llmModel: '',
  llmApiKeyConfigured: false
})

const loginForm = reactive({
  username: '',
  nickname: '',
  password: ''
})

const form = reactive({
  depth: 'standard',
  suggestionCount: 2,
  style: 'academic',
  enableRag: true
})

const modelReady = computed(() => Boolean(settings.llmModel && settings.llmApiKeyConfigured))
const accountInitial = computed(() => (auth.user?.nickname || auth.user?.username || 'A').slice(0, 1).toUpperCase())

const authModeOptions = [
  { label: '登录', value: 'login' },
  { label: '注册', value: 'register' }
]

const depthOptions = [
  { label: '快速', value: 'fast' },
  { label: '标准', value: 'standard' },
  { label: '深度', value: 'deep' }
]

const services = [
  { title: '文档解析', desc: '提取正文、图片文字和段落结构', icon: Document },
  { title: 'RAG 检索', desc: '检索学术写作规范作为判断参考', icon: Search },
  { title: '工具路由', desc: '按文件类型和深度自动选择分析工具', icon: Connection },
  { title: '表达优化', desc: '输出句子级风险原因和改写建议', icon: MagicStick }
]

const steps = [
  { title: '上传解析', desc: '解析 Word、PDF、TXT 和图片文本，形成可分析的段落与句子结构', icon: Document },
  { title: '风险识别', desc: '识别模板化表达、证据不足、语义空泛和 AI 风格痕迹', icon: DataAnalysis },
  { title: '知识库检索', desc: '结合私有写作规范和参考规则，减少纯模型判断带来的空泛结论', icon: Search },
  { title: '建议生成', desc: '按照用户选择的风格生成可复制的优化语句，并保存到个人报告', icon: MagicStick }
]

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleRemove() {
  selectedFile.value = null
}

async function loadSettings() {
  if (!auth.isLoggedIn) {
    Object.assign(settings, { llmModel: '', llmApiKeyConfigured: false })
    return
  }
  const result = await getUserSettings()
  Object.assign(settings, result)
}

async function submitAuth() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  authLoading.value = true
  try {
    if (authMode.value === 'login') {
      await auth.login({ username: loginForm.username, password: loginForm.password })
    } else {
      await auth.register({
        username: loginForm.username,
        password: loginForm.password,
        nickname: loginForm.nickname || loginForm.username
      })
    }
    ElMessage.success(authMode.value === 'login' ? '登录成功' : '注册成功')
    await loadSettings()
    if (route.query.redirect) {
      router.push(route.query.redirect)
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '账号操作失败')
  } finally {
    authLoading.value = false
  }
}

async function startAnalysis() {
  if (!auth.isLoggedIn) {
    authMode.value = 'login'
    ElMessage.warning('请先登录账号后再开始分析')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请先上传论文或图片')
    return
  }

  submitting.value = true
  try {
    const taskId = await store.createTask(selectedFile.value, form)
    router.push(`/progress/${taskId}`)
  } catch (error) {
    ElMessage.error(error.message || '创建分析任务失败')
  } finally {
    submitting.value = false
  }
}

watch(
  () => auth.isLoggedIn,
  () => loadSettings()
)

onMounted(loadSettings)
</script>
