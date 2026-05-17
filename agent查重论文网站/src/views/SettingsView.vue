<template>
  <section class="page settings-page">
    <div class="page-title compact">
      <p class="eyebrow">个人设置</p>
      <h1>分析偏好与模型配置</h1>
      <p>配置会绑定当前登录账号，Python AI Agent 在分析任务中会优先使用这里保存的大模型参数。</p>
    </div>

    <section class="settings-panel clean-settings">
      <el-alert
        v-if="settings.llmApiKeyConfigured"
        title="当前账号已保存 API Key。后续上传论文和使用论文助手时会自动使用该模型配置，无需重复填写。"
        type="success"
        show-icon
        :closable="false"
      />

      <div class="settings-block">
        <div>
          <p class="eyebrow">分析偏好</p>
          <h2>论文分析规则</h2>
        </div>

        <div class="settings-grid">
          <label>
            <span>推荐风格</span>
            <el-select v-model="settings.defaultStyle">
              <el-option label="正式学术" value="academic" />
              <el-option label="自然清晰" value="natural" />
              <el-option label="简洁严谨" value="concise" />
            </el-select>
          </label>

          <label>
            <span>推荐语句数量</span>
            <el-input-number v-model="settings.suggestionCount" :min="1" :max="5" />
          </label>

          <label>
            <span>高风险阈值</span>
            <el-slider v-model="settings.highRiskThreshold" :min="60" :max="95" show-input />
          </label>

          <label class="inline-setting">
            <span>启用 RAG 知识库</span>
            <el-switch v-model="settings.enableRag" />
          </label>
        </div>
      </div>

      <div class="settings-block ai-config-panel">
        <div>
          <p class="eyebrow">AI Agent</p>
          <h2>个人大模型配置</h2>
          <p>支持 OpenAI 兼容接口，例如 DeepSeek、通义千问、Kimi、智谱或本地 Ollama。</p>
        </div>

        <div class="settings-grid">
          <label>
            <span>Base URL</span>
            <el-input v-model="settings.llmBaseUrl" placeholder="https://api.deepseek.com/v1" />
          </label>

          <label>
            <span>模型名称</span>
            <el-input v-model="settings.llmModel" placeholder="deepseek-chat / qwen-plus" />
          </label>

          <label>
            <span>API Key</span>
            <el-input
              v-model="llmApiKeyInput"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="settings.llmApiKeyConfigured ? '已保存，如需更换请重新输入' : '请输入你的 API Key'"
            />
          </label>

          <label class="inline-setting">
            <span>清除已保存 Key</span>
            <el-switch v-model="clearLlmApiKey" />
          </label>
        </div>
      </div>

      <el-button type="primary" :loading="saving" @click="saveSettings">
        保存设置
      </el-button>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserSettings, updateUserSettings } from '../services/settingsApi'

const saving = ref(false)
const llmApiKeyInput = ref('')
const clearLlmApiKey = ref(false)

const settings = reactive({
  defaultStyle: 'academic',
  enableRag: true,
  highRiskThreshold: 75,
  suggestionCount: 2,
  llmBaseUrl: '',
  llmModel: '',
  llmApiKeyConfigured: false
})

async function loadSettings() {
  const result = await getUserSettings()
  Object.assign(settings, result)
}

async function saveSettings() {
  saving.value = true
  try {
    const payload = {
      defaultStyle: settings.defaultStyle,
      enableRag: settings.enableRag,
      highRiskThreshold: settings.highRiskThreshold,
      suggestionCount: settings.suggestionCount,
      llmBaseUrl: settings.llmBaseUrl,
      llmModel: settings.llmModel,
      clearLlmApiKey: clearLlmApiKey.value
    }
    if (llmApiKeyInput.value.trim()) {
      payload.llmApiKey = llmApiKeyInput.value.trim()
    }
    const result = await updateUserSettings(payload)
    Object.assign(settings, result)
    llmApiKeyInput.value = ''
    clearLlmApiKey.value = false
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(loadSettings)
</script>
