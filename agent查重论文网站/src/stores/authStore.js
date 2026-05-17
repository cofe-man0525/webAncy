import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, register as registerApi } from '../services/authApi'
import { TOKEN_KEY, USER_KEY } from '../services/sessionKeys'

if (localStorage.getItem(TOKEN_KEY) === 'dev-mock-token') {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token)
  },
  actions: {
    setSession(session) {
      this.token = session.token
      this.user = session.user
      localStorage.setItem(TOKEN_KEY, session.token)
      localStorage.setItem(USER_KEY, JSON.stringify(session.user))
    },
    async login(payload) {
      const session = await loginApi(payload)
      this.setSession(session)
      return session
    },
    async register(payload) {
      const session = await registerApi(payload)
      this.setSession(session)
      return session
    },
    async logout() {
      if (this.token) {
        try {
          await logoutApi()
        } catch {
          // 即使服务端 token 已过期，也要清理本地登录态。
        }
      }
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
