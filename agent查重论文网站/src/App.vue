<template>
  <el-config-provider namespace="el">
    <div class="app-shell">
      <header class="top-header">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">A</span>
          <span>
            <strong>文析智审</strong>
            <small>论文分析与表达优化</small>
          </span>
        </RouterLink>

        <nav class="top-nav">
          <RouterLink v-for="item in navItems" :key="item.path" :to="item.path" class="nav-link">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>

        <div class="header-actions">
          <template v-if="auth.isLoggedIn">
            <span class="user-name">{{ auth.user?.nickname || auth.user?.username }}</span>
            <el-tooltip v-if="isProgressPage" content="分析任务正在处理中，可以切换页面，暂不建议退出登录">
              <el-button text disabled>分析中</el-button>
            </el-tooltip>
            <el-button v-else text @click="logout">退出</el-button>
          </template>
          <RouterLink v-else class="login-link" to="/?auth=login">登录 / 注册</RouterLink>
        </div>
      </header>

      <main class="main-panel">
        <RouterView />
      </main>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Clock, House, Setting } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/authStore'

const navItems = [
  { label: '首页检测', path: '/', icon: House },
  { label: '论文助手', path: '/assistant', icon: ChatDotRound },
  { label: '历史报告', path: '/history', icon: Clock },
  { label: '个人设置', path: '/settings', icon: Setting }
]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const isProgressPage = computed(() => route.name === 'progress')

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>
