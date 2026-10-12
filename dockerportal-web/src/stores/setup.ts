import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSetupStatus } from '@/api/setup'

/**
 * 安装状态缓存。路由守卫每次导航都要知道"这个实例初始化过没有"，
 * 但没必要每次都打后端，所以拉一次存住，初始化成功后再主动失效。
 */
export const useSetupStore = defineStore('setup', () => {
  const needsSetup = ref(false)
  const freshInstall = ref(false)
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  async function fetchStatus() {
    try {
      const res: any = await getSetupStatus()
      needsSetup.value = !!res?.needsSetup
      freshInstall.value = !!res?.freshInstall
    } catch {
      // 探测失败一律按"已初始化"处理：宁可让向导少弹一次，也不能因状态接口异常把正常登录堵死
      needsSetup.value = false
      freshInstall.value = false
    } finally {
      loaded.value = true
    }
  }

  /** 并发导航共用同一次请求 */
  function ensureLoaded(): Promise<void> {
    if (loaded.value) return Promise.resolve()
    if (!inflight) {
      inflight = fetchStatus().finally(() => { inflight = null })
    }
    return inflight
  }

  /** 建号成功后刷新，让守卫重新认定"已初始化"，避免回落到 /setup */
  function reset() {
    loaded.value = false
    return ensureLoaded()
  }

  return { needsSetup, freshInstall, loaded, ensureLoaded, reset }
})
