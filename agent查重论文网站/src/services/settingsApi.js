import http from './http'

export function getUserSettings() {
  return http.get('/user/settings')
}

export function updateUserSettings(payload) {
  return http.put('/user/settings', payload)
}
