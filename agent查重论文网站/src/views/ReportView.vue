<template>
  <section class="page report-page">
    <el-skeleton v-if="store.loading" :rows="8" animated />

    <template v-else>
      <div class="report-top">
        <div>
          <p class="eyebrow">检测报告</p>
          <h1>{{ report.title }}</h1>
          <p>{{ report.summary }}</p>
          <div class="risk-stats">
            <span>高风险 {{ riskCounts.high }} 句</span>
            <span>中风险 {{ riskCounts.medium }} 句</span>
            <span>低风险 {{ riskCounts.low }} 句</span>
          </div>
        </div>
        <div class="score-panel">
          <RiskGauge :score="report.overallRiskScore" />
          <span>总体 AI 风格风险</span>
        </div>
      </div>

      <div class="report-grid">
        <section class="paper-reader">
          <div class="panel-head">
            <h2>论文原文标注</h2>
            <el-button :icon="Delete" type="danger" plain @click="removeReport">删除报告</el-button>
          </div>

          <article v-for="paragraph in report.paragraphs" :key="paragraph.id" class="paragraph-block">
            <div class="paragraph-meta">
              <strong>第 {{ paragraph.index }} 段</strong>
              <RiskBadge :score="paragraph.riskScore" />
            </div>
            <p>
              <button
                v-for="sentence in paragraph.sentences"
                :key="sentence.id"
                class="sentence-chip"
                :class="[riskClass(sentence.riskScore), { selected: sentence.id === store.activeSentenceId }]"
                @click="store.setActiveSentence(sentence.id)"
              >
                {{ sentence.text }}
                <small>{{ sentence.riskScore }}%</small>
              </button>
            </p>
          </article>
        </section>

        <aside class="analysis-panel">
          <div class="panel-head">
            <h2>句子优化建议</h2>
            <el-button plain @click="router.push('/')">继续检测</el-button>
          </div>

          <SentenceSuggestion v-if="activeSentence" :sentence="activeSentence" active />

          <section class="agent-panel">
            <h2>Agent 工具调用</h2>
            <AgentTrace :items="report.agentTrace" />
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import AgentTrace from '../components/AgentTrace.vue'
import RiskBadge from '../components/RiskBadge.vue'
import RiskGauge from '../components/RiskGauge.vue'
import SentenceSuggestion from '../components/SentenceSuggestion.vue'
import { deleteReport } from '../services/historyApi'
import { useAnalysisStore } from '../stores/analysisStore'
import { riskClass } from '../utils/risk'

const route = useRoute()
const router = useRouter()
const store = useAnalysisStore()
const report = computed(() => store.report)

const allSentences = computed(() => report.value.paragraphs?.flatMap((paragraph) => paragraph.sentences) || [])

const riskCounts = computed(() => {
  return allSentences.value.reduce(
    (acc, sentence) => {
      if (sentence.riskScore >= 75) acc.high += 1
      else if (sentence.riskScore >= 50) acc.medium += 1
      else acc.low += 1
      return acc
    },
    { high: 0, medium: 0, low: 0 }
  )
})

const activeSentence = computed(() => {
  return allSentences.value.find((sentence) => sentence.id === store.activeSentenceId)
})

async function removeReport() {
  await ElMessageBox.confirm('确定删除这份报告吗？删除后历史记录中也不会再显示。', '删除报告', {
    type: 'warning'
  })
  await deleteReport(route.params.taskId)
  ElMessage.success('报告已删除')
  router.push('/history')
}

onMounted(async () => {
  try {
    await store.fetchReport(route.params.taskId)
  } catch (error) {
    ElMessage.error(error.message || '报告暂未生成')
  }
})
</script>
