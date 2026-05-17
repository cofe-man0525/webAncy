import http from './http'

export function getAssistantSessions() {
  return http.get('/assistant/sessions')
}

export function getAssistantMessages(sessionId) {
  return http.get(`/assistant/sessions/${sessionId}/messages`)
}

export function sendAssistantMessage(formData) {
  return http.post('/assistant/chat', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}
