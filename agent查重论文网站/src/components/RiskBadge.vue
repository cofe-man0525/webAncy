<template>
  <el-tag :type="riskType(score)" effect="light" round>
    {{ riskLabel(score) }} {{ score }}%
  </el-tag>
</template>

<script setup>
import { riskLabel, riskType } from '../utils/risk'

defineProps({
  score: {
    type: Number,
    required: true
  }
})
</script>
