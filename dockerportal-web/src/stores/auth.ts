import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getMe } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const role = ref(localStorage.getItem('role') || '')
  const mustChangePassword = ref(localStorage.getItem('mustChangePassword') === 'true')

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')
  // 运维及以上：可执行容器级操作
  const isOperator = computed(() => role.value === 'ADMIN' || role.value === 'OPERATOR')

  function persist() {
    localStorage.setItem('token', token.value)
    localStorage.setItem('username', username.value)
    localStorage.setItem('role', role.value)
    localStorage.setItem('mustChangePassword', String(mustChangePassword.value))
  }

  async function login(user: string, pass: string) {
    const res = await loginApi(user, pass)
    token.value = res.accessToken
    username.value = res.username
    role.value = res.role
    mustChangePassword.value = !!res.mustChangePassword
    persist()
  }

  /** 刷新后重新拉取自身状态（首登改密标记可能被后端更新） */
  async function refreshMe() {
    try {
      const me = await getMe()
      if (me?.username) username.value = me.username
      if (me?.role) role.value = me.role
      mustChangePassword.value = !!me?.mustChangePassword
      persist()
    } catch (e) {
      // 忽略：拦截器会处理 401
    }
  }

  function clearMustChangePassword() {
    mustChangePassword.value = false
    persist()
  }

  /** 改密成功后换签的新 token（旧 token 携带 must_change claim 会被后端 40310 拦截） */
  function setToken(t: string) {
    token.value = t
    persist()
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    localStorage.removeItem('mustChangePassword')
  }

  return {
    token, username, role, mustChangePassword,
    isAuthenticated, isAdmin, isOperator,
    login, refreshMe, clearMustChangePassword, setToken, logout,
  }
})
