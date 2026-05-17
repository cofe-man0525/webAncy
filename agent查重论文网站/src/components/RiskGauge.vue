<template>
  <div ref="chartRef" class="risk-gauge"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  score: {
    type: Number,
    required: true
  }
})

const chartRef = ref(null)
let chart

function renderChart() {
  if (!chart && chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
  chart?.setOption({
    series: [
      {
        type: 'gauge',
        startAngle: 205,
        endAngle: -25,
        min: 0,
        max: 100,
        radius: '100%',
        pointer: { show: false },
        progress: {
          show: true,
          width: 18,
          itemStyle: { color: props.score >= 75 ? '#dc2626' : props.score >= 50 ? '#d97706' : '#059669' }
        },
        axisLine: {
          lineStyle: { width: 18, color: [[1, '#e5e7eb']] }
        },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}%',
          fontSize: 34,
          fontWeight: 700,
          color: '#111827',
          offsetCenter: [0, '8%']
        },
        data: [{ value: props.score }]
      }
    ]
  })
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', renderChart)
})

watch(() => props.score, renderChart)

onBeforeUnmount(() => {
  window.removeEventListener('resize', renderChart)
  chart?.dispose()
})
</script>
