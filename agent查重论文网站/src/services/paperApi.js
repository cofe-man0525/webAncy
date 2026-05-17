import http from './http'

export function uploadPaper(formData) {
  return http.post('/papers/upload', formData)
}
