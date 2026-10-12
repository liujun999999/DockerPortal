import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code === 200) {
      return data.data
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message))
  },
  (error) => {
    const code = error.response?.data?.code
    const url: string = error.config?.url || ''
    // Swarm 未初始化(40901)/Worker 节点(40902)/Manager 锁定(40903)：不弹红色报错，由页面展示引导组件
    if (code === 40901 || code === 40902 || code === 40903) {
      error.swarmGuide = code
      error.friendlyMessage = error.response?.data?.message
      return Promise.reject(error)
    }
    if (error.response?.status === 401 || code === 40100) {
      // 未认证 / token 过期 / 无效：一律清登录态并跳登录页。
      // 但登录接口自己返 401（密码错）不能重定向，否则登录页会反复刷新。
      if (url.includes('/auth/login')) {
        ElMessage.error(error.response?.data?.message || '用户名或密码错误')
      } else {
        const authStore = useAuthStore()
        authStore.logout()
        if (router.currentRoute.value.name !== 'Login') router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      }
    } else if (code === 40310) {
      // 待强制改密：置标记，由 AppLayout 弹出不可关闭的改密框
      const authStore = useAuthStore()
      authStore.mustChangePassword = true
      ElMessage.warning(error.response?.data?.message || '请先修改初始密码')
    } else if (error.response?.status === 403 || code === 40300) {
      // 已登录但角色不够：不跳登录页，仅提示
      ElMessage.error(error.response?.data?.message || '权限不足，该操作需要更高角色')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

// 响应拦截器已把 body 解包为 data.data，但 TS 仍按 AxiosResponse 推断；
// 这里按运行时真实返回值（解包后的业务数据）重新声明类型，避免每个调用点都写 as any。
interface UnwrappedRequest {
  get<T = any>(url: string, config?: any): Promise<T>
  post<T = any>(url: string, data?: any, config?: any): Promise<T>
  put<T = any>(url: string, data?: any, config?: any): Promise<T>
  delete<T = any>(url: string, config?: any): Promise<T>
}

const typedRequest = request as unknown as UnwrappedRequest

export default typedRequest
