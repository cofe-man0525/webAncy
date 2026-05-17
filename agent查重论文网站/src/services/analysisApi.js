import http from './http'

export function getTaskProgress(taskId) {
  return http.get(`/analysis/tasks/${taskId}/progress`)
}

export function getReport(taskId) {
  return http.get(`/analysis/reports/${taskId}`)
}

export function regenerateSuggestion(payload) {
  return http.post('/analysis/suggestions/regenerate', payload)
}
