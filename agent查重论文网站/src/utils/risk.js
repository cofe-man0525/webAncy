export function riskType(score) {
  if (score >= 75) return 'danger'
  if (score >= 50) return 'warning'
  return 'success'
}

export function riskLabel(score) {
  if (score >= 75) return '高风险'
  if (score >= 50) return '中风险'
  return '低风险'
}

export function riskClass(score) {
  if (score >= 75) return 'risk-high'
  if (score >= 50) return 'risk-medium'
  return 'risk-low'
}
