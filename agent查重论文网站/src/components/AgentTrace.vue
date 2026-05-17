<template>
  <div class="agent-trace">
    <article v-for="item in items" :key="item.name" class="trace-item">
      <el-icon><CircleCheck /></el-icon>
      <div>
        <strong>{{ item.name }}</strong>
        <span>{{ item.detail }}</span>
      </div>
    </article>
  </div>
</template>

<script setup>
import { CircleCheck } from '@element-plus/icons-vue'

defineProps({
  items: {
    type: Array,
    default: () => []
  }
})
</script>
