<template>
  <section class="page progress-page">
    <div class="progress-card">
      <p class="eyebrow">任务处理中</p>
      <h1>正在进行 Agent + RAG 分析</h1>
      <p>当前任务：{{ route.params.taskId }}</p>

      <el-progress :percentage="store.taskProgress" :stroke-width="14" striped striped-flow />

      <AgentTrace :items="visibleTrace" />

      <el-alert v-if="taskStatus === 'failed'" :title="errorMessage || '分析失败'" type="error" show-icon />

      <el-button v-if="taskStatus === 'done'" type="primary" size="large" @click="router.push(`/report/${route.params.taskId}`)">
        查看分析报告
      </el-button>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentTrace from '../components/AgentTrace.vue'
import { useAnalysisStore } from '../stores/analysisStore'

const route = useRoute()
const router = useRouter()
const store = useAnalysisStore()
const taskStatus = ref('queued')
const errorMessage = ref('')
let timer

const visibleTrace = computed(() => {
  const count = Math.max(1, Math.ceil(store.taskProgress / 25))
  return store.report.agentTrace.slice(0, count)
})

async function poll() {
  try {
    const result = await store.fetchProgress(route.params.taskId)
    taskStatus.value = result.status
    errorMessage.value = result.errorMessage || ''
    if (result.status === 'done' || result.status === 'failed') {
      window.clearInterval(timer)
    }
  } catch (error) {
    window.clearInterval(timer)
    ElMessage.error(error.message || '任务进度获取失败')
  }
}

onMounted(async () => {
  await poll()
  timer = window.setInterval(poll, 1200)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
})
</script>
