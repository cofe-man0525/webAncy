import axios from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY, USER_KEY } from './sessionKeys'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code !== 'undefined') {
      if (payload.code === 0) {
        return payload.data
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'

    if (status === 401 || status === 403) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      ElMessage.error(status === 401 ? '登录已过期，请重新登录' : '没有访问权限')
      if (window.location.pathname !== '/') {
        window.location.href = `/?auth=login&redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`
      }
    }

    return Promise.reject(new Error(message))
  }
)

export default http
