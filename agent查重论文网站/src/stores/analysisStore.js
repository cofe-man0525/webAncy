import { defineStore } from 'pinia'
import { getReport, getTaskProgress } from '../services/analysisApi'
import { getAnalysisMemory, getHistoryTasks } from '../services/historyApi'
import { uploadPaper } from '../services/paperApi'

const emptyReport = {
  title: '',
  summary: '',
  overallRiskScore: 0,
  paragraphs: [],
  agentTrace: [
    { name: '文档解析工具', status: 'waiting', detail: '等待 Python AI 服务解析论文内容' },
    { name: '风险识别工具', status: 'waiting', detail: '等待识别 AI 风格风险句子' },
    { name: 'RAG 检索工具', status: 'waiting', detail: '等待检索写作规范和参考信息' },
    { name: '表达优化工具', status: 'waiting', detail: '等待生成句子级优化建议' }
  ]
}

export const useAnalysisStore = defineStore('analysis', {
  state: () => ({
    currentTaskId: '',
    taskProgress: 0,
    activeSentenceId: '',
    report: emptyReport,
    tasks: [],
    memory: {
      reportCount: 0,
      averageRiskScore: 0,
      recentReports: [],
      commonReasons: []
    },
    loading: false
  }),
  actions: {
    async createTask(file, options = {}) {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('depth', options.depth || 'standard')
      formData.append('style', options.style || 'academic')
      formData.append('enableRag', options.enableRag ?? true)
      formData.append('suggestionCount', options.suggestionCount || 2)

      const result = await uploadPaper(formData)
      const taskId = result.taskId
      this.currentTaskId = taskId
      this.taskProgress = 0
      this.tasks.unshift({
        id: taskId,
        title: file?.name || '未命名论文.docx',
        createdAt: new Date().toLocaleString('zh-CN', { hour12: false }),
        status: result.status,
        score: null
      })
      return taskId
    },
    async fetchProgress(taskId) {
      const result = await getTaskProgress(taskId)
      this.currentTaskId = result.taskId
      this.taskProgress = result.progress || 0
      return result
    },
    async fetchReport(taskId) {
      this.loading = true
      try {
        const report = await getReport(taskId)
        this.report = report
        const firstSentence = report.paragraphs?.[0]?.sentences?.[0]
        this.activeSentenceId = firstSentence?.id || ''
        return report
      } finally {
        this.loading = false
      }
    },
    async fetchHistory() {
      this.tasks = await getHistoryTasks()
      return this.tasks
    },
    async fetchMemory() {
      this.memory = await getAnalysisMemory()
      return this.memory
    },
    setActiveSentence(sentenceId) {
      this.activeSentenceId = sentenceId
    }
  }
})
