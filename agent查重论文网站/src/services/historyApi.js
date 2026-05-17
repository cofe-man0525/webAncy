import http from './http'

export function getHistoryTasks() {
  return http.get('/history/tasks')
}

export function getAnalysisMemory() {
  return http.get('/history/memory')
}

export function deleteReport(taskId) {
  return http.delete(`/analysis/reports/${taskId}`)
}
