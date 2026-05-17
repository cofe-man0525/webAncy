<template>
  <section class="page">
    <div class="page-title compact">
      <p class="eyebrow">历史报告</p>
      <h1>最近分析过的论文</h1>
    </div>

    <section class="memory-panel">
      <div>
        <p class="eyebrow">AI 历史记忆</p>
        <h2>个人分析画像</h2>
        <p>
          已形成 {{ store.memory.reportCount || 0 }} 次历史分析记忆，
          近期平均风险分约 {{ store.memory.averageRiskScore || 0 }}。
        </p>
      </div>
      <div class="memory-tags">
        <el-tag v-for="item in store.memory.commonReasons" :key="item.reason" effect="light">
          {{ item.reason }} x {{ item.count }}
        </el-tag>
        <el-tag v-if="!store.memory.commonReasons?.length" type="info" effect="light">
          暂无高频风险原因
        </el-tag>
      </div>
    </section>

    <section class="history-list">
      <article v-for="task in store.tasks" :key="task.id" class="history-item">
        <div>
          <h2>{{ task.title }}</h2>
          <span>{{ task.createdAt }}</span>
        </div>
        <RiskBadge v-if="task.score" :score="task.score" />
        <el-tag v-else type="info" effect="light">{{ statusLabel(task.status) }}</el-tag>
        <div class="history-actions">
          <el-button type="primary" plain :disabled="task.status !== 'done'" @click="router.push(`/report/${task.id}`)">
            查看报告
          </el-button>
          <el-button type="danger" plain @click="remove(task.id)">删除</el-button>
        </div>
      </article>

      <el-empty v-if="!store.tasks.length" description="还没有分析记录" />
    </section>
  </section>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RiskBadge from '../components/RiskBadge.vue'
import { useAnalysisStore } from '../stores/analysisStore'
import { deleteReport } from '../services/historyApi'

const router = useRouter()
const store = useAnalysisStore()

function statusLabel(status) {
  return {
    queued: '等待中',
    processing: '分析中',
    done: '已完成',
    failed: '失败'
  }[status] || status
}

async function remove(taskId) {
  await ElMessageBox.confirm('确定删除这份报告和任务记录吗？删除后不会再出现在历史报告中。', '删除报告', {
    type: 'warning'
  })
  await deleteReport(taskId)
  ElMessage.success('报告已删除')
  await store.fetchHistory()
  await store.fetchMemory()
}

onMounted(async () => {
  await store.fetchHistory()
  await store.fetchMemory()
})
</script>
