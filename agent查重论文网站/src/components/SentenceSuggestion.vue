<template>
  <section class="suggestion-card" :class="{ active }">
    <div class="suggestion-head">
      <div>
        <p class="eyebrow">当前句子</p>
        <h3>{{ sentence.text }}</h3>
      </div>
      <RiskBadge :score="sentence.riskScore" />
    </div>

    <div class="reason-list">
      <span v-for="reason in sentence.reasons" :key="reason">{{ reason }}</span>
    </div>

    <div class="advice-box">
      <strong>修改方向</strong>
      <p>{{ sentence.advice }}</p>
    </div>

    <div v-if="sentence.ragReferences?.length" class="reference-box">
      <strong>RAG 检索参考</strong>
      <p v-for="reference in sentence.ragReferences" :key="reference">{{ reference }}</p>
    </div>

    <div class="rewrite-list">
      <article v-for="text in sentence.suggestedTexts" :key="text" class="rewrite-item">
        <p>{{ text }}</p>
        <el-button :icon="CopyDocument" plain @click="copyText(text)">复制语句</el-button>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import RiskBadge from './RiskBadge.vue'

defineProps({
  sentence: {
    type: Object,
    required: true
  },
  active: {
    type: Boolean,
    default: false
  }
})

async function copyText(text) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('推荐语句已复制')
}
</script>
