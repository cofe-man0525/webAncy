import { createRouter, createWebHistory } from 'vue-router'
import UploadView from '../views/UploadView.vue'
import ProgressView from '../views/ProgressView.vue'
import ReportView from '../views/ReportView.vue'
import HistoryView from '../views/HistoryView.vue'
import SettingsView from '../views/SettingsView.vue'
import AssistantView from '../views/AssistantView.vue'
import { useAuthStore } from '../stores/authStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', redirect: (to) => ({ path: '/', query: { auth: 'login', redirect: to.query.redirect } }) },
    { path: '/', name: 'upload', component: UploadView },
    { path: '/progress/:taskId', name: 'progress', component: ProgressView, meta: { requiresAuth: true } },
    { path: '/report/:taskId', name: 'report', component: ReportView, meta: { requiresAuth: true } },
    { path: '/assistant', name: 'assistant', component: AssistantView, meta: { requiresAuth: true } },
    { path: '/history', name: 'history', component: HistoryView, meta: { requiresAuth: true } },
    { path: '/settings', name: 'settings', component: SettingsView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/', query: { auth: 'login', redirect: to.fullPath } }
  }
  return true
})

export default router
